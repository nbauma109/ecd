/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
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
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {

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

    private static String hexDigestOfStream(InputStream in, MessageDigest digest) throws IOException {
        DigestInputStream din = new DigestInputStream(in, digest);
        byte[] buffer = new byte[4096 * 8];
        while (din.read(buffer) >= 0) {

        }
        return new BigInteger(1, digest.digest()).toString(16);
    }

}
