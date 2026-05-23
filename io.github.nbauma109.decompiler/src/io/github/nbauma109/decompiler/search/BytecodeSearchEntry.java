/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

final class BytecodeSearchEntry {

    enum Kind {
        TYPE,
        METHOD,
        CONSTRUCTOR,
        FIELD,
        PACKAGE,
        MODULE
    }

    private final Kind kind;
    private final boolean declaration;
    private final String elementHandle;
    private final IJavaElement anonymousElementFallback;
    private final String name;
    private final String qualifiedName;
    private final String declaringTypeName;
    private final String descriptor;

    BytecodeSearchEntry(Kind kind, boolean declaration, String elementHandle, IJavaElement anonymousElementFallback,
            String name, String qualifiedName, String declaringTypeName, String descriptor) {
        this.kind = kind;
        this.declaration = declaration;
        this.elementHandle = elementHandle;
        this.anonymousElementFallback = anonymousElementFallback;
        this.name = name == null ? "" : name; //$NON-NLS-1$
        this.qualifiedName = qualifiedName == null ? this.name : qualifiedName;
        this.declaringTypeName = declaringTypeName;
        this.descriptor = descriptor;
    }

    BytecodeSearchEntry(Kind kind, boolean declaration, IJavaElement element, String name, String qualifiedName,
            String declaringTypeName, String descriptor) {
        this(kind, declaration, element == null ? null : element.getHandleIdentifier(),
                element == null ? null : anonymousElementFallback(element.getHandleIdentifier(), element), name,
                qualifiedName, declaringTypeName, descriptor);
    }

    private static IJavaElement anonymousElementFallback(String elementHandle, IJavaElement element) {
        return elementHandle != null && elementHandle.contains("[~") ? element : null; //$NON-NLS-1$
    }

    Kind getKind() {
        return kind;
    }

    boolean isDeclaration() {
        return declaration;
    }

    IJavaElement getElement() {
        IJavaElement element = elementHandle == null ? null : JavaCore.create(elementHandle);
        return element == null ? anonymousElementFallback : element;
    }

    String getElementHandle() {
        return elementHandle;
    }

    String getName() {
        return name;
    }

    String getQualifiedName() {
        return qualifiedName;
    }

    String getDeclaringTypeName() {
        return declaringTypeName;
    }

    String getDescriptor() {
        return descriptor;
    }

}
