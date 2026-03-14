package io.github.nbauma109.decompiler.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UnicodeUtilTest {

    @Test
    public void decode_replacesUnicodeEscapesAndKeepsOtherText() {
        String input = "prefix \\u0041\\u00DF suffix";

        assertEquals("prefix Aß suffix", UnicodeUtil.decode(input));
    }

    @Test
    public void decode_leavesStringsWithoutEscapesUnchanged() {
        assertEquals("plain text", UnicodeUtil.decode("plain text"));
    }
}
