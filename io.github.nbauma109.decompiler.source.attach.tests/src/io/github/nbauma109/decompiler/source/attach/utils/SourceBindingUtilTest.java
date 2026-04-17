package io.github.nbauma109.decompiler.source.attach.utils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        Files.delete(sourceFile.toPath());

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
    // modifySourceBindingRecord – update existing entry with a new SHA
    // -----------------------------------------------------------------------

    @Test
    public void saveSourceBindingRecordTwiceWithDifferentShasMakesBothShasQueryable() throws IOException {
        File sourceFile = createMinimalZip("update-src.jar"); //$NON-NLS-1$
        File tempFile = createMinimalZip("update-tmp.jar"); //$NON-NLS-1$
        String sha1 = "sha-first-" + System.nanoTime(); //$NON-NLS-1$
        String sha2 = "sha-second-" + System.nanoTime(); //$NON-NLS-1$

        // First save creates the entry; second save should call modifySourceBindingRecord
        SourceBindingUtil.saveSourceBindingRecord(sourceFile, sha1, null, tempFile);
        SourceBindingUtil.saveSourceBindingRecord(sourceFile, sha2, null, tempFile);

        assertNotNull("First SHA must still be findable after a second save", //$NON-NLS-1$
                SourceBindingUtil.getSourceFileBySha(sha1));
        assertNotNull("Second SHA must be findable after the second save", //$NON-NLS-1$
                SourceBindingUtil.getSourceFileBySha(sha2));
    }

    @Test
    public void saveSourceBindingRecordUpdatesSameEntryWithDownloadUrl() throws IOException {
        File sourceFile = createMinimalZip("dl-update-src.jar"); //$NON-NLS-1$
        File tempFile = createMinimalZip("dl-update-tmp.jar"); //$NON-NLS-1$
        String sha = "sha-dl-update-" + System.nanoTime(); //$NON-NLS-1$
        String url = "https://example.com/dl-update-" + System.nanoTime() + "-sources.jar"; //$NON-NLS-1$ //$NON-NLS-2$

        // First save without URL, second save adds the URL to the same record
        SourceBindingUtil.saveSourceBindingRecord(sourceFile, sha, null, tempFile);
        SourceBindingUtil.saveSourceBindingRecord(sourceFile, sha, url, tempFile);

        String[] byUrl = SourceBindingUtil.getSourceFileByDownloadUrl(url);
        assertNotNull("Record should be findable by its download URL after the update", byUrl); //$NON-NLS-1$
        assertNotNull("Source path must be non-null", byUrl[0]); //$NON-NLS-1$
    }

    @Test
    public void getSourceFileByShaReturnsNullForUnknownSha() {
        String unknownSha = "sha-never-saved-" + System.nanoTime(); //$NON-NLS-1$
        assertNull(SourceBindingUtil.getSourceFileBySha(unknownSha));
    }

    @Test
    public void getSourceFileByDownloadUrlReturnsNullForUnknownUrl() {
        String unknownUrl = "https://example.com/never-saved-" + System.nanoTime() + "-sources.jar"; //$NON-NLS-1$ //$NON-NLS-2$
        assertNull(SourceBindingUtil.getSourceFileByDownloadUrl(unknownUrl));
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
