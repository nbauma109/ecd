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
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.actions.ActionDelegate;
import io.github.nbauma109.decompiler.editor.JavaDecompilerClassFileEditor;

public class ExportSourceActionDelegate extends ActionDelegate implements IEditorActionDelegate {

    JavaDecompilerClassFileEditor editor;

    @Override
    public void setActiveEditor(IAction action, IEditorPart targetEditor) {
        if (targetEditor instanceof JavaDecompilerClassFileEditor javaDecompilerClassFileEditor) {
            editor = javaDecompilerClassFileEditor;
        }
    }

    @Override
    public void run(IAction action) {
        new ExportEditorSourceAction().run();
    }

}
