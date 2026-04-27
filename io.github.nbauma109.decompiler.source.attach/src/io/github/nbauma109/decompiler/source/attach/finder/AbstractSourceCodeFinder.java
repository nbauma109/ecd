/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.finder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.net.proxy.IProxyService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import io.github.nbauma109.decompiler.source.attach.utils.ProxyUtil;
import io.github.nbauma109.decompiler.source.attach.utils.SourceBindingUtil;
import io.github.nbauma109.decompiler.util.Logger;

public abstract class AbstractSourceCodeFinder implements SourceCodeFinder {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36"; //$NON-NLS-1$

    protected String downloadUrl;

    @Override
    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    protected String getString(URL url) {
        try {
            // Get Eclipse proxy service
            IProxyService proxyService = getProxyService();

            // Open connection with proxy support
            URLConnection con;
            if (proxyService != null) {
                try {
                    URI uri = url.toURI();
                    Proxy proxy = ProxyUtil.getProxy(uri, proxyService);
                    con = url.openConnection(proxy);
                } catch (Exception e) {
                    Logger.debug(e);
                    con = url.openConnection();
                }
            } else {
                con = url.openConnection();
            }

            con.setRequestProperty("User-Agent", USER_AGENT);//$NON-NLS-1$
            con.setRequestProperty("Accept-Encoding", "gzip,deflate"); //$NON-NLS-1$ //$NON-NLS-2$
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            byte[] bytes = null;
            try {
                try (InputStream conIs = con.getInputStream()) {
                    bytes = IOUtils.toByteArray(conIs);
                }
                try (InputStream is = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                    return IOUtils.toString(is, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                if (bytes != null) {
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            Logger.debug(e);
        }
        return "";
    }

    /**
     * Gets the Eclipse proxy service if available.
     *
     * @return the proxy service or null if not available
     */
    protected IProxyService getProxyService() {
        try {
            BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
            if (bundleContext != null) {
                ServiceReference<IProxyService> serviceReference = bundleContext.getServiceReference(IProxyService.class);
                if (serviceReference != null) {
                    return bundleContext.getService(serviceReference);
                }
            }
        } catch (Exception e) {
            Logger.debug(e);
        }
        return null;
    }

    protected Optional<GAV> findGAVFromFile(String binFile) throws IOException {
        Set<GAV> gavs = new HashSet<>();

        // META-INF/maven/commons-beanutils/commons-beanutils/pom.properties
        try (ZipFile zipFile = new ZipFile(new File(binFile))) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry == null) {
                    break;
                }

                String zipEntryName = entry.getName();
                if (zipEntryName.startsWith("META-INF/maven/") && zipEntryName.endsWith("/pom.properties")) //$NON-NLS-1$ //$NON-NLS-2$
                {
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        Properties props = new Properties();
                        props.load(in);
                        String version = props.getProperty("version"); //$NON-NLS-1$
                        String groupId = props.getProperty("groupId"); //$NON-NLS-1$
                        String artifactId = props.getProperty("artifactId"); //$NON-NLS-1$
                        if (version != null && groupId != null && artifactId != null) {
                            GAV gav = new GAV();
                            gav.setGroupId(groupId);
                            gav.setArtifactId(artifactId);
                            gav.setVersion(version);
                            gavs.add(gav);
                        }
                    }
                }
            }
        }

        if (gavs.size() > 1) {
            gavs.clear(); // a merged file, the result will not be correct
        }
        return gavs.stream().findFirst();
    }

    protected String getText(HTMLDocument doc, HTMLDocument.Iterator iterator) throws BadLocationException {
        int startOffset = iterator.getStartOffset();
        int endOffset = iterator.getEndOffset();
        int length = endOffset - startOffset;
        return doc.getText(startOffset, length);
    }

    /**
     * Try to use cached source files if available.
     * @return true if a cached source was found and added to results
     */
    protected boolean tryCachedSources(String binFile, Map<GAV, String> sourcesUrls, List<SourceFileResult> results) {
        for (Map.Entry<GAV, String> entry : sourcesUrls.entrySet()) {
            try {
                String[] sourceFiles = SourceBindingUtil.getSourceFileByDownloadUrl(entry.getValue());
                if (sourceFiles != null && sourceFiles[0] != null && new File(sourceFiles[0]).exists()) {
                    File sourceFile = new File(sourceFiles[0]);
                    File tempFile = new File(sourceFiles[1]);
                    SourceFileResult result = new SourceFileResult(this, binFile, sourceFile, tempFile, 100);
                    results.add(result);
                    return true;
                }
            } catch (Exception e) {
                Logger.debug(e);
            }
        }
        return false;
    }
}
