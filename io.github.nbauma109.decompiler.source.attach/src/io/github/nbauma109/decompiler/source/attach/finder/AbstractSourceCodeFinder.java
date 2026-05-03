/*******************************************************************************
 * (C) 2017 cnfree (@cnfree)
 * (C) 2017 Pascal Bihler
 * (C) 2021 Jan S. (@jpstotz)
 * (C) 2022-2026 Nicolas Baumann (@nbauma109)
 * (C) 2026 Claude (@Claude)
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

import io.github.nbauma109.decompiler.util.EcfHttpClient;
import io.github.nbauma109.decompiler.source.attach.utils.SourceAttachUtil;
import io.github.nbauma109.decompiler.source.attach.utils.SourceBindingUtil;
import io.github.nbauma109.decompiler.source.attach.utils.SourceConstants;
import io.github.nbauma109.decompiler.util.Logger;

public abstract class AbstractSourceCodeFinder implements SourceCodeFinder {

    protected static final String SOURCES_JAR = "-sources.jar"; //$NON-NLS-1$

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
            byte[] bytes;
            String protocol = url.getProtocol().toLowerCase();

            // Use ECF for HTTP/HTTPS with automatic proxy authentication
            if ("http".equals(protocol) || "https".equals(protocol)) {
                EcfHttpClient client = new EcfHttpClient();
                client.setConnectTimeout(5000);
                client.setReadTimeout(5000);
                bytes = client.downloadToBytes(url.toString());
            } else {
                // For file:// and other protocols, use standard URLConnection
                try (InputStream is = url.openStream()) {
                    bytes = IOUtils.toByteArray(is);
                }
            }

            return decompressOrDecode(bytes);
        } catch (IOException e) {
            Logger.debug(e);
        }
        return "";
    }

    private String decompressOrDecode(byte[] bytes) {
        try (InputStream is = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * Calculate the Maven local repository path for a source JAR.
     * Returns null if the GAV coordinates are incomplete or contain path traversal characters.
     */
    protected File getMavenRepoSourceFile(GAV gav) {
        if (gav == null) {
            return null;
        }
        String groupId = gav.getGroupId();
        String artifactId = gav.getArtifactId();
        String version = gav.getVersion();
        if (groupId == null || artifactId == null || version == null) {
            return null;
        }
        if (containsUnsafePathPart(groupId) || containsUnsafePathPart(artifactId) || containsUnsafePathPart(version)) {
            return null;
        }
        String sourceFileName = artifactId + '-' + version + SOURCES_JAR;
        File groupIdDir = new File(SourceConstants.USER_M2_REPO_DIR, groupId.replace('.', File.separatorChar));
        File result = new File(groupIdDir, String.join(File.separator, artifactId, version, sourceFileName));
        try {
            String repoCanonical = SourceConstants.USER_M2_REPO_DIR.getCanonicalPath();
            String resultCanonical = result.getCanonicalPath();
            if (!resultCanonical.startsWith(repoCanonical + File.separator) && !resultCanonical.equals(repoCanonical)) {
                return null;
            }
        } catch (IOException e) {
            Logger.debug(e);
            return null;
        }
        return result;
    }

    private boolean containsUnsafePathPart(String value) {
        return value.contains("..") || value.contains("/") || value.contains("\\"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    protected boolean tryExistingMavenRepoSources(String binFile, Map<GAV, String> sourcesUrls,
            List<SourceFileResult> results) {
        for (Map.Entry<GAV, String> entry : sourcesUrls.entrySet()) {
            File mavenRepoSourceFile = getMavenRepoSourceFile(entry.getKey());
            if (mavenRepoSourceFile != null && mavenRepoSourceFile.exists()
                    && validateAndAddSource(binFile, mavenRepoSourceFile, mavenRepoSourceFile, entry.getValue(), results)) {
                return true;
            }
        }
        return false;
    }

    protected boolean validateAndAddSource(String binFile, File sourceFile, File tempFile,
            String downloadUrl, List<SourceFileResult> results) {
        try {
            if (SourceAttachUtil.isSourceCodeFor(sourceFile.getAbsolutePath(), binFile)) {
                SourceFileResult result = new SourceFileResult(this, binFile, sourceFile, tempFile, 100);
                results.add(result);
                setDownloadUrl(downloadUrl);
                return true;
            }
        } catch (RuntimeException e) {
            Logger.debug(e);
        }
        return false;
    }

    protected File getMavenRepoTargetForDownload(GAV gav, String downloadUrl) {
        if (isSourceDownloadUrl(downloadUrl)) {
            return getMavenRepoSourceFile(gav);
        }
        return null;
    }

    private boolean isSourceDownloadUrl(String downloadUrl) {
        return downloadUrl != null && (downloadUrl.endsWith(SOURCES_JAR)
                || downloadUrl.contains("filepath=") && downloadUrl.contains(SOURCES_JAR) //$NON-NLS-1$
                || downloadUrl.contains("c=sources") //$NON-NLS-1$
                || downloadUrl.contains("classifier=sources")); //$NON-NLS-1$
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
        return tryCachedSources(binFile, sourcesUrls, results, false);
    }

    protected boolean tryCachedSources(String binFile, Map<GAV, String> sourcesUrls, List<SourceFileResult> results,
            boolean persistInMavenRepo) {
        for (Map.Entry<GAV, String> entry : sourcesUrls.entrySet()) {
            try {
                SourceFileResult result = buildCachedSourceResult(binFile, entry, persistInMavenRepo);
                if (result != null) {
                    results.add(result);
                    setDownloadUrl(entry.getValue());
                    return true;
                }
            } catch (Exception e) {
                Logger.debug(e);
            }
        }
        return false;
    }

    private SourceFileResult buildCachedSourceResult(String binFile, Map.Entry<GAV, String> entry,
            boolean persistInMavenRepo) {
        String[] sourceFiles = SourceBindingUtil.getSourceFileByDownloadUrl(entry.getValue());
        if (sourceFiles == null || sourceFiles[0] == null || !new File(sourceFiles[0]).exists()) {
            return null;
        }
        File sourceFile = new File(sourceFiles[0]);
        File tempFile = sourceFiles.length > 1 && sourceFiles[1] != null ? new File(sourceFiles[1]) : sourceFile;
        if (persistInMavenRepo) {
            File mavenRepoSourceFile = persistCachedSourceInMavenRepo(entry.getKey(), entry.getValue(), sourceFile, binFile);
            if (mavenRepoSourceFile != null) {
                sourceFile = mavenRepoSourceFile;
                tempFile = mavenRepoSourceFile;
            }
        }
        return new SourceFileResult(this, binFile, sourceFile, tempFile, 100);
    }

    protected File persistCachedSourceInMavenRepo(GAV gav, String downloadUrl, File sourceFile, String binFile) {
        File mavenRepoSourceFile = getMavenRepoTargetForDownload(gav, downloadUrl);
        if (mavenRepoSourceFile == null) {
            return null;
        }
        if (mavenRepoSourceFile.exists() && SourceAttachUtil.isSourceCodeFor(mavenRepoSourceFile.getAbsolutePath(), binFile)) {
            return mavenRepoSourceFile;
        }
        if (mavenRepoSourceFile.exists()) {
            Logger.debug("Replacing stale/invalid Maven repo source: " + mavenRepoSourceFile.getAbsolutePath(), null); //$NON-NLS-1$
        }
        if (!SourceAttachUtil.isSourceCodeFor(sourceFile.getAbsolutePath(), binFile)) {
            return null;
        }
        try {
            File parent = mavenRepoSourceFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                return null;
            }
            Files.copy(sourceFile.toPath(), mavenRepoSourceFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return mavenRepoSourceFile;
        } catch (IOException e) {
            Logger.debug(e);
            return null;
        }
    }
}
