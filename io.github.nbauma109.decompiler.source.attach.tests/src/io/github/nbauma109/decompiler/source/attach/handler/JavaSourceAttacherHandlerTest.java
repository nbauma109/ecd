/*******************************************************************************
 * Copyright (c) 2026.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.junit.After;
import org.junit.Test;

import io.github.nbauma109.decompiler.source.attach.finder.SourceCodeFinder;
import io.github.nbauma109.decompiler.source.attach.finder.SourceFileResult;
import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport;
import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport.JavaProjectSetup;
import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport.LibrarySpec;
import io.github.nbauma109.decompiler.source.attach.utils.SourceConstants;

public class JavaSourceAttacherHandlerTest {

    private static final String TEST_JAR_BUNDLE_ID = "io.github.nbauma109.decompiler.source.attach.tests";
    private static final String TEST_JAR_PATH = "target/lib/commons-io.jar";

    private final List<IProject> projectsToDelete = new ArrayList<>();
    private final List<File> filesToDelete = new ArrayList<>();

    @After
    public void tearDown() throws IOException, CoreException {
        for (IProject p : projectsToDelete) {
            if (p != null && p.exists()) {
                p.delete(true, true, null);
            }
        }
        projectsToDelete.clear();

        for (File f : filesToDelete) {
            if (f != null && f.exists()) {
                FileUtils.deleteQuietly(f);
            }
        }
        filesToDelete.clear();

        JavaSourceAttacherHandler.clearRequests();
    }

    @Test
    public void testAttachSourceSetsAttachmentPath() throws IOException, CoreException, InterruptedException {
        File jar = SourceAttachTestSupport.resolveBundleEntryAsFile(TEST_JAR_BUNDLE_ID, TEST_JAR_PATH);
        File sourceJar = createZipUnderTarget("source-" + UUID.randomUUID() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$

        JavaProjectSetup setup = SourceAttachTestSupport.createJavaProjectWithLibraries(
                "attach-source-" + UUID.randomUUID(), //$NON-NLS-1$
                projectsToDelete,
                new LibrarySpec(jar, null));

        IPackageFragmentRoot root = setup.roots().get(0);
        assertNotNull(root);
        assertTrue(root.exists());

        boolean attached = JavaSourceAttacherHandler.attachSource(root, sourceJar);
        assertTrue(attached);

        IPath attachedPath = waitForSourceAttachment(root, 10, 200);
        assertNotNull(attachedPath);
        assertTrue(attachedPath.toFile().getAbsolutePath().equals(sourceJar.getAbsolutePath()));
    }

    @Test
    public void testProcessLibSourcesSkipsWhenDownloadUrlMissingAndFinderNotFacade()
            throws IOException, CoreException {
        File jar = SourceAttachTestSupport.resolveBundleEntryAsFile(TEST_JAR_BUNDLE_ID, TEST_JAR_PATH);
        File sourceJar = createZipUnderTarget("source-skip-" + UUID.randomUUID() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
        File tempSourceJar = createZipUnderTarget("temp-source-skip-" + UUID.randomUUID() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$

        JavaProjectSetup setup = SourceAttachTestSupport.createJavaProjectWithLibraries(
                "process-skip-" + UUID.randomUUID(), //$NON-NLS-1$
                projectsToDelete,
                new LibrarySpec(jar, null));

        IPackageFragmentRoot root = setup.roots().get(0);
        String binFile = jar.getAbsolutePath();

        JavaSourceAttacherHandler.putRequest(binFile, root);
        Set<String> notProcessed = new HashSet<>();
        notProcessed.add(binFile);

        SourceCodeFinder finder = new NoDownloadUrlFinder();
        SourceFileResult result = new SourceFileResult(finder, binFile, sourceJar, tempSourceJar, 100);
        List<SourceFileResult> responses = new ArrayList<>();
        responses.add(result);

        JavaSourceAttacherHandler.processLibSources(notProcessed, responses);

        IPath attachedPath = root.getSourceAttachmentPath();
        assertTrue(attachedPath == null || !attachedPath.toFile().exists());
    }

    @Test
    public void testProcessLibSourcesIgnoresNullSource() throws IOException, CoreException {
        File jar = SourceAttachTestSupport.resolveBundleEntryAsFile(TEST_JAR_BUNDLE_ID, TEST_JAR_PATH);

        JavaProjectSetup setup = SourceAttachTestSupport.createJavaProjectWithLibraries(
                "process-null-source-" + UUID.randomUUID(), //$NON-NLS-1$
                projectsToDelete,
                new LibrarySpec(jar, null));

        IPackageFragmentRoot root = setup.roots().get(0);
        String binFile = jar.getAbsolutePath();

        JavaSourceAttacherHandler.putRequest(binFile, root);
        Set<String> notProcessed = new HashSet<>();
        notProcessed.add(binFile);

        SourceCodeFinder finder = new NoDownloadUrlFinder();
        SourceFileResult result = new SourceFileResult(finder, binFile, (String) null, "ignored.jar", 0); //$NON-NLS-1$
        List<SourceFileResult> responses = new ArrayList<>();
        responses.add(result);

        JavaSourceAttacherHandler.processLibSources(notProcessed, responses);

        assertTrue(notProcessed.contains(binFile));
        IPath attachedPath = root.getSourceAttachmentPath();
        assertTrue(attachedPath == null || !attachedPath.toFile().exists());
    }

    @Test
    public void testUpdateSourceAttachmentsReturnsOkAndClearsRequests() throws IOException, CoreException {
        File jar = SourceAttachTestSupport.resolveBundleEntryAsFile(TEST_JAR_BUNDLE_ID, TEST_JAR_PATH);
        JavaProjectSetup setup = SourceAttachTestSupport.createJavaProjectWithLibraries(
                "update-source-attachments-" + UUID.randomUUID(), //$NON-NLS-1$
                projectsToDelete,
                new LibrarySpec(jar, null));

        IPackageFragmentRoot root = setup.roots().get(0);
        List<IPackageFragmentRoot> roots = new ArrayList<>();
        roots.add(root);

        Status status = (Status) JavaSourceAttacherHandler.updateSourceAttachments(roots, null);
        assertTrue(status.isOK());

        String binFile = jar.getCanonicalPath();
        assertTrue(!JavaSourceAttacherHandler.containsRequest(binFile));
    }

    @Test
    public void testUpdateSourceAttachmentsReturnsCancelWhenDuplicateRequest() throws IOException, CoreException {
        File jar = SourceAttachTestSupport.resolveBundleEntryAsFile(TEST_JAR_BUNDLE_ID, TEST_JAR_PATH);
        JavaProjectSetup setup = SourceAttachTestSupport.createJavaProjectWithLibraries(
                "update-source-attachments-dup-" + UUID.randomUUID(), //$NON-NLS-1$
                projectsToDelete,
                new LibrarySpec(jar, null));

        IPackageFragmentRoot root = setup.roots().get(0);
        List<IPackageFragmentRoot> roots = new ArrayList<>();
        roots.add(root);

        String binFile = jar.getCanonicalPath();
        JavaSourceAttacherHandler.putRequest(binFile, root);

        Status status = (Status) JavaSourceAttacherHandler.updateSourceAttachments(roots, new CanceledMonitor());
        assertEquals(Status.CANCEL_STATUS, status);
    }

    private static IPath waitForSourceAttachment(IPackageFragmentRoot root, int attempts, long delayMs)
            throws InterruptedException, JavaModelException {
        IPath attached = root.getSourceAttachmentPath();
        for (int i = 0; i < attempts && (attached == null || !attached.toFile().exists()); i++) {
            Thread.sleep(delayMs);
            attached = root.getSourceAttachmentPath();
        }
        return attached;
    }

    private File createZipUnderTarget(String name) throws IOException {
        File targetDir = SourceAttachTestSupport.ensureTargetDir();
        File out = new File(targetDir, name);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(new byte[] { 0x50, 0x4b, 0x03, 0x04 });
        }
        filesToDelete.add(out);
        return out;
    }

    @Test
    public void testProcessLibSourcesSkipsDeleteOnExitForMavenRepoFiles() throws IOException, CoreException {
        File jar = SourceAttachTestSupport.resolveBundleEntryAsFile(TEST_JAR_BUNDLE_ID, TEST_JAR_PATH);

        // Create a zip file in a Maven repo location (simulate a cached source JAR)
        File mavenRepoDir = new File(SourceConstants.USER_M2_REPO_DIR,
                "io/github/nbauma109/test-artifact/1.0"); //$NON-NLS-1$
        mavenRepoDir.mkdirs();
        File mavenRepoSourceJar = new File(mavenRepoDir, "test-artifact-1.0-sources.jar"); //$NON-NLS-1$
        try (FileOutputStream fos = new FileOutputStream(mavenRepoSourceJar)) {
            fos.write(new byte[] { 0x50, 0x4b, 0x03, 0x04 });
        }
        filesToDelete.add(mavenRepoSourceJar);

        JavaProjectSetup setup = SourceAttachTestSupport.createJavaProjectWithLibraries(
                "process-maven-repo-" + UUID.randomUUID(), //$NON-NLS-1$
                projectsToDelete,
                new LibrarySpec(jar, null));

        IPackageFragmentRoot root = setup.roots().get(0);
        String binFile = jar.getAbsolutePath();

        JavaSourceAttacherHandler.putRequest(binFile, root);
        Set<String> notProcessed = new HashSet<>();
        notProcessed.add(binFile);

        SourceCodeFinder finder = new FinderWithDownloadUrl("https://example.com/test-artifact-1.0-sources.jar"); //$NON-NLS-1$
        // Use the Maven repo file as both source and tempSource so the isInMavenRepo branch is taken
        SourceFileResult result = new SourceFileResult(finder, binFile, mavenRepoSourceJar, mavenRepoSourceJar, 100);
        List<SourceFileResult> responses = new ArrayList<>();
        responses.add(result);

        // Should not throw - Maven repo files should not be marked for deletion
        JavaSourceAttacherHandler.processLibSources(notProcessed, responses);

        // The Maven repo source file should still exist (not deleted)
        assertTrue(mavenRepoSourceJar.exists());
    }

    private static final class FinderWithDownloadUrl implements SourceCodeFinder {
        private final String downloadUrl;

        FinderWithDownloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }

        @Override
        public void find(String binFile, String sha1, List<SourceFileResult> resultList) {
            // This test double only exposes a download URL and never needs to populate results.
        }

        @Override
        public void cancel() {
            // The test finder performs no background work, so there is nothing to cancel.
        }

        @Override
        public String getDownloadUrl() {
            return downloadUrl;
        }
    }

    private static final class NoDownloadUrlFinder implements SourceCodeFinder {
        @Override
        public void find(String binFile, String sha1, List<SourceFileResult> resultList) {
            // This test double exists only to model a finder without a download URL.
        }

        @Override
        public void cancel() {
            // The test finder performs no background work, so there is nothing to cancel.
        }

        @Override
        public String getDownloadUrl() {
            return null;
        }
    }

    private static final class CanceledMonitor implements IProgressMonitor {
        @Override
        public void beginTask(String name, int totalWork) {
            // The monitor only reports cancellation for tests, so progress callbacks are ignored.
        }

        @Override
        public void done() {
            // No completion bookkeeping is needed for this always-canceled monitor.
        }

        @Override
        public void internalWorked(double work) {
            // Fractional progress is irrelevant for this cancellation-focused test monitor.
        }

        @Override
        public boolean isCanceled() {
            return true;
        }

        @Override
        public void setCanceled(boolean value) {
            // The monitor is intentionally hard-coded as canceled regardless of caller input.
        }

        @Override
        public void setTaskName(String name) {
            // Task names are not observed in these tests.
        }

        @Override
        public void subTask(String name) {
            // Subtask names are not observed in these tests.
        }

        @Override
        public void worked(int work) {
            // Worked units are not observed in these tests.
        }
    }
}
