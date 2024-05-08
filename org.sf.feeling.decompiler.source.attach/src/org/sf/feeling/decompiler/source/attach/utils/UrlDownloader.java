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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.Delete;
import org.apache.tools.ant.taskdefs.Zip;
import org.sf.feeling.decompiler.util.Logger;

public class UrlDownloader {

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 5.1) AppleWebKit/535.7 (KHTML, like Gecko) Chrome/16.0.912.63 Safari/535.7"; //$NON-NLS-1$
	private String serviceUser;
	private String servicePassword;

	public String download(final String url) throws Exception {
		String result;
		if (url != null && url.startsWith("scm:")) //$NON-NLS-1$
		{
			throw new UnsupportedOperationException("download source from scm url is not supported"); //$NON-NLS-1$
		} else if (new File(url).exists()) {
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
			try (InputStream is = this.openConnectionCheckRedirects(conn)) {
				try (OutputStream os = FileUtils.openOutputStream(file)) {
					IOUtils.copy(is, os);
				}
			}
		} catch (Exception ex) {
			Logger.error(ex);
			file.delete();
			return file.getAbsolutePath();
		}
		return file.getAbsolutePath();
	}

	private InputStream openConnectionCheckRedirects(URLConnection c) throws IOException {
		int redirects = 0;
		InputStream in = null;
		boolean redir;
		do {
			if (c instanceof HttpURLConnection) {
				((HttpURLConnection) c).setInstanceFollowRedirects(false);
				c.setRequestProperty("User-Agent", //$NON-NLS-1$
						USER_AGENT);
			}
			in = c.getInputStream();
			redir = false;
			if (c instanceof HttpURLConnection) {
				final HttpURLConnection http = (HttpURLConnection) c;
				final int stat = http.getResponseCode();
				if (stat < 300 || stat > 307 || stat == 306 || stat == 304) {
					continue;
				}
				final URL base = http.getURL();
				final String loc = http.getHeaderField("Location"); //$NON-NLS-1$
				URL target = null;
				if (loc != null) {
					target = new URL(base, loc);
				}
				http.disconnect();
				if (target == null || (!target.getProtocol().equals("http") && !target.getProtocol().equals("https")) //$NON-NLS-1$ //$NON-NLS-2$
						|| redirects >= 5) {
					throw new SecurityException("illegal URL redirect"); //$NON-NLS-1$
				}
				redir = true;
				c = target.openConnection();
				c.setConnectTimeout(5000);
				c.setReadTimeout(5000);
				++redirects;
			}
		} while (redir);
		return in;
	}

	public static String trim(final String str) {
		return str == null ? null : str.trim();
	}

	public static String trimToEmpty(final String str) {
		return str == null ? "" : str.trim();
	}

	public static String strip(String str, final String stripChars) {
		str = stripStart(str, stripChars);
		return stripEnd(str, stripChars);
	}

	public static String stripStart(final String str, final String stripChars) {
		Objects.requireNonNull(stripChars);
		final int strLen = length(str);
		if (strLen == 0) {
			return str;
		}
		int start = 0;
		if (stripChars.isEmpty()) {
			return str;
		} else {
			while (start != strLen && stripChars.indexOf(str.charAt(start)) != -1) {
				start++;
			}
		}
		return str.substring(start);
	}

	public static String stripEnd(final String str, final String stripChars) {
		Objects.requireNonNull(stripChars);
		int end = length(str);
		if (end == 0) {
			return str;
		}

		if (stripChars.isEmpty()) {
			return str;
		} else {
			while (end != 0 && stripChars.indexOf(str.charAt(end - 1)) != -1) {
				end--;
			}
		}
		return str.substring(0, end);
	}

	public static int length(final CharSequence cs) {
		return cs == null ? 0 : cs.length();
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