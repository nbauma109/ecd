/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.handler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import io.github.nbauma109.decompiler.source.attach.SourceAttachPlugin;
import io.github.nbauma109.decompiler.source.attach.attacher.SourceAttacher;
import io.github.nbauma109.decompiler.source.attach.finder.FinderManager;
import io.github.nbauma109.decompiler.source.attach.finder.SourceCheck;
import io.github.nbauma109.decompiler.source.attach.finder.SourceCodeFinderFacade;
import io.github.nbauma109.decompiler.source.attach.finder.SourceFileResult;
import io.github.nbauma109.decompiler.source.attach.i18n.Messages;
import io.github.nbauma109.decompiler.source.attach.utils.SourceAttachUtil;
import io.github.nbauma109.decompiler.source.attach.utils.SourceBindingUtil;
import io.github.nbauma109.decompiler.source.attach.utils.SourceConstants;
import io.github.nbauma109.decompiler.util.HashUtils;
import io.github.nbauma109.decompiler.util.Logger;

public class JavaSourceAttacherHandler extends AbstractHandler {

    static final Map<String, IPackageFragmentRoot> requests = new HashMap<>();

    @Override
    public Object execute(final ExecutionEvent event) throws ExecutionException {
        final ISelection selection = HandlerUtil.getCurrentSelection(event);
        if (!(selection instanceof IStructuredSelection structuredSelection)) {
            return null;
        }
        final List<IPackageFragmentRoot> selections = collectRoots(structuredSelection);
        filterAlreadyAttachedRoots(selections);

        if (!selections.isEmpty()) {
            final Job job = new Job(Messages.getString("JavaSourceAttacherHandler.Job.Name")) { //$NON-NLS-1$

                @Override
                protected IStatus run(final IProgressMonitor monitor) {
                    return JavaSourceAttacherHandler.updateSourceAttachments(selections, monitor);
                }
            };
            job.setPriority(30);
            job.schedule();
        }
        return null;
    }

    private static List<IPackageFragmentRoot> collectRoots(IStructuredSelection structuredSelection) {
        final List<IPackageFragmentRoot> selections = new ArrayList<>();
        for (Iterator<?> iterator = structuredSelection.iterator(); iterator.hasNext();) {
            IJavaElement aSelection = (IJavaElement) iterator.next();
            if (aSelection instanceof IPackageFragmentRoot pkgRoot) {
                selections.add(pkgRoot);
            } else if (aSelection instanceof IJavaProject p) {
                addProjectRoots(p, selections);
            }
        }
        return selections;
    }

    private static void addProjectRoots(IJavaProject project, List<IPackageFragmentRoot> selections) {
        try {
            for (IPackageFragmentRoot pkgRoot : project.getPackageFragmentRoots()) {
                selections.add(pkgRoot);
            }
        } catch (Exception e) {
            Logger.debug(e);
        }
    }

    private static void filterAlreadyAttachedRoots(List<IPackageFragmentRoot> selections) {
        final Iterator<IPackageFragmentRoot> it = selections.iterator();
        while (it.hasNext()) {
            final IPackageFragmentRoot pkgRoot = it.next();
            try {
                if (isAlreadyAttached(pkgRoot)) {
                    it.remove();
                } else if (!isEligibleBinaryArchive(pkgRoot)) {
                    it.remove();
                }
            } catch (Exception e) {
                Logger.debug(e);
            }
        }
    }

    private static boolean isEligibleBinaryArchive(IPackageFragmentRoot pkgRoot) throws Exception {
        if (pkgRoot.getKind() != IPackageFragmentRoot.K_BINARY || !pkgRoot.isArchive()) {
            return false;
        }
        int entryKind = pkgRoot.getRawClasspathEntry().getEntryKind();
        return entryKind == IClasspathEntry.CPE_LIBRARY
                || entryKind == IClasspathEntry.CPE_VARIABLE
                || entryKind == IClasspathEntry.CPE_CONTAINER;
    }

    private static boolean isAlreadyAttached(IPackageFragmentRoot pkgRoot) throws Exception {
        if (!isEligibleBinaryArchive(pkgRoot)) {
            return false;
        }
        final IPath source = pkgRoot.getSourceAttachmentPath();
        if (source == null || source.isEmpty() || !new File(source.toOSString()).exists()) {
            return false;
        }
        File binFile = pkgRoot.isExternal() ? pkgRoot.getPath().toFile()
                : pkgRoot.getResource().getLocation().toFile();
        return !SourceCheck.isWrongSource(new File(source.toOSString()), binFile);
    }

    public static IStatus updateSourceAttachments(final List<IPackageFragmentRoot> roots,
            final IProgressMonitor monitor) {

        for (final IPackageFragmentRoot pkgRoot : roots) {
            File file = getFileForRoot(pkgRoot);
            try {
                if (roots.size() == 1 && requests.containsKey(file.getCanonicalPath())) {
                    return Status.CANCEL_STATUS;
                }
                requests.put(file.getCanonicalPath(), pkgRoot);
            } catch (Exception e) {
                Logger.debug(e);
            }
        }
        final Set<String> notProcessedLibs = new HashSet<>();
        notProcessedLibs.addAll(requests.keySet());
        final List<SourceFileResult> responses = Collections.synchronizedList(new ArrayList<SourceFileResult>());
        final List<String> libs = new ArrayList<>();
        libs.addAll(requests.keySet());
        final FinderManager mgr = new FinderManager();
        mgr.findSources(libs, responses);
        waitForResults(mgr, monitor, notProcessedLibs, responses);

        mgr.cancel();
        if (!notProcessedLibs.isEmpty()) {
            processLibSources(notProcessedLibs, responses);
        }

        for (final IPackageFragmentRoot pkgRoot : roots) {
            File file = getFileForRoot(pkgRoot);
            try {
                requests.remove(file.getCanonicalPath());
            } catch (Exception e) {
                Logger.debug(e);
            }
        }

        return Status.OK_STATUS;
    }

    private static File getFileForRoot(IPackageFragmentRoot pkgRoot) {
        if (!pkgRoot.isExternal()) {
            return pkgRoot.getResource().getLocation().toFile();
        }
        return pkgRoot.getPath().toFile();
    }

    private static void waitForResults(FinderManager mgr, IProgressMonitor monitor,
            Set<String> notProcessedLibs, List<SourceFileResult> responses) {
        if (monitor == null) {
            waitWithoutMonitor(mgr, notProcessedLibs, responses);
        } else {
            waitWithMonitor(mgr, monitor, notProcessedLibs, responses);
        }
    }

    private static void waitWithoutMonitor(FinderManager mgr, Set<String> notProcessedLibs,
            List<SourceFileResult> responses) {
        while (mgr.isRunning() && !notProcessedLibs.isEmpty()) {
            processLibSources(notProcessedLibs, responses);
            sleepQuietly();
        }
    }

    private static void waitWithMonitor(FinderManager mgr, IProgressMonitor monitor,
            Set<String> notProcessedLibs, List<SourceFileResult> responses) {
        while (!monitor.isCanceled() && mgr.isRunning() && !notProcessedLibs.isEmpty()) {
            processLibSources(notProcessedLibs, responses);
            sleepQuietly();
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(1000L);
        } catch (Exception e) {
            Logger.debug(e);
        }
    }

    public static void processLibSources(final Set<String> notProcessedLibs, final List<SourceFileResult> responses) {

        while (!responses.isEmpty()) {
            final SourceFileResult response = responses.remove(0);
            final String binFile = response.getBinFile();
            if (!notProcessedLibs.contains(binFile) || response.getSource() == null) {
                continue;
            }
            final IPackageFragmentRoot pkgRoot = requests.get(binFile);
            try {
                processSourceFileResult(response, binFile, pkgRoot, notProcessedLibs);
            } catch (Exception e) {
                if (pkgRoot != null && pkgRoot.getResource() != null
                        && pkgRoot.getResource().getLocation() != null) {
                    Logger.debug("Cannot attach to " + pkgRoot.getResource().getLocation().toOSString(), e); //$NON-NLS-1$
                }
            }
        }
    }

    private static void processSourceFileResult(SourceFileResult response, String binFile,
            IPackageFragmentRoot pkgRoot, Set<String> notProcessedLibs) throws Exception {
        notProcessedLibs.remove(binFile);
        final String source = response.getSource();
        final String tempSource = response.getTempSource();
        final String suggestedSourceFileName = response.getSuggestedSourceFileName();
        final String downloadUrl = response.getFinder().getDownloadUrl();
        if (downloadUrl == null && !(response.getFinder() instanceof SourceCodeFinderFacade)) {
            return;
        }
        ensureSourceDirsExist();

        File[] sourceFiles = resolveSourceFiles(source, tempSource, suggestedSourceFileName);
        File sourceFile = sourceFiles[0];
        File sourceTempFile = sourceFiles[1];

        if (pkgRoot.getSourceAttachmentPath() != null
                && sourceTempFile.equals(pkgRoot.getSourceAttachmentPath().toFile())) {
            SourceAttachUtil.reattchSource(pkgRoot, sourceFile, sourceTempFile, downloadUrl);
        } else if (attachSource(pkgRoot, sourceTempFile)) {
            SourceBindingUtil.saveSourceBindingRecord(sourceFile,
                    HashUtils.sha1Hash(new File(binFile)), downloadUrl, sourceTempFile);
        }
    }

    private static void ensureSourceDirsExist() {
        if (!SourceConstants.SourceAttacherDir.exists()) {
            SourceConstants.SourceAttacherDir.mkdirs();
        }
        if (!SourceConstants.getSourceTempDir().exists()) {
            SourceConstants.getSourceTempDir().mkdirs();
        }
    }

    private static File[] resolveSourceFiles(String source, String tempSource, String suggestedSourceFileName)
            throws Exception {
        if (tempSource == null || !new File(tempSource).exists()) {
            return copyFromTempFile(source, suggestedSourceFileName);
        }
        return useExistingTempSource(source, tempSource, suggestedSourceFileName);
    }

    private static File[] copyFromTempFile(String source, String suggestedSourceFileName) throws Exception {
        File tempFile = new File(source);

        File sourceFile = new File(SourceConstants.SourceAttacherDir, suggestedSourceFileName);
        if (!sourceFile.exists()) {
            FileUtils.copyFile(tempFile, sourceFile);
        }

        File sourceTempFile = new File(SourceConstants.getSourceTempDir(), suggestedSourceFileName);
        if (!sourceTempFile.exists()) {
            FileUtils.copyFile(tempFile, sourceTempFile);
        }
        sourceTempFile.deleteOnExit();
        if (!tempFile.getAbsolutePath().startsWith(SourceConstants.SourceAttachPath)) {
            tempFile.delete();
        }
        return new File[] { sourceFile, sourceTempFile };
    }

    private static File[] useExistingTempSource(String source, String tempSource, String suggestedSourceFileName) {
        File sourceFile = new File(source);
        File sourceTempFile = new File(tempSource);

        boolean isInMavenRepo = sourceTempFile.getAbsolutePath()
                .startsWith(SourceConstants.USER_M2_REPO_DIR.getAbsolutePath());
        if (!isInMavenRepo && sourceTempFile.toPath().startsWith(SourceConstants.getSourceTempDir().toPath())) {
            sourceTempFile.deleteOnExit();
        }
        return new File[] { sourceFile, sourceTempFile };
    }

    public static boolean attachSource(final IPackageFragmentRoot root, final File sourcePath) {
        final SourceAttacher attacher = SourceAttachPlugin.getDefault().getSourceAttacher();
        if (attacher == null) {
            Logger.info("No SourceAttacher implementation found for " + sourcePath); //$NON-NLS-1$
            return false;
        }
        Logger.debug("Trying (using " + attacher.getClass().getSimpleName() + "):  " + sourcePath, null); //$NON-NLS-1$
        boolean attached;
        try {
            attached = attacher.attachSource(root, sourcePath);
        } catch (Throwable e) {
            Logger.debug("Exception when trying " + attacher.getClass().getSimpleName()
                    + " to attach to " + sourcePath, e); //$NON-NLS-1$
            attached = false;
        }
        if (attached) {
            SourceAttachUtil.updateSourceAttachStatus(root);
            Logger.debug("Attached library source " + sourcePath, null); //$NON-NLS-1$
        } else {
            Logger.info("Failed to attach library source " + sourcePath); //$NON-NLS-1$
        }

        return attached;
    }

    public static void clearRequests() {
        requests.clear();
    }

    public static void putRequest(String binFile, IPackageFragmentRoot root) {
        requests.put(binFile, root);
    }

    public static boolean containsRequest(String binFile) {
        return requests.containsKey(binFile);
    }
}
