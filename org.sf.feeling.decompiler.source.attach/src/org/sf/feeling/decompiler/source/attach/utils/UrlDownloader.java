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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.scm.ScmBranch;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmRevision;
import org.apache.maven.scm.ScmTag;
import org.apache.maven.scm.ScmVersion;
import org.apache.maven.scm.manager.BasicScmManager;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.provider.accurev.AccuRevScmProvider;
import org.apache.maven.scm.provider.bazaar.BazaarScmProvider;
import org.apache.maven.scm.provider.clearcase.ClearCaseScmProvider;
import org.apache.maven.scm.provider.cvslib.cvsexe.CvsExeScmProvider;
import org.apache.maven.scm.provider.cvslib.cvsjava.CvsJavaScmProvider;
import org.apache.maven.scm.provider.git.jgit.JGitScmProvider;
import org.apache.maven.scm.provider.hg.HgScmProvider;
import org.apache.maven.scm.provider.jazz.JazzScmProvider;
import org.apache.maven.scm.provider.local.LocalScmProvider;
import org.apache.maven.scm.provider.perforce.PerforceScmProvider;
import org.apache.maven.scm.provider.starteam.StarteamScmProvider;
import org.apache.maven.scm.provider.svn.svnexe.SvnExeScmProvider;
import org.apache.maven.scm.provider.synergy.SynergyScmProvider;
import org.apache.maven.scm.provider.vss.VssScmProvider;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.Delete;
import org.apache.tools.ant.taskdefs.Zip;
import org.sf.feeling.decompiler.util.HashUtils;
import org.sf.feeling.decompiler.util.Logger;

public class UrlDownloader {

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 5.1) AppleWebKit/535.7 (KHTML, like Gecko) Chrome/16.0.912.63 Safari/535.7"; //$NON-NLS-1$

	public String download(final String url) throws Exception {
		String result;
		if (url != null && url.startsWith("scm:")) //$NON-NLS-1$
		{
			result = this.downloadFromScm(url);
		} else if (new File(url).exists()) {
			result = url;
		} else {
			result = this.downloadFromUrl(url);
		}
		return result;
	}

	/*
	 * TODO: Documentation and tests - What use case triggers a download from an scm
	 * source ?
	 * 
	 */
	private String downloadFromScm(String url) throws Exception {
		if (url.indexOf("cvs:pserver:") != -1 && url.indexOf("@") == -1) //$NON-NLS-1$ //$NON-NLS-2$
		{
			url = url.replace("cvs:pserver:", "cvs:pserver:anonymous:@"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		String urlMD5 = HashUtils.md5Hash(url);
		final File tmpDir = new File(System.getProperty("java.io.tmpdir")); //$NON-NLS-1$
		final File checkoutDirectory = new File(tmpDir, SourceConstants.TEMP_SOURCE_PREFIX + "_" + urlMD5); //$NON-NLS-1$
		final File file = new File(tmpDir, SourceConstants.TEMP_SOURCE_PREFIX + "_" + urlMD5 + ".zip"); //$NON-NLS-1$ //$NON-NLS-2$
		if (!file.exists()) {
			if (!checkoutDirectory.exists() || checkoutDirectory.list().length == 0
					|| (checkoutDirectory.list().length == 1 && checkoutDirectory.list()[0].startsWith("."))) //$NON-NLS-1$
			{
				final ScmManager scmManager = new BasicScmManager();
				scmManager.setScmProvider("accurev", new AccuRevScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("bazaar", new BazaarScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("clearcase", new ClearCaseScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("hg", new HgScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("local", new LocalScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("perforce", new PerforceScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("cvs_native", new CvsExeScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("cvs", new CvsJavaScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("git", new JGitScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("svn", new SvnExeScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("starteam", new StarteamScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("synergy", new SynergyScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("vss", new VssScmProvider()); //$NON-NLS-1$
				scmManager.setScmProvider("jazz", new JazzScmProvider()); //$NON-NLS-1$
				String scmUrl;
				ScmVersion scmVersion;
				int idx = url.indexOf('#');
				if (idx != -1) {
					scmUrl = trimToEmpty(url.substring(0, idx));
					final String fragment = trimToEmpty(url.substring(idx + 1));
					int fragmentIndex = fragment.indexOf('=');
					if (fragmentIndex != -1) {
						final String version = trim(fragment.substring(fragmentIndex + 1));
						final String type = trim(fragment.substring(0, fragmentIndex));
						if ("tag".equals(type)) //$NON-NLS-1$
						{
							scmVersion = new ScmTag(version);
						} else if ("branch".equals(type)) //$NON-NLS-1$
						{
							scmVersion = new ScmBranch(version);
						} else if ("revision".equals(type)) //$NON-NLS-1$
						{
							scmVersion = new ScmRevision(version);
						} else {
							if (!"commitId".equals(type)) //$NON-NLS-1$
							{
								throw new IllegalArgumentException("'" + type + "' version type isn't known."); //$NON-NLS-1$ //$NON-NLS-2$
							}
							scmVersion = new ScmRevision(version);
						}
					} else {
						scmVersion = new ScmTag(fragment);
					}
				} else {
					int semiColonIdx = url.indexOf(';');
					if (semiColonIdx != -1) {
						scmUrl = trimToEmpty(url.substring(0, semiColonIdx));
						final String fragment = trimToEmpty(url.substring(semiColonIdx + 1));
						scmVersion = null;
						if (fragment.indexOf('=') != -1) {
							final String[] properties = fragment.split(";");
							String[] array;
							for (int length = (array = properties).length, i = 0; i < length; ++i) {
								final String property = array[i];
								final String[] versionTypeAndVersion2 = property.split("=");
								final String version2 = strip(versionTypeAndVersion2[1], " \""); //$NON-NLS-1$
								final String type2 = trim(versionTypeAndVersion2[0]);
								if ("tag".equals(type2)) //$NON-NLS-1$
								{
									scmVersion = new ScmTag(version2);
									break;
								}
								if ("branch".equals(type2)) //$NON-NLS-1$
								{
									scmVersion = new ScmBranch(version2);
									break;
								}
								if ("revision".equals(type2)) //$NON-NLS-1$
								{
									scmVersion = new ScmRevision(version2);
									break;
								}
								if ("commitId".equals(type2)) //$NON-NLS-1$
								{
									scmVersion = new ScmRevision(version2);
									break;
								}
							}
						} else {
							scmVersion = new ScmTag(fragment);
						}
					} else {
						scmUrl = url;
						scmVersion = null;
					}
				}
				if (!checkoutDirectory.exists()) {
					checkoutDirectory.mkdir();
				}
				final ScmRepository repository = scmManager.makeScmRepository(scmUrl);

				try {
					scmManager.checkOut(repository, new ScmFileSet(checkoutDirectory), scmVersion);
				} catch (Exception e) {
					Logger.debug(e);
					if (checkoutDirectory.exists()) {
						FileUtils.deleteDirectory(checkoutDirectory);
					}
				}
			}
			if (checkoutDirectory.exists() && checkoutDirectory.list().length > 0) {
				this.zipFolder(checkoutDirectory, file);
				FileUtils.deleteDirectory(checkoutDirectory);
			}
		}
		String result = null;
		if (file.exists()) {
			result = file.getAbsolutePath();
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
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);
			try (InputStream is = this.openConnectionCheckRedirects(conn)) {
				try (OutputStream os = FileUtils.openOutputStream(file)) {
					IOUtils.copy(is, os);
				}
			}
		} catch (Exception ex) {
			file.delete();
			return file.getAbsolutePath();
		}
		final String result = file.getAbsolutePath();
		return result;
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
}