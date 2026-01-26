/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.utils;

import java.io.File;
import java.nio.file.Paths;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;

public class SourceConstants {

    private static final String USER_HOME = System.getProperty("user.home"); //$NON-NLS-1$

    static final String TEMP_SOURCE_PREFIX = "source"; //$NON-NLS-1$

    public static final File USER_M2_REPO_DIR = Paths.get(USER_HOME,
            ".m2", "repository").toFile(); //$NON-NLS-1$ //$NON-NLS-2$

    public static final File USER_GRADLE_CACHE_DIR = Paths.get(USER_HOME,
            ".gradle", "caches", "modules-2", "files-2.1").toFile(); //$NON-NLS-1$ //$NON-NLS-2$

    public static final File SourceAttacherDir = Paths.get(USER_HOME,
            ".decompiler", TEMP_SOURCE_PREFIX).toFile(); //$NON-NLS-1$

    public static final File getSourceTempDir() {
        return new File(JavaDecompilerPlugin.getDefault().getPreferenceStore().getString(JavaDecompilerPlugin.TEMP_DIR)
                + File.separatorChar + TEMP_SOURCE_PREFIX);
    }

    public static final String SourceAttachPath = SourceAttacherDir.getAbsolutePath();
}
