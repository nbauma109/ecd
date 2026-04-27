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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.net.proxy.IProxyService;

import io.github.nbauma109.decompiler.source.attach.SourceAttachPlugin;
import io.github.nbauma109.decompiler.source.attach.utils.ProxyUtil;
import io.github.nbauma109.decompiler.source.attach.utils.SourceAttachUtil;
import io.github.nbauma109.decompiler.source.attach.utils.UrlDownloader;
import io.github.nbauma109.decompiler.util.Logger;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public class SonatypeSourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

    private static final String SOURCES_JAR = "-sources.jar"; //$NON-NLS-1$
    private static final String SONATYPE_BROWSE_COMPONENTS_API = "https://central.sonatype.com/api/internal/browse/components"; //$NON-NLS-1$
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

        // Try cached sources first
        if (tryCachedSources(binFile, sourcesUrls, results)) {
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
            String name = entry.getKey().getArtifactId() + '-' + entry.getKey().getVersion() + SOURCES_JAR;
            try {
                String tmpFile = new UrlDownloader().download(entry.getValue());
                if (tmpFile != null && new File(tmpFile).exists()
                        && SourceAttachUtil.isSourceCodeFor(tmpFile, binFile)) {
                    setDownloadUrl(entry.getValue());
                    SourceFileResult object = new SourceFileResult(this, binFile, tmpFile, name, 100);
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

        JsonArray componentArray = fetchComponents(sha1);
        extractGAVsFromComponents(componentArray, results);
        return results;
    }

    private JsonArray fetchComponents(String sha1) throws IOException {
        String payload = "{\"size\":" + PAGE_SIZE + ",\"searchTerm\":\"1:" //$NON-NLS-1$ //$NON-NLS-2$
                + sha1.toLowerCase(Locale.ROOT) + "\",\"filter\":[]}"; //$NON-NLS-1$
        JsonObject response = Json.parse(postJson(SONATYPE_BROWSE_COMPONENTS_API, payload)).asObject();
        JsonValue components = response.get("components"); //$NON-NLS-1$

        if (components == null || !components.isArray()) {
            return new JsonArray();
        }
        return components.asArray();
    }

    private void extractGAVsFromComponents(JsonArray componentArray, Set<GAV> results) {
        for (JsonValue value : componentArray) {
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
        if (version.isBlank()) {
            JsonValue latestVersionInfo = component.get("latestVersionInfo"); //$NON-NLS-1$
            if (latestVersionInfo != null && latestVersionInfo.isObject()) {
                version = latestVersionInfo.asObject().getString("version", ""); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        return version;
    }

    private String postJson(String apiUrl, String payload) throws IOException {
        // Get Eclipse proxy service via activator (ServiceTracker, no OSGi service leak)
        SourceAttachPlugin defaultPlugin = SourceAttachPlugin.getDefault();
        IProxyService proxyService = defaultPlugin != null ? defaultPlugin.getProxyService() : null;

        // Open connection with proxy support
        HttpURLConnection connection;
        try {
            URI uri = new URI(apiUrl);
            Proxy proxy = ProxyUtil.getProxy(uri, proxyService);
            connection = (HttpURLConnection) new URL(apiUrl).openConnection(proxy);
        } catch (Exception e) {
            Logger.debug(e);
            connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        }

        connection.setRequestMethod("POST"); //$NON-NLS-1$
        connection.setRequestProperty("Content-Type", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
        connection.setRequestProperty("Accept", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream is = connection.getInputStream()) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    private String buildSourcesUrl(GAV gav) {
        return MAVEN_CENTRAL_BASE_URL
                + gav.getGroupId().replace('.', '/')
                + '/' + gav.getArtifactId()
                + '/' + gav.getVersion()
                + '/' + gav.getArtifactId() + '-' + gav.getVersion() + SOURCES_JAR;
    }
}
