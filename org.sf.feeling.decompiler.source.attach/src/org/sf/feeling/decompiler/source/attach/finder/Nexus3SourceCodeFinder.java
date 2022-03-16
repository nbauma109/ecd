/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.finder;

import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sf.feeling.decompiler.source.attach.utils.SourceAttachUtil;
import org.sf.feeling.decompiler.source.attach.utils.SourceBindingUtil;
import org.sf.feeling.decompiler.source.attach.utils.UrlDownloader;
import org.sf.feeling.decompiler.util.HashUtils;
import org.sf.feeling.decompiler.util.Logger;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

public class Nexus3SourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

	private final String serviceUrl;
	private final String serviceUser;
	private final String servicePassword;
	private boolean canceled = false;

	public Nexus3SourceCodeFinder(String serviceUrl) {
		this(serviceUrl, null, null);
	}

	public Nexus3SourceCodeFinder(String nexusUrl, String nexusUser, String nexusPassword) {
		this.serviceUrl = nexusUrl;
		this.serviceUser = nexusUser;
		this.servicePassword = nexusPassword;
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
			gavs.addAll(findArtifactsUsingNexus(null, null, null, null, sha1, false));
		} catch (Throwable e) {
			Logger.error(e);
		}

		if (canceled)
			return;

		if (gavs.isEmpty()) {
			try {
				gavs.addAll(findGAVFromFile(binFile));
			} catch (Throwable e) {
				Logger.error(e);
			}
		}

		if (canceled)
			return;

		Map<GAV, String> sourcesUrls = new HashMap<>();
		try {
			sourcesUrls.putAll(findSourcesUsingNexus(gavs));
		} catch (Throwable e) {
			Logger.error(e);
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
				Logger.error(e);
			}
		}

		for (Map.Entry<GAV, String> entry : sourcesUrls.entrySet()) {
			String name = entry.getKey().getArtifactId() + '-' + entry.getKey().getVersion() + "-sources.jar"; //$NON-NLS-1$
			try {
				String tmpFile = new UrlDownloader().download(entry.getValue());
				if (tmpFile != null && new File(tmpFile).exists()
						&& SourceAttachUtil.isSourceCodeFor(tmpFile, binFile)) {
					setDownloadUrl(entry.getValue());
					SourceFileResult object = new SourceFileResult(this, binFile, tmpFile, name, 100);
					Logger.error(this.toString() + " FOUND: " + object, null); //$NON-NLS-1$
					results.add(object);

				}
			} catch (Throwable e) {
				Logger.error(e);
			}
		}
	}

	private Map<GAV, String> findSourcesUsingNexus(Collection<GAV> gavs) throws Exception {
		Map<GAV, String> results = new HashMap<>();
		for (GAV gav : gavs) {
			if (canceled)
				return results;
			Set<GAV> gavs2 = findArtifactsUsingNexus(gav.getGroupId(), gav.getArtifactId(), gav.getVersion(), "sources", //$NON-NLS-1$
					null, true);
			for (GAV gav2 : gavs2) {
				results.put(gav, gav2.getArtifactLink());
			}
		}

		return results;
	}

	/**
	 * *
	 * 
	 * @param g       group id to perform a maven search against (can be combined
	 *                with a, v, p & c params as well).
	 * @param a       artifact id to perform a maven search against (can be combined
	 *                with g, v, p & c params as well).
	 * @param v       version to perform a maven search against (can be combined
	 *                with g, a, p & c params as well).
	 * @param c       classifier to perform a maven search against (can be combined
	 *                with g, a, v & p params as well).
	 * @param sha1    provide this param for a checksum search (g, a, v, p, c, cn
	 *                params will be ignored).
	 * @param getLink
	 * @return
	 * @throws Exception
	 * @see https://repository.sonatype.org/nexus-indexer-lucene-plugin/default/docs/path__data_index.html
	 * @see https://repository.sonatype.org/nexus-indexer-lucene-plugin/default/docs/path__lucene_search.html
	 */
	private Set<GAV> findArtifactsUsingNexus(String g, String a, String v, String c, String sha1, boolean getLink)
			throws Exception {
		// https://repository.sonatype.org/service/local/lucene/search?sha1=686ef3410bcf4ab8ce7fd0b899e832aaba5facf7
		// https://repository.sonatype.org/service/local/data_index?sha1=686ef3410bcf4ab8ce7fd0b899e832aaba5facf7
		Set<GAV> results = new HashSet<>();
		String nexusUrl = getNexusContextUrl();

		String[] endpoints = new String[] { //
				nexusUrl + "service/extdirect", //$NON-NLS-1$
				// nexusUrl + "service/local/data_index", //$NON-NLS-1$
				// nexusUrl + "service/local/lucene/search" //$NON-NLS-1$
		};
		for (String endpoint : endpoints) {
			if (canceled)
				return results;
			String urlStr = endpoint;
			StringBuilder jsonPayload = new StringBuilder(
					"{\"action\":\"coreui_Search\",\"method\":\"read\",\"data\":[{\"page\":1,\"start\":0,\"limit\":300,\"filter\":");
			if (g != null && a != null && v != null && c != null) {
				jsonPayload.append("[{\"property\":\"group.raw\",\"value\":\"");
				jsonPayload.append(g);
				jsonPayload.append("\"},{\"property\":\"name.raw\",\"value\":\"");
				jsonPayload.append(a);
				jsonPayload.append("\"},{\"property\":\"version\",\"value\":\"");
				jsonPayload.append(v);
				jsonPayload.append("\"},{\"property\":\"assets.attributes.maven2.classifier\",\"value\":\"");
				jsonPayload.append(c);
			}
			if (sha1 != null) {
				jsonPayload.append("[{\"property\":\"assets.attributes.checksum.sha1\",\"value\":\"");
				jsonPayload.append(sha1);
			}
			jsonPayload.append("\"}]}],\"type\":\"rpc\",\"tid\":1}");

			HttpURLConnection connection = (HttpURLConnection) new URL(urlStr).openConnection();
			connection.setDoOutput(true);
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-type", "application/json;charset=utf-8");
			connection.setRequestProperty("Accept", "*/*");
			if (serviceUser != null && servicePassword != null) {
				connection.setAuthenticator(new Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(serviceUser, servicePassword.toCharArray());
					}
				});
			}
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(10000);
			try {
				connection.connect();
				OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());

				writer.write(jsonPayload.toString());
				writer.flush();
				JsonObject jsonResponse = (JsonObject) Json.parse(new InputStreamReader(connection.getInputStream()));
				JsonObject jsonResult = (JsonObject) jsonResponse.get("result");
				JsonArray jsonData = (JsonArray) jsonResult.get("data");
				if (jsonData.size() == 1) {
					JsonObject jsonValue = (JsonObject) jsonData.get(0);
					GAV gav = new GAV();
					gav.setGroupId(jsonValue.getString("group", ""));
					gav.setArtifactId(jsonValue.getString("name", ""));
					gav.setVersion(jsonValue.getString("version", ""));
					if (getLink) {
						StringBuilder link = new StringBuilder();
						link.append(getNexusContextUrl());
						link.append("repository/");
						link.append(jsonValue.getString("repositoryName", ""));
						link.append('/');
						link.append(gav.getGroupId().replace('.', '/'));
						link.append('/');
						link.append(gav.getArtifactId());
						link.append('/');
						link.append(gav.getVersion());
						link.append('/');
						link.append(gav.getArtifactId());
						link.append('-');
						link.append(gav.getVersion());
						if (c != null) {
							link.append('-');
							link.append(c);
						}
						link.append(".jar");
						gav.setArtifactLink(link.toString());
					}
					results.add(gav);
				}
			} catch (Throwable e) {
				Logger.error("Failed to query source code artifact", e);
			}
		}
		return results;

	}

	private String getNexusContextUrl() {
		String result = serviceUrl.substring(0, serviceUrl.lastIndexOf('/'));
		if (!result.endsWith("/")) //$NON-NLS-1$
		{
			result += '/';
		}
		return result;
	}

	public static void main(String[] args) throws Exception {
		String sha1 = "759f2c6722b7f52acb14dcdaaa587be5c7d0ce8b";
		Nexus3SourceCodeFinder nexus3SourceCodeFinder = new Nexus3SourceCodeFinder("https://maven.quiltmc.org/");
		Set<GAV> results = nexus3SourceCodeFinder.findArtifactsUsingNexus(null, null, null, null, sha1, true);
		System.out.println(results);
		Map<GAV, String> sources = nexus3SourceCodeFinder.findSourcesUsingNexus(results);
		System.out.println(sources);
	}
}