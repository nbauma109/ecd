/*******************************************************************************
 * Copyright (c) 2026 @nbauma109.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.editor;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorMatchingStrategy;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.PartInitException;

import io.github.nbauma109.decompiler.util.ClassUtil;

public class TopLevelClassFileEditorMatchingStrategy implements IEditorMatchingStrategy {

    @Override
    public boolean matches(IEditorReference editorRef, IEditorInput input) {
        try {
            IEditorInput editorInput = editorRef.getEditorInput();
            IClassFile requestedClassFile = getTopLevelClassFile(input);
            IClassFile openClassFile = getTopLevelClassFile(editorInput);
            if (requestedClassFile != null && openClassFile != null) {
                return requestedClassFile.equals(openClassFile);
            }
            return input != null && input.equals(editorInput);
        } catch (PartInitException e) {
            return false;
        }
    }

    private IClassFile getTopLevelClassFile(IEditorInput input) {
        if (input instanceof IClassFileEditorInput classFileInput) {
            return ClassUtil.getTopLevelClassFile(classFileInput.getClassFile());
        }
        return null;
    }
}
