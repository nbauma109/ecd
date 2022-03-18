/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.sf.feeling.decompiler.source.attach.IAttachSourceHandler;
import org.sf.feeling.decompiler.source.attach.i18n.Messages;
import org.sf.feeling.decompiler.source.attach.utils.SourceAttachUtil;
import org.sf.feeling.decompiler.util.Logger;

public class AttachSourceHandler implements IAttachSourceHandler {

	@Override
	public Thread execute(final IPackageFragmentRoot library, final boolean showUI) {
		if (!showUI && SourceAttachUtil.isMavenLibrary(library) && SourceAttachUtil.enableMavenDownload()) {
			return null;
		}

		final List<IPackageFragmentRoot> selections = new ArrayList<>();
		selections.add(library);
		if (!selections.isEmpty()) {
			if (showUI) {
				final Job job = new Job(Messages.getString("AttachSourceHandler.Job.Name")) { //$NON-NLS-1$

					@Override
					protected IStatus run(final IProgressMonitor monitor) {
						return JavaSourceAttacherHandler.updateSourceAttachments(selections, monitor);
					}
				};
				job.setPriority(30);
				job.schedule();
			} else {
				Thread thread = new Thread() {

					@Override
					public void run() {
						JavaSourceAttacherHandler.updateSourceAttachments(selections, null);
					}
				};
				thread.setDaemon(true);
				thread.start();
				return thread;
			}
		}
		return null;

	}

	@Override
	public boolean syncAttachSource(final IPackageFragmentRoot root) {
		try {
			boolean download = SourceAttachUtil.needDownloadSource(Arrays.asList(root));

			if (download) {
				return SourceAttachUtil.refreshSourceAttachStatus(root);
			} else {
				return true;
			}
		} catch (Exception e) {
			Logger.debug(e);
		}
		return false;
	}
}
