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
}
