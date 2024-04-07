/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.sf.feeling.decompiler.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {

	public static String md5Hash(File file) {
		if (file != null) {
			try (InputStream fis = new FileInputStream(file)) {
				return hexDigestOfStream(fis, MessageDigest.getInstance("MD5"));
			} catch (IOException | NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	public static String sha1Hash(File file) {
		if (file != null) {
			try (InputStream fis = new FileInputStream(file)) {
				return hexDigestOfStream(fis, MessageDigest.getInstance("SHA-1"));
			} catch (IOException | NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	public static String md5Hash(String string) {
		if (string == null) {
			return null;
		}
		byte[] content = string.getBytes(StandardCharsets.UTF_8);
		if (content != null) {
			try {
				return hexDigestOfStream(new ByteArrayInputStream(content), MessageDigest.getInstance("MD5"));
			} catch (NoSuchAlgorithmException | IOException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	public static byte[] sha1Digest(File file) {
		String md5 = sha1Hash(file);
		if (md5 != null) {
			return md5.getBytes();
		}
		return null;
	}

	private static String hexDigestOfStream(InputStream in, MessageDigest digest) throws IOException {
		DigestInputStream din = new DigestInputStream(in, digest);
		byte[] buffer = new byte[4096 * 8];
		while (din.read(buffer) >= 0) {

		}
		return new BigInteger(1, digest.digest()).toString(16);
	}

}
