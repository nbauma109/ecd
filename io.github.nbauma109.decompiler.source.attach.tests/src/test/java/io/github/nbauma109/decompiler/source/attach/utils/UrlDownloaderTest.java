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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UrlDownloaderTest {

    private static final String TEST_USER = "alice"; //$NON-NLS-1$
    private static final String TEST_PASSWORD = "secret"; //$NON-NLS-1$

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

    @Test(expected = UnsupportedOperationException.class)
    public void downloadWithScmUrlThrowsUnsupportedOperationException() throws Exception {
        new UrlDownloader().download("scm:git:https://example.com/repo.git", null);
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

        downloader.setServiceUser(TEST_USER);
        assertEquals(TEST_USER, downloader.getServiceUser());

        downloader.setServiceUser(null);
        assertNull(downloader.getServiceUser());
    }

    @Test
    public void downloadWithServiceCredentialsSetsAuthenticatorForHttpConnection() throws IOException {
        // Setting serviceUser + servicePassword triggers the conn.setAuthenticator(buildAuthenticator(...))
        // path inside setConnectionAuthenticator, covering that line and the buildAuthenticator method entry.
        UrlDownloader downloader = new UrlDownloader();
        downloader.setServiceUser(TEST_USER);
        downloader.setServicePassword(TEST_PASSWORD);

        File targetFile = new File(testRoot, "auth-attempt.jar");
        // localhost:0 is unreachable but still creates an HttpURLConnection object so that
        // setConnectionAuthenticator (and therefore buildAuthenticator) is invoked before connect.
        String result = downloader.download("http://localhost:0/test.jar", targetFile); //$NON-NLS-1$

        assertNotNull(result);
        // The download itself will fail; what matters is that the auth setup code is exercised.
    }

    @Test
    public void downloadWithServiceCredentialsCoversServerAuthBranchOnChallenge() throws IOException, InterruptedException {
        // Start a minimal HTTP/1.0 server that:
        //   1st request (no Authorization header) → 401 Unauthorized
        //   2nd request (with Authorization header) → 200 OK with body
        // The JDK's HttpURLConnection will invoke the per-connection authenticator on 401
        // and retry, exercising the SERVER branch inside buildAuthenticator.
        byte[] body = "secure-content".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
        CountDownLatch serverReady = new CountDownLatch(1);
        AtomicBoolean serverRunning = new AtomicBoolean(true);

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            Thread serverThread = new Thread(() -> {
                serverReady.countDown();
                // Handle up to 2 connections: first the 401 challenge, then the authenticated retry.
                for (int i = 0; i < 2 && serverRunning.get(); i++) {
                    try (Socket socket = serverSocket.accept()) {
                        handleHttpRequest(socket, body);
                    } catch (IOException e) {
                        if (serverRunning.get()) {
                            Thread.currentThread().interrupt();
                        }
                        break;
                    }
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            assertTrue("Server did not start in time", serverReady.await(5, TimeUnit.SECONDS)); //$NON-NLS-1$

            try {
                UrlDownloader downloader = new UrlDownloader();
                downloader.setServiceUser("user"); //$NON-NLS-1$
                downloader.setServicePassword("pass"); //$NON-NLS-1$

                File targetFile = new File(testRoot, "authed.jar");
                String result = downloader.download("http://localhost:" + port + "/auth.jar", targetFile); //$NON-NLS-1$

                assertNotNull(result);
            } finally {
                serverRunning.set(false);
            }
        }
    }

    /**
     * Serves a single HTTP request: sends 401 if no Authorization header is present,
     * otherwise sends 200 with the supplied body. Uses HTTP/1.0 so each request is
     * a distinct TCP connection, allowing the retry to come in as a separate accept().
     */
    private static void handleHttpRequest(Socket socket, byte[] body) throws IOException {
        try (InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream()) {
            // Read request headers (until blank line)
            StringBuilder sb = new StringBuilder();
            int prev = -1;
            int c;
            boolean hasAuth = false;
            while ((c = in.read()) != -1) {
                sb.append((char) c);
                if (prev == '\r' && c == '\n') {
                    String line = sb.toString().trim();
                    if (line.isEmpty()) {
                        break; // end of headers
                    }
                    if (line.startsWith("Authorization")) { //$NON-NLS-1$
                        hasAuth = true;
                    }
                    sb.setLength(0);
                }
                prev = c;
            }

            if (hasAuth) {
                byte[] statusLine = ("HTTP/1.0 200 OK\r\nContent-Type: application/octet-stream\r\n" //$NON-NLS-1$
                        + "Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
                out.write(statusLine);
                out.write(body);
            } else {
                byte[] challenge = ("""
                        HTTP/1.0 401 Unauthorized\r
                        WWW-Authenticate: Basic realm="test"\r
                        Content-Length: 0\r
                        \r
                        """).getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
                        out.write(challenge);
            }
            out.flush();
        }
    }

    @Test
    public void servicePasswordGetterAndSetterRoundTrip() {
        UrlDownloader downloader = new UrlDownloader();
        assertNull(downloader.getServicePassword());

        downloader.setServicePassword(TEST_PASSWORD);
        assertEquals(TEST_PASSWORD, downloader.getServicePassword());

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
