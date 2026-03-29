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

        final List<IPackageFragmentRoot> selections = collectFragmentRoots(structuredSelection);
        filterInvalidRoots(selections);

        if (!selections.isEmpty()) {
            scheduleSourceAttachmentJob(selections);
        }
        return null;
    }

    private List<IPackageFragmentRoot> collectFragmentRoots(IStructuredSelection structuredSelection) {
        final List<IPackageFragmentRoot> selections = new ArrayList<>();
        for (Iterator<?> iterator = structuredSelection.iterator(); iterator.hasNext();) {
            IJavaElement aSelection = (IJavaElement) iterator.next();
            if (aSelection instanceof IPackageFragmentRoot pkgRoot) {
                selections.add(pkgRoot);
            } else if (aSelection instanceof IJavaProject p) {
                addProjectFragmentRoots(p, selections);
            }
        }
        return selections;
    }

    private void addProjectFragmentRoots(IJavaProject project, List<IPackageFragmentRoot> selections) {
        try {
            IPackageFragmentRoot[] packageFragmentRoots = project.getPackageFragmentRoots();
            for (IPackageFragmentRoot pkgRoot : packageFragmentRoots) {
                selections.add(pkgRoot);
            }
        } catch (Exception e) {
            Logger.debug(e);
        }
    }

    private void filterInvalidRoots(List<IPackageFragmentRoot> selections) {
        final Iterator<IPackageFragmentRoot> it = selections.iterator();
        while (it.hasNext()) {
            final IPackageFragmentRoot pkgRoot = it.next();
            if (!isValidRootForAttachment(pkgRoot)) {
                it.remove();
            }
        }
    }

    private boolean isValidRootForAttachment(IPackageFragmentRoot pkgRoot) {
        try {
            if (!isBinaryArchiveLibrary(pkgRoot)) {
                return false;
            }

            final IPath source = pkgRoot.getSourceAttachmentPath();
            if (source == null || source.isEmpty() || !new File(source.toOSString()).exists()) {
                return false;
            }

            File binFile = getFragmentRootFile(pkgRoot);
            return !SourceCheck.isWrongSource(new File(source.toOSString()), binFile);
        } catch (Exception e) {
            Logger.debug(e);
            return false;
        }
    }

    private boolean isBinaryArchiveLibrary(IPackageFragmentRoot pkgRoot) throws JavaModelException {
        if (pkgRoot.getKind() != IPackageFragmentRoot.K_BINARY || !pkgRoot.isArchive()) {
            return false;
        }

        int entryKind = pkgRoot.getRawClasspathEntry().getEntryKind();
        return entryKind == IClasspathEntry.CPE_LIBRARY
                || entryKind == IClasspathEntry.CPE_VARIABLE
                || entryKind == IClasspathEntry.CPE_CONTAINER;
    }

    private File getFragmentRootFile(IPackageFragmentRoot pkgRoot) throws JavaModelException {
        if (!pkgRoot.isExternal()) {
            return pkgRoot.getResource().getLocation().toFile();
        }
        return pkgRoot.getPath().toFile();
    }

    private void scheduleSourceAttachmentJob(List<IPackageFragmentRoot> selections) {
        final Job job = new Job(Messages.getString("JavaSourceAttacherHandler.Job.Name")) { //$NON-NLS-1$
            @Override
            protected IStatus run(final IProgressMonitor monitor) {
                return JavaSourceAttacherHandler.updateSourceAttachments(selections, monitor);
            }
        };
        job.setPriority(30);
        job.schedule();
    }

    public static IStatus updateSourceAttachments(final List<IPackageFragmentRoot> roots,
            final IProgressMonitor monitor) {

        registerRoots(roots);

        final Set<String> notProcessedLibs = new HashSet<>(requests.keySet());
        final List<SourceFileResult> responses = Collections.synchronizedList(new ArrayList<>());
        final List<String> libs = new ArrayList<>(requests.keySet());

        final FinderManager mgr = new FinderManager();
        mgr.findSources(libs, responses);

        waitForSourceDiscovery(monitor, mgr, notProcessedLibs, responses);

        mgr.cancel();
        if (!notProcessedLibs.isEmpty()) {
            processLibSources(notProcessedLibs, responses);
        }

        unregisterRoots(roots);

        return Status.OK_STATUS;
    }

    private static void registerRoots(List<IPackageFragmentRoot> roots) {
        for (final IPackageFragmentRoot pkgRoot : roots) {
            try {
                File file = getFragmentRootFileStatic(pkgRoot);
                String canonicalPath = file.getCanonicalPath();

                if (roots.size() == 1 && requests.containsKey(canonicalPath)) {
                    return;
                }
                requests.put(canonicalPath, pkgRoot);
            } catch (Exception e) {
                Logger.debug(e);
            }
        }
    }

    private static void unregisterRoots(List<IPackageFragmentRoot> roots) {
        for (final IPackageFragmentRoot pkgRoot : roots) {
            try {
                File file = getFragmentRootFileStatic(pkgRoot);
                requests.remove(file.getCanonicalPath());
            } catch (Exception e) {
                Logger.debug(e);
            }
        }
    }

    private static File getFragmentRootFileStatic(IPackageFragmentRoot pkgRoot) throws JavaModelException {
        if (!pkgRoot.isExternal()) {
            return pkgRoot.getResource().getLocation().toFile();
        }
        return pkgRoot.getPath().toFile();
    }

    private static void waitForSourceDiscovery(IProgressMonitor monitor, FinderManager mgr,
            Set<String> notProcessedLibs, List<SourceFileResult> responses) {
        if (monitor == null) {
            waitWithoutMonitor(mgr, notProcessedLibs, responses);
        } else {
            waitWithMonitor(monitor, mgr, notProcessedLibs, responses);
        }
    }

    private static void waitWithoutMonitor(FinderManager mgr, Set<String> notProcessedLibs,
            List<SourceFileResult> responses) {
        while (mgr.isRunning() && !notProcessedLibs.isEmpty()) {
            processLibSources(notProcessedLibs, responses);
            sleepForDiscovery();
        }
    }

    private static void waitWithMonitor(IProgressMonitor monitor, FinderManager mgr,
            Set<String> notProcessedLibs, List<SourceFileResult> responses) {
        while (!monitor.isCanceled() && mgr.isRunning() && !notProcessedLibs.isEmpty()) {
            processLibSources(notProcessedLibs, responses);
            sleepForDiscovery();
        }
    }

    private static void sleepForDiscovery() {
        try {
            Thread.sleep(1000L);
        } catch (Exception e) {
            Logger.debug(e);
        }
    }

    public static void processLibSources(final Set<String> notProcessedLibs, final List<SourceFileResult> responses) {
        while (!responses.isEmpty()) {
            final SourceFileResult response = responses.remove(0);
            if (shouldProcessResponse(response, notProcessedLibs)) {
                processSourceResponse(response, notProcessedLibs);
            }
        }
    }

    private static boolean shouldProcessResponse(SourceFileResult response, Set<String> notProcessedLibs) {
        final String binFile = response.getBinFile();
        return notProcessedLibs.contains(binFile) && response.getSource() != null;
    }

    private static void processSourceResponse(SourceFileResult response, Set<String> notProcessedLibs) {
        final IPackageFragmentRoot pkgRoot = requests.get(response.getBinFile());
        try {
            notProcessedLibs.remove(response.getBinFile());

            if (!isValidFinder(response)) {
                return;
            }

            ensureDirectoriesExist();
            SourceFilePair sourceFiles = prepareSourceFiles(response);
            attachSourceToRoot(pkgRoot, response, sourceFiles);
        } catch (Exception e) {
            logAttachmentError(pkgRoot, e);
        }
    }

    private static boolean isValidFinder(SourceFileResult response) {
        final String downloadUrl = response.getFinder().getDownloadUrl();
        return downloadUrl != null || response.getFinder() instanceof SourceCodeFinderFacade;
    }

    private static void ensureDirectoriesExist() {
        if (!SourceConstants.SourceAttacherDir.exists()) {
            SourceConstants.SourceAttacherDir.mkdirs();
        }
        if (!SourceConstants.getSourceTempDir().exists()) {
            SourceConstants.getSourceTempDir().mkdirs();
        }
    }

    private static class SourceFilePair {
        File sourceFile;
        File sourceTempFile;

        SourceFilePair(File sourceFile, File sourceTempFile) {
            this.sourceFile = sourceFile;
            this.sourceTempFile = sourceTempFile;
        }
    }

    private static SourceFilePair prepareSourceFiles(SourceFileResult response) throws IOException {
        final String source = response.getSource();
        final String tempSource = response.getTempSource();
        final String suggestedSourceFileName = response.getSuggestedSourceFileName();

        if (tempSource == null || !new File(tempSource).exists()) {
            return prepareSourceFilesFromTemp(source, suggestedSourceFileName);
        }
        return prepareSourceFilesFromExisting(source, tempSource);
    }

    private static SourceFilePair prepareSourceFilesFromTemp(String source, String suggestedSourceFileName)
            throws IOException {
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

        return new SourceFilePair(sourceFile, sourceTempFile);
    }

    private static SourceFilePair prepareSourceFilesFromExisting(String source, String tempSource) {
        File sourceFile = new File(source);
        File sourceTempFile = new File(tempSource);

        boolean isInMavenRepo = sourceTempFile.getAbsolutePath()
                .startsWith(SourceConstants.USER_M2_REPO_DIR.getAbsolutePath());
        if (!isInMavenRepo && sourceTempFile.toPath().startsWith(SourceConstants.getSourceTempDir().toPath())) {
            sourceTempFile.deleteOnExit();
        }

        return new SourceFilePair(sourceFile, sourceTempFile);
    }

    private static void attachSourceToRoot(IPackageFragmentRoot pkgRoot, SourceFileResult response,
            SourceFilePair sourceFiles) throws Exception {
        final String downloadUrl = response.getFinder().getDownloadUrl();

        if (shouldReattachSource(pkgRoot, sourceFiles.sourceTempFile)) {
            SourceAttachUtil.reattchSource(pkgRoot, sourceFiles.sourceFile, sourceFiles.sourceTempFile, downloadUrl);
        } else if (attachSource(pkgRoot, sourceFiles.sourceTempFile)) {
            saveSourceBinding(response, sourceFiles.sourceFile, sourceFiles.sourceTempFile, downloadUrl);
        }
    }

    private static boolean shouldReattachSource(IPackageFragmentRoot pkgRoot, File sourceTempFile)
            throws JavaModelException {
        return pkgRoot.getSourceAttachmentPath() != null
                && sourceTempFile.equals(pkgRoot.getSourceAttachmentPath().toFile());
    }

    private static void saveSourceBinding(SourceFileResult response, File sourceFile, File sourceTempFile,
            String downloadUrl) throws Exception {
        SourceBindingUtil.saveSourceBindingRecord(sourceFile, HashUtils.sha1Hash(new File(response.getBinFile())),
                downloadUrl, sourceTempFile);
    }

    private static void logAttachmentError(IPackageFragmentRoot pkgRoot, Exception e) {
        if (pkgRoot != null && pkgRoot.getResource() != null && pkgRoot.getResource().getLocation() != null) {
            Logger.debug("Cannot attach to " + pkgRoot.getResource().getLocation().toOSString(), e); //$NON-NLS-1$
        }
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
