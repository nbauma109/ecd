/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.jd.decompiler;

import java.util.Collections;
import java.util.List;

import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.editor.IDecompiler;
import org.sf.feeling.decompiler.jd.JDCoreDecompilerPlugin;
import org.sf.feeling.decompiler.util.UIUtil;

import jd.core.ClassUtil;
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
     * @deprecated
     */
    @Override
    @Deprecated(since = "3.4.4", forRemoval = true)
    public void decompile(String root, String classPackage, String className) {
        throw new UnsupportedOperationException();
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
            if (JavaDecompilerPlugin.getDefault().isDebugMode() || UIUtil.isDebugPerspective()) {
                displayNumber = JavaDecompilerPlugin.getDefault().isDisplayLineNumber();
                JavaDecompilerPlugin.getDefault().displayLineNumber(Boolean.TRUE);
            }

            StringBuilder decompileClassName = new StringBuilder();
            if (packege != null && !packege.isEmpty()) {
                decompileClassName.append(packege);
                decompileClassName.append('/');
            }
            decompileClassName.append(ClassUtil.getInternalName(className));

            source = mapper.decompile(archivePath, decompileClassName.toString());
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