/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.search;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

import io.github.nbauma109.decompiler.search.JarLicenseDetector.Verdict;
import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport;

public class JarLicenseDetectorTest {

    private static final String APACHE_POM = pom("The Apache Software License, Version 2.0"); //$NON-NLS-1$
    private static final String MIT_TEXT = "Permission is hereby granted, free of charge, to any person"; //$NON-NLS-1$

    @Test
    public void singlePomDeclaringOpenSourceLicenseIsOpenSource() throws IOException {
        assertEquals(Verdict.OPEN_SOURCE, classify("META-INF/maven/g/a/pom.xml", APACHE_POM)); //$NON-NLS-1$
    }

    @Test
    public void bareMitInPomIsOpenSource() throws IOException {
        assertEquals(Verdict.OPEN_SOURCE, classify("META-INF/maven/g/a/pom.xml", pom("MIT"))); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void proprietaryPomIsUnknown() throws IOException {
        assertEquals(Verdict.UNKNOWN, classify("META-INF/maven/g/a/pom.xml", pom("ACME Commercial License"))); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void pomWithoutLicenseFallsBackToLicenseFile() throws IOException {
        assertEquals(Verdict.OPEN_SOURCE, classify("META-INF/maven/g/a/pom.xml", "<project/>", //$NON-NLS-1$ //$NON-NLS-2$
                "META-INF/LICENSE", "Apache License\nVersion 2.0")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void licenseFileAloneIsOpenSource() throws IOException {
        assertEquals(Verdict.OPEN_SOURCE, classify("LICENSE.txt", MIT_TEXT)); //$NON-NLS-1$
    }

    @Test
    public void licenseFileMarkedProprietaryIsUnknown() throws IOException {
        assertEquals(Verdict.UNKNOWN, classify("META-INF/LICENSE", MIT_TEXT + "\nThis product is proprietary")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void bundledPomsMustAllDeclareOpenSourceLicenses() throws IOException {
        assertEquals(Verdict.UNKNOWN, classify("META-INF/maven/g/own/pom.xml", "<project/>", //$NON-NLS-1$ //$NON-NLS-2$
                "META-INF/maven/g/dep/pom.xml", APACHE_POM, "META-INF/LICENSE", MIT_TEXT)); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(Verdict.OPEN_SOURCE, classify("META-INF/maven/g/own/pom.xml", APACHE_POM, //$NON-NLS-1$
                "META-INF/maven/g/dep/pom.xml", APACHE_POM)); //$NON-NLS-1$
    }

    @Test
    public void bundleLicenseHeaderIsOpenSource() throws IOException {
        assertEquals(Verdict.OPEN_SOURCE, classify("META-INF/MANIFEST.MF", //$NON-NLS-1$
                "Manifest-Version: 1.0\nBundle-License: https://www.apache.org/licenses/LICENSE-2.0\n")); //$NON-NLS-1$
    }

    @Test
    public void commentedOutLicenseIsIgnored() throws IOException {
        assertEquals(Verdict.UNKNOWN, classify("META-INF/maven/g/a/pom.xml", //$NON-NLS-1$
                "<project><!-- <licenses><license><name>MIT</name></license></licenses> --></project>")); //$NON-NLS-1$
    }

    @Test
    public void standardGplTextMentioningProprietaryProgramsIsOpenSource() throws IOException {
        assertEquals(Verdict.OPEN_SOURCE, classify("LICENSE.html", //$NON-NLS-1$
                "GNU General Public License ... incorporate it into proprietary programs ...")); //$NON-NLS-1$
    }

    @Test
    public void tooManyBundledPomsIsUnknown() throws IOException {
        String[] entries = new String[66 * 2];
        for (int i = 0; i < 65; i++) {
            entries[i * 2] = "META-INF/maven/g/a" + i + "/pom.xml"; //$NON-NLS-1$ //$NON-NLS-2$
            entries[i * 2 + 1] = APACHE_POM;
        }
        entries[130] = "META-INF/maven/g/own/pom.xml"; //$NON-NLS-1$
        entries[131] = pom("ACME Commercial License"); //$NON-NLS-1$
        assertEquals(Verdict.UNKNOWN, classify(entries));
    }

    @Test
    public void noEvidenceOrUnreadableJarIsUnknown() throws IOException {
        assertEquals(Verdict.UNKNOWN, classify("a/B.class", "x")); //$NON-NLS-1$ //$NON-NLS-2$
        File dir = DecompilerTestSupport.createTargetTempDir("license-detector-bad"); //$NON-NLS-1$
        try {
            File bad = new File(dir, "bad.jar"); //$NON-NLS-1$
            Files.writeString(bad.toPath(), "not a zip"); //$NON-NLS-1$
            assertEquals(Verdict.UNKNOWN, JarLicenseDetector.classify(bad));
        } finally {
            org.apache.commons.io.FileUtils.deleteQuietly(dir);
        }
    }

    private static String pom(String licenseName) {
        return "<project><licenses><license><name>" + licenseName + "</name></license></licenses></project>"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static Verdict classify(String... nameAndContentPairs) throws IOException {
        File dir = DecompilerTestSupport.createTargetTempDir("license-detector"); //$NON-NLS-1$
        try {
            File jar = new File(dir, "test.jar"); //$NON-NLS-1$
            Map<String, String> entries = new LinkedHashMap<>();
            for (int i = 0; i < nameAndContentPairs.length; i += 2) {
                entries.put(nameAndContentPairs[i], nameAndContentPairs[i + 1]);
            }
            try (OutputStream out = Files.newOutputStream(jar.toPath()); ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return JarLicenseDetector.classify(jar);
        } finally {
            org.apache.commons.io.FileUtils.deleteQuietly(dir);
        }
    }
}
