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

import org.junit.Test;

public class JavaSourceMemberParserTest {

    // -------------------------------------------------------------------------
    // findMatchingClose
    // -------------------------------------------------------------------------

    @Test
    public void findMatchingClose_parentheses_emptyParams() {
        String s = "foo()";
        assertEquals(4, JavaSourceMemberParser.findMatchingClose(s, 3, '(', ')'));
    }

    @Test
    public void findMatchingClose_parentheses_withParams() {
        String s = "foo(a, b)";
        assertEquals(8, JavaSourceMemberParser.findMatchingClose(s, 3, '(', ')'));
    }

    @Test
    public void findMatchingClose_parentheses_nested() {
        String s = "foo(bar(x))";
        assertEquals(10, JavaSourceMemberParser.findMatchingClose(s, 3, '(', ')'));
    }

    @Test
    public void findMatchingClose_braces_simple() {
        String s = "{ int x; }";
        assertEquals(9, JavaSourceMemberParser.findMatchingClose(s, 0, '{', '}'));
    }

    @Test
    public void findMatchingClose_braces_nested() {
        String s = "{ void m() { } }";
        assertEquals(15, JavaSourceMemberParser.findMatchingClose(s, 0, '{', '}'));
    }

    @Test
    public void findMatchingClose_brackets() {
        String s = "a[b[0]]";
        assertEquals(6, JavaSourceMemberParser.findMatchingClose(s, 1, '[', ']'));
    }

    @Test
    public void findMatchingClose_noneFound_returnsMinusOne() {
        assertEquals(-1, JavaSourceMemberParser.findMatchingClose("foo(bar", 3, '(', ')'));
    }

    @Test
    public void findMatchingClose_offsetBeyondEnd_returnsMinusOne() {
        assertEquals(-1, JavaSourceMemberParser.findMatchingClose("()", 5, '(', ')'));
    }

    // -------------------------------------------------------------------------
    // skipWhitespace
    // -------------------------------------------------------------------------

    @Test
    public void skipWhitespace_noWhitespace() {
        assertEquals(0, JavaSourceMemberParser.skipWhitespace("abc", 0));
    }

    @Test
    public void skipWhitespace_leadingSpaces() {
        assertEquals(3, JavaSourceMemberParser.skipWhitespace("   abc", 0));
    }

    @Test
    public void skipWhitespace_fromMiddle() {
        assertEquals(4, JavaSourceMemberParser.skipWhitespace("ab  cd", 2));
    }

    @Test
    public void skipWhitespace_negativeOffset_clampsToZero() {
        assertEquals(0, JavaSourceMemberParser.skipWhitespace("abc", -5));
    }

    @Test
    public void skipWhitespace_allWhitespace_returnsLength() {
        String s = "   ";
        assertEquals(s.length(), JavaSourceMemberParser.skipWhitespace(s, 0));
    }

    // -------------------------------------------------------------------------
    // isDirectTypeMember
    // -------------------------------------------------------------------------

    @Test
    public void isDirectTypeMember_field_isDirectMember() {
        // "class Foo { int x; }"
        //              0123456789...
        String s = "class Foo { int x; }";
        int typeOffset = 0;
        int nameStart = s.indexOf('x');
        assertTrue(JavaSourceMemberParser.isDirectTypeMember(s, typeOffset, nameStart));
    }

    @Test
    public void isDirectTypeMember_methodHeader_isDirectMember() {
        String s = "class Foo { void bar() { } }";
        int typeOffset = 0;
        int nameStart = s.indexOf("bar");
        assertTrue(JavaSourceMemberParser.isDirectTypeMember(s, typeOffset, nameStart));
    }

    @Test
    public void isDirectTypeMember_callInsideMethod_isNotDirectMember() {
        // foo() call is inside bar()'s body — depth 2
        String s = "class Foo { void bar() { foo(); } }";
        int typeOffset = 0;
        int nameStart = s.indexOf("foo");
        assertFalse(JavaSourceMemberParser.isDirectTypeMember(s, typeOffset, nameStart));
    }

    @Test
    public void isDirectTypeMember_abstractMethodHeader_isDirectMember() {
        String s = "abstract class Foo { abstract void baz(); }";
        int typeOffset = 0;
        int nameStart = s.indexOf("baz");
        assertTrue(JavaSourceMemberParser.isDirectTypeMember(s, typeOffset, nameStart));
    }

    @Test
    public void isDirectTypeMember_secondMethodAfterFirstMethod_isDirectMember() {
        String s = "class Foo { void m1() { } void m2() { } }";
        int typeOffset = 0;
        int nameStart = s.indexOf("m2");
        assertTrue(JavaSourceMemberParser.isDirectTypeMember(s, typeOffset, nameStart));
    }

    @Test
    public void isDirectTypeMember_interfaceMethod_isDirectMember() {
        String s = "interface Foo { void run(); }";
        int typeOffset = 0;
        int nameStart = s.indexOf("run");
        assertTrue(JavaSourceMemberParser.isDirectTypeMember(s, typeOffset, nameStart));
    }

    @Test
    public void isDirectTypeMember_offsetBeforeOpenBrace_returnsFalse() {
        String s = "class Foo { void bar() { } }";
        // typeOffset is already past the type body
        assertFalse(JavaSourceMemberParser.isDirectTypeMember(s, 5, 2));
    }

    @Test
    public void isDirectTypeMember_noBraceAtAll_returnsFalse() {
        assertFalse(JavaSourceMemberParser.isDirectTypeMember("no braces here", 0, 3));
    }

    // -------------------------------------------------------------------------
    // isMethodDeclaration — concrete methods (next char is '{')
    // -------------------------------------------------------------------------

    @Test
    public void isMethodDeclaration_concreteMethod_returnsTrue() {
        String s = "class Foo { void bar() { } }";
        int typeOffset = 0;
        int nameStart = s.indexOf("bar");
        int openParen = s.indexOf('(', nameStart);
        assertTrue(JavaSourceMemberParser.isMethodDeclaration(s, typeOffset, nameStart, openParen));
    }

    @Test
    public void isMethodDeclaration_concreteMethodWithParams_returnsTrue() {
        String s = "class Foo { int add(int a, int b) { return a + b; } }";
        int typeOffset = 0;
        int nameStart = s.indexOf("add");
        int openParen = s.indexOf('(', nameStart);
        assertTrue(JavaSourceMemberParser.isMethodDeclaration(s, typeOffset, nameStart, openParen));
    }

    // -------------------------------------------------------------------------
    // isMethodDeclaration — abstract / interface methods (next char is ';')
    // -------------------------------------------------------------------------

    @Test
    public void isMethodDeclaration_abstractMethod_returnsTrue() {
        String s = "abstract class Foo { abstract void baz(); }";
        int typeOffset = 0;
        int nameStart = s.indexOf("baz");
        int openParen = s.indexOf('(', nameStart);
        assertTrue(JavaSourceMemberParser.isMethodDeclaration(s, typeOffset, nameStart, openParen));
    }

    @Test
    public void isMethodDeclaration_interfaceMethod_returnsTrue() {
        String s = "interface Foo { void run(); }";
        int typeOffset = 0;
        int nameStart = s.indexOf("run");
        int openParen = s.indexOf('(', nameStart);
        assertTrue(JavaSourceMemberParser.isMethodDeclaration(s, typeOffset, nameStart, openParen));
    }

    /**
     * The key regression for P2: a same-named invocation inside a method body
     * must NOT be recognised as a declaration even though ')' is followed by ';'.
     */
    @Test
    public void isMethodDeclaration_invocationInsideMethodBody_returnsFalse() {
        String s = "class Foo { void bar() { baz(); } abstract void baz(); }";
        int typeOffset = 0;
        // Point at the 'baz' that is the call-site inside bar(), not the declaration
        int callSiteNameStart = s.indexOf("baz");
        int callSiteOpenParen = s.indexOf('(', callSiteNameStart);
        assertFalse(JavaSourceMemberParser.isMethodDeclaration(s, typeOffset, callSiteNameStart, callSiteOpenParen));
    }

    @Test
    public void isMethodDeclaration_declarationAfterInvocationOfSameName_returnsTrue() {
        String s = "class Foo { void bar() { baz(); } abstract void baz(); }";
        int typeOffset = 0;
        // Second occurrence of 'baz' is the abstract declaration
        int declNameStart = s.indexOf("baz", s.indexOf("baz") + 1);
        int declOpenParen = s.indexOf('(', declNameStart);
        assertTrue(JavaSourceMemberParser.isMethodDeclaration(s, typeOffset, declNameStart, declOpenParen));
    }

    // -------------------------------------------------------------------------
    // isMethodDeclaration — throws clause
    // -------------------------------------------------------------------------

    @Test
    public void isMethodDeclaration_concreteMethodWithThrows_returnsTrue() {
        String s = "class Foo { void bar() throws Exception { } }";
        int typeOffset = 0;
        int nameStart = s.indexOf("bar");
        int openParen = s.indexOf('(', nameStart);
        assertTrue(JavaSourceMemberParser.isMethodDeclaration(s, typeOffset, nameStart, openParen));
    }

    @Test
    public void isMethodDeclaration_abstractMethodWithThrows_returnsTrue() {
        String s = "abstract class Foo { abstract void bar() throws Exception; }";
        int typeOffset = 0;
        int nameStart = s.indexOf("bar");
        int openParen = s.indexOf('(', nameStart);
        assertTrue(JavaSourceMemberParser.isMethodDeclaration(s, typeOffset, nameStart, openParen));
    }

    // -------------------------------------------------------------------------
    // isMethodDeclaration — non-declaration patterns
    // -------------------------------------------------------------------------

    @Test
    public void isMethodDeclaration_unclosedParen_returnsFalse() {
        // no closing paren at all
        String s = "class Foo { void bar( }";
        int typeOffset = 0;
        int nameStart = s.indexOf("bar");
        int openParen = s.indexOf('(', nameStart);
        assertFalse(JavaSourceMemberParser.isMethodDeclaration(s, typeOffset, nameStart, openParen));
    }

    @Test
    public void isMethodDeclaration_assignmentContext_returnsFalse() {
        // result = bar(x) — next non-whitespace after ')' is ';' but inside a method body
        String s = "class Foo { void m() { int r = bar(x); } void bar(int x) { } }";
        int typeOffset = 0;
        int callNameStart = s.indexOf("bar(");
        int callOpenParen = s.indexOf('(', callNameStart);
        assertFalse(JavaSourceMemberParser.isMethodDeclaration(s, typeOffset, callNameStart, callOpenParen));
    }
}
