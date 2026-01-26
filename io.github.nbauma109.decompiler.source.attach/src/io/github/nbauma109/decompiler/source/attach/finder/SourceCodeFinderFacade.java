/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.finder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;
import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.source.attach.utils.SourceBindingUtil;
import io.github.nbauma109.decompiler.util.Logger;

public class SourceCodeFinderFacade implements SourceCodeFinder {

    static final String HTTPS_REPOSITORY_CLOUDERA_COM_ARTIFACTORY = "https://repository.cloudera.com";
    static final String HTTPS_NEXUS_XWIKI_ORG_NEXUS_INDEX_HTML = "https://nexus.xwiki.org/nexus";
    static final String HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML = "https://maven.alfresco.com/nexus";
    static final String HTTPS_REPOSITORY_APACHE_ORG_INDEX_HTML = "https://repository.apache.org";
    static final String HTTPS_REPO_GRAILS_ORG_GRAILS = "https://repo.grails.org/grails/webapp/home.html";
    static final String HTTPS_OSS_SONATYPE_ORG_INDEX_HTML = "https://oss.sonatype.org";

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
        if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_ALFRESCO)) {
            finders.add(new NexusSourceCodeFinder(HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML));
        }
        if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_NEXUS_XWIKI_ORG)) {
            finders.add(new NexusSourceCodeFinder(HTTPS_NEXUS_XWIKI_ORG_NEXUS_INDEX_HTML));
        }
        if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_CLOUDERA)) {
            finders.add(new NexusSourceCodeFinder(HTTPS_REPOSITORY_CLOUDERA_COM_ARTIFACTORY));
        }
        return finders;
    }

    private boolean canceled;

    @Override
    public void find(String binFilePath, String sha1, List<SourceFileResult> results) {
        File binFile = new File(binFilePath);
        if (!binFile.exists() || binFile.isDirectory()) {
            return;
        }
        String[] sourceFiles = SourceBindingUtil.getSourceFileBySha(sha1);
        if (sourceFiles != null && sourceFiles[0] != null && new File(sourceFiles[0]).exists()) {
            File sourceFile = new File(sourceFiles[0]);
            File tempFile = new File(sourceFiles[1]);
            SourceFileResult result = new SourceFileResult(this, binFilePath, sourceFile, tempFile, 100);
            results.add(result);
            return;
        }

        List<SourceCodeFinder> searchFinders = getFinders();

        for (int i = 0; i < searchFinders.size() && !this.canceled; i++) {
            List<SourceFileResult> results2 = new ArrayList<>();
            SourceCodeFinder finder = searchFinders.get(i);
            Logger.debug(finder + " " + binFile, null); //$NON-NLS-1$

            finder.find(binFilePath, sha1, results2);
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
                finders.add(new NexusSourceCodeFinder(nexusUrl, nexusUser, nexusPassword));
            } else {
                finders.add(new NexusSourceCodeFinder(nexusUrl));
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
}