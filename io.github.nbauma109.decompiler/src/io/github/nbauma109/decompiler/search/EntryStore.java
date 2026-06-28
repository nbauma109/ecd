/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import org.eclipse.core.runtime.CoreException;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;

interface EntryStore extends AutoCloseable {

    @FunctionalInterface
    interface EntryConsumer {
        void accept(BytecodeSearchEntry entry) throws CoreException;
    }

    int size();

    void collect(Kind kind, String name, String qualifiedName, boolean wildcard,
            EntryConsumer consumer) throws CoreException;

    @Override
    void close();
}
