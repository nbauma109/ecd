/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.m2e;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.PackageFragmentRoot;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.m2e.jdt.IClasspathManager;
import org.eclipse.m2e.jdt.MavenJdtPlugin;
import org.eclipse.ui.IEditorPart;
import io.github.nbauma109.decompiler.source.attach.handler.AttachSourceHandler;
import io.github.nbauma109.decompiler.source.attach.handler.JavaSourceAttacherHandler;
import io.github.nbauma109.decompiler.source.attach.utils.SourceAttachUtil;
import io.github.nbauma109.decompiler.util.Logger;

@SuppressWarnings("restriction")
public class MavenSourceDownloader {

    private IPackageFragmentRoot root = null;
    private static Set<String> libraries = new ConcurrentSkipListSet<>();

    public void downloadSource(IEditorPart part) {
        root = null;
        try {
            IClasspathManager buildpathManager = MavenJdtPlugin.getDefault().getBuildpathManager();
            IClassFileEditorInput input = (IClassFileEditorInput) part.getEditorInput();
            IJavaElement element = input.getClassFile();
            while (element.getParent() != null) {
                element = element.getParent();
                if (element instanceof IPackageFragmentRoot pkgRoot) {
                    if (scheduleDownloadIfNeeded(buildpathManager, pkgRoot)) {
                        break;
                    }
                }
            }
        } catch (JavaModelException e) {
            Logger.debug(e);
            scheduleAttachSourceFallback();
        }
    }

    private boolean scheduleDownloadIfNeeded(IClasspathManager buildpathManager, IPackageFragmentRoot pkgRoot)
            throws JavaModelException {
        root = pkgRoot;
        if (root.getPath() == null || root.getPath().toOSString() == null) {
            return false;
        }
        if (libraries.contains(root.getPath().toOSString())) {
            return false;
        }
        libraries.add(root.getPath().toOSString());
        if (!SourceAttachUtil.isMavenLibrary(root)) {
            return false;
        }
        final IPath sourcePath = root.getSourceAttachmentPath();
        if (sourcePath != null && sourcePath.toOSString() != null) {
            File tempfile = new File(sourcePath.toOSString());
            if (tempfile.exists() && tempfile.isFile()) {
                return true;
            }
        }
        buildpathManager.scheduleDownload(root, true, false);
        startSourceDownloadWatchThread(root);
        return false;
    }

    private static void startSourceDownloadWatchThread(IPackageFragmentRoot fragmentRoot) {
        Thread thread = new Thread(() -> waitForSourceAndAttach(fragmentRoot));
        thread.setDaemon(true);
        thread.start();
    }

    private static void waitForSourceAndAttach(IPackageFragmentRoot fragmentRoot) {
        if (!(fragmentRoot instanceof PackageFragmentRoot fRoot)) {
            return;
        }
        long time = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() - time > 60 * 1000) {
                new AttachSourceHandler().execute(fragmentRoot, true);
                break;
            }
            try {
                if (fRoot.getSourceAttachmentPath() != null && fRoot.getSourceAttachmentPath().toFile().exists()) {
                    SourceAttachUtil.updateSourceAttachStatus(fRoot);
                    break;
                }
            } catch (JavaModelException e) {
                Logger.debug(e);
                break;
            }
        }
    }

    private void scheduleAttachSourceFallback() {
        if (root == null) {
            return;
        }
        final List<IPackageFragmentRoot> selections = new ArrayList<>();
        selections.add(root);
        Thread thread = new Thread(() -> JavaSourceAttacherHandler.updateSourceAttachments(selections, null));
        thread.setDaemon(true);
        thread.start();
    }

}
