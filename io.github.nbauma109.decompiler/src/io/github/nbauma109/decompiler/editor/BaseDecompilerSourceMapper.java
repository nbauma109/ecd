/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.editor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.compiler.env.IBinaryType;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.core.ClassFile;
import org.eclipse.jdt.internal.core.ExternalPackageFragmentRoot;
import org.eclipse.jdt.internal.core.PackageFragmentRoot;
import org.eclipse.jdt.internal.core.SourceMapper;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jface.preference.IPreferenceStore;
import org.jd.core.v1.parser.ParseException;
import org.jd.core.v1.util.ParserRealigner;
import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.util.DecompileUtil;
import io.github.nbauma109.decompiler.util.Logger;
import io.github.nbauma109.decompiler.util.ReflectionUtils;
import io.github.nbauma109.decompiler.util.SortMemberUtil;
import io.github.nbauma109.decompiler.util.SourceMapperUtil;
import io.github.nbauma109.decompiler.util.UIUtil;
import io.github.nbauma109.decompiler.util.UnicodeUtil;

import com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers;
import com.heliosdecompiler.transformerapi.decompilers.Decompiler;

import jd.core.DecompilationResult;

public class BaseDecompilerSourceMapper extends DecompilerSourceMapper {

    protected Decompiler<?> currentDecompiler;
    private String classLocation;

    private static Map<String, String> compilerOptions = new HashMap<>();
    static {
        compilerOptions = new CompilerOptions().getMap();
        compilerOptions.put(CompilerOptions.OPTION_Compliance, JavaCore.latestSupportedJavaVersion());
        compilerOptions.put(CompilerOptions.OPTION_Source, JavaCore.latestSupportedJavaVersion());
    }

    public BaseDecompilerSourceMapper(String decompilerType) {
        this(new Path("."), ".");
        currentDecompiler = Decompilers.valueOf(decompilerType);
    }

    protected BaseDecompilerSourceMapper(IPath sourcePath, String rootPath) {
        this(sourcePath, rootPath, compilerOptions);
    }

    protected BaseDecompilerSourceMapper(IPath sourcePath, String rootPath, Map<String, String> options) {
        super(sourcePath, rootPath, options);
    }

    @Override
    public char[] findSource(IType type, IBinaryType info) {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        boolean always = prefs.getBoolean(JavaDecompilerPlugin.IGNORE_EXISTING);

        Collection<Exception> exceptions = new LinkedList<>();
        IPackageFragment pkgFrag = type.getPackageFragment();
        IPackageFragmentRoot root = (IPackageFragmentRoot) pkgFrag.getParent();

        JavaDecompilerPlugin.getDefault().syncLibrarySource(root);
        char[] attachedSource = null;

        if (UIUtil.requestFromJavadocHover() && !fromInput(type) && always) {
            sourceRanges.remove(type);
            return originalSourceMapper.get(root).findSource(type, info);
        }

        if (originalSourceMapper.containsKey(root)) {
            attachedSource = originalSourceMapper.get(root).findSource(type, info);

            if (attachedSource != null && !always) {
                updateSourceRanges(type, attachedSource);
                isAttachedSource = true;
                mapSourceSwitch(type, attachedSource, true);
                SourceMapperUtil.mapSource(((PackageFragmentRoot) root).getSourceMapper(), type, attachedSource, info);
                return attachedSource;
            }
        }

        if (info == null) {
            if (always) {
                return null;
            }
            return attachedSource;
        }

        try {
            if (root instanceof PackageFragmentRoot pfr) {

                SourceMapper sourceMapper = pfr.getSourceMapper();

                if (!originalSourceMapper.containsKey(root)) {
                    ReflectionUtils.setFieldValue(this, "options", //$NON-NLS-1$
                            ReflectionUtils.getFieldValue(sourceMapper, "options")); //$NON-NLS-1$
                    originalSourceMapper.put(root, sourceMapper);
                }

                if (sourceMapper != null && !always && !(sourceMapper instanceof DecompilerSourceMapper)) {
                    attachedSource = sourceMapper.findSource(type, info);
                    if (attachedSource != null) {
                        updateSourceRanges(type, attachedSource);
                        isAttachedSource = true;
                        mapSourceSwitch(type, attachedSource, true);
                        SourceMapperUtil.mapSource(((PackageFragmentRoot) root).getSourceMapper(), type, attachedSource,
                                info);
                        return attachedSource;
                    }
                }

                if (sourceMapper != this) {
                    pfr.setSourceMapper(this);
                }
            }
        } catch (JavaModelException e) {
            JavaDecompilerPlugin.logError(e, "Could not set source mapper."); //$NON-NLS-1$
        }

        isAttachedSource = false;

        if (JavaDecompilerPlugin.getDefault().isAutoAttachSource()) {
            boolean waitForSources = prefs.getBoolean(JavaDecompilerPlugin.WAIT_FOR_SOURCES);
            Thread attachSourceThread = JavaDecompilerPlugin.getDefault().attachSource(root, false);
            if (!always && waitForSources && attachSourceThread != null && root instanceof PackageFragmentRoot) {
                try {
                    long t0 = System.nanoTime();
                    attachSourceThread.join(10000);
                    long t1 = System.nanoTime();
                    Logger.warn("Source attach took " + TimeUnit.NANOSECONDS.toMillis(t1 - t0) + " millis");
                    PackageFragmentRoot pfr = (PackageFragmentRoot) root;
                    SourceMapper sourceMapper = pfr.getSourceMapper();
                    if (sourceMapper != null && !(sourceMapper instanceof DecompilerSourceMapper)) {
                        attachedSource = sourceMapper.findSource(type, info);
                        if (attachedSource != null) {
                            updateSourceRanges(type, attachedSource);
                            isAttachedSource = true;
                            mapSourceSwitch(type, attachedSource, true);
                            SourceMapperUtil.mapSource(((PackageFragmentRoot) root).getSourceMapper(), type,
                                    attachedSource, info);
                            return attachedSource;
                        }
                    }
                } catch (InterruptedException e) {
                    Logger.error(e);
                    // Restore interrupted state...
                    Thread.currentThread().interrupt();
                }
            }
        }

        String className = new String(info.getName());
        String fullName = new String(info.getFileName());
        int classNameIndex = fullName.lastIndexOf(className);
        if (classNameIndex < 0) {
            JavaDecompilerPlugin.logError(null,
                    "Unable to find className \"" + className + "\" in fullName \"" + fullName + "\""); //$NON-NLS-1$
            return null;
        }
        className = fullName.substring(classNameIndex);

        int index = className.lastIndexOf('/');
        className = className.substring(index + 1);

        classLocation = ""; //$NON-NLS-1$

        DecompilationResult res = decompile(type, exceptions, root, className);

        if (res == null || res.getDecompiledOutput() == null || res.getDecompiledOutput().isEmpty()) {
            return null;
        }

        String code = UnicodeUtil.decode(res.getDecompiledOutput());

        boolean showReport = prefs.getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_METADATA);

        boolean showLineNumber = prefs.getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_LINE_NUMBERS);
        boolean align = prefs.getBoolean(JavaDecompilerPlugin.ALIGN);
        if ((showLineNumber && align)
                || JavaDecompilerPlugin.getDefault().isDebug()
                || UIUtil.isDebugPerspective()) {
            try {
                code = new ParserRealigner().realign(code);
            } catch (ParseException e) {
                exceptions.add(e);
            }
        }

        StringBuilder source = new StringBuilder();

        if (!JavaDecompilerPlugin.getDefault().isDebug() && !UIUtil.isDebugPerspective()) {
            boolean useSorter = prefs.getBoolean(JavaDecompilerPlugin.USE_ECLIPSE_SORTER);
            if (useSorter) {
                className = new String(info.getName());
                fullName = new String(info.getFileName());
                if (fullName.lastIndexOf(className) != -1) {
                    className = fullName.substring(fullName.lastIndexOf(className));
                }

                code = SortMemberUtil.sortMember(type.getPackageFragment().getElementName(), className, code);
            }

            source.append(formatSource(code));

        } else {
            source.append(code);
        }

        if (showReport) {
            printDecompileReport(source, classLocation, exceptions);
        }

        char[] sourceAsCharArray = source.toString().toCharArray();
        if (originalSourceMapper.containsKey(root)) {
            SourceMapper rootSourceMapper = originalSourceMapper.get(root);
            if (rootSourceMapper.findSource(type, info) == null) {
                SourceMapperUtil.mapSource(rootSourceMapper, type, sourceAsCharArray, info);
            }
        }

        updateSourceRanges(type, sourceAsCharArray);
        return sourceAsCharArray;
    }

    private void updateSourceRanges(IType type, char[] attachedSource) {
        if (type.getParent() instanceof ClassFile cf) {
            try {
                DecompileUtil.updateSourceRanges(cf, new String(attachedSource));
            } catch (JavaModelException e) {
                Logger.debug(e);
            }
        }
    }

    private boolean fromInput(IType type) {
        JavaDecompilerClassFileEditor editor = UIUtil.getActiveEditor();
        if (editor != null && editor.getEditorInput() instanceof IClassFileEditorInput in) {
            IClassFile input = in.getClassFile();
            IType inputType = (IType) ReflectionUtils.invokeMethod(input, "getOuterMostEnclosingType", //$NON-NLS-1$
                    new Class[0], new Object[0]);
            return type.equals(inputType);
        }
        return false;
    }

    private DecompilationResult decompile(IType type, Collection<Exception> exceptions,
            IPackageFragmentRoot root, String className) {

        DecompilationResult result = null;

        String pkg = type.getPackageFragment().getElementName().replace('.', '/');

        Boolean displayNumber = null;
        if (JavaDecompilerPlugin.getDefault().isDebug() || UIUtil.isDebugPerspective()) {
            displayNumber = JavaDecompilerPlugin.getDefault().isDisplayLineNumber();
            JavaDecompilerPlugin.getDefault().displayLineNumber(Boolean.TRUE);
        }

        try {
            if (root.isArchive()) {
                String archivePath = getArchivePath(root);
                classLocation += archivePath;

                result = currentDecompiler.decompileFromArchive(archivePath, pkg, className);
            } else {
                String rootLocation = null;
                try {
                    if (root.getUnderlyingResource() != null) {
                        rootLocation = root.getUnderlyingResource().getLocation().toOSString();
                    } else if (root instanceof ExternalPackageFragmentRoot externalPackageFragmentRoot) {
                        rootLocation = externalPackageFragmentRoot.getPath().toOSString();
                    } else {
                        rootLocation = root.getPath().toOSString();
                    }
                    classLocation += rootLocation + "/" //$NON-NLS-1$
                            + pkg + "/" //$NON-NLS-1$
                            + className;

                    result = currentDecompiler.decompile(rootLocation, pkg, className);
                } catch (JavaModelException e) {
                    exceptions.add(e);
                }
            }
        } catch (Exception e) {
            exceptions.add(e);
        }

        if (displayNumber != null) {
            JavaDecompilerPlugin.getDefault().displayLineNumber(displayNumber);
        }
        return result;
    }

    @Override
    public String decompile(String decompilerType, File file) {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();

        Boolean displayNumber = null;
        if (JavaDecompilerPlugin.getDefault().isDebug() || UIUtil.isDebugPerspective()) {
            displayNumber = JavaDecompilerPlugin.getDefault().isDisplayLineNumber();
            JavaDecompilerPlugin.getDefault().displayLineNumber(Boolean.TRUE);
        }

        List<Exception> exceptions = new LinkedList<>();
        DecompilationResult decompilationResult = null;
        try {
            decompilationResult = currentDecompiler.decompile(file.getParentFile().getAbsolutePath(), "", //$NON-NLS-1$
                    file.getName());

            if (displayNumber != null) {
                JavaDecompilerPlugin.getDefault().displayLineNumber(displayNumber);
            }

            if (decompilationResult.getDecompiledOutput() == null || decompilationResult.getDecompiledOutput().isEmpty()) {
                return null;
            }
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | IOException e) {
            exceptions.add(e);
        }

        String code = decompilationResult == null ? "" : UnicodeUtil.decode(decompilationResult.getDecompiledOutput());

        boolean showReport = prefs.getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_METADATA);

        boolean showLineNumber = prefs.getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_LINE_NUMBERS);
        boolean align = prefs.getBoolean(JavaDecompilerPlugin.ALIGN);
        if ((showLineNumber && align)
                || JavaDecompilerPlugin.getDefault().isDebug()
                || UIUtil.isDebugPerspective()) {
            try {
                code = new ParserRealigner().realign(code);
            } catch (ParseException e) {
                exceptions.add(e);
            }
        }

        StringBuilder source = new StringBuilder();

        if (!JavaDecompilerPlugin.getDefault().isDebug() && !UIUtil.isDebugPerspective()) {
            source.append(formatSource(code));
        } else {
            source.append(code);
        }

        if (showReport) {
            printDecompileReport(source, file.getAbsolutePath(), exceptions);
        }

        return source.toString();
    }

    protected void logExceptions(Collection<Exception> exceptions, StringBuilder buffer) {
        if (!exceptions.isEmpty()) {
            buffer.append("\n\tCaught exceptions:"); //$NON-NLS-1$
            if (exceptions.isEmpty()) {
                return; // nothing to do
            }
            buffer.append("\n"); //$NON-NLS-1$
            StringWriter stackTraces = new StringWriter();
            try (PrintWriter stackTracesP = new PrintWriter(stackTraces)) {

                Iterator<Exception> i = exceptions.iterator();
                while (i.hasNext()) {
                    i.next().printStackTrace(stackTracesP);
                    stackTracesP.println(""); //$NON-NLS-1$
                }
                stackTracesP.flush();
            }
            buffer.append(stackTraces.toString());
        }
    }

    protected void printDecompileReport(StringBuilder source, String fileLocation, Collection<Exception> exceptions) {
        source.append("\n\n/*"); //$NON-NLS-1$
        source.append("\n\tDECOMPILATION REPORT\n"); //$NON-NLS-1$

        source.append("\n\tDecompiled from: "); //$NON-NLS-1$
        source.append(fileLocation);
        source.append("\n\tTotal time: "); //$NON-NLS-1$
        source.append(currentDecompiler.getDecompilationTime());
        source.append(" ms\n\t"); //$NON-NLS-1$
        logExceptions(exceptions, source);
        String decompiler = currentDecompiler.getName();
        String ver = currentDecompiler.getDecompilerVersion();
        if (decompiler != null) {
            source.append("\n\tDecompiled with "); //$NON-NLS-1$
            source.append(decompiler);
            if (ver != null) {
                source.append(" version "); //$NON-NLS-1$
                source.append(ver);
            }
            source.append(".\n"); //$NON-NLS-1$
        }
        source.append("*/"); //$NON-NLS-1$
    }
}
