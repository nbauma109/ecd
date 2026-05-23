/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.search.ui.text.Match;

import io.github.nbauma109.decompiler.util.Logger;

final class BytecodeSearchDebug {

    private static final String PREFIX = "[Application Library Search] "; //$NON-NLS-1$
    private static final int MAX_TEXT_LENGTH = 220;
    private static final int MAX_SNIPPET_LENGTH = 120;

    private BytecodeSearchDebug() {
    }

    static void info(String message) {
        Logger.info(PREFIX + message);
    }

    static String describeEntry(BytecodeSearchEntry entry) {
        if (entry == null) {
            return "<null entry>"; //$NON-NLS-1$
        }
        return "entry[kind=" + entry.getKind() //$NON-NLS-1$
                + ", declaration=" + entry.isDeclaration() //$NON-NLS-1$
                + ", name=" + entry.getName() //$NON-NLS-1$
                + ", qualifiedName=" + entry.getQualifiedName() //$NON-NLS-1$
                + ", declaringType=" + entry.getDeclaringTypeName() //$NON-NLS-1$
                + ", descriptor=" + entry.getDescriptor() //$NON-NLS-1$
                + ", element=" + describeElement(entry.getElement()) + ']'; //$NON-NLS-1$
    }

    static String describeMatch(Match match) {
        if (match == null) {
            return "<null match>"; //$NON-NLS-1$
        }
        return "match[class=" + match.getClass().getName() //$NON-NLS-1$
                + ", offset=" + match.getOffset() //$NON-NLS-1$
                + ", length=" + match.getLength() //$NON-NLS-1$
                + ", element=" + describeElement(match.getElement()) + ']'; //$NON-NLS-1$
    }

    static String describeElement(Object element) {
        if (element instanceof BytecodeSearchElement bytecodeElement) {
            return "BytecodeSearchElement[" + describeEntry(bytecodeElement.getEntry()) + ']'; //$NON-NLS-1$
        }
        if (!(element instanceof IJavaElement javaElement)) {
            return element == null ? "<null>" : safeText(element.toString()); //$NON-NLS-1$
        }
        return "IJavaElement[type=" + javaElement.getElementType() //$NON-NLS-1$
                + ", name=" + safeText(javaElement.getElementName()) //$NON-NLS-1$
                + ", handle=" + safeText(javaElement.getHandleIdentifier()) + ']'; //$NON-NLS-1$
    }

    static String describeRange(BytecodeSourceRangeResolver.SourceRange range, String source) {
        if (range == null) {
            return "<null range>"; //$NON-NLS-1$
        }
        return "range[offset=" + range.offset() //$NON-NLS-1$
                + ", length=" + range.length() //$NON-NLS-1$
                + ", snippet=" + snippet(source, range) + ']'; //$NON-NLS-1$
    }

    static String snippet(String source, BytecodeSourceRangeResolver.SourceRange range) {
        if (source == null || range == null || source.isEmpty()) {
            return "<no source>"; //$NON-NLS-1$
        }
        int start = Math.max(0, Math.min(source.length(), range.offset()));
        int end = Math.max(start, Math.min(source.length(), start + range.length()));
        return quote(source.substring(start, end), MAX_SNIPPET_LENGTH);
    }

    static String safeText(String text) {
        return quote(text, MAX_TEXT_LENGTH);
    }

    private static String quote(String text, int maxLength) {
        if (text == null) {
            return "<null>"; //$NON-NLS-1$
        }
        String normalized = text.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength) + "..."; //$NON-NLS-1$
        }
        return '"' + normalized + '"';
    }
}
