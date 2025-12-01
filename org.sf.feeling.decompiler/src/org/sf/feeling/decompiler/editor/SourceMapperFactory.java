/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.editor;

import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.fernflower.FernFlowerSourceMapper;

public class SourceMapperFactory {

	private static DecompilerSourceMapper fernFlowerSourceMapper;

	public static DecompilerSourceMapper getSourceMapper(String decompiler) {

		if (DecompilerType.FernFlower.equals(decompiler)) {
			if (fernFlowerSourceMapper == null) {
				fernFlowerSourceMapper = new FernFlowerSourceMapper();
			}
			return fernFlowerSourceMapper;
		}
        IDecompilerDescriptor descriptor = JavaDecompilerPlugin.getDefault().getDecompilerDescriptor(decompiler);
        if (descriptor != null) {
            return descriptor.getDecompilerSourceMapper();
        }
		return null;
	}
}
