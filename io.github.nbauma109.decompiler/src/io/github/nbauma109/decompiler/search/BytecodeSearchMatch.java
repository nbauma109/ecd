/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.util.Objects;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.search.ui.text.Match;

import io.github.nbauma109.decompiler.util.Logger;

public final class BytecodeSearchMatch extends Match {

    private final BytecodeSearchEntry entry;
    private final int ordinal;

    public BytecodeSearchMatch(BytecodeSearchEntry entry) {
        this(entry, 0);
    }

    public BytecodeSearchMatch(BytecodeSearchEntry entry, int ordinal) {
        super(new BytecodeSearchElement(entry), initialOffset(entry, ordinal), initialLength(entry));
        this.entry = entry;
        this.ordinal = ordinal;
    }

    public BytecodeSearchEntry getEntry() {
        return entry;
    }

    public int getOrdinal() {
        return ordinal;
    }

    void update(BytecodeSourceRangeResolver.SourceRange range) {
        setOffset(range.offset());
        setLength(range.length());
    }

    private static int initialOffset(BytecodeSearchEntry entry, int ordinal) {
        ISourceRange range = initialRange(entry);
        int base = range == null || range.getOffset() < 0 ? 0 : range.getOffset();
        // Eclipse deduplicates matches whose (element, offset, length) tuple is identical.
        // With handle-only element equality, different descriptor groups share the same element
        // key, so their initial offsets must also differ.  We fold the descriptor into a slot
        // number and multiply by a value that exceeds any realistic per-entry occurrence count.
        // The real offset is set via update() before any navigation, so this value is transient.
        int slot = Math.abs(Objects.hash(entry.getDescriptor(), entry.getName()) % 9973);
        return base + slot * 10000 + ordinal;
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
