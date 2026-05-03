/*******************************************************************************
 * (C) 2017 cnfree (@cnfree)
 * (C) 2017 Pascal Bihler
 * (C) 2021 Jan S. (@jpstotz)
 * (C) 2022-2026 Nicolas Baumann (@nbauma109)
 * (C) 2026 Claude (@Claude)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.Delete;
import org.apache.tools.ant.taskdefs.Zip;

import io.github.nbauma109.decompiler.util.EcfHttpClient;
import io.github.nbauma109.decompiler.util.Logger;

/**
 * Downloads files from URLs using Eclipse ECF for transparent proxy authentication support.
 */
public class UrlDownloader {

    private String serviceUser;
    private String servicePassword;
    private boolean noProxy;

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
            if (url.startsWith("file:")) { //$NON-NLS-1$
                // For file:// URLs use standard Java I/O; no ECF needed
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                    throw new IOException("Failed to create directory: " + parent);
                }
                try (InputStream is = URI.create(url).toURL().openStream()) {
                    Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                // Use ECF for HTTP/HTTPS downloads with transparent proxy authentication
                EcfHttpClient client = new EcfHttpClient();
                client.setConnectTimeout(5000);
                client.setReadTimeout(5000);
                client.setNoProxy(noProxy);
                if (serviceUser != null && servicePassword != null) {
                    String auth = serviceUser + ":" + servicePassword;
                    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                    client.setRequestHeader("Authorization", "Basic " + encodedAuth); //$NON-NLS-1$ //$NON-NLS-2$
                }
                client.downloadToFile(url, file);
            }
        } catch (IOException | RuntimeException ex) {
            Logger.error(ex);
            file.delete();
        }
        return file.getAbsolutePath();
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

    public void setNoProxy(boolean noProxy) {
        this.noProxy = noProxy;
    }
}
