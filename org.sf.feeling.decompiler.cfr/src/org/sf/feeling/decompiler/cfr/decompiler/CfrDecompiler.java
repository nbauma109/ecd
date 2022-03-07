/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.cfr.decompiler;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.benf.cfr.reader.apiunreleased.ClassFileSource2;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollectingDumper;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;
import org.benf.cfr.reader.util.output.MethodErrorCollector;
import org.benf.cfr.reader.util.output.StringStreamDumper;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.cfr.CfrDecompilerPlugin;
import org.sf.feeling.decompiler.editor.IDecompiler;
import org.sf.feeling.decompiler.util.FileUtil;
import org.sf.feeling.decompiler.util.JarClassExtractor;
import org.sf.feeling.decompiler.util.UnicodeUtil;

public class CfrDecompiler implements IDecompiler {

	private String source = ""; //$NON-NLS-1$
	private long time;
	private String log = ""; //$NON-NLS-1$

	/**
	 * Performs a <code>Runtime.exec()</code> on CFR with selected options.
	 * 
	 * @see IDecompiler#decompile(String, String, String)
	 */
	@Override
	public void decompile(String root, String packege, String className) {
		log = ""; //$NON-NLS-1$
		source = ""; //$NON-NLS-1$
		File workingDir = new File(root + "/" + packege); //$NON-NLS-1$

		String classPathStr = new File(workingDir, className).getAbsolutePath();

		GetOptParser getOptParser = new GetOptParser();

		try {
			Pair<List<String>, Options> options = getOptParser.parse(new String[] { classPathStr },
					OptionsImpl.getFactory());
			Options namedOptions = options.getSecond();
			ClassFileSource2 classFileSource = new ClassFileSourceImpl(namedOptions);
			classFileSource.informAnalysisRelativePathDetail(null, null);
			DCCommonState dcCommonState = new DCCommonState(namedOptions, classFileSource);

			IllegalIdentifierDump illegalIdentifierDump = IllegalIdentifierDump.Factory.get(namedOptions);

			ClassFile classFile = dcCommonState.getClassFileMaybePath(classPathStr);
			dcCommonState.configureWith(classFile);
			try {
				classFile = dcCommonState.getClassFile(classFile.getClassType());
			} catch (CannotLoadClassException e) {
				e.printStackTrace();
			}
			if (namedOptions.getOption(OptionsImpl.DECOMPILE_INNER_CLASSES).booleanValue()) {
				classFile.loadInnerClasses(dcCommonState);
			}
			TypeUsageCollectingDumper typeUsageCollectingDumper = new TypeUsageCollectingDumper(namedOptions,
					classFile);

			classFile.analyseTop(dcCommonState, typeUsageCollectingDumper);

			TypeUsageInformation typeUsageInfo = typeUsageCollectingDumper.getRealTypeUsageInformation();

			MethodErrorCollector methodErrorCollector = new MethodErrorCollector() {

				@Override
				public void addSummaryError(Method paramMethod, String msg) {
					log += String.format("\n%s: %s", paramMethod.toString(), msg);
				}

			};

			StringBuilder stringBuilder = new StringBuilder(4096);
			StringStreamDumper dumper = new StringStreamDumper(methodErrorCollector, stringBuilder, typeUsageInfo,
					namedOptions, illegalIdentifierDump);
			classFile.dump(dumper);
			source = UnicodeUtil.decode(stringBuilder.toString().trim());

			Pattern wp = Pattern.compile("/\\*.+?\\*/", Pattern.DOTALL); //$NON-NLS-1$
			Matcher m = wp.matcher(source);
			while (m.find()) {
				if (m.group().matches("/\\*\\s+\\d*\\s+\\*/")) //$NON-NLS-1$
					continue;
				String group = m.group();
				group = group.replace("/*", ""); //$NON-NLS-1$ //$NON-NLS-2$
				group = group.replace("*/", ""); //$NON-NLS-1$ //$NON-NLS-2$
				group = group.replace("*", ""); //$NON-NLS-1$ //$NON-NLS-2$
				if (log.length() > 0) {
					log += "\n"; //$NON-NLS-1$
				}
				log += group;

				source = source.replace(m.group(), "").trim(); //$NON-NLS-1$
			}

		} catch (Exception e) {
			JavaDecompilerPlugin.logError(e, e.getMessage());
		}

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
			return;
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
		return false;
	}

	@Override
	public boolean supportDebug() {
		return false;
	}
}