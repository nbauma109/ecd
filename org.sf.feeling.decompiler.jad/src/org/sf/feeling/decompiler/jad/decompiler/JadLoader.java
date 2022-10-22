/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.jad.decompiler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.Platform;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.util.IOUtils;

public final class JadLoader {

	public static String loadJad() {
		String jadFileName = null;
		String jadFilePath = null;

		String os = Platform.getOS();
		if (Platform.OS_WIN32.equalsIgnoreCase(os)) {
			jadFileName = "jad" + System.currentTimeMillis() + ".exe"; //$NON-NLS-1$ //$NON-NLS-2$
			// Jad_exe instead of Jad.exe to avoid GitHub page nag message
			jadFilePath = "/native/jad/win32/jad_exe"; //$NON-NLS-1$
		} else if (Platform.OS_LINUX.equalsIgnoreCase(os)) {
			jadFileName = "jad" + System.currentTimeMillis(); //$NON-NLS-1$
			jadFilePath = "/native/jad/linux/jad"; //$NON-NLS-1$
		} else if (Platform.OS_MACOSX.equalsIgnoreCase(os)) {
			jadFileName = "jad" + System.currentTimeMillis(); //$NON-NLS-1$
			jadFilePath = "/native/jad/macosx/jad"; //$NON-NLS-1$
		} else {
			throw new Error("Can't obtain jad executable file."); //$NON-NLS-1$
		}

		try {
			File tempDir = new File(
					JavaDecompilerPlugin.getDefault().getPreferenceStore().getString(JavaDecompilerPlugin.TEMP_DIR));
			File jad = new File(tempDir, jadFileName);

			try (InputStream is = JadLoader.class.getResourceAsStream(jadFilePath)) { // $NON-NLS-1$
				if (is == null) {
					throw new Error("Can't obtain jad executable file."); //$NON-NLS-1$
				}
				if (!tempDir.exists()) {
					tempDir.mkdirs();
				}
				jad.createNewFile();
				jad.deleteOnExit();
				try (FileOutputStream fos = new FileOutputStream(jad)) {
					IOUtils.copy(is, fos);
				}
			}

			if (Platform.OS_LINUX.equalsIgnoreCase(os) || Platform.OS_MACOSX.equalsIgnoreCase(os)) {
				Set<PosixFilePermission> perms = new HashSet<>();
				perms.add(PosixFilePermission.OWNER_EXECUTE);
				perms.add(PosixFilePermission.GROUP_EXECUTE);
				perms.add(PosixFilePermission.OTHERS_EXECUTE);

				Files.setPosixFilePermissions(jad.toPath(), perms);
			}

			return jad.getAbsolutePath();
		} catch (IOException e) {
			throw new Error("Failed to create temporary file for jad.exe: " //$NON-NLS-1$
					+ e);
		}
	}
}
