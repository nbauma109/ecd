/*******************************************************************************
 * Copyright (c) 2026 ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.utils;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;

import io.github.nbauma109.decompiler.util.Logger;

/**
 * Utility class for obtaining proxy settings from Eclipse's IProxyService.
 * Callers are responsible for applying per-connection authentication using
 * the IProxyData returned by {@link #getProxyData(URI, IProxyService)}.
 */
public class ProxyUtil {

    private ProxyUtil() {
        // Utility class
    }

    /**
     * Returns a {@link Proxy} for the given URI from Eclipse's proxy service.
     * Has no JVM-global side effects. Returns {@link Proxy#NO_PROXY} if no
     * suitable proxy is configured.
     *
     * @param uri the URI to connect to
     * @param proxyService the Eclipse proxy service (can be null)
     * @return a Proxy object or Proxy.NO_PROXY
     */
    public static Proxy getProxy(URI uri, IProxyService proxyService) {
        try {
            IProxyData proxyData = getProxyData(uri, proxyService);
            if (proxyData == null) {
                return Proxy.NO_PROXY;
            }
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyData.getHost(), proxyData.getPort()));
        } catch (RuntimeException e) {
            Logger.debug(e);
            return Proxy.NO_PROXY;
        }
    }

    /**
     * Returns validated {@link IProxyData} for the given URI, or {@code null} if
     * no HTTP/HTTPS proxy is configured.
     *
     * <p>Callers should use the returned object to configure per-connection proxy
     * authentication via {@code HttpURLConnection.setAuthenticator()} rather than
     * the JVM-global {@code Authenticator.setDefault()}.</p>
     *
     * <p><strong>Proxy Detection Mechanism:</strong></p>
     * <p>This method leverages Eclipse's IProxyService to detect proxy configuration.
     * The IProxyService.select(uri) method automatically detects proxy settings based on
     * Eclipse's proxy configuration (Window &gt; Preferences &gt; General &gt; Network Connections).
     * In "Native" mode, Eclipse reads proxy settings from the operating system:</p>
     * <ul>
     *   <li><strong>Windows:</strong> Reads from Internet Explorer/Edge proxy settings via WinINet API</li>
     *   <li><strong>macOS:</strong> Reads from System Preferences &gt; Network &gt; Advanced &gt; Proxies</li>
     *   <li><strong>Linux:</strong> Reads from GNOME/KDE proxy settings or environment variables</li>
     * </ul>
     * <p>When Eclipse is in "Native" mode, the returned IProxyData may contain credentials
     * sourced from the OS credential store (Windows Credential Manager, macOS Keychain, etc.).</p>
     *
     * @param uri the URI to get proxy data for
     * @param proxyService the Eclipse proxy service (can be null)
     * @return validated IProxyData or null if no valid proxy is available
     */
    public static IProxyData getProxyData(URI uri, IProxyService proxyService) {
        if (proxyService == null) {
            return null;
        }
        // Query Eclipse's proxy service - this automatically detects system proxy configuration
        // and returns appropriate proxy settings for the given URI
        IProxyData[] proxyDataArray = proxyService.select(uri);

        if (proxyDataArray == null || proxyDataArray.length == 0) {
            return null;
        }

        IProxyData proxyData = proxyDataArray[0];

        if (proxyData == null || IProxyData.SOCKS_PROXY_TYPE.equals(proxyData.getType())) {
            // SOCKS proxy is handled differently and not supported in this implementation
            return null;
        }

        String proxyHost = proxyData.getHost();
        int proxyPort = proxyData.getPort();

        if (proxyHost == null || proxyHost.isEmpty() || proxyPort < 0) {
            return null;
        }

        return proxyData;
    }
}
