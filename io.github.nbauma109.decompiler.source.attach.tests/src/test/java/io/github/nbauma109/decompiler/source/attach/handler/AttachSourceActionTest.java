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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.junit.After;
import org.junit.Test;

import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport;
import io.github.nbauma109.decompiler.source.attach.testutil.SourceAttachTestSupport.LibrarySpec;

public class AttachSourceActionTest {

    private static final String TEST_JAR_BUNDLE_ID = "io.github.nbauma109.decompiler.source.attach.tests"; //$NON-NLS-1$
    private static final String TEST_JAR_PATH = "target/lib/commons-io.jar"; //$NON-NLS-1$

    private final List<IProject> projectsToDelete = new ArrayList<>();
    private final List<File> filesToDelete = new ArrayList<>();

    @Test
    public void testActionUsesLocalizedLabel() {
        AttachSourceAction action = new AttachSourceAction(new ArrayList<>());

        assertEquals("Attach Library Source", action.getText()); //$NON-NLS-1$
    }

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

        JavaSourceAttacherHandler.clearRequests();
    }

    @Test
    public void testRunWithRootSelectionAttachesSource() throws IOException, CoreException {
        File jar = SourceAttachTestSupport.resolveBundleEntryAsFile(TEST_JAR_BUNDLE_ID, TEST_JAR_PATH);
        SourceJarCandidates candidates = prepareSourceJarCandidates(jar);

        SingleJarProjectSetup setup = createJavaProjectWithLibrary("attach-source-action-root-" + UUID.randomUUID(), jar); //$NON-NLS-1$
        IPackageFragmentRoot root = setup.root();
        assertNotNull(root);
        assertTrue(root.exists());

        List<Object> selection = new ArrayList<>();
        selection.add(root);

        new AttachSourceAction(selection).run();

        IPath attachedPath = waitForSourceAttachment(root, 20, 250);
        assertNotNull(attachedPath);
        assertTrue(attachedPath.toFile().exists());
        assertTrue(candidates.matches(attachedPath.toFile()));
    }

    @Test
    public void testRunWithClassFileSelectionAttachesSource() throws IOException, CoreException {
        File jar = SourceAttachTestSupport.resolveBundleEntryAsFile(TEST_JAR_BUNDLE_ID, TEST_JAR_PATH);
        SourceJarCandidates candidates = prepareSourceJarCandidates(jar);

        SingleJarProjectSetup setup = createJavaProjectWithLibrary("attach-source-action-classfile-" + UUID.randomUUID(), jar); //$NON-NLS-1$
        IPackageFragmentRoot root = setup.root();
        assertNotNull(root);
        assertTrue(root.exists());

        IClassFile classFile = SourceAttachTestSupport.findAnyClassFileInRoot(root);

        List<Object> selection = new ArrayList<>();
        selection.add(classFile);

        new AttachSourceAction(selection).run();

        IPath attachedPath = waitForSourceAttachment(root, 20, 250);
        assertNotNull(attachedPath);
        assertTrue(attachedPath.toFile().exists());
        assertTrue(candidates.matches(attachedPath.toFile()));
    }

    @Test
    public void testRunWithPackageFragmentSelectionAttachesSource() throws IOException, CoreException {
        File jar = SourceAttachTestSupport.resolveBundleEntryAsFile(TEST_JAR_BUNDLE_ID, TEST_JAR_PATH);
        SourceJarCandidates candidates = prepareSourceJarCandidates(jar);

        SingleJarProjectSetup setup = createJavaProjectWithLibrary("attach-source-action-package-" + UUID.randomUUID(), jar); //$NON-NLS-1$
        IPackageFragmentRoot root = setup.root();
        assertNotNull(root);
        assertTrue(root.exists());

        IPackageFragment pkg = SourceAttachTestSupport.findAnyPackageWithClasses(root);

        List<Object> selection = new ArrayList<>();
        selection.add(pkg);
        selection.add(new Object());

        new AttachSourceAction(selection).run();

        IPath attachedPath = waitForSourceAttachment(root, 20, 250);
        assertNotNull(attachedPath);
        assertTrue(attachedPath.toFile().exists());
        assertTrue(candidates.matches(attachedPath.toFile()));
    }

    private static IPath waitForSourceAttachment(IPackageFragmentRoot root, int attempts, long delayMs)
            throws JavaModelException {
        IPath attached = root.getSourceAttachmentPath();
        for (int i = 0; i < attempts && (attached == null || !attached.toFile().exists()); i++) {
            java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(delayMs));
            attached = root.getSourceAttachmentPath();
        }
        return attached;
    }

    private SingleJarProjectSetup createJavaProjectWithLibrary(String projectName, File binaryJar) throws CoreException {
        SourceAttachTestSupport.JavaProjectSetup setup = SourceAttachTestSupport.createJavaProjectWithLibraries(projectName,
                projectsToDelete, new LibrarySpec(binaryJar, null));
        return new SingleJarProjectSetup(setup.project(), setup.roots().get(0));
    }

    private SourceJarCandidates prepareSourceJarCandidates(File binaryJar) {
        List<File> candidates = new ArrayList<>();

        addCandidate(candidates, new File(binaryJar.getParentFile(), "commons-io-sources.jar")); //$NON-NLS-1$

        Optional<MavenCoordinates> coords = readMavenCoordinates(binaryJar);
        if (coords.isPresent()) {
            MavenCoordinates c = coords.get();

            addCandidate(candidates,
                    new File(binaryJar.getParentFile(), c.artifactId() + "-" + c.version() + "-sources.jar")); //$NON-NLS-1$ //$NON-NLS-2$

            addCandidate(candidates,
                    new File(getM2RepoRoot(), toM2RelativePath(c.groupId(), c.artifactId(), c.version(), true)));
        }

        return new SourceJarCandidates(candidates);
    }

    private void addCandidate(List<File> candidates, File candidate) {
        if (candidate == null) {
            return;
        }

        if (!candidate.exists()) {
            try {
                ensureParentDirectory(candidate);
                createMinimalValidSourceJar(candidate);
                filesToDelete.add(candidate);
            } catch (IOException e) {
                return;
            }
        }

        if (candidate.exists() && candidate.isFile() && candidate.length() > 0) {
            candidates.add(candidate);
        }
    }

    private static void ensureParentDirectory(File file) throws IOException {
        File parent = file.getParentFile();
        if ((parent == null) || parent.exists()) {
            return;
        }
        if (!parent.mkdirs() && !parent.exists()) {
            throw new IOException("Unable to create directory: " + parent.getAbsolutePath()); //$NON-NLS-1$
        }
    }

    private static void createMinimalValidSourceJar(File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
                JarOutputStream jos = new JarOutputStream(fos)) {

            JarEntry entry = new JarEntry("p/Dummy.java"); //$NON-NLS-1$
            jos.putNextEntry(entry);
            jos.write("package p; public class Dummy {}".getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
            jos.closeEntry();
        }
    }

    private static File getM2RepoRoot() {
        String userHome = System.getProperty("user.home"); //$NON-NLS-1$
        return new File(new File(userHome, ".m2"), "repository"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String toM2RelativePath(String groupId, String artifactId, String version, boolean sources) {
        String groupPath = groupId.replace('.', File.separatorChar);
        String fileName = artifactId + "-" + version + (sources ? "-sources.jar" : ".jar"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return groupPath + File.separator + artifactId + File.separator + version + File.separator + fileName;
    }

    private static Optional<MavenCoordinates> readMavenCoordinates(File binaryJar) {
        try (JarFile jarFile = new JarFile(binaryJar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if ((name == null) || !name.startsWith("META-INF/maven/") || !name.endsWith("/pom.properties")) { //$NON-NLS-1$ //$NON-NLS-2$
                    continue;
                }

                Properties props = new Properties();
                try (InputStream in = jarFile.getInputStream(entry)) {
                    props.load(in);
                }

                String groupId = props.getProperty("groupId"); //$NON-NLS-1$
                String artifactId = props.getProperty("artifactId"); //$NON-NLS-1$
                String version = props.getProperty("version"); //$NON-NLS-1$
                if (groupId != null && artifactId != null && version != null) {
                    return Optional.of(new MavenCoordinates(groupId, artifactId, version));
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private record SourceJarCandidates(List<File> candidates) {

        public boolean matches(File attached) throws IOException {
            if (attached == null || !attached.exists()) {
                return false;
            }
            if (candidates == null || candidates.isEmpty()) {
                return true;
            }

            String attachedCanonical = attached.getCanonicalPath();
            for (File candidate : candidates) {
                if (candidate != null && candidate.exists()) {
                    String candidateCanonical = candidate.getCanonicalPath();
                    if (attachedCanonical.equals(candidateCanonical)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private record MavenCoordinates(String groupId, String artifactId, String version) {
    }

    private record SingleJarProjectSetup(IProject project, IPackageFragmentRoot root) {
    }
}
