/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.finder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sf.feeling.decompiler.source.attach.utils.SourceAttachUtil;
import org.sf.feeling.decompiler.source.attach.utils.SourceBindingUtil;
import org.sf.feeling.decompiler.source.attach.utils.UrlDownloader;
import org.sf.feeling.decompiler.util.Logger;

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
			gavs.addAll(findGAVFromFile(binFile));
		} catch (Throwable e) {
			Logger.error(e);
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
				UrlDownloader urlDownloader = new UrlDownloader();
				urlDownloader.setServiceUser(serviceUser);
				urlDownloader.setServicePassword(servicePassword);
				String tmpFile = urlDownloader.download(entry.getValue());
				if (tmpFile != null && new File(tmpFile).exists()
						&& SourceAttachUtil.isSourceCodeFor(tmpFile, binFile)) {
					setDownloadUrl(entry.getValue());
					SourceFileResult object = new SourceFileResult(this, binFile, tmpFile, name, 100);
					results.add(object);
				}
			} catch (Throwable e) {
				Logger.error(e);
			}
		}
	}

	private Map<GAV, String> findSourcesUsingNexus(Collection<GAV> gavs) {
		Map<GAV, String> results = new HashMap<>();
		for (GAV gav : gavs) {
			if (canceled)
				return results;
			Set<GAV> gavs2 = findArtifactsUsingNexus(gav.getGroupId(), gav.getArtifactId(), gav.getVersion(),
					"sources");
			for (GAV gav2 : gavs2) {
				results.put(gav, gav2.getArtifactLink());
			}
		}

		return results;
	}

	private Set<GAV> findArtifactsUsingNexus(String g, String a, String v, String c) {
		Set<GAV> results = new HashSet<>();
		GAV gav = new GAV();
		gav.setGroupId(g);
		gav.setArtifactId(a);
		gav.setVersion(v);
		StringBuilder link = new StringBuilder();
		link.append(serviceUrl);
		if (!serviceUrl.endsWith("/") && !serviceUrl.endsWith("=")) {
			link.append('/');
		}
		link.append(g.replace('.', '/'));
		link.append('/');
		link.append(a);
		link.append('/');
		link.append(v);
		link.append('/');
		link.append(a);
		link.append('-');
		link.append(v);
		if (c != null) {
			link.append('-');
			link.append(c);
		}
		link.append(".jar");
		gav.setArtifactLink(link.toString());
		results.add(gav);
		return results;
	}

	public static void main(String[] args) {
		String serviceUrl = "https://repo1.maven.org/maven2/";
		Nexus3SourceCodeFinder directLinkSourceCodeFinder = new Nexus3SourceCodeFinder(serviceUrl);
		List<SourceFileResult> results = new ArrayList<>();
		File downloadDir = new File(System.getProperty("user.home"), "Downloads");
		File jarFile = new File(downloadDir, "activemq-broker-5.17.0.jar");
		if (jarFile.exists()) {
			directLinkSourceCodeFinder.find(jarFile, results);
			System.out.println(results);
		}
	}
}