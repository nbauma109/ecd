/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.jad.decompiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.preference.IPreferenceStore;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.editor.IDecompiler;
import org.sf.feeling.decompiler.jad.JadDecompilerPlugin;
import org.sf.feeling.decompiler.util.ClassUtil;
import org.sf.feeling.decompiler.util.FileUtil;
import org.sf.feeling.decompiler.util.JarClassExtractor;
import org.sf.feeling.decompiler.util.UnicodeUtil;

/**
 * This implementation of <code>IDecompiler</code> uses Jad as the underlying
 * decompler.
 */
public class JadDecompiler implements IDecompiler {

	public static final String OPTION_ANNOTATE = "-a"; // format //$NON-NLS-1$
	public static final String OPTION_ANNOTATE_FQ = "-af"; // format //$NON-NLS-1$
	public static final String OPTION_BRACES = "-b"; // format //$NON-NLS-1$
	public static final String OPTION_CLEAR = "-clear"; // format //$NON-NLS-1$
	public static final String OPTION_DIR = "-d"; // ? //$NON-NLS-1$
	public static final String OPTION_DEAD = "-dead"; // directives //$NON-NLS-1$
	public static final String OPTION_DISASSEMBLER = "-dis"; // directives //$NON-NLS-1$
	public static final String OPTION_FULLNAMES = "-f"; // format //$NON-NLS-1$
	public static final String OPTION_FIELDSFIRST = "-ff"; // format //$NON-NLS-1$
	public static final String OPTION_DEFINITS = "-i"; // format //$NON-NLS-1$
	public static final String OPTION_SPLITSTR_MAX = "-l"; // format //$NON-NLS-1$
	public static final String OPTION_LNC = JavaDecompilerPlugin.PREF_DISPLAY_LINE_NUMBERS;// "-lnc";
	// //
	// debug
	public static final String OPTION_LRADIX = "-lradix"; // format //$NON-NLS-1$
	public static final String OPTION_SPLITSTR_NL = "-nl"; // format //$NON-NLS-1$
	public static final String OPTION_NOCONV = "-noconv"; // directives //$NON-NLS-1$
	public static final String OPTION_NOCAST = "-nocast"; // directives //$NON-NLS-1$
	public static final String OPTION_NOCLASS = "-noclass"; // directives //$NON-NLS-1$
	public static final String OPTION_NOCODE = "-nocode"; // directives //$NON-NLS-1$
	public static final String OPTION_NOCTOR = "-noctor"; // directives //$NON-NLS-1$
	public static final String OPTION_NODOS = "-nodos"; // directives //$NON-NLS-1$
	public static final String OPTION_NOFLDIS = "-nofd"; // directives //$NON-NLS-1$
	public static final String OPTION_NOINNER = "-noinner"; // directives //$NON-NLS-1$
	public static final String OPTION_NOLVT = "-nolvt"; // directives //$NON-NLS-1$
	public static final String OPTION_NONLB = "-nonlb"; // format //$NON-NLS-1$
	public static final String OPTION_OVERWRITE = "-o"; // ? //$NON-NLS-1$
	public static final String OPTION_SENDSTDOUT = "-p"; // ? //$NON-NLS-1$
	public static final String OPTION_PA = "-pa"; // directives //$NON-NLS-1$
	public static final String OPTION_PC = "-pc"; // directives //$NON-NLS-1$
	public static final String OPTION_PE = "-pe"; // directives //$NON-NLS-1$
	public static final String OPTION_PF = "-pf"; // directives //$NON-NLS-1$
	public static final String OPTION_PI = "-pi"; // format //$NON-NLS-1$
	public static final String OPTION_PL = "-pl"; // directives //$NON-NLS-1$
	public static final String OPTION_PM = "-pm"; // directives //$NON-NLS-1$
	public static final String OPTION_PP = "-pp"; // directives //$NON-NLS-1$
	public static final String OPTION_PV = "-pv"; // format //$NON-NLS-1$
	public static final String OPTION_RESTORE = "-r"; // ? //$NON-NLS-1$
	public static final String OPTION_IRADIX = "-radix"; // format //$NON-NLS-1$
	public static final String OPTION_EXT = "-s"; // ? //$NON-NLS-1$
	public static final String OPTION_SAFE = "-safe"; // directives //$NON-NLS-1$
	public static final String OPTION_SPACE = "-space"; // format //$NON-NLS-1$
	public static final String OPTION_STAT = "-stat"; // misc //$NON-NLS-1$
	public static final String OPTION_INDENT_SPACE = "-t"; // format //$NON-NLS-1$
	public static final String OPTION_INDENT_TAB = "-t"; // ? //$NON-NLS-1$
	public static final String OPTION_VERBOSE = "-v"; // misc //$NON-NLS-1$
	public static final String OPTION_ANSI = "-8"; // misc //$NON-NLS-1$
	public static final String OPTION_REDSTDERR = "-&"; // ? //$NON-NLS-1$

	public static final String USE_TAB = "use tab"; //$NON-NLS-1$

	public static final String[] TOGGLE_OPTION = { OPTION_ANNOTATE, OPTION_ANNOTATE_FQ, OPTION_BRACES, OPTION_CLEAR,
			OPTION_DEAD, OPTION_DISASSEMBLER, OPTION_FULLNAMES, OPTION_FIELDSFIRST, OPTION_DEFINITS, OPTION_LNC,
			OPTION_SPLITSTR_NL, OPTION_NOCONV, OPTION_NOCAST, OPTION_NOCLASS, OPTION_NOCODE, OPTION_NOCTOR,
			OPTION_NODOS, OPTION_NOFLDIS, OPTION_NOINNER, OPTION_NOLVT, OPTION_NONLB,
			/* OPTION_OVERWRITE, */
			/* OPTION_SENDSTDOUT, */
			/* OPTION_RESTORE, */
			OPTION_SAFE, OPTION_SPACE, OPTION_STAT, OPTION_INDENT_TAB, OPTION_VERBOSE, OPTION_ANSI,
			/* OPTION_REDSTDERR */
	};

	public static final String[] VALUE_OPTION_STRING = {
			/* OPTION_DIR, */
			OPTION_PA, OPTION_PC, OPTION_PE, OPTION_PF, OPTION_PL, OPTION_PM, OPTION_PP,
			/* OPTION_EXT, */
	};

	public static final String[] VALUE_OPTION_INT = {
			/* OPTION_INDENT_SPACE, */
			OPTION_SPLITSTR_MAX, OPTION_LRADIX, OPTION_PI, OPTION_PV, OPTION_IRADIX, };

	private String source = "/* ERROR? */"; //$NON-NLS-1$
	private StringBuffer log;
	private List<Exception> excList = new ArrayList<>();
	private long time;

	private String[] buildCmdLine(String classFileName) {
		ArrayList<String> cmdLine = new ArrayList<>();
		IPreferenceStore settings = JavaDecompilerPlugin.getDefault().getPreferenceStore();

		// command and special options
		cmdLine.add(settings.getString(JadDecompilerPlugin.CMD));
		cmdLine.add(OPTION_SENDSTDOUT);

		String indent = settings.getString(OPTION_INDENT_SPACE);
		if (indent.equals(USE_TAB))
			cmdLine.add(OPTION_INDENT_TAB);
		else {
			try {
				Integer.parseInt(indent);
				cmdLine.add(OPTION_INDENT_SPACE + indent);
			} catch (Exception e) {
			}
		}

		// toggles
		for (int i = 0; i < TOGGLE_OPTION.length; i++) {
			if (settings.getBoolean(TOGGLE_OPTION[i])) {
				if (OPTION_LNC.equals(TOGGLE_OPTION[i])) {
					// cmdLine.add( "-lnc" ); //$NON-NLS-1$
				} else
					cmdLine.add(TOGGLE_OPTION[i]);
			}
		}

		if (ClassUtil.isDebug()) {
			cmdLine.add("-lnc"); //$NON-NLS-1$
		}

		// integers, 0 means disabled
		int iValue;
		for (int i = 0; i < VALUE_OPTION_INT.length; i++) {
			iValue = settings.getInt(VALUE_OPTION_INT[i]);
			if (iValue > 0)
				cmdLine.add(VALUE_OPTION_INT[i] + iValue);
		}

		// strings, "" means disabled
		String sValue;
		for (int i = 0; i < VALUE_OPTION_STRING.length; i++) {
			sValue = settings.getString(VALUE_OPTION_STRING[i]);
			if (sValue != null && sValue.length() > 0)
				cmdLine.add(VALUE_OPTION_STRING[i] + " " + sValue); //$NON-NLS-1$

		}

		cmdLine.add(classFileName);
		// debugCmdLine(cmdLine);
		return cmdLine.toArray(new String[cmdLine.size()]);

	}

	void debugCmdLine(List<String> segments) {
		StringBuffer cmdline = new StringBuffer();
		for (int i = 0; i < segments.size(); i++)
			cmdline.append(segments.get(i)).append(" "); //$NON-NLS-1$
		System.err.println("-> " + cmdline.toString()); //$NON-NLS-1$
	}

	/**
	 * Performs a <code>Runtime.exec()</code> on jad executable with selected
	 * options.
	 * 
	 * @see IDecompiler#decompile(String, String, String)
	 */
	@Override
	public void decompile(String root, String packege, String className) {
		log = new StringBuffer();
		source = ""; //$NON-NLS-1$
		File workingDir = new File(root + "/" + packege); //$NON-NLS-1$
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ByteArrayOutputStream errors = new ByteArrayOutputStream();
		PrintWriter errorsP = new PrintWriter(new OutputStreamWriter(errors));
		// errorsP.println("\n\n\n/***** DECOMPILE LOG *****\n");
		int status = 0;

		long start = System.nanoTime();
		try {

			errorsP.println("\tJad reported messages/errors:"); //$NON-NLS-1$
			Process p = Runtime.getRuntime().exec(buildCmdLine(className), new String[] {}, workingDir);
			StreamRedirectThread outRedirect = new StreamRedirectThread("output_reader", //$NON-NLS-1$
					p.getInputStream(), bos);
			StreamRedirectThread errRedirect = new StreamRedirectThread("error_reader", //$NON-NLS-1$
					p.getErrorStream(), errors);
			outRedirect.start();
			errRedirect.start();
			status = p.waitFor(); // wait for jad to finish
			outRedirect.join(); // wait until output stream content is fully
			// copied
			errRedirect.join(); // wait until error stream content is fully
			// copied
			if (outRedirect.getException() != null)
				excList.add(outRedirect.getException());
			if (errRedirect.getException() != null)
				excList.add(errRedirect.getException());
		} catch (Exception e) {
			excList.add(e);
		} finally {
			try {
				bos.flush();
				bos.close();
				errorsP.println("\tExit status: " + status); //$NON-NLS-1$
				// errorsP.print(" *************************/");
				errors.flush();
				errorsP.close();
			} catch (Exception e) {
				excList.add(e); // will never get here...
			}
			time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
		}

		source = UnicodeUtil.decode(bos.toString());

		log = new StringBuffer(errors.toString());
		// logExceptions();
		// result = new DecompiledClassFile(classFile, source.toString());
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
		File workingDir = new File(
				JavaDecompilerPlugin.getDefault().getPreferenceStore().getString(JavaDecompilerPlugin.TEMP_DIR) + "/" //$NON-NLS-1$
						+ System.currentTimeMillis());

		try {
			workingDir.mkdirs();
			JarClassExtractor.extract(archivePath, packege, className, true, workingDir.getAbsolutePath());
			decompile(workingDir.getAbsolutePath(), "", className); //$NON-NLS-1$
			time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
		} catch (Exception e) {
			excList.add(e);
			// logExceptions();
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
		return excList;
	}

	/**
	 * @see IDecompiler#getLog()
	 */
	@Override
	public String getLog() {
		return log == null ? "" : log.toString(); //$NON-NLS-1$
	}

	// private void logExceptions()
	// {
	// if (log == null) log = new StringBuffer();
	// log.append("\n\tCAUGHT EXCEPTIONS:\n");
	// if (excList.size() == 0) return;
	// StringWriter stackTraces = new StringWriter();
	// PrintWriter stackTracesP = new PrintWriter(stackTraces);
	//
	// for (int i = 0; i < excList.size(); i++)
	// {
	// ((Exception) excList.get(i)).printStackTrace(stackTracesP);
	// stackTracesP.println("");
	// }
	//
	// stackTracesP.flush();
	// stackTracesP.close();
	// log.append(stackTraces.toString());
	// }

	/**
	 * @see IDecompiler#getSource()
	 */
	@Override
	public String getSource() {
		return source;
	}

	@Override
	public String getDecompilerType() {
		return JadDecompilerPlugin.decompilerType;
	}

	@Override
	public String removeComment(String source) {

		String[] spilts = source.replace("\r\n", "\n").split("\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < spilts.length; i++) {
			if (i > 0 && i < 5)
				continue;
			String string = spilts[i];
			Pattern pattern = Pattern.compile("\\s*/\\*\\s*\\S*\\*/", //$NON-NLS-1$
					Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(string);
			if (matcher.find()) {
				if (matcher.start() == 0) {
					buffer.append(string).append("\r\n"); //$NON-NLS-1$
					continue;
				}
			}

			boolean refer = false;

			pattern = Pattern.compile("\\s*// Referenced", //$NON-NLS-1$
					Pattern.CASE_INSENSITIVE);
			matcher = pattern.matcher(string);
			if (matcher.find()) {
				refer = true;

				while (true) {
					i++;
					if (spilts[i].trim().startsWith("//")) //$NON-NLS-1$
					{
						continue;
					} else if (i >= spilts.length) {
						break;
					} else {
						i--;
						break;
					}
				}
			}

			if (!refer)
				buffer.append(string + "\r\n"); //$NON-NLS-1$
		}
		return buffer.toString();

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

// OPTION_ANNOTATE,
// OPTION_ANNOTATE_FQ,
// OPTION_BRACES,
// OPTION_CLEAR,
// OPTION_DIR,
// OPTION_DEAD,
// OPTION_DISASSEMBLER,
// OPTION_FULLNAMES,
// OPTION_FIELDSFIRST,
// OPTION_DEFINITS,
// OPTION_SPLITSTR_MAX,
// OPTION_LNC,
// OPTION_LRADIX,
// OPTION_SPLITSTR_NL,
// OPTION_NOCONV,
// OPTION_NOCAST,
// OPTION_NOCLASS,
// OPTION_NOCODE,
// OPTION_NOCTOR,
// OPTION_NODOS,
// OPTION_NOFLDIS,
// OPTION_NOINNER,
// OPTION_NOLVT,
// OPTION_NOLB,
// OPTION_OVERWRITE,
// OPTION_SENDSTDOUT,
// OPTION_PA,
// OPTION_PC,
// OPTION_PE,
// OPTION_PF,
// OPTION_PI,
// OPTION_PL,
// OPTION_PM,
// OPTION_PP,
// OPTION_PV,
// OPTION_RESTORE,
// OPTION_IRADIX,
// OPTION_EXT,
// OPTION_SAFE,
// OPTION_SPACE,
// OPTION_STAT,
// OPTION_INDENT_SPACE,
// OPTION_INDENT_TAB,
// OPTION_VERBOSE,
// OPTION_ANSI,
// OPTION_REDSTDERR