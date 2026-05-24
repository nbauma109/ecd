/*******************************************************************************
 * © 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

public class JavaSourceMemberParserTest {

    // -------------------------------------------------------------------------
    // findMatchingClose
    // -------------------------------------------------------------------------

    private static final String CLASS_FOO_VOID_BAR = "class Foo { void bar() { } }";

	@Test
    public void findMatchingCloseEmptyParens() {
        assertEquals(4, JavaSourceMemberParser.findMatchingClose("foo()", 3, '(', ')'));
    }

    @Test
    public void findMatchingCloseParensWithArgs() {
        assertEquals(8, JavaSourceMemberParser.findMatchingClose("foo(a, b)", 3, '(', ')'));
    }

    @Test
    public void findMatchingCloseNestedParens() {
        assertEquals(10, JavaSourceMemberParser.findMatchingClose("foo(bar(x))", 3, '(', ')'));
    }

    @Test
    public void findMatchingCloseSimpleBraces() {
        assertEquals(9, JavaSourceMemberParser.findMatchingClose("{ int x; }", 0, '{', '}'));
    }

    @Test
    public void findMatchingCloseNestedBraces() {
        assertEquals(15, JavaSourceMemberParser.findMatchingClose("{ void m() { } }", 0, '{', '}'));
    }

    @Test
    public void findMatchingCloseIgnoresBracesInsideLiteralsAndComments() {
        assertEquals(66, JavaSourceMemberParser.findMatchingClose(
                "{ String json = \"{\"; char close = '}'; /* { */ // }\n void m() { } }", 0, '{', '}')); //$NON-NLS-1$
    }

    @Test
    public void findMatchingCloseNestedBrackets() {
        assertEquals(6, JavaSourceMemberParser.findMatchingClose("a[b[0]]", 1, '[', ']'));
    }

    @Test
    public void findMatchingCloseReturnsMinusOneWhenUnmatched() {
        assertEquals(-1, JavaSourceMemberParser.findMatchingClose("foo(bar", 3, '(', ')'));
    }

    @Test
    public void findMatchingCloseReturnsMinusOneWhenOffsetPastEnd() {
        assertEquals(-1, JavaSourceMemberParser.findMatchingClose("()", 5, '(', ')'));
    }

    // -------------------------------------------------------------------------
    // skipWhitespace
    // -------------------------------------------------------------------------

    @Test
    public void skipWhitespaceAtNonWhitespace() {
        assertEquals(0, JavaSourceMemberParser.skipWhitespace("abc", 0));
    }

    @Test
    public void skipWhitespaceLeadingSpaces() {
        assertEquals(3, JavaSourceMemberParser.skipWhitespace("   abc", 0));
    }

    @Test
    public void skipWhitespaceFromMiddle() {
        assertEquals(4, JavaSourceMemberParser.skipWhitespace("ab  cd", 2));
    }

    @Test
    public void skipWhitespaceClampsNegativeOffsetToZero() {
        assertEquals(0, JavaSourceMemberParser.skipWhitespace("abc", -5));
    }

    @Test
    public void skipWhitespaceReturnsLengthForAllWhitespace() {
        String s = "   ";
        assertEquals(s.length(), JavaSourceMemberParser.skipWhitespace(s, 0));
    }

    // -------------------------------------------------------------------------
    // isDirectTypeMember — edge cases not covered by the parameterized group
    // -------------------------------------------------------------------------

    @Test
    public void isDirectTypeMemberReturnsFalseWhenOffsetBeforeOpenBrace() {
        // typeOffset=5 puts the brace search past the offset=2 position
        assertFalse(JavaSourceMemberParser.isDirectTypeMember(CLASS_FOO_VOID_BAR, 5, 2));
    }

    @Test
    public void isDirectTypeMemberReturnsFalseWhenNoBrace() {
        assertFalse(JavaSourceMemberParser.isDirectTypeMember("no braces here", 0, 3));
    }

    @Test
    public void isDirectTypeMemberIgnoresBracesInsideLiteralsAndComments() {
        String source = "class Foo { String json = \"{\"; // }\n /* { */ void direct() { } }"; //$NON-NLS-1$
        int offset = source.indexOf("direct"); //$NON-NLS-1$

        assertTrue(JavaSourceMemberParser.isDirectTypeMember(source, 0, offset));
    }

    // -------------------------------------------------------------------------
    // isMethodDeclaration — test suite and edge cases
    // -------------------------------------------------------------------------

    /**
     * A call-statement and an abstract declaration both produce the token sequence
     * {@code ) ;}, so {@code isMethodDeclaration} must not rely on {@code ';'} alone.
     * When the same method name appears first as a call inside a method body and then
     * as an abstract declaration, only the brace-depth check ({@code isDirectTypeMember})
     * can tell them apart: the call site is at depth&nbsp;2, the declaration at depth&nbsp;1.
     */
    @Test
    public void isMethodDeclarationReturnsFalseForInvocationInsideMethodBody() {
        String s = "class Foo { void bar() { baz(); } abstract void baz(); }";
        int callSiteNameStart = s.indexOf("baz");
        assertFalse(JavaSourceMemberParser.isMethodDeclaration(s, 0, callSiteNameStart, s.indexOf('(', callSiteNameStart)));
    }

    @Test
    public void isMethodDeclarationReturnsTrueForDeclarationAfterSameNamedInvocation() {
        String s = "class Foo { void bar() { baz(); } abstract void baz(); }";
        int declNameStart = s.indexOf("baz", s.indexOf("baz") + 1);
        assertTrue(JavaSourceMemberParser.isMethodDeclaration(s, 0, declNameStart, s.indexOf('(', declNameStart)));
    }

    @Test
    public void isMethodDeclarationReturnsFalseForUnclosedParen() {
        String s = "class Foo { void bar( }";
        int nameStart = s.indexOf("bar");
        assertFalse(JavaSourceMemberParser.isMethodDeclaration(s, 0, nameStart, s.indexOf('(', nameStart)));
    }

    @Test
    public void isMethodDeclarationReturnsFalseForCallInAssignmentContext() {
        // bar(x) is a call inside a method body; the declaration follows it
        String s = "class Foo { void m() { int r = bar(x); } void bar(int x) { } }";
        int callNameStart = s.indexOf("bar");
        assertFalse(JavaSourceMemberParser.isMethodDeclaration(s, 0, callNameStart, s.indexOf('(', callNameStart)));
    }

    // -------------------------------------------------------------------------
    // isDirectTypeMember — parameterized: all follow (source, searchToken, expected)
    // -------------------------------------------------------------------------

    @RunWith(Parameterized.class)
    public static class IsDirectTypeMemberTest {

        record Case(String description, String source, String searchToken, boolean expected) {
            @Override
            public String toString() { return description; }
        }

        @Parameters(name = "{0}")
        public static List<Case> data() {
            return List.of(
                new Case("field is direct member",               "class Foo { int x; }",                        "x",   true),  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new Case("method header is direct member",       CLASS_FOO_VOID_BAR,                "bar", true),  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new Case("call inside method body is not direct","class Foo { void bar() { foo(); } }",         "foo", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new Case("abstract method header is direct",     "abstract class Foo { abstract void baz(); }", "baz", true),  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new Case("second method after first is direct",  "class Foo { void m1() { } void m2() { } }",  "m2",  true),  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new Case("interface method is direct member",    "interface Foo { void run(); }",               "run", true)   //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            );
        }

        private final Case testCase;

        public IsDirectTypeMemberTest(Case testCase) {
            this.testCase = testCase;
        }

        @Test
        public void isDirectTypeMemberMatchesExpected() {
            int offset = testCase.source().indexOf(testCase.searchToken());
            assertEquals(testCase.expected(), JavaSourceMemberParser.isDirectTypeMember(testCase.source(), 0, offset));
        }
    }

    // -------------------------------------------------------------------------
    // isMethodDeclaration — parameterized: positive cases (all return true)
    // -------------------------------------------------------------------------

    @RunWith(Parameterized.class)
    public static class IsMethodDeclarationTest {

        record Case(String description, String source, String methodName) {
            @Override
            public String toString() { return description; }
        }

        @Parameters(name = "{0}")
        public static List<Case> data() {
            return List.of(
                new Case("concrete method",            CLASS_FOO_VOID_BAR,                                  "bar"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new Case("concrete method with params","class Foo { int add(int a, int b) { return a + b; } }",         "add"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new Case("abstract method",            "abstract class Foo { abstract void baz(); }",                   "baz"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new Case("interface method",           "interface Foo { void run(); }",                                  "run"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new Case("concrete with throws",              "class Foo { void bar() throws Exception { } }",                    "bar"),   //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new Case("abstract with throws",             "abstract class Foo { abstract void bar() throws Exception; }",     "bar"),   //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new Case("annotation member without default","@interface Ann { String value(); }",                               "value"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new Case("annotation member with default",   "@interface Ann { String value() default \"x\"; }",                "value")  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            );
        }

        private final Case testCase;

        public IsMethodDeclarationTest(Case testCase) {
            this.testCase = testCase;
        }

        @Test
        public void isMethodDeclarationReturnsTrue() {
            int nameStart = testCase.source().indexOf(testCase.methodName());
            int openParen = testCase.source().indexOf('(', nameStart);
            assertTrue(JavaSourceMemberParser.isMethodDeclaration(testCase.source(), 0, nameStart, openParen));
        }
    }
}
