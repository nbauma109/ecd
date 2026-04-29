/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.finder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.core.net.proxy.IProxyService;

import io.github.nbauma109.decompiler.source.attach.SourceAttachPlugin;
import io.github.nbauma109.decompiler.source.attach.utils.ProxyUtil;
import io.github.nbauma109.decompiler.source.attach.utils.SourceAttachUtil;
import io.github.nbauma109.decompiler.source.attach.utils.UrlDownloader;
import io.github.nbauma109.decompiler.util.Logger;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public class ArtifactorySourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

    protected boolean canceled = false;
    private String serviceUrl;

    public ArtifactorySourceCodeFinder(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    @Override
    public String toString() {
        return this.getClass() + "; serviceUrl=" + serviceUrl; //$NON-NLS-1$
    }

    @Override
    public void cancel() {
        this.canceled = true;
    }

    @Override
    public void find(String binFile, String sha1, List<SourceFileResult> results) {
        Collection<GAV> gavs = new HashSet<>();
        try {
            gavs.addAll(findArtifactsUsingArtifactory(null, null, null, null, sha1, false));
        } catch (Throwable e) {
            Logger.debug(e);
        }

        if (canceled) {
            return;
        }

        if (gavs.isEmpty()) {
            try {
                findGAVFromFile(binFile).ifPresent(gavs::add);
            } catch (Throwable e) {
                Logger.debug(e);
            }
        }

        if (canceled) {
            return;
        }

        Map<GAV, String> sourcesUrls = new HashMap<>();
        try {
            sourcesUrls.putAll(findSourcesUsingArtifactory(gavs));
        } catch (Throwable e) {
            Logger.debug(e);
        }

        if (tryExistingMavenRepoSources(binFile, sourcesUrls, results)) {
            return;
        }

        if (tryCachedSources(binFile, sourcesUrls, results, true)) {
            return;
        }

        for (Map.Entry<GAV, String> entry : sourcesUrls.entrySet()) {
            File mavenRepoSourceFile = getMavenRepoTargetForDownload(entry.getKey(), entry.getValue());
            try {
                String result = new UrlDownloader().download(entry.getValue(), mavenRepoSourceFile);
                if (result != null && new File(result).exists() && SourceAttachUtil.isSourceCodeFor(result, binFile)) {
                    setDownloadUrl(entry.getValue());
                    File downloadedFile = new File(result);
                    SourceFileResult object;
                    if (mavenRepoSourceFile != null) {
                        object = new SourceFileResult(this, binFile, mavenRepoSourceFile, mavenRepoSourceFile, 100);
                    } else {
                        String suggestedName = entry.getKey().getArtifactId() + '-' + entry.getKey().getVersion() + "-sources.jar"; //$NON-NLS-1$
                        object = new SourceFileResult(this, binFile, downloadedFile.getAbsolutePath(), suggestedName, 100);
                    }
                    Logger.debug(this.toString() + " FOUND: " + object, null); //$NON-NLS-1$
                    results.add(object);
                }
            } catch (Throwable e) {
                Logger.debug(e);
            }
        }
    }

    protected Map<GAV, String> findSourcesUsingArtifactory(Collection<GAV> gavs) throws IOException {
        Map<GAV, String> results = new HashMap<>();
        for (GAV gav : gavs) {
            if (canceled) {
                return results;
            }
            Set<GAV> gavs2 = findArtifactsUsingArtifactory(gav.getGroupId(), gav.getArtifactId(), gav.getVersion(),
                    "sources", null, true); //$NON-NLS-1$
            for (GAV gav2 : gavs2) {
                if (gav2.getArtifactLink().endsWith("-sources.jar") //$NON-NLS-1$
                        || gav2.getArtifactLink().endsWith("-sources.zip")) //$NON-NLS-1$
                {
                    String uri = gav2.getArtifactLink();
                    File file = new File(new UrlDownloader().download(uri));
                    String json = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                    JsonObject resp = Json.parse(json).asObject();
                    results.put(gav, resp.getString("downloadUri", "")); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
        }

        return results;
    }

    protected Set<GAV> findArtifactsUsingArtifactory(String g, String a, String v, String c, String sha1,
            boolean getLink) throws IOException {
        Set<GAV> results = new HashSet<>();
        String apiUrl = getArtifactApiUrl();
        String url = buildSearchUrl(apiUrl, g, a, v, c, sha1);

        // Get Eclipse proxy service via activator (ServiceTracker, no OSGi service leak)
        SourceAttachPlugin defaultPlugin = SourceAttachPlugin.getDefault();
        IProxyService proxyService = defaultPlugin != null ? defaultPlugin.getProxyService() : null;

        // Open connection with proxy support
        URLConnection connection;
        try {
            URI uri = new URI(url);
            Proxy proxy = ProxyUtil.getProxy(uri, proxyService);
            connection = uri.toURL().openConnection(proxy);
        } catch (URISyntaxException | IOException e) {
            Logger.debug(e);
            connection = URI.create(url).toURL().openConnection();
        }

        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.connect();
        try {
            String json = readJsonResponse(connection);
            JsonObject resp = Json.parse(json).asObject();
            processSearchResults(resp, getLink, results);
        } catch (Throwable e) {
            Logger.debug(e);
        }

        return results;
    }

    private String buildSearchUrl(String apiUrl, String g, String a, String v, String c, String sha1) {
        if (sha1 != null) {
            return apiUrl + "search/checksum?sha1=" + sha1; //$NON-NLS-1$
        }
        return apiUrl + "search/gavc?g=" + g + "&a=" + a + "&v=" + v + (c != null ? "&c=" + c : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

    private String readJsonResponse(URLConnection connection) throws IOException {
        try (InputStream is = connection.getInputStream()) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    private void processSearchResults(JsonObject resp, boolean getLink, Set<GAV> results) {
        for (JsonValue elem : resp.get("results").asArray()) { //$NON-NLS-1$
            JsonObject result = elem.asObject();
            String uri = result.getString("uri", ""); //$NON-NLS-1$ //$NON-NLS-2$
            GAV gav = parseGAVFromUri(uri, getLink);
            if (gav != null) {
                results.add(gav);
            }
        }
    }

    private GAV parseGAVFromUri(String uri, boolean getLink) {
        String regex = "/api/storage/[^/]+/(.+)$"; //$NON-NLS-1$
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(uri);
        if (!matcher.find()) {
            return null;
        }

        String[] gavInArray = matcher.group(1).split("/"); //$NON-NLS-1$
        if (gavInArray.length < 3) {
            return null;
        }

        GAV gav = new GAV();
        gav.setGroupId(buildGroupId(gavInArray));
        gav.setArtifactId(gavInArray[gavInArray.length - 3]);
        gav.setVersion(gavInArray[gavInArray.length - 2]);

        if (getLink) {
            gav.setArtifactLink(uri);
        }
        return gav;
    }

    private String buildGroupId(String[] gavInArray) {
        StringBuilder group = new StringBuilder().append(gavInArray[0]);
        for (int i = 1; i < gavInArray.length - 3; i++) {
            group.append(".").append(gavInArray[i]); //$NON-NLS-1$
        }
        return group.toString();
    }

    private String getArtifactApiUrl() {
        String result = null;
        if (serviceUrl.endsWith("/webapp/home.html")) //$NON-NLS-1$
        {
            result = serviceUrl.replace("/webapp/home.html", "/api/"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return result;
    }
}
