/*******************************************************************************
 * © 2017 Chen Chao (@cnfree)
 * © 2017 Pascal Bihler (@pbi-qfs)
 * © 2021 Jan Peter Stotz (@jpstotz)
 * © 2025-2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowPulldownDelegate;
import org.eclipse.ui.IWorkbenchWindowPulldownDelegate2;

public class DecompilerMenuItemAction implements IWorkbenchWindowPulldownDelegate, IWorkbenchWindowPulldownDelegate2 {

    @Override
    public Menu getMenu(Control parent) {
        return new SubMenuCreator().getMenu(parent);
    }

    @Override
    public Menu getMenu(Menu parent) {
        return new SubMenuCreator().getMenu(parent);
    }

    @Override
    public void init(IWorkbenchWindow window) {
        // This pulldown action has no window-specific state to initialize.
    }

    @Override
    public void dispose() {
        // This pulldown action does not hold resources that need explicit disposal.
    }

    @Override
    public void run(IAction action) {
        new DecompileAction().run();
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        action.setEnabled(isEnable());
    }

    private boolean isEnable() {
        return true;
    }
}
