/*******************************************************************************
 * © 2017 Chen Chao (@cnfree)
 * © 2017 Pascal Bihler (@pbi-qfs)
 * © 2021 Jan Peter Stotz (@jpstotz)
 * © 2025-2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.attacher;

import java.io.File;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IPackageFragmentRoot;

public interface SourceAttacher {

    boolean attachSource(IPackageFragmentRoot paramIPackageFragmentRoot, File paramString) throws CoreException;
}
