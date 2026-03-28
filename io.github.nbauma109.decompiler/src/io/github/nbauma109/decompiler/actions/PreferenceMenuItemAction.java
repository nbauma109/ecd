/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
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

public class PreferenceMenuItemAction implements IWorkbenchWindowPulldownDelegate, IWorkbenchWindowPulldownDelegate2 {

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
        // This pulldown action has no window-specific state to initialize.
    }

    @Override
    public void dispose() {
        // This pulldown action does not hold resources that need explicit disposal.
    }

    @Override
    public void run(IAction action) {
        new DecompilerPreferenceAction().run();

    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        // Preferences are always available, so selection changes do not affect this action.
    }
}
