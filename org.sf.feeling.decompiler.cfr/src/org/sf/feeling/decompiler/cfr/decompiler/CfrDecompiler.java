/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.cfr.decompiler;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.benf.cfr.reader.api.CfrDriver;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.cfr.CfrDecompilerPlugin;
import org.sf.feeling.decompiler.editor.IDecompiler;
import org.sf.feeling.decompiler.util.ClassUtil;
import org.sf.feeling.decompiler.util.FileUtil;
import org.sf.feeling.decompiler.util.JarClassExtractor;

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
		try (CFRZipLoader loader = new CFRZipLoader(Paths.get(root), null)) {
			CFROutputStreamFactory sink = new CFROutputStreamFactory();
			String entryPath = packege + '/' + className;
			String internalName = ClassUtil.getInternalName(className);
			CfrDriver driver = new CfrDriver.Builder()
					.withClassFileSource(new CFRDataSource(loader, loader.load(internalName), entryPath))
					.withOutputSink(sink).build();
			driver.analyse(Arrays.asList(entryPath));
			source = sink.getGeneratedSource();
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