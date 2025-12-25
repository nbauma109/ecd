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
import java.io.IOException;
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
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.preference.IPreferenceStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;

public class ExportSourceActionTest {

    private static final String TEST_BUNDLE_ID = "org.sf.feeling.decompiler.tests";
    private static final String TEST_JAR_PATH = "resources/test.jar";
    private static final String DECOMPILER_FERNFLOWER = "FernFlower"; //$NON-NLS-1$

    private IProject project;
    private IJavaProject javaProject;
    private IPackageFragmentRoot jarRoot;
    private File jarFileOnDisk;
    private File tempDir;

    @Before
    public void setUp() throws Exception {
        jarFileOnDisk = resolveTestJar();
        assertNotNull(jarFileOnDisk);
        assertTrue(jarFileOnDisk.exists());
        assertTrue(jarFileOnDisk.isFile());

        tempDir = createTempDirUnderTarget("ecd-test-tmp-" + UUID.randomUUID()); //$NON-NLS-1$
        assertNotNull(tempDir);
        assertTrue(tempDir.exists());
        assertTrue(tempDir.isDirectory());

        configureExportPreferences(tempDir);

        String projectName = "export-source-action-test-project"; //$NON-NLS-1$
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
        if (tempDir != null && tempDir.exists()) {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testCollectClassesFromPackageFlat() throws Exception {
        JarLayout layout = readJarLayout(jarFileOnDisk);
        assertNotNull(layout);

        String anyPackage = layout.findAnyPackage().orElse(""); //$NON-NLS-1$
        IPackageFragment pkg = jarRoot.getPackageFragment(anyPackage);
        assertNotNull(pkg);
        assertTrue(pkg.exists());

        ExportSourceAction action = new ExportSourceAction(new ArrayList());
        setBooleanField(action, "isFlat", true); //$NON-NLS-1$

        Map classes = new HashMap();
        invokeCollectClasses(action, pkg, classes);

        assertTrue(classes.containsKey(pkg));

        Set<String> collected = new HashSet();
        Object[] keys = classes.keySet().toArray();
        for (int i = 0; i < keys.length; i++) {
            IPackageFragment key = (IPackageFragment) keys[i];
            collected.add(key.getElementName());
        }

        assertEquals(1, collected.size());
        assertTrue(collected.contains(anyPackage));
    }

    @Test
    public void testCollectClassesFromPackageHierarchical() throws Exception {
        JarLayout layout = readJarLayout(jarFileOnDisk);
        assertNotNull(layout);

        Optional<PackagePair> pair = layout.findBaseAndSubpackagePair();
        String selectedPackage = pair.map(PackagePair::base).orElseGet(() -> layout.findAnyPackage().orElse("")); //$NON-NLS-1$

        IPackageFragment base = jarRoot.getPackageFragment(selectedPackage);
        assertTrue(base.exists());

        ExportSourceAction action = new ExportSourceAction(new ArrayList());
        setBooleanField(action, "isFlat", false); //$NON-NLS-1$

        Map classes = new HashMap();
        invokeCollectClasses(action, base, classes);

        Set<String> collected = new HashSet();
        Object[] keys = classes.keySet().toArray();
        for (int i = 0; i < keys.length; i++) {
            IPackageFragment key = (IPackageFragment) keys[i];
            collected.add(key.getElementName());
        }

        assertTrue(collected.contains(selectedPackage));

        if (pair.isPresent()) {
            assertTrue(collected.contains(pair.get().sub()));
            assertTrue(collected.size() >= 2);
        } else {
            assertEquals(1, collected.size());
        }
    }

    @Test
    public void testCollectClassesFromSingleClassFile() throws Exception {
        JarLayout layout = readJarLayout(jarFileOnDisk);
        assertNotNull(layout);

        Optional<ClassLocation> anyClass = layout.findAnyClass();
        assertTrue(anyClass.isPresent());

        IPackageFragment pkg = jarRoot.getPackageFragment(anyClass.get().packageName());
        assertTrue(pkg.exists());

        IClassFile classFile = pkg.getClassFile(anyClass.get().classFileName());
        assertNotNull(classFile);
        assertTrue(classFile.exists());

        ExportSourceAction action = new ExportSourceAction(new ArrayList());
        setBooleanField(action, "isFlat", true); //$NON-NLS-1$

        Map classes = new HashMap();
        invokeCollectClasses(action, classFile, classes);

        assertTrue(classes.containsKey(pkg));
        List list = (List) classes.get(pkg);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(classFile, list.get(0));
    }

    @Test
    public void testExportPackageSourcesExportsAllClassesAsJavaFilesWithoutFailures() throws Exception {
        IPreferenceStore store = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        assertEquals(DECOMPILER_FERNFLOWER, store.getString(JavaDecompilerPlugin.DECOMPILER_TYPE));

        boolean reuseBuf = store.getBoolean(JavaDecompilerPlugin.REUSE_BUFFER);
        boolean always = store.getBoolean(JavaDecompilerPlugin.IGNORE_EXISTING);

        File outZip = new File(tempDir, "exported-src-all-" + UUID.randomUUID() + ".zip"); //$NON-NLS-1$ //$NON-NLS-2$
        if (outZip.exists()) {
            assertTrue(outZip.delete());
        }

        IJavaElement[] children = jarRoot.getChildren();
        assertNotNull(children);
        assertTrue(children.length > 0);

        ExportSourceAction action = new ExportSourceAction(new ArrayList());
        List exceptions = new ArrayList();

        invokeExportPackageSources(action, DECOMPILER_FERNFLOWER, reuseBuf, always, outZip.getAbsolutePath(), children,
                exceptions);

        assertTrue("No exceptions should be reported", exceptions.isEmpty()); //$NON-NLS-1$
        assertTrue(outZip.exists());
        assertTrue(outZip.length() > 0);

        Set<String> expectedJavaEntries = listExpectedJavaEntriesFromJar(jarFileOnDisk);
        assertTrue(!expectedJavaEntries.isEmpty());

        Set<String> actualJavaEntries = new HashSet(listZipEntries(outZip, ".java")); //$NON-NLS-1$
        assertEquals("Exported entry count must match jar class count", expectedJavaEntries.size(), actualJavaEntries.size()); //$NON-NLS-1$

        for (String expected : expectedJavaEntries) {
            assertTrue("Missing exported entry: " + expected, actualJavaEntries.contains(expected)); //$NON-NLS-1$
        }
    }

    private static void configureExportPreferences(File tempDir) {
        IPreferenceStore store = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        store.setValue(JavaDecompilerPlugin.TEMP_DIR, tempDir.getAbsolutePath());
        store.setValue(JavaDecompilerPlugin.DECOMPILER_TYPE, DECOMPILER_FERNFLOWER);
        store.setValue(JavaDecompilerPlugin.REUSE_BUFFER, false);
        store.setValue(JavaDecompilerPlugin.IGNORE_EXISTING, true);
    }

    private static void invokeExportPackageSources(ExportSourceAction action, String decompilerType, boolean reuseBuf,
            boolean always, String projectFile, IJavaElement[] children, List exceptions) throws Exception {
        Method method = ExportSourceAction.class.getDeclaredMethod("exportPackageSources", //$NON-NLS-1$
                org.eclipse.core.runtime.IProgressMonitor.class, String.class, boolean.class, boolean.class, String.class,
                IJavaElement[].class, List.class);
        method.setAccessible(true);
        org.eclipse.core.runtime.IProgressMonitor monitor = new org.eclipse.core.runtime.NullProgressMonitor();
        method.invoke(action, monitor, decompilerType, reuseBuf, always, projectFile, children, exceptions);
    }

    private static List<String> listZipEntries(File zip, String suffix) throws IOException {
        List<String> names = new ArrayList();
        try (ZipFile zf = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name != null && name.endsWith(suffix)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static Set<String> listExpectedJavaEntriesFromJar(File jarFile) throws Exception {
        Set<String> expected = new HashSet();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name == null || !name.endsWith(".class")) { //$NON-NLS-1$
                    continue;
                }
                expected.add(name.substring(0, name.length() - ".class".length()) + ".java"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        return expected;
    }

    private static void configureClasspathWithJre(IJavaProject project) throws JavaModelException {
        IClasspathEntry[] classpath = new IClasspathEntry[] { JavaRuntime.getDefaultJREContainerEntry() };
        project.setRawClasspath(classpath, null);
    }

    private static File resolveTestJar() throws Exception {
        Bundle bundle = Platform.getBundle(TEST_BUNDLE_ID);
        assertNotNull(bundle);

        URL entry = bundle.getEntry(TEST_JAR_PATH);
        assertNotNull(entry);

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

        return findJarPackageFragmentRoot(project, jarPath).orElseThrow(
                () -> new IllegalStateException("Unable to locate package fragment root for jar: " + jarPath.toOSString())); //$NON-NLS-1$
    }

    private static Optional<IPackageFragmentRoot> findJarPackageFragmentRoot(IJavaProject project, IPath jarPath)
            throws JavaModelException {
        IPackageFragmentRoot[] roots = project.getAllPackageFragmentRoots();
        for (int i = 0; i < roots.length; i++) {
            IPackageFragmentRoot root = roots[i];
            IPath rootPath = root.getPath();
            if (rootPath != null && rootPath.equals(jarPath)) {
                root.open(null);
                return Optional.of(root);
            }
        }
        return Optional.empty();
    }

    private static void invokeCollectClasses(ExportSourceAction action, IJavaElement element, Map classes) throws Exception {
        Method method = ExportSourceAction.class.getDeclaredMethod("collectClasses", //$NON-NLS-1$
                IJavaElement.class, Map.class, org.eclipse.core.runtime.IProgressMonitor.class);
        method.setAccessible(true);
        org.eclipse.core.runtime.IProgressMonitor monitor = new org.eclipse.core.runtime.NullProgressMonitor();
        method.invoke(action, element, classes, monitor);
    }

    private static void setBooleanField(Object target, String fieldName, boolean value) throws Exception {
        Field field = ExportSourceAction.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static File createTempDirUnderTarget(String name) throws IOException {
        File base = new File("target"); //$NON-NLS-1$
        if (!base.exists() && !base.mkdirs() && !base.exists()) {
            throw new IOException("Unable to create target directory: " + base.getAbsolutePath()); //$NON-NLS-1$
        }

        File dir = new File(base, name);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("Unable to create temp directory: " + dir.getAbsolutePath()); //$NON-NLS-1$
        }
        return dir;
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteRecursively(children[i]);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("Unable to delete: " + file.getAbsolutePath()); //$NON-NLS-1$
        }
    }

    private static JarLayout readJarLayout(File jarFile) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            Set<String> packages = new HashSet();
            List<ClassLocation> classes = new ArrayList();

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (!name.endsWith(".class")) { //$NON-NLS-1$
                    continue;
                }

                int lastSlash = name.lastIndexOf('/');
                String packageName = ""; //$NON-NLS-1$
                String fileName = name;
                if (lastSlash >= 0) {
                    packageName = name.substring(0, lastSlash).replace('/', '.');
                    fileName = name.substring(lastSlash + 1);
                }

                packages.add(packageName);
                classes.add(new ClassLocation(packageName, fileName));
            }

            return new JarLayout(packages, classes);
        }
    }

    public record PackagePair(String base, String sub) {
    }

    public record ClassLocation(String packageName, String classFileName) {
    }

    public record JarLayout(Set<String> packages, List<ClassLocation> classes) {

        public Optional<String> findAnyPackage() {
            if (packages == null || packages.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(packages.iterator().next());
        }

        public Optional<PackagePair> findBaseAndSubpackagePair() {
            if (packages == null || packages.isEmpty()) {
                return Optional.empty();
            }

            String[] all = packages.toArray(new String[0]);
            for (int i = 0; i < all.length; i++) {
                String base = all[i];
                if (base == null || base.isEmpty()) {
                    continue;
                }

                String prefix = base + "."; //$NON-NLS-1$
                for (int j = 0; j < all.length; j++) {
                    String candidate = all[j];
                    if (candidate != null && candidate.startsWith(prefix)) {
                        return Optional.of(new PackagePair(base, candidate));
                    }
                }
            }
            return Optional.empty();
        }

        public Optional<ClassLocation> findAnyClass() {
            if (classes == null || classes.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(classes.get(0));
        }
    }
}
