/*******************************************************************************
 * Copyright (c) 2026.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UrlDownloaderTest {

    private File testRoot;

    @Before
    public void setUp() {
        File targetDir = new File("target");
        assertTrue(targetDir.exists() || targetDir.mkdirs());
        testRoot = new File(targetDir, "url-downloader-tests-" + System.nanoTime());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void tearDown() {
        FileUtils.deleteQuietly(testRoot);
    }

    @Test
    public void downloadToTargetFileDownloadsContentToSpecifiedPath() throws Exception {
        // Create a source file with known content
        File sourceFile = new File(testRoot, "source.txt");
        byte[] content = "test content for download".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write(content);
        }

        // Target in a new subdirectory (parent does not exist yet)
        File targetDir = new File(testRoot, "sub-" + UUID.randomUUID());
        File targetFile = new File(targetDir, "downloaded.txt");

        String result = new UrlDownloader().download(sourceFile.toURI().toURL().toString(), targetFile);

        assertNotNull(result);
        assertEquals(targetFile.getAbsolutePath(), result);
        assertTrue(targetFile.exists());
        byte[] actualContent = Files.readAllBytes(targetFile.toPath());
        assertEquals(new String(content, StandardCharsets.UTF_8), new String(actualContent, StandardCharsets.UTF_8));
    }

    @Test
    public void downloadToTargetFileWithExistingParentDownloadsContentSuccessfully() throws Exception {
        // Create a source file
        File sourceFile = new File(testRoot, "source2.txt");
        byte[] content = "another test content".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write(content);
        }

        // Target in an already-existing directory
        File targetFile = new File(testRoot, "already-there.txt");

        String result = new UrlDownloader().download(sourceFile.toURI().toURL().toString(), targetFile);

        assertNotNull(result);
        assertEquals(targetFile.getAbsolutePath(), result);
        assertTrue(targetFile.exists());
    }

    @Test
    public void downloadWithNullTargetFileDownloadsTempFile() throws Exception {
        // Create a source file
        File sourceFile = new File(testRoot, "source3.txt");
        byte[] content = "temp content".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write(content);
        }

        String result = new UrlDownloader().download(sourceFile.toURI().toURL().toString(), null);

        assertNotNull(result);
        File downloadedFile = new File(result);
        assertTrue(downloadedFile.exists());
        downloadedFile.delete();
    }

    @Test
    public void downloadWithInvalidUrlReturnsPathAndDoesNotThrow() throws Exception {
        File targetFile = new File(testRoot, "should-not-exist.txt");

        String result = new UrlDownloader().download("http://localhost:0/no-such-file.jar", targetFile);

        // Should return the file path (even if download failed and file was deleted)
        assertNotNull(result);
    }

    @Test
    public void downloadWithScmUrlThrowsUnsupportedOperationException() {
        try {
            new UrlDownloader().download("scm:git:https://example.com/repo.git", null);
            fail("Expected UnsupportedOperationException"); //$NON-NLS-1$
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Exception e) {
            fail("Expected UnsupportedOperationException but got " + e.getClass()); //$NON-NLS-1$
        }
    }

    @Test
    public void downloadWithExistingLocalFileReturnsPathDirectly() throws Exception {
        // Create a local file
        File localFile = new File(testRoot, "local.txt");
        try (FileOutputStream fos = new FileOutputStream(localFile)) {
            fos.write("local".getBytes(StandardCharsets.UTF_8));
        }

        // When URL is a path to an existing file, it should return that path without downloading
        String result = new UrlDownloader().download(localFile.getAbsolutePath(), null);

        assertEquals(localFile.getAbsolutePath(), result);
    }

    @Test
    public void serviceUserGetterAndSetterRoundTrip() {
        UrlDownloader downloader = new UrlDownloader();
        assertNull(downloader.getServiceUser());

        downloader.setServiceUser("alice"); //$NON-NLS-1$
        assertEquals("alice", downloader.getServiceUser()); //$NON-NLS-1$

        downloader.setServiceUser(null);
        assertNull(downloader.getServiceUser());
    }

    @Test
    public void servicePasswordGetterAndSetterRoundTrip() {
        UrlDownloader downloader = new UrlDownloader();
        assertNull(downloader.getServicePassword());

        downloader.setServicePassword("secret"); //$NON-NLS-1$
        assertEquals("secret", downloader.getServicePassword()); //$NON-NLS-1$

        downloader.setServicePassword(null);
        assertNull(downloader.getServicePassword());
    }

    @Test
    public void zipFolderCreatesZipContainingOnlyJavaFiles() throws IOException {
        // Create a source folder with java and non-java files
        File srcFolder = new File(testRoot, "src-to-zip");
        File pkgDir = new File(srcFolder, "com/example");
        assertTrue(pkgDir.mkdirs());

        File javaFile = new File(pkgDir, "Hello.java");
        File classFile = new File(pkgDir, "Hello.class");
        try (FileOutputStream fos = new FileOutputStream(javaFile)) {
            fos.write("public class Hello {}".getBytes(StandardCharsets.UTF_8));
        }
        try (FileOutputStream fos = new FileOutputStream(classFile)) {
            fos.write(new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE });
        }

        File destZip = new File(testRoot, "out.zip");
        new UrlDownloader().zipFolder(srcFolder, destZip);

        assertTrue("Zip file should be created", destZip.exists());
        assertTrue("Zip file should have content", destZip.length() > 0);

        // Verify: only .java entries (no .class)
        // Guard against zip-bomb: cap entry count and total uncompressed size.
        final int maxEntries = 10_000;
        final long maxTotalSize = 100_000_000L; // 100 MB
        boolean hasJava = false;
        boolean hasClass = false;
        try (ZipFile zf = new ZipFile(destZip)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            int entryCount = 0;
            long totalSize = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entryCount++;
                assertTrue("Too many zip entries – possible zip bomb", entryCount <= maxEntries);
                long entrySize = entry.getSize();
                if (entrySize > 0) {
                    totalSize += entrySize;
                    assertTrue("Total uncompressed size exceeds limit – possible zip bomb", totalSize <= maxTotalSize);
                }
                String name = entry.getName();
                if (name.endsWith(".java")) { //$NON-NLS-1$
                    hasJava = true;
                }
                if (name.endsWith(".class")) { //$NON-NLS-1$
                    hasClass = true;
                }
            }
        }
        assertTrue("Zip should contain at least one .java entry", hasJava);
        assertFalse("Zip should not contain any .class entries", hasClass);
    }

    @Test
    public void deleteFolderRemovesDirectoryAndItsContents() throws IOException {
        File dir = new File(testRoot, "dir-to-delete");
        File nested = new File(dir, "sub");
        assertTrue(nested.mkdirs());

        File file = new File(dir, "file.txt");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write("content".getBytes(StandardCharsets.UTF_8));
        }

        assertTrue(dir.exists());

        new UrlDownloader().delete(dir);

        assertFalse("Directory should have been deleted", dir.exists());
    }
}
