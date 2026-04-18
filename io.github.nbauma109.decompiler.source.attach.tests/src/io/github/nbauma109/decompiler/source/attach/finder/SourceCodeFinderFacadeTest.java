package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport;

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
}
