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

public class PreferenceMenuItemAction extends AbstractPulldownMenuItemAction {

    @Override
    public void run(IAction action) {
        new DecompilerPreferenceAction().run();

    }
}
