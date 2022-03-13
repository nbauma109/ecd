/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.jd.decompiler;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.lazy.LazyLoader;
import org.jetbrains.java.decompiler.util.DataInputFullStream;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.editor.IDecompiler;
import org.sf.feeling.decompiler.jd.JDCoreDecompilerPlugin;
import org.sf.feeling.decompiler.util.FileUtil;
import org.sf.feeling.decompiler.util.UIUtil;

import jd.ide.eclipse.editors.JDSourceMapper;

public class JDCoreDecompiler implements IDecompiler {

	private String source = ""; // $NON-NLS-1$ //$NON-NLS-1$
	private long time;
	private String log = ""; //$NON-NLS-1$

	private JDSourceMapper mapper;

	public JDCoreDecompiler(JDSourceMapper mapper) {
		this.mapper = mapper;
	}

	/**
	 * Performs a <code>Runtime.exec()</code> on jad executable with selected
	 * options.
	 * 
	 * @see IDecompiler#decompile(String, String, String)
	 */
	@Override
	public void decompile(String root, String classPackage, String className) {
		long start = System.nanoTime();
		log = ""; //$NON-NLS-1$
		source = ""; //$NON-NLS-1$
		Boolean displayNumber = null;

		File workingDir = new File(root); // $NON-NLS-1$

		File zipFile = new File(System.getProperty("java.io.tmpdir"), //$NON-NLS-1$
				className.replaceAll("(?i)\\.class", System.currentTimeMillis() + ".jar")); //$NON-NLS-1$ //$NON-NLS-2$
		String zipFileName = zipFile.getAbsolutePath();

		try {
			if (classPackage.length() == 0) {
				DataInputFullStream difs = new DataInputFullStream(FileUtil.getBytes(new File(root, className)));
				StructClass structClass = StructClass.create(difs, true, new LazyLoader(null));
				structClass.releaseResources();
				classPackage = structClass.qualifiedName.replace("/" //$NON-NLS-1$
						+ className.replaceAll("(?i)\\.class", ""), ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			FileUtil.zipDir(workingDir, classPackage, zipFileName);

			if (UIUtil.isDebugPerspective() || JavaDecompilerPlugin.getDefault().isDebugMode()) {
				displayNumber = JavaDecompilerPlugin.getDefault().isDisplayLineNumber();
				JavaDecompilerPlugin.getDefault().displayLineNumber(Boolean.TRUE);
			}

			source = mapper.decompile(zipFileName, (classPackage.length() > 0 ? (classPackage + "/") : "") //$NON-NLS-1$ //$NON-NLS-2$
					+ className);

			if (!zipFile.delete()) {
				zipFile.deleteOnExit();
			}
		} catch (Exception e) {
			JavaDecompilerPlugin.logError(e, e.getMessage());
		}

		if (displayNumber != null) {
			JavaDecompilerPlugin.getDefault().displayLineNumber(displayNumber);
		}

		time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
	}

	/**
	 * Our {@link JDCoreZipLoader} supports direct decompilation from within a JAR
	 * archive
	 * 
	 * @see IDecompiler#decompileFromArchive(String, String, String)
	 */
	@Override
	public void decompileFromArchive(String archivePath, String packege, String className) {
		long start = System.nanoTime();
		Boolean displayNumber = null;

		try {
			if (UIUtil.isDebugPerspective() || JavaDecompilerPlugin.getDefault().isDebugMode()) {
				displayNumber = JavaDecompilerPlugin.getDefault().isDisplayLineNumber();
				JavaDecompilerPlugin.getDefault().displayLineNumber(Boolean.TRUE);
			}

			String decompileClassName = packege + "/" + className.replaceAll("(?i)\\.class$", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			source = mapper.decompile(archivePath, decompileClassName);
		} catch (Exception e) {
			JavaDecompilerPlugin.logError(e, e.getMessage());
		}

		if (displayNumber != null) {
			JavaDecompilerPlugin.getDefault().displayLineNumber(displayNumber);
		}

		time = (System.nanoTime() - start) / 1000000;
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
		return JDCoreDecompilerPlugin.decompilerType;
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