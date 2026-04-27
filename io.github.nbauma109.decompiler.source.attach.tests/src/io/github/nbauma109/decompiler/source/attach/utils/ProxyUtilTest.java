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

import java.net.Proxy;
import java.net.URI;

import org.junit.Test;

public class ProxyUtilTest {

    @Test
    public void getProxyWithNullServiceReturnsNoProxy() throws Exception {
        URI uri = new URI("http://example.com");
        Proxy proxy = ProxyUtil.getProxy(uri, null);

        assertNotNull(proxy);
        assertEquals(Proxy.NO_PROXY, proxy);
    }

    @Test
    public void getProxyDoesNotThrowWithValidUri() throws Exception {
        // This test verifies that getProxy gracefully handles proxy service errors
        URI uri = new URI("http://example.com");

        // Should not throw even when proxy service is null
        Proxy proxy = ProxyUtil.getProxy(uri, null);
        assertNotNull(proxy);
    }
}
