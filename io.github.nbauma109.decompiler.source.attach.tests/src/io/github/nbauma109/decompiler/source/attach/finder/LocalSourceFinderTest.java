package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport;

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
}
