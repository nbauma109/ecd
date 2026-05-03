/*******************************************************************************
 * (C) 2017 cnfree (@cnfree)
 * (C) 2017 Pascal Bihler
 * (C) 2021 Jan S. (@jpstotz)
 * (C) 2022-2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.finder;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.nbauma109.decompiler.util.EcfHttpClient;
import io.github.nbauma109.decompiler.source.attach.utils.SourceAttachUtil;
import io.github.nbauma109.decompiler.source.attach.utils.UrlDownloader;
import io.github.nbauma109.decompiler.util.Logger;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

public class MavenRepoSourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

    private static final String SOURCES_JAR = "-sources.jar";
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
    public File getMavenRepoSourceFile(GAV gav) {
        return super.getMavenRepoSourceFile(gav);
    }

    @Override
    public void find(String binFile, String sha1, List<SourceFileResult> results) {
        Collection<GAV> gavs = fetchGAVsFromMavenCentral(sha1);
        if (canceled) {
            return;
        }

        Map<GAV, String> sourcesUrls = fetchSourcesUrls(gavs);

        // Try existing Maven repo files first
        if (tryExistingMavenRepoSources(binFile, sourcesUrls, results)) {
            return;
        }

        // Try cached sources
        if (tryCachedSources(binFile, sourcesUrls, results, true)) {
            return;
        }

        // Download and verify sources
        downloadAndVerifySources(binFile, sourcesUrls, results);
    }

    private Collection<GAV> fetchGAVsFromMavenCentral(String sha1) {
        Collection<GAV> gavs = new HashSet<>();
        try {
            gavs.addAll(findArtifactsUsingMavenCentral(sha1));
        } catch (IOException e) {
            Logger.debug(e);
        }
        return gavs;
    }

    private Map<GAV, String> fetchSourcesUrls(Collection<GAV> gavs) {
        Map<GAV, String> sourcesUrls = new HashMap<>();
        try {
            sourcesUrls.putAll(findSourcesUsingMavenCentral(gavs));
        } catch (IOException e) {
            Logger.debug(e);
        }
        return sourcesUrls;
    }

    private void downloadAndVerifySources(String binFile, Map<GAV, String> sourcesUrls,
            List<SourceFileResult> results) {
        for (Map.Entry<GAV, String> entry : sourcesUrls.entrySet()) {
            GAV gav = entry.getKey();
            File mavenRepoSourceFile = getMavenRepoSourceFile(gav);

            try {
                String tmpFile = new UrlDownloader().download(entry.getValue(), mavenRepoSourceFile);
                if (tmpFile != null && new File(tmpFile).exists()
                        && SourceAttachUtil.isSourceCodeFor(tmpFile, binFile)) {
                    setDownloadUrl(entry.getValue());
                    File downloadedFile = new File(tmpFile);
                    File sourceFileRef = mavenRepoSourceFile != null ? mavenRepoSourceFile : downloadedFile;
                    SourceFileResult object = new SourceFileResult(this, binFile, sourceFileRef, sourceFileRef, 100);
                    Logger.debug(this.toString() + " FOUND: " + object, null); //$NON-NLS-1$
                    results.add(object);
                    return;
                }
            } catch (IOException e) {
                Logger.debug(e);
            }
        }
    }

    private Map<GAV, String> findSourcesUsingMavenCentral(Collection<GAV> gavs) throws IOException {
        Map<GAV, String> results = new HashMap<>();
        for (GAV gav : gavs) {
            if (canceled) {
                return results;
            }

            // g:"ggg" AND a:"aaa" AND v:"vvv" AND l:"sources"
            String qVal = "g:\"" //$NON-NLS-1$
                    + gav.getGroupId() + "\" AND a:\"" //$NON-NLS-1$
                    + gav.getArtifactId() + "\" AND v:\"" //$NON-NLS-1$
                    + gav.getVersion() + "\" AND l:\"sources\""; //$NON-NLS-1$
            String url = "https://search.maven.org/solrsearch/select?q=" //$NON-NLS-1$
                    + URLEncoder.encode(qVal, "UTF-8") //$NON-NLS-1$
                    + "&rows=20&wt=json"; //$NON-NLS-1$
            String json;
            // Use ECF for HTTP downloads with automatic proxy authentication
            EcfHttpClient client = new EcfHttpClient();
            byte[] bytes = client.downloadToBytes(url);
            json = new String(bytes, StandardCharsets.UTF_8);
            JsonObject jsonObject = Json.parse(json).asObject();
            JsonObject response = jsonObject.get("response").asObject(); //$NON-NLS-1$

            for (int i = 0; i < response.getInt("numFound", 0); i++) //$NON-NLS-1$
            {
                JsonArray docs = response.get("docs").asArray(); //$NON-NLS-1$
                JsonObject doci = docs.get(i).asObject();
                String g = doci.getString("g", ""); //$NON-NLS-1$ //$NON-NLS-2$
                String a = doci.getString("a", ""); //$NON-NLS-1$ //$NON-NLS-2$
                String v = doci.getString("v", ""); //$NON-NLS-1$ //$NON-NLS-2$
                JsonArray array = doci.get("ec").asArray(); //$NON-NLS-1$
                if (array.toString().contains(SOURCES_JAR))
                {
                    String path = g.replace('.', '/') + '/' + a + '/' + v + '/' + a + '-' + v + SOURCES_JAR;
                    path = "https://search.maven.org/remotecontent?filepath=" + path; //$NON-NLS-1$
                    results.put(gav, path);
                }
            }
        }

        return results;
    }

    private Collection<GAV> findArtifactsUsingMavenCentral(String sha1) throws IOException {
        Set<GAV> results = new HashSet<>();
        String json;
        String url = "https://search.maven.org/solrsearch/select?q=" //$NON-NLS-1$
                + URLEncoder.encode("1:\"" + sha1 + "\"", "UTF-8") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "&rows=20&wt=json"; //$NON-NLS-1$
        // Use ECF for HTTP downloads with automatic proxy authentication
        EcfHttpClient client = new EcfHttpClient();
        byte[] bytes = client.downloadToBytes(url);
        json = new String(bytes, StandardCharsets.UTF_8);
        JsonObject jsonObject = Json.parse(json).asObject();
        JsonObject response = jsonObject.get("response").asObject(); //$NON-NLS-1$

        for (int i = 0; i < response.getInt("numFound", 0); i++) //$NON-NLS-1$
        {
            JsonArray docs = response.get("docs").asArray(); //$NON-NLS-1$
            JsonObject doci = docs.get(i).asObject();
            GAV gav = new GAV();
            gav.setGroupId(doci.getString("g", "")); //$NON-NLS-1$ //$NON-NLS-2$
            gav.setArtifactId(doci.getString("a", "")); //$NON-NLS-1$ //$NON-NLS-2$
            gav.setVersion(doci.getString("v", "")); //$NON-NLS-1$ //$NON-NLS-2$
            results.add(gav);
        }
        return results;
    }
}
