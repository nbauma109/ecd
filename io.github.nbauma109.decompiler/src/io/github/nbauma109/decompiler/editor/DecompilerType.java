/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.editor;

import java.util.Set;

import com.heliosdecompiler.transformerapi.StandardTransformers;
import com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers;

public class DecompilerType {

    private DecompilerType() {
    }

    public static Set<String> getDecompilerTypes() {
        return StandardTransformers.Decompilers.AVAILABLE_DECOMPILERS;
    }

    public static String getDefault() {
        return Decompilers.getDefault().getName();
    }
}
