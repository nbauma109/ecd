package io.github.nbauma109.decompiler.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class ClassUtilTest {

    @Test
    public void testIsClassFile() throws Exception {
        assertTrue(ClassUtil.isClassFile(Files.readAllBytes(Paths.get("target/classes/HelloWorld.class"))));
        assertFalse(ClassUtil.isClassFile(Files.readAllBytes(Paths.get("target/classes/Test.txt"))));
    }
}
