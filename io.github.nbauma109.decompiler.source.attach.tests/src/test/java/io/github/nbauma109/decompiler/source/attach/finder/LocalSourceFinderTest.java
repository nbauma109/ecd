/*******************************************************************************
 * © 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport;
import io.github.nbauma109.decompiler.source.attach.utils.SourceConstants;

public class LocalSourceFinderTest {

    private static final String BIN_JAR_NAME = "demo.jar";
    private static final String IGNORED_SHA1 = "ignored";

    private File testRoot;

    @Before
    public void setUp() {
        testRoot = SourceAttachTestSupport.createTargetTempDir("local-source-finder-tests"); //$NON-NLS-1$
    }

    @After
    public void tearDown() {
        FileUtils.deleteQuietly(testRoot);
    }

    @Test
    public void findRegistersAdjacentSourcesJar() throws IOException {
        File binJar = new File(testRoot, BIN_JAR_NAME);
        File srcJar = new File(testRoot, "demo-sources.jar");
        Files.writeString(binJar.toPath(), "bin", StandardCharsets.UTF_8);
        Files.writeString(srcJar.toPath(), "src", StandardCharsets.UTF_8);
        LocalSourceFinder finder = new LocalSourceFinder();
        List<SourceFileResult> results = new ArrayList<>();

        finder.find(binJar.getAbsolutePath(), IGNORED_SHA1, results);

        assertEquals(1, results.size());
        assertEquals(srcJar.getAbsolutePath(), results.get(0).getSource());
        assertEquals(srcJar.getName(), results.get(0).getSuggestedSourceFileName());
        assertEquals(100, results.get(0).getAccuracy());
        assertEquals(srcJar.toURI().toURL().toString(), finder.getDownloadUrl());
        assertTrue(finder.toString().contains("LocalSourceFinder"));
    }

    @Test
    public void findReturnsNothingWhenCanceled() throws IOException {
        File binJar = new File(testRoot, BIN_JAR_NAME);
        File srcJar = new File(testRoot, "demo-sources.jar");
        Files.writeString(binJar.toPath(), "bin", StandardCharsets.UTF_8);
        Files.writeString(srcJar.toPath(), "src", StandardCharsets.UTF_8);
        LocalSourceFinder finder = new LocalSourceFinder();
        List<SourceFileResult> results = new ArrayList<>();

        finder.cancel();
        finder.find(binJar.getAbsolutePath(), IGNORED_SHA1, results);

        assertTrue(results.isEmpty());
    }

    @Test
    public void findReturnsNothingWhenNoAdjacentSourceOrGavIsAvailable() throws IOException {
        File binJar = new File(testRoot, BIN_JAR_NAME);
        Files.writeString(binJar.toPath(), "not a jar with pom properties", StandardCharsets.UTF_8);
        LocalSourceFinder finder = new LocalSourceFinder();
        List<SourceFileResult> results = new ArrayList<>();

        finder.find(binJar.getAbsolutePath(), IGNORED_SHA1, results);

        assertTrue(results.isEmpty());
    }

    @Test
    public void findResolvesSourceFromMavenRepoWhenGavEmbeddedInPomProperties() throws IOException {
        // Create a binary JAR that embeds pom.properties so the finder can discover its GAV
        File binJar = createJarWithPomProperties("lib-gav.jar", //$NON-NLS-1$
                "org.testgroup.local", "test-lib-gav", "3.1.0"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        // Pre-create the expected Maven-repo source file at the conventional path
        File m2SourceDir = new File(SourceConstants.USER_M2_REPO_DIR,
                "org/testgroup/local/test-lib-gav/3.1.0"); //$NON-NLS-1$
        m2SourceDir.mkdirs();
        File sourceJar = new File(m2SourceDir, "test-lib-gav-3.1.0-sources.jar"); //$NON-NLS-1$
        Files.writeString(sourceJar.toPath(), "source-placeholder", StandardCharsets.UTF_8); //$NON-NLS-1$

        try {
            LocalSourceFinder finder = new LocalSourceFinder();
            List<SourceFileResult> results = new ArrayList<>();

            finder.find(binJar.getAbsolutePath(), IGNORED_SHA1, results);

            assertEquals(1, results.size());
            assertEquals(sourceJar.getAbsolutePath(), results.get(0).getSource());
            assertEquals(100, results.get(0).getAccuracy());
        } finally {
            Files.deleteIfExists(sourceJar.toPath());
            deleteEmptyDirs(m2SourceDir);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private File createJarWithPomProperties(String name, String groupId, String artifactId, String version)
            throws IOException {
        File jar = new File(testRoot, name);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties", //$NON-NLS-1$ //$NON-NLS-2$
                "groupId=" + groupId + "\nartifactId=" + artifactId + "\nversion=" + version + "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        entries.put("com/example/Demo.class", "bytecode"); //$NON-NLS-1$ //$NON-NLS-2$
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(jar)))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setSize(bytes.length);
                zos.putNextEntry(zipEntry);
                zos.write(bytes);
                zos.closeEntry();
            }
        }
        return jar;
    }

    private static void deleteEmptyDirs(File dir) throws IOException {
        // Walk up the directory tree, removing each empty directory up to (but not including)
        // the Maven local repository root so we don't accidentally remove ~/.m2/repository.
        File cursor = dir;
        while (cursor != null && cursor.exists() && cursor.isDirectory()
                && !cursor.equals(SourceConstants.USER_M2_REPO_DIR)) {
            File[] contents = cursor.listFiles();
            if (contents == null || contents.length != 0) {
                break; // non-empty (or unreadable) – stop here
            }
            Files.delete(cursor.toPath());
            cursor = cursor.getParentFile();
        }
    }
}
