/*******************************************************************************
 * Copyright (c) 2026 @nbauma109.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.debug;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.BufferManager;
import org.eclipse.jdt.internal.core.ClassFile;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.editor.ClassFileSourceMap;
import io.github.nbauma109.decompiler.editor.DecompilerSourceMapper;
import io.github.nbauma109.decompiler.editor.JavaDecompilerBufferManager;

public final class DecompileBufferPopulator {

    private static final Set<String> DECOMPILED_CLASSFILES = ConcurrentHashMap.newKeySet();

    private DecompileBufferPopulator() {
    }

    public static boolean ensureDecompiled(IClassFile classFile) {
        if (classFile == null || !classFile.exists() || ClassFileResolver.hasRealSource(classFile)) {
            return false;
        }

        String decompilerType = JavaDecompilerPlugin.getDefault()
                .getPreferenceStore()
                .getString(JavaDecompilerPlugin.DECOMPILER_TYPE);

        DecompilerSourceMapper sourceMapper = JavaDecompilerPlugin.getDefault().getSourceMapper(decompilerType);
        if (sourceMapper == null) {
            return false;
        }

        try {
            char[] source = sourceMapper.findSource(classFile.getType());
            if (source == null) {
                return false;
            }

            JavaDecompilerBufferManager bufferManager = bufferManager();
            IBuffer buffer = BufferManager.createBuffer(classFile);
            buffer.setContents(source);
            bufferManager.addBuffer(buffer);

            sourceMapper.mapSourceSwitch(classFile.getType(), source, true);

            if (classFile instanceof ClassFile cf) {
                ClassFileSourceMap.updateSource(bufferManager, cf, source);
            }

            DECOMPILED_CLASSFILES.add(classFile.getHandleIdentifier());
            return true;
        } catch (JavaModelException | RuntimeException e) {
            JavaDecompilerPlugin.logError(e, "");
            return false;
        }
    }

    public static boolean wasDecompiled(IClassFile classFile) {
        return classFile != null && DECOMPILED_CLASSFILES.contains(classFile.getHandleIdentifier());
    }

    private static JavaDecompilerBufferManager bufferManager() {
        BufferManager defaultManager = BufferManager.getDefaultBufferManager();
        if (defaultManager instanceof JavaDecompilerBufferManager mgr) {
            return mgr;
        }
        return new JavaDecompilerBufferManager(defaultManager);
    }
}
