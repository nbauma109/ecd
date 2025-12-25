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
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;

public class ExportSourceActionTest {

    private static final String TEST_BUNDLE_ID = "org.sf.feeling.decompiler.tests";
    private static final String TEST_JAR_PATH = "resources/test.jar";

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

        tempDir = createTempDirUnderWorkspace(".ecd-test-tmp-" + UUID.randomUUID().toString());
        assertNotNull(tempDir);
        assertTrue(tempDir.exists());

        JavaDecompilerPlugin.getDefault().getPreferenceStore().setValue(JavaDecompilerPlugin.TEMP_DIR,
                tempDir.getAbsolutePath());

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
        if (tempDir != null && tempDir.exists()) {
            deleteRecursively(tempDir);
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

        assertTrue("Hierarchical mode should include the selected package", collected.contains(selectedPackage));

        if (pair.isPresent()) {
            assertTrue("Hierarchical mode should include subpackages when present", collected.contains(pair.get().sub));
            assertTrue("Hierarchical mode should collect at least base and subpackage", collected.size() >= 2);
        } else {
            assertEquals(
                    "When no subpackages exist, hierarchical mode should behave like flat collection for the selected package",
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

    @Test
    public void testExportPackageSourcesCreatesZipWithJavaEntries() throws Exception {
        String decompilerType = JavaDecompilerPlugin.getDefault().getPreferenceStore()
                .getString(JavaDecompilerPlugin.DECOMPILER_TYPE);

        Assume.assumeTrue("Decompiler type must be configured for tests", decompilerType != null && !decompilerType.trim().isEmpty());

        boolean reuseBuf = JavaDecompilerPlugin.getDefault().getPreferenceStore()
                .getBoolean(JavaDecompilerPlugin.REUSE_BUFFER);
        boolean always = JavaDecompilerPlugin.getDefault().getPreferenceStore()
                .getBoolean(JavaDecompilerPlugin.IGNORE_EXISTING);

        File outZip = new File(tempDir, "exported-src-" + UUID.randomUUID().toString() + ".zip");
        if (outZip.exists()) {
            assertTrue(outZip.delete());
        }

        IJavaElement[] children = jarRoot.getChildren();
        assertNotNull(children);
        assertTrue(children.length > 0);

        ExportSourceAction action = new ExportSourceAction(new ArrayList());
        List exceptions = new ArrayList();

        invokeExportPackageSources(action, decompilerType, reuseBuf, always, outZip.getAbsolutePath(), children, exceptions);

        assertTrue("No exceptions should be reported", exceptions.isEmpty());
        assertTrue("Output zip should be created", outZip.exists());
        assertTrue("Output zip should be non-empty", outZip.length() > 0);

        List<String> javaEntries = listZipEntries(outZip, ".java");
        assertTrue("Zip should contain at least one .java entry", !javaEntries.isEmpty());
    }

    @Test
    public void testExportPackageSourcesSkipsInnerClassesIfPresent() throws Exception {
        String decompilerType = JavaDecompilerPlugin.getDefault().getPreferenceStore()
                .getString(JavaDecompilerPlugin.DECOMPILER_TYPE);

        Assume.assumeTrue("Decompiler type must be configured for tests", decompilerType != null && !decompilerType.trim().isEmpty());

        boolean reuseBuf = JavaDecompilerPlugin.getDefault().getPreferenceStore()
                .getBoolean(JavaDecompilerPlugin.REUSE_BUFFER);
        boolean always = JavaDecompilerPlugin.getDefault().getPreferenceStore()
                .getBoolean(JavaDecompilerPlugin.IGNORE_EXISTING);

        JarLayout layout = readJarLayout(jarFileOnDisk);
        assertNotNull(layout);

        boolean jarHasInnerClasses = layout.hasAnyInnerClass;
        Assume.assumeTrue("test.jar must contain at least one inner class to verify skipping behavior", jarHasInnerClasses);

        File outZip = new File(tempDir, "exported-src-inner-skip-" + UUID.randomUUID().toString() + ".zip");
        if (outZip.exists()) {
            assertTrue(outZip.delete());
        }

        IJavaElement[] children = jarRoot.getChildren();
        assertNotNull(children);
        assertTrue(children.length > 0);

        ExportSourceAction action = new ExportSourceAction(new ArrayList());
        List exceptions = new ArrayList();

        invokeExportPackageSources(action, decompilerType, reuseBuf, always, outZip.getAbsolutePath(), children, exceptions);

        assertTrue("No exceptions should be reported", exceptions.isEmpty());
        assertTrue("Output zip should be created", outZip.exists());

        List<String> javaEntries = listZipEntries(outZip, ".java");
        for (int i = 0; i < javaEntries.size(); i++) {
            String entry = javaEntries.get(i);
            assertTrue("Inner class sources should not be exported: " + entry, entry.indexOf('$') < 0);
        }
    }

    private static void invokeExportPackageSources(ExportSourceAction action, String decompilerType, boolean reuseBuf,
            boolean always, String projectFile, IJavaElement[] children, List exceptions) throws Exception {
        Method method = ExportSourceAction.class.getDeclaredMethod("exportPackageSources",
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

    private static void configureClasspathWithJre(IJavaProject project) throws JavaModelException {
        IClasspathEntry[] classpath = new IClasspathEntry[] { JavaRuntime.getDefaultJREContainerEntry() };
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

        return findJarPackageFragmentRoot(project, jarPath).orElseThrow(
                () -> new IllegalStateException("Unable to locate package fragment root for jar: " + jarPath.toOSString()));
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

    private static File createTempDirUnderWorkspace(String name) throws IOException {
        File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
        File dir = new File(workspaceRoot, name);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create temp directory: " + dir.getAbsolutePath());
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
            throw new IOException("Unable to delete: " + file.getAbsolutePath());
        }
    }

    private static JarLayout readJarLayout(File jarFile) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            Set<String> packages = new HashSet();
            List<ClassLocation> topLevelClasses = new ArrayList();
            boolean hasInner = false;

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
                    hasInner = true;
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

            return new JarLayout(packages, topLevelClasses, hasInner);
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
        private final boolean hasAnyInnerClass;

        private JarLayout(Set<String> packages, List<ClassLocation> topLevelClasses, boolean hasAnyInnerClass) {
            this.packages = packages;
            this.topLevelClasses = topLevelClasses;
            this.hasAnyInnerClass = hasAnyInnerClass;
        }

        private Optional<String> findAnyPackage() {
            if (packages.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(packages.iterator().next());
        }

        private Optional<PackagePair> findBaseAndSubpackagePair() {
            String[] all = packages.toArray(new String[0]);
            for (int i = 0; i < all.length; i++) {
                String base = all[i];
                if (base == null || base.isEmpty()) {
                    continue;
                }

                String prefix = base + ".";
                for (int j = 0; j < all.length; j++) {
                    String candidate = all[j];
                    if (candidate != null && candidate.startsWith(prefix)) {
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
