/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.dialogs.PreferencesUtil;
import io.github.nbauma109.decompiler.editor.JavaDecompilerClassFileEditor;
import io.github.nbauma109.decompiler.i18n.Messages;
import io.github.nbauma109.decompiler.util.UIUtil;

public class DecompilerPreferenceAction extends Action {

    public DecompilerPreferenceAction() {
        super(Messages.getString("JavaDecompilerActionBarContributor.Action.Preferences")); //$NON-NLS-1$
    }

    @Override
    public void run() {
        JavaDecompilerClassFileEditor editor = UIUtil.getActiveDecompilerEditor();

        String showId = "io.github.nbauma109.decompiler.Main"; //$NON-NLS-1$

        if (editor != null) {
            PreferencesUtil.createPreferenceDialogOn(Display.getDefault().getActiveShell(), showId, // $NON-NLS-1$
                    editor.collectContextMenuPreferencePages(), null).open();
        } else {
            PreferencesUtil.createPreferenceDialogOn(Display.getDefault().getActiveShell(), showId, // $NON-NLS-1$
                    new String[] { showId // $NON-NLS-1$
            }, null).open();
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}