package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport;
import io.github.nbauma109.decompiler.source.attach.utils.SourceBindingUtil;
import io.github.nbauma109.decompiler.util.HashUtils;

public class SourceCodeFinderFacadeTest {

    private File testRoot;

    @Before
    public void setUp() {
        testRoot = SourceAttachTestSupport.createTargetTempDir("facade-test"); //$NON-NLS-1$
    }

    @After
    public void tearDown() {
        FileUtils.deleteQuietly(testRoot);
    }

    @Test
    public void findWithNonExistentBinaryFileDoesNotAddResults() {
        // Construct a path inside testRoot that is never created on disk
        File nonExistentBinaryFile = new File(testRoot, "nonexistent-" + System.nanoTime() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("file must not exist for this test to be meaningful", //$NON-NLS-1$
                !nonExistentBinaryFile.exists());

        SourceCodeFinderFacade facade = new SourceCodeFinderFacade();
        List<SourceFileResult> results = new ArrayList<>();

        facade.find(nonExistentBinaryFile.getAbsolutePath(), "deadbeef", results); //$NON-NLS-1$

        assertTrue(results.isEmpty());
    }

    @Test
    public void findWithDirectoryPathDoesNotAddResults() {
        assertTrue("testRoot must be a directory for this test to be meaningful", //$NON-NLS-1$
                testRoot.isDirectory());
        SourceCodeFinderFacade facade = new SourceCodeFinderFacade();
        List<SourceFileResult> results = new ArrayList<>();

        facade.find(testRoot.getAbsolutePath(), "deadbeef", results); //$NON-NLS-1$

        assertTrue(results.isEmpty());
    }

    @Test
    public void getDownloadUrlReturnsNull() {
        assertNull(new SourceCodeFinderFacade().getDownloadUrl());
    }

    @Test
    public void cancelDoesNotThrow() {
        SourceCodeFinderFacade facade = new SourceCodeFinderFacade();
        facade.cancel(); // must not throw regardless of state
    }

    @Test
    public void findAfterCancelDoesNotInvokeFinders() throws IOException {
        // A real existing ZIP that has no adjacent source jar and no registered SHA binding.
        File binFile = createMinimalZip("bin-cancel-test.jar"); //$NON-NLS-1$

        SourceCodeFinderFacade facade = new SourceCodeFinderFacade();
        facade.cancel(); // set canceled flag before calling find()

        List<SourceFileResult> results = new ArrayList<>();
        facade.find(binFile.getAbsolutePath(), "deadbeef-cancel-" + System.nanoTime(), results); //$NON-NLS-1$

        // Because the facade was canceled, the finder loop is never entered.
        assertTrue("No results expected when facade is canceled before find()", results.isEmpty()); //$NON-NLS-1$
    }

    @Test
    public void findReturnsCachedResultWhenShaIsRegistered() throws IOException {
        // Create real source and temp zip files so saveSourceBindingRecord accepts them.
        File sourceFile = createMinimalZip("facade-src-" + System.nanoTime() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
        File tempFile = createMinimalZip("facade-tmp-" + System.nanoTime() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
        File binFile = createMinimalZip("facade-bin-" + System.nanoTime() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$

        // Compute the actual SHA of the binary so the facade's cache lookup succeeds.
        String sha = HashUtils.sha1Hash(binFile);
        SourceBindingUtil.saveSourceBindingRecord(sourceFile, sha, null, tempFile);

        SourceCodeFinderFacade facade = new SourceCodeFinderFacade();
        List<SourceFileResult> results = new ArrayList<>();
        facade.find(binFile.getAbsolutePath(), sha, results);

        assertEquals("Expected exactly one cached result", 1, results.size()); //$NON-NLS-1$
        assertEquals(sourceFile.getAbsolutePath(), results.get(0).getSource());
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
