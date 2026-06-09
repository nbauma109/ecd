/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.search.ui.text.Match;

import io.github.nbauma109.decompiler.util.Logger;

public final class BytecodeSearchMatch extends Match {

    private final BytecodeSearchEntry entry;

    public BytecodeSearchMatch(BytecodeSearchEntry entry) {
        super(new BytecodeSearchElement(entry), initialOffset(entry), initialLength(entry));
        this.entry = entry;
    }

    public BytecodeSearchEntry getEntry() {
        return entry;
    }

    void update(BytecodeSourceRangeResolver.SourceRange range) {
        setOffset(range.offset());
        setLength(range.length());
    }

    private static int initialOffset(BytecodeSearchEntry entry) {
        ISourceRange range = initialRange(entry);
        return range == null || range.getOffset() < 0 ? 0 : range.getOffset();
    }

    private static int initialLength(BytecodeSearchEntry entry) {
        ISourceRange range = initialRange(entry);
        if (range == null || range.getLength() <= 0) {
            return Math.max(1, entry.getName().length());
        }
        return Math.clamp(range.getLength(), 1, Math.max(1, entry.getName().length()));
    }

    private static ISourceRange initialRange(BytecodeSearchEntry entry) {
        IJavaElement element = entry.getElement();
        if (!(element instanceof ISourceReference sourceReference)) {
            return null;
        }
        try {
            if (entry.isDeclaration()) {
                ISourceRange nameRange = sourceReference.getNameRange();
                if (nameRange != null && nameRange.getOffset() >= 0 && nameRange.getLength() > 0) {
                    return nameRange;
                }
            }
            return sourceReference.getSourceRange();
        } catch (JavaModelException e) {
            Logger.debug(e);
            return null;
        }
    }
}
