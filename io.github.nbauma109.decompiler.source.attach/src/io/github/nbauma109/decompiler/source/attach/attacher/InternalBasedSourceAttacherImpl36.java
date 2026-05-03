/*******************************************************************************
 * © 2017 Chen Chao (@cnfree)
 * © 2017 Pascal Bihler (@pbi-qfs)
 * © 2021 Jan Peter Stotz (@jpstotz)
 * © 2025-2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.attacher;

import java.io.File;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathSupport;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.swt.widgets.Shell;
import io.github.nbauma109.decompiler.util.Logger;

@SuppressWarnings("restriction")
public class InternalBasedSourceAttacherImpl36 implements SourceAttacher {

    @Override
    public boolean attachSource(final IPackageFragmentRoot fRoot, final File newSourcePath) throws CoreException {
        try {
            if (!validatePackageFragmentRoot(fRoot)) {
                return false;
            }

            final IJavaProject jproject = fRoot.getJavaProject();
            IClasspathEntry entry0 = JavaModelUtil.getClasspathEntry(fRoot);
            IPath containerPath = null;

            if (entry0.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
                ContainerResult result = handleContainerEntry(entry0, jproject, fRoot);
                if (result == null) {
                    return false;
                }
                containerPath = result.containerPath;
                entry0 = result.entry;
            }

            return performSourceAttachment(fRoot, newSourcePath, jproject, entry0, containerPath);
        } catch (CoreException e) {
            Logger.debug("error", e); //$NON-NLS-1$
            return false;
        }
    }

    private boolean validatePackageFragmentRoot(IPackageFragmentRoot fRoot) throws JavaModelException {
        if (fRoot == null || fRoot.getKind() != IPackageFragmentRoot.K_BINARY) {
            Logger.debug("error(!=K_BINARY)", null); //$NON-NLS-1$
            return false;
        }
        return true;
    }

    private static class ContainerResult {
        IPath containerPath;
        IClasspathEntry entry;

        ContainerResult(IPath containerPath, IClasspathEntry entry) {
            this.containerPath = containerPath;
            this.entry = entry;
        }
    }

    private ContainerResult handleContainerEntry(IClasspathEntry entry, IJavaProject jproject, IPackageFragmentRoot fRoot) throws CoreException {
        IPath containerPath = entry.getPath();
        final ClasspathContainerInitializer initializer = JavaCore
                .getClasspathContainerInitializer(containerPath.segment(0));
        final IClasspathContainer container = JavaCore.getClasspathContainer(containerPath, jproject);

        if (initializer == null || container == null) {
            Logger.debug("error(initializer == null || container == null)", null); //$NON-NLS-1$
            return null;
        }

        final IStatus status = initializer.getSourceAttachmentStatus(containerPath, jproject);
        if (status.getCode() == ClasspathContainerInitializer.ATTRIBUTE_NOT_SUPPORTED) {
            Logger.debug("error(ATTRIBUTE_NOT_SUPPORTED)", null); //$NON-NLS-1$
            return null;
        }
        if (status.getCode() == ClasspathContainerInitializer.ATTRIBUTE_READ_ONLY) {
            Logger.debug("error(ATTRIBUTE_READ_ONLY)", null); //$NON-NLS-1$
            return null;
        }

        IClasspathEntry updatedEntry = JavaModelUtil.findEntryInContainer(container, fRoot.getPath());
        return new ContainerResult(containerPath, updatedEntry);
    }

    private boolean performSourceAttachment(IPackageFragmentRoot fRoot, File newSourcePath,
            IJavaProject jproject, IClasspathEntry fEntry, IPath fContainerPath) throws CoreException {
        final CPListElement elem = CPListElement.createFromExisting(fEntry, (IJavaProject) null);
        IPath srcAttPath = computeSourcePath(newSourcePath, fEntry);

        elem.setAttribute("sourcepath", srcAttPath); //$NON-NLS-1$
        final IClasspathEntry entry2 = elem.getClasspathEntry();

        if (entry2.equals(fEntry)) {
            Logger.debug("NO CHANGE", null); //$NON-NLS-1$
            return true;
        }

        applySourceAttachment(fRoot, jproject, entry2, fEntry, fContainerPath);
        return true;
    }

    private IPath computeSourcePath(File newSourcePath, IClasspathEntry fEntry) throws JavaModelException {
        IPath srcAttPath = Path.fromOSString(newSourcePath.getAbsolutePath()).makeAbsolute();
        if (fEntry.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
            final File sourceAttacherDir = newSourcePath.getParentFile();
            JavaCore.setClasspathVariable("SOURCE_ATTACHER", //$NON-NLS-1$
                    new Path(sourceAttacherDir.getAbsolutePath()), (IProgressMonitor) null);
            srcAttPath = new Path("SOURCE_ATTACHER/" + newSourcePath.getName()); //$NON-NLS-1$
        }
        return srcAttPath;
    }

    private void applySourceAttachment(IPackageFragmentRoot fRoot, IJavaProject jproject,
            IClasspathEntry newEntry, IClasspathEntry fEntry, IPath fContainerPath) throws CoreException {
        final boolean isReferencedEntry = fEntry.getReferencingEntry() != null;
        final String[] changedAttributes = { "sourcepath" }; //$NON-NLS-1$

        int count = 0;
        while (count < 10) {
            BuildPathSupport.modifyClasspathEntry((Shell) null, newEntry, changedAttributes, jproject,
                    fContainerPath, isReferencedEntry, new NullProgressMonitor());
            if (fRoot.getSourceAttachmentPath() != null && fRoot.getSourceAttachmentPath().toFile().exists()) {
                break;
            }
            count++;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}