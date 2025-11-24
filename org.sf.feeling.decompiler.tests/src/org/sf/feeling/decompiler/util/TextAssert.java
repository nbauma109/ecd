package org.sf.feeling.decompiler.util;

import static org.junit.Assert.assertEquals;

public final class TextAssert {

    private TextAssert() {
    }

    public static void assertEquivalent(String expected, String actual) {
        String normalizedExpected = normalize(expected);
        String normalizedActual = normalize(actual);
        assertEquals(normalizedExpected, normalizedActual);
    }

    private static String normalize(String input) {
        String unified = input.replace("\r\n", "\n").replace("\r", "\n");
        return unified.replaceAll("[ \t]+(?=\n)", "").replaceAll("[ \t]+$", "");
    }
}
