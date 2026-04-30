/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
/*******************************************************************************
 * Nexus source finder for Nexus Repository Manager 2 and 3.
 *
 * Responsibilities:
 * - Detect whether the target base URL serves Nexus 2 or Nexus 3.
 * - Resolve artifacts from a SHA-1 checksum using the appropriate public APIs.
 * - Prefer source artifacts when available, otherwise fall back to the main JAR.
 * - Generate download URLs that work even when repositories are not exposed for browsing.
 * - Normalize scheme and port mismatches such as http on port 443.
 *
 * Design notes:
 * - JSON parsing relies exclusively on com.eclipsesource.minimal-json.
 * - For Nexus 3, the code queries /service/rest/v1/search/assets and then
 *   falls back to /service/rest/v1/search if the asset endpoint returns no items.
 * - For Nexus 2, the code first queries /service/local/identify/sha1/{sha1}
 *   and then falls back to /service/local/lucene/search?sha1=..., building
 *   final download links with /service/local/artifact/maven/redirect to avoid
 *   direct /content links when a repository exists but is not exposed.
 * - The first candidate that verifies against the target binary is returned.
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.finder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.github.nbauma109.decompiler.source.attach.utils.EcfHttpClient;
import io.github.nbauma109.decompiler.source.attach.utils.SourceAttachUtil;
import io.github.nbauma109.decompiler.source.attach.utils.UrlDownloader;
import io.github.nbauma109.decompiler.util.HashUtils;
import io.github.nbauma109.decompiler.util.Logger;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public class NexusSourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

    private static final String SOURCES_JAR = "-sources.jar"; //$NON-NLS-1$
    private static final String VERSION_FIELD = "version"; //$NON-NLS-1$

    // ---------------------------------------------------------------------------------
    // Configuration supplied by the caller
    // ---------------------------------------------------------------------------------
    private final String serviceUrl;      // Base URL, for example: https://repo.example.com (with or without /nexus)
    private final String serviceUser;     // May be null for anonymous access
    private final String servicePassword; // May be null
    private final boolean bypassProxy;

    // ---------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------
    private volatile boolean canceled = false;

    // ---------------------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------------------
    public NexusSourceCodeFinder(String serviceUrl) {
        this(serviceUrl, null, null, false);
    }

    public NexusSourceCodeFinder(String serviceUrl, String user, String password) {
        this(serviceUrl, user, password, true);
    }

    public NexusSourceCodeFinder(String serviceUrl, String user, String password, boolean bypassProxy) {
        this.serviceUrl = trimTrailingSlash(Objects.requireNonNull(serviceUrl, "serviceUrl"));
        this.serviceUser = user;
        this.servicePassword = password;
        this.bypassProxy = bypassProxy;
    }

    @Override
    public void cancel() {
        this.canceled = true;
    }

    @Override
    public String toString() {
        return "NexusSourceCodeFinder{serviceUrl=" + serviceUrl + "}";
    }

    // ---------------------------------------------------------------------------------
    // Main entry
    // ---------------------------------------------------------------------------------
    @Override
    public void find(String binFile, String sha1, List<SourceFileResult> results) {
        if (canceled) {
            return;
        }

        // If SHA-1 is not provided, compute it from the binary file
        String targetSha1 = sha1;
        if (targetSha1 == null && binFile != null) {
            try {
                targetSha1 = HashUtils.sha1Hash(new File(binFile));
            } catch (Throwable t) {
                Logger.debug(t);
            }
        }
        if (targetSha1 == null) {
            return;
        }

        // Detect Nexus family
        Detection d = detectFamily();
        if (canceled) {
            return;
        }

        // Collect candidate download URLs, keyed by GAV; prefer sources when available
        Map<GAV, String> candidates = new LinkedHashMap<>();

        try {
            switch (d.family) {
                case NXRM3:
                    candidates.putAll(nx3SearchBySha1(d.base, targetSha1));
                    break;
                case NXRM2:
                    candidates.putAll(nx2SearchBySha1(d.base, targetSha1));
                    break;
                case UNKNOWN:
                    // Try both base and base+/nexus for Nexus 3 first, then Nexus 2
                    String withNexus = ensureWithNexus(d.base);
                    candidates.putAll(nx3SearchBySha1(d.base, targetSha1));
                    if (candidates.isEmpty()) {
                        candidates.putAll(nx3SearchBySha1(withNexus, targetSha1));
                    }
                    if (candidates.isEmpty()) {
                        candidates.putAll(nx2SearchBySha1(d.base, targetSha1));
                        if (candidates.isEmpty()) {
                            candidates.putAll(nx2SearchBySha1(withNexus, targetSha1));
                        }
                    }
                    break;
            }
        } catch (Throwable t) {
            Logger.debug(t);
        }

        if (canceled || candidates.isEmpty()) {
            return;
        }

        if (tryExistingMavenRepoSources(binFile, candidates, results)) {
            return;
        }

        if (tryCachedSources(binFile, candidates, results, true)) {
            return;
        }

        // Download and verify each candidate. The first verified match wins.
        UrlDownloader downloader = new UrlDownloader();
        downloader.setServiceUser(serviceUser);
        downloader.setServicePassword(servicePassword);
        downloader.setNoProxy(bypassProxy);

        for (Map.Entry<GAV, String> e : candidates.entrySet()) {
            if (canceled) {
                return;
            }
            String downloadUrl = normalizeSchemeByPort(e.getValue()); // normalize odd http://...:443/... links
            try {
                File mavenRepoSourceFile = getMavenRepoTargetForDownload(e.getKey(), downloadUrl);
                String tmp = downloader.download(downloadUrl, mavenRepoSourceFile);
                if (tmp != null && new File(tmp).exists() && SourceAttachUtil.isSourceCodeFor(tmp, binFile)) {
                    setDownloadUrl(downloadUrl);
                    File downloadedFile = new File(tmp);
                    SourceFileResult result;
                    if (mavenRepoSourceFile != null) {
                        result = new SourceFileResult(this, binFile, mavenRepoSourceFile, mavenRepoSourceFile, 100);
                    } else {
                        String suggestedName = e.getKey().getArtifactId() + '-' + e.getKey().getVersion() + SOURCES_JAR;
                        result = new SourceFileResult(this, binFile, downloadedFile.getAbsolutePath(), suggestedName, 100);
                    }
                    results.add(result);
                    return; // stop after the first verified match
                }
            } catch (Throwable ex) {
                Logger.debug(ex);
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Detection
    // ---------------------------------------------------------------------------------
    private enum NexusFamily { NXRM3, NXRM2, UNKNOWN }

    private static final class Detection {
        final NexusFamily family;
        final String base;
        Detection(NexusFamily family, String base) { this.family = family; this.base = base; }
    }

    private Detection detectFamily() {
        if (canceled) {
            return new Detection(NexusFamily.UNKNOWN, serviceUrl);
        }

        // Try as provided
        NexusFamily fam = probeFamily(serviceUrl);
        if (fam != NexusFamily.UNKNOWN) {
            return new Detection(fam, serviceUrl);
        }

        // Try with /nexus appended
        String withNexus = ensureWithNexus(serviceUrl);
        fam = probeFamily(withNexus);
        if (fam != NexusFamily.UNKNOWN) {
            return new Detection(fam, withNexus);
        }

        return new Detection(NexusFamily.UNKNOWN, serviceUrl);
    }

    private NexusFamily probeFamily(String base) {
        if (canceled) {
            return NexusFamily.UNKNOWN;
        }
        try {
            int c3 = httpStatusCode(base + "/service/rest/v1/status");
            if (c3 == 200) {
                return NexusFamily.NXRM3;
            }
        } catch (Throwable ignored) {}
        if (canceled) {
            return NexusFamily.UNKNOWN;
        }
        try {
            int c2 = httpStatusCode(base + "/service/local/status");
            if (c2 == 200) {
                return NexusFamily.NXRM2;
            }
        } catch (Throwable ignored) {}
        return NexusFamily.UNKNOWN;
    }

    // ---------------------------------------------------------------------------------
    // Nexus 3 search (JSON)
    // ---------------------------------------------------------------------------------
    /**
     * Searches Nexus 3 by SHA-1. It tries asset search first, then component search.
     * The result map prefers a sources jar when available, but also includes the main jar as a fallback.
     */
    private Map<GAV, String> nx3SearchBySha1(String base, String sha1) {
        Map<GAV, String> out = new LinkedHashMap<>();
        if (canceled) {
            return out;
        }

        // 1) Asset search: /service/rest/v1/search/assets?sha1=...
        boolean any = nx3SearchAssets(base, sha1, out);

        // 2) Fallback to component search if asset search did not return anything
        if (!any) {
            nx3SearchComponents(base, sha1, out);
        }

        // 3) If we only have main jars, try to synthesize a "-sources.jar" in the same directory
        Map<GAV, String> upgraded = new LinkedHashMap<>();
        for (Map.Entry<GAV, String> e : out.entrySet()) {
            String url = e.getValue();
            if (url != null && url.endsWith(".jar") && !url.endsWith(SOURCES_JAR)) {
                String candidate = url.substring(0, url.length() - 4) + SOURCES_JAR;
                candidate = normalizeSchemeByPort(candidate);
                if (httpExists(candidate)) {
                    upgraded.put(e.getKey(), candidate);
                    continue;
                }
            }
            upgraded.put(e.getKey(), url);
        }
        return upgraded;
    }

    private boolean nx3SearchAssets(String base, String sha1, Map<GAV, String> sink) {
        boolean any = false;
        String next = null;

        do {
            StringBuilder u = new StringBuilder(base)
                    .append("/service/rest/v1/search/assets?sha1=")
                    .append(urlenc(sha1));
            if (next != null) {
                u.append("&continuationToken=").append(urlenc(next));
            }

            JsonObject root = getJson(u.toString());
            if (root == null) {
                break;
            }

            JsonArray items = jsonArray(root, "items");
            for (JsonValue v : items) {
                if (canceled) {
                    return any;
                }
                JsonObject it = v.asObject();
                String download = jsonString(it, "downloadUrl");
                download = normalizeSchemeByPort(download); // normalize if the server returned http on port 443
                String path = jsonString(it, "path");
                if (download == null || path == null) {
                    continue;
                }

                Optional<GAV> gavOpt = parseMavenPathToGav(path);
                GAV gav = gavOpt.orElseGet(GAV::new);
                if (!gavOpt.isPresent()) {
                    // Best effort if the path is not Maven-like: keep empty GAV but allow dedup by link
                    gav.setArtifactLink(download);
                }
                // Prefer sources
                boolean isSources = path.endsWith(SOURCES_JAR) || path.endsWith(".src.zip");
                if (isSources || !sink.containsKey(gav)) {
                    sink.put(gav, download);
                }
                any = true;
            }

            JsonValue cont = root.get("continuationToken");
            next = (cont == null || cont.isNull()) ? null : cont.asString();
        } while (next != null);

        return any;
    }

    private void nx3SearchComponents(String base, String sha1, Map<GAV, String> sink) {
        String next = null;

        do {
            String url = buildSearchUrl(base, sha1, next);
            JsonObject root = getJson(url);
            if (root == null) {
                break;
            }

            if (processComponentItems(root, sink)) {
                return;
            }

            JsonValue cont = root.get("continuationToken");
            next = (cont == null || cont.isNull()) ? null : cont.asString();
        } while (next != null);
    }

    private String buildSearchUrl(String base, String sha1, String continuationToken) {
        StringBuilder u = new StringBuilder(base)
                .append("/service/rest/v1/search?sha1=")
                .append(urlenc(sha1));
        if (continuationToken != null) {
            u.append("&continuationToken=").append(urlenc(continuationToken));
        }
        return u.toString();
    }

    private boolean processComponentItems(JsonObject root, Map<GAV, String> sink) {
        JsonArray items = jsonArray(root, "items");
        for (JsonValue v : items) {
            if (canceled) {
                return true;
            }
            JsonObject comp = v.asObject();
            processComponent(comp, sink);
        }
        return false;
    }

    private void processComponent(JsonObject comp, Map<GAV, String> sink) {
        String group = jsonString(comp, "group");
        String name = jsonString(comp, "name");
        String version = jsonString(comp, VERSION_FIELD);

        Set<String> assetUrls = extractAssetUrls(comp);

        if (!assetUrls.isEmpty()) {
            GAV gav = createGAV(group, name, version);
            String chosen = chooseSourcesFirst(assetUrls);
            sink.compute(gav, (k, v) -> v == null || chosen.endsWith(SOURCES_JAR) ? chosen : v);
        }
    }

    private Set<String> extractAssetUrls(JsonObject comp) {
        Set<String> assetUrls = new LinkedHashSet<>();
        JsonArray assets = jsonArray(comp, "assets");
        for (JsonValue av : assets) {
            JsonObject a = av.asObject();
            String download = jsonString(a, "downloadUrl");
            download = normalizeSchemeByPort(download);
            String path = jsonString(a, "path");
            if (download != null && path != null) {
                assetUrls.add(download);
            }
        }
        return assetUrls;
    }

    private GAV createGAV(String group, String name, String version) {
        GAV gav = new GAV();
        if (group != null && name != null && version != null) {
            gav.setGroupId(group);
            gav.setArtifactId(name);
            gav.setVersion(version);
        }
        return gav;
    }

    // ---------------------------------------------------------------------------------
    // Nexus 2 search (JSON)
    // ---------------------------------------------------------------------------------
    /**
     * Searches Nexus 2 by SHA-1 using:
     *   1) /service/local/identify/sha1/{sha1}  → construct content URL(s)
     *   2) /service/local/lucene/search?sha1=…  → construct content URL(s)
     */
    private Map<GAV, String> nx2SearchBySha1(String base, String sha1) {
        Map<GAV, String> sink = new LinkedHashMap<>();
        if (canceled) {
            return sink;
        }

        // Try identify endpoint first (usually more accurate)
        if (tryNx2IdentifyEndpoint(base, sha1, sink)) {
            return sink;
        }

        // Fall back to Lucene search
        tryNx2LuceneSearch(base, sha1, sink);
        return sink;
    }

    private boolean tryNx2IdentifyEndpoint(String base, String sha1, Map<GAV, String> sink) {
        try {
            String u = base + "/service/local/identify/sha1/" + urlenc(sha1);
            JsonObject root = getJson(u);
            if (root != null) {
                return processNx2IdentifyResult(base, root, sink);
            }
        } catch (Throwable t) {
            Logger.debug(t);
        }
        return false;
    }

    private boolean processNx2IdentifyResult(String base, JsonObject root, Map<GAV, String> sink) {
        String repoId     = jsonStringAny(root, "repoId", "repositoryId");
        String groupId    = jsonString(root, "groupId");
        String artifactId = jsonString(root, "artifactId");
        String version    = jsonString(root, VERSION_FIELD);
        String classifier = jsonString(root, "classifier");
        String ext        = firstNonEmpty(jsonString(root, "extension"), "jar");

        if (groupId != null && artifactId != null && version != null && repoId != null) {
            String redirectBase = ensureTrailingSlash(base) + "service/local/artifact/maven/redirect";
            String src = buildNx2Redirect(redirectBase, repoId, groupId, artifactId, version, ext, "sources");
            String jar = buildNx2Redirect(redirectBase, repoId, groupId, artifactId, version, ext, isEmpty(classifier) ? null : classifier);

            String chosen = httpExists(src) ? src : jar;

            if (chosen != null) {
                GAV g = createGAV(groupId, artifactId, version);
                g.setArtifactLink(chosen);
                sink.put(g, chosen);
                return true; // identify usually yields a definitive match
            }
        }
        return false;
    }

    private void tryNx2LuceneSearch(String base, String sha1, Map<GAV, String> sink) {
        try {
            String u = base + "/service/local/lucene/search?sha1=" + urlenc(sha1);
            JsonObject root = getJson(u);
            if (root != null) {
                processNx2LuceneResults(base, root, sink);
            }
        } catch (Throwable t) {
            Logger.debug(t);
        }
    }

    private void processNx2LuceneResults(String base, JsonObject root, Map<GAV, String> sink) {
        JsonArray data = jsonArray(root, "data");
        for (JsonValue v : data) {
            if (canceled) {
                return;
            }
            processNx2LuceneItem(base, v.asObject(), sink);
        }
    }

    private void processNx2LuceneItem(String base, JsonObject item, Map<GAV, String> sink) {
        String groupId    = jsonString(item, "groupId");
        String artifactId = jsonString(item, "artifactId");
        String version    = jsonString(item, VERSION_FIELD);
        String repoId     = jsonStringAny(item, "latestReleaseRepositoryId", "repositoryId", "repoId");

        if (groupId == null || artifactId == null || version == null || repoId == null) {
            return;
        }

        String redirectBase = ensureTrailingSlash(base) + "service/local/artifact/maven/redirect";
        String src = buildNx2Redirect(redirectBase, repoId, groupId, artifactId, version, "jar", "sources");
        String jar = buildNx2Redirect(redirectBase, repoId, groupId, artifactId, version, "jar", null);

        String chosen = httpExists(src) ? src : (httpExists(jar) ? jar : null);
        if (chosen != null) {
            GAV g = createGAV(groupId, artifactId, version);
            g.setArtifactLink(chosen);
            sink.compute(g, (k, v) -> v == null || chosen.endsWith(SOURCES_JAR) ? chosen : v);
        }
    }

    private String buildNx2Redirect(String redirectBase, String repoId, String groupId, String artifactId,
            String version, String extension, String classifierOpt) {
        StringBuilder sb = new StringBuilder(redirectBase)
                .append("?r=").append(urlenc(repoId))
                .append("&g=").append(urlenc(groupId))
                .append("&a=").append(urlenc(artifactId))
                .append("&v=").append(urlenc(version))
                .append("&e=").append(urlenc(firstNonEmpty(extension, "jar")));
        if (!isEmpty(classifierOpt)) {
            sb.append("&c=").append(urlenc(classifierOpt));
        }
        return normalizeSchemeByPort(sb.toString());
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------
    private String urlenc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) {
            return null;
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String ensureTrailingSlash(String s) {
        return s.endsWith("/") ? s : (s + "/");
    }

    private static String ensureWithNexus(String base) {
        return base.endsWith("/nexus") ? base : (base + "/nexus");
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static String firstNonEmpty(String a, String b) {
        return !isEmpty(a) ? a : b;
    }

    private String chooseSourcesFirst(Set<String> urls) {
        for (String u : urls) {
            if (u.endsWith(SOURCES_JAR) || u.endsWith(".src.zip")) {
                return u;
            }
        }
        return urls.iterator().next();
    }

    private Optional<GAV> parseMavenPathToGav(String path) {
        // Example: com/acme/lib/mylib/1.2.3/mylib-1.2.3(-classifier).jar
        try {
            if (path == null) {
                return Optional.empty();
            }
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash <= 0) {
                return Optional.empty();
            }

            String fileName = path.substring(lastSlash + 1);
            String parent = path.substring(0, lastSlash);

            String[] parts = parent.split("/");
            if (parts.length < 3) {
                return Optional.empty();
            }

            String version = parts[parts.length - 1];
            String artifactId = parts[parts.length - 2];
            StringBuilder groupSb = new StringBuilder();
            for (int i = 0; i < parts.length - 2; i++) {
                if (i > 0) {
                    groupSb.append('.');
                }
                groupSb.append(parts[i]);
            }
            String groupId = groupSb.toString();

            // fileName pattern: artifactId-version(-classifier).ext
            String base = fileName;
            int dot = base.lastIndexOf('.');
            if (dot > 0) {
                base = base.substring(0, dot);
            }

            String expectedPrefix = artifactId + "-" + version;
            if (!base.startsWith(expectedPrefix)) {
                return Optional.empty();
            }

            GAV g = new GAV();
            g.setGroupId(groupId);
            g.setArtifactId(artifactId);
            g.setVersion(version);
            return Optional.of(g);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /**
     * Corrects common scheme and port mismatches. When the scheme is http and the
     * port is 443, the method upgrades to https on port 443. When the scheme is https
     * and the port is 80, the method downgrades to http on port 80. In other cases,
     * the input is returned unchanged.
     */
    private String normalizeSchemeByPort(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI u = new URI(url);
            String scheme = u.getScheme();
            int port = u.getPort(); // -1 means default
            if ("http".equalsIgnoreCase(scheme) && port == 443) {
                return new URI(
                        "https",
                        u.getUserInfo(),
                        u.getHost(),
                        443,
                        u.getPath(),
                        u.getQuery(),
                        u.getFragment()
                        ).toString();
            }
            if ("https".equalsIgnoreCase(scheme) && port == 80) {
                return new URI(
                        "http",
                        u.getUserInfo(),
                        u.getHost(),
                        80,
                        u.getPath(),
                        u.getQuery(),
                        u.getFragment()
                        ).toString();
            }
            return url;
        } catch (URISyntaxException ignore) {
            return url; // be conservative on parse errors
        }
    }

    // ---------------------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------------------
    private int httpStatusCode(String url) throws IOException {
        EcfHttpClient client = newHttpClient();
        return client.getStatusCode(normalizeSchemeByPort(url));
    }

    private boolean httpExists(String url) {
        url = normalizeSchemeByPort(url);
        try {
            int code = newHttpClient().getStatusCode(url);
            return code >= 200 && code < 300;
        } catch (Throwable t) {
            return false;
        }
    }

    private JsonObject getJson(String url) {
        if (canceled) {
            return null;
        }
        url = normalizeSchemeByPort(url);
        try {
            byte[] bytes = newHttpClient().downloadToBytes(url);
            return Json.parse(new String(bytes, StandardCharsets.UTF_8)).asObject();
        } catch (Throwable t) {
            Logger.debug(t);
            return null;
        }
    }

    private EcfHttpClient newHttpClient() {
        EcfHttpClient client = new EcfHttpClient();
        client.setConnectTimeout(15000);
        client.setReadTimeout(30000);
        client.setRequestHeader("Accept", "application/json, */*;q=0.8");
        client.setNoProxy(bypassProxy);
        if (hasServiceCredentials()) {
            String token = serviceUser + ":" + (servicePassword == null ? "" : servicePassword);
            String basic = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
            client.setRequestHeader("Authorization", "Basic " + basic);
        }
        return client;
    }

    private boolean hasServiceCredentials() {
        return !isEmpty(serviceUser);
    }

    // ---------------------------------------------------------------------------------
    // Minimal-json helpers
    // ---------------------------------------------------------------------------------
    private static JsonArray jsonArray(JsonObject obj, String name) {
        if (obj == null) {
            return new JsonArray();
        }
        JsonValue v = obj.get(name);
        return (v == null || v.isNull()) ? new JsonArray() : v.asArray();
    }

    private static String jsonString(JsonObject obj, String name) {
        if (obj == null) {
            return null;
        }
        JsonValue v = obj.get(name);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static String jsonStringAny(JsonObject obj, String... names) {
        if (obj == null) {
            return null;
        }
        for (String n : names) {
            JsonValue v = obj.get(n);
            if (v != null && !v.isNull()) {
                String s = v.asString();
                if (s != null && !s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }
}
