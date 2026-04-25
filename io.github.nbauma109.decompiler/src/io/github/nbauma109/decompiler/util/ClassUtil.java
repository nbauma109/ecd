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
        return topLevelByName != null && topLevelByName.exists() ? topLevelByName : classFile;
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

        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(classFile.getBytes()))) {
            ClassFileConstants constants = readClassFileConstants(data);
            if (constants == null) {
                return false;
            }

            String currentClass = readTypeDeclaration(data, constants);
            skipMembers(data);
            skipMembers(data);
            int attributesCount = data.readUnsignedShort();
            for (int i = 0; i < attributesCount; i++) {
                int attributeNameIndex = data.readUnsignedShort();
                String attributeName = constants.utf8(attributeNameIndex);
                int attributeLength = data.readInt();
                if ("EnclosingMethod".equals(attributeName)) { //$NON-NLS-1$
                    String enclosingClass = constants.className(data.readUnsignedShort());
                    data.readUnsignedShort();
                    if (isEnclosedByTopLevel(enclosingClass, topLevelInternalName)) {
                        return true;
                    }
                } else if ("InnerClasses".equals(attributeName)) { //$NON-NLS-1$
                    if (hasTopLevelInnerClassEntry(data, constants, currentClass, topLevelInternalName)) {
                        return true;
                    }
                } else {
                    skipFully(data, attributeLength);
                }
            }
        } catch (IOException | JavaModelException e) {
            Logger.debug(e);
        }
        return false;
    }

    private static ClassFileConstants readClassFileConstants(DataInputStream data) throws IOException {
        if (0xCAFEBABE != data.readInt()) {
            return null;
        }
        data.readUnsignedShort();
        data.readUnsignedShort();
        int constantPoolCount = data.readUnsignedShort();
        String[] utf8Entries = new String[constantPoolCount];
        int[] classNameIndexes = new int[constantPoolCount];
        for (int i = 1; i < constantPoolCount; i++) {
            int tag = data.readUnsignedByte();
            switch (tag) {
            case 1:
                utf8Entries[i] = data.readUTF();
                break;
            case 7:
                classNameIndexes[i] = data.readUnsignedShort();
                break;
            case 8:
            case 16:
            case 19:
            case 20:
                data.readUnsignedShort();
                break;
            case 3:
            case 4:
            case 9:
            case 10:
            case 11:
            case 12:
            case 17:
            case 18:
                data.readInt();
                break;
            case 5:
            case 6:
                data.readLong();
                i++;
                break;
            case 15:
                data.readUnsignedByte();
                data.readUnsignedShort();
                break;
            default:
                return null;
            }
        }
        return new ClassFileConstants(utf8Entries, classNameIndexes);
    }

    private static String readTypeDeclaration(DataInputStream data, ClassFileConstants constants) throws IOException {
        data.readUnsignedShort();
        String currentClass = constants.className(data.readUnsignedShort());
        data.readUnsignedShort();
        int interfaceCount = data.readUnsignedShort();
        for (int i = 0; i < interfaceCount; i++) {
            data.readUnsignedShort();
        }
        return currentClass;
    }

    private static void skipMembers(DataInputStream data) throws IOException {
        int membersCount = data.readUnsignedShort();
        for (int i = 0; i < membersCount; i++) {
            data.readUnsignedShort();
            data.readUnsignedShort();
            data.readUnsignedShort();
            int attributesCount = data.readUnsignedShort();
            for (int j = 0; j < attributesCount; j++) {
                data.readUnsignedShort();
                skipFully(data, data.readInt());
            }
        }
    }

    private static boolean hasTopLevelInnerClassEntry(DataInputStream data, ClassFileConstants constants,
            String currentClass, String topLevelInternalName) throws IOException {
        int numberOfClasses = data.readUnsignedShort();
        for (int i = 0; i < numberOfClasses; i++) {
            String innerClass = constants.className(data.readUnsignedShort());
            String outerClass = constants.className(data.readUnsignedShort());
            data.readUnsignedShort();
            data.readUnsignedShort();
            if (currentClass != null && currentClass.equals(innerClass)
                    && isEnclosedByTopLevel(outerClass, topLevelInternalName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEnclosedByTopLevel(String enclosingClass, String topLevelInternalName) {
        return enclosingClass != null
                && topLevelInternalName.equals(stripNestedInternalName(enclosingClass));
    }

    private static String stripNestedInternalName(String internalName) {
        int nestedSeparator = internalName.indexOf(NESTED_CLASS_SEPARATOR);
        return nestedSeparator < 0 ? internalName : internalName.substring(0, nestedSeparator);
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

    private static void skipFully(DataInputStream data, int length) throws IOException {
        int skipped = 0;
        while (skipped < length) {
            int count = data.skipBytes(length - skipped);
            if (count <= 0) {
                data.readUnsignedByte();
                count = 1;
            }
            skipped += count;
        }
    }

    private static String stripClassExtension(String name) {
        if (name.endsWith(CLASS_FILE_EXTENSION)) {
            return name.substring(0, name.length() - CLASS_FILE_EXTENSION.length());
        }
        return name;
    }

    private static class ClassFileConstants {

        private final String[] utf8Entries;
        private final int[] classNameIndexes;

        private ClassFileConstants(String[] utf8Entries, int[] classNameIndexes) {
            this.utf8Entries = utf8Entries;
            this.classNameIndexes = classNameIndexes;
        }

        private String utf8(int index) {
            return index > 0 && index < utf8Entries.length ? utf8Entries[index] : null;
        }

        private String className(int index) {
            if (index <= 0 || index >= classNameIndexes.length) {
                return null;
            }
            return utf8(classNameIndexes[index]);
        }

    }

}
