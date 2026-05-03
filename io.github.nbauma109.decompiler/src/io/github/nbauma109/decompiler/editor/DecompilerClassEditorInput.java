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

package io.github.nbauma109.decompiler.editor;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.ui.ide.FileStoreEditorInput;

public class DecompilerClassEditorInput extends FileStoreEditorInput {

    private String toolTipText = null;

    public DecompilerClassEditorInput(IFileStore fileStore) {
        super(fileStore);
    }

    @Override
    public String getToolTipText() {
        if (toolTipText != null) {
            return toolTipText;
        }
        return super.getToolTipText();
    }

    public void setToolTipText(String toolTipText) {
        this.toolTipText = toolTipText;
    }

}
