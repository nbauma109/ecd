/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach;

import java.util.List;

import org.eclipse.jdt.core.IPackageFragmentRoot;

public interface IAttachSourceHandler {

    default Thread execute(IPackageFragmentRoot library, boolean showUI) {
        return execute(List.of(library), showUI);
    }

    Thread execute(List<IPackageFragmentRoot> libraries, boolean showUI);

    boolean syncAttachSource(IPackageFragmentRoot root);

}
