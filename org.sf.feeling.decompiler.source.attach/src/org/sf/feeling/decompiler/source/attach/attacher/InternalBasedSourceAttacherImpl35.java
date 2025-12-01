/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.attacher;

import java.io.File;

import org.eclipse.core.runtime.Assert;
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
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathSupport;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.sf.feeling.decompiler.util.Logger;

@SuppressWarnings("restriction")
public class InternalBasedSourceAttacherImpl35 implements SourceAttacher {

	@Override
	public boolean attachSource(final IPackageFragmentRoot fRoot, final File newSourcePath) throws CoreException {
		try {
			IPath fContainerPath = null;
			if (fRoot == null || fRoot.getKind() != IPackageFragmentRoot.K_BINARY) {
				Logger.debug("error(!=K_BINARY)", null); //$NON-NLS-1$
				return false;
			}
			IPath containerPath = null;
			final IJavaProject jproject = fRoot.getJavaProject();
			IClasspathEntry entry = fRoot.getRawClasspathEntry();
			if (entry == null) {
				entry = JavaCore.newLibraryEntry(fRoot.getPath(), (IPath) null, (IPath) null);
			} else if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				containerPath = entry.getPath();
				final ClasspathContainerInitializer initializer = JavaCore
						.getClasspathContainerInitializer(containerPath.segment(0));
				final IClasspathContainer container = JavaCore.getClasspathContainer(containerPath, jproject);
				if (initializer == null || container == null) {
					Logger.debug("error(initializer == null || container == null)", null); //$NON-NLS-1$
					return false;
				}
				final IStatus status = initializer.getSourceAttachmentStatus(containerPath, jproject);
				if (status.getCode() == ClasspathContainerInitializer.ATTRIBUTE_NOT_SUPPORTED) {
					Logger.debug("error(ATTRIBUTE_NOT_SUPPORTED)", null); //$NON-NLS-1$
					return false;
				}
				if (status.getCode() == ClasspathContainerInitializer.ATTRIBUTE_READ_ONLY) {
					Logger.debug("error(ATTRIBUTE_READ_ONLY)", null); //$NON-NLS-1$
					return false;
				}
				entry = JavaModelUtil.findEntryInContainer(container, fRoot.getPath());
				Assert.isNotNull(entry);
			}
			fContainerPath = containerPath;
			IClasspathEntry fEntry = entry;
			final CPListElement elem = CPListElement.createFromExisting(fEntry, (IJavaProject) null);
			IPath srcAttPath = Path.fromOSString(newSourcePath.getAbsolutePath()).makeAbsolute();
			if (fEntry.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
				final File sourceAttacherDir = newSourcePath.getParentFile();
				JavaCore.setClasspathVariable("SOURCE_ATTACHER", //$NON-NLS-1$
						new Path(sourceAttacherDir.getAbsolutePath()), (IProgressMonitor) null);
				srcAttPath = new Path("SOURCE_ATTACHER/" + newSourcePath.getName()); //$NON-NLS-1$
			}
			elem.setAttribute("sourcepath", srcAttPath); //$NON-NLS-1$
			final IClasspathEntry entry2 = elem.getClasspathEntry();
			if (entry2.equals(fEntry)) {
				Logger.debug("NO CHANGE", null); //$NON-NLS-1$
				return true;
			}
			final IClasspathEntry newEntry = entry2;
			final String[] changedAttributes = { "sourcepath" //$NON-NLS-1$
			};
			final boolean isReferencedEntry = fEntry.getReferencingEntry() != null;
			BuildPathSupport.modifyClasspathEntry(null, newEntry, changedAttributes, jproject, fContainerPath, isReferencedEntry, new NullProgressMonitor());
		} catch (CoreException e4) {
			Logger.debug("error", e4); //$NON-NLS-1$
			return false;
		}
		return true;
	}
}