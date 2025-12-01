/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.finder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class SourceCheck {

	public static boolean isWrongSource(final File srcFile, final File binFile) throws IOException {
		final List<String> classnames = getJavaFileNames(binFile, ".class"); //$NON-NLS-1$
		final List<String> javanames = getJavaFileNames(srcFile, ".java"); //$NON-NLS-1$
		return !classnames.isEmpty() && javanames.isEmpty();
	}

	private static List<String> getJavaFileNames(final File file, final String ext) throws IOException {
		final List<String> classnames = new ArrayList<>();
		try (final ZipFile zf = new ZipFile(file)) {
			final Enumeration<? extends ZipEntry> entries = zf.entries();
			while (entries.hasMoreElements()) {
				final ZipEntry entry = entries.nextElement();
				final String entryName = entry.getName();
				if (entryName.endsWith(ext)) {
					classnames.add(entryName);
				}
			}
		}
		return classnames;
	}
}