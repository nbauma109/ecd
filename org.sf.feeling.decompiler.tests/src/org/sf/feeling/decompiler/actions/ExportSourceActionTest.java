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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
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
        javaProject.setRawClasspath(JavaRuntime.getDefaultJREContainerEntry(), null);

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

        Optional<String> basePackage = layout.findPackageWithSubpackage();
        assertTrue("test.jar should contain at least one package that has a subpackage", basePackage.isPresent());

        IPackageFragment pkg = jarRoot.getPackageFragment(basePackage.get());
        assertNotNull(pkg);
        assertTrue(pkg.exists());

        ExportSourceAction action = new ExportSourceAction(new ArrayList());
        setBooleanField(action, "isFlat", true);

        Map classes = new HashMap();
        invokeCollectClasses(action, pkg, classes);

        assertTrue(classes.containsKey(pkg));

        Set<String> collected = new HashSet();
        Object[] keys = classes.keySet().toArray();
        for (int i = 0; i < keys.length; i++) {
            IPackageFragment key = (IPackageFragment) keys[i];
            collected.add(key.getElementName());
        }

        assertEquals("Flat mode should only collect the selected package", 1, collected.size());
        assertTrue(collected.contains(basePackage.get()));
    }

    @Test
    public void testCollectClassesFromPackageHierarchical() throws Exception {
        JarLayout layout = readJarLayout(jarFileOnDisk);
        assertNotNull(layout);

        Optional<PackagePair> pair = layout.findBaseAndSubpackagePair();
        assertTrue("test.jar should contain a base package and a subpackage", pair.isPresent());

        IPackageFragment base = jarRoot.getPackageFragment(pair.get().base);
        IPackageFragment sub = jarRoot.getPackageFragment(pair.get().sub);
        assertTrue(base.exists());
        assertTrue(sub.exists());

        ExportSourceAction action = new ExportSourceAction(new ArrayList());
        setBooleanField(action, "isFlat", false);

        Map classes = new HashMap();
        invokeCollectClasses(action, base, classes);

        Set<String> collected = new HashSet();
        Object[] keys = classes.keySet().toArray();
        for (int i = 0; i < keys.length; i++) {
            IPackageFragment key = (IPackageFragment) keys[i];
            collected.add(key.getElementName());
        }

        assertTrue("Hierarchical mode should include the selected base package", collected.contains(pair.get().base));
        assertTrue("Hierarchical mode should include subpackages", collected.contains(pair.get().sub));
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

        ExportSourceAction action = new ExportSourceAction(new ArrayList());
        setBooleanField(action, "isFlat", true);

        Map classes = new HashMap();
        invokeCollectClasses(action, classFile, classes);

        assertTrue(classes.containsKey(pkg));
        List list = (List) classes.get(pkg);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(classFile, list.get(0));
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

        org.eclipse.jdt.core.IClasspathEntry[] existing = project.getRawClasspath();
        org.eclipse.jdt.core.IClasspathEntry[] updated = new org.eclipse.jdt.core.IClasspathEntry[existing.length + 1];

        int i = 0;
        for (; i < existing.length; i++) {
            updated[i] = existing[i];
        }
        updated[i] = JavaCore.newLibraryEntry(jarPath, null, null);

        project.setRawClasspath(updated, null);
        IPackageFragmentRoot root = project.getPackageFragmentRoot(jarPath);
        root.open(null);
        return root;
    }

    private static void invokeCollectClasses(ExportSourceAction action, IJavaElement element, Map classes)
            throws Exception {
        Method method = ExportSourceAction.class.getDeclaredMethod("collectClasses", IJavaElement.class, Map.class,
                org.eclipse.core.runtime.IProgressMonitor.class);
        method.setAccessible(true);
        org.eclipse.core.runtime.IProgressMonitor monitor = new org.eclipse.core.runtime.NullProgressMonitor();
        method.invoke(action, element, classes, monitor);
    }

    private static void setBooleanField(Object target, String fieldName, boolean value) throws Exception {
        Field field = ExportSourceAction.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static JarLayout readJarLayout(File jarFile) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            Set<String> packages = new HashSet();
            List<ClassLocation> topLevelClasses = new ArrayList();

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }
                if (name.indexOf('$') >= 0) {
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

    private static final class PackagePair {
        private final String base;
        private final String sub;

        private PackagePair(String base, String sub) {
            this.base = base;
            this.sub = sub;
        }
    }

    private static final class ClassLocation {
        private final String packageName;
        private final String classFileName;

        private ClassLocation(String packageName, String classFileName) {
            this.packageName = packageName;
            this.classFileName = classFileName;
        }
    }

    private static final class JarLayout {
        private final Set<String> packages;
        private final List<ClassLocation> topLevelClasses;

        private JarLayout(Set<String> packages, List<ClassLocation> topLevelClasses) {
            this.packages = packages;
            this.topLevelClasses = topLevelClasses;
        }

        private Optional<String> findPackageWithSubpackage() {
            Optional<PackagePair> pair = findBaseAndSubpackagePair();
            return pair.map(p -> p.base);
        }

        private Optional<PackagePair> findBaseAndSubpackagePair() {
            String[] all = packages.toArray(new String[0]);
            for (int i = 0; i < all.length; i++) {
                String base = all[i];
                if (base == null) {
                    continue;
                }
                if (base.isEmpty()) {
                    continue;
                }

                String prefix = base + ".";
                for (int j = 0; j < all.length; j++) {
                    String candidate = all[j];
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
