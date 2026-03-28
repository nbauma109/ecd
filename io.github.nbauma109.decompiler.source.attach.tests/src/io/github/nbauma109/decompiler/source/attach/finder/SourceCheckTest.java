package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport;

public class SourceCheckTest {

    private static final String BIN_JAR_NAME = "demo.jar";
    private static final String SOURCE_JAR_NAME = "demo-sources.jar";

    private File testRoot;

    @Before
    public void setUp() {
        testRoot = SourceAttachTestSupport.createTargetTempDir("sourcecheck-tests"); //$NON-NLS-1$
    }

    @After
    public void tearDown() {
        if (testRoot != null) {
            FileUtils.deleteQuietly(testRoot);
        }
    }

    @Test
    public void isWrongSourceReturnsTrueWhenBinaryHasClassesButSourceHasNoJavaFiles() throws IOException {
        File binJar = createZip(BIN_JAR_NAME, "pkg/Demo.class", "bytecode");
        File srcJar = createZip(SOURCE_JAR_NAME, "META-INF/MANIFEST.MF", "manifest");

        assertTrue(SourceCheck.isWrongSource(srcJar, binJar));
    }

    @Test
    public void isWrongSourceReturnsFalseWhenSourceContainsJavaFiles() throws IOException {
        File binJar = createZip(BIN_JAR_NAME, "pkg/Demo.class", "bytecode");
        File srcJar = createZip(SOURCE_JAR_NAME, "pkg/Demo.java", "class Demo {}");

        assertFalse(SourceCheck.isWrongSource(srcJar, binJar));
    }

    @Test
    public void isWrongSourceReturnsFalseWhenBinaryHasNoClasses() throws IOException {
        File binJar = createZip(BIN_JAR_NAME, "META-INF/MANIFEST.MF", "manifest");
        File srcJar = createZip(SOURCE_JAR_NAME, "docs/readme.txt", "text");

        assertFalse(SourceCheck.isWrongSource(srcJar, binJar));
    }

    private File createZip(String name, String entryName, String content) throws IOException {
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
}
