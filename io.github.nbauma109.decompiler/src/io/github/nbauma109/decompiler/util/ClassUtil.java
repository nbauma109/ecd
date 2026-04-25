/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;

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

    public static IClassFile getTopLevelClassFile(IClassFile classFile) {
        if (classFile == null) {
            return null;
        }

        IClassFile topLevel = getTopLevelClassFile(classFile.getType());
        if (topLevel != null && topLevel.exists() && !topLevel.equals(classFile)) {
            return topLevel;
        }

        IClassFile topLevelByName = getTopLevelClassFileByName(classFile);
        if (topLevelByName != null && topLevelByName.exists()) {
            return topLevelByName;
        }

        return topLevel != null && topLevel.exists() ? topLevel : classFile;
    }

    public static IClassFile getTopLevelClassFile(IType type) {
        if (type == null) {
            return null;
        }

        IType topLevelType = type;
        IType declaringType = topLevelType.getDeclaringType();
        while (declaringType != null) {
            topLevelType = declaringType;
            declaringType = topLevelType.getDeclaringType();
        }

        IClassFile classFile = topLevelType.getClassFile();
        return classFile != null && classFile.exists() ? classFile : null;
    }

    private static IClassFile getTopLevelClassFileByName(IClassFile classFile) {
        String elementName = classFile.getElementName();
        int nestedSeparator = elementName.indexOf('$');
        if (nestedSeparator < 0 || !(classFile.getParent() instanceof IPackageFragment pkg)) {
            return classFile;
        }

        IClassFile topLevel = pkg.getClassFile(elementName.substring(0, nestedSeparator) + ".class"); //$NON-NLS-1$
        return topLevel != null && topLevel.exists() ? topLevel : classFile;
    }

}
