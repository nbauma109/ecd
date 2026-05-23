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

    enum Access {
        NONE,
        READ,
        WRITE
    }

    private final Kind kind;
    private final boolean declaration;
    private final String elementHandle;
    private final IJavaElement anonymousElementFallback;
    private final String name;
    private final String qualifiedName;
    private final String declaringTypeName;
    private final String descriptor;
    private final Access access;

    BytecodeSearchEntry(Kind kind, boolean declaration, ElementReference elementReference,
            SymbolReference symbolReference) {
        this(kind, declaration, elementReference, symbolReference, Access.NONE);
    }

    BytecodeSearchEntry(Kind kind, boolean declaration, ElementReference elementReference,
            SymbolReference symbolReference, Access access) {
        this.kind = kind;
        this.declaration = declaration;
        this.elementHandle = elementReference.handle();
        this.anonymousElementFallback = elementReference.anonymousFallback();
        this.name = symbolReference.name() == null ? "" : symbolReference.name(); //$NON-NLS-1$
        this.qualifiedName = symbolReference.qualifiedName() == null ? this.name : symbolReference.qualifiedName();
        this.declaringTypeName = symbolReference.declaringTypeName();
        this.descriptor = symbolReference.descriptor();
        this.access = access == null ? Access.NONE : access;
    }

    BytecodeSearchEntry(Kind kind, boolean declaration, IJavaElement element, String name, String qualifiedName,
            String declaringTypeName, String descriptor) {
        this(kind, declaration, elementReference(element),
                new SymbolReference(name, qualifiedName, declaringTypeName, descriptor));
    }

    static ElementReference elementReference(String handle, IJavaElement anonymousFallback) {
        return new ElementReference(handle, anonymousFallback);
    }

    static SymbolReference symbolReference(String name, String qualifiedName, String declaringTypeName,
            String descriptor) {
        return new SymbolReference(name, qualifiedName, declaringTypeName, descriptor);
    }

    private static ElementReference elementReference(IJavaElement element) {
        if (element == null) {
            return new ElementReference(null, null);
        }
        String handle = element.getHandleIdentifier();
        return new ElementReference(handle, anonymousElementFallback(handle, element));
    }

    private static IJavaElement anonymousElementFallback(String elementHandle, IJavaElement element) {
        return elementHandle != null && elementHandle.contains("[~") ? element : null; //$NON-NLS-1$
    }

    record ElementReference(String handle, IJavaElement anonymousFallback) {
    }

    record SymbolReference(String name, String qualifiedName, String declaringTypeName, String descriptor) {
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

    IJavaElement getAnonymousElementFallback() {
        return anonymousElementFallback;
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

    Access getAccess() {
        return access;
    }

}
