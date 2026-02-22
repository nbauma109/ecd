/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.internal.ui.navigator.IExtensionStateConstants.Values;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.navigator.CommonNavigatorManager;
import org.eclipse.ui.navigator.IExtensionStateModel;
import org.eclipse.ui.navigator.INavigatorContentService;
import org.eclipse.ui.views.contentoutline.ContentOutline;
import io.github.nbauma109.decompiler.editor.JavaDecompilerClassFileEditor;

public class UIUtil {

    private UIUtil() {
    }

    public static JavaDecompilerClassFileEditor getActiveEditor() {
        final JavaDecompilerClassFileEditor[] editors = new JavaDecompilerClassFileEditor[1];
        Display.getDefault().syncExec(() -> {
            IWorkbenchPart editor = getActiveEditor(true);
            if (editor instanceof JavaDecompilerClassFileEditor) {
                editors[0] = (JavaDecompilerClassFileEditor) editor;
            } else {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

                if (window != null) {
                    IWorkbenchPage pg = window.getActivePage();

                    if (pg != null) {
                        IEditorPart editorPart = pg.getActiveEditor();
                        if (editorPart instanceof JavaDecompilerClassFileEditor) {
                            editors[0] = (JavaDecompilerClassFileEditor) editorPart;
                        }
                    }
                }
            }
        });
        return editors[0];
    }

    public static JavaDecompilerClassFileEditor getActiveDecompilerEditor() {
        final JavaDecompilerClassFileEditor[] editors = new JavaDecompilerClassFileEditor[1];
        Display.getDefault().syncExec(() -> {
            IWorkbenchPart editor = getActiveEditor(true);
            if (editor instanceof JavaDecompilerClassFileEditor) {
                editors[0] = (JavaDecompilerClassFileEditor) editor;
            }
        });
        return editors[0];
    }

    public static List getActiveSelection() {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        final List classes = getSelectedElements(window.getSelectionService(), IClassFile.class);
        if (classes != null && !classes.isEmpty()) {
            return classes;
        }
        return null;
    }

    private static String getActivePerspectiveId() {
        final String[] ids = new String[1];
        Display.getDefault().syncExec(() -> {
            IWorkbench wb = PlatformUI.getWorkbench();
            if (wb == null) {
                ids[0] = null;
                return;
            }

            IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
            if (win == null) {
                ids[0] = null;
                return;
            }
            IWorkbenchPage page = win.getActivePage();
            if (page == null) {
                ids[0] = null;
                return;
            }

            IPerspectiveDescriptor perspective = page.getPerspective();
            if (perspective == null) {
                ids[0] = null;
                return;
            }
            ids[0] = perspective.getId();

        });
        return ids[0];
    }

    public static boolean isDebugPerspective() {
        return "org.eclipse.debug.ui.DebugPerspective" //$NON-NLS-1$
                .equals(getActivePerspectiveId());
    }

    private static List getSelectedElements(ISelectionService selService, Class<?> eleClass) {

        Iterator selections = getSelections(selService);
        List elements = new ArrayList();

        while (selections != null && selections.hasNext()) {
            Object select = selections.next();

            if (eleClass.isInstance(select)) {
                elements.add(select);
            }
        }

        return elements;
    }

    private static Iterator getSelections(ISelectionService selService) {
        ISelection selection = selService.getSelection();

        if (selection instanceof IStructuredSelection structuredSelection) {
            return structuredSelection.iterator();
        }

        return null;
    }

    private static IWorkbenchPart getActiveEditor(boolean activePageOnly) {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

        if (window != null) {
            if (activePageOnly) {
                IWorkbenchPage pg = window.getActivePage();

                if (pg != null) {
                    IWorkbenchPart activePart = pg.getActivePart();
                    if (!(activePart instanceof ContentOutline outline)) {
                        return activePart;
                    }
                    IWorkbenchPart part = (IWorkbenchPart) ReflectionUtils.invokeMethod(outline,
                            "getCurrentContributingPart"); //$NON-NLS-1$
                    if (part == null) {
                        return (IWorkbenchPart) ReflectionUtils.getFieldValue(outline, "hiddenPart"); //$NON-NLS-1$
                    }
                }
            } else {
                for (IWorkbenchPage pg : window.getPages()) {
                    if (pg != null) {
                        IWorkbenchPart part = pg.getActivePart();
                        if (part != null) {
                            return part;
                        }
                    }
                }
            }
        }

        return null;
    }

    public static List getExportSelections() {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        final List selectedJars = getSelectedElements(window.getSelectionService(), IPackageFragmentRoot.class);
        if (selectedJars.size() == 1) {
            return selectedJars;
        }

        if (selectedJars.size() > 1) {
            return null;
        }

        final List selectedPackages = getSelectedElements(window.getSelectionService(), IPackageFragment.class);
        final List selectedClasses = getSelectedElements(window.getSelectionService(), IClassFile.class);
        selectedClasses.addAll(selectedPackages);
        if (!selectedClasses.isEmpty()) {
            return selectedClasses;
        }

        return null;
    }

    public static boolean isPackageFlat() {
        boolean isFlat = false;
        try {
            IWorkbenchPart view = getActiveEditor(true);
            if (view != null) {
                if ("org.eclipse.ui.navigator.ProjectExplorer".equals(view.getSite().getId())) //$NON-NLS-1$
                {
                    CommonNavigator explorer = (CommonNavigator) view;
                    Field field = CommonNavigator.class.getDeclaredField("commonManager"); //$NON-NLS-1$
                    if (field != null) {
                        field.setAccessible(true);
                        CommonNavigatorManager manager = (CommonNavigatorManager) field.get(explorer);

                        field = CommonNavigatorManager.class.getDeclaredField("contentService"); //$NON-NLS-1$
                        if (field != null) {
                            field.setAccessible(true);
                            INavigatorContentService service = (INavigatorContentService) field.get(manager);
                            IExtensionStateModel model = service.findStateModel("org.eclipse.jdt.java.ui.javaContent"); //$NON-NLS-1$
                            isFlat = model.getBooleanProperty(Values.IS_LAYOUT_FLAT);
                        }
                    }
                } else if ("org.eclipse.jdt.ui.PackageExplorer".equals(view.getSite().getId())) //$NON-NLS-1$
                {
                    PackageExplorerPart explorer = (PackageExplorerPart) view;
                    isFlat = explorer.isFlatLayout();
                }
            }
        } catch (Exception e) {
        }
        return isFlat;
    }

    public static String getPathLocation(IPath path) {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot root = workspace.getRoot();
        IResource resource = root.findMember(path);
        if (resource != null) {
            return resource.getLocation().toOSString();
        }
        return null;
    }

    public static boolean requestFromJavadocHover() {
        StackTraceElement[] stacks = Thread.currentThread().getStackTrace();
        for (int i = 0; i < stacks.length && i < 12; i++) {
            if (stacks[i].getClassName().indexOf("BinaryType") != -1 //$NON-NLS-1$
                    && "getJavadocRange".equals(stacks[i].getMethodName())) { //$NON-NLS-1$
                return false;
            }

            if (stacks[i].getClassName().indexOf("JavadocHover") != -1 //$NON-NLS-1$
                    && "getHoverInfo".equals(stacks[i].getMethodName())) { //$NON-NLS-1$
                return true;
            }
        }
        return false;
    }

    public static boolean requestCreateBuffer() {
        StackTraceElement[] stacks = Thread.currentThread().getStackTrace();
        for (int i = 0; i < stacks.length && i < 12; i++) {
            if (stacks[i].getClassName().indexOf("BinaryType") != -1 //$NON-NLS-1$
                    && "getJavadocRange".equals(stacks[i].getMethodName())) { //$NON-NLS-1$
                return false;
            }

            if ((stacks[i].getClassName().indexOf("JavadocHover") != -1 //$NON-NLS-1$
                    && "getHoverInfo2".equals(stacks[i].getMethodName())) || (stacks[i].getClassName().indexOf("JavaSourceHover") != -1 //$NON-NLS-1$
                    && "getHoverInfo".equals(stacks[i].getMethodName()))) { //$NON-NLS-1$
                return true;
            }

            if (stacks[i].getClassName().indexOf("FindOccurrencesInFileAction") != -1 //$NON-NLS-1$
                    && "getMember".equals(stacks[i].getMethodName())) { //$NON-NLS-1$
                return true;
            }

            // if ( stacks[i].getClassName( ).indexOf( "HyperlinkManager" ) !=
            // -1 //$NON-NLS-1$
            // && stacks[i].getMethodName( ).equals( "findHyperlinks" ) )
            // //$NON-NLS-1$
            // return true;

            // if ( stacks[i].getClassName( )
            // .indexOf( "DefaultJavaFoldingStructureProvider" ) != -1
            // //$NON-NLS-1$
            // && stacks[i].getMethodName( )
            // .equals( "computeProjectionRanges" ) ) //$NON-NLS-1$
            // return true;
        }
        return false;
    }
}
