/*******************************************************************************
 * © 2017 Chen Chao (@cnfree)
 * © 2017 Pascal Bihler (@pbi-qfs)
 * © 2021-2024 Jan Peter Stotz (@jpstotz)
 * © 2025-2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private HashUtils() {
    }

    public static String sha1Hash(File file) {
        return hash(file, "SHA-1"); //$NON-NLS-1$
    }

    public static String sha256Hash(File file) {
        return hash(file, "SHA-256"); //$NON-NLS-1$
    }

    private static String hash(File file, String algorithm) {
        if (file != null) {
            try (InputStream fis = new FileInputStream(file)) {
                return hexDigestOfStream(fis, MessageDigest.getInstance(algorithm));
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to compute " + algorithm + " for " + file, e); //$NON-NLS-1$ //$NON-NLS-2$
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(algorithm + " algorithm is not available", e); //$NON-NLS-1$
            }
        }
        return null;
    }

    private static String hexDigestOfStream(InputStream in, MessageDigest digest) throws IOException {
        try (DigestInputStream din = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[4096 * 8];
            while (din.read(buffer) >= 0) {
                // consume stream to update digest
            }
        }
        byte[] hash = digest.digest();
        char[] hex = new char[hash.length * 2];
        for (int i = 0; i < hash.length; i++) {
            int v = hash[i] & 0xFF;
            hex[i * 2] = HEX[v >>> 4];
            hex[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(hex);
    }

}
