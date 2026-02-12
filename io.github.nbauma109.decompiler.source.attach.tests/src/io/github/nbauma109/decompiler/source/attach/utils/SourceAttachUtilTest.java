/*******************************************************************************
 * Copyright (c) 2025.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.junit.After;
import org.junit.Test;
import org.osgi.framework.Bundle;

public class SourceAttachUtilTest {

    private static final String TEST_JAR_BUNDLE_ID = "io.github.nbauma109.decompiler.source.attach.tests";
    private static final String TEST_JAR_PATH = "target/lib/commons-io.jar";

    private final List<IProject> projectsToDelete = new ArrayList<>();
    private final List<File> filesToDelete = new ArrayList<>();

    @After
    public void tearDown() throws Exception {
        for (int i = 0; i < projectsToDelete.size(); i++) {
            IProject p = projectsToDelete.get(i);
            if (p != null && p.exists()) {
                p.delete(true, true, null);
            }
        }
        projectsToDelete.clear();

        for (File f : filesToDelete) {
            if (f != null && f.exists()) {
                deleteRecursively(f);
            }
        }
        filesToDelete.clear();
    }

    @Test
    public void testNeedDownloadSourceReturnsTrueForSingleRootSelection() throws Exception {
        File jar = resolveTestJar();
        JavaProjectSetup setup = createJavaProjectWithLibraries("need-download-true-" + UUID.randomUUID(), //$NON-NLS-1$
                new LibrarySpec(jar, null));

        IPackageFragmentRoot root = setup.roots().get(0);
        IClassFile classFile = findAnyClassFileInRoot(root);

        List<IJavaElement> selection = new ArrayList<>();
        selection.add(classFile);

        assertTrue(SourceAttachUtil.needDownloadSource(selection));
    }

    @Test
    public void testNeedDownloadSourceReturnsFalseForDifferentRoots() throws Exception {
        File jar1 = resolveTestJar();
        File jar2 = copyJarToTempFile(jar1, "test-copy-" + UUID.randomUUID() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$

        JavaProjectSetup setup = createJavaProjectWithLibraries("need-download-multi-root-" + UUID.randomUUID(), //$NON-NLS-1$
                new LibrarySpec(jar1, null), new LibrarySpec(jar2, null));

        IPackageFragmentRoot rootA = setup.roots().get(0);
        IPackageFragmentRoot rootB = setup.roots().get(1);

        IPackageFragment pkgA = findAnyPackageWithClasses(rootA);
        IPackageFragment pkgB = findAnyPackageWithClasses(rootB);

        List<IJavaElement> selection = new ArrayList<>();
        selection.add(pkgA);
        selection.add(pkgB);

        assertTrue(!SourceAttachUtil.needDownloadSource(selection));
    }

    @Test
    public void testNeedDownloadSourceReturnsFalseWhenSourceAlreadyAttachedToDifferentExistingFile() throws Exception {
        File jar = resolveTestJar();
        File existingSourceJar = createNonEmptyFileUnderTarget("attached-source-" + UUID.randomUUID() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$

        JavaProjectSetup setup = createJavaProjectWithLibraries("need-download-attached-" + UUID.randomUUID(), //$NON-NLS-1$
                new LibrarySpec(jar, existingSourceJar));

        IPackageFragmentRoot root = setup.roots().get(0);
        IPackageFragment pkg = findAnyPackageWithClasses(root);

        List<IJavaElement> selection = new ArrayList<>();
        selection.add(pkg);

        assertTrue(!SourceAttachUtil.needDownloadSource(selection));
    }

    @Test
    public void testUpdateSourceAttachStatusDoesNotThrow() throws Exception {
        File jar = resolveTestJar();
        JavaProjectSetup setup = createJavaProjectWithLibraries("update-source-attach-" + UUID.randomUUID(), //$NON-NLS-1$
                new LibrarySpec(jar, null));

        IPackageFragmentRoot root = setup.roots().get(0);
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
        } catch (Exception e) {
            return false;
        }
    }

    private File resolveTestJar() throws Exception {
        Bundle bundle = Platform.getBundle(TEST_JAR_BUNDLE_ID);
        assertNotNull(bundle);

        URL entry = bundle.getEntry(TEST_JAR_PATH);
        assertNotNull(entry);

        URL resolved = FileLocator.toFileURL(entry);
        IPath path = new Path(resolved.getPath());
        File file = path.toFile();
        assertTrue(file.exists());
        return file;
    }

    private JavaProjectSetup createJavaProjectWithLibraries(String projectName, LibrarySpec... libs) throws Exception {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);

        if (project.exists()) {
            project.delete(true, true, null);
        }
        project.create(null);
        project.open(null);
        projectsToDelete.add(project);

        IProjectDescription description = project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, null);

        IJavaProject javaProject = JavaCore.create(project);

        List<IClasspathEntry> entries = new ArrayList<>();
        for (int i = 0; i < libs.length; i++) {
            LibrarySpec lib = libs[i];
            IPath jarPath = new Path(lib.binaryJar().getAbsolutePath());

            IPath srcPath = null;
            if (lib.sourceJarOrZip() != null) {
                srcPath = new Path(lib.sourceJarOrZip().getAbsolutePath());
            }

            IClasspathEntry libEntry = JavaCore.newLibraryEntry(jarPath, srcPath, null);
            entries.add(libEntry);
        }

        javaProject.setRawClasspath(entries.toArray(new IClasspathEntry[0]), null);

        List<IPackageFragmentRoot> roots = new ArrayList<>();
        IPackageFragmentRoot[] allRoots = javaProject.getAllPackageFragmentRoots();

        for (int i = 0; i < libs.length; i++) {
            File jar = libs[i].binaryJar();
            IPath jarPath = new Path(jar.getAbsolutePath());

            IPackageFragmentRoot match = null;
            for (int r = 0; r < allRoots.length; r++) {
                IPackageFragmentRoot candidate = allRoots[r];
                IPath candidatePath = candidate.getPath();
                if (candidatePath != null && candidatePath.equals(jarPath)) {
                    match = candidate;
                    break;
                }
            }
            assertNotNull("No package fragment root for jar: " + jar.getAbsolutePath(), match); //$NON-NLS-1$
            match.open(null);
            roots.add(match);
        }

        return new JavaProjectSetup(project, javaProject, roots);
    }

    private static IClassFile findAnyClassFileInRoot(IPackageFragmentRoot root) throws Exception {
        IJavaElement[] children = root.getChildren();
        for (int i = 0; i < children.length; i++) {
            if (children[i] instanceof IPackageFragment pkg) {
                IClassFile[] classFiles = pkg.getClassFiles();
                if (classFiles != null && classFiles.length > 0) {
                    return classFiles[0];
                }
            }
        }
        throw new IllegalStateException("No class file found in root: " + root.getElementName()); //$NON-NLS-1$
    }

    private static IPackageFragment findAnyPackageWithClasses(IPackageFragmentRoot root) throws Exception {
        IJavaElement[] children = root.getChildren();
        for (int i = 0; i < children.length; i++) {
            if (children[i] instanceof IPackageFragment pkg) {
                IClassFile[] classFiles = pkg.getClassFiles();
                if (classFiles != null && classFiles.length > 0) {
                    return pkg;
                }
            }
        }
        throw new IllegalStateException("No package with class files found in root: " + root.getElementName()); //$NON-NLS-1$
    }

    private File copyJarToTempFile(File original, String name) throws IOException {
        File targetDir = ensureTargetDir();
        File out = new File(targetDir, name);
        Files.copy(original.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        filesToDelete.add(out);
        return out;
    }

    private File createNonEmptyFileUnderTarget(String name) throws IOException {
        File targetDir = ensureTargetDir();
        File out = new File(targetDir, name);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(new byte[] { 0x50, 0x4b, 0x03, 0x04 });
        }
        filesToDelete.add(out);
        return out;
    }

    private static File ensureTargetDir() throws IOException {
        File target = new File("target"); //$NON-NLS-1$
        if (!target.exists() && !target.mkdirs() && !target.exists()) {
            throw new IOException("Unable to create target directory: " + target.getAbsolutePath()); //$NON-NLS-1$
        }
        return target;
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("Unable to delete: " + file.getAbsolutePath()); //$NON-NLS-1$
        }
    }

    public record LibrarySpec(File binaryJar, File sourceJarOrZip) {
    }

    public record JavaProjectSetup(IProject project, IJavaProject javaProject, List<IPackageFragmentRoot> roots) {
    }
}
