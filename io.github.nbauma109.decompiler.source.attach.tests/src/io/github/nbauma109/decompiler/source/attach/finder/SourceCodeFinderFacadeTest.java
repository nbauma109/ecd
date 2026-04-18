package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class SourceCodeFinderFacadeTest {

    @Test
    public void findWithNonExistentBinaryFileDoesNotAddResults() throws Exception {
        File nonExistentBinaryFile = File.createTempFile("SourceCodeFinderFacadeTest-", ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
        String nonExistentBinaryFilePath = nonExistentBinaryFile.getAbsolutePath();
        assertTrue("temporary file must be deleted for this test to be meaningful", //$NON-NLS-1$
                nonExistentBinaryFile.delete());
        assertTrue("temporary file path must not exist for this test to be meaningful", //$NON-NLS-1$
                !nonExistentBinaryFile.exists());

        SourceCodeFinderFacade facade = new SourceCodeFinderFacade();
        List<SourceFileResult> results = new ArrayList<>();

        facade.find(nonExistentBinaryFilePath, "deadbeef", results); //$NON-NLS-1$

        assertTrue(results.isEmpty());
    }

    @Test
    public void findWithDirectoryPathDoesNotAddResults() {
        File dir = new File("target"); //$NON-NLS-1$
        if (!dir.exists()) {
            assertTrue("target directory could not be created for this test", dir.mkdirs()); //$NON-NLS-1$
        }
        assertTrue("target path must be a directory for this test to be meaningful", //$NON-NLS-1$
                dir.isDirectory());
        SourceCodeFinderFacade facade = new SourceCodeFinderFacade();
        List<SourceFileResult> results = new ArrayList<>();

        facade.find(dir.getAbsolutePath(), "deadbeef", results); //$NON-NLS-1$

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
