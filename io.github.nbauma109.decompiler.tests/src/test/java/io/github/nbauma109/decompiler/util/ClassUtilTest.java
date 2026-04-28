package io.github.nbauma109.decompiler.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class ClassUtilTest {

    @Test
    public void testIsClassFile() throws IOException {
        assertTrue(ClassUtil.isClassFile(Files.readAllBytes(Paths.get("target/classes/HelloWorld.class"))));
        assertFalse(ClassUtil.isClassFile(Files.readAllBytes(Paths.get("target/test-classes/Test.txt"))));
    }

    @Test
    public void isClassFileReturnsFalseForEmptyByteArray() {
        assertFalse(ClassUtil.isClassFile(new byte[] {}));
    }

    @Test
    public void isClassFileReturnsFalseForWrongMagicBytes() {
        // All zeros – not a class file
        assertFalse(ClassUtil.isClassFile(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }));
    }

    @Test
    public void isClassFileReturnsFalseForTruncatedHeader() {
        // Only the 4-byte magic, missing the two version shorts
        assertFalse(ClassUtil.isClassFile(new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE }));
    }

    @Test
    public void isClassFileReturnsTrueForMinimalValidHeader() {
        // 0xCAFEBABE + minor version 0 + major version 61 (Java 17)
        byte[] header = {
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                0x00, 0x00,
                0x00, 0x3D
        };
        assertTrue(ClassUtil.isClassFile(header));
    }
}
