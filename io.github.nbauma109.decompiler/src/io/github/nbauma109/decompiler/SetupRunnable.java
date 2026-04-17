/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
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
import org.eclipse.ui.internal.registry.EditorDescriptor;
import org.eclipse.ui.internal.registry.EditorRegistry;
import org.eclipse.ui.internal.registry.FileEditorMapping;

import io.github.nbauma109.decompiler.actions.DecompileAction;
import io.github.nbauma109.decompiler.debug.BreakpointPresentationBridge;
import io.github.nbauma109.decompiler.debug.DecompilerSourceLookupBridge;
import io.github.nbauma109.decompiler.editor.JavaDecompilerClassFileEditor;
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
                // We only react to perspective activation; intermediate changes do not require action.
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
                refreshDecompilerEditor(part);
            }

            @Override
            public void partDeactivated(IWorkbenchPart part) {
                // Deactivation does not require any decompiler-specific adjustment.
            }

            @Override
            public void partClosed(IWorkbenchPart part) {
                // Closing a part does not require any decompiler-specific cleanup here.
            }

            @Override
            public void partBroughtToTop(IWorkbenchPart part) {
                refreshDecompilerEditor(part);
            }

            @Override
            public void partActivated(IWorkbenchPart part) {
                // Activation alone is not enough; we only refresh when a part is brought to the top.
            }
        };

        final IPageListener pageListener = new IPageListener() {

            @Override
            public void pageOpened(IWorkbenchPage page) {
                page.removePartListener(partListener);
                page.addPartListener(partListener);
                refreshDecompilerEditors(page);
            }

            @Override
            public void pageClosed(IWorkbenchPage page) {
                page.removePartListener(partListener);
                // Removing the listener is the only cleanup needed when a page closes.
            }

            @Override
            public void pageActivated(IWorkbenchPage page) {
                page.removePartListener(partListener);
                page.addPartListener(partListener);
                refreshDecompilerEditors(page);
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
                        refreshDecompilerEditors(page);
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
                        refreshDecompilerEditors(page);
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
        refreshDecompilerEditors(page);
    }

    private void refreshDecompilerEditors(IWorkbenchPage page) {
        if (page == null) {
            return;
        }
        for (IEditorReference editorReference : page.getEditorReferences()) {
            IEditorPart editor = editorReference.getEditor(false);
            refreshDecompilerEditor(editor);
        }
    }

    private void refreshDecompilerEditor(IWorkbenchPart part) {
        if (part instanceof JavaDecompilerClassFileEditor editor) {
            editor.refreshContentIfNeeded();
        }
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
        EditorDescriptor decompilerEditor = registry.findEditor(JavaDecompilerPlugin.EDITOR_ID) instanceof EditorDescriptor descriptor
                ? descriptor
                        : null;

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

        if (decompilerEditor != null && classNoSource != null && classPlain instanceof FileEditorMapping plainMapping) {
            if (!containsEditor(classPlain, decompilerEditor.getId())) {
                // Once ECD has populated source, JDT starts treating the same class as a plain ".class" file.
                // Keeping the ECD editor in that mapping prevents reopen from falling back to the stock editor.
                plainMapping.addEditor(decompilerEditor);
            }

            // Use ECD for both cases:
            // - "class without source" on the first open
            // - plain "class" after decompiled source has been cached by JDT
            registry.setDefaultEditor("." + classNoSource.getExtension(), decompilerEditor.getId());
            registry.setDefaultEditor("." + classPlain.getExtension(), decompilerEditor.getId());
            registry.setFileEditorMappings((FileEditorMapping[]) mappings);
            registry.saveAssociations();
        }
    }

    private boolean containsEditor(IFileEditorMapping mapping, String editorId) {
        for (IEditorDescriptor descriptor : mapping.getEditors()) {
            if (descriptor.getId().equals(editorId)) {
                return true;
            }
        }
        return false;
    }
}
