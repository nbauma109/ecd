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
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;

import org.sf.feeling.decompiler.source.attach.utils.SourceAttachUtil;
import org.sf.feeling.decompiler.source.attach.utils.SourceBindingUtil;
import org.sf.feeling.decompiler.source.attach.utils.UrlDownloader;
import org.sf.feeling.decompiler.util.Logger;

public class NexusSourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

	private final String serviceUrl;
	private boolean canceled = false;

	public NexusSourceCodeFinder(String serviceUrl) {
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
			gavs.addAll(findArtifactsUsingNexus(null, null, null, null, sha1, false));
		} catch (Throwable e) {
			Logger.debug(e);
		}

		if (canceled)
			return;

		Map<GAV, String> sourcesUrls = new HashMap<>();
		try {
			sourcesUrls.putAll(findSourcesUsingNexus(gavs));
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
				String tmpFile = new UrlDownloader().download(entry.getValue());
				if (tmpFile != null && new File(tmpFile).exists()
						&& SourceAttachUtil.isSourceCodeFor(tmpFile, binFile)) {
					setDownloadUrl(entry.getValue());
					SourceFileResult object = new SourceFileResult(this, binFile, tmpFile, name, 100);
					Logger.debug(this.toString() + " FOUND: " + object, null); //$NON-NLS-1$
					results.add(object);

				}
			} catch (Throwable e) {
				Logger.debug(e);
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
				// nexusUrl + "service/local/data_index" /* DEPRECATED */, //$NON-NLS-1$
				nexusUrl + "service/local/lucene/search" //$NON-NLS-1$
		};
		for (String endpoint : endpoints) {
			if (canceled)
				return results;
			String urlStr = endpoint;
			LinkedHashMap<String, String> params = new LinkedHashMap<>();
			if (g != null) {
				params.put("g", g); //$NON-NLS-1$
			}
			if (a != null) {
				params.put("a", a); //$NON-NLS-1$
			}
			if (v != null) {
				params.put("v", v); //$NON-NLS-1$
			}
			if (c != null) {
				params.put("c", c); //$NON-NLS-1$
			}
			if (sha1 != null) {
				params.put("sha1", sha1); //$NON-NLS-1$
			}
			for (Map.Entry<String, String> entry : params.entrySet()) {
				if (!urlStr.endsWith("&") && !urlStr.endsWith("?")) //$NON-NLS-1$ //$NON-NLS-2$
				{
					if (urlStr.indexOf('?') == -1)
						urlStr += "?"; //$NON-NLS-1$
					else
						urlStr += "&"; //$NON-NLS-1$
				}
				urlStr += URLEncoder.encode(entry.getKey(), "UTF-8") //$NON-NLS-1$
						+ "=" //$NON-NLS-1$
						+ URLEncoder.encode(entry.getValue(), "UTF-8"); //$NON-NLS-1$
			}

			URL url = new URL(urlStr);

			URLConnection connection = url.openConnection();

			connection.setConnectTimeout(5000);
			connection.setReadTimeout(10000);
			XMLInputFactory xif = XMLInputFactory.newFactory();
			// This disables DTDs entirely for that factory
			xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);
			// This causes XMLStreamException to be thrown if external DTDs are accessed.
			xif.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			// disable external entities
			xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
			XMLStreamReader xsr = xif.createXMLStreamReader(new StreamSource(connection.getInputStream()));

			Map<String, String> gavMap = new HashMap<>();
			String name = "";
			while (xsr.hasNext()) {
				int next = xsr.next();
				if (next == XMLStreamConstants.START_ELEMENT) {
					name = xsr.getLocalName();
				}
				if (next == XMLStreamConstants.CHARACTERS) {
					String text = xsr.getText().trim();
					if (text.length() > 0) {
						gavMap.put(name, text);
					}
				}
			}

			xsr.close();

			GAV gav = new GAV();
			gav.setGroupId(gavMap.get("groupId"));
			gav.setArtifactId(gavMap.get("artifactId"));
			gav.setVersion(gavMap.get("version"));

			if (gav.isValid()) {
				if (getLink) {
					String repositoryId = gavMap.get("repositoryId");
					String extension = Optional.ofNullable(gavMap.get("extension")).orElse("jar");
					StringBuilder artifactLink = new StringBuilder();
					artifactLink.append(urlStr.replace("lucene/search", "artifact/maven/redirect"));
					artifactLink.append("&r=");
					artifactLink.append(repositoryId);
					artifactLink.append("&e=");
					artifactLink.append(extension);
					gav.setArtifactLink(artifactLink.toString());
				}
				results.add(gav);
				Logger.warn("GAV result: " + gav);
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
}