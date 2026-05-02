/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.Test;

public class UpdateCheckJobTest {

    // ---------------------------------------------------------------------------
    // Constructor / metadata
    // ---------------------------------------------------------------------------

    @Test
    public void constructorConfiguresJobAsSystemDecorateJob() {
        UpdateCheckJob job = new UpdateCheckJob();
        assertTrue("job should be a system job", job.isSystem()); //$NON-NLS-1$
        assertEquals("job priority should be DECORATE", Job.DECORATE, job.getPriority()); //$NON-NLS-1$
    }

    // ---------------------------------------------------------------------------
    // run() – mock GitHub API server helpers
    // ---------------------------------------------------------------------------

    /** Runs {@code job.run(null)} against a local HTTP server that serves {@code responseBody}. */
    private IStatus runJobAgainstMockServer(String responseBody) throws Exception {
        CountDownLatch serverReady = new CountDownLatch(1);
        AtomicBoolean serverRunning = new AtomicBoolean(true);

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            Thread serverThread = new Thread(() -> {
                serverReady.countDown();
                try (Socket socket = serverSocket.accept()) {
                    drainRequest(socket);
                    sendHttpResponse(socket, 200, responseBody);
                } catch (IOException e) {
                    if (serverRunning.get()) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            assertTrue("server did not start in time", serverReady.await(5, TimeUnit.SECONDS)); //$NON-NLS-1$

            try {
                UpdateCheckJob job = new UpdateCheckJob("http://localhost:" + port + "/releases/latest"); //$NON-NLS-1$
                return job.run(null);
            } finally {
                serverRunning.set(false);
            }
        }
    }

    private static void drainRequest(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        byte[] buf = new byte[4096];
        // Read until the blank line that ends the HTTP request headers
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            sb.append((char) b);
            if (sb.length() >= 4 && sb.substring(sb.length() - 4).equals("\r\n\r\n")) { //$NON-NLS-1$
                break;
            }
        }
    }

    private static void sendHttpResponse(Socket socket, int statusCode, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String response = "HTTP/1.1 " + statusCode + " OK\r\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "Content-Type: application/json\r\n" //$NON-NLS-1$
                + "Content-Length: " + bodyBytes.length + "\r\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "Connection: close\r\n" //$NON-NLS-1$
                + "\r\n"; //$NON-NLS-1$
        OutputStream out = socket.getOutputStream();
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
    }

    // ---------------------------------------------------------------------------
    // run() – various GitHub API response scenarios
    // ---------------------------------------------------------------------------

    @Test
    public void runReturnsOkWhenNewerVersionIsAvailable() throws Exception {
        // A version far in the future triggers the "show popup" path
        String body = "{\"tag_name\":\"v9999.0.0\"}"; //$NON-NLS-1$
        IStatus status = runJobAgainstMockServer(body);
        assertEquals(IStatus.OK, status.getSeverity());
    }

    @Test
    public void runReturnsOkWhenCurrentVersionIsAlreadyLatest() throws Exception {
        // Same version as what's in the bundle — no popup should be shown
        String currentVersion = JavaDecompilerPlugin.getDefault().getBundle().getVersion().toString();
        String body = "{\"tag_name\":\"v" + currentVersion + "\"}"; //$NON-NLS-1$ //$NON-NLS-2$
        IStatus status = runJobAgainstMockServer(body);
        assertEquals(IStatus.OK, status.getSeverity());
    }

    @Test
    public void runReturnsOkWhenTagNameIsMissingFromResponse() throws Exception {
        // JSON object without "tag_name" — fetchLatestVersion returns null
        String body = "{\"name\":\"Some Release\",\"published_at\":\"2026-01-01T00:00:00Z\"}"; //$NON-NLS-1$
        IStatus status = runJobAgainstMockServer(body);
        assertEquals(IStatus.OK, status.getSeverity());
    }

    @Test
    public void runReturnsOkWhenResponseIsNotAJsonObject() throws Exception {
        // A plain string is not a JSON object
        String body = "\"not-an-object\""; //$NON-NLS-1$
        IStatus status = runJobAgainstMockServer(body);
        assertEquals(IStatus.OK, status.getSeverity());
    }

    @Test
    public void runReturnsOkWhenResponseIsInvalidJson() throws Exception {
        String body = "not-json-at-all"; //$NON-NLS-1$
        IStatus status = runJobAgainstMockServer(body);
        assertEquals(IStatus.OK, status.getSeverity());
    }

    @Test
    public void runReturnsOkWhenVersionStringIsUnparseable() throws Exception {
        // tag_name present but version string is not a valid OSGi version
        String body = "{\"tag_name\":\"not-a-version\"}"; //$NON-NLS-1$
        IStatus status = runJobAgainstMockServer(body);
        assertEquals(IStatus.OK, status.getSeverity());
    }

    @Test
    public void runReturnsOkWhenVersionHasNoVPrefix() throws Exception {
        // Version without "v" prefix — parseVersion should still work
        String body = "{\"tag_name\":\"9999.0.0\"}"; //$NON-NLS-1$
        IStatus status = runJobAgainstMockServer(body);
        assertEquals(IStatus.OK, status.getSeverity());
    }

    @Test
    public void runHandlesConnectionRefusedGracefully() {
        // Port 1 is normally inaccessible — simulate unreachable host
        UpdateCheckJob job = new UpdateCheckJob("http://localhost:1/releases/latest"); //$NON-NLS-1$
        IStatus status = job.run(null);
        assertEquals(IStatus.OK, status.getSeverity());
    }
}
