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
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;

public class ClassUtil {

    private static final String CLASS_FILE_EXTENSION = ".class"; //$NON-NLS-1$
    private static final String NESTED_CLASS_SEPARATOR = "$"; //$NON-NLS-1$

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

        if (classFile instanceof IOrdinaryClassFile ordinaryClassFile) {
            IClassFile topLevel = getTopLevelClassFile(ordinaryClassFile.getType());
            if (topLevel != null && topLevel.exists() && !topLevel.equals(classFile)) {
                return topLevel;
            }
        }

        IClassFile topLevelByName = getTopLevelClassFileByName(classFile);
        return topLevelByName.exists() ? topLevelByName : classFile;
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
        int nestedSeparator = elementName.indexOf(NESTED_CLASS_SEPARATOR);
        if (nestedSeparator <= 0 || !(classFile.getParent() instanceof IPackageFragment pkg)) {
            return classFile;
        }

        String nestedName = stripClassExtension(elementName).substring(nestedSeparator + 1);
        if (nestedName.isBlank()) {
            return classFile;
        }

        IClassFile topLevel = pkg.getClassFile(elementName.substring(0, nestedSeparator) + CLASS_FILE_EXTENSION);
        if (topLevel == null || !topLevel.exists()) {
            return classFile;
        }

        if (isDeclaredNestedType(topLevel, nestedName) || isClassFileEnclosedIn(classFile, topLevel)) {
            return topLevel;
        }
        return classFile;
    }

    private static boolean isDeclaredNestedType(IClassFile topLevelClassFile, String nestedName) {
        if (!(topLevelClassFile instanceof IOrdinaryClassFile ordinaryClassFile)) {
            return false;
        }

        IType type = ordinaryClassFile.getType();
        for (String segment : nestedName.split("\\" + NESTED_CLASS_SEPARATOR)) { //$NON-NLS-1$
            if (segment.isBlank()) {
                return false;
            }
            IType child = type.getType(segment);
            if (child == null || !child.exists()) {
                return false;
            }
            type = child;
        }
        return true;
    }

    private static boolean isClassFileEnclosedIn(IClassFile classFile, IClassFile topLevelClassFile) {
        String topLevelInternalName = getInternalName(topLevelClassFile);
        if (topLevelInternalName == null) {
            return false;
        }

        try {
            ClassReader reader = new ClassReader(classFile.getBytes());
            EnclosingClassVisitor visitor = new EnclosingClassVisitor(reader.getClassName(), topLevelInternalName);
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor.isEnclosedInTopLevel();
        } catch (IllegalArgumentException | JavaModelException e) {
            Logger.debug(e);
        }
        return false;
    }

    private static String getInternalName(IClassFile classFile) {
        if (!(classFile.getParent() instanceof IPackageFragment pkg)) {
            return null;
        }
        String typeName = stripClassExtension(classFile.getElementName());
        String packageName = pkg.getElementName();
        if (packageName == null || packageName.isBlank()) {
            return typeName;
        }
        return packageName.replace('.', '/') + "/" + typeName; //$NON-NLS-1$
    }

    private static String stripClassExtension(String name) {
        if (name.endsWith(CLASS_FILE_EXTENSION)) {
            return name.substring(0, name.length() - CLASS_FILE_EXTENSION.length());
        }
        return name;
    }

    private static class EnclosingClassVisitor extends ClassVisitor {

        private final String currentClass;
        private final String topLevelInternalName;
        private boolean enclosedInTopLevel;

        private EnclosingClassVisitor(String currentClass, String topLevelInternalName) {
            super(Opcodes.ASM9);
            this.currentClass = currentClass;
            this.topLevelInternalName = topLevelInternalName;
        }

        @Override
        public void visitOuterClass(String owner, String name, String descriptor) {
            if (isEnclosedByTopLevel(owner, topLevelInternalName)) {
                enclosedInTopLevel = true;
            }
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (currentClass.equals(name) && isEnclosedByTopLevel(outerName, topLevelInternalName)) {
                enclosedInTopLevel = true;
            }
        }

        private boolean isEnclosedInTopLevel() {
            return enclosedInTopLevel;
        }

        private static boolean isEnclosedByTopLevel(String enclosingClass, String topLevelInternalName) {
            return enclosingClass != null
                    && topLevelInternalName.equals(stripNestedInternalName(enclosingClass));
        }

        private static String stripNestedInternalName(String internalName) {
            int nestedSeparator = internalName.indexOf(NESTED_CLASS_SEPARATOR);
            return nestedSeparator < 0 ? internalName : internalName.substring(0, nestedSeparator);
        }
    }

}
