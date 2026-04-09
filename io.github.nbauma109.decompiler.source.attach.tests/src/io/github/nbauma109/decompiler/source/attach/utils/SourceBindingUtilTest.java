package io.github.nbauma109.decompiler.source.attach.utils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport;

public class SourceBindingUtilTest {

    private File testRoot;

    @Before
    public void setUp() {
        testRoot = SourceAttachTestSupport.createTargetTempDir("source-binding-util-tests"); //$NON-NLS-1$
    }

    @After
    public void tearDown() {
        FileUtils.deleteQuietly(testRoot);
    }

    // -----------------------------------------------------------------------
    // Null-guard paths
    // -----------------------------------------------------------------------

    @Test
    public void saveSourceBindingRecordWithNullSourceFileIsNoOp() throws IOException {
        File tempFile = createMinimalZip("temp-null-src.jar");
        // Must not throw even though sourceFile is null
        SourceBindingUtil.saveSourceBindingRecord(null, "sha-null-src-" + System.nanoTime(), null, tempFile);
    }

    @Test
    public void saveSourceBindingRecordWithNullTempFileIsNoOp() throws IOException {
        File sourceFile = createMinimalZip("src-null-temp.jar");
        // Must not throw even though tempFile is null
        SourceBindingUtil.saveSourceBindingRecord(sourceFile, "sha-null-tmp-" + System.nanoTime(), null, null);
    }

    @Test
    public void saveSourceBindingRecordWithNonExistingSourceFileIsNoOp() throws IOException {
        File missingSource = new File(testRoot, "missing-source.jar");
        File tempFile = createMinimalZip("temp-missing-src.jar");
        // Must not throw when sourceFile does not exist
        SourceBindingUtil.saveSourceBindingRecord(missingSource, "sha-miss-src-" + System.nanoTime(), null, tempFile);
    }

    @Test
    public void saveSourceBindingRecordWithNonExistingTempFileIsNoOp() throws IOException {
        File sourceFile = createMinimalZip("src-missing-tmp.jar");
        File missingTemp = new File(testRoot, "missing-temp.jar");
        // Must not throw when tempSourceFile does not exist
        SourceBindingUtil.saveSourceBindingRecord(sourceFile, "sha-miss-tmp-" + System.nanoTime(), null, missingTemp);
    }

    // -----------------------------------------------------------------------
    // Save / read round-trips
    // -----------------------------------------------------------------------

    @Test
    public void saveAndGetSourceFileByShaRoundTrip() throws IOException {
        File sourceFile = createMinimalZip("round-trip-src.jar");
        File tempFile = createMinimalZip("round-trip-tmp.jar");
        String sha = "cafebabe-sha-" + System.nanoTime(); //$NON-NLS-1$

        SourceBindingUtil.saveSourceBindingRecord(sourceFile, sha, null, tempFile);

        String[] result = SourceBindingUtil.getSourceFileBySha(sha);
        assertNotNull("Expected a result after saving a binding record", result);
        assertNotNull("Source path should be non-null", result[0]);
    }

    @Test
    public void saveAndGetSourceFileByDownloadUrlRoundTrip() throws IOException {
        File sourceFile = createMinimalZip("dl-url-src.jar");
        File tempFile = createMinimalZip("dl-url-tmp.jar");
        String sha = "sha-dl-url-" + System.nanoTime(); //$NON-NLS-1$
        String downloadUrl = "https://example.com/dl-url-" + System.nanoTime() + "-sources.jar"; //$NON-NLS-1$ //$NON-NLS-2$

        SourceBindingUtil.saveSourceBindingRecord(sourceFile, sha, downloadUrl, tempFile);

        String[] result = SourceBindingUtil.getSourceFileByDownloadUrl(downloadUrl);
        assertNotNull("Expected a result after saving a binding record with a download URL", result);
        assertNotNull("Source path should be non-null", result[0]);
    }

    // -----------------------------------------------------------------------
    // checkSourceBindingConfig
    // -----------------------------------------------------------------------

    @Test
    public void checkSourceBindingConfigRemovesEntriesForMissingSourceFiles() throws IOException {
        // Save a record, then delete the source file to simulate a stale entry
        File sourceFile = createMinimalZip("stale-src.jar");
        File tempFile = createMinimalZip("stale-tmp.jar");
        String sha = "sha-stale-" + System.nanoTime(); //$NON-NLS-1$

        SourceBindingUtil.saveSourceBindingRecord(sourceFile, sha, null, tempFile);

        // Verify it can be found before deletion
        assertNotNull(SourceBindingUtil.getSourceFileBySha(sha));

        // Delete the source file to make the entry stale
        sourceFile.delete();

        // checkSourceBindingConfig should remove the stale entry
        SourceBindingUtil.checkSourceBindingConfig();

        // The entry should now be gone
        assertNull(SourceBindingUtil.getSourceFileBySha(sha));
    }

    @Test
    public void checkSourceBindingConfigIsNoOpWhenNoBindingFileExists() {
        // Should not throw even if no binding file exists yet (e.g., first run)
        SourceBindingUtil.checkSourceBindingConfig();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private File createMinimalZip(String name) throws IOException {
        File dest = new File(testRoot, name);
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(dest)))) {
            ZipEntry entry = new ZipEntry("placeholder.txt"); //$NON-NLS-1$
            zos.putNextEntry(entry);
            zos.write("placeholder".getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
            zos.closeEntry();
        }
        return dest;
    }
}
