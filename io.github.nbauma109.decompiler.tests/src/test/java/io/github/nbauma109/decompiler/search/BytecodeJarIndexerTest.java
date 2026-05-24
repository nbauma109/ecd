/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.search;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.Test;

import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport;

@SuppressWarnings("restriction")
public class BytecodeJarIndexerTest {

	@Test
    public void planSkipsMultiReleaseVersionedClassEntries()
            throws IOException {
        File tempDir = DecompilerTestSupport.createTargetTempDir("bytecode-jar-indexer"); //$NON-NLS-1$
        File jar = new File(tempDir, "multi-release.jar"); //$NON-NLS-1$
        try {
            createJar(jar);

            BytecodeJarIndexer.JarWork work = BytecodeJarIndexer.plan(jar);

            assertEquals(1, work.entries().size());
            assertEquals("pkg/Base.class", work.entries().get(0).name()); //$NON-NLS-1$
        } finally {
            org.apache.commons.io.FileUtils.deleteQuietly(tempDir);
        }
    }

    private static void createJar(File jar)
            throws IOException {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addEntry(output, "pkg/Base.class"); //$NON-NLS-1$
            addEntry(output, "META-INF/versions/11/pkg/Base.class"); //$NON-NLS-1$
            addEntry(output, "META-INF/versions/21/pkg/Other.class"); //$NON-NLS-1$
        }
    }

    private static void addEntry(JarOutputStream output, String name)
            throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(new byte[] { 0, 0, 0, 0 });
        output.closeEntry();
    }
}
