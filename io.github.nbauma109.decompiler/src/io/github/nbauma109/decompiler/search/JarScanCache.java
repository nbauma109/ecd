/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Caches the ZIP directory scan of a jar for the duration of one refresh, keyed by the
 * canonical file path and its metadata. The same jar is often referenced by several projects
 * (and therefore by several classpath roots); scanning it once avoids repeating both the ZIP
 * inspection and the SHA-256 computation for every reference.
 */
public final class JarScanCache {

    @FunctionalInterface
    public interface Scanner {
        BytecodeJarIndexer.JarWork scan(File jar, IProgressMonitor monitor);
    }

    private final Map<ScanKey, Optional<BytecodeJarIndexer.JarWork>> scans = new HashMap<>();
    private final Scanner scanner;

    public JarScanCache() {
        this(BytecodeJarIndexer::scan);
    }

    public JarScanCache(Scanner scanner) {
        this.scanner = scanner;
    }

    public BytecodeJarIndexer.JarWork scan(File jar, IProgressMonitor monitor) {
        ScanKey key = scanKey(jar);
        return scans.computeIfAbsent(key, ignored -> Optional.ofNullable(scanner.scan(jar, monitor))).orElse(null);
    }

    public void put(File jar, BytecodeJarIndexer.JarWork work) {
        scans.put(scanKey(jar), Optional.ofNullable(work));
    }

    static ScanKey scanKey(File jar) {
        return new ScanKey(canonicalPath(jar), jar.lastModified(), jar.length());
    }

    public static Path canonicalPath(File jar) {
        try {
            return jar.toPath().toRealPath();
        } catch (IOException e) {
            return jar.toPath().toAbsolutePath().normalize();
        }
    }

    record ScanKey(Path path, long lastModified, long length) {
    }
}
