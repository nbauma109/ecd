/*******************************************************************************
 * Copyright (c) 2026 nbauma109.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.finder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import io.github.nbauma109.decompiler.source.attach.utils.SourceAttachUtil;
import io.github.nbauma109.decompiler.source.attach.utils.UrlDownloader;
import io.github.nbauma109.decompiler.util.Logger;

public class JitPackSourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

    private static final String JITPACK_BASE_URL = "https://jitpack.io/"; //$NON-NLS-1$
    private static final String SOURCES_JAR = "-sources.jar"; //$NON-NLS-1$

    private boolean canceled;

    @Override
    public void cancel() {
        this.canceled = true;
    }

    @Override
    public String toString() {
        return this.getClass().toString();
    }

    @Override
    public void find(String binFile, String sha1, List<SourceFileResult> results) {
        if (canceled) {
            return;
        }

        try {
            findGAVFromFile(binFile).ifPresent(gav -> findByGav(binFile, gav, results));
        } catch (IOException e) {
            Logger.debug(e);
        }
    }

    private void findByGav(String binFile, GAV gav, List<SourceFileResult> results) {
        if (results.isEmpty() && !canceled) {
            String sourceUrl = buildSourceUrl(gav);
            if (sourceUrl == null) {
                return;
            }

            File mavenRepoSourceFile = getMavenRepoSourceFile(gav);

            if (mavenRepoSourceFile != null && mavenRepoSourceFile.exists()) {
                try {
                    if (SourceAttachUtil.isSourceCodeFor(mavenRepoSourceFile.getAbsolutePath(), binFile)) {
                        setDownloadUrl(sourceUrl);
                        results.add(new SourceFileResult(this, binFile, mavenRepoSourceFile, mavenRepoSourceFile, 100));
                        return;
                    }
                } catch (RuntimeException e) {
                    Logger.debug(e);
                }
            }

            if (tryCachedSources(binFile, Collections.singletonMap(gav, sourceUrl), results, true)) {
                setDownloadUrl(sourceUrl);
                return;
            }

            try {
                String downloadedFile = new UrlDownloader().download(sourceUrl, mavenRepoSourceFile);
                if (downloadedFile != null && new File(downloadedFile).exists()
                        && SourceAttachUtil.isSourceCodeFor(downloadedFile, binFile)) {
                    setDownloadUrl(sourceUrl);
                    File sourceFileRef = mavenRepoSourceFile != null ? mavenRepoSourceFile : new File(downloadedFile);
                    results.add(new SourceFileResult(this, binFile, sourceFileRef, sourceFileRef, 100));
                }
            } catch (IOException e) {
                Logger.debug(e);
            }
        }
    }

    private String buildSourceUrl(GAV gav) {
        String groupId = gav.getGroupId();
        String artifactId = gav.getArtifactId();
        String version = gav.getVersion();
        if (groupId == null || artifactId == null || version == null) {
            return null;
        }
        return JITPACK_BASE_URL + groupId.replace('.', '/') + '/' + artifactId + '/' + version + '/' + artifactId
                + '-' + version + SOURCES_JAR;
    }
}
