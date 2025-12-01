/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.actions.ActionDelegate;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.editor.JavaDecompilerClassFileEditor;

public class DebugModeActionDelegate extends ActionDelegate implements IEditorActionDelegate {

    JavaDecompilerClassFileEditor editor;

    @Override
    public void setActiveEditor(IAction action, IEditorPart targetEditor) {
        if (targetEditor instanceof JavaDecompilerClassFileEditor) {
            editor = (JavaDecompilerClassFileEditor) targetEditor;
            action.setChecked(JavaDecompilerPlugin.getDefault().isDebugMode());
        }
    }

    @Override
    public void run(IAction action) {
        new DebugModeAction().run();
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        action.setChecked(JavaDecompilerPlugin.getDefault().isDebugMode());
    }
}
