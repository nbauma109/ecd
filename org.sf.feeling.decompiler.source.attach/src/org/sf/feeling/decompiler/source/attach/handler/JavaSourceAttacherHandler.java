/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.handler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.sf.feeling.decompiler.source.attach.attacher.SourceAttacher;
import org.sf.feeling.decompiler.source.attach.finder.EclipseSourceReferencesSourceCodeFinder;
import org.sf.feeling.decompiler.source.attach.finder.FinderManager;
import org.sf.feeling.decompiler.source.attach.finder.JreSourceCodeFinder;
import org.sf.feeling.decompiler.source.attach.finder.SourceCheck;
import org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade;
import org.sf.feeling.decompiler.source.attach.finder.SourceFileResult;
import org.sf.feeling.decompiler.source.attach.i18n.Messages;
import org.sf.feeling.decompiler.source.attach.utils.SourceAttachUtil;
import org.sf.feeling.decompiler.source.attach.utils.SourceBindingUtil;
import org.sf.feeling.decompiler.source.attach.utils.SourceConstants;
import org.sf.feeling.decompiler.util.HashUtils;
import org.sf.feeling.decompiler.util.Logger;

public class JavaSourceAttacherHandler extends AbstractHandler {

	final static Map<String, IPackageFragmentRoot> requests = new HashMap<String, IPackageFragmentRoot>();

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (!(selection instanceof IStructuredSelection)) {
			return null;
		}
		final IStructuredSelection structuredSelection = (IStructuredSelection) selection;
		final List<IPackageFragmentRoot> selections = new ArrayList<IPackageFragmentRoot>();
		for (Iterator<?> iterator = structuredSelection.iterator(); iterator.hasNext();) {
			IJavaElement aSelection = (IJavaElement) iterator.next();
			if (aSelection instanceof IPackageFragmentRoot) {
				final IPackageFragmentRoot pkgRoot = (IPackageFragmentRoot) aSelection;
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
		final Set<String> notProcessedLibs = new HashSet<String>();
		notProcessedLibs.addAll(requests.keySet());
		final List<SourceFileResult> responses = Collections.synchronizedList(new ArrayList<SourceFileResult>());
		final List<String> libs = new ArrayList<String>();
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

	private static void processLibSources(final Set<String> notProcessedLibs, final List<SourceFileResult> responses) {

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
						if (SourceAttachUtil.reattchSource(pkgRoot, sourceFile, sourceTempFile, downloadUrl)) {
							String[] files = SourceBindingUtil.getSourceFileByDownloadUrl(downloadUrl);
							attachLibraries(response, pkgRoot, new File(files[1]), sourceFile);
						}
					} else if (attachSource(pkgRoot, sourceTempFile)) {
						SourceBindingUtil.saveSourceBindingRecord(sourceFile,
								HashUtils.sha1Hash(new File(response.getBinFile())), downloadUrl, sourceTempFile);
						String[] files = SourceBindingUtil.getSourceFileByDownloadUrl(downloadUrl);
						attachLibraries(response, pkgRoot, new File(files[1]), sourceFile);
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

	private static void attachLibraries(final SourceFileResult response, final IPackageFragmentRoot pkgRoot,
			final File sourceTempFile, final File sourceFile) {
		Thread thread = new Thread() {

			@Override
			public void run() {
				try {
					if (sourceFile.getName().startsWith("jre_")) //$NON-NLS-1$
					{
						attachJRELibrarySources(response, pkgRoot, sourceTempFile);
					}
					if (sourceFile.getName().startsWith("eclipse_")) //$NON-NLS-1$
					{
						attachEclipseLibrarySources(response, pkgRoot, sourceTempFile);
					}
				} catch (Exception e) {
					Logger.debug(e);
				}
			}
		};
		thread.setDaemon(true);
		thread.start();
	}

	private static void attachJRELibrarySources(final SourceFileResult response, final IPackageFragmentRoot pkgRoot,
			File sourceTempFile) throws Exception {
		if (response.getFinder() instanceof JreSourceCodeFinder) {
			IPackageFragmentRoot[] roots = pkgRoot.getJavaProject().getAllPackageFragmentRoots();
			for (int i = 0; i < roots.length; i++) {
				IPackageFragmentRoot element = roots[i];
				if (element.equals(pkgRoot))
					continue;
				List<String> paths = Arrays.asList(element.getPath().segments());
				if (paths.contains("jre")) //$NON-NLS-1$
				{
					if (element.getSourceAttachmentPath() == null
							|| element.getSourceAttachmentPath().toOSString() == null
							|| !new File(element.getSourceAttachmentPath().toOSString()).exists()) {
						attachSource(element, sourceTempFile);
					}
				}
			}
		}
	}

	private static void attachEclipseLibrarySources(final SourceFileResult response, final IPackageFragmentRoot pkgRoot,
			File sourceTempFile) throws Exception {
		if (response.getFinder() instanceof EclipseSourceReferencesSourceCodeFinder) {
			List<String> plugins = SourceAttachUtil.getEclipsePlugins(sourceTempFile);

			IPackageFragmentRoot[] roots = pkgRoot.getJavaProject().getAllPackageFragmentRoots();
			for (int i = 0; i < roots.length; i++) {
				IPackageFragmentRoot element = roots[i];
				if (element.equals(pkgRoot))
					continue;
				String fileName = element.getPath().lastSegment();
				if (plugins.contains(fileName.split("_")[0])) //$NON-NLS-1$
				{
					if (element.getSourceAttachmentPath() == null
							|| element.getSourceAttachmentPath().toOSString() == null
							|| !new File(element.getSourceAttachmentPath().toOSString()).exists()
							|| !SourceAttachUtil.isSourceCodeFor(element.getSourceAttachmentPath().toOSString(),
									SourceAttachUtil.getBinFile(element).getAbsolutePath())) {
						attachSource(element, sourceTempFile);
					}
				}
			}
		}
	}

	public static boolean attachSource(final IPackageFragmentRoot root, final File sourcePath) throws Exception {
		boolean attached = false;
		try {
			final SourceAttacher attacher = (SourceAttacher) Class
					.forName("org.sf.feeling.decompiler.source.attach.attacher.InternalBasedSourceAttacherImpl36") //$NON-NLS-1$
					.newInstance();
			Logger.debug("Trying (using InternalBasedSourceAttacherImpl36):  " + sourcePath, null); //$NON-NLS-1$
			attached = attacher.attachSource(root, sourcePath);
		} catch (Throwable e) {
			Logger.debug("Exception when trying InternalBasedSourceAttacherImpl36 to attach to " + sourcePath, e); //$NON-NLS-1$
		}
		if (!attached) {
			Logger.debug("Previous attempt failed:  " + sourcePath, null); //$NON-NLS-1$
			try {
				final SourceAttacher attacher = (SourceAttacher) Class
						.forName("org.sf.feeling.decompiler.source.attach.attacher.InternalBasedSourceAttacherImpl35") //$NON-NLS-1$
						.newInstance();
				Logger.debug("Trying (using InternalBasedSourceAttacherImpl35):  " + sourcePath, null); //$NON-NLS-1$
				attached = attacher.attachSource(root, sourcePath);
			} catch (Throwable e) {
				Logger.debug("Exception when trying InternalBasedSourceAttacherImpl35 to attach to " + sourcePath, e); //$NON-NLS-1$
			}
		}
		if (!attached) {
			Logger.debug("Previous attempt failed:  " + sourcePath, null); //$NON-NLS-1$
			try {
				final SourceAttacher attacher = (SourceAttacher) Class
						.forName("org.sf.feeling.decompiler.source.attach.attacher.MySourceAttacher") //$NON-NLS-1$
						.newInstance();
				Logger.debug("Trying (using MySourceAttacher):  " + sourcePath, null); //$NON-NLS-1$
				attached = attacher.attachSource(root, sourcePath);
			} catch (Throwable e) {
				Logger.debug("Exception when trying MySourceAttacher to attach to " + sourcePath, e); //$NON-NLS-1$
			}
		}
		if (!attached) {
			Logger.debug("Previous attempt failed:  " + sourcePath, null); //$NON-NLS-1$
			try {
				final SourceAttacher attacher = (SourceAttacher) Class
						.forName("org.sf.feeling.decompiler.source.attach.attacher.MySourceAttacher2") //$NON-NLS-1$
						.newInstance();
				Logger.debug("Trying (using MySourceAttacher2):  " + sourcePath, null); //$NON-NLS-1$
				attached = attacher.attachSource(root, sourcePath);
			} catch (Throwable e) {
				Logger.debug("Exception when trying MySourceAttacher2 to attach to " + sourcePath, e); //$NON-NLS-1$
			}
		}
		if (attached) {
			SourceAttachUtil.updateSourceAttachStatus(root);
			Logger.debug("Attached library source " + sourcePath, null); //$NON-NLS-1$
		} else {
			Logger.info("Failed to attach library source " + sourcePath); //$NON-NLS-1$
		}

		return attached;
	}
}
