/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.actions;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.PlatformUI;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.actions.OpenClassWithContributionFactory.OpenClassesAction;
import org.sf.feeling.decompiler.editor.JavaDecompilerClassFileEditor;
import org.sf.feeling.decompiler.util.UIUtil;

public class DecompileHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        final List classes = UIUtil.getActiveSelection();
        if (classes != null && !classes.isEmpty() && PlatformUI.getWorkbench() != null) {
            IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
            IEditorDescriptor editor = registry.findEditor(JavaDecompilerPlugin.EDITOR_ID);
            IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
            String decompilerType = prefs.getString(JavaDecompilerPlugin.DECOMPILER_TYPE);
            new OpenClassesAction(editor, classes, decompilerType).run();
        } else {
            JavaDecompilerClassFileEditor editor = UIUtil.getActiveEditor();
            if (editor != null) {
                editor.doSetInput(true);
            }
        }
        return null;
    }

}