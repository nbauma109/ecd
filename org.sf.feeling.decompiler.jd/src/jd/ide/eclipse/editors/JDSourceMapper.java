/*
 * Copyright (c) 2008-2015 Emmanuel Dupuy
 * This program is made available under the terms of the GPLv3 License.
 */

package jd.ide.eclipse.editors;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.jd.core.v1.ClassFileToJavaSourceDecompiler;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.editor.BaseDecompilerSourceMapper;
import org.sf.feeling.decompiler.jd.decompiler.JDCorePrinter;
import org.sf.feeling.decompiler.jd.decompiler.JDCoreZipLoader;
import org.sf.feeling.decompiler.jd.decompiler.JDCoreZipLoader.EntriesCache;

/**
 * JDSourceMapper
 * 
 * @project Java Decompiler Eclipse Plugin
 * @version 0.1.5
 * @see org.eclipse.jdt.internal.core.SourceMapper
 */
public abstract class JDSourceMapper extends BaseDecompilerSourceMapper {

	private final static String JAVA_CLASS_SUFFIX = ".class";
	private final static String JAVA_SOURCE_SUFFIX = ".java";
	private final static int JAVA_SOURCE_SUFFIX_LENGTH = 5;

	private static EntriesCache entriesCache = null;

	private File basePath;

	public JDSourceMapper(File basePath, IPath sourcePath, String sourceRootPath, Map<String, String> options) {
		super(sourcePath, sourceRootPath, options);
		this.basePath = basePath;
	}

	@Override
	public char[] findSource(String javaSourcePath) {
		char[] source = null;

		// Search source file
		if (this.rootPaths == null) {
			source = super.findSource(javaSourcePath);
		} else {
			Iterator<String> iterator = this.rootPaths.iterator();
			while (iterator.hasNext() && (source == null)) {
				String sourcesRootPath = iterator.next();
				source = super.findSource(sourcesRootPath + IPath.SEPARATOR + javaSourcePath);
			}
		}

		if ((source == null) && javaSourcePath.endsWith(JAVA_SOURCE_SUFFIX)) {
			String classPath = javaSourcePath.substring(0, javaSourcePath.length() - JAVA_SOURCE_SUFFIX_LENGTH)
					+ JAVA_CLASS_SUFFIX;

			// Decompile class file
			try {
				File decompilePath = this.basePath.getAbsoluteFile();
				if (decompilePath.isFile()) {
					String result = decompile(decompilePath.toString(), classPath);
					if (result != null) {
						source = result.toCharArray();
					}
				} else {
					JavaDecompilerPlugin.getDefault().getLog()
							.log(new Status(IStatus.ERROR, JavaDecompilerPlugin.PLUGIN_ID, 0,
									"Unable to decompile: " + decompilePath + " is not a valid file.", null));
				}
			} catch (Exception e) {
				JavaDecompilerPlugin.getDefault().getLog()
						.log(new Status(IStatus.ERROR, JavaDecompilerPlugin.PLUGIN_ID, 0, e.getMessage(), e));
			}
		}

		return source;
	}

	/**
	 * @param basePath          Path to the root of the classpath, either a path to
	 *                          a directory or a path to a jar file.
	 * @param internalClassName internal name of the class.
	 * @return Decompiled class text.
	 * @throws Exception
	 */
	public String decompile(String basePath, String classPath) throws Exception {
		// Load preferences
		IPreferenceStore store = JavaDecompilerPlugin.getDefault().getPreferenceStore();

		boolean realignmentLineNumber = store.getBoolean(JavaDecompilerPlugin.ALIGN);
		boolean unicodeEscape = false; // currently unused :
										// store.getBoolean(JavaDecompilerPlugin.PREF_ESCAPE_UNICODE_CHARACTERS);
		boolean showLineNumbers = store.getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_LINE_NUMBERS);
		// boolean showMetadata =
		// store.getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_METADATA);

		Map<String, Object> configuration = new TreeMap<>();
		configuration.put("realignLineNumbers", realignmentLineNumber);

		if (classPath.endsWith(JAVA_CLASS_SUFFIX)) {
			classPath = classPath.substring(0, classPath.length() - 6);
		}

		Path jarPath = Paths.get(basePath);

		EntriesCache cache = null;
		if (entriesCache != null && entriesCache.isForTheSameFile(jarPath)) {
			// The saved cache is for the same file and the file has not changed
			// => we can just re-use it
			cache = entriesCache;
		}

		try (JDCoreZipLoader loader = new JDCoreZipLoader(jarPath, cache)) {
			JDCorePrinter printer = new JDCorePrinter(unicodeEscape, showLineNumbers);

			ClassFileToJavaSourceDecompiler decompiler = new ClassFileToJavaSourceDecompiler();
			decompiler.decompile(loader, printer, classPath, configuration);

			// Save the cache so we don't have to re-load the class names
			// in case we decompile another class from the same JAR file
			entriesCache = loader.getEntriesCache();

			return printer.toString();
		}
	}

	/**
	 * @return version of JD-Core
	 * @since JD-Core 1.2.8
	 */
	public static String getVersion() {
		return "1.2.8";
	}
}
