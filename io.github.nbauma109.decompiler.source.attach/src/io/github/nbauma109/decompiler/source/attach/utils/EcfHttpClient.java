/*******************************************************************************
 * Copyright (c) 2026 ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.eclipse.ecf.core.util.Proxy;
import org.eclipse.ecf.core.util.ProxyAddress;
import org.eclipse.ecf.filetransfer.IFileTransferListener;
import org.eclipse.ecf.filetransfer.IRetrieveFileTransferOptions;
import org.eclipse.ecf.filetransfer.IRetrieveFileTransferContainerAdapter;
import org.eclipse.ecf.filetransfer.IncomingFileTransferException;
import org.eclipse.ecf.filetransfer.events.IFileTransferEvent;
import org.eclipse.ecf.filetransfer.events.IIncomingFileTransferReceiveDataEvent;
import org.eclipse.ecf.filetransfer.events.IIncomingFileTransferReceiveDoneEvent;
import org.eclipse.ecf.filetransfer.events.IIncomingFileTransferReceiveStartEvent;
import org.eclipse.ecf.filetransfer.identity.FileCreateException;
import org.eclipse.ecf.filetransfer.identity.FileIDFactory;
import org.eclipse.ecf.filetransfer.identity.IFileID;
import org.eclipse.ecf.filetransfer.service.IRetrieveFileTransferFactory;
import org.eclipse.ecf.internal.provider.filetransfer.httpclient5.IHttpClientFactory;
import org.eclipse.ecf.provider.filetransfer.httpclient5.HttpClientOptions;
import org.eclipse.ecf.provider.filetransfer.util.JREProxyHelper;
import org.eclipse.ecf.provider.filetransfer.util.ProxySetupHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Synchronous HTTP client wrapper using Eclipse ECF file transfer.
 * This provides transparent system proxy authentication support via ECF httpclient5.
 */
public class EcfHttpClient {

    private static final String USER_AGENT = "ECD-SourceAttach/1.0 (Eclipse ECF)";
    private static final int DEFAULT_TIMEOUT_MS = 5000;

    private int connectTimeout = DEFAULT_TIMEOUT_MS;
    private int readTimeout = DEFAULT_TIMEOUT_MS;
    private final Map<String, String> requestHeaders = new HashMap<>();
    private boolean noProxy;

    public EcfHttpClient() {
        requestHeaders.put("User-Agent", USER_AGENT);
        requestHeaders.put("Accept-Encoding", "gzip,deflate");
    }

    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
    }

    public void setReadTimeout(int timeout) {
        this.readTimeout = timeout;
    }

    public void setRequestHeader(String name, String value) {
        if (value == null) {
            requestHeaders.remove(name);
        } else {
            requestHeaders.put(name, value);
        }
    }

    public void setNoProxy(boolean noProxy) {
        this.noProxy = noProxy;
    }

    /**
     * Download a file from the given URL to the specified output file.
     * This method blocks until the download completes or fails.
     *
     * @param url the URL to download from
     * @param outputFile the file to write to
     * @throws IOException if the download fails
     */
    public void downloadToFile(String url, File outputFile) throws IOException {
        // Create parent directories if needed
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Failed to create directory: " + parent);
        }

        // Download to a temp file first, then move to final location
        File tempFile = File.createTempFile("ecf-download-", ".tmp", parent);
        try {
            try (OutputStream out = Files.newOutputStream(tempFile.toPath())) {
                downloadToStream(url, out);
            }
            // Move temp file to final location
            Files.move(tempFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Clean up temp file on failure
            try {
                Files.deleteIfExists(tempFile.toPath());
            } catch (IOException ignored) {
                // Ignore cleanup errors
            }
            throw e;
        }
    }

    /**
     * Download content from URL and return as byte array.
     * This method blocks until the download completes or fails.
     *
     * @param url the URL to download from
     * @return the downloaded content as bytes
     * @throws IOException if the download fails
     */
    public byte[] downloadToBytes(String url) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        downloadToStream(url, out);
        return out.toByteArray();
    }

    public byte[] postJson(String url, String jsonPayload) throws IOException {
        IHttpClientFactory factory = getHttpClientFactory();
        HttpClientContext context = factory.newClientContext();
        RequestConfig.Builder requestConfig = newRequestConfig(factory, context);
        JREProxyHelper proxyHelper = null;

        try (CloseableHttpClient client = factory.newClient().build()) {
            proxyHelper = setupProxy(url, requestConfig);

            HttpPost post = new HttpPost(url);
            for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                post.setHeader(header.getKey(), header.getValue());
            }
            post.setHeader("Accept", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
            post.setHeader("Content-Type", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
            post.setEntity(new ByteArrayEntity(
                    jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    org.apache.hc.core5.http.ContentType.APPLICATION_JSON));
            post.setConfig(requestConfig.build());

            try (CloseableHttpResponse response = client.execute(post, context)) {
                HttpEntity entity = response.getEntity();
                byte[] responseBytes = entity == null ? new byte[0] : EntityUtils.toByteArray(entity);
                int statusCode = response.getCode();
                if (statusCode >= 200 && statusCode < 300) {
                    return responseBytes;
                }
                throw new EcfHttpStatusException("POST failed: HTTP " + statusCode, statusCode, null);
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + url, e);
        } finally {
            if (proxyHelper != null) {
                proxyHelper.dispose();
            }
        }
    }

    /**
     * Download content from URL and write to the given output stream.
     * This method blocks until the download completes or fails.
     *
     * @param url the URL to download from
     * @param outputStream the stream to write to
     * @throws IOException if the download fails
     */
    public void downloadToStream(String url, OutputStream outputStream) throws IOException {
        IRetrieveFileTransferContainerAdapter adapter = newRetrieveAdapter();
        if (adapter == null) {
            throw new IOException("ECF file transfer service is not available");
        }

        if (noProxy) {
            adapter.setProxy(Proxy.NO_PROXY);
        }

        // Prepare transfer options with headers
        Map<String, Object> options = new HashMap<>();
        options.put(IRetrieveFileTransferOptions.REQUEST_HEADERS, new HashMap<>(requestHeaders));
        options.put(IRetrieveFileTransferOptions.CONNECT_TIMEOUT, connectTimeout);
        options.put(IRetrieveFileTransferOptions.READ_TIMEOUT, readTimeout);

        try {
            URI uri = new URI(url);
            IFileID fileID = FileIDFactory.getDefault().createFileID(adapter.getRetrieveNamespace(), uri.toString());

            // Create a synchronous transfer handler
            SynchronousTransferHandler handler = new SynchronousTransferHandler(outputStream);

            // Start the transfer
            adapter.sendRetrieveRequest(fileID, handler, options);

            // Wait for completion (with timeout)
            handler.waitForCompletion((long) connectTimeout + readTimeout);

            // Check for errors
            Exception ex = handler.exception.get();
            if (ex != null) {
                throw toIOException("Download failed", ex);
            }

        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + url, e);
        } catch (IncomingFileTransferException e) {
            throw toIOException("Failed to initiate file transfer", e);
        } catch (FileCreateException e) {
            throw new IOException("Failed to create file ID: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    private IRetrieveFileTransferContainerAdapter newRetrieveAdapter() {
        BundleContext context = getBundleContext();
        if (context == null) {
            return null;
        }
        startBundle(context, "org.eclipse.ecf.provider.filetransfer.httpclient5");
        startBundle(context, "org.eclipse.ecf.provider.filetransfer");
        ServiceTracker<IRetrieveFileTransferFactory, IRetrieveFileTransferFactory> tracker =
                new ServiceTracker<>(context, IRetrieveFileTransferFactory.class, null);
        tracker.open();
        try {
            IRetrieveFileTransferFactory factory = tracker.waitForService(10000);
            return factory != null ? factory.newInstance() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            IRetrieveFileTransferFactory factory = tracker.getService();
            return factory != null ? factory.newInstance() : null;
        } finally {
            tracker.close();
        }
    }

    private IHttpClientFactory getHttpClientFactory() throws IOException {
        BundleContext context = getBundleContext();
        if (context == null) {
            throw new IOException("ECF httpclient5 service is not available");
        }
        if (!startBundle(context, "org.eclipse.ecf.provider.filetransfer.httpclient5")) {
            throw new IOException("ECF httpclient5 service is not available");
        }
        return org.eclipse.ecf.internal.provider.filetransfer.httpclient5.Activator.getDefault().getHttpClientFactory();
    }

    private BundleContext getBundleContext() {
        Bundle bundle = FrameworkUtil.getBundle(EcfHttpClient.class);
        if (bundle == null) {
            return null;
        }
        try {
            if (bundle.getBundleContext() == null) {
                bundle.start(Bundle.START_TRANSIENT);
            }
        } catch (BundleException e) {
            return null;
        }
        BundleContext context = bundle.getBundleContext();
        if (context == null) {
            return null;
        }
        return context;
    }

    private RequestConfig.Builder newRequestConfig(IHttpClientFactory factory, HttpClientContext context) {
        Map<String, Object> options = new HashMap<>();
        options.put(HttpClientOptions.RETRIEVE_CONNECTION_TIMEOUT_PROP, connectTimeout);
        options.put(HttpClientOptions.RETRIEVE_READ_TIMEOUT_PROP, readTimeout);

        RequestConfig.Builder requestConfig = factory.newRequestConfig(context, options);
        requestConfig.setConnectTimeout(connectTimeout, TimeUnit.MILLISECONDS);
        requestConfig.setResponseTimeout(readTimeout, TimeUnit.MILLISECONDS);
        return requestConfig;
    }

    private JREProxyHelper setupProxy(String url, RequestConfig.Builder requestConfig) throws IOException, URISyntaxException {
        if (noProxy) {
            requestConfig.setProxy(null);
            return null;
        }

        Proxy proxy = null;
        URL targetUrl = new URI(url).toURL();
        try {
            proxy = ProxySetupHelper.getSocksProxy(targetUrl);
            if (proxy == null) {
                proxy = ProxySetupHelper.getProxy(targetUrl.toExternalForm());
            }
        } catch (NoClassDefFoundError e) {
            org.eclipse.ecf.internal.provider.filetransfer.httpclient5.Activator.logNoProxyWarning(e);
        }

        if (proxy == null) {
            return null;
        }
        if (proxy.getType().equals(Proxy.Type.HTTP)) {
            ProxyAddress address = proxy.getAddress();
            requestConfig.setProxy(new HttpHost(address.getHostName(), address.getPort()));
            return null;
        }
        if (proxy.getType().equals(Proxy.Type.SOCKS)) {
            requestConfig.setProxy(null);
            JREProxyHelper proxyHelper = new JREProxyHelper();
            proxyHelper.setupProxy(proxy);
            return proxyHelper;
        }
        return null;
    }

    private boolean startBundle(BundleContext context, String symbolicName) {
        for (Bundle bundle : context.getBundles()) {
            if (symbolicName.equals(bundle.getSymbolicName())) {
                try {
                    bundle.start(Bundle.START_TRANSIENT);
                    return true;
                } catch (BundleException e) {
                    // The service tracker below will report the unavailable ECF service.
                    return false;
                }
            }
        }
        return false;
    }

    public int getStatusCode(String url) throws IOException {
        try {
            downloadToStream(url, OutputStream.nullOutputStream());
            return 200;
        } catch (EcfHttpStatusException e) {
            if (e.getStatusCode() > 0) {
                return e.getStatusCode();
            }
            throw e;
        }
    }

    private IOException toIOException(String message, Exception ex) {
        if (ex instanceof IncomingFileTransferException) {
            IncomingFileTransferException transferException = (IncomingFileTransferException) ex;
            return new EcfHttpStatusException(message + ": " + ex.getMessage(),
                    transferException.getErrorCode(), transferException);
        }
        return new IOException(message + ": " + ex.getMessage(), ex);
    }

    public static class EcfHttpStatusException extends IOException {
        private static final long serialVersionUID = 1L;

        private final int statusCode;

        EcfHttpStatusException(String message, int statusCode, Throwable cause) {
            super(message, cause);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    /**
     * Synchronous handler for ECF file transfer events.
     * This handles the asynchronous ECF events and blocks until completion.
     */
    private static class SynchronousTransferHandler implements IFileTransferListener {
        private final OutputStream outputStream;
        private final Object lock = new Object();
        private boolean completed = false;
        private final AtomicReference<Exception> exception = new AtomicReference<>();

        public SynchronousTransferHandler(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void handleTransferEvent(IFileTransferEvent event) {
            if (event instanceof IIncomingFileTransferReceiveStartEvent) {
                IIncomingFileTransferReceiveStartEvent startEvent = (IIncomingFileTransferReceiveStartEvent) event;
                try {
                    // Accept the transfer and start receiving
                    startEvent.receive(outputStream);
                } catch (IOException e) {
                    exception.set(e);
                    signalCompletion();
                }
            } else if (event instanceof IIncomingFileTransferReceiveDataEvent) {
                // Data is being received - nothing to do here as it goes directly to outputStream
                // Could add progress monitoring here if needed
            } else if (event instanceof IIncomingFileTransferReceiveDoneEvent) {
                IIncomingFileTransferReceiveDoneEvent doneEvent = (IIncomingFileTransferReceiveDoneEvent) event;
                if (doneEvent.getException() != null) {
                    exception.set(doneEvent.getException());
                }
                signalCompletion();
            }
        }

        private void signalCompletion() {
            synchronized (lock) {
                completed = true;
                lock.notifyAll();
            }
        }

        public void waitForCompletion(long timeoutMs) throws InterruptedException, IOException {
            synchronized (lock) {
                long startTime = System.currentTimeMillis();
                long remaining = timeoutMs;

                while (!completed && remaining > 0) {
                    lock.wait(remaining);
                    long elapsed = System.currentTimeMillis() - startTime;
                    remaining = timeoutMs - elapsed;
                }

                if (!completed) {
                    throw new IOException("Download timeout after " + timeoutMs + "ms");
                }
            }
        }
    }
}
