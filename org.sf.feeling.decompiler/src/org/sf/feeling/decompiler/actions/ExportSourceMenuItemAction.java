/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.actions;

import java.util.List;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowPulldownDelegate;
import org.eclipse.ui.IWorkbenchWindowPulldownDelegate2;
import org.sf.feeling.decompiler.util.UIUtil;

public class ExportSourceMenuItemAction implements IWorkbenchWindowPulldownDelegate, IWorkbenchWindowPulldownDelegate2 {

    @Override
    public Menu getMenu(Control parent) {
        return null;
    }

    @Override
    public Menu getMenu(Menu parent) {
        return null;
    }

    @Override
    public void init(IWorkbenchWindow window) {

    }

    @Override
    public void dispose() {
    }

    @Override
    public void run(IAction action) {
        if (UIUtil.getActiveEditor() != null) {
            new ExportEditorSourceAction().run();

        } else {
            List list = UIUtil.getExportSelections();
            if (list != null) {
                new ExportSourceAction(list).run();
            }
        }
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        action.setEnabled(isEnable());
    }

    private boolean isEnable() {
        return UIUtil.getActiveEditor() != null || UIUtil.getActiveSelection() != null
                || UIUtil.getExportSelections() != null;
    }
}