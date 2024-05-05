/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.finder;

import java.io.File;

public class SourceFileResult {

	private String binFile;
	private String source;
	private String suggestedSourceFileName;
	private int accuracy;
	private SourceCodeFinder finder;
	private String tempSource;

	public SourceFileResult(SourceCodeFinder finder, String binFile, String source, String suggestedSourceFileName,
			int accuracy) {
		this.finder = finder;
		this.binFile = binFile;
		this.source = source;
		this.suggestedSourceFileName = suggestedSourceFileName;
		this.accuracy = accuracy;
	}

	public SourceFileResult(SourceCodeFinder finder, String binFile, File sourceFile, File tempFile, int accuracy) {
		this.finder = finder;
		this.binFile = binFile;
		this.source = sourceFile.getAbsolutePath();
		this.suggestedSourceFileName = sourceFile.getName();
		this.accuracy = accuracy;
		this.tempSource = tempFile.getAbsolutePath();
	}

	@Override
	public String toString() {
		return "SourceFileResult[source = " //$NON-NLS-1$
				+ this.source + " ; suggestedSourceFileName = " //$NON-NLS-1$
				+ this.suggestedSourceFileName + " ; accuracy = " //$NON-NLS-1$
				+ this.accuracy + " ; binFile = " //$NON-NLS-1$
				+ this.binFile + " ; finder = " //$NON-NLS-1$
				+ this.finder + "]";
	}

	public String getBinFile() {
		return this.binFile;
	}

	public void setBinFile(String binFile) {
		this.binFile = binFile;
	}

	public String getSource() {
		return this.source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getSuggestedSourceFileName() {
		return this.suggestedSourceFileName;
	}

	public void setSuggestedSourceFileName(String suggestedSourceFileName) {
		this.suggestedSourceFileName = suggestedSourceFileName;
	}

	public int getAccuracy() {
		return this.accuracy;
	}

	public void setAccuracy(int accuracy) {
		this.accuracy = accuracy;
	}

	public SourceCodeFinder getFinder() {
		return finder;
	}

	public void setFinder(SourceCodeFinder finder) {
		this.finder = finder;
	}

	public String getTempSource() {
		return tempSource;
	}

	public void setTempSource(String tempSource) {
		this.tempSource = tempSource;
	}
}
