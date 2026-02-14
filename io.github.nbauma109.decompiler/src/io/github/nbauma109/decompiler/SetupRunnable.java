/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.preference.IPreferenceStore;
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

import io.github.nbauma109.decompiler.actions.DecompileAction;
import io.github.nbauma109.decompiler.debug.BreakpointPresentationBridge;
import io.github.nbauma109.decompiler.debug.DecompilerSourceLookupBridge;
import io.github.nbauma109.decompiler.editor.JavaDecompilerClassFileEditor;
import io.github.nbauma109.decompiler.util.ClassUtil;
import io.github.nbauma109.decompiler.util.Logger;
import io.github.nbauma109.decompiler.util.UIUtil;

public class SetupRunnable implements Runnable {

    @Override
    public void run() {
        try {
            if (PlatformUI.getWorkbench() == null || PlatformUI.getWorkbench().getActiveWorkbenchWindow() == null
                    || PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage() == null) {
                Display.getDefault().timerExec(1000, SetupRunnable.this::run);
            } else {
                checkClassFileAssociation();
                setupPartListener();
                DecompilerSourceLookupBridge.install();
                BreakpointPresentationBridge.install();
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
                if (part instanceof JavaDecompilerClassFileEditor editor) {
                    String code = editor.getViewer().getDocument().get();
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
                    for (IWorkbenchPage page : pages) {
                        page.removePartListener(partListener);
                        page.addPartListener(partListener);
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
                    for (IWorkbenchPage page : pages) {
                        page.removePartListener(partListener);
                        page.addPartListener(partListener);
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
            store.addPropertyChangeListener(event -> {
                if (IPreferenceConstants.RESOURCES.equals(event.getProperty())) {
                    updateClassDefaultEditor();
                }
            });
        }
    }

    protected void updateClassDefaultEditor() {
        EditorRegistry registry = (EditorRegistry) PlatformUI.getWorkbench().getEditorRegistry();

        IFileEditorMapping[] mappings = registry.getFileEditorMappings();

        IFileEditorMapping classNoSource = null;
        IFileEditorMapping classPlain = null;

        // Search Class file editor mappings
        for (IFileEditorMapping mapping : mappings) {
            if (mapping.getExtension().equals("class without source")) //$NON-NLS-1$
            {
                classNoSource = mapping;
            } else if (mapping.getExtension().equals("class")) //$NON-NLS-1$
            {
                classPlain = mapping;
            }
        }

        if (classPlain instanceof FileEditorMapping c && classNoSource != null) {
            // Search ECD editor descriptor on "class" extension
            for (IEditorDescriptor descriptor : classPlain.getEditors()) {
                if (descriptor.getId().equals(JavaDecompilerPlugin.EDITOR_ID)) {
                    // Remove ECD editor on "class" extension
                    c.removeEditor(descriptor);

                    // Set ECD as default editor on "class without source" extension
                    registry.setDefaultEditor("." + classNoSource.getExtension(), descriptor.getId());
                    break;
                }
            }

            // Restore the default editor for "class" extension
            IEditorDescriptor defaultClassFileEditor = registry.findEditor(JavaUI.ID_CF_EDITOR);

            if (defaultClassFileEditor != null) {
                registry.setDefaultEditor("." + classPlain.getExtension(), JavaUI.ID_CF_EDITOR);
            }

            registry.setFileEditorMappings((FileEditorMapping[]) mappings);
            registry.saveAssociations();
        }
    }
}
