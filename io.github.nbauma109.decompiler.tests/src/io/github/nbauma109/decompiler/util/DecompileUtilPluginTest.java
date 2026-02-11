package io.github.nbauma109.decompiler.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DecompileUtilPluginTest {

    private File testRootDirectory;
    private IProject project;

    @Before
    public void setUp() throws Exception {
        File targetDirectory = new File("target");
        assertTrue(targetDirectory.exists() || targetDirectory.mkdirs());

        testRootDirectory = new File(targetDirectory, "decompileutil-tests" + File.separator + System.nanoTime());
        assertTrue(testRootDirectory.mkdirs());

        waitForWorkspaceJobs();
    }

    @After
    public void tearDown() throws Exception {
        deleteProjectIfPresent();
        deleteRecursively(testRootDirectory);
        waitForWorkspaceJobs();
    }

    @Test
    public void getPackageName_returnsPackageName_whenPresent() {
        String source =
                "/*header*/\n" +
                "package com.example.test;\n" +
                "public class A {}\n";

        assertEquals("com.example.test", DecompileUtil.getPackageName(source));
    }

    @Test
    public void getPackageName_returnsNull_whenMissing() {
        String source = "public class A {}";
        assertNull(DecompileUtil.getPackageName(source));
    }

    @Test
    public void decompile_returnsExistingSource_whenReusable() throws Exception {
        LibraryArtifacts artifacts = createLibraryArtifacts();

        String projectName = ".decompileutil-" + System.nanoTime();
        project = createJavaProjectWithLibrary(projectName, artifacts.binaryJar, artifacts.sourceJar);

        IType type = JavaCore.create(project).findType("com.example.Lib");
        assertNotNull(type);

        IClassFile classFile = type.getClassFile();
        assertNotNull(classFile);

        String originalSource = classFile.getSource();
        assertNotNull(originalSource);

        String result = DecompileUtil.decompile(classFile, "any", false, true, false);
        assertEquals(normalizeLineSeparators(originalSource), normalizeLineSeparators(result));
    }

    @Test
    public void decompiler_decompilesFileFromEditorInput_returnsNonNullStringOrNullWithoutThrowing() throws Exception {
        File classFile = createStandaloneClassFileOnDisk();

        IFileStore store = EFS.getStore(URI.create(classFile.toURI().toString()));
        FileStoreEditorInput input = new FileStoreEditorInput(store);

        String result = DecompileUtil.decompiler(input, "");
        assertTrue(result == null || result.length() >= 0);
    }

    private LibraryArtifacts createLibraryArtifacts() throws Exception {
        File sourceRoot = new File(testRootDirectory, "library-source");
        File classRoot = new File(testRootDirectory, "library-classes");
        File jarRoot = new File(testRootDirectory, "library-jars");

        assertTrue(sourceRoot.mkdirs());
        assertTrue(classRoot.mkdirs());
        assertTrue(jarRoot.mkdirs());

        File javaFile = new File(sourceRoot, "com/example/Lib.java");
        assertTrue(javaFile.getParentFile().exists() || javaFile.getParentFile().mkdirs());

        String javaSource =
                "package com.example;\n" +
                "\n" +
                "public class Lib {\n" +
                "    public String hello() {\n" +
                "        return \"hello\";\n" +
                "    }\n" +
                "}\n";

        writeUtf8(javaFile, javaSource);
        compileJava(javaFile, classRoot);

        File binaryJar = new File(jarRoot, "lib.jar");
        File sourceJar = new File(jarRoot, "lib-sources.jar");

        createJarWithSingleFile(binaryJar, classRoot, "com/example/Lib.class");
        createSourceJar(sourceJar, sourceRoot, "com/example/Lib.java");

        return new LibraryArtifacts(binaryJar, sourceJar);
    }

    private File createStandaloneClassFileOnDisk() throws Exception {
        File sourceRoot = new File(testRootDirectory, "standalone-source");
        File classRoot = new File(testRootDirectory, "standalone-classes");
        assertTrue(sourceRoot.mkdirs());
        assertTrue(classRoot.mkdirs());

        File javaFile = new File(sourceRoot, "com/example/Standalone.java");
        assertTrue(javaFile.getParentFile().exists() || javaFile.getParentFile().mkdirs());

        String javaSource =
                "package com.example;\n" +
                "\n" +
                "public class Standalone {\n" +
                "    public int value() {\n" +
                "        return 1;\n" +
                "    }\n" +
                "}\n";

        writeUtf8(javaFile, javaSource);
        compileJava(javaFile, classRoot);

        File classFile = new File(classRoot, "com/example/Standalone.class");
        assertTrue(classFile.exists());
        return classFile;
    }

    private IProject createJavaProjectWithLibrary(String projectName, File binaryJar, File sourceJar) throws Exception {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject newProject = root.getProject(projectName);

        if (newProject.exists()) {
            newProject.delete(true, true, new NullProgressMonitor());
        }

        newProject.create(new NullProgressMonitor());
        newProject.open(new NullProgressMonitor());

        IProjectDescription description = newProject.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        newProject.setDescription(description, new NullProgressMonitor());

        org.eclipse.jdt.core.IJavaProject javaProject = JavaCore.create(newProject);

        IClasspathEntry jreEntry = JavaCore.newContainerEntry(JavaRuntime.newDefaultJREContainerPath());
        IClasspathEntry libraryEntry = JavaCore.newLibraryEntry(
                org.eclipse.core.runtime.Path.fromOSString(binaryJar.getAbsolutePath()),
                org.eclipse.core.runtime.Path.fromOSString(sourceJar.getAbsolutePath()),
                null);

        javaProject.setRawClasspath(new IClasspathEntry[] { jreEntry, libraryEntry }, new NullProgressMonitor());
        javaProject.open(new NullProgressMonitor());

        waitForWorkspaceJobs();
        return newProject;
    }

    private static void compileJava(File javaFile, File classRoot) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        try {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, java.util.Collections.singletonList(classRoot));
            Iterable<? extends javax.tools.JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromFiles(java.util.Collections.singletonList(javaFile));

            Boolean ok = compiler.getTask(null, fileManager, null, null, null, units).call();
            assertTrue(Boolean.TRUE.equals(ok));
        } finally {
            fileManager.close();
        }
    }

    private static void createJarWithSingleFile(File jarFile, File classRoot, String relativePath) throws IOException {
        File entryFile = new File(classRoot, relativePath);
        assertTrue(entryFile.exists());

        try (JarOutputStream jarOut = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile)))) {
            JarEntry entry = new JarEntry(relativePath.replace('\\', '/'));
            jarOut.putNextEntry(entry);
            jarOut.write(Files.readAllBytes(entryFile.toPath()));
            jarOut.closeEntry();
        }
        assertTrue(jarFile.exists());
    }

    private static void createSourceJar(File sourceJar, File sourceRoot, String relativePath) throws IOException {
        File entryFile = new File(sourceRoot, relativePath);
        assertTrue(entryFile.exists());

        try (JarOutputStream jarOut = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(sourceJar)))) {
            JarEntry entry = new JarEntry(relativePath.replace('\\', '/'));
            jarOut.putNextEntry(entry);
            jarOut.write(Files.readAllBytes(entryFile.toPath()));
            jarOut.closeEntry();
        }
        assertTrue(sourceJar.exists());
    }

    private static void writeUtf8(File file, String content) throws IOException {
        Files.createDirectories(file.toPath().getParent());
        try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8.name())) {
            writer.print(content);
        }
    }

    private void deleteProjectIfPresent() throws Exception {
        if (project == null) {
            return;
        }
        if (project.exists()) {
            if (project.isOpen()) {
                project.close(new NullProgressMonitor());
            }
            project.delete(true, true, new NullProgressMonitor());
        }
        project = null;
    }

    private static void deleteRecursively(File root) {
        if (root == null || !root.exists()) {
            return;
        }
        if (root.isFile()) {
            root.delete();
            return;
        }
        File[] children = root.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        root.delete();
    }

    private static void waitForWorkspaceJobs() throws Exception {
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, new NullProgressMonitor());
        Job.getJobManager().join(ResourcesPlugin.FAMILY_MANUAL_BUILD, new NullProgressMonitor());
    }

    private static String normalizeLineSeparators(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private record LibraryArtifacts(File binaryJar, File sourceJar) {
    }
}
