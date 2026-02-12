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
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.junit.After;
import org.junit.Test;
import org.osgi.framework.Bundle;

import io.github.nbauma109.decompiler.source.attach.finder.SourceCodeFinder;
import io.github.nbauma109.decompiler.source.attach.finder.SourceFileResult;

public class JavaSourceAttacherHandlerTest {

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

        JavaSourceAttacherHandler.clearRequests();
    }

    @Test
    public void testAttachSourceSetsAttachmentPath() throws Exception {
        File jar = resolveTestJar();
        File sourceJar = createZipUnderTarget("source-" + UUID.randomUUID() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$

        JavaProjectSetup setup = createJavaProjectWithLibraries(
                "attach-source-" + UUID.randomUUID(), //$NON-NLS-1$
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
    public void testProcessLibSourcesSkipsWhenDownloadUrlMissingAndFinderNotFacade() throws Exception {
        File jar = resolveTestJar();
        File sourceJar = createZipUnderTarget("source-skip-" + UUID.randomUUID() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$
        File tempSourceJar = createZipUnderTarget("temp-source-skip-" + UUID.randomUUID() + ".jar"); //$NON-NLS-1$ //$NON-NLS-2$

        JavaProjectSetup setup = createJavaProjectWithLibraries(
                "process-skip-" + UUID.randomUUID(), //$NON-NLS-1$
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
    public void testProcessLibSourcesIgnoresNullSource() throws Exception {
        File jar = resolveTestJar();

        JavaProjectSetup setup = createJavaProjectWithLibraries(
                "process-null-source-" + UUID.randomUUID(), //$NON-NLS-1$
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
    public void testUpdateSourceAttachmentsReturnsOkAndClearsRequests() throws Exception {
        File jar = resolveTestJar();
        JavaProjectSetup setup = createJavaProjectWithLibraries(
                "update-source-attachments-" + UUID.randomUUID(), //$NON-NLS-1$
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
    public void testUpdateSourceAttachmentsReturnsCancelWhenDuplicateRequest() throws Exception {
        File jar = resolveTestJar();
        JavaProjectSetup setup = createJavaProjectWithLibraries(
                "update-source-attachments-dup-" + UUID.randomUUID(), //$NON-NLS-1$
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
            throws Exception {
        IPath attached = root.getSourceAttachmentPath();
        for (int i = 0; i < attempts && (attached == null || !attached.toFile().exists()); i++) {
            Thread.sleep(delayMs);
            attached = root.getSourceAttachmentPath();
        }
        return attached;
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

    private File createZipUnderTarget(String name) throws IOException {
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

    private static final class NoDownloadUrlFinder implements SourceCodeFinder {
        @Override
        public void find(String binFile, String sha1, List<SourceFileResult> resultList) {
        }

        @Override
        public void cancel() {
        }

        @Override
        public String getDownloadUrl() {
            return null;
        }
    }

    private static final class CanceledMonitor implements IProgressMonitor {
        @Override
        public void beginTask(String name, int totalWork) {
        }

        @Override
        public void done() {
        }

        @Override
        public void internalWorked(double work) {
        }

        @Override
        public boolean isCanceled() {
            return true;
        }

        @Override
        public void setCanceled(boolean value) {
        }

        @Override
        public void setTaskName(String name) {
        }

        @Override
        public void subTask(String name) {
        }

        @Override
        public void worked(int work) {
        }
    }
}
