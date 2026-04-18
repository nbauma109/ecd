package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class SourceCodeFinderFacadeTest {

    @Test
    public void findWithNonExistentBinaryFileDoesNotAddResults() {
        SourceCodeFinderFacade facade = new SourceCodeFinderFacade();
        List<SourceFileResult> results = new ArrayList<>();

        facade.find("/path/that/absolutely/does/not/exist-" + System.nanoTime() + ".jar", //$NON-NLS-1$ //$NON-NLS-2$
                "deadbeef", results); //$NON-NLS-1$

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
