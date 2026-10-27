/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assume;
import org.junit.Test;

import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport;

@SuppressWarnings("restriction")
public class JarScanCacheTest {

    @Test
    public void sameJarSharedBySeveralProjectsIsScannedOnce() throws IOException {
        File tempDir = DecompilerTestSupport.createTargetTempDir("jar-scan-cache-shared"); //$NON-NLS-1$
        File jar = new File(tempDir, "shared.jar"); //$NON-NLS-1$
        try {
            Files.writeString(jar.toPath(), "content"); //$NON-NLS-1$
            AtomicInteger scans = new AtomicInteger();
            JarScanCache cache = new JarScanCache((file, monitor) -> {
                scans.incrementAndGet();
                return work(null);
            });

            BytecodeJarIndexer.JarWork first = cache.scan(jar, null);
            BytecodeJarIndexer.JarWork second = cache.scan(new File(jar.getAbsolutePath()), null);

            assertSame(first, second);
            assertEquals(1, scans.get());
        } finally {
            org.apache.commons.io.FileUtils.deleteQuietly(tempDir);
        }
    }

    @Test
    public void distinctJarsAreScannedSeparately() throws IOException {
        File tempDir = DecompilerTestSupport.createTargetTempDir("jar-scan-cache-distinct"); //$NON-NLS-1$
        File firstJar = new File(tempDir, "first.jar"); //$NON-NLS-1$
        File secondJar = new File(tempDir, "second.jar"); //$NON-NLS-1$
        try {
            Files.writeString(firstJar.toPath(), "first"); //$NON-NLS-1$
            Files.writeString(secondJar.toPath(), "second"); //$NON-NLS-1$
            AtomicInteger scans = new AtomicInteger();
            JarScanCache cache = new JarScanCache((file, monitor) -> {
                scans.incrementAndGet();
                return work(null);
            });

            cache.scan(firstJar, null);
            cache.scan(secondJar, null);

            assertEquals(2, scans.get());
        } finally {
            org.apache.commons.io.FileUtils.deleteQuietly(tempDir);
        }
    }

    @Test
    public void metadataChangeInvalidatesTheCachedScan() throws IOException {
        File tempDir = DecompilerTestSupport.createTargetTempDir("jar-scan-cache-metadata"); //$NON-NLS-1$
        File jar = new File(tempDir, "touched.jar"); //$NON-NLS-1$
        try {
            Files.writeString(jar.toPath(), "content"); //$NON-NLS-1$
            AtomicInteger scans = new AtomicInteger();
            JarScanCache cache = new JarScanCache((file, monitor) -> {
                scans.incrementAndGet();
                return work(null);
            });

            cache.scan(jar, null);
            Files.setLastModifiedTime(jar.toPath(), FileTime.fromMillis(jar.lastModified() + 2000L));
            cache.scan(jar, null);

            assertEquals(2, scans.get());
        } finally {
            org.apache.commons.io.FileUtils.deleteQuietly(tempDir);
        }
    }

    @Test
    public void failedScanIsNotRetriedForEveryProject() throws IOException {
        File tempDir = DecompilerTestSupport.createTargetTempDir("jar-scan-cache-failure"); //$NON-NLS-1$
        File jar = new File(tempDir, "broken.jar"); //$NON-NLS-1$
        try {
            Files.writeString(jar.toPath(), "not a zip"); //$NON-NLS-1$
            AtomicInteger scans = new AtomicInteger();
            JarScanCache cache = new JarScanCache((file, monitor) -> {
                scans.incrementAndGet();
                return null;
            });

            assertNull(cache.scan(jar, null));
            assertNull(cache.scan(jar, null));
            assertEquals(1, scans.get());
        } finally {
            org.apache.commons.io.FileUtils.deleteQuietly(tempDir);
        }
    }

    @Test
    public void symbolicLinksResolveToTheSameCanonicalFile() throws IOException {
        File tempDir = DecompilerTestSupport.createTargetTempDir("jar-scan-cache-symlink"); //$NON-NLS-1$
        File target = new File(tempDir, "target.jar"); //$NON-NLS-1$
        File link = new File(tempDir, "link.jar"); //$NON-NLS-1$
        try {
            Files.writeString(target.toPath(), "content"); //$NON-NLS-1$
            try {
                Files.createSymbolicLink(link.toPath(), target.toPath().toAbsolutePath());
            } catch (UnsupportedOperationException | IOException e) {
                Assume.assumeNoException(e);
            }
            AtomicInteger scans = new AtomicInteger();
            JarScanCache cache = new JarScanCache((file, monitor) -> {
                scans.incrementAndGet();
                return work(null);
            });

            cache.scan(target, null);
            cache.scan(link, null);

            assertEquals(1, scans.get());
        } finally {
            org.apache.commons.io.FileUtils.deleteQuietly(tempDir);
        }
    }

    private static BytecodeJarIndexer.JarWork work(String contentHash) {
        return new BytecodeJarIndexer.JarWork(List.of(), 0L, 0, 0L, contentHash);
    }
}
