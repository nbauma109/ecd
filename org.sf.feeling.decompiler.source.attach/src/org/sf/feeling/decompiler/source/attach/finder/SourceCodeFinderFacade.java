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
import java.util.List;

import org.sf.feeling.decompiler.source.attach.utils.SourceBindingUtil;
import org.sf.feeling.decompiler.util.HashUtils;
import org.sf.feeling.decompiler.util.Logger;

public class SourceCodeFinderFacade implements SourceCodeFinder {

	private SourceCodeFinder[] finders = {

			new MavenRepoSourceCodeFinder(), new NexusSourceCodeFinder("https://oss.sonatype.org/index.html"), //$NON-NLS-1$
			new ArtifactorySourceCodeFinder("https://repo.grails.org/grails/webapp/home.html"), //$NON-NLS-1$

			new NexusSourceCodeFinder("https://repository.apache.org/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://repository.ow2.org/nexus/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://maven.java.net/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://maven.nuxeo.org/nexus/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://maven.alfresco.com/nexus/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://nexus.xwiki.org/nexus/index.html"), //$NON-NLS-1$

			new ArtifactorySourceCodeFinder("https://repository.cloudera.com/artifactory/webapp/home.html"), //$NON-NLS-1$
			new ArtifactorySourceCodeFinder("https://repo.spring.io/webapp/home.html"), //$NON-NLS-1$

			new EclipsePluginSourceByUrlPatternFinder("https://www.mmnt.ru/int/get?st={0}"), //$NON-NLS-1$

			new EclipseSourceReferencesSourceCodeFinder(), new JreSourceCodeFinder() };

	private SourceCodeFinder[] jreFinders = {

			new MavenRepoSourceCodeFinder(), new JreSourceCodeFinder(),

			new NexusSourceCodeFinder("https://oss.sonatype.org/index.html"), //$NON-NLS-1$
			new ArtifactorySourceCodeFinder("https://repo.grails.org/grails/webapp/home.html"), //$NON-NLS-1$

			new NexusSourceCodeFinder("https://repository.apache.org/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://repository.ow2.org/nexus/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://maven.java.net/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://maven.nuxeo.org/nexus/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://maven.alfresco.com/nexus/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://nexus.xwiki.org/nexus/index.html"), //$NON-NLS-1$

			new ArtifactorySourceCodeFinder("https://repository.cloudera.com/artifactory/webapp/home.html"), //$NON-NLS-1$
			new ArtifactorySourceCodeFinder("https://repo.spring.io/webapp/home.html"), //$NON-NLS-1$

			new EclipsePluginSourceByUrlPatternFinder("https://www.mmnt.ru/int/get?st={0}"), //$NON-NLS-1$

			new EclipseSourceReferencesSourceCodeFinder() };

	private SourceCodeFinder[] eclipseFinders = {

			new EclipsePluginSourceByUrlPatternFinder("https://www.mmnt.ru/int/get?st={0}"), //$NON-NLS-1$

			new MavenRepoSourceCodeFinder(), new EclipseSourceReferencesSourceCodeFinder(),

			new NexusSourceCodeFinder("https://oss.sonatype.org/index.html"), //$NON-NLS-1$
			new ArtifactorySourceCodeFinder("https://repo.grails.org/grails/webapp/home.html"), //$NON-NLS-1$

			new NexusSourceCodeFinder("https://repository.apache.org/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://repository.ow2.org/nexus/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://maven.java.net/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://maven.nuxeo.org/nexus/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://maven.alfresco.com/nexus/index.html"), //$NON-NLS-1$
			new NexusSourceCodeFinder("https://nexus.xwiki.org/nexus/index.html"), //$NON-NLS-1$

			new ArtifactorySourceCodeFinder("https://repository.cloudera.com/artifactory/webapp/home.html"), //$NON-NLS-1$
			new ArtifactorySourceCodeFinder("https://repo.spring.io/webapp/home.html"), //$NON-NLS-1$

			new JreSourceCodeFinder() };

	private boolean canceled;

	@Override
	public void find(String binFilePath, List<SourceFileResult> results) {
		File binFile = new File(binFilePath);
		if (!binFile.exists() || binFile.isDirectory())
			return;
		String sha1 = HashUtils.sha1Hash(binFile);
		String[] sourceFiles = SourceBindingUtil.getSourceFileBySha(sha1);
		if (sourceFiles != null && sourceFiles[0] != null && new File(sourceFiles[0]).exists()) {
			File sourceFile = new File(sourceFiles[0]);
			File tempFile = new File(sourceFiles[1]);
			SourceFileResult result = new SourceFileResult(this, binFilePath, sourceFile, tempFile, 100);
			results.add(result);
			return;
		}

		SourceCodeFinder[] searchFinders = finders;
		if (binFilePath.toLowerCase().indexOf("jre") != -1) //$NON-NLS-1$
		{
			searchFinders = jreFinders;
		} else if (binFilePath.toLowerCase().indexOf("eclipse") != -1) //$NON-NLS-1$
		{
			searchFinders = eclipseFinders;
		}

		for (int i = 0; i < searchFinders.length && !this.canceled; i++) {
			List<SourceFileResult> results2 = new ArrayList<>();
			SourceCodeFinder finder = searchFinders[i];
			Logger.debug(finder + " " + binFile, null); //$NON-NLS-1$

			finder.find(binFilePath, results2);
			if (!results2.isEmpty()) {
				results.addAll(results2);
				break;
			}
		}
	}

	@Override
	public void cancel() {
		this.canceled = true;
		for (int i = 0; i < this.finders.length && !this.canceled; i++) {
			SourceCodeFinder finder = this.finders[i];
			finder.cancel();
		}
	}

	@Override
	public String getDownloadUrl() {
		return null;
	}

	public static void main(String[] args) {
		SourceCodeFinderFacade finder = new SourceCodeFinderFacade();
		List<SourceFileResult> results = new ArrayList<>();
		finder.find("C:\\Temp\\groovy-all-1.7.6.jar", results); //$NON-NLS-1$
		for (SourceFileResult r : results) {
			System.out.println(r);
		}
	}

}