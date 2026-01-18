/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;

public class ClassUtil {

    private ClassUtil() {
    }

    public static boolean isDebug() {
        return JavaDecompilerPlugin.getDefault().isDebug()
                || UIUtil.isDebugPerspective();
    }

    public static boolean isClassFile(byte[] classData) {
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(classData))) {
            if (0xCAFEBABE != data.readInt()) {
                return false;
            }
            data.readUnsignedShort();
            data.readUnsignedShort();
            return true;
        } catch (IOException e) {
            Logger.error("Class file test failed", e);
        }
        return false;
    }

}
