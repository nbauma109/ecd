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
            processElementHierarchy(element, buildpathManager);
        } catch (JavaModelException e) {
            Logger.debug(e);
            handleDownloadException();
        }
    }

    private void processElementHierarchy(IJavaElement element, IClasspathManager buildpathManager) throws JavaModelException {
        while (element.getParent() != null) {
            element = element.getParent();
            if (element instanceof IPackageFragmentRoot) {
                root = (IPackageFragmentRoot) element;
                if (shouldProcessRoot(root)) {
                    scheduleSourceDownload(root, buildpathManager);
                    break;
                }
            }
        }
    }

    private boolean shouldProcessRoot(IPackageFragmentRoot root) throws JavaModelException {
        if (root.getPath() == null || root.getPath().toOSString() == null) {
            return false;
        }

        String pathString = root.getPath().toOSString();
        if (libraries.contains(pathString)) {
            return false;
        }

        libraries.add(pathString);

        if (!SourceAttachUtil.isMavenLibrary(root)) {
            return false;
        }

        if (hasExistingSourceAttachment(root)) {
            return false;
        }

        return true;
    }

    private boolean hasExistingSourceAttachment(IPackageFragmentRoot root) throws JavaModelException {
        IPath sourcePath = root.getSourceAttachmentPath();
        if (sourcePath != null && sourcePath.toOSString() != null) {
            File sourceFile = new File(sourcePath.toOSString());
            return sourceFile.exists() && sourceFile.isFile();
        }
        return false;
    }

    private void scheduleSourceDownload(IPackageFragmentRoot root, IClasspathManager buildpathManager) {
        buildpathManager.scheduleDownload(root, true, false);
        startSourcePollingThread(root);
    }

    private void startSourcePollingThread(IPackageFragmentRoot root) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                pollForSourceAttachment(root);
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    private void pollForSourceAttachment(IPackageFragmentRoot root) {
        if (!(root instanceof PackageFragmentRoot fRoot)) {
            return;
        }

        long startTime = System.currentTimeMillis();
        long timeout = 60 * 1000;

        while (true) {
            if (System.currentTimeMillis() - startTime > timeout) {
                new AttachSourceHandler().execute(root, true);
                break;
            }

            try {
                if (isSourceAttached(fRoot)) {
                    SourceAttachUtil.updateSourceAttachStatus(fRoot);
                    break;
                }
            } catch (JavaModelException e) {
                Logger.debug(e);
                break;
            }
        }
    }

    private boolean isSourceAttached(PackageFragmentRoot fRoot) throws JavaModelException {
        IPath sourcePath = fRoot.getSourceAttachmentPath();
        return sourcePath != null && sourcePath.toFile().exists();
    }

    private void handleDownloadException() {
        if (root == null) {
            return;
        }

        List<IPackageFragmentRoot> selections = new ArrayList<>();
        selections.add(root);

        Thread thread = new Thread() {
            @Override
            public void run() {
                JavaSourceAttacherHandler.updateSourceAttachments(selections, null);
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

}
