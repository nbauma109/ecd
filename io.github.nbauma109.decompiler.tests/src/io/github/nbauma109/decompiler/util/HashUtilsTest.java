package io.github.nbauma109.decompiler.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport;

public class HashUtilsTest {

    private File testRoot;

    @Before
    public void setUp() {
        testRoot = DecompilerTestSupport.createTargetTempDir("hashutils-tests"); //$NON-NLS-1$
    }

    @After
    public void tearDown() {
        if (testRoot != null) {
            FileUtils.deleteQuietly(testRoot);
        }
    }

    @Test
    public void sha1HashReturnsExpectedDigestForExistingFile() throws IOException {
        File file = new File(testRoot, "payload.txt");
        Files.writeString(file.toPath(), "hello world", StandardCharsets.UTF_8);

        assertEquals("2aae6c35c94fcfb415dbe95f408b9ce91ee846ed", HashUtils.sha1Hash(file));
    }

    @Test
    public void sha1HashReturnsNullForNullFile() {
        assertNull(HashUtils.sha1Hash(null));
    }

    @Test
    public void sha1HashWrapsIoErrors() {
        File missing = new File(testRoot, "missing.txt");

        try {
            HashUtils.sha1Hash(missing);
        } catch (UncheckedIOException e) {
            assertTrue(e.getCause() instanceof FileNotFoundException);
            return;
        }

        throw new AssertionError("Expected UncheckedIOException to be thrown");
    }
}
