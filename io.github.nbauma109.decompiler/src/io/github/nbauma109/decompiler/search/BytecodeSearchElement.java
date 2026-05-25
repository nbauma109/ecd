/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IJavaElement;

public final class BytecodeSearchElement implements IAdaptable {

    private final BytecodeSearchEntry entry;

    public BytecodeSearchElement(BytecodeSearchEntry entry) {
        this.entry = entry;
    }

    BytecodeSearchEntry getEntry() {
        return entry;
    }

    IJavaElement getJavaElement() {
        return entry.getElement();
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        IJavaElement javaElement = getJavaElement();
        if (javaElement != null && adapter.isInstance(javaElement)) {
            return adapter.cast(javaElement);
        }
        return null;
    }

    @Override
    public String toString() {
        IJavaElement javaElement = getJavaElement();
        return javaElement == null ? entry.getName() : javaElement.getElementName();
    }
}
