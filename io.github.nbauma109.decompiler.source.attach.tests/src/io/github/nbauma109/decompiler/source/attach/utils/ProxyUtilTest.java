/*******************************************************************************
 * Copyright (c) 2026 ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

import io.github.nbauma109.decompiler.source.attach.testutil.ProxyStubs;
import io.github.nbauma109.decompiler.source.attach.testutil.ProxyStubs.StubProxyData;
import io.github.nbauma109.decompiler.source.attach.testutil.ProxyStubs.StubProxyService;

import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ProxyUtilTest {

    private static final String EXAMPLE_URL = "http://example.com";
    private static final String PROXY_HOST = "proxy.example.com";

    /** Saves and restores the global authenticator so tests stay isolated. */
    private Authenticator savedAuthenticator;

    @Before
    public void saveAuthenticator() {
        savedAuthenticator = Authenticator.getDefault();
    }

    @After
    public void restoreAuthenticator() {
        Authenticator.setDefault(savedAuthenticator);
    }

    // -----------------------------------------------------------------------
    // getProxy()
    // -----------------------------------------------------------------------

    @Test
    public void getProxyNullServiceReturnsNoProxy() {
        Proxy proxy = ProxyUtil.getProxy(URI.create(EXAMPLE_URL), null);

        assertNotNull(proxy);
        assertSame(Proxy.NO_PROXY, proxy);
    }

    @Test
    public void getProxyServiceReturnsNoDataReturnsNoProxy() {
        IProxyService service = new StubProxyService(new IProxyData[0]);

        Proxy proxy = ProxyUtil.getProxy(URI.create(EXAMPLE_URL), service);

        assertSame(Proxy.NO_PROXY, proxy);
    }

    @Test
    public void getProxyServiceReturnsProxyReturnsCorrectHostAndPort() {
        IProxyData proxyData = new StubProxyData(IProxyData.HTTP_PROXY_TYPE, PROXY_HOST, 8080, null, null);
        IProxyService service = new StubProxyService(new IProxyData[] { proxyData });

        Proxy proxy = ProxyUtil.getProxy(URI.create(EXAMPLE_URL), service);

        assertEquals(Proxy.Type.HTTP, proxy.type());
        InetSocketAddress addr = (InetSocketAddress) proxy.address();
        assertEquals(PROXY_HOST, addr.getHostName());
        assertEquals(8080, addr.getPort());
    }

    @Test
    public void getProxyDoesNotInstallGlobalAuthenticator() {
        IProxyData proxyData = new StubProxyData(IProxyData.HTTP_PROXY_TYPE, PROXY_HOST, 8080, "user", "pass");
        IProxyService service = new StubProxyService(new IProxyData[] { proxyData });
        Authenticator.setDefault(null);

        ProxyUtil.getProxy(URI.create(EXAMPLE_URL), service);

        // getProxy() must not touch the JVM-global authenticator
        assertNull("getProxy() must not call Authenticator.setDefault()", Authenticator.getDefault());
    }

    // -----------------------------------------------------------------------
    // getProxyData()
    // -----------------------------------------------------------------------

    @Test
    public void getProxyDataNullServiceReturnsNull() {
        assertNull(ProxyUtil.getProxyData(URI.create(EXAMPLE_URL), null));
    }

    @Test
    public void getProxyDataServiceReturnsNoDataReturnsNull() {
        IProxyService service = new StubProxyService(new IProxyData[0]);
        assertNull(ProxyUtil.getProxyData(URI.create(EXAMPLE_URL), service));
    }

    @Test
    public void getProxyDataServiceReturnsProxyReturnsProxyData() {
        IProxyData proxyData = new StubProxyData(IProxyData.HTTP_PROXY_TYPE, PROXY_HOST, 3128, "alice", "secret");
        IProxyService service = new StubProxyService(new IProxyData[] { proxyData });

        IProxyData result = ProxyUtil.getProxyData(URI.create(EXAMPLE_URL), service);

        assertNotNull(result);
        assertEquals(PROXY_HOST, result.getHost());
        assertEquals(3128, result.getPort());
        assertEquals("alice", result.getUserId());
        assertEquals("secret", result.getPassword());
    }

    @Test
    public void getProxyDataSocksProxyReturnsNull() {
        IProxyData proxyData = new StubProxyData(IProxyData.SOCKS_PROXY_TYPE, "socks.example.com", 1080, null, null);
        IProxyService service = new StubProxyService(new IProxyData[] { proxyData });

        assertNull("SOCKS proxies should not be returned", ProxyUtil.getProxyData(URI.create("socks://example.com"), service));
    }
}
