/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.utils;

import java.io.File;

import org.sf.feeling.decompiler.JavaDecompilerPlugin;

public class SourceConstants {

	public static final String TEMP_SOURCE_PREFIX = "source"; //$NON-NLS-1$

	public static final File USER_M2_REPO_DIR = new File(String.valueOf(System.getProperty("user.home")) //$NON-NLS-1$
			+ File.separatorChar + ".m2" + File.separatorChar + "repository"); //$NON-NLS-1$ //$NON-NLS-2$

	public static final File SourceAttacherDir = new File(String.valueOf(System.getProperty("user.home")) //$NON-NLS-1$
			+ File.separatorChar + ".decompiler" + File.separatorChar + TEMP_SOURCE_PREFIX); //$NON-NLS-1$ //$NON-NLS-2$

	public static final File getSourceTempDir() {
		return new File(JavaDecompilerPlugin.getDefault().getPreferenceStore().getString(JavaDecompilerPlugin.TEMP_DIR)
				+ File.separatorChar + TEMP_SOURCE_PREFIX);
	}

	public static final String SourceAttachPath = SourceAttacherDir.getAbsolutePath();

	public static final String getSourceTempPath() {
		return getSourceTempDir().getAbsolutePath();
	}
}
