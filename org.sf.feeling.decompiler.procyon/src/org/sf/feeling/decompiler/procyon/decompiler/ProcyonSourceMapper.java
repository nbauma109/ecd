/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.procyon.decompiler;

import java.util.Collection;

import org.eclipse.core.runtime.Path;
import org.sf.feeling.decompiler.editor.BaseDecompilerSourceMapper;
import org.sf.feeling.decompiler.procyon.ProcyonDecompilerPlugin;

public class ProcyonSourceMapper extends BaseDecompilerSourceMapper {

	public ProcyonSourceMapper() {
		super(new Path("."), ""); //$NON-NLS-1$ //$NON-NLS-2$
		origionalDecompiler = new ProcyonDecompiler();
	}

	@Override
	protected void printDecompileReport(StringBuffer source, String fileLocation, Collection<Exception> exceptions,
			long decompilationTime) {
		String logMsg = origionalDecompiler.getLog().replaceAll("\t", "") //$NON-NLS-1$ //$NON-NLS-2$
				.replaceAll("\n\\s*", "\n\t"); //$NON-NLS-1$ //$NON-NLS-2$

		source.append("\n\n/*"); //$NON-NLS-1$
		source.append("\n\tDECOMPILATION REPORT\n"); //$NON-NLS-1$
		source.append("\n\tDecompiled from: "); //$NON-NLS-1$
		source.append(fileLocation);
		source.append("\n\tTotal time: "); //$NON-NLS-1$
		source.append(decompilationTime);
		source.append(" ms\n\t"); //$NON-NLS-1$
		source.append(logMsg);
		exceptions.addAll(origionalDecompiler.getExceptions());
		logExceptions(exceptions, source);
		source.append("\n\tDecompiled with "); //$NON-NLS-1$
		source.append(ProcyonDecompilerPlugin.decompilerType);
		source.append(" version ");//$NON-NLS-1$
		source.append(ProcyonDecompilerPlugin.decompilerVersion);
		source.append(".\n*/"); //$NON-NLS-1$
	}

}