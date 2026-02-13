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

import io.github.nbauma109.decompiler.extension.DecompilerAdapterManager;
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
        if (!(selection instanceof IStructuredSelection)) {
            return null;
        }
        final IStructuredSelection structuredSelection = (IStructuredSelection) selection;
        final List<IPackageFragmentRoot> selections = new ArrayList<>();
        for (Iterator<?> iterator = structuredSelection.iterator(); iterator.hasNext();) {
            IJavaElement aSelection = (IJavaElement) iterator.next();
            if (aSelection instanceof IPackageFragmentRoot pkgRoot) {
                selections.add(pkgRoot);
            } else {
                if (!(aSelection instanceof IJavaProject)) {
                    continue;
                }
                final IJavaProject p = (IJavaProject) aSelection;
                try {
                    IPackageFragmentRoot[] packageFragmentRoots;
                    for (int length = (packageFragmentRoots = p.getPackageFragmentRoots()).length,
                            i = 0; i < length; ++i) {
                        final IPackageFragmentRoot pkgRoot2 = packageFragmentRoots[i];
                        selections.add(pkgRoot2);
                    }
                } catch (Exception e) {
                    Logger.debug(e);
                }
            }
        }
        final Iterator<IPackageFragmentRoot> it = selections.iterator();
        while (it.hasNext()) {
            final IPackageFragmentRoot pkgRoot3 = it.next();
            try {
                if (pkgRoot3.getKind() == IPackageFragmentRoot.K_BINARY && pkgRoot3.isArchive()
                        && (pkgRoot3.getRawClasspathEntry().getEntryKind() == IClasspathEntry.CPE_LIBRARY
                        || pkgRoot3.getRawClasspathEntry().getEntryKind() == IClasspathEntry.CPE_VARIABLE
                        || pkgRoot3.getRawClasspathEntry().getEntryKind() == IClasspathEntry.CPE_CONTAINER)) {
                    final IPath source = pkgRoot3.getSourceAttachmentPath();
                    if (source == null || source.isEmpty() || !new File(source.toOSString()).exists()) {
                        continue;
                    }
                    File binFile;
                    if (!pkgRoot3.isExternal()) {
                        binFile = pkgRoot3.getResource().getLocation().toFile();
                    } else {
                        binFile = pkgRoot3.getPath().toFile();
                    }
                    if (SourceCheck.isWrongSource(new File(source.toOSString()), binFile)) {
                        continue;
                    }
                    it.remove();
                } else {
                    it.remove();
                }
            } catch (Exception e2) {
                Logger.debug(e2);
            }
        }

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

    public static IStatus updateSourceAttachments(final List<IPackageFragmentRoot> roots,
            final IProgressMonitor monitor) {

        for (final IPackageFragmentRoot pkgRoot : roots) {
            File file;

            if (!pkgRoot.isExternal()) {
                file = pkgRoot.getResource().getLocation().toFile();
            } else {
                file = pkgRoot.getPath().toFile();
            }
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
        if (monitor == null) {
            while (mgr.isRunning() && !notProcessedLibs.isEmpty()) {
                processLibSources(notProcessedLibs, responses);
                try {
                    Thread.sleep(1000L);
                } catch (Exception e2) {
                    Logger.debug(e2);
                }
            }
        } else {
            while (!monitor.isCanceled() && mgr.isRunning() && !notProcessedLibs.isEmpty()) {
                processLibSources(notProcessedLibs, responses);
                try {
                    Thread.sleep(1000L);
                } catch (Exception e2) {
                    Logger.debug(e2);
                }
            }
        }

        mgr.cancel();
        if (!notProcessedLibs.isEmpty()) {
            processLibSources(notProcessedLibs, responses);
        }

        for (final IPackageFragmentRoot pkgRoot : roots) {
            File file;

            if (!pkgRoot.isExternal()) {
                file = pkgRoot.getResource().getLocation().toFile();
            } else {
                file = pkgRoot.getPath().toFile();
            }
            try {

                requests.remove(file.getCanonicalPath());
            } catch (Exception e) {
                Logger.debug(e);
            }
        }

        return Status.OK_STATUS;
    }

    public static void processLibSources(final Set<String> notProcessedLibs, final List<SourceFileResult> responses) {

        while (!responses.isEmpty()) {
            final SourceFileResult response = responses.remove(0);
            final String binFile = response.getBinFile();
            if (notProcessedLibs.contains(binFile) && response.getSource() != null) {
                final IPackageFragmentRoot pkgRoot = requests.get(binFile);
                try {
                    notProcessedLibs.remove(response.getBinFile());
                    final String source = response.getSource();
                    final String tempSource = response.getTempSource();
                    final String suggestedSourceFileName = response.getSuggestedSourceFileName();
                    final String downloadUrl = response.getFinder().getDownloadUrl();
                    if (downloadUrl == null && !(response.getFinder() instanceof SourceCodeFinderFacade)) {
                        continue;
                    }
                    if (!SourceConstants.SourceAttacherDir.exists()) {
                        SourceConstants.SourceAttacherDir.mkdirs();
                    }

                    if (!SourceConstants.getSourceTempDir().exists()) {
                        SourceConstants.getSourceTempDir().mkdirs();
                    }

                    File sourceTempFile;
                    File sourceFile;
                    if (tempSource == null || !new File(tempSource).exists()) {
                        File tempFile = new File(source);
                        sourceFile = new File(SourceConstants.SourceAttacherDir, suggestedSourceFileName);
                        if (!sourceFile.exists()) {
                            FileUtils.copyFile(tempFile, sourceFile);
                        }

                        sourceTempFile = new File(SourceConstants.getSourceTempDir(), suggestedSourceFileName);
                        if (!sourceTempFile.exists()) {
                            FileUtils.copyFile(tempFile, sourceTempFile);
                        }
                        sourceTempFile.deleteOnExit();
                        if (!tempFile.getAbsolutePath().startsWith(SourceConstants.SourceAttachPath)) {
                            tempFile.delete();
                        }
                    } else {
                        sourceFile = new File(source);
                        sourceTempFile = new File(tempSource);
                        if (sourceTempFile.toPath().startsWith(SourceConstants.getSourceTempDir().toPath())) {
                            sourceTempFile.deleteOnExit();
                        }
                    }

                    if (pkgRoot.getSourceAttachmentPath() != null
                            && sourceTempFile.equals(pkgRoot.getSourceAttachmentPath().toFile())) {
                        SourceAttachUtil.reattchSource(pkgRoot, sourceFile, sourceTempFile, downloadUrl);
                    } else if (attachSource(pkgRoot, sourceTempFile)) {
                        SourceBindingUtil.saveSourceBindingRecord(sourceFile,
                                HashUtils.sha1Hash(new File(response.getBinFile())), downloadUrl, sourceTempFile);
                    }
                } catch (Exception e) {
                    if (pkgRoot != null && pkgRoot.getResource() != null
                            && pkgRoot.getResource().getLocation() != null) {
                        Logger.debug("Cannot attach to " + pkgRoot.getResource().getLocation().toOSString(), e); //$NON-NLS-1$
                    }
                }
            }
        }
    }

    public static boolean attachSource(final IPackageFragmentRoot root, final File sourcePath) throws Exception {
        final SourceAttacher attacher = getSourceAttacher();
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

    private static SourceAttacher getSourceAttacher() {
        return (SourceAttacher) DecompilerAdapterManager.getAdapter(SourceAttachPlugin.getDefault(), SourceAttacher.class);
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
