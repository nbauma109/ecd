/*******************************************************************************
 * © 2026 Nicolas Baumann (@nbauma109)
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
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

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
    // run() – connection refused
    // ---------------------------------------------------------------------------

    @Test
    public void runHandlesConnectionRefusedGracefully() {
        // Port 1 is normally inaccessible — simulate unreachable host
        UpdateCheckJob job = new UpdateCheckJob("http://localhost:1/releases/latest"); //$NON-NLS-1$
        IStatus status = job.run(null);
        assertEquals(IStatus.OK, status.getSeverity());
    }

    // ---------------------------------------------------------------------------
    // run() – mock GitHub API server helpers
    // ---------------------------------------------------------------------------

    /** Runs {@code job.run(null)} against a local HTTP server that serves {@code responseBody}. */
    static IStatus runJobAgainstMockServer(String responseBody) throws Exception {
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
        // Read until the blank line that ends the HTTP request headers
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            sb.append((char) b);
            if (sb.length() >= 4 && "\r\n\r\n".equals(sb.substring(sb.length() - 4))) { //$NON-NLS-1$
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
    // run() – various GitHub API response scenarios (parameterized)
    // ---------------------------------------------------------------------------

    @RunWith(Parameterized.class)
    public static class MockServerResponseTest {

        @Parameters(name = "{0}")
        public static Collection<Object[]> data() {
            String currentVersion = JavaDecompilerPlugin.getDefault().getBundle().getVersion().toString();
            return Arrays.asList(new Object[][] {
                { "newer version triggers showPopup path",        "{\"tag_name\":\"v9999.0.0\"}" },                                            //$NON-NLS-1$ //$NON-NLS-2$
                { "current version already latest",              "{\"tag_name\":\"v" + currentVersion + "\"}" },                              //$NON-NLS-1$ //$NON-NLS-2$
                { "tag_name missing from response",              "{\"name\":\"Some Release\",\"published_at\":\"2026-01-01T00:00:00Z\"}" },    //$NON-NLS-1$ //$NON-NLS-2$
                { "response is not a JSON object",               "\"not-an-object\"" },                                                        //$NON-NLS-1$ //$NON-NLS-2$
                { "response is invalid JSON",                    "not-json-at-all" },                                                          //$NON-NLS-1$ //$NON-NLS-2$
                { "version string is unparseable",               "{\"tag_name\":\"not-a-version\"}" },                                         //$NON-NLS-1$ //$NON-NLS-2$
                { "version has no v prefix",                     "{\"tag_name\":\"9999.0.0\"}" },                                              //$NON-NLS-1$ //$NON-NLS-2$
            });
        }

        private final String responseBody;

        /**
         * @param description unused test description
         */
        public MockServerResponseTest(String description, String responseBody) {
            this.responseBody = responseBody;
        }

        @Test
        public void runReturnsOkForAllResponseVariants() throws Exception {
            IStatus status = runJobAgainstMockServer(responseBody);
            assertEquals(IStatus.OK, status.getSeverity());
        }
    }
}
