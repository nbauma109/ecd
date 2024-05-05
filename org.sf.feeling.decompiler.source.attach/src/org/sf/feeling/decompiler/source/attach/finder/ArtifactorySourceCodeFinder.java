/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.finder;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
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
import org.sf.feeling.decompiler.source.attach.utils.SourceAttachUtil;
import org.sf.feeling.decompiler.source.attach.utils.SourceBindingUtil;
import org.sf.feeling.decompiler.source.attach.utils.UrlDownloader;
import org.sf.feeling.decompiler.util.HashUtils;
import org.sf.feeling.decompiler.util.Logger;

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
	public void find(String binFile, List<SourceFileResult> results) {
		Collection<GAV> gavs = new HashSet<>();
		try {
			String sha1 = HashUtils.sha1Hash(new File(binFile));
			gavs.addAll(findArtifactsUsingArtifactory(null, null, null, null, sha1, false));
		} catch (Throwable e) {
			Logger.debug(e);
		}

		if (canceled)
			return;

		if (gavs.isEmpty()) {
			try {
				findGAVFromFile(binFile).ifPresent(gavs::add);
			} catch (Throwable e) {
				Logger.debug(e);
			}
		}

		if (canceled)
			return;

		Map<GAV, String> sourcesUrls = new HashMap<>();
		try {
			sourcesUrls.putAll(findSourcesUsingArtifactory(gavs));
		} catch (Throwable e) {
			Logger.debug(e);
		}

		for (Map.Entry<GAV, String> entry : sourcesUrls.entrySet()) {
			try {
				String[] sourceFiles = SourceBindingUtil.getSourceFileByDownloadUrl(entry.getValue());
				if (sourceFiles != null && sourceFiles[0] != null && new File(sourceFiles[0]).exists()) {
					File sourceFile = new File(sourceFiles[0]);
					File tempFile = new File(sourceFiles[1]);
					SourceFileResult result = new SourceFileResult(this, binFile, sourceFile, tempFile, 100);
					results.add(result);
					return;
				}
			} catch (Throwable e) {
				Logger.debug(e);
			}
		}

		for (Map.Entry<GAV, String> entry : sourcesUrls.entrySet()) {
			String name = entry.getKey().getArtifactId() + '-' + entry.getKey().getVersion() + "-sources.jar"; //$NON-NLS-1$
			try {
				String result = new UrlDownloader().download(entry.getValue());
				if (result != null && new File(result).exists() && SourceAttachUtil.isSourceCodeFor(result, binFile)) {
					setDownloadUrl(entry.getValue());
					SourceFileResult object = new SourceFileResult(this, binFile, result, name, 100);
					Logger.debug(this.toString() + " FOUND: " + object, null); //$NON-NLS-1$
					results.add(object);
				}
			} catch (Throwable e) {
				Logger.debug(e);
			}
		}
	}

	protected Map<GAV, String> findSourcesUsingArtifactory(Collection<GAV> gavs) throws Exception {
		Map<GAV, String> results = new HashMap<>();
		for (GAV gav : gavs) {
			if (canceled)
				return results;
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
			boolean getLink) throws Exception {
		// https://repository.cloudera.com/artifactory/api/search/checksum?sha1=2bf96b7aa8b611c177d329452af1dc933e14501c
		// {"results":[{"uri":"https://repository.cloudera.com/artifactory/api/storage/repo1-cache/commons-cli/commons-cli/1.2/commons-cli-1.2.jar"}]}
		// GET
		// /api/search/gavc?g=org.acme&a=artifact*&v=1.0&c=sources&repos=libs-release-local

		Set<GAV> results = new HashSet<>();
		String apiUrl = getArtifactApiUrl();

		String url;
		if (sha1 != null) {
			url = apiUrl + "search/checksum?sha1=" + sha1; //$NON-NLS-1$
		} else {
			url = apiUrl + "search/gavc?g=" + g + "&a=" + a + "&v=" + v + (c != null ? "&c=" + c : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}

		URLConnection connection = new URL(url).openConnection();
		connection.setConnectTimeout(5000);
		connection.setReadTimeout(5000);
		connection.connect();
		try {
			String json;
			try (InputStream is = connection.getInputStream()) {
				json = IOUtils.toString(is, StandardCharsets.UTF_8);
			}

			JsonObject resp = Json.parse(json).asObject();
			for (JsonValue elem : resp.get("results").asArray()) //$NON-NLS-1$
			{
				JsonObject result = elem.asObject();
				String uri = result.getString("uri", ""); //$NON-NLS-1$ //$NON-NLS-2$
				// https://repository.cloudera.com/artifactory/api/storage/repo1-cache/commons-cli/commons-cli/1.2/commons-cli-1.2.jar
				String regex = "/api/storage/[^/]+/(.+)$"; //$NON-NLS-1$
				Pattern pattern = Pattern.compile(regex);
				Matcher matcher = pattern.matcher(uri);
				if (matcher.find()) {
					String[] gavInArray = matcher.group(1).split("/"); //$NON-NLS-1$

					GAV gav = new GAV();
					String group = gavInArray[0];
					for (int i = 1; i < gavInArray.length - 3; i++) {
						group += "." + gavInArray[i]; //$NON-NLS-1$
					}
					gav.setGroupId(group);

					gav.setArtifactId(gavInArray[gavInArray.length - 3]);
					gav.setVersion(gavInArray[gavInArray.length - 2]);

					if (getLink)
						gav.setArtifactLink(uri);
					results.add(gav);
				}

			}

		} catch (Throwable e) {
			Logger.debug(e);
		}

		return results;
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
