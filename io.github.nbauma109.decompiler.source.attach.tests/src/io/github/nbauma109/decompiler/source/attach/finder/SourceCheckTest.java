package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SourceCheckTest {

    private File testRoot;

    @Before
    public void setUp() {
        File targetDir = new File("target");
        assertTrue(targetDir.exists() || targetDir.mkdirs());

        testRoot = new File(targetDir, "sourcecheck-tests" + File.separator + System.nanoTime());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void tearDown() {
        if (testRoot != null) {
            deleteRecursively(testRoot);
        }
    }

    @Test
    public void isWrongSource_returnsTrueWhenBinaryHasClassesButSourceHasNoJavaFiles() throws Exception {
        File binJar = createZip("demo.jar", "pkg/Demo.class", "bytecode");
        File srcJar = createZip("demo-sources.jar", "META-INF/MANIFEST.MF", "manifest");

        assertTrue(SourceCheck.isWrongSource(srcJar, binJar));
    }

    @Test
    public void isWrongSource_returnsFalseWhenSourceContainsJavaFiles() throws Exception {
        File binJar = createZip("demo.jar", "pkg/Demo.class", "bytecode");
        File srcJar = createZip("demo-sources.jar", "pkg/Demo.java", "class Demo {}");

        assertFalse(SourceCheck.isWrongSource(srcJar, binJar));
    }

    @Test
    public void isWrongSource_returnsFalseWhenBinaryHasNoClasses() throws Exception {
        File binJar = createZip("demo.jar", "META-INF/MANIFEST.MF", "manifest");
        File srcJar = createZip("demo-sources.jar", "docs/readme.txt", "text");

        assertFalse(SourceCheck.isWrongSource(srcJar, binJar));
    }

    private File createZip(String name, String entryName, String content) throws Exception {
        File zip = new File(testRoot, name);
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))) {
            ZipEntry entry = new ZipEntry(entryName);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            entry.setSize(bytes.length);
            zos.putNextEntry(entry);
            zos.write(bytes);
            zos.closeEntry();
        }
        return zip;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
