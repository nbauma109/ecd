/*******************************************************************************
 * Copyright (c) 2026 nbauma109.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.finder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.jface.preference.IPreferenceStore;
import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.source.attach.utils.SourceAttachUtil;
import io.github.nbauma109.decompiler.source.attach.utils.SourceBindingUtil;
import io.github.nbauma109.decompiler.util.Logger;

public class SourceCodeFinderFacade extends AbstractSourceCodeFinder {

    static final String HTTPS_REPOSITORY_CLOUDERA_COM_ARTIFACTORY = "https://repository.cloudera.com";
    static final String HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML = "https://maven.alfresco.com/nexus";
    static final String HTTPS_REPOSITORY_APACHE_ORG_INDEX_HTML = "https://repository.apache.org";
    static final String HTTPS_REPO_GRAILS_ORG_GRAILS = "https://repo.grails.org/grails/webapp/home.html";

    public static boolean hasConfiguredSourceProvider() {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        return !prefs.getString(JavaDecompilerPlugin.NEXUS_URL).isBlank()
                || prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_CENTRAL)
                || prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_SONATYPE_CENTRAL)
                || prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_JITPACK)
                || prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_CLOUDERA)
                || prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_ALFRESCO)
                || prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_APACHE_ORG)
                || prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_GRAILS_ORG);
    }

    private static List<SourceCodeFinder> getFinders() {
        List<SourceCodeFinder> finders = new ArrayList<>();
        finders.add(new LocalSourceFinder());
        addPrivateNexusRepo(finders);
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_CENTRAL)) {
            finders.add(new MavenRepoSourceCodeFinder());
        }
        if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_SONATYPE_CENTRAL)) {
            finders.add(new SonatypeSourceCodeFinder());
        }
        if (prefs.getBoolean(JavaDecompilerPlugin.PUBLIC_REPO_JITPACK)) {
            finders.add(new JitPackSourceCodeFinder());
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
            results.add(createCachedSourceResult(binFilePath, sourceFiles));
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

    private SourceFileResult createCachedSourceResult(String binFilePath, String[] sourceFiles) {
        File sourceFile = new File(sourceFiles[0]);
        File tempFile = sourceFiles.length > 1 && sourceFiles[1] != null ? new File(sourceFiles[1]) : sourceFile;
        File mavenRepoSourceFile = persistShaCachedSourceInMavenRepo(binFilePath, sourceFile);
        if (mavenRepoSourceFile != null) {
            sourceFile = mavenRepoSourceFile;
            tempFile = mavenRepoSourceFile;
        } else if (!tempFile.exists()) {
            tempFile = sourceFile;
        }
        return new SourceFileResult(this, binFilePath, sourceFile, tempFile, 100);
    }

    private File persistShaCachedSourceInMavenRepo(String binFilePath, File sourceFile) {
        try {
            Optional<GAV> gav = findGAVFromFile(binFilePath);
            if (!gav.isPresent()) {
                return null;
            }
            File mavenRepoSourceFile = getMavenRepoSourceFile(gav.get());
            if (mavenRepoSourceFile == null) {
                return null;
            }
            if (mavenRepoSourceFile.exists() && SourceAttachUtil.isSourceCodeFor(mavenRepoSourceFile.getAbsolutePath(), binFilePath)) {
                return mavenRepoSourceFile;
            }
            if (mavenRepoSourceFile.exists()) {
                Logger.debug("Replacing stale/invalid Maven repo source: " + mavenRepoSourceFile.getAbsolutePath(), null); //$NON-NLS-1$
            }
            if (!SourceAttachUtil.isSourceCodeFor(sourceFile.getAbsolutePath(), binFilePath)) {
                Logger.debug("SHA-cached source does not match binary, skipping Maven repo copy: " + sourceFile.getAbsolutePath(), null); //$NON-NLS-1$
                return null;
            }
            File parent = mavenRepoSourceFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                return null;
            }
            Files.copy(sourceFile.toPath(), mavenRepoSourceFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return mavenRepoSourceFile;
        } catch (IOException e) {
            Logger.debug(e);
        }
        return null;
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
