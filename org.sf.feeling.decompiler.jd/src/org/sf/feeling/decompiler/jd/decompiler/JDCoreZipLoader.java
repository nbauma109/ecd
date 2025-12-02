/*******************************************************************************
 * Copyright (c) 2020 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.jd.decompiler;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.jd.core.v1.api.loader.Loader;
import org.objectweb.asm.ClassReader;
import org.sf.feeling.decompiler.util.Logger;

/**
 * 
 * JD-Core Loader implementation for decompiling classes directly from within a
 * ZIP/JAR archive
 * 
 * @author Jan Peter Stotz
 *
 */
public class JDCoreZipLoader implements Loader, Closeable {

    private final ZipFile zipFile;

    private final EntriesCache entriesCache;

    private final Set<String> loadedEntries = new TreeSet<>();

    public JDCoreZipLoader(Path zipFilePath, EntriesCache entriesCache) throws IOException {
        super();
        if (entriesCache != null && !entriesCache.zipFilePath.equals(zipFilePath)) {
            throw new IllegalArgumentException("entriesCache is for the wrong zipFilePath");
        }
        if (Files.exists(zipFilePath)) {
            zipFile = new ZipFile(zipFilePath.toFile());
        } else {
            throw new IOException("Could not find " + zipFilePath.toAbsolutePath());
        }

        if (entriesCache == null) {
            entriesCache = new EntriesCache(zipFilePath);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.startsWith("/")) {
                    name = name.substring(1);
                }
                if (name.endsWith(".class")) {
                    try {
                        // Extract class name from class file
                        ClassReader cr = new ClassReader(zipFile.getInputStream(entry));
                        String className = cr.getClassName();

                        if (className != null && !className.isEmpty()) {
                            String oldEntry = entriesCache.entriesMap.put(className, entry.getName());
                            if (oldEntry != null) {
                                Logger.info("Duplicate class " + className + " found in JAR " + zipFilePath + ": "
                                        + entry.getName() + "/" + oldEntry);
                            }
                        }
                    } catch (Exception e) {
                        Logger.error("Failed to read entry " + name + ": " + e.toString());
                    }
                }
            }
        }
        this.entriesCache = entriesCache;
    }

    @Override
    public boolean canLoad(String internalName) {
        return entriesCache.entriesMap.containsKey(internalName);
    }

    @Override
    public byte[] load(String internalName) throws IOException {
        String entryName = entriesCache.entriesMap.get(internalName);
        if (entryName == null) {
            Logger.error("Class not found: " + internalName);
            return new byte[0];
        }
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            // Should never happen
            Logger.error("Entry - missing for class file: " + entryName);
            return new byte[0];
        }
        int initialSize = entry.getSize() > 0 ? (int) entry.getSize() : 4096;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(initialSize)) {
            try (InputStream in = zipFile.getInputStream(entry)) {
                IOUtils.copy(in, out, 8 * 1024);
                loadedEntries.add(internalName);
                return out.toByteArray();
            }
        }
    }

    public Set<String> getLoadedEntries() {
        return loadedEntries;
    }

    public EntriesCache getEntriesCache() {
        return entriesCache;
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    public static class EntriesCache {
        private final Path zipFilePath;
        private final FileTime zipFileLastModified;

        /**
         * maps class name to file name in the JAR file
         */
        private final Map<String, String> entriesMap = new TreeMap<>();

        public EntriesCache(Path zipFilePath) throws IOException {
            super();
            this.zipFilePath = zipFilePath;
            this.zipFileLastModified = Files.getLastModifiedTime(zipFilePath);
        }

        public Path getZipFilePath() {
            return zipFilePath;
        }

        public boolean isForTheSameFile(Path fileToTest) throws IOException {
            boolean equal = zipFilePath.equals(fileToTest);
            if (equal) {
                FileTime lastMod = Files.getLastModifiedTime(fileToTest);
                return (zipFileLastModified.equals(lastMod));
            }
            return false;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((zipFilePath == null) ? 0 : zipFilePath.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            EntriesCache other = (EntriesCache) obj;
            if (zipFilePath == null) {
                if (other.zipFilePath != null)
                    return false;
            } else if (!zipFilePath.equals(other.zipFilePath))
                return false;
            return true;
        }

    }
}
