/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.finder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.sf.feeling.decompiler.source.attach.utils.UrlDownloader;
import org.sf.feeling.decompiler.util.HashUtils;
import org.sf.feeling.decompiler.util.Logger;

public class SourceCheck {

	public static boolean proposeSourceLink(String path, String url) throws IOException {
		boolean success = false;
		try {
			if (path != null && url != null && !path.isBlank() && !url.isBlank()) {
				path = path.trim();
				url = url.trim();
				final File file1 = new File(path);
				final List<String> classnames = getJavaFileNames(file1, ".class"); //$NON-NLS-1$
				final File file2 = download(url);
				final List<String> javanames = getJavaFileNames(file2, ".java"); //$NON-NLS-1$
				final boolean isSource = isSource(javanames, classnames);
				if (isSource) {
					final String origin = path;
					final String md5 = HashUtils.md5Hash(file1);
					final String sha1 = HashUtils.sha1Hash(file1);
					final String src_origin = url;
					final String src_md5 = HashUtils.md5Hash(file2);
					final String src_sha1 = HashUtils.md5Hash(file2);
					final String src_urls = url;
					postToServer(origin, md5, sha1, src_origin, src_md5, src_sha1, src_urls);
					success = true;
				}
			}
		} catch (Exception e) {
			Logger.debug(e);
		}
		return success;
	}

	private static void postToServer(final String origin, final String md5, final String sha1, final String src_origin,
			final String src_md5, final String src_sha1, final String src_urls)
			throws UnsupportedEncodingException, MalformedURLException, IOException {
		String data = "origin=" + URLEncoder.encode(origin, "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
		data = String.valueOf(data) + "&md5=" + md5; //$NON-NLS-1$
		data = String.valueOf(data) + "&sha1=" + sha1; //$NON-NLS-1$
		data = String.valueOf(data) + "&src_origin=" + URLEncoder.encode(src_origin, "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
		data = String.valueOf(data) + "&src_md5=" + src_md5; //$NON-NLS-1$
		data = String.valueOf(data) + "&src_sha1=" + src_sha1; //$NON-NLS-1$
		data = String.valueOf(data) + "&src_urls=" + URLEncoder.encode(src_urls, "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
		final URL url2 = new URL("https://javasourceattacher2.appspot.com/rest/libraries"); //$NON-NLS-1$
		final HttpURLConnection conn = (HttpURLConnection) url2.openConnection();
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		conn.setDoOutput(true);
		final OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
		wr.write(data);
		wr.flush();
		final BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		while (rd.readLine() != null) {
		}
		wr.close();
		rd.close();
		if (conn.getResponseCode() != 200) {
			throw new IllegalStateException("Cannot submit " + src_origin); //$NON-NLS-1$
		}
	}

	private static File download(final String str) throws Exception {
		return new File(new UrlDownloader().download(str));
	}

	public static boolean isWrongSource(final File srcFile, final File binFile) throws IOException {
		final List<String> classnames = getJavaFileNames(binFile, ".class"); //$NON-NLS-1$
		final List<String> javanames = getJavaFileNames(srcFile, ".java"); //$NON-NLS-1$
		final boolean isWrongSource = !classnames.isEmpty() && javanames.isEmpty();
		return isWrongSource;
	}

	private static boolean isSource(final List<String> javanames, final List<String> classnames) {
		final Set<String> javanames2 = new HashSet<String>();
		for (final String javaname : javanames) {
			final String name = FilenameUtils.getName(javaname);
			if (name.endsWith(".java")) //$NON-NLS-1$
			{
				javanames2.add(name.substring(0, name.length() - ".java".length())); //$NON-NLS-1$
			}
		}
		final Set<String> classnames2 = new HashSet<String>();
		for (final String classname : classnames) {
			final String name2 = FilenameUtils.getName(classname);
			if (name2.endsWith(".class") && !name2.contains("$")) //$NON-NLS-1$ //$NON-NLS-2$
			{
				classnames2.add(name2.substring(0, name2.length() - ".class".length())); //$NON-NLS-1$
			}
		}
		javanames2.retainAll(classnames2);
		final int commonCount = javanames2.size();
		return commonCount / classnames2.size() >= 0.5;
	}

	private static List<String> getJavaFileNames(final File file, final String ext) throws IOException {
		final List<String> classnames = new ArrayList<String>();
		final ZipFile zf = new ZipFile(file);
		try {
			final Enumeration<ZipArchiveEntry> entries = zf.getEntries();
			while (entries.hasMoreElements()) {
				final ZipArchiveEntry entry = entries.nextElement();
				final String entryName = entry.getName();
				if (entryName.endsWith(ext)) {
					classnames.add(entryName);
				}
			}
		} finally {
			zf.close();
		}
		zf.close();
		return classnames;
	}
}