/*******************************************************************************
 * © 2025-2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
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

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport;
import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport.BundleJarProjectSetup;
import io.github.nbauma109.decompiler.util.DecompileUtil;

public class ExportSourceActionTest {

    private static final String TEST_BUNDLE_ID = "io.github.nbauma109.decompiler.tests";
    private static final String TEST_JAR_PATH = "src/test/resources/test.jar";

    private IProject project;
    private IPackageFragmentRoot jarRoot;
    private File jarFileOnDisk;

    private String originalTempDir;
    private File tempDirForTest;
    private final List<File> filesToDelete = new ArrayList<>();

    @Before
    public void setUp() throws IOException, CoreException {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "export-source-action-test-project");
        project = setup.project();
        jarRoot = setup.jarRoot();
        jarFileOnDisk = setup.jarFile();

        JavaDecompilerPlugin plugin = JavaDecompilerPlugin.getDefault();
        assertNotNull(plugin);

        originalTempDir = plugin.getPreferenceStore().getString(JavaDecompilerPlugin.TEMP_DIR);
        tempDirForTest = new File(System.getProperty("java.io.tmpdir"),
                "export-source-action-test-" + UUID.randomUUID());
        plugin.getPreferenceStore().setValue(JavaDecompilerPlugin.TEMP_DIR, tempDirForTest.getAbsolutePath());

        assertNotNull(jarRoot);
        assertTrue(jarRoot.exists());
    }

    @After
    public void tearDown() throws CoreException {
        JavaDecompilerPlugin plugin = JavaDecompilerPlugin.getDefault();
        if (plugin != null && originalTempDir != null) {
            plugin.getPreferenceStore().setValue(JavaDecompilerPlugin.TEMP_DIR, originalTempDir);
        }

        for (File f : filesToDelete) {
            FileUtils.deleteQuietly(f);
        }
        filesToDelete.clear();

        FileUtils.deleteQuietly(tempDirForTest);
        tempDirForTest = null;

        if (project != null && project.exists()) {
            project.delete(true, true, null);
        }
    }

    @Test
    public void testIsEnabledSelectionNullIsFalse() {
        ExportSourceAction action = new ExportSourceAction(null);
        assertFalse(action.isEnabled());
    }

    @Test
    public void testIsEnabledSelectionNonNullIsTrue() {
        ExportSourceAction action = new ExportSourceAction(new ArrayList<>());
        assertTrue(action.isEnabled());
    }

    @Test
    public void testCollectClassesFromPackageFlat() throws IOException, JavaModelException {
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
    public void testCollectClassesFromPackageHierarchical() throws IOException, JavaModelException {
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
            assertEquals(
                    "When no subpackages exist, hierarchical mode should behave like flat collection for the selected package",
                    1, collected.size());
        }
    }

    @Test
    public void testCollectClassesFromSingleClassFile() throws IOException, JavaModelException {
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

    @Test
    public void testCollectClassesAddsToExistingPackageList() throws IOException, JavaModelException {
        IClassFile classFile = anyTopLevelClassFile();
        IPackageFragment pkg = (IPackageFragment) classFile.getParent();

        ExportSourceAction action = new ExportSourceAction(new ArrayList<>());
        action.setFlat(true);

        Map<IJavaElement, List<IJavaElement>> classes = new HashMap<>();
        action.collectClasses(classFile, classes, new NullProgressMonitor());
        action.collectClasses(classFile, classes, new NullProgressMonitor());

        assertTrue(classes.containsKey(pkg));
        List<IJavaElement> list = classes.get(pkg);
        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals(classFile, list.get(0));
        assertEquals(classFile, list.get(1));
    }

    @Test
    public void testEnsureDirectoryExistsCreatesDirectoryWhenBlockedByFile() throws IOException {
        File blocked = new File(tempDirForTest, "blocked-dir");
        ensureParentDirectoryExistsForFile(blocked);

        try (FileOutputStream out = new FileOutputStream(blocked)) {
            out.write(new byte[] { 0x01 });
        }

        assertTrue(blocked.exists());
        assertTrue(blocked.isFile());

        ExportSourceAction.ensureDirectoryExists(blocked);

        assertTrue(blocked.exists());
        assertTrue(blocked.isDirectory());
    }

    @Test
    public void testEnsureParentDirectoryExistsCreatesParents() throws IOException {
        File target = new File(tempDirForTest, "a/b/c/out.java");
        assertFalse(target.getParentFile().exists());

        ExportSourceAction.ensureParentDirectoryExists(target);

        assertTrue(target.getParentFile().exists());
        assertTrue(target.getParentFile().isDirectory());
    }

    @Test(expected = IOException.class)
    public void testEnsureParentDirectoryExistsNullTargetThrowsIOException() throws IOException {
        ExportSourceAction.ensureParentDirectoryExists(null);
    }

    @Test
    public void testExportPackageSourcesCanceledDoesNotCreateZip() throws IOException, JavaModelException {
        File zip = createTempZipFile();
        assertFalse(zip.exists());

        ExportSourceAction action = new ExportSourceAction(new ArrayList<>());
        List<IStatus> exceptions = new ArrayList<>();

        action.exportPackageSources(new AlwaysCanceledProgressMonitor(), resolveDecompilerTypeForTest(), true, true,
                zip.getAbsolutePath(), jarRoot.getChildren(), exceptions);

        assertFalse(zip.exists());
        assertTrue(exceptions.isEmpty());
    }

    @Test
    public void testExportPackageSourcesNoChildrenDoesNotCreateZip() throws IOException, JavaModelException {
        File zip = createTempZipFile();
        assertFalse(zip.exists());

        ExportSourceAction action = new ExportSourceAction(new ArrayList<>());
        List<IStatus> exceptions = new ArrayList<>();

        action.exportPackageSources(new NullProgressMonitor(), resolveDecompilerTypeForTest(), true, true,
                zip.getAbsolutePath(), new IJavaElement[0], exceptions);

        assertFalse(zip.exists());
        assertTrue(exceptions.isEmpty());
    }

    @Test
    public void testExportPackageSourcesCreatesZipAndCleansExportDirectory() throws IOException, JavaModelException {
        File zip = createTempZipFile();
        assertFalse(zip.exists());

        ExportSourceAction action = new ExportSourceAction(new ArrayList<>());
        List<IStatus> exceptions = new ArrayList<>();

        String decompilerType = resolveDecompilerTypeForTest();

        action.exportPackageSources(new NullProgressMonitor(), decompilerType, true, true, zip.getAbsolutePath(),
                jarRoot.getChildren(), exceptions);

        assertTrue(zip.exists());
        assertTrue(zip.isFile());
        assertTrue(zip.length() > 0L);

        List<String> entries = listZipEntries(zip);

        boolean hasJava = false;
        for (String name : entries) {
            if (name.endsWith(".java")) {
                hasJava = true;
                assertTrue("Our export should skip inner classes", name.indexOf('$') < 0);
            }
        }

        if (hasJava) {
            assertTrue(exceptions.isEmpty());
        } else {
            assertTrue("When export could not decompile any class, we expect error statuses", !exceptions.isEmpty());
        }

        File exportDir = new File(tempDirForTest, "export");
        assertFalse("Our export should delete the export working directory", exportDir.exists());
    }

    private File createTempZipFile() throws IOException {
        File zip = Files.createTempFile("export-source-action-", ".zip").toFile();
        if (zip.exists()) {
            assertTrue(zip.delete());
        }
        filesToDelete.add(zip);
        return zip;
    }

    private String resolveDecompilerTypeForTest() throws IOException, JavaModelException {
        JavaDecompilerPlugin plugin = JavaDecompilerPlugin.getDefault();
        if (plugin != null) {
            String configured = plugin.getPreferenceStore().getString(JavaDecompilerPlugin.DECOMPILER_TYPE);
            Optional<String> working = findWorkingDecompilerType(configured);
            if (working.isPresent()) {
                return working.get();
            }
        }

        Optional<String> working = findWorkingDecompilerType(null);
        return working.orElse("");
    }

    private Optional<String> findWorkingDecompilerType(String preferred) throws IOException, JavaModelException {
        IClassFile cf = anyTopLevelClassFile();
        cf.open(new NullProgressMonitor());

        List<String> candidates = new ArrayList<>();
        if (preferred != null && !preferred.trim().isEmpty()) {
            candidates.add(preferred.trim());
        }

        candidates.add("cfr");
        candidates.add("CFR");
        candidates.add("fernflower");
        candidates.add("Fernflower");
        candidates.add("procyon");
        candidates.add("Procyon");
        candidates.add("jd-core");
        candidates.add("jdcore");
        candidates.add("JD-Core");
        candidates.add("JAD");
        candidates.add("jad");

        for (String candidate : candidates) {
            try {
                String result = DecompileUtil.decompile(cf, candidate, true, true, true);
                if (result != null && !result.trim().isEmpty()) {
                    return Optional.of(candidate);
                }
            } catch (Throwable t) {
                // We keep trying candidates so our tests remain resilient across environments.
            }
        }
        return Optional.empty();
    }

    private IClassFile anyTopLevelClassFile() throws IOException {
        JarLayout layout = readJarLayout(jarFileOnDisk);
        Optional<ClassLocation> anyTopLevel = layout.findAnyTopLevelClass();
        if (!anyTopLevel.isPresent()) {
            throw new IllegalStateException("test.jar should contain at least one top-level .class");
        }

        IPackageFragment pkg = jarRoot.getPackageFragment(anyTopLevel.get().packageName);
        if (!pkg.exists()) {
            throw new IllegalStateException("Package does not exist: " + anyTopLevel.get().packageName);
        }

        IClassFile classFile = pkg.getClassFile(anyTopLevel.get().classFileName);
        if (classFile == null || !classFile.exists()) {
            throw new IllegalStateException("Class file does not exist: " + anyTopLevel.get().classFileName);
        }
        return classFile;
    }

    private static List<String> listZipEntries(File zipFile) throws IOException {
        List<String> entries = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                entries.add(entry.getName());
            }
        }
        return entries;
    }

    private static void ensureParentDirectoryExistsForFile(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            assertTrue(parent.mkdirs());
        }
    }

    private static JarLayout readJarLayout(File jarFile) throws IOException {
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

    private static final class AlwaysCanceledProgressMonitor implements IProgressMonitor {

        @Override
        public void beginTask(String name, int totalWork) {
            // This monitor only simulates a canceled state, so progress callbacks are ignored.
        }

        @Override
        public void done() {
            // No completion bookkeeping is needed for this test monitor.
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
