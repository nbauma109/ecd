/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.actions;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipOutputStream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.i18n.Messages;
import io.github.nbauma109.decompiler.util.DecompileUtil;
import io.github.nbauma109.decompiler.util.FileUtil;
import io.github.nbauma109.decompiler.util.Logger;
import io.github.nbauma109.decompiler.util.UIUtil;

public class ExportSourceAction extends Action {

    private static final String[] ZIP_FILTER = { "*.zip" };

    private List<?> selection = null;
    private boolean isFlat = false;

    public ExportSourceAction(List<?> selection) {
        super(Messages.getString("ExportSourceAction.Action.Text")); //$NON-NLS-1$
        this.setImageDescriptor(JavaDecompilerPlugin.getImageDescriptor("icons/etool16/export_wiz.png")); //$NON-NLS-1$
        this.setDisabledImageDescriptor(JavaDecompilerPlugin.getImageDescriptor("icons/dtool16/export_wiz.png")); //$NON-NLS-1$
        this.selection = selection;
        this.isFlat = UIUtil.isPackageFlat();
    }

    @Override
    public void run() {
        if (selection == null || selection.isEmpty()) {
            return;
        }

        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        final String decompilerType = prefs.getString(JavaDecompilerPlugin.DECOMPILER_TYPE);
        final boolean reuseBuf = prefs.getBoolean(JavaDecompilerPlugin.REUSE_BUFFER);
        final boolean always = prefs.getBoolean(JavaDecompilerPlugin.IGNORE_EXISTING);

        Object firstElement = selection.get(0);
        if (selection.size() == 1 && firstElement instanceof IClassFile cf) {
            exportClass(decompilerType, reuseBuf, always, cf);
        } else if (selection.size() == 1 && firstElement instanceof IPackageFragmentRoot root) {
            FileDialog dialog = new FileDialog(Display.getDefault().getActiveShell(), SWT.SAVE | SWT.SHEET);
            String fileName = root.getElementName();
            int index = fileName.lastIndexOf('.');
            if (index != -1) {
                fileName = fileName.substring(0, index);
            }
            dialog.setFileName(fileName + "-src"); //$NON-NLS-1$
            dialog.setFilterExtensions(ZIP_FILTER);
            String file = dialog.open();
            if (file == null || file.trim().isEmpty()) {
                return;
            }
            final String projectFile = file.trim();
            try {
                final IJavaElement[] children = root.getChildren();
                exportPackagesSource(decompilerType, reuseBuf, always, projectFile, children);
            } catch (CoreException e) {
                ExceptionHandler.handle(e, Messages.getString("ExportSourceAction.ErrorDialog.Title"), //$NON-NLS-1$
                        Messages.getString("ExportSourceAction.ErrorDialog.Message.CollectClassInfo")); //$NON-NLS-1$
            }
        } else {
            IPackageFragmentRoot root = null;
            if (firstElement instanceof IClassFile iClassFile) {
                root = (IPackageFragmentRoot) iClassFile.getParent().getParent();
            } else if (firstElement instanceof IPackageFragment iPackageFragment) {
                root = (IPackageFragmentRoot) iPackageFragment.getParent();
            }
            if (root == null) {
                return;
            }
            FileDialog dialog = new FileDialog(Display.getDefault().getActiveShell(), SWT.SAVE | SWT.SHEET);
            String fileName = root.getElementName();
            int index = fileName.lastIndexOf('.');
            if (index != -1) {
                fileName = fileName.substring(0, index);
            }
            dialog.setFileName(fileName + "-src"); //$NON-NLS-1$
            dialog.setFilterExtensions(ZIP_FILTER);
            String file = dialog.open();
            if (file == null || file.trim().isEmpty()) {
                return;
            }
            final String projectFile = file.trim();

            exportPackagesSource(decompilerType, reuseBuf, always, projectFile,
                    selection.toArray(IJavaElement[]::new));
        }
    }

    private void exportPackagesSource(final String decompilerType, final boolean reuseBuf, final boolean always,
            final String projectFile, final IJavaElement[] children) {
        try {
            final List<IStatus> exceptions = new ArrayList<>();
            ProgressMonitorDialog dialog = new ProgressMonitorDialog(Display.getDefault().getActiveShell());
            dialog.run(true, true, monitor -> {
                exportPackageSources(monitor, decompilerType, reuseBuf, always, projectFile, children, exceptions);

                final File workingDir = new File(JavaDecompilerPlugin.getDefault().getPreferenceStore()
                        .getString(JavaDecompilerPlugin.TEMP_DIR));// $NON-NLS-1$

                if (workingDir.exists()) {
                    try {
                        FileUtil.deleteDirectory(null, workingDir, 0);
                    } catch (IOException e) {
                        Logger.debug(e);
                    }
                }
            });

            if (dialog.getProgressMonitor().isCanceled()) {
                MessageDialog.openInformation(Display.getDefault().getActiveShell(),
                        Messages.getString("ExportSourceAction.InfoDialog.Title"), //$NON-NLS-1$
                        Messages.getString("ExportSourceAction.InfoDialog.Message.Canceled")); //$NON-NLS-1$
            } else if (!exceptions.isEmpty()) {
                final MultiStatus status = new MultiStatus(JavaDecompilerPlugin.PLUGIN_ID, IStatus.WARNING,
                        exceptions.size() <= 1
                        ? Messages.getFormattedString("ExportSourceAction.WarningDialog.Message.Failed", //$NON-NLS-1$
                                new String[] { "" + exceptions.size() //$NON-NLS-1$
                        })
                                : Messages.getFormattedString("ExportSourceAction.WarningDialog.Message.Failed.Multi", //$NON-NLS-1$
                                        new String[] { "" + exceptions.size() //$NON-NLS-1$
                                }), null) {

                    @Override
                    public void add(IStatus status) {
                        super.add(status);
                        setSeverity(IStatus.WARNING);
                    }
                };
                for (IStatus exception : exceptions) {
                    status.add(exception);
                }

                JavaDecompilerPlugin.getDefault().getLog().log(status);
                ErrorDialog.openError(Display.getDefault().getActiveShell(),
                        Messages.getString("ExportSourceAction.WarningDialog.Title"), //$NON-NLS-1$
                        Messages.getString("ExportSourceAction.WarningDialog.Message.Success"), //$NON-NLS-1$
                        status);

            } else {
                MessageDialog.openInformation(Display.getDefault().getActiveShell(),
                        Messages.getString("ExportSourceAction.InfoDialog.Title"), //$NON-NLS-1$
                        Messages.getString("ExportSourceAction.InfoDialog.Message.Success")); //$NON-NLS-1$
            }
        } catch (Exception e) {
            IStatus status = new Status(IStatus.ERROR, JavaDecompilerPlugin.PLUGIN_ID,
                    Messages.getString("ExportSourceAction.Status.Error.DecompileAndExport"), //$NON-NLS-1$
                    e);
            ExceptionHandler.handle(status, Messages.getString("ExportSourceAction.ErrorDialog.Title"), //$NON-NLS-1$
                    status.getMessage());
        }
    }

    private void exportPackageSources(IProgressMonitor monitor, final String decompilerType, final boolean reuseBuf,
            final boolean always, final String projectFile, final IJavaElement[] children, List<IStatus> exceptions) {
        monitor.beginTask(Messages.getString("ExportSourceAction.Task.Begin"), //$NON-NLS-1$
                1000000);

        final File workingDir = new File(
                JavaDecompilerPlugin.getDefault().getPreferenceStore().getString(JavaDecompilerPlugin.TEMP_DIR)
                + "/export/" //$NON-NLS-1$
                + System.currentTimeMillis());

        try {
            ensureDirectoryExists(workingDir);
        } catch (IOException e) {
            final IStatus status = new Status(IStatus.ERROR, JavaDecompilerPlugin.PLUGIN_ID,
                    Messages.getString("ExportSourceAction.Status.Error.ExportFailed"), //$NON-NLS-1$
                    e);
            exceptions.add(status);
            return;
        }

        Map<IJavaElement, List<IJavaElement>> classesMap = new HashMap<>();
        for (IJavaElement child : children) {
            if (monitor.isCanceled()) {
                return;
            }
            try {
                collectClasses(child, classesMap, monitor);
            } catch (JavaModelException e) {
                IStatus status = new Status(IStatus.ERROR, JavaDecompilerPlugin.PLUGIN_ID,
                        Messages.getString("ExportSourceAction.Status.Error.CollectPackage"), //$NON-NLS-1$
                        e);
                exceptions.add(status);
            }
        }

        monitor.worked(20000);

        IPackageFragment[] pkgs = classesMap.keySet().toArray(IPackageFragment[]::new);
        if (pkgs.length == 0) {
            return;
        }

        int step = 880000 / pkgs.length;
        for (IPackageFragment pkg : pkgs) {
            if (monitor.isCanceled()) {
                return;
            }
            List<IJavaElement> clazzList = classesMap.get(pkg);
            if (clazzList.isEmpty()) {
                monitor.worked(step);
                continue;
            }
            int total = 0;
            int classStep = step / clazzList.size();
            for (IJavaElement clazz : clazzList) {
                if (monitor.isCanceled()) {
                    return;
                }
                if (clazz instanceof IClassFile cf && clazz.getParent() instanceof IPackageFragment) {
                    String className = pkg.getElementName();
                    if (!pkg.getElementName().isEmpty()) {
                        className += "." + clazz.getElementName(); //$NON-NLS-1$
                    }
                    monitor.subTask(className);
                    try {
                        if (cf.getElementName().indexOf('$') != -1) {
                            continue;
                        }
                        cf.open(monitor);

                        String result = DecompileUtil.decompile(cf, decompilerType, always, reuseBuf, true);
                        if (result == null) {
                            IStatus status = new Status(IStatus.ERROR, JavaDecompilerPlugin.PLUGIN_ID,
                                    Messages.getFormattedString("ExportSourceAction.Status.Error.DecompileFailed", //$NON-NLS-1$
                                            new String[] { className }));
                            throw new CoreException(status);
                        }

                        String packageName = pkg.getElementName().replace('.', '/');
                        if (!packageName.isEmpty()) {
                            packageName += "/"; //$NON-NLS-1$
                        }

                        File target = new File(workingDir,
                                packageName + cf.getElementName().replaceAll("\\..+", "") //$NON-NLS-1$ //$NON-NLS-2$
                                + ".java"); //$NON-NLS-1$

                        ensureParentDirectoryExists(target);
                        FileUtil.writeToFile(target, result);
                    } catch (Exception e) {
                        IStatus status = new Status(IStatus.ERROR, JavaDecompilerPlugin.PLUGIN_ID,
                                Messages.getFormattedString("ExportSourceAction.Status.Error.DecompileFailed", //$NON-NLS-1$
                                        new String[] { className }),
                                e);
                        exceptions.add(status);
                    }

                }
                total += classStep;
                monitor.worked(classStep);
            }
            if (total < step) {
                monitor.worked(step - total);
            }
        }
        try {
            int exportStep = 80000 / pkgs.length;
            monitor.setTaskName(Messages.getString("ExportSourceAction.Task.ExportSource")); //$NON-NLS-1$
            monitor.subTask(""); //$NON-NLS-1$
            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(new FileOutputStream(projectFile)))) {
                zos.setLevel(Deflater.BEST_SPEED);
                FileUtil.recursiveZip(monitor, zos, workingDir, "", //$NON-NLS-1$
                        null, exportStep);
                monitor.subTask(""); //$NON-NLS-1$
            }

            int total = exportStep * pkgs.length;
            if (total < 80000) {
                monitor.worked(80000 - total);
            }

            int deleteStep = 20000 / pkgs.length;
            monitor.setTaskName(Messages.getString("ExportSourceAction.Task.Clean")); //$NON-NLS-1$
            monitor.subTask(""); //$NON-NLS-1$
            FileUtil.deleteDirectory(monitor, workingDir.getParentFile(), deleteStep);
            total = deleteStep * pkgs.length;
            if (total < 20000) {
                monitor.worked(20000 - total);
            }
        } catch (Exception e) {
            final IStatus status = new Status(IStatus.ERROR, JavaDecompilerPlugin.PLUGIN_ID,
                    Messages.getString("ExportSourceAction.Status.Error.ExportFailed"), //$NON-NLS-1$
                    e);
            exceptions.add(status);
        }
    }

    public void collectClasses(IJavaElement element, Map<IJavaElement, List<IJavaElement>> classesMap, IProgressMonitor monitor) throws JavaModelException {
        if (element instanceof IPackageFragment pkg) {
            if (!classesMap.containsKey(pkg)) {
                monitor.subTask(pkg.getElementName());
                List<IJavaElement> list = new ArrayList<>();
                Collections.addAll(list, pkg.getChildren());
                classesMap.put(pkg, list);
            }
            if (!isFlat) {
                IPackageFragmentRoot root = (IPackageFragmentRoot) pkg.getParent();
                for (IJavaElement child : root.getChildren()) {
                    if (child.getElementName().startsWith(pkg.getElementName() + ".") //$NON-NLS-1$
                            && !classesMap.containsKey(child)) {
                        collectClasses(child, classesMap, monitor);
                    }
                }
            }
        } else if (element instanceof IClassFile cf) {
            IPackageFragment pkg = (IPackageFragment) cf.getParent();
            if (!classesMap.containsKey(pkg)) {
                monitor.subTask(pkg.getElementName());
                List<IJavaElement> list = new ArrayList<>();
                list.add(element);
                classesMap.put(pkg, list);
            } else {
                classesMap.get(pkg).add(element);
            }
        }
    }

    private void exportClass(String decompilerType, boolean reuseBuf, boolean always, IClassFile cf) {
        FileDialog dialog = new FileDialog(Display.getDefault().getActiveShell(), SWT.SAVE | SWT.SHEET);
        dialog.setFileName(cf.getElementName().replaceAll("\\..+", "")); //$NON-NLS-1$ //$NON-NLS-2$
        dialog.setFilterExtensions(new String[] { "*.java" //$NON-NLS-1$
        });
        String file = dialog.open();
        if (file == null || file.trim().isEmpty()) {
            return;
        }
        IPackageFragment pkg = (IPackageFragment) cf.getParent();
        String className = pkg.getElementName();
        if (!pkg.getElementName().isEmpty()) {
            className += "." + cf.getElementName(); //$NON-NLS-1$
        }

        String projectFile = file.trim();
        try {
            cf.open(null);
            String result = DecompileUtil.decompile(cf, decompilerType, always, reuseBuf, true);
            if (result == null) {
                IStatus status = new Status(IStatus.ERROR, JavaDecompilerPlugin.PLUGIN_ID,
                        Messages.getFormattedString("ExportSourceAction.Status.Error.DecompileFailed", //$NON-NLS-1$
                                new String[] { className }));
                throw new CoreException(status);
            }
            File target = new File(projectFile);
            ensureParentDirectoryExists(target);
            FileUtil.writeToFile(target, result);
        } catch (CoreException | IOException e) {
            MessageDialog.openError(Display.getDefault().getActiveShell(),
                    Messages.getString("ExportSourceAction.ErrorDialog.Title"), //$NON-NLS-1$
                    Messages.getFormattedString("ExportSourceAction.Status.Error.DecompileFailed", //$NON-NLS-1$
                            new String[] { className }));
        }
    }

    private static void ensureParentDirectoryExists(File target) throws IOException {
        if (target == null) {
            throw new IOException("Target is null"); //$NON-NLS-1$
        }
        File parent = target.getParentFile();
        if (parent == null) {
            return;
        }
        ensureDirectoryExists(parent);
    }

    private static void ensureDirectoryExists(File dir) throws IOException {
        if (dir == null) {
            throw new IOException("Directory is null"); //$NON-NLS-1$
        }
        if (dir.exists()) {
            if (dir.isDirectory()) {
                return;
            }
            if (!dir.delete() && dir.exists()) {
                throw new IOException("Unable to delete file blocking directory: " + dir.getAbsolutePath()); //$NON-NLS-1$
            }
        }
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Unable to create directory: " + dir.getAbsolutePath()); //$NON-NLS-1$
        }
    }

    @Override
    public boolean isEnabled() {
        return selection != null;
    }

    public void setFlat(boolean isFlat) {
        this.isFlat = isFlat;
    }
}
