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
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.nbauma109.decompiler.util.EcfHttpClient;
import io.github.nbauma109.decompiler.source.attach.utils.SourceAttachUtil;
import io.github.nbauma109.decompiler.source.attach.utils.UrlDownloader;
import io.github.nbauma109.decompiler.util.Logger;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public class SonatypeSourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

    private static final String SOURCES_JAR = "-sources.jar"; //$NON-NLS-1$
    private static final String SONATYPE_BROWSE_COMPONENTS_API =
            "https://central.sonatype.com/api/internal/browse/components"; //$NON-NLS-1$
    private static final String MAVEN_CENTRAL_BASE_URL = "https://repo1.maven.org/maven2/"; //$NON-NLS-1$
    private static final int PAGE_SIZE = 20;

    private boolean canceled = false;

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
        Collection<GAV> gavs = fetchGAVs(binFile, sha1);
        if (canceled || gavs.isEmpty()) {
            return;
        }

        Map<GAV, String> sourcesUrls = buildSourcesUrls(gavs);

        // Try existing Maven repo files first
        if (tryExistingMavenRepoSources(binFile, sourcesUrls, results)) {
            return;
        }

        // Try cached sources first
        if (tryCachedSources(binFile, sourcesUrls, results, true)) {
            return;
        }

        // Download and verify sources
        downloadAndVerifySources(binFile, sourcesUrls, results);
    }

    private Collection<GAV> fetchGAVs(String binFile, String sha1) {
        Collection<GAV> gavs = new HashSet<>();
        try {
            gavs.addAll(findArtifactsUsingSonatype(sha1));
        } catch (IOException e) {
            Logger.debug(e);
        }

        if (canceled) {
            return gavs;
        }

        if (gavs.isEmpty()) {
            try {
                findGAVFromFile(binFile).ifPresent(gavs::add);
            } catch (Throwable e) {
                Logger.debug(e);
            }
        }
        return gavs;
    }

    private Map<GAV, String> buildSourcesUrls(Collection<GAV> gavs) {
        Map<GAV, String> sourcesUrls = new HashMap<>();
        for (GAV gav : gavs) {
            if (gav != null && gav.isValid()) {
                sourcesUrls.put(gav, buildSourcesUrl(gav));
            }
        }
        return sourcesUrls;
    }

    private void downloadAndVerifySources(String binFile, Map<GAV, String> sourcesUrls, List<SourceFileResult> results) {
        for (Map.Entry<GAV, String> entry : sourcesUrls.entrySet()) {
            File mavenRepoSourceFile = getMavenRepoTargetForDownload(entry.getKey(), entry.getValue());
            try {
                String tmpFile = new UrlDownloader().download(entry.getValue(), mavenRepoSourceFile);
                if (tmpFile != null && new File(tmpFile).exists()
                        && SourceAttachUtil.isSourceCodeFor(tmpFile, binFile)) {
                    setDownloadUrl(entry.getValue());
                    File downloadedFile = new File(tmpFile);
                    SourceFileResult object;
                    if (mavenRepoSourceFile != null) {
                        object = new SourceFileResult(this, binFile, mavenRepoSourceFile, mavenRepoSourceFile, 100);
                    } else {
                        String suggestedName = entry.getKey().getArtifactId() + '-' + entry.getKey().getVersion() + SOURCES_JAR;
                        object = new SourceFileResult(this, binFile, downloadedFile.getAbsolutePath(), suggestedName, 100);
                    }
                    Logger.debug(this.toString() + " FOUND: " + object, null); //$NON-NLS-1$
                    results.add(object);
                    return;
                }
            } catch (IOException e) {
                Logger.debug(e);
            }
        }
    }

    private Collection<GAV> findArtifactsUsingSonatype(String sha1) throws IOException {
        Set<GAV> results = new HashSet<>();
        if (sha1 == null || sha1.isBlank()) {
            return results;
        }

        JsonArray components = fetchComponents(sha1);
        extractGAVsFromComponents(components, results);
        return results;
    }

    private JsonArray fetchComponents(String sha1) throws IOException {
        String payload = "{\"size\":" + PAGE_SIZE //$NON-NLS-1$
                + ",\"searchTerm\":\"1:" + sha1.toLowerCase(Locale.ROOT) //$NON-NLS-1$
                + "\",\"filter\":[]}"; //$NON-NLS-1$
        String json = postJson(SONATYPE_BROWSE_COMPONENTS_API, payload);
        try {
            JsonObject response = Json.parse(json).asObject();
            JsonValue components = response.get("components"); //$NON-NLS-1$
            if (components == null || !components.isArray()) {
                return new JsonArray();
            }
            return components.asArray();
        } catch (RuntimeException e) {
            Logger.debug(e);
            return new JsonArray();
        }
    }

    private void extractGAVsFromComponents(JsonArray components, Set<GAV> results) {
        for (JsonValue value : components) {
            if (value == null || !value.isObject()) {
                continue;
            }
            JsonObject component = value.asObject();
            GAV gav = parseGAVFromComponent(component);
            if (gav != null) {
                results.add(gav);
            }
        }
    }

    private GAV parseGAVFromComponent(JsonObject component) {
        String namespace = component.getString("namespace", ""); //$NON-NLS-1$ //$NON-NLS-2$
        String artifactId = component.getString("name", ""); //$NON-NLS-1$ //$NON-NLS-2$
        String version = getVersionFromComponent(component);

        if (!namespace.isBlank() && !artifactId.isBlank() && !version.isBlank()) {
            GAV gav = new GAV();
            gav.setGroupId(namespace);
            gav.setArtifactId(artifactId);
            gav.setVersion(version);
            return gav;
        }
        return null;
    }

    private String getVersionFromComponent(JsonObject component) {
        String version = component.getString("version", ""); //$NON-NLS-1$ //$NON-NLS-2$
        if (!version.isBlank()) {
            return version;
        }

        JsonValue latestVersionInfo = component.get("latestVersionInfo"); //$NON-NLS-1$
        if (latestVersionInfo != null && latestVersionInfo.isObject()) {
            return latestVersionInfo.asObject().getString("version", ""); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return ""; //$NON-NLS-1$
    }

    private String postJson(String apiUrl, String payload) throws IOException {
        EcfHttpClient client = new EcfHttpClient();
        client.setConnectTimeout(5000);
        client.setReadTimeout(5000);

        byte[] responseBytes = client.postJson(apiUrl, payload);
        return new String(responseBytes, StandardCharsets.UTF_8);
    }

    private String buildSourcesUrl(GAV gav) {
        return MAVEN_CENTRAL_BASE_URL
                + gav.getGroupId().replace('.', '/')
                + '/' + gav.getArtifactId()
                + '/' + gav.getVersion()
                + '/' + gav.getArtifactId() + '-' + gav.getVersion() + SOURCES_JAR;
    }
}
