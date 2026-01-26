/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.attacher;

import java.io.File;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

public class MySourceAttacher implements SourceAttacher {

    @Override
    public boolean attachSource(final IPackageFragmentRoot root, final File sourcePath) throws Exception {
        final IJavaProject javaProject = root.getJavaProject();
        final IClasspathEntry[] entries = javaProject.getRawClasspath().clone();
        boolean attached = false;
        for (int i = 0; i < entries.length; ++i) {
            final IClasspathEntry entry = entries[i];
            String entryPath;
            if (entry.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
                entryPath = JavaCore.getResolvedVariablePath(entry.getPath()).toOSString();
            } else {
                entryPath = entry.getPath().toOSString();
            }
            final String rootPath = root.getPath().toOSString();
            if (entryPath.equals(rootPath)) {
                entries[i] = addSourceAttachment(root, entries[i], sourcePath.getAbsolutePath(), null);
                attached = true;
                break;
            }
        }
        if (!attached) {
            root.attachSource(new Path(sourcePath.getAbsolutePath()), (IPath) null, (IProgressMonitor) null);
        }
        javaProject.setRawClasspath(entries, (IProgressMonitor) null);
        return true;
    }

    private static IClasspathEntry addSourceAttachment(final IPackageFragmentRoot root, final IClasspathEntry entry,
            final String sourcePath, final String sourceRoot) throws Exception {
        final int entryKind = entry.getEntryKind();
        IClasspathEntry result = null;
        switch (entryKind) {
            case 1: {
                result = JavaCore.newLibraryEntry(entry.getPath(), (sourcePath == null) ? null : new Path(sourcePath),
                        (sourceRoot == null) ? null : new Path(sourceRoot), entry.getAccessRules(),
                                entry.getExtraAttributes(), entry.isExported());
                break;
            }
            case 4: {
                final File sourceAttacherDir = new File(sourcePath).getParentFile();
                JavaCore.setClasspathVariable("SOURCE_ATTACHER", //$NON-NLS-1$
                        new Path(sourceAttacherDir.getAbsolutePath()), (IProgressMonitor) null);
                final Path varAttPath = new Path("SOURCE_ATTACHER/" + new File(sourcePath).getName()); //$NON-NLS-1$
                result = JavaCore.newVariableEntry(entry.getPath(), varAttPath,
                        (sourceRoot == null) ? null : new Path(sourceRoot), entry.getAccessRules(),
                                entry.getExtraAttributes(), entry.isExported());
                break;
            }
            default: {
                result = entry;
                break;
            }
        }
        return result;
    }
}