/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.procyon.decompiler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jface.preference.IPreferenceStore;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.editor.IDecompiler;
import org.sf.feeling.decompiler.procyon.ProcyonDecompilerPlugin;
import org.sf.feeling.decompiler.procyon.decompiler.LineNumberFormatter.LineNumberOption;
import org.sf.feeling.decompiler.util.ClassUtil;
import org.sf.feeling.decompiler.util.CommentUtil;
import org.sf.feeling.decompiler.util.FileUtil;
import org.sf.feeling.decompiler.util.JarClassExtractor;
import org.sf.feeling.decompiler.util.Logger;
import org.sf.feeling.decompiler.util.UnicodeUtil;

import com.strobel.assembler.InputTypeLoader;
import com.strobel.assembler.metadata.DeobfuscationUtilities;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.languages.Language;
import com.strobel.decompiler.languages.LineNumberPosition;
import com.strobel.decompiler.languages.TypeDecompilationResults;

public class ProcyonDecompiler implements IDecompiler {

	private String source = ""; // $NON-NLS-1$ //$NON-NLS-1$
	private long time;
	private String log = ""; //$NON-NLS-1$

	/**
	 * @see IDecompiler#decompile(String, String, String)
	 */
	@Override
	public void decompile(String root, String packege, String className) {
		long start = System.nanoTime();
		log = ""; //$NON-NLS-1$
		source = ""; //$NON-NLS-1$
		File workingDir = new File(root, packege); // $NON-NLS-1$

		final String classPathStr = new File(workingDir, className).getAbsolutePath();

		final IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
		final boolean showLineNumber = prefs.getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_LINE_NUMBERS);
		final boolean includeLineNumbers = showLineNumber || ClassUtil.isDebug();

		DecompilationOptions decompilationOptions = new DecompilationOptions();

		DecompilerSettings settings = DecompilerSettings.javaDefaults();
		settings.setTypeLoader(new InputTypeLoader((internalName, buffer) -> false));
		settings.setForceExplicitImports(true);

		decompilationOptions.setSettings(settings);
		decompilationOptions.setFullDecompilation(true);

		MetadataSystem metadataSystem = new NoRetryMetadataSystem(decompilationOptions.getSettings().getTypeLoader());
		metadataSystem.setEagerMethodLoadingEnabled(false);

		TypeReference type = metadataSystem.lookupType(classPathStr);

		TypeDefinition resolvedType;
		if ((type == null) || ((resolvedType = type.resolve()) == null)) {
			Logger.error("!!! ERROR: Failed to load class " //$NON-NLS-1$
					+ classPathStr);
			return;
		}

		DeobfuscationUtilities.processType(resolvedType);

		String property = "java.io.tmpdir"; //$NON-NLS-1$
		String tempDir = System.getProperty(property);
		File classFile = new File(tempDir, System.currentTimeMillis() + className);
		try {
			TypeDecompilationResults results;
			try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(classFile)))) {

				PlainTextOutput output = new PlainTextOutput(writer);

				output.setUnicodeOutputEnabled(decompilationOptions.getSettings().isUnicodeOutputEnabled());

				Language lang = decompilationOptions.getSettings().getLanguage();

				// perform the actual decompilation
				results = lang.decompileType(resolvedType, output, decompilationOptions);
			}

			List<LineNumberPosition> lineNumberPositions = results.getLineNumberPositions();

			if (includeLineNumbers) {
				EnumSet<LineNumberOption> lineNumberOptions = EnumSet.noneOf(LineNumberOption.class);

				lineNumberOptions.add(LineNumberFormatter.LineNumberOption.LEADING_COMMENTS);

				LineNumberFormatter lineFormatter = new LineNumberFormatter(classFile, lineNumberPositions,
						lineNumberOptions);

				boolean align = prefs.getBoolean(JavaDecompilerPlugin.ALIGN);
				if (showLineNumber) {
					source = lineFormatter.reformatFile();
					if (align) {
						source = CommentUtil.clearComments(source);
					}
				}
			} else {
				source = FileUtil.getContent(classFile);
			}

		} catch (Exception e) {
			Logger.error(e);
		}

		source = UnicodeUtil.decode(source);

		try {
			Files.delete(classFile.toPath());
		} catch (IOException e) {
			Logger.error(e);
		}

		time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
	}

	/**
	 * @see IDecompiler#decompileFromArchive(String, String, String)
	 */
	@Override
	public void decompileFromArchive(String archivePath, String packege, String className) {
		long start = System.nanoTime();
		File workingDir = new File(
				JavaDecompilerPlugin.getDefault().getPreferenceStore().getString(JavaDecompilerPlugin.TEMP_DIR) + "/" //$NON-NLS-1$
						+ System.currentTimeMillis());

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
		return ProcyonDecompilerPlugin.decompilerType;
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