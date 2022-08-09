/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.fernflower;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.preference.IPreferenceStore;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.editor.DecompilerType;
import org.sf.feeling.decompiler.editor.IDecompiler;
import org.sf.feeling.decompiler.util.ClassUtil;
import org.sf.feeling.decompiler.util.CommentUtil;
import org.sf.feeling.decompiler.util.FileUtil;
import org.sf.feeling.decompiler.util.JarClassExtractor;
import org.sf.feeling.decompiler.util.Logger;
import org.sf.feeling.decompiler.util.UnicodeUtil;

public class FernFlowerDecompiler implements IDecompiler {

	private String source = ""; // $NON-NLS-1$ //$NON-NLS-1$
	private long time, start;
	private String log = ""; //$NON-NLS-1$

	ByteArrayOutputStream loggerStream;

	/**
	 * Performs a <code>Runtime.exec()</code> on jad executable with selected
	 * options.
	 * 
	 * @see IDecompiler#decompile(String, String, String)
	 */
	@Override
	public void decompile(String root, String packege, final String className) {
		if (root == null || packege == null || className == null) {
			return;
		}

		start = System.currentTimeMillis();
		log = ""; //$NON-NLS-1$
		source = ""; //$NON-NLS-1$

		loggerStream = new ByteArrayOutputStream();

		File workingDir = new File(root, packege); // $NON-NLS-1$

		final Map<String, Object> mapOptions = new HashMap<>();

		mapOptions.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1"); //$NON-NLS-1$
		mapOptions.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1"); //$NON-NLS-1$
		mapOptions.put(IFernflowerPreferences.DECOMPILE_INNER, "1"); //$NON-NLS-1$
		mapOptions.put(IFernflowerPreferences.DECOMPILE_ENUM, "1"); //$NON-NLS-1$
		mapOptions.put(IFernflowerPreferences.LOG_LEVEL, IFernflowerLogger.Severity.ERROR.name());
		mapOptions.put(IFernflowerPreferences.ASCII_STRING_CHARACTERS, "1"); //$NON-NLS-1$
		IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
		boolean showLineNumber = prefs.getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_LINE_NUMBERS);
		boolean align = prefs.getBoolean(JavaDecompilerPlugin.ALIGN);
		if (showLineNumber || ClassUtil.isDebug()) {
			mapOptions.put(IFernflowerPreferences.DUMP_ORIGINAL_LINES, "1"); //$NON-NLS-1$
			mapOptions.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1"); //$NON-NLS-1$
		}

		final File tmpDir = new File(System.getProperty("java.io.tmpdir"), //$NON-NLS-1$
				String.valueOf(System.currentTimeMillis()));

		if (!tmpDir.exists())
			tmpDir.mkdirs();

		// Work around protected constructor
		class EmbeddedConsoleDecompiler extends ConsoleDecompiler {

			protected EmbeddedConsoleDecompiler() {
				super(tmpDir, mapOptions, new PrintStreamLogger(new PrintStream(loggerStream)));
			}

		}

		String classNameFilterTmp = className.toLowerCase();
		if (classNameFilterTmp.endsWith(".class")) {
			classNameFilterTmp = classNameFilterTmp.substring(0, classNameFilterTmp.length() - 6);
		}
		final String classNameFilter = classNameFilterTmp;

		ConsoleDecompiler decompiler = new EmbeddedConsoleDecompiler();

		File[] files = workingDir.listFiles((dir, name) -> {
			name = name.toLowerCase();
			return name.startsWith(classNameFilter) && name.endsWith(".class");
		});
		if (files != null) {
			for (File file : files) {
				decompiler.addSource(file);
			}
		}

		decompiler.decompileContext();

		File classFile = new File(tmpDir, className.replaceAll("(?i)\\.class", ".java")); //$NON-NLS-1$ //$NON-NLS-2$

		try {
			source = UnicodeUtil.decode(Files.readString(classFile.toPath(), StandardCharsets.UTF_8));
			Files.delete(classFile.toPath());
		} catch (IOException e) {
			Logger.error(e);
		}

		FileUtil.deltree(tmpDir);

		if (align) {
			try {
				source = CommentUtil.clearComments(source);
			} catch (Exception e) {
				Logger.error(e);
			}
		}

		time = System.currentTimeMillis() - start;
	}

	/**
	 * Jad doesn't support decompilation from archives. This methods extracts
	 * request class file from the specified archive into temp directory and then
	 * calls <code>decompile</code>.
	 * 
	 * @see IDecompiler#decompileFromArchive(String, String, String)
	 */
	@Override
	public void decompileFromArchive(String archivePath, String packege, String className) {
		start = System.currentTimeMillis();
		File workingDir = new File(
				JavaDecompilerPlugin.getDefault().getPreferenceStore().getString(JavaDecompilerPlugin.TEMP_DIR) + "/" //$NON-NLS-1$
						+ System.currentTimeMillis());

		try {
			workingDir.mkdirs();
			JarClassExtractor.extract(archivePath, packege, className, true, workingDir.getAbsolutePath());
			decompile(workingDir.getAbsolutePath(), "", className); //$NON-NLS-1$
		} catch (Exception e) {
			JavaDecompilerPlugin.logError(e, e.getMessage());
		} finally {
			FileUtil.deltree(workingDir);
		}
	}

	@Override
	public long getDecompilationTime() {
		return time;
	}

	@Override
	public List<Exception> getExceptions() {
		return Collections.emptyList();
	}

	/**
	 * @see IDecompiler#getLog()
	 */
	@Override
	public String getLog() {
		if (loggerStream != null) {
			return log + loggerStream.toString();
		}
		return log;
	}

	/**
	 * @see IDecompiler#getSource()
	 */
	@Override
	public String getSource() {
		return source;
	}

	@Override
	public String getDecompilerType() {
		return DecompilerType.FernFlower;
	}

	@Override
	public String removeComment(String source) {
		return source;
	}

	@Override
	public boolean supportLevel(int level) {
		return true;
	}

	@Override
	public boolean supportDebugLevel(int level) {
		return true;
	}

	@Override
	public boolean supportDebug() {
		return true;
	}

}