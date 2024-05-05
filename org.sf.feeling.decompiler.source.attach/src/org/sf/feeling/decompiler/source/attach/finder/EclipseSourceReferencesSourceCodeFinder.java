/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.finder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.sf.feeling.decompiler.source.attach.utils.SourceAttachUtil;
import org.sf.feeling.decompiler.source.attach.utils.SourceBindingUtil;
import org.sf.feeling.decompiler.source.attach.utils.SourceConstants;
import org.sf.feeling.decompiler.source.attach.utils.UrlDownloader;
import org.sf.feeling.decompiler.util.Logger;

public class EclipseSourceReferencesSourceCodeFinder extends AbstractSourceCodeFinder {

	private String findMetaInfoFromFile(String binFile) throws Exception {
		String result = null;

		try (ZipInputStream in = new ZipInputStream(new java.io.FileInputStream(binFile))) {
			byte[] data = new byte[2048];
			String zipEntryName;
			do {
				ZipEntry entry = in.getNextEntry();
				if (entry == null) {
					break;
				}

				zipEntryName = entry.getName();
			} while (!zipEntryName.equals("META-INF/MANIFEST.MF")); //$NON-NLS-1$
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			for (;;) {
				int read = in.read(data);
				if (read < 0)
					break;
				os.write(data, 0, read);
			}

			Manifest manifest = new Manifest(new java.io.ByteArrayInputStream(os.toByteArray()));
			Attributes attr = manifest.getMainAttributes();
			String esr = attr.getValue("Eclipse-SourceReferences"); //$NON-NLS-1$
			result = esr;
		}
		return result;
	}

	@Override
	public void find(String binFile, List<SourceFileResult> results) {
		try {
			String sourceReferences = findMetaInfoFromFile(binFile);
			if (sourceReferences == null) {
				return;
			}

			String[] sourceFiles = SourceBindingUtil.getSourceFileByDownloadUrl(sourceReferences);
			if (sourceFiles != null && sourceFiles[0] != null && new File(sourceFiles[0]).exists()) {
				File sourceFile = new File(sourceFiles[0]);
				File tempFile = new File(sourceFiles[1]);
				SourceFileResult result = new SourceFileResult(this, binFile, sourceFile, tempFile, 100);
				results.add(result);
				return;
			}

			String tmpFile = new UrlDownloader().download(sourceReferences);
			if (tmpFile != null && new File(tmpFile).exists()
					&& SourceAttachUtil.isSourceCodeFor(tmpFile, new File(binFile).getAbsolutePath())) {
				setDownloadUrl(sourceReferences);
				String name = new File(tmpFile).getName().replace(SourceConstants.TEMP_SOURCE_PREFIX, "eclipse"); //$NON-NLS-1$
				SourceFileResult object = new SourceFileResult(this, binFile, tmpFile, name, 50);
				Logger.debug(toString() + " FOUND: " + object, null); //$NON-NLS-1$
				results.add(object);
			}
		} catch (Throwable e) {
			Logger.debug(e);
		}
	}

	@Override
	public void cancel() {
	}

	public static void main(String[] args) {
		EclipseSourceReferencesSourceCodeFinder finder = new EclipseSourceReferencesSourceCodeFinder();
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		finder.find(
				"C:\\develop\\eclipse-jee-luna-SR2-win32\\eclipse\\plugins\\org.apache.commons.codec_1.6.0.v201305230611.jar", //$NON-NLS-1$
				results);
		System.out.println(results.get(0).getSource());
	}
}
