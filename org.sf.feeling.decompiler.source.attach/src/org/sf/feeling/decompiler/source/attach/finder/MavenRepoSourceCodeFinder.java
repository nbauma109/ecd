/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.finder;

import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.sf.feeling.decompiler.source.attach.utils.SourceAttachUtil;
import org.sf.feeling.decompiler.source.attach.utils.SourceBindingUtil;
import org.sf.feeling.decompiler.source.attach.utils.UrlDownloader;
import org.sf.feeling.decompiler.util.Logger;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

public class MavenRepoSourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

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
		Collection<GAV> gavs = new HashSet<>();
		try {
			gavs.addAll(findArtifactsUsingMavenCentral(sha1));
		} catch (Exception e) {
			Logger.debug(e);
		}

		if (canceled)
			return;

		if (gavs.isEmpty()) {
			try {
				findGAVFromFile(binFile).ifPresent(gavs::add);
			} catch (Exception e) {
				Logger.debug(e);
			}
		}

		if (canceled)
			return;

		Map<GAV, String> sourcesUrls = new HashMap<>();
		try {
			sourcesUrls.putAll(findSourcesUsingMavenCentral(gavs));
		} catch (Exception e) {
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
				String tmpFile = new UrlDownloader().download(entry.getValue());
				if (tmpFile != null && new File(tmpFile).exists()
						&& SourceAttachUtil.isSourceCodeFor(tmpFile, binFile)) {
					setDownloadUrl(entry.getValue());
					SourceFileResult object = new SourceFileResult(this, binFile, tmpFile, name, 100);
					Logger.debug(this.toString() + " FOUND: " + object, null); //$NON-NLS-1$
					results.add(object);

				}
			} catch (Exception e) {
				Logger.debug(e);
			}
		}
	}

	private Map<GAV, String> findSourcesUsingMavenCentral(Collection<GAV> gavs) throws Exception {
		Map<GAV, String> results = new HashMap<>();
		for (GAV gav : gavs) {
			if (canceled)
				return results;

			// g:"ggg" AND a:"aaa" AND v:"vvv" AND l:"sources"
			String qVal = "g:\"" //$NON-NLS-1$
					+ gav.getGroupId() + "\" AND a:\"" //$NON-NLS-1$
					+ gav.getArtifactId() + "\" AND v:\"" //$NON-NLS-1$
					+ gav.getVersion() + "\" AND l:\"sources\""; //$NON-NLS-1$
			String url = "https://search.maven.org/solrsearch/select?q=" //$NON-NLS-1$
					+ URLEncoder.encode(qVal, "UTF-8") //$NON-NLS-1$
					+ "&rows=20&wt=json"; //$NON-NLS-1$
			String json = IOUtils.toString(new URL(url).openStream());
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
				if (array.toString().contains("-sources.jar")) //$NON-NLS-1$
				{
					String path = g.replace('.', '/') + '/' + a + '/' + v + '/' + a + '-' + v + "-sources.jar"; //$NON-NLS-1$
					path = "https://search.maven.org/remotecontent?filepath=" + path; //$NON-NLS-1$
					results.put(gav, path);
				}
			}
		}

		return results;
	}

	private Collection<GAV> findArtifactsUsingMavenCentral(String sha1) throws Exception {
		Set<GAV> results = new HashSet<>();
		String json = IOUtils.toString(new URL("https://search.maven.org/solrsearch/select?q=" //$NON-NLS-1$
				+ URLEncoder.encode("1:\"" + sha1 + "\"", "UTF-8") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ "&rows=20&wt=json").openStream()); //$NON-NLS-1$
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