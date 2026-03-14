package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LocalSourceFinderTest {

    private File testRoot;

    @Before
    public void setUp() {
        File targetDir = new File("target");
        assertTrue(targetDir.exists() || targetDir.mkdirs());

        testRoot = new File(targetDir, "local-source-finder-tests" + File.separator + System.nanoTime());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void tearDown() {
        deleteRecursively(testRoot);
    }

    @Test
    public void find_registersAdjacentSourcesJar() throws Exception {
        File binJar = new File(testRoot, "demo.jar");
        File srcJar = new File(testRoot, "demo-sources.jar");
        Files.writeString(binJar.toPath(), "bin", StandardCharsets.UTF_8);
        Files.writeString(srcJar.toPath(), "src", StandardCharsets.UTF_8);
        LocalSourceFinder finder = new LocalSourceFinder();
        List<SourceFileResult> results = new ArrayList<>();

        finder.find(binJar.getAbsolutePath(), "ignored", results);

        assertEquals(1, results.size());
        assertEquals(srcJar.getAbsolutePath(), results.get(0).getSource());
        assertEquals(srcJar.getName(), results.get(0).getSuggestedSourceFileName());
        assertEquals(100, results.get(0).getAccuracy());
        assertEquals(srcJar.toURI().toURL().toString(), finder.getDownloadUrl());
        assertTrue(finder.toString().contains("LocalSourceFinder"));
    }

    @Test
    public void find_returnsNothingWhenCanceled() throws Exception {
        File binJar = new File(testRoot, "demo.jar");
        File srcJar = new File(testRoot, "demo-sources.jar");
        Files.writeString(binJar.toPath(), "bin", StandardCharsets.UTF_8);
        Files.writeString(srcJar.toPath(), "src", StandardCharsets.UTF_8);
        LocalSourceFinder finder = new LocalSourceFinder();
        List<SourceFileResult> results = new ArrayList<>();

        finder.cancel();
        finder.find(binJar.getAbsolutePath(), "ignored", results);

        assertTrue(results.isEmpty());
    }

    @Test
    public void find_returnsNothingWhenNoAdjacentSourceOrGavIsAvailable() throws Exception {
        File binJar = new File(testRoot, "demo.jar");
        Files.writeString(binJar.toPath(), "not a jar with pom properties", StandardCharsets.UTF_8);
        LocalSourceFinder finder = new LocalSourceFinder();
        List<SourceFileResult> results = new ArrayList<>();

        finder.find(binJar.getAbsolutePath(), "ignored", results);

        assertTrue(results.isEmpty());
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
