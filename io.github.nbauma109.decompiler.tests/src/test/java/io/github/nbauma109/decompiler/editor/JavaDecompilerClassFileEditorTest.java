/*******************************************************************************
 * Copyright (c) 2025.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.editor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.ui.IPackagesViewPart;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.SetupRunnable;
import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport;
import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport.BundleJarProjectSetup;
import io.github.nbauma109.decompiler.util.ClassUtil;

public class JavaDecompilerClassFileEditorTest {

    private static final String KEYWORD_CLASS = "class";
    private static final String CLASS = ".class";
    private static final String TEST_BUNDLE_ID = "io.github.nbauma109.decompiler.tests";
    private static final String TEST_JAR_PATH = "src/test/resources/test.jar";
    private static final String DECOMPILER_FERNFLOWER = "Fernflower"; //$NON-NLS-1$
    private static final String NO_CLASS_ENTRY_FOUND = "No .class entry found in test.jar"; //$NON-NLS-1$
    private static final String TEST_PACKAGE = "test"; //$NON-NLS-1$
    private static final String TEST_TOP_LEVEL_CLASS = "Test.class"; //$NON-NLS-1$
    private static final String TEST_INNER_CLASS = "Test$Inner1.class"; //$NON-NLS-1$
    private static final String TEST_INNER_TYPE = "Inner1"; //$NON-NLS-1$
    private static final String TEST_TOP_LEVEL_SOURCE_DECLARATION = "class Test"; //$NON-NLS-1$

    private static final String TEST_ANONYMOUS_CLASS = "Test$1.class"; //$NON-NLS-1$

    private IProject project;
    private IPackageFragmentRoot jarRoot;
    private File jarFileOnDisk;
    private File tempDir;
    private IEditorPart openedEditor;

    @Before
    public void setUp() throws IOException, CoreException {
        refreshDecompilerEditorAssociations();

        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "java-decompiler-classfile-editor-test-project"); //$NON-NLS-1$
        project = setup.project();
        jarRoot = setup.jarRoot();
        jarFileOnDisk = setup.jarFile();

        tempDir = createTempDirUnderTarget("ecd-test-tmp-" + UUID.randomUUID()); //$NON-NLS-1$
        configurePreferences(tempDir);
        assertNotNull(jarRoot);
        assertTrue(jarRoot.exists());
    }

    @After
    public void tearDown() throws CoreException {
        closeOpenedEditor();

        if (project != null && project.exists()) {
            project.delete(true, true, null);
        }
        if (tempDir != null && tempDir.exists()) {
            FileUtils.deleteQuietly(tempDir);
        }
    }

    @Test
    public void testGetTopLevelClassFileReturnsNullForNullClassFile() {
        assertNull(ClassUtil.getTopLevelClassFile((org.eclipse.jdt.core.IClassFile) null));
    }

    @Test
    public void testGetTopLevelClassFileReturnsNullForNullType() {
        assertNull(ClassUtil.getTopLevelClassFile((org.eclipse.jdt.core.IType) null));
    }

    @Test
    public void testGetTopLevelClassFileReturnsItselfForTopLevelClass() {
        IPackageFragment pkg = jarRoot.getPackageFragment(TEST_PACKAGE);
        IClassFile topLevel = pkg.getClassFile(TEST_TOP_LEVEL_CLASS);
        assertTrue(topLevel.exists());

        IClassFile result = ClassUtil.getTopLevelClassFile(topLevel);
        assertNotNull(result);
        assertEquals(TEST_TOP_LEVEL_CLASS, result.getElementName());
    }

    @Test
    public void testGetTopLevelClassFileReturnsTopLevelForInnerClass() {
        IPackageFragment pkg = jarRoot.getPackageFragment(TEST_PACKAGE);
        IClassFile inner = pkg.getClassFile(TEST_INNER_CLASS);
        assertTrue(inner.exists());

        IClassFile result = ClassUtil.getTopLevelClassFile(inner);
        assertNotNull(result);
        assertEquals(TEST_TOP_LEVEL_CLASS, result.getElementName());
    }

    @Test
    public void testGetTopLevelClassFileReturnsTopLevelForAnonymousClass() {
        IPackageFragment pkg = jarRoot.getPackageFragment(TEST_PACKAGE);
        IClassFile anonymous = pkg.getClassFile(TEST_ANONYMOUS_CLASS);
        assertTrue("Expected anonymous class file to exist: " + TEST_ANONYMOUS_CLASS, anonymous.exists()); //$NON-NLS-1$

        IClassFile result = ClassUtil.getTopLevelClassFile(anonymous);
        assertNotNull(result);
        assertEquals(TEST_TOP_LEVEL_CLASS, result.getElementName());
    }

    @Test
    public void testGetTopLevelClassFileITypeReturnsTopLevelForTopLevelType() {
        IPackageFragment pkg = jarRoot.getPackageFragment(TEST_PACKAGE);
        IClassFile topLevel = pkg.getClassFile(TEST_TOP_LEVEL_CLASS);
        assertTrue(topLevel.exists());
        IType topLevelType = getType(topLevel);

        IClassFile result = ClassUtil.getTopLevelClassFile(topLevelType);
        assertNotNull(result);
        assertEquals(TEST_TOP_LEVEL_CLASS, result.getElementName());
    }

    @Test
    public void testGetTopLevelClassFileITypeReturnsTopLevelForInnerType() {
        IPackageFragment pkg = jarRoot.getPackageFragment(TEST_PACKAGE);
        IClassFile inner = pkg.getClassFile(TEST_INNER_CLASS);
        assertTrue(inner.exists());
        IType innerType = getType(inner);

        IClassFile result = ClassUtil.getTopLevelClassFile(innerType);
        assertNotNull(result);
        assertEquals(TEST_TOP_LEVEL_CLASS, result.getElementName());
    }

    @Test
    public void testOpeningAnonymousClassFileUsesTopLevelEditorInput()
            throws CoreException, InterruptedException {
        IPackageFragment pkg = jarRoot.getPackageFragment(TEST_PACKAGE);
        IClassFile anonymousClassFile = pkg.getClassFile(TEST_ANONYMOUS_CLASS);
        assertTrue(anonymousClassFile.exists());

        openedEditor = openDefault(anonymousClassFile);
        assertTrue(openedEditor instanceof JavaDecompilerClassFileEditor);

        IClassFile openedClassFile = getClassFileFromEditor(openedEditor);
        assertNotNull(openedClassFile);
        assertEquals(TEST_TOP_LEVEL_CLASS, openedClassFile.getElementName());

        ITextEditor textEditor = adaptToTextEditor(openedEditor);
        assertNotNull(textEditor);
        IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
        String contents = waitForNonEmptyDocument(document);
        assertTrue(contents.contains(TEST_TOP_LEVEL_SOURCE_DECLARATION));
    }

    @Test
    public void testOpenClassFileWithDecompilerEditorIdShowsSource()
            throws IOException, CoreException, InterruptedException {
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
        assertTrue(contents.contains(KEYWORD_CLASS));
        assertFalse(openedEditor.getTitle().contains(" [" + DECOMPILER_FERNFLOWER + "]")); //$NON-NLS-1$ //$NON-NLS-2$
        assertSame(JavaDecompilerPlugin.getDecompilerImage(DECOMPILER_FERNFLOWER), openedEditor.getTitleImage());
    }

    @Test
    public void testReopenClassFileUsesDecompilerEditorAfterSourceWasCached()
            throws IOException, CoreException, InterruptedException {
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
        assertSame(JavaDecompilerPlugin.getDecompilerImage(DECOMPILER_FERNFLOWER), openedEditor.getTitleImage());
    }

    @Test
    public void testOpeningInnerClassFileUsesTopLevelEditorInput()
            throws CoreException, InterruptedException {
        IPackageFragment pkg = jarRoot.getPackageFragment(TEST_PACKAGE);
        assertTrue(pkg.exists());

        IClassFile innerClassFile = pkg.getClassFile(TEST_INNER_CLASS);
        assertTrue(innerClassFile.exists());

        openedEditor = openDefault(innerClassFile);
        assertTrue(openedEditor instanceof JavaDecompilerClassFileEditor);

        IClassFile openedClassFile = getClassFileFromEditor(openedEditor);
        assertNotNull(openedClassFile);
        assertEquals(TEST_TOP_LEVEL_CLASS, openedClassFile.getElementName());

        ITextEditor textEditor = adaptToTextEditor(openedEditor);
        assertNotNull(textEditor);
        IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
        String contents = waitForNonEmptyDocument(document);
        assertTrue(contents.contains(TEST_TOP_LEVEL_SOURCE_DECLARATION));
        assertTrue(contents.contains(TEST_INNER_TYPE));
    }

    @Test
    public void testOpeningInnerClassFileReusesTopLevelEditor()
            throws CoreException {
        IPackageFragment pkg = jarRoot.getPackageFragment(TEST_PACKAGE);
        assertTrue(pkg.exists());

        IClassFile topLevelClassFile = pkg.getClassFile(TEST_TOP_LEVEL_CLASS);
        IClassFile innerClassFile = pkg.getClassFile(TEST_INNER_CLASS);
        assertTrue(topLevelClassFile.exists());
        assertTrue(innerClassFile.exists());

        openedEditor = openDefault(topLevelClassFile);
        IEditorPart topLevelEditor = openedEditor;

        openedEditor = openDefault(innerClassFile);
        assertSame(topLevelEditor, openedEditor);
    }

    @Test
    public void testOpeningInnerTypeFocusesNestedType()
            throws CoreException, InterruptedException {
        IPackageFragment pkg = jarRoot.getPackageFragment(TEST_PACKAGE);
        assertTrue(pkg.exists());

        IClassFile innerClassFile = pkg.getClassFile(TEST_INNER_CLASS);
        assertTrue(innerClassFile.exists());

        openedEditor = openDefault(getType(innerClassFile));
        assertTrue(openedEditor instanceof JavaDecompilerClassFileEditor);

        JavaDecompilerClassFileEditor editor = (JavaDecompilerClassFileEditor) openedEditor;
        assertTrue(waitForSelectedElement(editor, TEST_INNER_TYPE));
    }

    @Test
    public void testOpeningInnerTypeLinksPackageExplorerToNestedType()
            throws CoreException, InterruptedException {
        IPackageFragment pkg = jarRoot.getPackageFragment(TEST_PACKAGE);
        assertTrue(pkg.exists());

        IClassFile innerClassFile = pkg.getClassFile(TEST_INNER_CLASS);
        assertTrue(innerClassFile.exists());

        IPackagesViewPart packagesView = showPackageExplorer();
        runInUiThread(() -> packagesView.setLinkingEnabled(true));

        openedEditor = openDefault(getType(innerClassFile));
        assertTrue(openedEditor instanceof JavaDecompilerClassFileEditor);

        assertTrue("Expected Package Explorer to select the inner type or its visible class-file fallback, actual selection: " //$NON-NLS-1$
                + describePackageExplorerSelection(packagesView),
                waitForPackageExplorerSelection(packagesView, TEST_INNER_TYPE, TEST_INNER_CLASS, TEST_TOP_LEVEL_CLASS));
    }

    @Test
    public void testRefreshContentIfNeededReloadsBlankDocument()
            throws IOException, CoreException, InterruptedException {
        ClassInJar classInJar = findPreferredClass(jarFileOnDisk).orElseThrow(
                () -> new IllegalStateException(NO_CLASS_ENTRY_FOUND));

        IPackageFragment pkg = jarRoot.getPackageFragment(classInJar.packageName());
        assertTrue(pkg.exists());

        IClassFile classFile = pkg.getClassFile(classInJar.classFileName());
        assertTrue(classFile.exists());

        openedEditor = openDefault(classFile);
        assertTrue(openedEditor instanceof JavaDecompilerClassFileEditor);

        JavaDecompilerClassFileEditor editor = (JavaDecompilerClassFileEditor) openedEditor;
        ITextEditor textEditor = adaptToTextEditor(editor);
        assertNotNull(textEditor);

        IDocument initialDocument = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
        assertNotNull(initialDocument);
        String initialContents = waitForNonEmptyDocument(initialDocument);
        assertTrue(initialContents.contains(stripClassExtension(classInJar.classFileName())));

        runInUiThread(() -> initialDocument.set("")); //$NON-NLS-1$
        assertTrue(runInUiThreadWithResult(editor::refreshContentIfNeeded));

        IDocument refreshedDocument = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
        assertNotNull(refreshedDocument);
        String refreshedContents = waitForNonEmptyDocument(refreshedDocument);
        assertTrue(refreshedContents.contains(stripClassExtension(classInJar.classFileName())));
        assertTrue(refreshedContents.contains(KEYWORD_CLASS));
    }

    @Test
    public void testUpdateTitleImageKeepsCurrentImageWhenDecompilerImageUnavailable()
            throws IOException, CoreException {
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

        Image customImage = runInUiThreadWithResult(() -> new Image(Display.getDefault(), 1, 1));
        try {
            runInUiThread(() -> {
                editor.setEditorTitleImage(customImage);
                editor.setDecompilerType(null);
                editor.updateTitleImage();
            });

            assertSame(customImage, editor.getTitleImage());
        } finally {
            runInUiThread(customImage::dispose);
        }
    }

    @Test
    public void testUpdateTitleImageRestoresDecompilerImageWhenAvailable()
            throws IOException, CoreException {
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

        Image customImage = runInUiThreadWithResult(() -> new Image(Display.getDefault(), 1, 1));
        try {
            runInUiThread(() -> {
                editor.setEditorTitleImage(customImage);
                editor.setDecompilerType(DECOMPILER_FERNFLOWER);
                editor.updateTitleImage();
            });

            assertSame(decompilerImage, editor.getTitleImage());
        } finally {
            runInUiThread(customImage::dispose);
        }
    }

    private static IEditorPart openWithEditorId(IClassFile classFile, String editorId) throws CoreException {
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

    private static IEditorPart openDefault(IClassFile classFile) throws CoreException {
        return openDefault((IJavaElement) classFile);
    }

    private static IEditorPart openDefault(IJavaElement element) throws CoreException {
        return runInUiThreadWithResult(() -> {
            IEditorPart editor = JavaUI.openInEditor(element, true, true);
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

            String className = e.getAttribute(KEYWORD_CLASS);
            if (expectedClassName.equals(className)) {
                return e.getAttribute("id"); //$NON-NLS-1$
            }
        }
        return null;
    }

    private static IPackagesViewPart showPackageExplorer() throws CoreException {
        return runInUiThreadWithResult(() -> {
            IWorkbenchWindow window = resolveWorkbenchWindow();
            IWorkbenchPage page = resolveWorkbenchPage(window);
            IViewPart view = page.showView(JavaUI.ID_PACKAGES);
            if (!(view instanceof IPackagesViewPart packagesView)) {
                throw new IllegalStateException("Package Explorer view is not available"); //$NON-NLS-1$
            }
            return packagesView;
        });
    }

    private static boolean waitForSelectedElement(JavaDecompilerClassFileEditor editor, String elementName) {
        int attempts = 250;
        for (int i = 0; i < attempts; i++) {
            if (hasSelectedElement(editor, elementName)) {
                return true;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(25L));
            drainUiEvents();
        }
        return hasSelectedElement(editor, elementName);
    }

    private static boolean hasSelectedElement(JavaDecompilerClassFileEditor editor, String elementName) {
        if (editor.getSelectedElement() instanceof IJavaElement javaElement) {
            return elementName.equals(javaElement.getElementName());
        }
        return false;
    }

    private static boolean waitForPackageExplorerSelection(IPackagesViewPart packagesView, String... elementNames) {
        int attempts = 250;
        for (int i = 0; i < attempts; i++) {
            if (hasPackageExplorerSelection(packagesView, elementNames)) {
                return true;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(25L));
            drainUiEvents();
        }
        return hasPackageExplorerSelection(packagesView, elementNames);
    }

    private static boolean hasPackageExplorerSelection(IPackagesViewPart packagesView, String... elementNames) {
        IStructuredSelection selection = (IStructuredSelection) packagesView.getTreeViewer().getSelection();
        Object selected = selection.getFirstElement();
        if (selected instanceof IJavaElement javaElement) {
            for (String elementName : elementNames) {
                if (elementName.equals(javaElement.getElementName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String describePackageExplorerSelection(IPackagesViewPart packagesView) {
        IStructuredSelection selection = (IStructuredSelection) packagesView.getTreeViewer().getSelection();
        Object selected = selection.getFirstElement();
        if (selected instanceof IJavaElement javaElement) {
            return javaElement.getElementName() + " (" + javaElement.getClass().getName() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        return selected == null ? "<null>" : selected.toString() + " (" + selected.getClass().getName() + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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

    private static IClassFile getClassFileFromEditor(IEditorPart editor) {
        IEditorInput input = editor.getEditorInput();
        if (input instanceof IClassFileEditorInput classFileInput) {
            return classFileInput.getClassFile();
        }
        return null;
    }

    private static IType getType(IClassFile classFile) {
        if (classFile instanceof IOrdinaryClassFile ordinaryClassFile) {
            return ordinaryClassFile.getType();
        }
        throw new IllegalArgumentException("Class file is not ordinary: " + classFile.getElementName()); //$NON-NLS-1$
    }

    private static String waitForNonEmptyDocument(IDocument document) {
        int attempts = 250;
        for (int i = 0; i < attempts; i++) {
            String text = document.get();
            if (text != null && !text.trim().isEmpty()) {
                return text;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(25L));
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

    private void closeOpenedEditor() throws CoreException {
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

    private static Optional<ClassInJar> findPreferredClass(File jarFile) throws IOException {
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

    private static void runInUiThread(UiRunnable runnable) throws CoreException {
        Display display = Display.getDefault();
        if (display == null) {
            runnable.run();
            return;
        }
        final ArrayList<CoreException> errors = new ArrayList<>();
        display.syncExec(() -> {
            try {
                runnable.run();
            } catch (CoreException e) {
                errors.add(e);
            }
        });
        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
    }

    private static <T> T runInUiThreadWithResult(UiSupplier<T> supplier) throws CoreException {
        Display display = Display.getDefault();
        if (display == null) {
            return supplier.get();
        }
        final ArrayList<CoreException> errors = new ArrayList<>();
        final ArrayList<T> results = new ArrayList<>();
        display.syncExec(() -> {
            try {
                results.add(supplier.get());
            } catch (CoreException e) {
                errors.add(e);
            }
        });
        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
        return results.get(0);
    }

    private static void refreshDecompilerEditorAssociations() throws CoreException {
        runInUiThread(() -> new SetupRunnableAccessor().apply());
    }

    private interface UiRunnable {
        void run() throws CoreException;
    }

    private interface UiSupplier<T> {
        T get() throws CoreException;
    }

    private static final class SetupRunnableAccessor extends SetupRunnable {
        private void apply() {
            updateClassDefaultEditor();
        }
    }

    public record ClassInJar(String packageName, String classFileName) {
    }
}
