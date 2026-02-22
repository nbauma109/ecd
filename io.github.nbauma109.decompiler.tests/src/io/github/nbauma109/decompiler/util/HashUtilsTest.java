package io.github.nbauma109.decompiler.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HashUtilsTest {

    private File testRoot;

    @Before
    public void setUp() {
        File targetDir = new File("target");
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        testRoot = new File(targetDir, "hashutils-tests" + File.separator + System.nanoTime());
        testRoot.mkdirs();
    }

    @After
    public void tearDown() {
        if (testRoot != null) {
            FileUtil.deltree(testRoot);
        }
    }

    @Test
    public void sha1Hash_preservesLeadingZeroes() throws Exception {
        File file = new File(testRoot, "leading-zero.txt");
        Files.write(file.toPath(), "9".getBytes(StandardCharsets.UTF_8));

        String sha1 = HashUtils.sha1Hash(file);

        assertEquals("0ade7c2cf97f75d009975f4d720d1fa6c19f4897", sha1);
        assertEquals(40, sha1.length());
    }

    @Test
    public void sha1Hash_nullInput_returnsNull() {
        assertNull(HashUtils.sha1Hash(null));
    }
}
