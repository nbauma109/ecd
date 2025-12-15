/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.utils;

import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.StandardCopyOption;
import org.apache.commons.io.file.PathUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.Delete;
import org.apache.tools.ant.taskdefs.Zip;
import org.sf.feeling.decompiler.util.Logger;

public class UrlDownloader {

    private String serviceUser;
    private String servicePassword;

    public String download(final String url) throws Exception {
        String result;
        if (url != null && url.startsWith("scm:")) //$NON-NLS-1$
        {
            throw new UnsupportedOperationException("download source from scm url is not supported"); //$NON-NLS-1$
        }
        if (new File(url).exists()) {
            result = url;
        } else {
            result = this.downloadFromUrl(url);
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

    private String downloadFromUrl(final String url) throws IOException {
        final File file = File.createTempFile(SourceConstants.TEMP_SOURCE_PREFIX, ".tmp"); //$NON-NLS-1$
        try {
            final URLConnection conn = new URL(url).openConnection();
            if (serviceUser != null && servicePassword != null) {
                ((HttpURLConnection) conn).setAuthenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(serviceUser, servicePassword.toCharArray());
                    }
                });
            }
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            PathUtils.copy(conn::getInputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
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
}