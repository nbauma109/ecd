/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.attacher;

import java.io.File;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathSupport;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;

@SuppressWarnings("restriction")
public class MySourceAttacher2 implements SourceAttacher {

    @Override
    public boolean attachSource(final IPackageFragmentRoot root, final File sourcePath) throws Exception {
        IClasspathEntry entry;
        try {
            entry = JavaModelUtil.getClasspathEntry(root);
        } catch (JavaModelException ex) {
            if (!ex.isDoesNotExist()) {
                throw ex;
            }
            entry = null;
        }
        IPath containerPath = null;
        final IJavaProject jproject = root.getJavaProject();
        if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
            containerPath = entry.getPath();
            final IClasspathContainer container = JavaCore.getClasspathContainer(containerPath, jproject);
            entry = JavaModelUtil.findEntryInContainer(container, root.getPath());
            Assert.isNotNull(entry);
        }
        final IClasspathEntry newEntry = this.configureSourceAttachment(sourcePath.getAbsolutePath(), entry, jproject);
        this.applySourceAttachment(newEntry, jproject, containerPath, entry.getReferencingEntry() != null);
        return true;
    }

    private IClasspathEntry configureSourceAttachment(final String sourcePath, final IClasspathEntry initialEntry,
            final IJavaProject jproject) throws Exception {
        if (initialEntry == null) {
            throw new IllegalArgumentException();
        }
        final int entryKind = initialEntry.getEntryKind();
        if (entryKind != IClasspathEntry.CPE_LIBRARY && entryKind != IClasspathEntry.CPE_VARIABLE) {
            throw new IllegalArgumentException();
        }
        return this.getNewEntry(sourcePath, initialEntry, jproject);
    }

    public IClasspathEntry getNewEntry(final String sourcePath, final IClasspathEntry fEntry,
            final IJavaProject fProject) throws Exception {
        final CPListElement elem = CPListElement.createFromExisting(fEntry, fProject);
        final IPath sourceAttachmentPath = Path.fromOSString(sourcePath).makeAbsolute();
        final String encoding = ResourcesPlugin.getWorkspace().getRoot().getDefaultCharset();
        elem.setAttribute("sourcepath", sourceAttachmentPath); //$NON-NLS-1$
        elem.setAttribute("source_encoding", encoding); //$NON-NLS-1$
        return elem.getClasspathEntry();
    }

    private void applySourceAttachment(final IClasspathEntry newEntry, final IJavaProject project,
            final IPath containerPath, final boolean isReferencedEntry) throws Exception {
        final String[] changedAttributes = { "sourcepath", "source_encoding" //$NON-NLS-1$ //$NON-NLS-2$
        };
        BuildPathSupport.modifyClasspathEntry(null, newEntry, changedAttributes, project, containerPath,
                isReferencedEntry, new NullProgressMonitor());
    }
}
