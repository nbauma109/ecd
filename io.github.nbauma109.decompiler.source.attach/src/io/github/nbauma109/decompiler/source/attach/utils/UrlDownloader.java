/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.utils;

import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.StandardCopyOption;
import org.apache.commons.io.file.PathUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.Delete;
import org.apache.tools.ant.taskdefs.Zip;
import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import io.github.nbauma109.decompiler.source.attach.SourceAttachPlugin;
import io.github.nbauma109.decompiler.util.Logger;

public class UrlDownloader {

    private String serviceUser;
    private String servicePassword;

    public String download(final String url) throws IOException {
        return download(url, null);
    }

    public String download(final String url, final File targetFile) throws IOException {
        String result;
        if (url != null && url.startsWith("scm:")) //$NON-NLS-1$
        {
            throw new UnsupportedOperationException("download source from scm url is not supported"); //$NON-NLS-1$
        }
        if (new File(url).exists()) {
            result = url;
        } else {
            result = this.downloadFromUrl(url, targetFile);
        }
        return result;
    }

    public void zipFolder(final File srcFolder, final File destZipFile) {
        final Zip zipper = new Zip();
        zipper.setLevel(1);
        zipper.setDestFile(destZipFile);
        zipper.setBasedir(srcFolder);
        zipper.setIncludes("**/*.java"); //$NON-NLS-1$
        zipper.setTaskName("zip"); //$NON-NLS-1$
        zipper.setTaskType("zip"); //$NON-NLS-1$
        zipper.setProject(new Project());
        zipper.setOwningTarget(new Target());
        zipper.execute();
    }

    public void delete(final File folder) {
        final Delete delete = new Delete();
        delete.setDir(folder);
        delete.setTaskName("delete"); //$NON-NLS-1$
        delete.setTaskType("delete"); //$NON-NLS-1$
        delete.setProject(new Project());
        delete.setOwningTarget(new Target());
        delete.execute();
    }

    private String downloadFromUrl(final String url, final File targetFile) throws IOException {
        final File file = targetFile != null ? targetFile : File.createTempFile(SourceConstants.TEMP_SOURCE_PREFIX, ".tmp"); //$NON-NLS-1$
        try {
            // Create parent directories if needed
            if (targetFile != null) {
                final File parent = file.getParentFile();
                if ((parent != null && !parent.exists()) && (!parent.mkdirs() && !parent.exists())) {
                    throw new IOException("Failed to create directory: " + parent);
                }
            }

            // Get Eclipse proxy service via activator (ServiceTracker, no OSGi service leak)
            SourceAttachPlugin defaultPlugin = SourceAttachPlugin.getDefault();
            IProxyService proxyService = defaultPlugin != null ? defaultPlugin.getProxyService() : null;

            URLConnection conn = openConnectionWithProxy(url, proxyService);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            PathUtils.copy(conn::getInputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ex) {
            Logger.error(ex);
            file.delete();
        }
        return file.getAbsolutePath();
    }

    private URLConnection openConnectionWithProxy(final String url, IProxyService proxyService) throws IOException {
        try {
            URI uri = new URI(url);
            Proxy proxy = ProxyUtil.getProxy(uri, proxyService);
            URLConnection conn = uri.toURL().openConnection(proxy);
            if (conn instanceof HttpURLConnection httpURLConnection) {
                setConnectionAuthenticator(httpURLConnection, uri, proxyService);
            }
            return conn;
        } catch (URISyntaxException | IOException e) {
            Logger.debug(e);
            return URI.create(url).toURL().openConnection();
        }
    }

    private void setConnectionAuthenticator(HttpURLConnection conn, URI uri, IProxyService proxyService) {
        IProxyData proxyData = ProxyUtil.getProxyData(uri, proxyService);
        final String proxyUser = proxyData != null ? proxyData.getUserId() : null;
        final char[] proxyPass = proxyData != null && proxyData.getPassword() != null
                ? proxyData.getPassword().toCharArray() : null;
        if ((proxyUser != null && !proxyUser.isEmpty() && proxyPass != null)
                || (serviceUser != null && servicePassword != null)) {
            conn.setAuthenticator(buildAuthenticator(proxyUser, proxyPass));
        }
    }

    private Authenticator buildAuthenticator(String proxyUser, char[] proxyPass) {
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == Authenticator.RequestorType.PROXY
                        && proxyUser != null && !proxyUser.isEmpty() && proxyPass != null) {
                    return new PasswordAuthentication(proxyUser, proxyPass);
                }
                if (getRequestorType() == Authenticator.RequestorType.SERVER
                        && serviceUser != null && servicePassword != null) {
                    return new PasswordAuthentication(serviceUser, servicePassword.toCharArray());
                }
                return null;
            }
        };
    }

    /**
     * @return the serviceUser
     */
    public String getServiceUser() {
        return serviceUser;
    }

    /**
     * @param serviceUser the serviceUser to set
     */
    public void setServiceUser(String serviceUser) {
        this.serviceUser = serviceUser;
    }

    /**
     * @return the servicePassword
     */
    public String getServicePassword() {
        return servicePassword;
    }

    /**
     * @param servicePassword the servicePassword to set
     */
    public void setServicePassword(String servicePassword) {
        this.servicePassword = servicePassword;
    }
}
