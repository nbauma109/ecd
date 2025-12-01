/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JarClassExtractor {

    /**
     * extracts class files from jar/zip archive to specified path. See
     * <code>IDecompiler</code> documentation for the format of parameters.
     */
    public static void extract(String archivePath, String packege, String className, boolean inner, String to)
            throws IOException {
        try (ZipFile archive = new ZipFile(archivePath)) {
            List<ZipEntry> entries = findRelevant(archive, packege, className, inner);
            byte[] buffer = new byte[1024 * 16];
            String outFile;
            int lastSep;
            int amountRead;

            for (ZipEntry entry : entries) {
                outFile = entry.getName();
                if ((lastSep = outFile.lastIndexOf('/')) != -1)
                    outFile = outFile.substring(lastSep);

                try (InputStream in = archive.getInputStream(entry)) {
                    if (in == null)
                        throw new IOException("Zip file entry <" //$NON-NLS-1$
                                + entry.getName() + "> not found"); //$NON-NLS-1$
                    Path outPath = Paths.get(to + File.separator + outFile);
                    if (!outPath.normalize().startsWith(Paths.get(to))) {
                        throw new IOException("Bad zip entry");
                    }
                    try (OutputStream out = Files.newOutputStream(outPath)) {
                        while ((amountRead = in.read(buffer)) != -1)
                            out.write(buffer, 0, amountRead);
                    }
                }
            }
        }
    }

    private static List<ZipEntry> findRelevant(ZipFile archive, String packege, String className, boolean inner) {
        String entryName = (packege.length() == 0) ? className
                : packege + "/" //$NON-NLS-1$
                        + className;
        String innerPrefix = entryName.substring(0, entryName.length() - 6) + "$"; //$NON-NLS-1$
        // strip .class + $
        Enumeration<? extends ZipEntry> entries = archive.entries();
        ZipEntry entry;
        String name;
        ArrayList<ZipEntry> relevant = new ArrayList();

        while (entries.hasMoreElements()) {
            entry = entries.nextElement();
            name = entry.getName();
            if (name.equals(entryName) || (name.startsWith(innerPrefix) && inner))
                relevant.add(entry);
        }
        return relevant;
    }
}