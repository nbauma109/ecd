/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.ui.search.IMatchPresentation;
import org.eclipse.jdt.ui.search.PatternQuerySpecification;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.search.ui.text.Match;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.SetupRunnable;
import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport;
import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport.BundleJarProjectSetup;

public class ApplicationLibrarySearchParticipantTest {

    private static final String TEST_BUNDLE_ID = "io.github.nbauma109.decompiler.tests"; //$NON-NLS-1$
    private static final String TEST_JAR_PATH = "src/test/resources/test.jar"; //$NON-NLS-1$
    private static final String TEST_PACKAGE = "test"; //$NON-NLS-1$
    private static final String DECOMPILER_FERNFLOWER = "Fernflower"; //$NON-NLS-1$
    private static final String PRINTLN = "println"; //$NON-NLS-1$
    private static final String SEARCH_ANNOTATION_TYPE = "org.eclipse.search.results"; //$NON-NLS-1$

    private IProject project;
    private File tempDir;

    @Before
    public void setUp() throws IOException, CoreException {
        refreshDecompilerEditorAssociations();
        tempDir = DecompilerTestSupport.createTargetTempDir("application-library-search"); //$NON-NLS-1$
        configurePreferences(tempDir);
    }

    @After
    public void tearDown() throws CoreException {
        closeAllEditors();
        waitForUiIdle();
        if (project != null && project.exists()) {
            project.delete(true, true, new NullProgressMonitor());
        }
        if (tempDir != null) {
            FileUtils.deleteQuietly(tempDir);
        }
        BytecodeSearchIndex.getDefault().stop();
    }

    @Test
    public void showMatchResolvesSystemOutPrintlnReferenceRangesFromTestJar()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                PRINTLN,
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library println references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("Expected System.out.println references from test.jar", matches.isEmpty()); //$NON-NLS-1$
        assertNull("Search must not open an editor while collecting results", activeTextEditor()); //$NON-NLS-1$

        IMatchPresentation presentation = participant.getUIParticipant();
        assertNotNull(presentation);

        for (Match match : matches) {
            assertFalse("Application library matches must not expose a directly openable Java element", //$NON-NLS-1$
                    match.getElement() instanceof IJavaElement);
            assertTrue("Application library matches must keep an adaptable Java tree element", //$NON-NLS-1$
                    match.getElement() instanceof IAdaptable
                            && ((IAdaptable) match.getElement()).getAdapter(IJavaElement.class) != null);
            runInUiThread(() -> {
                int offset = match.getOffset();
                int length = match.getLength();
                presentation.showMatch(match, offset, length, true);
            });
            waitForUiIdle();
            assertTrue("Consulting the search result must resolve a non-zero text offset", match.getOffset() > 0); //$NON-NLS-1$
            String selectedText = selectedText(match);
            assertTrue("Search result must point at the println invocation", selectedText.startsWith(PRINTLN + "(")); //$NON-NLS-1$ //$NON-NLS-2$
            assertTrue("Search result must include method arguments", selectedText.endsWith(")")); //$NON-NLS-1$ //$NON-NLS-2$
            assertEquals("Editor selection must stay on the println invocation after pending editor reveals run", //$NON-NLS-1$
                    selectedText, activeSelectedText());
            waitForUiIdle();
            assertEquals("Editor selection must not jump back to the enclosing method declaration", //$NON-NLS-1$
                    selectedText, activeSelectedText());
            assertSearchAnnotation(match);
        }
    }

    @Test
    public void participantDoesNotDuplicateJdtMethodDeclarationsFromTestJar()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-declaration-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "method1", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.DECLARATIONS,
                scope,
                "Application library method1 declarations"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertTrue("JDT already reports binary declarations; the ASM participant must not duplicate them", //$NON-NLS-1$
                matches.isEmpty());
    }

    @Test
    public void bytecodeMatchesFromInnerClassesAreShownInTopLevelEditor()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-inner-editor-test-project"); //$NON-NLS-1$
        project = setup.project();

        IPackageFragment pkg = setup.jarRoot().getPackageFragment(TEST_PACKAGE);
        IClassFile topLevelClassFile = pkg.getClassFile("Test.class"); //$NON-NLS-1$
        IClassFile innerClassFile = pkg.getClassFile("Test$Inner1.class"); //$NON-NLS-1$
        assertTrue(topLevelClassFile.exists());
        assertTrue(innerClassFile.exists());

        IMethod innerMethod = getType(innerClassFile).getMethod("method1", new String[0]); //$NON-NLS-1$
        assertTrue(innerMethod.exists());

        assertTrue("Inner-class bytecode matches must remain highlighted in the top-level decompiled editor", //$NON-NLS-1$
                isShownInSameTopLevelClass(topLevelClassFile, innerMethod));
    }

    private static List<Match> runSearchInBackground(ApplicationLibrarySearchParticipant participant,
            PatternQuerySpecification specification) throws Exception {
        List<Match> matches = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread searchThread = new Thread(() -> {
            try {
                participant.search(matches::add, specification, new NullProgressMonitor());
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "application-library-search-test"); //$NON-NLS-1$

        searchThread.start();
        while (searchThread.isAlive()) {
            searchThread.join(25L);
            drainUiEvents();
        }
        if (failure.get() != null) {
            throw new AssertionError("Search failed", failure.get()); //$NON-NLS-1$
        }
        return matches;
    }

    private static String selectedText(Match match) throws Exception {
        ITextEditor textEditor = activeTextEditor();
        assertNotNull("Expected search result consultation to open a text editor", textEditor); //$NON-NLS-1$
        IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
        assertNotNull(document);
        int offset = match.getOffset();
        int length = match.getLength();
        assertTrue("Resolved range must be inside the editor document", offset >= 0 && offset + length <= document.getLength()); //$NON-NLS-1$
        return document.get(offset, length);
    }

    private static String activeSelectedText() throws CoreException {
        return runInUiThreadWithResult(() -> {
            ITextEditor textEditor = activeTextEditor();
            assertNotNull("Expected search result consultation to keep a text editor active", textEditor); //$NON-NLS-1$
            if (textEditor.getSelectionProvider().getSelection() instanceof ITextSelection selection) {
                return selection.getText();
            }
            return ""; //$NON-NLS-1$
        });
    }

    private static void assertSearchAnnotation(Match match) throws Exception {
        runInUiThread(() -> {
            ITextEditor textEditor = activeTextEditor();
            assertNotNull("Expected search result consultation to keep a text editor active", textEditor); //$NON-NLS-1$
            IAnnotationModel model = textEditor.getDocumentProvider().getAnnotationModel(textEditor.getEditorInput());
            assertNotNull("Expected an editor annotation model", model); //$NON-NLS-1$
            boolean found = false;
            for (java.util.Iterator<Annotation> iterator = model.getAnnotationIterator(); iterator.hasNext();) {
                Annotation annotation = iterator.next();
                Position position = model.getPosition(annotation);
                if (SEARCH_ANNOTATION_TYPE.equals(annotation.getType()) && position != null
                        && position.getOffset() == match.getOffset() && position.getLength() == match.getLength()) {
                    found = true;
                    break;
                }
            }
            assertTrue("Search result must install the Eclipse search annotation for ruler icon and highlight", found); //$NON-NLS-1$
        });
    }

    private static ITextEditor activeTextEditor() throws CoreException {
        return runInUiThreadWithResult(() -> {
            IWorkbenchPage page = activePage();
            IEditorPart editor = page.getActiveEditor();
            if (editor instanceof ITextEditor textEditor) {
                return textEditor;
            }
            return editor == null ? null : editor.getAdapter(ITextEditor.class);
        });
    }

    private static void configurePreferences(File tempDir) {
        IPreferenceStore store = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        store.setValue(JavaDecompilerPlugin.TEMP_DIR, tempDir.getAbsolutePath());
        store.setValue(JavaDecompilerPlugin.DECOMPILER_TYPE, DECOMPILER_FERNFLOWER);
        store.setValue(JavaDecompilerPlugin.REUSE_BUFFER, false);
        store.setValue(JavaDecompilerPlugin.IGNORE_EXISTING, true);
        store.setValue(JavaDecompilerPlugin.DEFAULT_EDITOR, true);
    }

    private static void refreshDecompilerEditorAssociations() throws CoreException {
        runInUiThread(() -> new SetupRunnableAccessor().apply());
    }

    private static org.eclipse.jdt.core.IType getType(IClassFile classFile) {
        if (classFile instanceof IOrdinaryClassFile ordinaryClassFile) {
            return ordinaryClassFile.getType();
        }
        throw new IllegalArgumentException("Class file is not ordinary: " + classFile.getElementName()); //$NON-NLS-1$
    }

    private static boolean isShownInSameTopLevelClass(IJavaElement editorElement, IJavaElement javaElement)
            throws Exception {
        Class<?> presentationClass =
                Class.forName("io.github.nbauma109.decompiler.search.ApplicationLibrarySearchMatchPresentation"); //$NON-NLS-1$
        Method method = presentationClass.getDeclaredMethod(
                "isShownInSameTopLevelClass", IJavaElement.class, IJavaElement.class); //$NON-NLS-1$
        method.setAccessible(true);
        return Boolean.TRUE.equals(method.invoke(null, editorElement, javaElement));
    }

    private static void closeAllEditors() throws CoreException {
        runInUiThread(() -> activePage().closeAllEditors(false));
    }

    private static IWorkbenchPage activePage() {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
            if (windows != null && windows.length > 0) {
                window = windows[0];
            }
        }
        if (window == null || window.getActivePage() == null) {
            throw new IllegalStateException("No active workbench page available"); //$NON-NLS-1$
        }
        return window.getActivePage();
    }

    private static void drainUiEvents() {
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return;
        }
        while (display.readAndDispatch()) {
            // keep the UI thread responsive while the background search waits for the index job
        }
    }

    private static void waitForUiIdle() {
        for (int i = 0; i < 20; i++) {
            drainUiEvents();
            java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(25L));
        }
        drainUiEvents();
    }

    private static void runInUiThread(UiRunnable runnable) throws CoreException {
        Display display = Display.getDefault();
        if (display == null || Display.getCurrent() == display) {
            runnable.run();
            return;
        }
        AtomicReference<CoreException> failure = new AtomicReference<>();
        display.syncExec(() -> {
            try {
                runnable.run();
            } catch (CoreException e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    private static <T> T runInUiThreadWithResult(UiSupplier<T> supplier) throws CoreException {
        Display display = Display.getDefault();
        if (display == null || Display.getCurrent() == display) {
            return supplier.get();
        }
        AtomicReference<CoreException> failure = new AtomicReference<>();
        AtomicReference<T> result = new AtomicReference<>();
        display.syncExec(() -> {
            try {
                result.set(supplier.get());
            } catch (CoreException e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
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
}
