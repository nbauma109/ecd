package io.github.nbauma109.decompiler.source.attach.finder;

import java.io.IOException;

import org.junit.Test;

public class JitPackSourceCodeFinderTest extends AbstractSourceCodeFinderTests {

    private static final String ASCII_TABLE_GAV_URL = "https://jitpack.io/com/github/freva/ascii-table/1.12.0/"; //$NON-NLS-1$
    private static final String ASCII_TABLE_FILE_NAME = "ascii-table-1.12.0"; //$NON-NLS-1$

    @Test
    public void testFind() throws IOException {
        testFind(ASCII_TABLE_GAV_URL, null, ASCII_TABLE_FILE_NAME);
    }

    @Override
    protected AbstractSourceCodeFinder newSourceCodeFinder(String serviceUrl) {
        return new JitPackSourceCodeFinder();
    }
}
