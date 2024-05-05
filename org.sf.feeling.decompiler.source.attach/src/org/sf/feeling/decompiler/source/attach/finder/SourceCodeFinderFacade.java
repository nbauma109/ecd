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

import org.eclipse.jface.preference.IPreferenceStore;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.source.attach.utils.SourceBindingUtil;
import org.sf.feeling.decompiler.util.HashUtils;
import org.sf.feeling.decompiler.util.Logger;

public class SourceCodeFinderFacade implements SourceCodeFinder {

	private static final String HTTPS_WWW_MMNT_RU_INT_GET_ST_0 = "https://www.mmnt.ru/int/get?st={0}";
	private static final String HTTPS_REPO_SPRING_IO = "https://repo.spring.io/webapp/home.html";
	private static final String HTTPS_REPOSITORY_CLOUDERA_COM_ARTIFACTORY = "https://repository.cloudera.com/artifactory/webapp/home.html";
	private static final String HTTPS_NEXUS_XWIKI_ORG_NEXUS_INDEX_HTML = "https://nexus.xwiki.org/nexus/index.html";
	private static final String HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML = "https://maven.alfresco.com/nexus/index.html";
	private static final String HTTPS_MAVEN_NUXEO_ORG_NEXUS_INDEX_HTML = "https://maven.nuxeo.org/nexus/index.html";
	private static final String HTTPS_MAVEN_JAVA_NET_INDEX_HTML = "https://maven.java.net/index.html";
	private static final String HTTPS_REPOSITORY_OW2_ORG_NEXUS_INDEX_HTML = "https://repository.ow2.org/nexus/index.html";
	private static final String HTTPS_REPOSITORY_APACHE_ORG_INDEX_HTML = "https://repository.apache.org/index.html";
	private static final String HTTPS_REPO_GRAILS_ORG_GRAILS = "https://repo.grails.org/grails/webapp/home.html";
	private static final String HTTPS_OSS_SONATYPE_ORG_INDEX_HTML = "https://oss.sonatype.org/index.html";

	private static List<SourceCodeFinder> getFinders() {
		List<SourceCodeFinder> finders = new ArrayList<>();
		finders.add(new LocalSourceFinder());
		addPrivateNexusRepo(finders);
		IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_CENTRAL)) {
			finders.add(new MavenRepoSourceCodeFinder());
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_OSS_SONATYPE_ORG)) {
			finders.add(new NexusSourceCodeFinder(HTTPS_OSS_SONATYPE_ORG_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_GRAILS_ORG)) {
			finders.add(new ArtifactorySourceCodeFinder(HTTPS_REPO_GRAILS_ORG_GRAILS));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_APACHE_ORG)) {
			finders.add(new NexusSourceCodeFinder(HTTPS_REPOSITORY_APACHE_ORG_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_OW2_ORG)) {
			finders.add(new NexusSourceCodeFinder(HTTPS_REPOSITORY_OW2_ORG_NEXUS_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_JAVA_NET)) {
			finders.add(new NexusSourceCodeFinder(HTTPS_MAVEN_JAVA_NET_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_NUXEO_ORG)) {
			finders.add(new NexusSourceCodeFinder(HTTPS_MAVEN_NUXEO_ORG_NEXUS_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_ALFRESCO)) {
			finders.add(new NexusSourceCodeFinder(HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_NEXUS_XWIKI_ORG)) {
			finders.add(new NexusSourceCodeFinder(HTTPS_NEXUS_XWIKI_ORG_NEXUS_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_CLOUDERA)) {
			finders.add(new ArtifactorySourceCodeFinder(HTTPS_REPOSITORY_CLOUDERA_COM_ARTIFACTORY));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_SPRING)) {
			finders.add(new ArtifactorySourceCodeFinder(HTTPS_REPO_SPRING_IO));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MMNT_RU)) {
			finders.add(new EclipsePluginSourceByUrlPatternFinder(HTTPS_WWW_MMNT_RU_INT_GET_ST_0));
		}
		finders.add(new EclipseSourceReferencesSourceCodeFinder());
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_HG_OPEN_JDK_JAVA_NET)) {
			finders.add(new JreSourceCodeFinder());
		}
		return finders;
	}

	private static List<SourceCodeFinder> getJreFinders() {
		List<SourceCodeFinder> jreFinders = new ArrayList<>();
		jreFinders.add(new LocalSourceFinder());
		addPrivateNexusRepo(jreFinders);
		jreFinders.add(new MavenRepoSourceCodeFinder());
		IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_HG_OPEN_JDK_JAVA_NET)) {
			jreFinders.add(new JreSourceCodeFinder());
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_OSS_SONATYPE_ORG)) {
			jreFinders.add(new NexusSourceCodeFinder(HTTPS_OSS_SONATYPE_ORG_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_GRAILS_ORG)) {
			jreFinders.add(new ArtifactorySourceCodeFinder(HTTPS_REPO_GRAILS_ORG_GRAILS));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_APACHE_ORG)) {
			jreFinders.add(new NexusSourceCodeFinder(HTTPS_REPOSITORY_APACHE_ORG_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_OW2_ORG)) {
			jreFinders.add(new NexusSourceCodeFinder(HTTPS_REPOSITORY_OW2_ORG_NEXUS_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_JAVA_NET)) {
			jreFinders.add(new NexusSourceCodeFinder(HTTPS_MAVEN_JAVA_NET_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_NUXEO_ORG)) {
			jreFinders.add(new NexusSourceCodeFinder(HTTPS_MAVEN_NUXEO_ORG_NEXUS_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_ALFRESCO)) {
			jreFinders.add(new NexusSourceCodeFinder(HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_NEXUS_XWIKI_ORG)) {
			jreFinders.add(new NexusSourceCodeFinder(HTTPS_NEXUS_XWIKI_ORG_NEXUS_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_CLOUDERA)) {
			jreFinders.add(new ArtifactorySourceCodeFinder(HTTPS_REPOSITORY_CLOUDERA_COM_ARTIFACTORY));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_SPRING)) {
			jreFinders.add(new ArtifactorySourceCodeFinder(HTTPS_REPO_SPRING_IO));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MMNT_RU)) {
			jreFinders.add(new EclipsePluginSourceByUrlPatternFinder(HTTPS_WWW_MMNT_RU_INT_GET_ST_0));
		}
		jreFinders.add(new EclipseSourceReferencesSourceCodeFinder());
		return jreFinders;
	}

	private static List<SourceCodeFinder> getEclipseFinders() {
		List<SourceCodeFinder> eclipseFinders = new ArrayList<>();
		eclipseFinders.add(new LocalSourceFinder());
		addPrivateNexusRepo(eclipseFinders);
		IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MMNT_RU)) {
			eclipseFinders.add(new EclipsePluginSourceByUrlPatternFinder(HTTPS_WWW_MMNT_RU_INT_GET_ST_0));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_CENTRAL)) {
			eclipseFinders.add(new MavenRepoSourceCodeFinder());
		}
		eclipseFinders.add(new EclipseSourceReferencesSourceCodeFinder());
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_OSS_SONATYPE_ORG)) {
			eclipseFinders.add(new NexusSourceCodeFinder(HTTPS_OSS_SONATYPE_ORG_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_GRAILS_ORG)) {
			eclipseFinders.add(new ArtifactorySourceCodeFinder(HTTPS_REPO_GRAILS_ORG_GRAILS));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_APACHE_ORG)) {
			eclipseFinders.add(new NexusSourceCodeFinder(HTTPS_REPOSITORY_APACHE_ORG_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_OW2_ORG)) {
			eclipseFinders.add(new NexusSourceCodeFinder(HTTPS_REPOSITORY_OW2_ORG_NEXUS_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_JAVA_NET)) {
			eclipseFinders.add(new NexusSourceCodeFinder(HTTPS_MAVEN_JAVA_NET_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_NUXEO_ORG)) {
			eclipseFinders.add(new NexusSourceCodeFinder(HTTPS_MAVEN_NUXEO_ORG_NEXUS_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_ALFRESCO)) {
			eclipseFinders.add(new NexusSourceCodeFinder(HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_NEXUS_XWIKI_ORG)) {
			eclipseFinders.add(new NexusSourceCodeFinder(HTTPS_NEXUS_XWIKI_ORG_NEXUS_INDEX_HTML));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_CLOUDERA)) {
			eclipseFinders.add(new ArtifactorySourceCodeFinder(HTTPS_REPOSITORY_CLOUDERA_COM_ARTIFACTORY));
		}
		if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_SPRING)) {
			eclipseFinders.add(new ArtifactorySourceCodeFinder(HTTPS_REPO_SPRING_IO));
		}
		eclipseFinders.add(new JreSourceCodeFinder());
		return eclipseFinders;
	}

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

		List<SourceCodeFinder> searchFinders = getFinders();
		if (binFilePath.toLowerCase().indexOf("jre") != -1) //$NON-NLS-1$
		{
			searchFinders = getJreFinders();
		} else if (binFilePath.toLowerCase().indexOf("eclipse") != -1) //$NON-NLS-1$
		{
			searchFinders = getEclipseFinders();
		}

		for (int i = 0; i < searchFinders.size() && !this.canceled; i++) {
			List<SourceFileResult> results2 = new ArrayList<>();
			SourceCodeFinder finder = searchFinders.get(i);
			Logger.debug(finder + " " + binFile, null); //$NON-NLS-1$

			finder.find(binFilePath, results2);
			if (!results2.isEmpty()) {
				results.addAll(results2);
				break;
			}
		}
	}

	private static void addPrivateNexusRepo(List<SourceCodeFinder> finders) {
		IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
		String nexusUrl = prefs.getString(JavaDecompilerPlugin.NEXUS_URL);
		String nexusUser = prefs.getString(JavaDecompilerPlugin.NEXUS_USER);
		String nexusPassword = prefs.getString(JavaDecompilerPlugin.NEXUS_PASSWORD);
		if (!nexusUrl.isBlank()) {
			if (!nexusUser.isBlank() && !nexusPassword.isBlank()) {
				finders.add(new Nexus3SourceCodeFinder(nexusUrl, nexusUser, nexusPassword));
			} else {
				finders.add(new Nexus3SourceCodeFinder(nexusUrl));
			}
		}
	}

	@Override
	public void cancel() {
		this.canceled = true;
		getFinders().forEach(SourceCodeFinder::cancel);
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