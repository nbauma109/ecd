package io.github.nbauma109.decompiler.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileUtilTest {

    private File testRoot;

    @Before
    public void setUp() {
        File targetDir = new File("target");
        assertTrue(targetDir.exists() || targetDir.mkdirs());

        testRoot = new File(targetDir, "fileutil-tests" + File.separator + System.nanoTime());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void tearDown() {
        if (testRoot != null) {
            FileUtil.deltree(testRoot);
        }
    }

    @Test
    public void writeToFile_withoutEncoding_createsParentsAndWritesContent() throws Exception {
        File file = resolve("no-encoding/hello.txt");
        String content = "plain-ascii\nsecond-line\n";

        FileUtil.writeToFile(file, content);

        assertTrue(file.exists());
        assertDecodedEqualsUsingCommonCharsets(file, content);
    }

    @Test
    public void writeToFile_withExplicitEncoding_createsParentsAndWritesContent() throws Exception {
        File file = resolve("a/b/c.txt");
        String content = "line1\nline2\n";

        FileUtil.writeToFile(file, content, StandardCharsets.UTF_8.name());

        assertTrue(file.exists());
        assertEquals(content, readString(file, StandardCharsets.UTF_8));
    }

    @Test
    public void getContent_trimsWhitespace() throws Exception {
        File file = resolve("trim.txt");
        FileUtil.writeToFile(file, "  hello  \n", StandardCharsets.UTF_8.name());

        assertEquals("hello", FileUtil.getContent(file));
    }

    @Test
    public void getContent_missingFile_returnsNull() {
        File missing = resolve("missing.txt");
        assertNull(FileUtil.getContent(missing));
    }

    @Test
    public void isZipFile_nullAndNonZipAndZip() throws Exception {
        assertFalse(FileUtil.isZipFile(null));

        File nonZip = resolve("not-a-zip.txt");
        FileUtil.writeToFile(nonZip, "data", StandardCharsets.UTF_8.name());
        assertFalse(FileUtil.isZipFile(nonZip.getAbsolutePath()));

        File zip = resolve("ok.zip");
        createZipWithSingleEntry(zip, "a.txt", "payload");
        assertTrue(FileUtil.isZipFile(zip.getAbsolutePath()));
    }

    @Test
    public void copyFile_copiesBytesAndReturnsFalse() throws Exception {
        File src = resolve("src.bin");
        File dest = resolve("dest.bin");

        byte[] payload = new byte[] { 0, 1, 2, 3, 4, 10, 20, 30, 40, 50, (byte) 255 };
        writeBytes(src, payload);

        boolean result = FileUtil.copyFile(src.getAbsolutePath(), dest.getAbsolutePath());

        assertFalse(result);
        assertTrue(dest.exists());
        assertArrayEquals(payload, Files.readAllBytes(dest.toPath()));
    }

    @Test
    public void deleteDirectory_deletesRecursively_NoProgressMonitor() throws Exception {
        File dir = resolve("to-delete");
        File nested = new File(dir, "x/y/z");
        assertTrue(nested.mkdirs());

        File file1 = new File(dir, "a.txt");
        File file2 = new File(nested, "b.txt");
        FileUtil.writeToFile(file1, "a", StandardCharsets.UTF_8.name());
        FileUtil.writeToFile(file2, "b", StandardCharsets.UTF_8.name());

        assertTrue(dir.exists());

        FileUtil.deleteDirectory(null, dir, 1);

        assertFalse(dir.exists());
    }

    @Test
    public void deltree_deletesRecursively() throws Exception {
        File dir = resolve("tree");
        File nested = new File(dir, "p/q");
        assertTrue(nested.mkdirs());

        File file = new File(nested, "c.txt");
        FileUtil.writeToFile(file, "c", StandardCharsets.UTF_8.name());

        assertTrue(dir.exists());

        FileUtil.deltree(dir);

        assertFalse(dir.exists());
    }

    @Test
    public void recursiveZip_zipsFilteredFilesWithExpectedPathsAndContent() throws Exception {
        File root = resolve("zip-root");
        File sub = new File(root, "sub");
        assertTrue(sub.mkdirs());

        File keep1 = new File(root, "keep1.txt");
        File keep2 = new File(sub, "keep2.txt");
        File skip = new File(sub, "skip.tmp");

        FileUtil.writeToFile(keep1, "one", StandardCharsets.UTF_8.name());
        FileUtil.writeToFile(keep2, "two", StandardCharsets.UTF_8.name());
        FileUtil.writeToFile(skip, "skip", StandardCharsets.UTF_8.name());

        File zipFile = resolve("out.zip");

        FileFilter filter = file -> !file.getName().endsWith(".tmp");

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            FileUtil.recursiveZip(null, zos, root, "", filter, 1);
        }

        Map<String, byte[]> entries = readZipEntries(zipFile);

        assertTrue(entries.containsKey("keep1.txt"));
        assertTrue(entries.containsKey("sub/keep2.txt"));
        assertFalse(entries.containsKey("sub/skip.tmp"));

        assertEquals("one", new String(entries.get("keep1.txt"), StandardCharsets.UTF_8));
        assertEquals("two", new String(entries.get("sub/keep2.txt"), StandardCharsets.UTF_8));
    }

    private File resolve(String relativePath) {
        return new File(testRoot, relativePath);
    }

    private static String readString(File file, java.nio.charset.Charset charset) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes, charset);
    }

    private static void writeBytes(File file, byte[] bytes) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.exists() || parent.mkdirs());
        }
        Files.write(file.toPath(), bytes);
    }

    private static void createZipWithSingleEntry(File zipFile, String entryName, String content) throws IOException {
        File parent = zipFile.getParentFile();
        if (parent != null) {
            assertTrue(parent.exists() || parent.mkdirs());
        }
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            byte[] payload = content.getBytes(StandardCharsets.UTF_8);
            ZipEntry entry = new ZipEntry(entryName);
            entry.setSize(payload.length);
            zos.putNextEntry(entry);
            zos.write(payload);
            zos.closeEntry();
        }
    }

    private static Map<String, byte[]> readZipEntries(File zipFile) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipFile zf = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> enumeration = zf.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] bytes = readAllBytes(zf, entry);
                entries.put(entry.getName(), bytes);
            }
        }
        return entries;
    }

    private static byte[] readAllBytes(ZipFile zf, ZipEntry entry) throws IOException {
        try (InputStream is = zf.getInputStream(entry)) {
            return toByteArray(is);
        }
    }

    private static byte[] toByteArray(InputStream is) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        while ((read = is.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void assertDecodedEqualsUsingCommonCharsets(File file, String expected) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        Charset[] candidates = new Charset[] {
                StandardCharsets.UTF_8,
                StandardCharsets.ISO_8859_1,
                StandardCharsets.US_ASCII,
                StandardCharsets.UTF_16,
                StandardCharsets.UTF_16LE,
                StandardCharsets.UTF_16BE
        };

        for (Charset charset : candidates) {
            String decoded = new String(bytes, charset);
            if (expected.equals(decoded)) {
                return;
            }
        }

        String bestEffort = new String(bytes, StandardCharsets.UTF_8);
        fail("File content did not match expected text with common encodings. UTF-8 decode was: " + bestEffort);
    }

}
