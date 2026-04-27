/*******************************************************************************
 * Copyright (c) 2026 ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.utils;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.net.URLConnection;

import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;

import io.github.nbauma109.decompiler.util.Logger;

/**
 * Utility class for configuring URLConnection with Eclipse proxy settings.
 * Supports "Native" mode proxy configuration with authentication.
 */
public class ProxyUtil {

    private ProxyUtil() {
        // Utility class
    }

    /**
     * Configures the connection with proxy settings from Eclipse proxy service.
     * This method supports authenticated proxies in Native mode.
     *
     * @param connection the URLConnection to configure
     * @param uri the URI being accessed
     * @param proxyService the Eclipse proxy service (can be null)
     */
    public static void configureProxy(URLConnection connection, URI uri, IProxyService proxyService) {
        if (proxyService == null) {
            return;
        }

        try {
            IProxyData proxyData = getValidProxyData(uri, proxyService);
            if (proxyData == null) {
                return;
            }

            String proxyHost = proxyData.getHost();
            int proxyPort = proxyData.getPort();

            setupProxyAuthentication(proxyData, proxyHost, proxyPort);

            // Set proxy properties for the connection
            System.setProperty("http.proxyHost", proxyHost);
            System.setProperty("http.proxyPort", String.valueOf(proxyPort));
            System.setProperty("https.proxyHost", proxyHost);
            System.setProperty("https.proxyPort", String.valueOf(proxyPort));

        } catch (Exception e) {
            Logger.debug(e);
        }
    }

    /**
     * Opens a connection with proxy support.
     *
     * @param uri the URI to connect to
     * @param proxyService the Eclipse proxy service (can be null)
     * @return a Proxy object or Proxy.NO_PROXY
     */
    public static Proxy getProxy(URI uri, IProxyService proxyService) {
        if (proxyService == null) {
            return Proxy.NO_PROXY;
        }

        try {
            IProxyData proxyData = getValidProxyData(uri, proxyService);
            if (proxyData == null) {
                return Proxy.NO_PROXY;
            }

            String proxyHost = proxyData.getHost();
            int proxyPort = proxyData.getPort();

            setupProxyAuthentication(proxyData, proxyHost, proxyPort);

            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));

        } catch (Exception e) {
            Logger.debug(e);
            return Proxy.NO_PROXY;
        }
    }

    /**
     * Retrieves and validates proxy data for the given URI.
     *
     * @param uri the URI to get proxy data for
     * @param proxyService the Eclipse proxy service
     * @return validated IProxyData or null if no valid proxy is available
     */
    private static IProxyData getValidProxyData(URI uri, IProxyService proxyService) {
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

    /**
     * Sets up proxy authentication if credentials are available.
     *
     * @param proxyData the proxy data containing credentials
     * @param proxyHost the proxy host
     * @param proxyPort the proxy port
     */
    private static void setupProxyAuthentication(IProxyData proxyData, String proxyHost, int proxyPort) {
        String proxyUser = proxyData.getUserId();
        String proxyPassword = proxyData.getPassword();

        if (proxyUser != null && !proxyUser.isEmpty() && proxyPassword != null) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() == RequestorType.PROXY) {
                        String requestingHost = getRequestingHost();
                        int requestingPort = getRequestingPort();

                        if (proxyHost.equals(requestingHost) && proxyPort == requestingPort) {
                            return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
                        }
                    }
                    return null;
                }
            });
        }
    }
}
