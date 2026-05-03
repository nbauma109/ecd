/*******************************************************************************
 * (C) 2017 cnfree (@cnfree)
 * (C) 2017 Pascal Bihler
 * (C) 2021 Jan S. (@jpstotz)
 * (C) 2024-2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.actions.ActionDelegate;
import io.github.nbauma109.decompiler.editor.JavaDecompilerClassFileEditor;

public class SourceCodeActionDelegate extends ActionDelegate implements IEditorActionDelegate {

    JavaDecompilerClassFileEditor editor;

    @Override
    public void setActiveEditor(IAction action, IEditorPart targetEditor) {
        if (targetEditor instanceof JavaDecompilerClassFileEditor javaDecompilerClassFileEditor) {
            editor = javaDecompilerClassFileEditor;
            action.setChecked(true);
        }
    }
}
