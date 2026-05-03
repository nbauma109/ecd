/*******************************************************************************
 * (C) 2017 cnfree (@cnfree)
 * (C) 2017 Pascal Bihler
 * (C) 2021 Jan S. (@jpstotz)
 * (C) 2024-2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.editor;

import java.util.Set;
import java.util.TreeSet;

import com.heliosdecompiler.transformerapi.StandardTransformers;
import com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers;

public class DecompilerType {

    private DecompilerType() {
    }

    public static Set<String> getDecompilerTypes() {
        return new TreeSet<>(StandardTransformers.Decompilers.AVAILABLE_DECOMPILERS);
    }

    public static String getDefault() {
        return Decompilers.getDefault().getName();
    }
}
