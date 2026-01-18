/*******************************************************************************
 * Copyright (c) 2025.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;

public class ExportSourceActionTest {

    private static final String TEST_BUNDLE_ID = "org.sf.feeling.decompiler.tests";
    private static final String TEST_JAR_PATH = "resources/test.jar";

    private IProject project;
    private IJavaProject javaProject;
    private IPackageFragmentRoot jarRoot;
    private File jarFileOnDisk;

    @Before
    public void setUp() throws Exception {
        jarFileOnDisk = resolveTestJar();
        assertNotNull(jarFileOnDisk);
        assertTrue(jarFileOnDisk.exists());
        assertTrue(jarFileOnDisk.isFile());

        String projectName = "export-source-action-test-project";
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        project = root.getProject(projectName);

        if (project.exists()) {
            project.delete(true, true, null);
        }

        project.create(null);
        project.open(null);

        IProjectDescription description = project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, null);

        javaProject = JavaCore.create(project);
        configureClasspathWithJre(javaProject);

        jarRoot = addJarToClasspathAndGetRoot(javaProject, jarFileOnDisk);
        assertNotNull(jarRoot);
        assertTrue(jarRoot.exists());
    }

    @After
    public void tearDown() throws Exception {
        if (project != null && project.exists()) {
            project.delete(true, true, null);
        }
    }

    @Test
    public void testCollectClassesFromPackageFlat() throws Exception {
        JarLayout layout = readJarLayout(jarFileOnDisk);
        assertNotNull(layout);

        String anyPackage = layout.findAnyPackage().orElse("");
        IPackageFragment pkg = jarRoot.getPackageFragment(anyPackage);
        assertNotNull(pkg);
        assertTrue(pkg.exists());

        ExportSourceAction action = new ExportSourceAction(new ArrayList<>());
        action.setFlat(true);

        Map<IJavaElement, List<IJavaElement>> classes = new HashMap<>();
        action.collectClasses(pkg, classes, new NullProgressMonitor());

        assertTrue(classes.containsKey(pkg));

        Set<String> collected = new HashSet<>();
        Object[] keys = classes.keySet().toArray();
        for (Object o : keys) {
            IPackageFragment key = (IPackageFragment) o;
            collected.add(key.getElementName());
        }

        assertEquals("Flat mode should only collect the selected package", 1, collected.size());
        assertTrue(collected.contains(anyPackage));
    }

    @Test
    public void testCollectClassesFromPackageHierarchical() throws Exception {
        JarLayout layout = readJarLayout(jarFileOnDisk);
        assertNotNull(layout);

        Optional<PackagePair> pair = layout.findBaseAndSubpackagePair();

        String selectedPackage = pair.map(p -> p.base).orElseGet(() -> layout.findAnyPackage().orElse(""));
        IPackageFragment base = jarRoot.getPackageFragment(selectedPackage);
        assertTrue(base.exists());

        ExportSourceAction action = new ExportSourceAction(new ArrayList<>());
        action.setFlat(false);

        Map<IJavaElement, List<IJavaElement>> classes = new HashMap<>();
        action.collectClasses(base, classes, new NullProgressMonitor());

        Set<String> collected = new HashSet<>();
        Object[] keys = classes.keySet().toArray();
        for (Object o : keys) {
            IPackageFragment key = (IPackageFragment) o;
            collected.add(key.getElementName());
        }

        assertTrue("Hierarchical mode should include the selected package", collected.contains(selectedPackage));

        if (pair.isPresent()) {
            assertTrue("Hierarchical mode should include subpackages when present", collected.contains(pair.get().sub));
            assertTrue("Hierarchical mode should collect at least base and subpackage", collected.size() >= 2);
        } else {
            assertEquals("When no subpackages exist, hierarchical mode should behave like flat collection for the selected package",
                    1, collected.size());
        }
    }

    @Test
    public void testCollectClassesFromSingleClassFile() throws Exception {
        JarLayout layout = readJarLayout(jarFileOnDisk);
        assertNotNull(layout);

        Optional<ClassLocation> anyTopLevel = layout.findAnyTopLevelClass();
        assertTrue("test.jar should contain at least one top-level .class", anyTopLevel.isPresent());

        IPackageFragment pkg = jarRoot.getPackageFragment(anyTopLevel.get().packageName);
        assertTrue(pkg.exists());

        IClassFile classFile = pkg.getClassFile(anyTopLevel.get().classFileName);
        assertNotNull(classFile);
        assertTrue(classFile.exists());

        ExportSourceAction action = new ExportSourceAction(new ArrayList<>());
        action.setFlat(true);

        Map<IJavaElement, List<IJavaElement>> classes = new HashMap<>();
        action.collectClasses(classFile, classes, new NullProgressMonitor());

        assertTrue(classes.containsKey(pkg));
        List<IJavaElement> list = classes.get(pkg);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(classFile, list.get(0));
    }

    private static void configureClasspathWithJre(IJavaProject project) throws JavaModelException {
        IClasspathEntry[] classpath = { JavaRuntime.getDefaultJREContainerEntry() };
        project.setRawClasspath(classpath, null);
    }

    private static File resolveTestJar() throws Exception {
        Bundle bundle = Platform.getBundle(TEST_BUNDLE_ID);
        assertNotNull("Test bundle must be available: " + TEST_BUNDLE_ID, bundle);

        URL entry = bundle.getEntry(TEST_JAR_PATH);
        assertNotNull("Missing test.jar at: " + TEST_JAR_PATH, entry);

        URL resolved = FileLocator.toFileURL(entry);
        IPath path = new Path(resolved.getPath());
        return path.toFile();
    }

    private static IPackageFragmentRoot addJarToClasspathAndGetRoot(IJavaProject project, File jar) throws Exception {
        IPath jarPath = new Path(jar.getAbsolutePath());

        IClasspathEntry[] existing = project.getRawClasspath();
        IClasspathEntry[] updated = new IClasspathEntry[existing.length + 1];

        int i = 0;
        for (; i < existing.length; i++) {
            updated[i] = existing[i];
        }
        updated[i] = JavaCore.newLibraryEntry(jarPath, null, null);

        project.setRawClasspath(updated, null);

        return findJarPackageFragmentRoot(project, jarPath).orElseThrow(() -> new IllegalStateException(
                "Unable to locate package fragment root for jar: " + jarPath.toOSString()));
    }

    private static Optional<IPackageFragmentRoot> findJarPackageFragmentRoot(IJavaProject project, IPath jarPath)
            throws JavaModelException {
        IPackageFragmentRoot[] roots = project.getAllPackageFragmentRoots();
        for (IPackageFragmentRoot root : roots) {
            IPath rootPath = root.getPath();
            if (rootPath != null && rootPath.equals(jarPath)) {
                root.open(null);
                return Optional.of(root);
            }
        }
        return Optional.empty();
    }

    private static JarLayout readJarLayout(File jarFile) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            Set<String> packages = new HashSet<>();
            List<ClassLocation> topLevelClasses = new ArrayList<>();

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (!name.endsWith(".class") || name.indexOf('$') >= 0) {
                    continue;
                }

                int lastSlash = name.lastIndexOf('/');
                String packageName = "";
                String fileName = name;
                if (lastSlash >= 0) {
                    packageName = name.substring(0, lastSlash).replace('/', '.');
                    fileName = name.substring(lastSlash + 1);
                }

                packages.add(packageName);
                topLevelClasses.add(new ClassLocation(packageName, fileName));
            }

            return new JarLayout(packages, topLevelClasses);
        }
    }

    private record PackagePair(String base, String sub) {
    }

    private record ClassLocation(String packageName, String classFileName) {
    }

    private record JarLayout(Set<String> packages, List<ClassLocation> topLevelClasses) {

        private Optional<String> findAnyPackage() {
            if (packages.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(packages.iterator().next());
        }

        private Optional<PackagePair> findBaseAndSubpackagePair() {
            String[] all = packages.toArray(new String[0]);
            for (String base : all) {
                if (base == null || base.isEmpty()) {
                    continue;
                }

                String prefix = base + ".";
                for (String candidate : all) {
                    if (candidate == null) {
                        continue;
                    }
                    if (candidate.startsWith(prefix)) {
                        return Optional.of(new PackagePair(base, candidate));
                    }
                }
            }
            return Optional.empty();
        }

        private Optional<ClassLocation> findAnyTopLevelClass() {
            if (topLevelClasses.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(topLevelClasses.get(0));
        }
    }
}
