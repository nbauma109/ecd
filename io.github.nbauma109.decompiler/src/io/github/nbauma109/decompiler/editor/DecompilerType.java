/*******************************************************************************
 * © 2017 Chen Chao (@cnfree)
 * © 2017 Pascal Bihler (@pbi-qfs)
 * © 2021 Jan Peter Stotz (@jpstotz)
 * © 2024-2026 Nicolas Baumann (@nbauma109)
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
