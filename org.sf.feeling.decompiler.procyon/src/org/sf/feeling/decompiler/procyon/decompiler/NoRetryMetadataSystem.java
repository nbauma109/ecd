/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.procyon.decompiler;

import java.util.HashSet;
import java.util.Set;

import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;

public final class NoRetryMetadataSystem extends MetadataSystem {

    private final Set<String> _failedTypes = new HashSet<>();

    public NoRetryMetadataSystem() {
    }

    public NoRetryMetadataSystem(ITypeLoader typeLoader) {
        super(typeLoader);
    }

    @Override
    protected TypeDefinition resolveType(String descriptor, boolean mightBePrimitive) {
        if (this._failedTypes.contains(descriptor)) {
            return null;
        }
        TypeDefinition result = super.resolveType(descriptor, mightBePrimitive);
        if (result == null) {
            this._failedTypes.add(descriptor);
        }
        return result;
    }
}
