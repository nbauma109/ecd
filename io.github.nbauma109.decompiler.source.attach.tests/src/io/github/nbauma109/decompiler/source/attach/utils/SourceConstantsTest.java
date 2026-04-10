package io.github.nbauma109.decompiler.source.attach.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

public class SourceConstantsTest {

    private static final String USER_HOME = System.getProperty("user.home"); //$NON-NLS-1$

    @Test
    public void userM2RepoDirIsNonNullAndLocatedUnderUserHome() {
        assertNotNull(SourceConstants.USER_M2_REPO_DIR);
        String path = SourceConstants.USER_M2_REPO_DIR.getAbsolutePath();
        assertTrue("Expected USER_M2_REPO_DIR to be under user home", path.startsWith(USER_HOME)); //$NON-NLS-1$
    }

    @Test
    public void userM2RepoDirEndsWithExpectedSegments() {
        String path = SourceConstants.USER_M2_REPO_DIR.getAbsolutePath();
        assertTrue("Expected .m2" + File.separator + "repository suffix", //$NON-NLS-1$ //$NON-NLS-2$
                path.endsWith(File.separator + ".m2" + File.separator + "repository") //$NON-NLS-1$ //$NON-NLS-2$
                || path.endsWith("/.m2/repository")); //$NON-NLS-1$
    }

    @Test
    public void userGradleCacheDirIsNonNullAndLocatedUnderUserHome() {
        assertNotNull(SourceConstants.USER_GRADLE_CACHE_DIR);
        String path = SourceConstants.USER_GRADLE_CACHE_DIR.getAbsolutePath();
        assertTrue("Expected USER_GRADLE_CACHE_DIR to be under user home", path.startsWith(USER_HOME)); //$NON-NLS-1$
    }

    @Test
    public void userGradleCacheDirContainsExpectedSegments() {
        String path = SourceConstants.USER_GRADLE_CACHE_DIR.getAbsolutePath();
        assertTrue("Expected .gradle in path", path.contains(".gradle")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("Expected caches in path", path.contains("caches")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("Expected modules-2 in path", path.contains("modules-2")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("Expected files-2.1 in path", path.contains("files-2.1")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void sourceAttacherDirIsNonNullAndLocatedUnderUserHome() {
        assertNotNull(SourceConstants.SourceAttacherDir);
        String path = SourceConstants.SourceAttacherDir.getAbsolutePath();
        assertTrue("Expected SourceAttacherDir to be under user home", path.startsWith(USER_HOME)); //$NON-NLS-1$
    }

    @Test
    public void sourceAttachPathMatchesSourceAttacherDirAbsolutePath() {
        assertEquals(SourceConstants.SourceAttacherDir.getAbsolutePath(), SourceConstants.SourceAttachPath);
    }
}
