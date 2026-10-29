/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import io.github.nbauma109.decompiler.util.Logger;

/**
 * Fast, offline, best-effort check of whether a jar declares an open-source license. Only the
 * Maven {@code pom.xml} files, the license files and the OSGi {@code Bundle-License} header are
 * read; classes are never touched. A jar without recognizable evidence is {@link Verdict#UNKNOWN}
 * (never "proprietary"), so callers must treat anything but {@link Verdict#OPEN_SOURCE} as closed.
 * <p>
 * The check is deliberately conservative towards {@link Verdict#UNKNOWN}: a jar bundling several
 * Maven projects (shaded or fat jar) is open source only if every bundled pom declares an open-source
 * license, because a proprietary jar commonly ships the license files of its third-party dependencies.
 */
public final class JarLicenseDetector {

    public enum Verdict {
        OPEN_SOURCE, UNKNOWN
    }

    private static final int MAX_TEXT_BYTES = 64 * 1024;
    private static final int MAX_POMS = 64;
    private static final String MAVEN_DIR = "META-INF/maven/"; //$NON-NLS-1$
    private static final String POM_XML = "/pom.xml"; //$NON-NLS-1$
    private static final String MANIFEST = "META-INF/MANIFEST.MF"; //$NON-NLS-1$
    private static final String BUNDLE_LICENSE = "Bundle-License"; //$NON-NLS-1$

    private static final Pattern LICENSE_FILE_NAME = Pattern.compile(
            "(?i)(?:META-INF/)?(?:license|licence|copying|unlicense)(?:[-_.][^/]*)?"); //$NON-NLS-1$

    private static final List<Pattern> OPEN_SOURCE = List.of(
            licensePattern("apache[- ](?:software )?(?:license|licence|2)", "apache-2\\.0", "apache\\.org/licenses"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            licensePattern("mit (?:license|licence)", "mit-0", "opensource\\.org/licenses/mit", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    "permission is hereby granted, free of charge"), //$NON-NLS-1$
            licensePattern("bsd(?:[- ]?[23](?:[- ]clause)?)?", "redistribution and use in source and binary forms"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            licensePattern("(?:gnu )?(?:lesser |library |affero )?general public license", "a?l?gpl(?:-?v?[23])?"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            licensePattern("mozilla public license", "mpl[- ]?(?:1\\.1|2\\.0)"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            licensePattern("eclipse public license", "eclipse distribution license", "epl[- ]?(?:1\\.0|2\\.0)"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            licensePattern("common development and distribution license", "cddl"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            licensePattern("isc license", "permission to use, copy, modify, and/or distribute this software"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            licensePattern("unlicense", "cc0", "creative commons zero", "zlib(?:/libpng)? license")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    // A bare "MIT" is only trusted in a declared license name, never in free text ("mit" is a German word).
    private static final Pattern DECLARED_MIT = Pattern.compile("(?:^|[\\s(])mit(?:$|[\\s)])"); //$NON-NLS-1$
    // Only explicit declarations: standard license texts (GPL, ...) mention "proprietary programs".
    // Conjunctions and modifiers that may add terms to a recognized license name; the remainder is checked
    // after the license names are removed, so "Common Development and Distribution License" is unaffected.
    private static final Pattern RESTRICTIVE = Pattern.compile(
            "\\b(?:and|with|plus|except|excluding|clause|licenseref|proprietary|commercial|confidential|restricted)\\b"); //$NON-NLS-1$
    private static final Pattern PROPRIETARY = Pattern.compile(
            "\\b(?:proprietary and confidential|confidential and proprietary|strictly confidential" //$NON-NLS-1$
                    + "|(?:is|are) (?:the )?(?:proprietary|confidential)|proprietary (?:software )?licen[sc]e)\\b"); //$NON-NLS-1$

    private JarLicenseDetector() {
    }

    private static Pattern licensePattern(String... alternatives) {
        return Pattern.compile("\\b(?:" + String.join("|", alternatives) + ")\\b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    public static Verdict classify(File jar) {
        try (ZipFile zip = new ZipFile(jar)) {
            List<ZipEntry> poms = new ArrayList<>();
            boolean tooManyPoms = false;
            List<ZipEntry> licenseFiles = new ArrayList<>();
            ZipEntry manifest = null;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith(MAVEN_DIR) && name.endsWith(POM_XML)) {
                    if (poms.size() < MAX_POMS) {
                        poms.add(entry);
                    } else {
                        tooManyPoms = true;
                    }
                } else if (MANIFEST.equals(name)) {
                    manifest = entry;
                } else if (LICENSE_FILE_NAME.matcher(name).matches()) {
                    licenseFiles.add(entry);
                }
            }
            // Not every bundled pom can be inspected, so open source cannot be established.
            return tooManyPoms ? Verdict.UNKNOWN : classify(zip, poms, licenseFiles, manifest);
        } catch (IOException | RuntimeException e) {
            Logger.debug(e);
            return Verdict.UNKNOWN;
        }
    }

    private static Verdict classify(ZipFile zip, List<ZipEntry> poms, List<ZipEntry> licenseFiles, ZipEntry manifest)
            throws IOException {
        int declaring = countOpenSourcePoms(zip, poms);
        if (declaring < 0) {
            return Verdict.UNKNOWN;
        }
        String bundleLicense = manifest == null ? null : bundleLicense(zip, manifest);
        boolean bundleDeclared = bundleLicense != null && !bundleLicense.isBlank();
        if (bundleDeclared && !isOpenSourceDeclaration(bundleLicense)) {
            // An explicit declaration wins over poms and license files, which may belong to a bundled dependency.
            return Verdict.UNKNOWN;
        }
        if (poms.size() > 1) {
            // Bundled projects: the license files may belong to any of them.
            return declaring == poms.size() ? Verdict.OPEN_SOURCE : Verdict.UNKNOWN;
        }
        return declaring == 1 || bundleDeclared || hasOpenSourceLicenseFile(zip, licenseFiles) ? Verdict.OPEN_SOURCE
                : Verdict.UNKNOWN;
    }

    /**
     * Counts the poms declaring licenses, all of them open source, or returns -1 when a pom declares a
     * license that is not recognized or cannot be read completely.
     */
    private static int countOpenSourcePoms(ZipFile zip, List<ZipEntry> poms) throws IOException {
        int declaring = 0;
        for (ZipEntry pom : poms) {
            String pomXml = readText(zip, pom);
            if (pomXml == null) {
                // An unread part of the pom may hold a declaration that contradicts the others.
                return -1;
            }
            Optional<List<String>> parsed = declaredLicenses(pomXml);
            if (parsed.isEmpty()) {
                return -1;
            }
            List<String> declared = parsed.get();
            if (declared.isEmpty()) {
                continue;
            }
            if (!declared.stream().allMatch(JarLicenseDetector::isOpenSourceDeclaration)) {
                return -1;
            }
            declaring++;
        }
        return declaring;
    }

    private static boolean hasOpenSourceLicenseFile(ZipFile zip, List<ZipEntry> licenseFiles) throws IOException {
        for (ZipEntry licenseFile : licenseFiles) {
            String text = readText(zip, licenseFile);
            if (text != null && isOpenSourceText(text) && !PROPRIETARY.matcher(text.toLowerCase(Locale.ROOT)).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the license names and urls declared in the pom's {@code <licenses>} element, or empty when the
     * pom cannot be interpreted reliably, which callers must treat like a contradicting declaration.
     */
    static Optional<List<String>> declaredLicenses(String pomXml) {
        Element project;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            project = builder.parse(new InputSource(new StringReader(pomXml))).getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException | RuntimeException e) {
            Logger.debug(e);
            return Optional.empty();
        }
        List<String> declared = new ArrayList<>();
        for (Element licenses : childElements(project, "licenses")) { //$NON-NLS-1$
            for (Element license : childElements(licenses, "license")) { //$NON-NLS-1$
                StringBuilder text = new StringBuilder();
                for (Element field : childElements(license, null)) {
                    if ("name".equals(field.getLocalName()) || "url".equals(field.getLocalName())) { //$NON-NLS-1$ //$NON-NLS-2$
                        text.append(field.getTextContent().trim()).append(' ');
                    }
                }
                declared.add(text.toString());
            }
        }
        return Optional.of(declared);
    }

    private static List<Element> childElements(Element parent, String localName) {
        List<Element> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && (localName == null || localName.equals(element.getLocalName()))) {
                children.add(element);
            }
        }
        return children;
    }

    static boolean isOpenSourceText(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return OPEN_SOURCE.stream().anyMatch(pattern -> pattern.matcher(lower).find());
    }

    /**
     * A declaration is accepted only when everything left after removing the recognized license names is
     * free of restrictive terms, so "MIT AND LicenseRef-Proprietary" or "Apache 2.0 with Commons Clause" are not.
     */
    private static boolean isOpenSourceDeclaration(String declared) {
        if (declared == null) {
            return false;
        }
        String remainder = declared.toLowerCase(Locale.ROOT);
        boolean recognized = false;
        List<Pattern> names = new ArrayList<>(OPEN_SOURCE);
        names.add(DECLARED_MIT);
        for (Pattern name : names) {
            Matcher matcher = name.matcher(remainder);
            if (matcher.find()) {
                recognized = true;
                remainder = matcher.replaceAll(" ");
            }
        }
        return recognized && !RESTRICTIVE.matcher(remainder).find();
    }

    private static String bundleLicense(ZipFile zip, ZipEntry manifestEntry) throws IOException {
        try (InputStream in = zip.getInputStream(manifestEntry)) {
            return new Manifest(in).getMainAttributes().getValue(new Attributes.Name(BUNDLE_LICENSE));
        }
    }

    /** Returns the entry's text, or {@code null} when it exceeds {@link #MAX_TEXT_BYTES} and would be truncated. */
    private static String readText(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] bytes = in.readNBytes(MAX_TEXT_BYTES + 1);
            return bytes.length > MAX_TEXT_BYTES ? null : new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
