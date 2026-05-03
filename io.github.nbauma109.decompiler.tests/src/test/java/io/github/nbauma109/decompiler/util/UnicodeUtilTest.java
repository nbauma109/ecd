/*******************************************************************************
 * (C) 2026 Claude (@Claude)
 * (C) 2026 Copilot (@Copilot)
 * (C) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UnicodeUtilTest {

    @Test
    public void decodeReplacesUnicodeEscapesAndKeepsOtherText() {
        String input = "prefix \\u0041\\u00DF suffix";

        assertEquals("prefix Aß suffix", UnicodeUtil.decode(input));
    }

    @Test
    public void decodeLeavesStringsWithoutEscapesUnchanged() {
        assertEquals("plain text", UnicodeUtil.decode("plain text"));
    }

    @Test
    public void decodeEmptyStringReturnsEmptyString() {
        assertEquals("", UnicodeUtil.decode(""));
    }

    @Test
    public void decodeStringContainingOnlyUnicodeEscapes() {
        // \u0048 = 'H', \u0069 = 'i'
        assertEquals("Hi", UnicodeUtil.decode("\\u0048\\u0069"));
    }

    @Test
    public void decodeHandlesUppercaseHexDigits() {
        // \u004A = 'J' (using upper-case A)
        assertEquals("J", UnicodeUtil.decode("\\u004A"));
    }

    @Test
    public void decodeHandlesMixedCaseHexDigits() {
        // \u004a and \u004A should both produce 'J'
        assertEquals("JJ", UnicodeUtil.decode("\\u004a\\u004A"));
    }

    @Test
    public void decodeConsecutiveEscapesWithNoSeparator() {
        // \u0041 = 'A', \u0042 = 'B', \u0043 = 'C'
        assertEquals("ABC", UnicodeUtil.decode("\\u0041\\u0042\\u0043"));
    }

    @Test
    public void decodeNullCharacterEscape() {
        // \u0000 = null character
        assertEquals(String.valueOf('\u0000'), UnicodeUtil.decode("\\u0000"));
    }

    @Test
    public void decodeDoesNotMatchIncompleteEscape() {
        // Only 3 hex digits - pattern requires exactly 4, so nothing is replaced
        String incomplete = "\\u004";
        assertEquals(incomplete, UnicodeUtil.decode(incomplete));
    }
}
