/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.jad.decompiler;

import java.util.Collection;

import org.eclipse.core.runtime.Path;
import org.sf.feeling.decompiler.editor.BaseDecompilerSourceMapper;

public class JadSourceMapper extends BaseDecompilerSourceMapper {

	public JadSourceMapper() {
		super(new Path("."), ""); //$NON-NLS-1$ //$NON-NLS-2$
		origionalDecompiler = new JadDecompiler();
	}

	@Override
	protected void printDecompileReport(StringBuffer source, String fileLocation, Collection<Exception> exceptions,
			long decompilationTime) {
		String location = "\tDecompiled from: " //$NON-NLS-1$
				+ fileLocation;
		source.append("\n\n/*"); //$NON-NLS-1$
		source.append("\n\tDECOMPILATION REPORT\n\n"); //$NON-NLS-1$
		source.append(location).append("\n"); //$NON-NLS-1$
		source.append("\tTotal time: ") //$NON-NLS-1$
				.append(decompilationTime).append(" ms\n"); //$NON-NLS-1$
		source.append("\t" //$NON-NLS-1$
				+ origionalDecompiler.getLog().replace("\t", "").replace("\n", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						"\n\t")); //$NON-NLS-1$
		logExceptions(exceptions, source);
		source.append("\n\tDecompiled with Jad."); //$NON-NLS-1$
		source.append("\n*/"); //$NON-NLS-1$
	}

}