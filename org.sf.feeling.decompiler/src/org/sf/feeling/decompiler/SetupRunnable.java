/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IFileEditorMapping;
import org.eclipse.ui.IPageListener;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveListener;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.IPreferenceConstants;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.registry.EditorRegistry;
import org.eclipse.ui.internal.registry.FileEditorMapping;
import org.sf.feeling.decompiler.actions.DecompileAction;
import org.sf.feeling.decompiler.editor.JavaDecompilerClassFileEditor;
import org.sf.feeling.decompiler.util.ClassUtil;
import org.sf.feeling.decompiler.util.Logger;
import org.sf.feeling.decompiler.util.ReflectionUtils;
import org.sf.feeling.decompiler.util.UIUtil;

public class SetupRunnable implements Runnable {

    @Override
    public void run() {
        try {
            if (PlatformUI.getWorkbench() == null || PlatformUI.getWorkbench().getActiveWorkbenchWindow() == null
                    || PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage() == null) {
                Display.getDefault().timerExec(1000, new Runnable() {

                    public void run() {
                        SetupRunnable.this.run();
                    }
                });
            } else {
                checkClassFileAssociation();
                setupPartListener();
            }
        } catch (Throwable e) {
            Logger.debug(e);
        }
    }

    private void setupPartListener() {
        final IPerspectiveListener perspectiveListener = new IPerspectiveListener() {

            @Override
            public void perspectiveChanged(IWorkbenchPage page, IPerspectiveDescriptor perspective, String changeId) {
            }

            @Override
            public void perspectiveActivated(IWorkbenchPage page, IPerspectiveDescriptor perspective) {
                if (UIUtil.isDebugPerspective()) {
                    new DecompileAction().run();
                }
            }
        };

        final IPartListener partListener = new IPartListener() {

            @Override
            public void partOpened(IWorkbenchPart part) {

            }

            @Override
            public void partDeactivated(IWorkbenchPart part) {

            }

            @Override
            public void partClosed(IWorkbenchPart part) {

            }

            @Override
            public void partBroughtToTop(IWorkbenchPart part) {
                if (part instanceof JavaDecompilerClassFileEditor) {
                    String code = ((JavaDecompilerClassFileEditor) part).getViewer().getDocument().get();
                    if (ClassUtil.isDebug() != JavaDecompilerClassFileEditor.isDebug(code)) {
                        ((JavaDecompilerClassFileEditor) part).doSetInput(false);
                    }
                }
            }

            @Override
            public void partActivated(IWorkbenchPart part) {

            }
        };

        final IPageListener pageListener = new IPageListener() {

            @Override
            public void pageOpened(IWorkbenchPage page) {
                page.removePartListener(partListener);
                page.addPartListener(partListener);
            }

            @Override
            public void pageClosed(IWorkbenchPage page) {
                page.removePartListener(partListener);

            }

            @Override
            public void pageActivated(IWorkbenchPage page) {
                page.removePartListener(partListener);
                page.addPartListener(partListener);
            }
        };

        IWindowListener windowListener = new IWindowListener() {

            @Override
            public void windowOpened(IWorkbenchWindow window) {
                window.removePageListener(pageListener);
                window.addPageListener(pageListener);
                window.removePerspectiveListener(perspectiveListener);
                window.addPerspectiveListener(perspectiveListener);
                IWorkbenchPage[] pages = window.getPages();
                if (pages != null) {
                    for (int i = 0; i < pages.length; i++) {
                        pages[i].removePartListener(partListener);
                        pages[i].addPartListener(partListener);
                    }
                }
            }

            @Override
            public void windowDeactivated(IWorkbenchWindow window) {
                window.removePageListener(pageListener);
                window.removePerspectiveListener(perspectiveListener);
            }

            @Override
            public void windowClosed(IWorkbenchWindow window) {
                window.removePageListener(pageListener);
                window.removePerspectiveListener(perspectiveListener);
            }

            @Override
            public void windowActivated(IWorkbenchWindow window) {
                window.removePageListener(pageListener);
                window.addPageListener(pageListener);
                window.removePerspectiveListener(perspectiveListener);
                window.addPerspectiveListener(perspectiveListener);
                IWorkbenchPage[] pages = window.getPages();
                if (pages != null) {
                    for (int i = 0; i < pages.length; i++) {
                        pages[i].removePartListener(partListener);
                        pages[i].addPartListener(partListener);
                    }
                }
            }
        };

        if (PlatformUI.getWorkbench() == null) {
            return;
        }

        PlatformUI.getWorkbench().removeWindowListener(windowListener);
        PlatformUI.getWorkbench().addWindowListener(windowListener);

        if (PlatformUI.getWorkbench().getActiveWorkbenchWindow() == null) {
            return;
        }

        PlatformUI.getWorkbench().getActiveWorkbenchWindow().removePageListener(pageListener);
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().addPageListener(pageListener);

        PlatformUI.getWorkbench().getActiveWorkbenchWindow().removePerspectiveListener(perspectiveListener);
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().addPerspectiveListener(perspectiveListener);

        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        if (page == null) {
            return;
        }

        page.removePartListener(partListener);
        page.addPartListener(partListener);
    }

    private void checkClassFileAssociation() {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        if (prefs.getBoolean(JavaDecompilerPlugin.DEFAULT_EDITOR)) {
            updateClassDefaultEditor();

            IPreferenceStore store = WorkbenchPlugin.getDefault().getPreferenceStore();
            store.addPropertyChangeListener(new IPropertyChangeListener() {

                @Override
                public void propertyChange(PropertyChangeEvent event) {
                    if (IPreferenceConstants.RESOURCES.equals(event.getProperty())) {
                        updateClassDefaultEditor();
                    }
                }
            });
        }
    }

    protected void updateClassDefaultEditor() {
        EditorRegistry registry = (EditorRegistry) PlatformUI.getWorkbench().getEditorRegistry();

        IFileEditorMapping[] mappings = registry.getFileEditorMappings();

        IFileEditorMapping classNoSource = null;
        IFileEditorMapping classPlain = null;

        for (int i = 0; i < mappings.length; i++) {
            IFileEditorMapping mapping = mappings[i];
            if (mapping.getExtension().equals("class without source")) //$NON-NLS-1$
            {
                classNoSource = mapping;
            } else if (mapping.getExtension().equals("class")) //$NON-NLS-1$
            {
                classPlain = mapping;
            }
        }

        IFileEditorMapping[] classMappings = new IFileEditorMapping[] { classNoSource, classPlain };

        boolean needUpdate = checkDefaultEditor(classMappings);
        if (needUpdate) {
            for (int i = 0; i < classMappings.length; i++) {
                IFileEditorMapping mapping = classMappings[i];
                for (int j = 0; j < mapping.getEditors().length; j++) {
                    IEditorDescriptor editor = mapping.getEditors()[j];
                    if (editor.getId().equals(JavaDecompilerPlugin.EDITOR_ID)) {
                        try {
                            ReflectionUtils.invokeMethod(mapping, "setDefaultEditor", //$NON-NLS-1$
                                    new Class[] { Class.forName("org.eclipse.ui.IEditorDescriptor") //$NON-NLS-1$
                            }, new Object[] { editor });
                        } catch (ClassNotFoundException e) {
                        }

                        try {
                            ReflectionUtils.invokeMethod(mapping, "setDefaultEditor", //$NON-NLS-1$
                                    new Class[] { Class.forName("org.eclipse.ui.internal.registry.EditorDescriptor") //$NON-NLS-1$
                            }, new Object[] { editor });
                        } catch (ClassNotFoundException e) {
                        }
                    }
                }
            }

            registry.setFileEditorMappings((FileEditorMapping[]) mappings);
            registry.saveAssociations();
        }
    }

    protected boolean checkDefaultEditor(IFileEditorMapping[] classMappings) {
        for (int i = 0; i < classMappings.length; i++) {
            IFileEditorMapping mapping = classMappings[i];
            if (mapping.getDefaultEditor() != null
                    && !mapping.getDefaultEditor().getId().equals(JavaDecompilerPlugin.EDITOR_ID)) {
                return true;
            }
        }
        return false;
    }
}
