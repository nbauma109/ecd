package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import io.github.nbauma109.decompiler.source.attach.utils.SourceConstants;
import io.github.nbauma109.decompiler.util.HashUtils;

public class MavenRepoSourceCodeFinderTest extends AbstractSourceCodeFinderTests {

    private static final String ASM_UTIL_GAV_URL = "https://repo1.maven.org/maven2/org/ow2/asm/asm-util/9.7/"; //$NON-NLS-1$
    private static final String ASM_UTIL_FILE_NAME = "asm-util-9.7"; //$NON-NLS-1$
    private static final String ORG_EXAMPLE = "org.example"; //$NON-NLS-1$

    @Test
    public void testFind() throws IOException {
        testFindAsmUtil(null);
    }

    @Test
    public void testFindWithLeadingZeroSha1() throws IOException {
        testFindAsmAnalysis(null);
    }

    @Test
    public void testFindUsesLocalCacheWhenSourceAlreadyInMavenRepo() throws IOException {
        // Download binary and source JARs for a known artifact
        File downloadDir = new File("target"); //$NON-NLS-1$
        File jarFile = new File(downloadDir, ASM_UTIL_FILE_NAME + ".jar"); //$NON-NLS-1$
        File srcFile = new File(downloadDir, ASM_UTIL_FILE_NAME + "-sources.jar"); //$NON-NLS-1$

        URL binUrl = new URL(ASM_UTIL_GAV_URL + ASM_UTIL_FILE_NAME + ".jar"); //$NON-NLS-1$
        URL srcUrl = new URL(ASM_UTIL_GAV_URL + ASM_UTIL_FILE_NAME + "-sources.jar"); //$NON-NLS-1$

        FileUtils.copyURLToFile(binUrl, jarFile);
        FileUtils.copyURLToFile(srcUrl, srcFile);

        if (!jarFile.exists() || !srcFile.exists()) {
            return; // Skip if download fails
        }

        // Pre-place the source JAR in the expected Maven repo location
        File mavenRepoSourceFile = new File(SourceConstants.USER_M2_REPO_DIR,
                "org/ow2/asm/asm-util/9.7/asm-util-9.7-sources.jar"); //$NON-NLS-1$
        mavenRepoSourceFile.getParentFile().mkdirs();
        Files.copy(srcFile.toPath(), mavenRepoSourceFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        try {
            String sha1 = HashUtils.sha1Hash(jarFile);
            MavenRepoSourceCodeFinder finder = new MavenRepoSourceCodeFinder();
            List<SourceFileResult> results = new ArrayList<>();
            finder.find(jarFile.getAbsolutePath(), sha1, results);

            assertNotNull(results);
            assertEquals(1, results.size());
            // Verify that the result source points to the Maven repo file
            assertEquals(mavenRepoSourceFile.getAbsolutePath(), results.get(0).getSource());
            assertTrue(new File(results.get(0).getSource()).exists());
        } finally {
            // Leave the file in Maven repo (that's the intent of the feature)
        }
    }

    @Override
    protected AbstractSourceCodeFinder newSourceCodeFinder(String serviceUrl) {
        return new MavenRepoSourceCodeFinder();
    }

    @Test
    public void getMavenRepoSourceFileReturnsNullForPathTraversalInGroupId() {
        MavenRepoSourceCodeFinder finder = new MavenRepoSourceCodeFinder();
        GAV gav = new GAV();
        gav.setGroupId("org/../etc");
        gav.setArtifactId("lib");
        gav.setVersion("1.0");
        assertNull(finder.getMavenRepoSourceFile(gav));
    }

    @Test
    public void getMavenRepoSourceFileReturnsNullForSlashInGroupId() {
        MavenRepoSourceCodeFinder finder = new MavenRepoSourceCodeFinder();
        GAV gav = new GAV();
        gav.setGroupId("org/evil");
        gav.setArtifactId("lib");
        gav.setVersion("1.0");
        assertNull(finder.getMavenRepoSourceFile(gav));
    }

    @Test
    public void getMavenRepoSourceFileReturnsNullForPathTraversalInVersion() {
        MavenRepoSourceCodeFinder finder = new MavenRepoSourceCodeFinder();
        GAV gav = new GAV();
        gav.setGroupId(ORG_EXAMPLE);
        gav.setArtifactId("lib");
        gav.setVersion("../evil");
        assertNull(finder.getMavenRepoSourceFile(gav));
    }

    @Test
    public void getMavenRepoSourceFileReturnsValidPathForValidGav() {
        MavenRepoSourceCodeFinder finder = new MavenRepoSourceCodeFinder();
        GAV gav = new GAV();
        gav.setGroupId(ORG_EXAMPLE);
        gav.setArtifactId("mylib");
        gav.setVersion("2.3.4");
        File result = finder.getMavenRepoSourceFile(gav);
        assertNotNull(result);
        String path = result.getAbsolutePath();
        assertTrue(path.startsWith(SourceConstants.USER_M2_REPO_DIR.getAbsolutePath()));
        assertTrue(path.endsWith("mylib-2.3.4-sources.jar")); //$NON-NLS-1$
    }
}
