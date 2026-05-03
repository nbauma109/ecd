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

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.PlatformUI;
import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.actions.OpenClassWithContributionFactory.OpenClassesAction;
import io.github.nbauma109.decompiler.editor.JavaDecompilerClassFileEditor;
import io.github.nbauma109.decompiler.util.UIUtil;

public class BaseDecompilerHandler extends DecompileHandler {

    public Object handleDecompile(String decompilerType) {
        final List<IClassFile> classes = UIUtil.getActiveSelection();
        if (classes != null && !classes.isEmpty()) {
            IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
            IEditorDescriptor editorDescriptor = registry.findEditor(JavaDecompilerPlugin.EDITOR_ID);
            if (editorDescriptor == null) {
                JavaDecompilerClassFileEditor editor = UIUtil.getActiveEditor();
                if (editor != null) {
                    editor.doSetInput(decompilerType, true);
                }
            } else {
                new OpenClassesAction(editorDescriptor, classes, decompilerType).run();
            }
        } else {
            JavaDecompilerClassFileEditor editor = UIUtil.getActiveEditor();
            if (editor != null) {
                editor.doSetInput(decompilerType, true);
            }
        }
        return null;
    }
}
