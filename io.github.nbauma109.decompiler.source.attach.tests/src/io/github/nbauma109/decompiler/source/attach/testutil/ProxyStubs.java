/*******************************************************************************
 * Copyright (c) 2026 ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.testutil;

import java.net.URI;

import org.eclipse.core.net.proxy.IProxyChangeListener;
import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.runtime.CoreException;

/**
 * Minimal stub implementations of Eclipse proxy service interfaces for use in
 * unit tests that exercise proxy-aware code without requiring a running OSGi
 * container or real network proxy.
 */
public final class ProxyStubs {

    private ProxyStubs() {
        // Utility class – no instances
    }

    /** Minimal {@link IProxyData} stub backed by constructor-supplied values. */
    public static class StubProxyData implements IProxyData {
        private final String type;
        private String host;
        private int port;
        private String userId;
        private String password;

        public StubProxyData(String type, String host, int port, String userId, String password) {
            this.type = type;
            this.host = host;
            this.port = port;
            this.userId = userId;
            this.password = password;
        }

        @Override
        public String getType() { return type; }

        @Override
        public String getHost() { return host; }

        @Override
        public int getPort() { return port; }

        @Override
        public String getUserId() { return userId; }

        @Override
        public String getPassword() { return password; }

        @Override
        public boolean isRequiresAuthentication() { return userId != null && !userId.isEmpty(); }

        @Override
        public void setHost(String h) { this.host = h; }

        @Override
        public void setPort(int p) { this.port = p; }

        @Override
        public void setUserid(String u) { this.userId = u; }

        @Override
        public void setPassword(String pw) { this.password = pw; }

        @Override
        public void disable() { this.host = null; this.port = -1; this.userId = null; this.password = null; }
    }

    /** Minimal {@link IProxyService} stub that always returns the supplied array from {@code select()}. */
    public static class StubProxyService implements IProxyService {
        private final IProxyData[] data;

        public StubProxyService(IProxyData[] data) { this.data = data; }

        @Override
        public IProxyData[] select(URI uri) { return data; }

        @Override
        public IProxyData[] getProxyData() { return data; }

        @Override
        public IProxyData getProxyData(String type) { return null; }

        @Override
        public IProxyData[] getProxyDataForHost(String host) { return data; }

        @Override
        public IProxyData getProxyDataForHost(String host, String type) { return null; }

        @Override
        public boolean isProxiesEnabled() { return true; }

        @Override
        public boolean hasSystemProxies() { return false; }

        @Override
        public boolean isSystemProxiesEnabled() { return false; }

        @Override
        public void setProxiesEnabled(boolean e) { /* no-op stub */ }

        @Override
        public void setSystemProxiesEnabled(boolean e) { /* no-op stub */ }

        @Override
        public void setProxyData(IProxyData[] proxies) throws CoreException { /* no-op stub */ }

        @Override
        public String[] getNonProxiedHosts() { return new String[0]; }

        @Override
        public void setNonProxiedHosts(String[] hosts) throws CoreException { /* no-op stub */ }

        @Override
        public void addProxyChangeListener(IProxyChangeListener l) { /* no-op stub */ }

        @Override
        public void removeProxyChangeListener(IProxyChangeListener l) { /* no-op stub */ }
    }
}
