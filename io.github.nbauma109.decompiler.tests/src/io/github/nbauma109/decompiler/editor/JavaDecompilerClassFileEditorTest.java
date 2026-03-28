/*******************************************************************************
 * Copyright (c) 2025.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.editor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.SetupRunnable;

public class JavaDecompilerClassFileEditorTest {

    private static final String CLASS = ".class";
    private static final String TEST_BUNDLE_ID = "io.github.nbauma109.decompiler.tests";
    private static final String TEST_JAR_PATH = "resources/test.jar";
    private static final String DECOMPILER_FERNFLOWER = "Fernflower"; //$NON-NLS-1$
    private static final String NO_CLASS_ENTRY_FOUND = "No .class entry found in test.jar"; //$NON-NLS-1$
    private static final String DEFAULT_TITLE_IMAGE_FIELD = "defaultTitleImage"; //$NON-NLS-1$

    private IProject project;
    private IPackageFragmentRoot jarRoot;
    private File jarFileOnDisk;
    private File tempDir;
    private IEditorPart openedEditor;

    @Before
    public void setUp() throws Exception {
        refreshDecompilerEditorAssociations();

        jarFileOnDisk = resolveTestJar();
        assertNotNull(jarFileOnDisk);
        assertTrue(jarFileOnDisk.exists());
        assertTrue(jarFileOnDisk.isFile());

        tempDir = createTempDirUnderTarget("ecd-test-tmp-" + UUID.randomUUID()); //$NON-NLS-1$
        configurePreferences(tempDir);

        String projectName = "java-decompiler-classfile-editor-test-project"; //$NON-NLS-1$
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

        IJavaProject javaProject = JavaCore.create(project);
        configureClasspathWithJre(javaProject);

        jarRoot = addJarToClasspathAndGetRoot(javaProject, jarFileOnDisk);
        assertNotNull(jarRoot);
        assertTrue(jarRoot.exists());
    }

    @After
    public void tearDown() throws Exception {
        closeOpenedEditor();

        if (project != null && project.exists()) {
            project.delete(true, true, null);
        }
        if (tempDir != null && tempDir.exists()) {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testOpenClassFileWithDecompilerEditorIdShowsSource() throws Exception {
        String editorId = resolveDecompilerEditorIdFromRegistry();
        assertNotNull(editorId);
        assertTrue(!editorId.trim().isEmpty());

        ClassInJar classInJar = findPreferredClass(jarFileOnDisk).orElseThrow(
                () -> new IllegalStateException(NO_CLASS_ENTRY_FOUND));

        IPackageFragment pkg = jarRoot.getPackageFragment(classInJar.packageName());
        assertTrue(pkg.exists());

        IClassFile classFile = pkg.getClassFile(classInJar.classFileName());
        assertTrue(classFile.exists());

        openedEditor = openWithEditorId(classFile, editorId);
        assertNotNull(openedEditor);

        assertTrue("Expected JavaDecompilerClassFileEditor but got: " + openedEditor.getClass().getName(), //$NON-NLS-1$
                openedEditor instanceof JavaDecompilerClassFileEditor);

        ITextEditor textEditor = adaptToTextEditor(openedEditor);
        assertNotNull(textEditor);

        IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
        assertNotNull(document);

        String contents = waitForNonEmptyDocument(document);
        assertNotNull(contents);
        assertTrue(!contents.trim().isEmpty());

        String expectedSimpleName = stripClassExtension(classInJar.classFileName());
        assertTrue(contents.contains(expectedSimpleName));
        assertTrue(contents.contains("class")); //$NON-NLS-1$
        assertFalse(openedEditor.getTitle().contains(" [" + DECOMPILER_FERNFLOWER + "]")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(openedEditor.getTitleImage() == JavaDecompilerPlugin.getDecompilerImage(DECOMPILER_FERNFLOWER));
    }

    @Test
    public void testReopenClassFileUsesDecompilerEditorAfterSourceWasCached() throws Exception {
        ClassInJar classInJar = findPreferredClass(jarFileOnDisk).orElseThrow(
                () -> new IllegalStateException(NO_CLASS_ENTRY_FOUND));

        IPackageFragment pkg = jarRoot.getPackageFragment(classInJar.packageName());
        assertTrue(pkg.exists());

        IClassFile classFile = pkg.getClassFile(classInJar.classFileName());
        assertTrue(classFile.exists());

        openedEditor = openDefault(classFile);
        assertTrue(openedEditor instanceof JavaDecompilerClassFileEditor);

        ITextEditor initialTextEditor = adaptToTextEditor(openedEditor);
        assertNotNull(initialTextEditor);

        IDocument initialDocument = initialTextEditor.getDocumentProvider().getDocument(initialTextEditor.getEditorInput());
        assertNotNull(initialDocument);
        String initialContents = waitForNonEmptyDocument(initialDocument);
        assertTrue(initialContents.contains(stripClassExtension(classInJar.classFileName())));

        // The first open seeds JDT's cached source for this binary class. Reopening through the normal Java path
        // used to switch to the stock class editor because the file no longer looked like "without source".
        closeOpenedEditor();

        openedEditor = openDefault(classFile);
        assertTrue("Expected reopen to use JavaDecompilerClassFileEditor but got: " + openedEditor.getClass().getName(), //$NON-NLS-1$
                openedEditor instanceof JavaDecompilerClassFileEditor);
        assertTrue(openedEditor.getTitleImage() == JavaDecompilerPlugin.getDecompilerImage(DECOMPILER_FERNFLOWER));
    }

    @Test
    public void testUpdateTitleImageDoesNotUseDisposedDefaultImage() throws Exception {
        ClassInJar classInJar = findPreferredClass(jarFileOnDisk).orElseThrow(
                () -> new IllegalStateException(NO_CLASS_ENTRY_FOUND));

        IPackageFragment pkg = jarRoot.getPackageFragment(classInJar.packageName());
        assertTrue(pkg.exists());

        IClassFile classFile = pkg.getClassFile(classInJar.classFileName());
        assertTrue(classFile.exists());

        openedEditor = openDefault(classFile);
        assertTrue(openedEditor instanceof JavaDecompilerClassFileEditor);

        JavaDecompilerClassFileEditor editor = (JavaDecompilerClassFileEditor) openedEditor;
        Image decompilerImage = JavaDecompilerPlugin.getDecompilerImage(DECOMPILER_FERNFLOWER);
        assertSame(decompilerImage, editor.getTitleImage());

        Image disposedImage = createDisposedImage();

        runInUiThread(() -> {
            setPrivateField(editor, DEFAULT_TITLE_IMAGE_FIELD, disposedImage);
            setPrivateField(editor, "decompilerType", null); //$NON-NLS-1$
            invokePrivateMethod(editor, "updateTitleImage"); //$NON-NLS-1$
        });

        assertSame(decompilerImage, editor.getTitleImage());
    }

    @Test
    public void testUpdateTitleImageDoesNotReplaceDisposedDefaultImageWithDecompilerImage() throws Exception {
        ClassInJar classInJar = findPreferredClass(jarFileOnDisk).orElseThrow(
                () -> new IllegalStateException(NO_CLASS_ENTRY_FOUND));

        IPackageFragment pkg = jarRoot.getPackageFragment(classInJar.packageName());
        assertTrue(pkg.exists());

        IClassFile classFile = pkg.getClassFile(classInJar.classFileName());
        assertTrue(classFile.exists());

        openedEditor = openDefault(classFile);
        assertTrue(openedEditor instanceof JavaDecompilerClassFileEditor);

        JavaDecompilerClassFileEditor editor = (JavaDecompilerClassFileEditor) openedEditor;
        Image decompilerImage = JavaDecompilerPlugin.getDecompilerImage(DECOMPILER_FERNFLOWER);
        assertSame(decompilerImage, editor.getTitleImage());

        Image disposedImage = createDisposedImage();

        runInUiThread(() -> {
            setPrivateField(editor, DEFAULT_TITLE_IMAGE_FIELD, disposedImage);
            invokePrivateMethod(editor, "updateTitleImage"); //$NON-NLS-1$
        });

        Image storedDefaultImage = runInUiThreadWithResult(
                () -> (Image) getPrivateField(editor, DEFAULT_TITLE_IMAGE_FIELD));
        assertSame(disposedImage, storedDefaultImage);
    }

    private static IEditorPart openWithEditorId(IClassFile classFile, String editorId) throws Exception {
        return runInUiThreadWithResult(() -> {
            IWorkbenchWindow window = resolveWorkbenchWindow();
            IWorkbenchPage page = resolveWorkbenchPage(window);

            IEditorPart first = JavaUI.openInEditor(classFile, true, true);
            if (first == null) {
                throw new IllegalStateException("Unable to open initial editor for class file"); //$NON-NLS-1$
            }

            IEditorInput input = first.getEditorInput();
            if (input == null) {
                page.closeEditor(first, false);
                throw new IllegalStateException("Initial editor input is null"); //$NON-NLS-1$
            }

            page.closeEditor(first, false);

            IEditorPart editor = IDE.openEditor(page, input, editorId, true);
            page.activate(editor);
            return editor;
        });
    }

    private static IEditorPart openDefault(IClassFile classFile) throws Exception {
        return runInUiThreadWithResult(() -> {
            IEditorPart editor = JavaUI.openInEditor(classFile, true, true);
            if (editor == null) {
                throw new IllegalStateException("Unable to open default editor for class file"); //$NON-NLS-1$
            }
            editor.getSite().getPage().activate(editor);
            return editor;
        });
    }

    private static IWorkbenchWindow resolveWorkbenchWindow() {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window != null) {
            return window;
        }
        IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
        if (windows != null && windows.length > 0) {
            return windows[0];
        }
        throw new IllegalStateException("No workbench window available"); //$NON-NLS-1$
    }

    private static IWorkbenchPage resolveWorkbenchPage(IWorkbenchWindow window) {
        IWorkbenchPage page = window.getActivePage();
        if (page != null) {
            return page;
        }
        throw new IllegalStateException("No workbench page available"); //$NON-NLS-1$
    }

    private static String resolveDecompilerEditorIdFromRegistry() {
        IExtensionRegistry registry = Platform.getExtensionRegistry();
        if (registry == null) {
            return null;
        }

        IConfigurationElement[] elements = registry.getConfigurationElementsFor("org.eclipse.ui.editors"); //$NON-NLS-1$
        String expectedClassName = JavaDecompilerClassFileEditor.class.getName();

        for (IConfigurationElement e : elements) {
            if (!"editor".equals(e.getName())) { //$NON-NLS-1$
                continue;
            }

            String className = e.getAttribute("class"); //$NON-NLS-1$
            if (expectedClassName.equals(className)) {
                return e.getAttribute("id"); //$NON-NLS-1$
            }
        }
        return null;
    }

    private static void configurePreferences(File tempDir) {
        IPreferenceStore store = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        store.setValue(JavaDecompilerPlugin.TEMP_DIR, tempDir.getAbsolutePath());
        store.setValue(JavaDecompilerPlugin.DECOMPILER_TYPE, DECOMPILER_FERNFLOWER);
        store.setValue(JavaDecompilerPlugin.REUSE_BUFFER, false);
        store.setValue(JavaDecompilerPlugin.IGNORE_EXISTING, true);
    }

    private static ITextEditor adaptToTextEditor(IEditorPart editor) {
        if (editor instanceof ITextEditor textEditor) {
            return textEditor;
        }
        return editor.getAdapter(ITextEditor.class);
    }

    private static String waitForNonEmptyDocument(IDocument document) throws InterruptedException {
        int attempts = 250;
        for (int i = 0; i < attempts; i++) {
            String text = document.get();
            if (text != null && !text.trim().isEmpty()) {
                return text;
            }
            Thread.sleep(25L);
            drainUiEvents();
        }
        return document.get();
    }

    private static void drainUiEvents() {
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return;
        }
        while (display.readAndDispatch()) {
            // we drain pending UI work so our editor can finish loading content
        }
    }

    private void closeOpenedEditor() throws Exception {
        if (openedEditor == null) {
            return;
        }
        runInUiThread(() -> {
            IWorkbenchPage page = openedEditor.getSite().getPage();
            if (page != null) {
                page.closeEditor(openedEditor, false);
            }
        });
        openedEditor = null;
    }

    private static void configureClasspathWithJre(IJavaProject project) throws Exception {
        IClasspathEntry[] classpath = { JavaRuntime.getDefaultJREContainerEntry() };
        project.setRawClasspath(classpath, null);
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

        IPackageFragmentRoot[] roots = project.getAllPackageFragmentRoots();
        for (IPackageFragmentRoot root : roots) {
            IPath rootPath = root.getPath();
            if (rootPath != null && rootPath.equals(jarPath)) {
                root.open(null);
                return root;
            }
        }
        throw new IllegalStateException("Unable to locate package fragment root for jar: " + jarPath.toOSString()); //$NON-NLS-1$
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

    private static Optional<ClassInJar> findPreferredClass(File jarFile) throws Exception {
        Set<String> classEntries = new HashSet<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name != null && name.endsWith(CLASS)) {
                    classEntries.add(name);
                }
            }
        }

        if (classEntries.isEmpty()) {
            return Optional.empty();
        }

        String preferred;
        if (classEntries.contains("test/Test.class")) { //$NON-NLS-1$
            preferred = "test/Test.class"; //$NON-NLS-1$
        } else {
            preferred = classEntries.iterator().next();
        }

        int lastSlash = preferred.lastIndexOf('/');
        String packageName = ""; //$NON-NLS-1$
        String classFileName = preferred;
        if (lastSlash >= 0) {
            packageName = preferred.substring(0, lastSlash).replace('/', '.');
            classFileName = preferred.substring(lastSlash + 1);
        }

        return Optional.of(new ClassInJar(packageName, classFileName));
    }

    private static String stripClassExtension(String name) {
        if (name == null) {
            return ""; //$NON-NLS-1$
        }
        if (name.endsWith(CLASS)) {
            return name.substring(0, name.length() - CLASS.length());
        }
        return name;
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
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("Unable to delete: " + file.getAbsolutePath()); //$NON-NLS-1$
        }
    }

    private static void runInUiThread(UiRunnable runnable) throws Exception {
        Display display = Display.getDefault();
        if (display == null) {
            runnable.run();
            return;
        }
        final ArrayList<Exception> errors = new ArrayList<>();
        display.syncExec(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                errors.add(e);
            }
        });
        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
    }

    private static <T> T runInUiThreadWithResult(UiSupplier<T> supplier) throws Exception {
        Display display = Display.getDefault();
        if (display == null) {
            return supplier.get();
        }
        final ArrayList<Exception> errors = new ArrayList<>();
        final ArrayList<T> results = new ArrayList<>();
        display.syncExec(() -> {
            try {
                results.add(supplier.get());
            } catch (Exception e) {
                errors.add(e);
            }
        });
        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
        return results.get(0);
    }

    private static void refreshDecompilerEditorAssociations() throws Exception {
        runInUiThread(() -> new SetupRunnableAccessor().apply());
    }

    private static Image createDisposedImage() throws Exception {
        return runInUiThreadWithResult(() -> {
            Display display = Display.getDefault();
            Image image = new Image(display, 1, 1);
            image.dispose();
            return image;
        });
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invokePrivateMethod(Object target, String methodName) throws Exception {
        Method method = findMethod(target.getClass(), methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static Field findField(Class<?> type, String fieldName) throws Exception {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static Method findMethod(Class<?> type, String methodName) throws Exception {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private interface UiRunnable {
        void run() throws Exception;
    }

    private interface UiSupplier<T> {
        T get() throws Exception;
    }

    private static final class SetupRunnableAccessor extends SetupRunnable {
        private void apply() {
            updateClassDefaultEditor();
        }
    }

    public record ClassInJar(String packageName, String classFileName) {
    }
}
