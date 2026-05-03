/*******************************************************************************
 * (C) 2017 cnfree (@cnfree)
 * (C) 2017 Pascal Bihler
 * (C) 2021 Jan S. (@jpstotz)
 * (C) 2025-2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.actions;

import java.util.List;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import io.github.nbauma109.decompiler.util.UIUtil;

public class ExportSourceMenuItemAction extends AbstractPulldownMenuItemAction {

    @Override
    public void run(IAction action) {
        if (UIUtil.getActiveEditor() != null) {
            new ExportEditorSourceAction().run();

        } else {
            List<?> list = UIUtil.getExportSelections();
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
