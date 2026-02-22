/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.actions;

import java.util.Arrays;

import org.eclipse.core.runtime.PerformanceStats;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.part.Page;
import org.eclipse.ui.texteditor.IUpdate;
import io.github.nbauma109.decompiler.editor.JavaDecompilerClassFileEditor;
import io.github.nbauma109.decompiler.i18n.Messages;

public class DecompileActionGroup extends ActionGroup {

    private static final String MENU_MAIN = "io.github.nbauma109.decompiler.main"; //$NON-NLS-1$

    private static final String MENU_SOURCE = "io.github.nbauma109.decompiler.source"; //$NON-NLS-1$

    private static final String PERF_DECOMPILE_ACTION_GROUP = "io.github.nbauma109.decompiler/perf/DecompileActionGroup"; //$NON-NLS-1$

    private static final String QUICK_MENU_MAIN = "io.github.nbauma109.decompiler.main.quickMenu"; //$NON-NLS-1$

    private static final String QUICK_MENU_SOURCE = "io.github.nbauma109.decompiler.source.quickMenu"; //$NON-NLS-1$

    private String fGroupName = IContextMenuConstants.GROUP_REORGANIZE;

    private JavaDecompilerClassFileEditor fEditor;

    public DecompileActionGroup(IViewPart part) {
        this(part.getSite(), null);
    }

    public DecompileActionGroup(Page page) {
        this(page.getSite(), null);
    }

    public DecompileActionGroup(JavaDecompilerClassFileEditor editor, String groupName, boolean binary) {

        final PerformanceStats stats = PerformanceStats.getStats(PERF_DECOMPILE_ACTION_GROUP, this);
        stats.startRun();

        fEditor = editor;
        fGroupName = groupName;

        stats.endRun();
    }

    public DecompileActionGroup(IWorkbenchSite site, ISelectionProvider selectionProvider) {
        final PerformanceStats stats = PerformanceStats.getStats(PERF_DECOMPILE_ACTION_GROUP, this);
        stats.startRun();

        stats.endRun();
    }

    @Override
    public void fillContextMenu(IMenuManager menu) {
        super.fillContextMenu(menu);
        addSourceSubmenu(menu);
        addMainSubmenu(menu);
    }

    /*
     * @see ActionGroup#dispose()
     */
    @Override
    public void dispose() {
        super.dispose();
    }

    private void addMainSubmenu(IMenuManager menu) {
        MenuManager mainSubmenu = new MenuManager(
                Messages.getString("JavaDecompilerActionBarContributor.Menu.Decompiler"), //$NON-NLS-1$
                MENU_MAIN);
        mainSubmenu.setActionDefinitionId(QUICK_MENU_MAIN);
        if (fEditor != null) {
            final ITypeRoot element = getEditorInput();
            if (element != null && ActionUtil.isOnBuildPath(element)) {
                mainSubmenu.addMenuListener(this::showMenu);
                menu.appendToGroup(fGroupName, new Separator());
                menu.appendToGroup(fGroupName, mainSubmenu);
                menu.appendToGroup(fGroupName, new Separator());
            }
        }
    }

    private void addSourceSubmenu(IMenuManager menu) {
        MenuManager sourceSubmenu = new MenuManager(
                Messages.getString("JavaDecompilerActionBarContributor.Menu.Source"), //$NON-NLS-1$
                MENU_SOURCE);
        sourceSubmenu.setActionDefinitionId(QUICK_MENU_SOURCE);
        if (fEditor != null) {
            final ITypeRoot element = getEditorInput();
            if (element != null && ActionUtil.isOnBuildPath(element)) {
                sourceSubmenu.addMenuListener(this::showMenu);
                menu.appendToGroup(fGroupName, new Separator());
                menu.appendToGroup(fGroupName, sourceSubmenu);
                menu.appendToGroup(fGroupName, new Separator());
            }
        }
    }

    private void showMenu(IMenuManager submenu) {
        for (IContributionItem item : Arrays.asList(submenu.getItems())) {
            if (item instanceof ActionContributionItem actionContributionItem) {
                IAction action = actionContributionItem.getAction();
                if (action instanceof IUpdate iUpdate) {
                    iUpdate.update();
                }
            }
        }
    }

    private ITypeRoot getEditorInput() {
        return JavaUI.getEditorInputTypeRoot(fEditor.getEditorInput());
    }
}
