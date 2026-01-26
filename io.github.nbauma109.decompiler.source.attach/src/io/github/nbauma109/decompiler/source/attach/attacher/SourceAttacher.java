/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.attacher;

import java.io.File;

import org.eclipse.jdt.core.IPackageFragmentRoot;

public interface SourceAttacher {

    boolean attachSource(IPackageFragmentRoot paramIPackageFragmentRoot, File paramString) throws Exception;
}
