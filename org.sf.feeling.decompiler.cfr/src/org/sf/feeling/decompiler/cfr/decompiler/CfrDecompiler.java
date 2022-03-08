/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.cfr.decompiler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.cfr.CfrDecompilerPlugin;
import org.sf.feeling.decompiler.editor.IDecompiler;
import org.sf.feeling.decompiler.util.CommentUtil;
import org.sf.feeling.decompiler.util.FileUtil;
import org.sf.feeling.decompiler.util.JarClassExtractor;

public class CfrDecompiler implements IDecompiler {

	private String source = ""; //$NON-NLS-1$
	private long time;
	private String log = ""; //$NON-NLS-1$

	/**
	 * @see IDecompiler#decompile(String, String, String)
	 */
	@Override
	public void decompile(String root, String packege, String className) {
		log = ""; //$NON-NLS-1$
		source = ""; //$NON-NLS-1$
		File workingDir = new File(root, packege); // $NON-NLS-1$

		String classPathStr = new File(workingDir, className).getAbsolutePath();

		try {
			source = decompile(classPathStr, null, false, true);
			source = CommentUtil.clearComments(source);
		} catch (Exception e) {
			JavaDecompilerPlugin.logError(e, e.getMessage());
		}

	}

	/**
	 * @see IDecompiler#decompileFromArchive(String, String, String)
	 */
	@Override
	public void decompileFromArchive(String archivePath, String packege, String className) {
		long start = System.nanoTime();
		String tempDir = JavaDecompilerPlugin.getDefault().getPreferenceStore()
				.getString(JavaDecompilerPlugin.TEMP_DIR);
		File workingDir = new File(tempDir + "/ecd_cfr_" + System.currentTimeMillis()); //$NON-NLS-1$
		try {
			workingDir.mkdirs();
			JarClassExtractor.extract(archivePath, packege, className, true, workingDir.getAbsolutePath());
			decompile(workingDir.getAbsolutePath(), "", className); //$NON-NLS-1$
			time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
		} catch (Exception e) {
			JavaDecompilerPlugin.logError(e, e.getMessage());
		} finally {
			FileUtil.deltree(workingDir);
		}
	}

	public static String decompile(String classFilePath, String methodName) {
		return decompile(classFilePath, methodName, false);
	}

	public static String decompile(String classFilePath, String methodName, boolean hideUnicode) {
		return decompile(classFilePath, methodName, hideUnicode, true);
	}

	public static Pair<String, NavigableMap<Integer, Integer>> decompileWithMappings(String classFilePath,
			String methodName, boolean hideUnicode, boolean printLineNumber) {
		final StringBuilder sb = new StringBuilder();

		final NavigableMap<Integer, Integer> lineMapping = new TreeMap<>();

		OutputSinkFactory mySink = new CfrOutputSinkFactory(sb, lineMapping);

		HashMap<String, String> options = new HashMap<>();
		options.put("showversion", "true");
		options.put("hideutf", String.valueOf(hideUnicode));
		options.put("trackbytecodeloc", "true");
		if (methodName != null && !methodName.isBlank()) {
			options.put("methodname", methodName);
		}

		CfrDriver driver = new CfrDriver.Builder().withOptions(options).withOutputSink(mySink).build();
		List<String> toAnalyse = new ArrayList<>();
		toAnalyse.add(classFilePath);
		driver.analyse(toAnalyse);

		String resultCode = sb.toString();
		if (printLineNumber && !lineMapping.isEmpty()) {
			resultCode = addLineNumber(resultCode, lineMapping);
		}

		return Pair.make(resultCode, lineMapping);
	}

	public static String decompile(String classFilePath, String methodName, boolean hideUnicode,
			boolean printLineNumber) {
		return decompileWithMappings(classFilePath, methodName, hideUnicode, printLineNumber).getFirst();
	}

	private static String addLineNumber(String src, Map<Integer, Integer> lineMapping) {
		int maxLineNumber = 0;
		for (Integer value : lineMapping.values()) {
			if (value != null && value > maxLineNumber) {
				maxLineNumber = value;
			}
		}

		String formatStr = "/* %2d */ ";
		String emptyStr = "       ";

		StringBuilder sb = new StringBuilder();

		if (maxLineNumber >= 1000) {
			formatStr = "/* %4d */ ";
			emptyStr = "         ";
		} else if (maxLineNumber >= 100) {
			formatStr = "/* %3d */ ";
			emptyStr = "        ";
		}

		int index = 0;
		try (Scanner sc = new Scanner(src)) {
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				Integer srcLineNumber = lineMapping.get(index + 1);
				if (srcLineNumber != null) {
					sb.append(String.format(formatStr, srcLineNumber));
				} else {
					sb.append(emptyStr);
				}
				sb.append(line).append("\n");
				index++;
			}
		}
		return sb.toString();
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
		return CfrDecompilerPlugin.decompilerType;
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