/*******************************************************************************
 * © 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.editor;

/**
 * Pure string-scanning helpers used to locate source-level declarations within
 * decompiled Java source text.
 *
 * <p>No Eclipse API is used here, so every method is straightforward to unit-test.</p>
 */
public final class JavaSourceMemberParser {

    private JavaSourceMemberParser() {
        // utility class
    }

    /**
     * Returns the index of the character that closes the opener at {@code openOffset},
     * tracking nesting depth for the given {@code open}/{@code close} pair, or -1 if
     * no matching close is found before the end of the string.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code findMatchingClose(src, i, '(', ')')} — parentheses</li>
     *   <li>{@code findMatchingClose(src, i, '{', '}')} — braces</li>
     *   <li>{@code findMatchingClose(src, i, '[', ']')} — brackets</li>
     * </ul>
     * </p>
     */
    public static int findMatchingClose(String source, int openOffset, char open, char close) {
        int depth = 0;
        for (int i = openOffset; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Returns the first index at or after {@code offset} that is not a whitespace
     * character, clamped to the beginning of the string.
     */
    public static int skipWhitespace(String source, int offset) {
        int current = Math.max(0, offset);
        while (current < source.length() && Character.isWhitespace(source.charAt(current))) {
            current++;
        }
        return current;
    }

    /**
     * Returns {@code true} if {@code offset} is a <em>direct type member</em> of the
     * type whose declaration starts at {@code typeOffset}.
     *
     * <p>A <em>direct type member</em> is any declaration — field, method header, or
     * nested type — that lives at brace-depth&nbsp;1 inside a type body, i.e. directly
     * enclosed by the type's {@code {…}} and <em>not</em> further nested inside a method
     * body, initializer block, lambda, or anonymous class.  For example:</p>
     * <pre>
     *   class Outer {            // typeOffset here
     *       int field;           // direct member (depth 1) → returns true
     *       void method() {
     *           foo();           // NOT a direct member (depth 2) → returns false
     *       }
     *   }
     * </pre>
     *
     * <p>The implementation uses {@link #findMatchingClose} to skip each completed
     * nested block between the type's opening brace and {@code offset}: if any nested
     * block is still open at {@code offset}, the position is inside that block and
     * therefore not a direct type member.</p>
     */
    public static boolean isDirectTypeMember(String source, int typeOffset, int offset) {
        int openBrace = source.indexOf('{', typeOffset);
        if (openBrace < 0 || offset <= openBrace) {
            return false;
        }
        // offset must fall before the type body closes
        int typeClose = findMatchingClose(source, openBrace, '{', '}');
        if (typeClose >= 0 && offset >= typeClose) {
            return false;
        }
        // Skip each completed nested block. If offset falls inside an unclosed block, it
        // is nested (method body, lambda, anonymous class, etc.) — not a direct member.
        int pos = openBrace + 1;
        while (pos < offset) {
            int nextOpen = source.indexOf('{', pos);
            if (nextOpen < 0 || nextOpen >= offset) {
                break;
            }
            int nextClose = findMatchingClose(source, nextOpen, '{', '}');
            if (nextClose < 0 || nextClose >= offset) {
                return false;
            }
            pos = nextClose + 1;
        }
        return true;
    }

    /**
     * Returns {@code true} if the pattern starting at {@code nameStart} represents a
     * method <em>declaration</em> rather than a method <em>invocation</em>.
     *
     * <p>The method looks at the first non-whitespace token after the closing parenthesis
     * of the parameter list and recognises three declaration forms:</p>
     * <ul>
     *   <li>{@code '{'} — the method has a concrete body.</li>
     *   <li>{@code ';'} at a position that is a {@link #isDirectTypeMember direct type member} —
     *       abstract or interface method.  A bare {@code ';'} is ambiguous because a
     *       call-statement ({@code foo();}) also ends with {@code ')' ';'}.  Only
     *       declarations appear at brace-depth&nbsp;1 inside the type body, so
     *       {@link #isDirectTypeMember} is used to rule out call-sites inside method
     *       bodies.</li>
     *   <li>{@code default} keyword — annotation element with a default value
     *       (e.g. {@code String value() default "x";}); verified with
     *       {@link #isDirectTypeMember} for the same reason as {@code ';'}.</li>
     *   <li>{@code throws} clause — the method declares checked exceptions; the clause
     *       must be terminated by either {@code '{'} (concrete body) or {@code ';'}
     *       (abstract/interface).</li>
     * </ul>
     */
    public static boolean isMethodDeclaration(String source, int typeOffset, int nameStart, int openParenOffset) {
        int closeParen = findMatchingClose(source, openParenOffset, '(', ')');
        if (closeParen < 0) {
            return false;
        }
        int nextToken = skipWhitespace(source, closeParen + 1);
        if (nextToken >= source.length()) {
            return false;
        }
        char next = source.charAt(nextToken);
        if (next == '{') {
            return true;
        }
        // A ';' after ')' appears in both abstract/interface declarations and call-statements.
        // Only declarations are at brace-depth 1 (direct type members); calls live inside
        // method bodies at higher depth, so isDirectTypeMember distinguishes them.
        if (next == ';') {
            return isDirectTypeMember(source, typeOffset, nameStart);
        }
        // Annotation element with a default value
        if (source.startsWith("default", nextToken)) { //$NON-NLS-1$
            return isDirectTypeMember(source, typeOffset, nameStart);
        }
        if (!source.startsWith("throws", nextToken)) { //$NON-NLS-1$
            return false;
        }
        int terminatorStart = nextToken + "throws".length(); //$NON-NLS-1$
        int brace = source.indexOf('{', terminatorStart);
        int semicolon = source.indexOf(';', terminatorStart);
        if (brace < 0) {
            return semicolon >= 0;
        }
        return true; // brace found — concrete or throws-declared method is a declaration regardless of ';'
    }
}
