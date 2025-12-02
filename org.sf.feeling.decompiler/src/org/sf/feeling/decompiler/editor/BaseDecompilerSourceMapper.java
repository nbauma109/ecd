/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.editor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IPath;
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
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.fernflower.FernFlowerDecompiler;
import org.sf.feeling.decompiler.util.ClassUtil;
import org.sf.feeling.decompiler.util.DecompileUtil;
import org.sf.feeling.decompiler.util.DecompilerOutputUtil;
import org.sf.feeling.decompiler.util.Logger;
import org.sf.feeling.decompiler.util.ReflectionUtils;
import org.sf.feeling.decompiler.util.SortMemberUtil;
import org.sf.feeling.decompiler.util.SourceMapperUtil;
import org.sf.feeling.decompiler.util.UIUtil;

public abstract class BaseDecompilerSourceMapper extends DecompilerSourceMapper {

    protected IDecompiler originalDecompiler;
    private String classLocation;

    private static Map<String, String> compilerOptions = new HashMap<>();
    static {
        compilerOptions = new CompilerOptions().getMap();
        compilerOptions.put(CompilerOptions.OPTION_Compliance, JavaCore.latestSupportedJavaVersion());
        compilerOptions.put(CompilerOptions.OPTION_Source, JavaCore.latestSupportedJavaVersion());
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
            attachedSource = originalSourceMapper.get(root).findSource(type, info);
            return attachedSource;
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
            if (always)
                return null;
            return attachedSource;
        }

        try {
            if (root instanceof PackageFragmentRoot) {
                PackageFragmentRoot pfr = (PackageFragmentRoot) root;

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

        IDecompiler usedDecompiler = decompile(null, type, exceptions, root, className);

        if (usedDecompiler == null || usedDecompiler.getSource() == null || usedDecompiler.getSource().length() == 0) {
            if (usedDecompiler == null || !DecompilerType.FernFlower.equals(usedDecompiler.getDecompilerType())) {
                usedDecompiler = decompile(new FernFlowerDecompiler(), type, exceptions, root, className);
                if (usedDecompiler == null || usedDecompiler.getSource() == null
                        || usedDecompiler.getSource().length() == 0) {
                    return null;
                }
            }
        }

        String code = usedDecompiler.getSource();

        boolean showReport = prefs.getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_METADATA);
        if (!showReport) {
            code = usedDecompiler.removeComment(code);
        }

        boolean showLineNumber = prefs.getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_LINE_NUMBERS);
        boolean align = prefs.getBoolean(JavaDecompilerPlugin.ALIGN);
        if ((showLineNumber && align) || JavaDecompilerPlugin.getDefault().isDebugMode()
                || UIUtil.isDebugPerspective()) {
            if (showReport)
                code = usedDecompiler.removeComment(code);
            DecompilerOutputUtil decompilerOutputUtil = new DecompilerOutputUtil(code);
            code = decompilerOutputUtil.realign();
        }

        StringBuffer source = new StringBuffer();

        if (!(JavaDecompilerPlugin.getDefault().isDebugMode() || UIUtil.isDebugPerspective())) {
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

            if (showReport) {
                printDecompileReport(source, classLocation, exceptions, usedDecompiler.getDecompilationTime());
            }
        } else {
            source.append(code);
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
        if (type.getParent() instanceof ClassFile) {
            try {
                DecompileUtil.updateSourceRanges(((ClassFile) type.getParent()), new String(attachedSource));
            } catch (JavaModelException e) {
                Logger.debug(e);
            }
        }
    }

    private boolean fromInput(IType type) {
        JavaDecompilerClassFileEditor editor = UIUtil.getActiveEditor();
        if (editor != null && editor.getEditorInput() instanceof IClassFileEditorInput) {
            IClassFile input = ((IClassFileEditorInput) editor.getEditorInput()).getClassFile();
            IType inputType = (IType) ReflectionUtils.invokeMethod(input, "getOuterMostEnclosingType", //$NON-NLS-1$
                    new Class[0], new Object[0]);
            return type.equals(inputType);
        }
        return false;
    }

    private IDecompiler decompile(IDecompiler decompiler, IType type, Collection<Exception> exceptions,
            IPackageFragmentRoot root, String className) {
        IDecompiler result = decompiler;

        String pkg = type.getPackageFragment().getElementName().replace('.', '/');

        Boolean displayNumber = null;
        if (JavaDecompilerPlugin.getDefault().isDebugMode() || UIUtil.isDebugPerspective()) {
            displayNumber = JavaDecompilerPlugin.getDefault().isDisplayLineNumber();
            JavaDecompilerPlugin.getDefault().displayLineNumber(Boolean.TRUE);
        }

        try {
            if (root.isArchive()) {
                String archivePath = getArchivePath(root);
                classLocation += archivePath;

                if (result == null) {
                    try {
                        result = ClassUtil.checkAvailableDecompiler(originalDecompiler,
                                new ByteArrayInputStream(type.getClassFile().getBytes()));
                    } catch (JavaModelException e) {
                        result = originalDecompiler;
                    }
                }
                result.decompileFromArchive(archivePath, pkg, className);
            } else {
                String rootLocation = null;
                try {
                    if (root.getUnderlyingResource() != null) {
                        rootLocation = root.getUnderlyingResource().getLocation().toOSString();
                        classLocation += rootLocation + "/" //$NON-NLS-1$
                                + pkg + "/" //$NON-NLS-1$
                                + className;
                    } else if (root instanceof ExternalPackageFragmentRoot) {
                        rootLocation = ((ExternalPackageFragmentRoot) root).getPath().toOSString();
                        classLocation += rootLocation + "/" //$NON-NLS-1$
                                + pkg + "/" //$NON-NLS-1$
                                + className;
                    } else {
                        rootLocation = root.getPath().toOSString();
                        classLocation += rootLocation + "/" //$NON-NLS-1$
                                + pkg + "/" //$NON-NLS-1$
                                + className;
                    }

                    if (result == null) {
                        result = ClassUtil.checkAvailableDecompiler(originalDecompiler, new File(classLocation));
                    }
                    result.decompile(rootLocation, pkg, className);
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
        if (JavaDecompilerPlugin.getDefault().isDebugMode() || UIUtil.isDebugPerspective()) {
            displayNumber = JavaDecompilerPlugin.getDefault().isDisplayLineNumber();
            JavaDecompilerPlugin.getDefault().displayLineNumber(Boolean.TRUE);
        }

        IDecompiler currentDecompiler = ClassUtil.checkAvailableDecompiler(originalDecompiler, file);

        currentDecompiler.decompile(file.getParentFile().getAbsolutePath(), "", //$NON-NLS-1$
                file.getName());

        if (displayNumber != null) {
            JavaDecompilerPlugin.getDefault().displayLineNumber(displayNumber);
        }

        if (currentDecompiler.getSource() == null || currentDecompiler.getSource().length() == 0)
            return null;

        String code = currentDecompiler.getSource();

        boolean showReport = prefs.getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_METADATA);
        if (!showReport) {
            code = currentDecompiler.removeComment(code);
        }

        boolean showLineNumber = prefs.getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_LINE_NUMBERS);
        boolean align = prefs.getBoolean(JavaDecompilerPlugin.ALIGN);
        if ((showLineNumber && align) || JavaDecompilerPlugin.getDefault().isDebugMode()
                || UIUtil.isDebugPerspective()) {
            if (showReport)
                code = currentDecompiler.removeComment(code);
            DecompilerOutputUtil decompilerOutputUtil = new DecompilerOutputUtil(code);
            code = decompilerOutputUtil.realign();
        }

        StringBuffer source = new StringBuffer();

        if (!(JavaDecompilerPlugin.getDefault().isDebugMode() || UIUtil.isDebugPerspective())) {
            source.append(formatSource(code));

            if (showReport) {
                Collection<Exception> exceptions = new LinkedList<>();
                exceptions.addAll(currentDecompiler.getExceptions());
                printDecompileReport(source, file.getAbsolutePath(), exceptions,
                        currentDecompiler.getDecompilationTime());
            }
        } else {
            source.append(code);
        }

        return source.toString();
    }

    protected void logExceptions(Collection<Exception> exceptions, StringBuffer buffer) {
        if (!exceptions.isEmpty()) {
            buffer.append("\n\tCaught exceptions:"); //$NON-NLS-1$
            if (exceptions.isEmpty())
                return; // nothing to do
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

    protected abstract void printDecompileReport(StringBuffer source, String location, Collection<Exception> exceptions,
            long decompilationTime);
}
