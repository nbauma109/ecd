/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.editor;

import java.util.Enumeration;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.internal.core.BufferManager;
import io.github.nbauma109.decompiler.util.Logger;

/**
 * This class is a hack that replaces JDT <code>BufferManager</code> in order to
 * make <code>addBuffer()</code> and <code>removeBuffer()</code> accessible.
 */
public class JavaDecompilerBufferManager extends BufferManager {

    public static void closeDecompilerBuffers(boolean all) {
        BufferManager manager = BufferManager.getDefaultBufferManager();
        if (manager instanceof JavaDecompilerBufferManager) {
            Enumeration enumeration = manager.getOpenBuffers();
            while (enumeration.hasMoreElements()) {
                IBuffer buffer = (IBuffer) enumeration.nextElement();
                ((JavaDecompilerBufferManager) manager).removeBuffer(buffer);
            }
        }
    }

    public JavaDecompilerBufferManager(BufferManager manager) {
        synchronized (BufferManager.class) {
            Enumeration enumeration = manager.getOpenBuffers();
            while (enumeration.hasMoreElements()) {
                IBuffer buffer = (IBuffer) enumeration.nextElement();
                addBuffer(buffer);
            }
            BufferManager.DEFAULT_BUFFER_MANAGER = this;
        }
    }

    @Override
    public void addBuffer(final IBuffer buffer) {
        if (buffer == null || buffer.getContents() == null) {
            if (buffer != null) {
                delayAddBuffer(buffer);
            }
            return;
        }
        super.addBuffer(buffer);
    }

    private void delayAddBuffer(final IBuffer buffer) {
        new Thread() {

            @Override
            public void run() {
                if (buffer.getContents() != null) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Logger.debug(e);
                    }
                    addBuffer(buffer);
                }
            }
        }.start();
    }

    @Override
    public void removeBuffer(IBuffer buffer) {
        super.removeBuffer(buffer);
    }
}
