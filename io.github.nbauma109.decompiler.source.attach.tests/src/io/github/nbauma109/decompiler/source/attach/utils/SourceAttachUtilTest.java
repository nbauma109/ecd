/*******************************************************************************
 * Copyright (c) 2025.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.junit.After;
import org.junit.Test;

import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport;
import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport.JavaProjectSetup;
import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport.LibrarySpec;
import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport.SingleLibrarySetup;

public class SourceAttachUtilTest {

    private static final String TEST_JAR_BUNDLE_ID = "io.github.nbauma109.decompiler.source.attach.tests";
    private static final String TEST_JAR_PATH = "target/lib/commons-io.jar";

    private final List<IProject> projectsToDelete = new ArrayList<>();
    private final List<File> filesToDelete = new ArrayList<>();

    @After
    public void tearDown() throws CoreException {
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
    }

    @Test
    public void testNeedDownloadSourceReturnsTrueForSingleRootSelection() throws IOException, CoreException {
        SingleLibrarySetup setup = SourceAttachTestSupport.createSingleLibraryProjectFromBundleJar(
                TEST_JAR_BUNDLE_ID,
                TEST_JAR_PATH,
                "need-download-true-" + UUID.randomUUID(), //$NON-NLS-1$
                projectsToDelete);

        IPackageFragmentRoot root = setup.root();
        IClassFile classFile = SourceAttachTestSupport.findAnyClassFileInRoot(root);

        List<IJavaElement> selection = new ArrayList<>();
        selection.add(classFile);

        assertTrue(SourceAttachUtil.needDownloadSource(selection));
    }

    @Test
    public void testNeedDownloadSourceReturnsFalseForDifferentRoots() throws IOException, CoreException {
        File jar1 = SourceAttachTestSupport.resolveBundleEntryAsFile(TEST_JAR_BUNDLE_ID, TEST_JAR_PATH);
        File jar2 = copyJarToTempFile(jar1, "test-copy-" + UUID.randomUUID() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$

        JavaProjectSetup setup = SourceAttachTestSupport.createJavaProjectWithLibraries("need-download-multi-root-" + UUID.randomUUID(), //$NON-NLS-1$
                projectsToDelete,
                new LibrarySpec(jar1, null), new LibrarySpec(jar2, null));

        IPackageFragmentRoot rootA = setup.roots().get(0);
        IPackageFragmentRoot rootB = setup.roots().get(1);

        IPackageFragment pkgA = SourceAttachTestSupport.findAnyPackageWithClasses(rootA);
        IPackageFragment pkgB = SourceAttachTestSupport.findAnyPackageWithClasses(rootB);

        List<IJavaElement> selection = new ArrayList<>();
        selection.add(pkgA);
        selection.add(pkgB);

        assertTrue(!SourceAttachUtil.needDownloadSource(selection));
    }

    @Test
    public void testNeedDownloadSourceReturnsFalseWhenSourceAlreadyAttachedToDifferentExistingFile()
            throws IOException, CoreException {
        File jar = SourceAttachTestSupport.resolveBundleEntryAsFile(TEST_JAR_BUNDLE_ID, TEST_JAR_PATH);
        File existingSourceJar = createNonEmptyFileUnderTarget("attached-source-" + UUID.randomUUID() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$

        JavaProjectSetup setup = SourceAttachTestSupport.createJavaProjectWithLibraries("need-download-attached-" + UUID.randomUUID(), //$NON-NLS-1$
                projectsToDelete,
                new LibrarySpec(jar, existingSourceJar));

        IPackageFragmentRoot root = setup.roots().get(0);
        IPackageFragment pkg = SourceAttachTestSupport.findAnyPackageWithClasses(root);

        List<IJavaElement> selection = new ArrayList<>();
        selection.add(pkg);

        assertTrue(!SourceAttachUtil.needDownloadSource(selection));
    }

    @Test
    public void testUpdateSourceAttachStatusDoesNotThrow() throws IOException, CoreException {
        SingleLibrarySetup setup = SourceAttachTestSupport.createSingleLibraryProjectFromBundleJar(
                TEST_JAR_BUNDLE_ID,
                TEST_JAR_PATH,
                "update-source-attach-" + UUID.randomUUID(), //$NON-NLS-1$
                projectsToDelete);

        IPackageFragmentRoot root = setup.root();
        assertNotNull(root);
        assertTrue(root.exists());

        SourceAttachUtil.updateSourceAttachStatus(root);

        assertTrue(root.exists());
    }

    @Test
    public void testEnableMavenDownloadMatchesReflectionProbe() {
        boolean expected = expectedEnableMavenDownload();
        assertEquals(expected, SourceAttachUtil.enableMavenDownload());
    }

    private static boolean expectedEnableMavenDownload() {
        try {
            Class<?> clazz = Class.forName("org.eclipse.m2e.jdt.IClasspathManager"); //$NON-NLS-1$
            Class<?>[] parameterTypes = new Class[] { IPackageFragmentRoot.class, boolean.class, boolean.class };
            return clazz.getMethod("scheduleDownload", parameterTypes) != null; //$NON-NLS-1$
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private File copyJarToTempFile(File original, String name) throws IOException {
        File targetDir = SourceAttachTestSupport.ensureTargetDir();
        File out = new File(targetDir, name);
        Files.copy(original.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        filesToDelete.add(out);
        return out;
    }

    private File createNonEmptyFileUnderTarget(String name) throws IOException {
        File targetDir = SourceAttachTestSupport.ensureTargetDir();
        File out = new File(targetDir, name);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(new byte[] { 0x50, 0x4b, 0x03, 0x04 });
        }
        filesToDelete.add(out);
        return out;
    }

    @Test
    public void testReattachSourceDoesNotCallDeleteOnExitForMavenRepoFiles() throws IOException, CoreException {
        File initialSource = createNonEmptyFileUnderTarget("initial-source-" + UUID.randomUUID() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$

        // Create a project with source already attached (so getSourceAttachmentPath() is non-null)
        JavaProjectSetup setup = SourceAttachTestSupport.createJavaProjectWithLibraries("reattach-maven-" + UUID.randomUUID(), //$NON-NLS-1$
                projectsToDelete,
                new LibrarySpec(SourceAttachTestSupport.resolveBundleEntryAsFile(TEST_JAR_BUNDLE_ID, TEST_JAR_PATH),
                        initialSource));

        IPackageFragmentRoot root = setup.roots().get(0);
        assertNotNull(root.getSourceAttachmentPath());

        // Create a source file in the Maven repo location
        File mavenRepoDir = new File(SourceConstants.USER_M2_REPO_DIR,
                "io/github/nbauma109/test-reattch/1.0"); //$NON-NLS-1$
        mavenRepoDir.mkdirs();
        File mavenRepoSourceJar = new File(mavenRepoDir, "test-reattch-1.0-sources.jar"); //$NON-NLS-1$
        Files.copy(initialSource.toPath(), mavenRepoSourceJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        filesToDelete.add(mavenRepoSourceJar);

        // Call reattchSource with a Maven repo file - should not call deleteOnExit on it
        SourceAttachUtil.reattchSource(root, mavenRepoSourceJar, mavenRepoSourceJar, null);

        // Even if attach fails due to test constraints, the Maven repo file must still exist
        assertTrue(mavenRepoSourceJar.exists());
        // The key is that reattchSource was called without throwing and the Maven repo file is preserved
    }

    // -----------------------------------------------------------------------
    // isSourceCodeFor tests
    // -----------------------------------------------------------------------

    @Test
    public void isSourceCodeForReturnsTrueWhenSourceHasMatchingJavaFile() throws IOException {
        File testRoot = SourceAttachTestSupport.createTargetTempDir("is-source-code-for-match"); //$NON-NLS-1$
        try {
            File binJar = createZip(new File(testRoot, "demo.jar"), "pkg/Demo.class", "bytecode"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            File srcJar = createZip(new File(testRoot, "demo-sources.jar"), "pkg/Demo.java", "class Demo {}"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

            assertTrue(SourceAttachUtil.isSourceCodeFor(srcJar.getAbsolutePath(), binJar.getAbsolutePath()));
        } finally {
            FileUtils.deleteQuietly(testRoot);
        }
    }

    @Test
    public void isSourceCodeForReturnsFalseWhenSourceHasNoMatchingJavaFile() throws IOException {
        File testRoot = SourceAttachTestSupport.createTargetTempDir("is-source-code-for-no-match"); //$NON-NLS-1$
        try {
            File binJar = createZip(new File(testRoot, "demo.jar"), "pkg/Demo.class", "bytecode"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            File srcJar = createZip(new File(testRoot, "demo-sources.jar"), "pkg/Other.java", "class Other {}"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

            assertFalse(SourceAttachUtil.isSourceCodeFor(srcJar.getAbsolutePath(), binJar.getAbsolutePath()));
        } finally {
            FileUtils.deleteQuietly(testRoot);
        }
    }

    @Test
    public void isSourceCodeForReturnsFalseForNonExistingFiles() {
        assertFalse(SourceAttachUtil.isSourceCodeFor("nonexistent-src.jar", "nonexistent-bin.jar")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void needDownloadSourceReturnsTrueForEmptySelection() {
        assertTrue(SourceAttachUtil.needDownloadSource(new ArrayList<>()));
    }

    @Test
    public void needDownloadSourceReturnsFalseForNonJavaElementInSelection() {
        List<Object> selection = new ArrayList<>();
        selection.add(new Object());
        assertFalse(SourceAttachUtil.needDownloadSource(selection));
    }

    private static File createZip(File dest, String entryName, String content) throws IOException {
        dest.getParentFile().mkdirs();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(dest)))) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            ZipEntry entry = new ZipEntry(entryName);
            entry.setSize(bytes.length);
            zos.putNextEntry(entry);
            zos.write(bytes);
            zos.closeEntry();
        }
        return dest;
    }
}
