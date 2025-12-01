/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.Display;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.editor.JavaDecompilerClassFileEditor;
import org.sf.feeling.decompiler.i18n.Messages;
import org.sf.feeling.decompiler.util.UIUtil;

public class DebugModeAction extends Action {

    public DebugModeAction() {
        super(Messages.getString("DebugModeAction.Action.Text"), //$NON-NLS-1$
                AS_CHECK_BOX);
    }

    @Override
    public void run() {
        JavaDecompilerPlugin.getDefault().setDebugMode(!isChecked());
        new DecompileAction().run();
        final JavaDecompilerClassFileEditor editor = UIUtil.getActiveEditor();
        if (editor != null) {
            editor.showSource();
            editor.notifyPropertiesChange();
            Display.getDefault().asyncExec(new Runnable() {

                public void run() {
                    editor.setFocus();
                }
            });
        }
    }

    @Override
    public boolean isChecked() {
        return JavaDecompilerPlugin.getDefault().isDebugMode();
    }

}