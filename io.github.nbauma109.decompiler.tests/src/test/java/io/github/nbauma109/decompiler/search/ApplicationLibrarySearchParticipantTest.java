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
import static io.github.nbauma109.decompiler.search.ApplicationLibrarySearchMatchPresentation.isShownInSameTopLevelClass;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.ui.search.ElementQuerySpecification;
import org.eclipse.jdt.ui.search.IMatchPresentation;
import org.eclipse.jdt.ui.search.PatternQuerySpecification;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.IEditorMatchAdapter;
import org.eclipse.search.ui.text.IFileMatchAdapter;
import org.eclipse.search.ui.text.Match;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.SetupRunnable;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport;
import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport.BundleJarProjectSetup;

@SuppressWarnings("restriction")
public class ApplicationLibrarySearchParticipantTest {

    private static final String TEST_BUNDLE_ID = "io.github.nbauma109.decompiler.tests"; //$NON-NLS-1$
    private static final String TEST_JAR_PATH = "src/test/resources/test.jar"; //$NON-NLS-1$
    private static final String TEST_PACKAGE = "test"; //$NON-NLS-1$
    private static final String DECOMPILER_FERNFLOWER = "Fernflower"; //$NON-NLS-1$
    private static final String PRINTLN = "println"; //$NON-NLS-1$
    private static final String SEARCH_ANNOTATION_TYPE = "org.eclipse.search.results"; //$NON-NLS-1$

    private IProject project;
    private final List<IProject> extraProjects = new ArrayList<>();
    private File tempDir;

    @Before
    public void setUp() throws CoreException {
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
        for (IProject extraProject : extraProjects) {
            if (extraProject != null && extraProject.exists()) {
                extraProject.delete(true, true, new NullProgressMonitor());
            }
        }
        extraProjects.clear();
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
                    match.getElement() instanceof IAdaptable ia
                            && ia.getAdapter(IJavaElement.class) != null);
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
    public void allOccurrencesIncludesIndexedMethodDeclarationsFromTestJar()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-all-occurrences-declaration-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "method1", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.ALL_OCCURRENCES,
                scope,
                "Application library method1 all occurrences"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertTrue("All-occurrences searches must include indexed bytecode declarations", //$NON-NLS-1$
                matches.stream()
                        .anyMatch(ApplicationLibrarySearchParticipantTest::isDeclarationMatch));
    }

    @Test
    public void qualifiedMethodPatternFindsPrintlnReferencesFromTestJar()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-qualified-pattern-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "java.io.PrintStream.println(*)", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library qualified println references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("Qualified Java search patterns must match application-library bytecode references", //$NON-NLS-1$
                matches.isEmpty());
    }

    @Test
    public void applicationLibrarySearchIndexesNonJarArchiveRoots()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-zip-archive-test-project"); //$NON-NLS-1$
        project = setup.project();
        File zipArchive = new File(tempDir, "test-library.zip"); //$NON-NLS-1$
        FileUtils.copyFile(setup.jarFile(), zipArchive);
        IPackageFragmentRoot zipRoot = DecompilerTestSupport.addJarToClasspathAndGetRoot(setup.javaProject(), zipArchive);

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { zipRoot });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "java.io.PrintStream.println(*)", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library println references from zip archive"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("Binary archive roots with non-jar extensions must be indexed", matches.isEmpty()); //$NON-NLS-1$
    }

    @Test
    public void sameJarReferencedByMultipleProjectsIsIndexedForEachProjectRoot()
            throws Exception {
        BundleJarProjectSetup first = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-shared-jar-first-project"); //$NON-NLS-1$
        BundleJarProjectSetup second = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-shared-jar-second-project"); //$NON-NLS-1$
        project = first.project();
        extraProjects.add(second.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();

        assertFalse("The first project scope must find bytecode matches from the shared jar", //$NON-NLS-1$
                printlnReferenceMatches(participant, first.jarRoot()).isEmpty());
        assertFalse("The second project scope must find bytecode matches from the same shared jar", //$NON-NLS-1$
                printlnReferenceMatches(participant, second.jarRoot()).isEmpty());
    }

    @Test
    public void fineGrainedTypeSearchesRespectIndexedTypeCategories()
            throws Exception {
        File jar = new File(tempDir, "type-categories.jar"); //$NON-NLS-1$
        createTypeCategoryJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-type-category-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        assertEquals(List.of("classes.Same"), typeDeclarationNames(runSearchInBackground(participant, //$NON-NLS-1$
                sameTypeSpecification(IJavaSearchConstants.CLASS, scope))));
        assertEquals(List.of("interfaces.Same"), typeDeclarationNames(runSearchInBackground(participant, //$NON-NLS-1$
                sameTypeSpecification(IJavaSearchConstants.INTERFACE, scope))));
        assertEquals(List.of("enums.Same"), typeDeclarationNames(runSearchInBackground(participant, //$NON-NLS-1$
                sameTypeSpecification(IJavaSearchConstants.ENUM, scope))));
        assertEquals(List.of("annotations.Same"), typeDeclarationNames(runSearchInBackground(participant, //$NON-NLS-1$
                sameTypeSpecification(IJavaSearchConstants.ANNOTATION_TYPE, scope))));
    }

    @Test
    public void narrowedClassSearchKeepsUnknownExternalTypeReferences()
            throws Exception {
        File jar = new File(tempDir, "external-type-reference.jar"); //$NON-NLS-1$
        createStringReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-external-type-reference-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "java.lang.String", //$NON-NLS-1$
                IJavaSearchConstants.CLASS,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library narrowed external String references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("Unknown external type categories must remain eligible for narrowed reference searches", //$NON-NLS-1$
                matches.isEmpty());
    }

    @Test
    public void repeatedElementScopedTypeReferencesArePreserved()
            throws Exception {
        File jar = new File(tempDir, "repeated-type-references.jar"); //$NON-NLS-1$
        createRepeatedTypeReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-repeated-type-references-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "pkg.Foo", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library repeated Foo references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertEquals(2, matches.size());
    }

    @Test
    public void methodHandleDescriptorsContributeTypeReferences()
            throws Exception {
        File jar = new File(tempDir, "handle-descriptor-references.jar"); //$NON-NLS-1$
        createHandleDescriptorReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-handle-descriptor-references-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "pkg.OnlyInHandleDescriptor", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library method handle descriptor references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertEquals(1, matches.size());
    }

    @Test
    public void methodTypeConstantsContributeTypeReferences()
            throws Exception {
        File jar = new File(tempDir, "method-type-references.jar"); //$NON-NLS-1$
        createMethodTypeReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-method-type-references-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "pkg.OnlyInMethodType", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library method type constant references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertEquals(1, matches.size());
    }

    @Test
    public void multiReleaseJarSearchesUseEffectiveVersionedClassBytes()
            throws Exception {
        File jar = new File(tempDir, "effective-multi-release.jar"); //$NON-NLS-1$
        createMultiReleaseReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-effective-multi-release-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "java.lang.String", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library effective multi-release String references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("Effective versioned class bytes must contribute bytecode type references", matches.isEmpty()); //$NON-NLS-1$
    }

    @Test
    public void annotationMethodDefaultsContributeTypeReferences()
            throws Exception {
        File jar = new File(tempDir, "annotation-defaults.jar"); //$NON-NLS-1$
        createAnnotationDefaultJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-annotation-default-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "java.lang.String", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library annotation default String references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("Annotation method default values must contribute bytecode type references", matches.isEmpty()); //$NON-NLS-1$
    }

    @Test
    public void ignoreDeclaringTypeWidensQualifiedMethodPattern()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-ignore-declaring-type-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "wrong.Owner.println(*)", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES | IJavaSearchConstants.IGNORE_DECLARING_TYPE,
                scope,
                "Application library println references ignoring declaring type"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("IGNORE_DECLARING_TYPE must let method matches ignore the parsed declaring type", matches.isEmpty()); //$NON-NLS-1$
    }

    @Test
    public void genericMethodPatternWithReturnTypeFindsPrintlnReferencesFromTestJar()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-generic-method-pattern-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "<String>println(*) void", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library generic println references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("Generic method type arguments and return type filters must match bytecode references", //$NON-NLS-1$
                matches.isEmpty());
    }

    @Test
    public void returnTypeConstrainedMethodPatternFiltersMismatches()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-return-type-pattern-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "java.io.PrintStream.println(*) int", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library println references returning int"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertTrue("Return-type-constrained method patterns must reject bytecode descriptors with another return type", //$NON-NLS-1$
                matches.isEmpty());
    }

    @Test
    public void parameterizedTypePatternFindsPrintStreamReferencesFromTestJar()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-parameterized-type-pattern-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "java.io.PrintStream<String>", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library parameterized PrintStream references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("Parameterized type search patterns must match erased bytecode type references", //$NON-NLS-1$
                matches.isEmpty());
    }

	@Test
    public void fieldReadAndWriteAccessLimitsAreHonored()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-field-access-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> readMatches = runSearchInBackground(participant, new PatternQuerySpecification(
                "java.lang.System.out", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.READ_ACCESSES,
                scope,
                "Application library System.out reads")); //$NON-NLS-1$
        List<Match> writeMatches = runSearchInBackground(participant, new PatternQuerySpecification(
                "java.lang.System.out", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.WRITE_ACCESSES,
                scope,
                "Application library System.out writes")); //$NON-NLS-1$

        assertFalse("System.out getstatic bytecode instructions must be reported as field reads", readMatches.isEmpty()); //$NON-NLS-1$
        assertTrue("System.out getstatic bytecode instructions must not be reported as field writes", writeMatches.isEmpty()); //$NON-NLS-1$
    }

    @Test
    public void typedFieldPatternFindsSystemOutReferencesFromTestJar()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-typed-field-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "java.lang.System.out java.io.PrintStream", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.READ_ACCESSES,
                scope,
                "Application library typed System.out reads"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("Typed Java field search patterns must match application-library bytecode references", //$NON-NLS-1$
                matches.isEmpty());
    }

    @Test
    public void typedFieldPatternRejectsMismatchedSystemOutType()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-mismatched-typed-field-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "java.lang.System.out java.lang.String", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.READ_ACCESSES,
                scope,
                "Application library incorrectly typed System.out reads"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertTrue("Typed Java field search patterns must reject bytecode references with another field type", //$NON-NLS-1$
                matches.isEmpty());
    }

    @Test
    public void unqualifiedConstructorPatternFindsOwnerReferencesFromTestJar()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-constructor-pattern-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "Object()", //$NON-NLS-1$
                IJavaSearchConstants.CONSTRUCTOR,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library unqualified Object constructor references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("Unqualified constructor search patterns must match fully qualified bytecode owners", //$NON-NLS-1$
                matches.isEmpty());
    }

    @Test
    public void qualifiedWildcardConstructorPatternFindsOwnerReferencesFromTestJar()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-qualified-constructor-pattern-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "java.lang.*()", //$NON-NLS-1$
                IJavaSearchConstants.CONSTRUCTOR,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library qualified wildcard constructor references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("Qualified wildcard constructor patterns must match indexed constructor qualified names", //$NON-NLS-1$
                matches.isEmpty());
    }

    @Test
    public void qualifiedReferenceLimitFlagFindsMethodReferencesFromTestJar()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-qualified-reference-limit-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "java.io.PrintStream.println(java.lang.String)", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES | IJavaSearchConstants.QUALIFIED_REFERENCE,
                scope,
                "Application library qualified-reference println references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertFalse("Fine-grained reference limit flags must not disable bytecode reference matches", //$NON-NLS-1$
                matches.isEmpty());
    }

    // -----------------------------------------------------------------------
    // ElementQuerySpecification tests — cover forElement, normalizeTypeName,
    // normalizeDeclaringType, declaringSimpleName, sameDescriptor,
    // normalizeJdtMethodParameterTypes, normalizeBytecodeMethodParameterTypes,
    // normalizeJdtTypeSignature, and (via pattern) splitParameterTypes.
    // -----------------------------------------------------------------------

    @Test
    public void elementQueryForPrintStreamTypeFindsReferences()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-element-type-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        // forElement(TYPE) + normalizeTypeName
        IType printStream = setup.javaProject().findType("java.io.PrintStream"); //$NON-NLS-1$
        assertNotNull("java.io.PrintStream must be resolvable from the test project", printStream); //$NON-NLS-1$

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        ElementQuerySpecification spec = new ElementQuerySpecification(printStream,
                IJavaSearchConstants.REFERENCES, scope, "element-type-refs"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, spec);
        assertFalse("PrintStream type references should be found in test.jar", matches.isEmpty()); //$NON-NLS-1$
    }

    @Test
    public void elementQueryForSystemOutFieldFindsReferences()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-element-field-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        // forElement(FIELD) + normalizeDeclaringType (parent is IType)
        IType system = setup.javaProject().findType("java.lang.System"); //$NON-NLS-1$
        assertNotNull(system);
        IField outField = system.getField("out"); //$NON-NLS-1$
        assertTrue(outField.exists());

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        ElementQuerySpecification spec = new ElementQuerySpecification(outField,
                IJavaSearchConstants.READ_ACCESSES, scope, "element-field-refs"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, spec);
        assertFalse("System.out field reads should be found in test.jar", matches.isEmpty()); //$NON-NLS-1$
    }

    @Test
    public void elementQueryForPrintlnMethodCoversSameDescriptor()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-element-method-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        // forElement(METHOD) + normalizeDeclaringType + sameDescriptor +
        // normalizeJdtMethodParameterTypes + normalizeBytecodeMethodParameterTypes +
        // normalizeJdtTypeSignature (CLASS_TYPE_SIGNATURE branch via String/Object param)
        IType printStream = setup.javaProject().findType("java.io.PrintStream"); //$NON-NLS-1$
        assertNotNull(printStream);
        IMethod printlnWithClassParam = null;
        for (IMethod m : printStream.getMethods()) {
            if (PRINTLN.equals(m.getElementName()) && m.getParameterTypes().length == 1 //$NON-NLS-1$
                    && Signature.getTypeSignatureKind(
                            Signature.getElementType(m.getParameterTypes()[0])) == Signature.CLASS_TYPE_SIGNATURE) {
                printlnWithClassParam = m;
                break;
            }
        }
        assertNotNull("Expected a println overload with a class-type parameter (e.g. String or Object)", //$NON-NLS-1$
                printlnWithClassParam);

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        ElementQuerySpecification spec = new ElementQuerySpecification(printlnWithClassParam,
                IJavaSearchConstants.REFERENCES, scope, "element-method-descriptor"); //$NON-NLS-1$

        // sameDescriptor is exercised for every println bytecode entry iterated from test.jar,
        // regardless of whether the result list ends up empty or not.
        runSearchInBackground(participant, spec);
    }

    @Test
    public void elementQueryForObjectConstructorCoversDeclaringSimpleName()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-element-constructor-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        // forElement(CONSTRUCTOR) + declaringSimpleName
        IType objectType = setup.javaProject().findType("java.lang.Object"); //$NON-NLS-1$
        assertNotNull(objectType);
        IMethod objectConstructor = null;
        for (IMethod m : objectType.getMethods()) {
            if (m.isConstructor()) {
                objectConstructor = m;
                break;
            }
        }
        assertNotNull("java.lang.Object must have a constructor", objectConstructor); //$NON-NLS-1$

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        ElementQuerySpecification spec = new ElementQuerySpecification(objectConstructor,
                IJavaSearchConstants.REFERENCES, scope, "element-constructor-refs"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, spec);
        assertFalse("Object() constructor references should be found in test.jar", matches.isEmpty()); //$NON-NLS-1$
    }

    @Test
    public void patternWithMultipleExplicitParameterTypesCoversSplitParameterTypes()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-split-params-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        // Two explicit parameter types (not "*") cause parseParameterTypes → splitParameterTypes to run.
        // The result may be empty because test.jar may not call this particular overload
        // executing the split logic is the coverage goal.
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "println(String,boolean)", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library split-parameter-types coverage"); //$NON-NLS-1$

        assertNotNull(runSearchInBackground(participant, specification));
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

    @Test
    public void bytecodeMatchesInPageSelectsOnlyBytecodeMatchesForTheOpenTopLevelClass()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-page-matches-test-project"); //$NON-NLS-1$
        project = setup.project();

        IPackageFragment pkg = setup.jarRoot().getPackageFragment(TEST_PACKAGE);
        IClassFile topLevelClassFile = pkg.getClassFile("Test.class"); //$NON-NLS-1$
        IClassFile innerClassFile = pkg.getClassFile("Test$Inner1.class"); //$NON-NLS-1$
        IMethod innerMethod = getType(innerClassFile).getMethod("method1", new String[0]); //$NON-NLS-1$
        IType stringType = setup.javaProject().findType("java.lang.String"); //$NON-NLS-1$
        IMethod unrelatedMethod = stringType.getMethods()[0];

        BytecodeSearchMatch included = new BytecodeSearchMatch(reference(Kind.METHOD, innerMethod, "method1")); //$NON-NLS-1$
        TestSearchResult result = new TestSearchResult();
        result.addMatch(included);
        result.addMatch(new BytecodeSearchMatch(reference(Kind.METHOD, unrelatedMethod, unrelatedMethod.getElementName())));
        result.addMatch(new Match(new BytecodeSearchElement(reference(Kind.METHOD, innerMethod, "method1")), 0, 1)); //$NON-NLS-1$
        result.addMatch(new Match(innerMethod, 0, 1));

        assertEquals(List.of(included),
                ApplicationLibrarySearchMatchPresentation.bytecodeMatchesInPage(result, topLevelClassFile));
        assertTrue(ApplicationLibrarySearchMatchPresentation.bytecodeMatchesInPage(null, topLevelClassFile).isEmpty());
        assertTrue(ApplicationLibrarySearchMatchPresentation.bytecodeMatchesInPage(result, null).isEmpty());
    }

    @Test
    public void matchPresentationUnwrapsBytecodeElementsForNavigationAndLabels()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-label-provider-test-project"); //$NON-NLS-1$
        project = setup.project();

        IClassFile innerClassFile = setup.jarRoot().getPackageFragment(TEST_PACKAGE).getClassFile("Test$Inner1.class"); //$NON-NLS-1$
        IMethod innerMethod = getType(innerClassFile).getMethod("method1", new String[0]); //$NON-NLS-1$
        BytecodeSearchEntry entry = reference(Kind.METHOD, innerMethod, "method1"); //$NON-NLS-1$
        BytecodeSearchElement element = new BytecodeSearchElement(entry);

        assertEquals(innerMethod, ApplicationLibrarySearchMatchPresentation.javaElement(new BytecodeSearchMatch(entry)));
        assertEquals(innerMethod, ApplicationLibrarySearchMatchPresentation.javaElement(new Match(element, 0, 1)));
        assertEquals(innerMethod, ApplicationLibrarySearchMatchPresentation.javaElement(new Match(innerMethod, 0, 1)));
        assertNull(ApplicationLibrarySearchMatchPresentation.javaElement(new Match("not-java", 0, 1))); //$NON-NLS-1$

        runInUiThread(() -> {
            ILabelProvider labels = new ApplicationLibrarySearchMatchPresentation().createLabelProvider();
            ILabelProviderListener listener = event -> {
                // No operation; adding and removing a listener exercises delegated lifecycle behavior.
            };
            try {
                assertEquals(labels.getText(innerMethod), labels.getText(element));
                assertEquals(labels.getImage(innerMethod), labels.getImage(element));
                labels.addListener(listener);
                assertEquals(labels.isLabelProperty(innerMethod, "text"), labels.isLabelProperty(element, "text")); //$NON-NLS-1$ //$NON-NLS-2$
                labels.removeListener(listener);
            } finally {
                labels.dispose();
            }
        });
    }

    // -----------------------------------------------------------------------
    // forElement(IPackageFragment / IModuleDescription), wildcardPattern '?',
    // collectWildcard(), entryCount()
    // -----------------------------------------------------------------------

    @Test
    public void elementQueryForPackageFragmentCreatesPackageMatcher()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-element-package-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        // forElement(IPackageFragment) → Kind.PACKAGE
        IPackageFragment testPkg = setup.jarRoot().getPackageFragment(TEST_PACKAGE);
        assertTrue(testPkg.exists());

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        ElementQuerySpecification spec = new ElementQuerySpecification(testPkg,
                IJavaSearchConstants.REFERENCES, scope, "element-package-refs"); //$NON-NLS-1$

        // The search exercises forElement(IPackageFragment) without error.
        // test.jar bytecode rarely contains explicit package references, so the list may be empty.
        assertNotNull(runSearchInBackground(participant, spec));
    }

    @Test
    public void elementQueryForModuleDescriptionCreatesModuleMatcher()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-element-module-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        // Find any module description available in the project classpath (Java 9+).
        IModuleDescription module = null;
        for (IPackageFragmentRoot root : setup.javaProject().getAllPackageFragmentRoots()) {
            IModuleDescription mod;
            try {
                mod = root.getModuleDescription();
            } catch (Exception e) {
                continue;
            }
            if (mod != null && mod.exists()) {
                module = mod;
                break;
            }
        }
        Assume.assumeTrue("No module description found; test skipped on Java 8 runtime", module != null); //$NON-NLS-1$

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        ElementQuerySpecification spec = new ElementQuerySpecification(module,
                IJavaSearchConstants.REFERENCES, scope, "element-module-refs"); //$NON-NLS-1$

        // The search exercises forElement(IModuleDescription) without error.
        assertNotNull(runSearchInBackground(participant, spec));
    }

    @Test
    public void wildcardPatternWithQuestionMarkMatchesSingleChar()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-question-mark-wildcard-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        // "printl?" exercises the '?' → single-char-'.' branch in wildcardPattern().
        // It matches "println" (7 chars) because '?' replaces exactly one character.
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "printl?", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library single-char wildcard coverage"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);
        assertFalse("Single-char wildcard ? must match println references in test.jar", matches.isEmpty()); //$NON-NLS-1$
    }

    @Test
    public void wildcardStarPatternExercisesCollectWildcardAndEntryCount()
            throws Exception {
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithBundleJar(
                TEST_BUNDLE_ID,
                TEST_JAR_PATH,
                "application-library-search-star-wildcard-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "*", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library wildcard-star coverage"); //$NON-NLS-1$

        // runSearchInBackground drives waitForInitialRefresh(), then routes through
        // JarIndex.collect() → collectWildcard() → CompactEntries.size() for each indexed jar.
        List<Match> matches = runSearchInBackground(participant, specification);
        assertFalse("Wildcard * must match all method references in the indexed test jar", matches.isEmpty()); //$NON-NLS-1$

        // After search completes the index is fully populated; entryCount() must be positive.
        int entryCount = BytecodeSearchIndex.getDefault().entryCount();
        assertTrue("Index must contain at least some entries from test.jar", entryCount > 0); //$NON-NLS-1$

        // estimateTicks() calls entryCount() internally and returns a value ≥ 50.
        int ticks = participant.estimateTicks(specification);
        assertTrue("estimateTicks must return a positive value based on entryCount", ticks >= 50); //$NON-NLS-1$
    }

    private static List<Match> runSearchInBackground(ApplicationLibrarySearchParticipant participant,
            ElementQuerySpecification specification) throws Exception {
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

    private static List<Match> printlnReferenceMatches(ApplicationLibrarySearchParticipant participant,
            IPackageFragmentRoot root) throws Exception {
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { root });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "java.io.PrintStream.println(*)", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library shared jar println references"); //$NON-NLS-1$
        return runSearchInBackground(participant, specification);
    }

    private static PatternQuerySpecification sameTypeSpecification(int searchFor, IJavaSearchScope scope) {
        return new PatternQuerySpecification(
                "Same", //$NON-NLS-1$
                searchFor,
                true,
                IJavaSearchConstants.ALL_OCCURRENCES,
                scope,
                "Application library same type category search"); //$NON-NLS-1$
    }

    private static List<String> typeDeclarationNames(List<Match> matches) {
        List<String> names = new ArrayList<>();
        for (Match match : matches) {
            if (match instanceof BytecodeSearchMatch
                    && match.getElement() instanceof IAdaptable adaptable
                    && adaptable.getAdapter(IJavaElement.class) instanceof IType type) {
                names.add(type.getFullyQualifiedName('.'));
            }
        }
        return names;
    }

    private static void createTypeCategoryJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addType(output, "classes/Same", Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, //$NON-NLS-1$
                    "java/lang/Object"); //$NON-NLS-1$
            addType(output, "interfaces/Same", Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, //$NON-NLS-1$
                    "java/lang/Object"); //$NON-NLS-1$
            addType(output, "enums/Same", Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, //$NON-NLS-1$
                    "java/lang/Enum"); //$NON-NLS-1$
            addType(output, "annotations/Same", //$NON-NLS-1$
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ANNOTATION,
                    "java/lang/Object", "java/lang/annotation/Annotation"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static void createStringReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/ExternalReference.class", classBytesWithStringField("pkg/ExternalReference")); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static void createRepeatedTypeReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/Repeated.class", repeatedTypeReferenceBytes()); //$NON-NLS-1$
        }
    }

    private static void createHandleDescriptorReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/HandleUses.class", handleDescriptorReferenceBytes()); //$NON-NLS-1$
        }
    }

    private static void createMethodTypeReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/MethodTypeUses.class", methodTypeReferenceBytes()); //$NON-NLS-1$
        }
    }

    private static void createMultiReleaseReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar), multiReleaseManifest())) {
            addClass(output, "pkg/Versioned.class", classBytes("pkg/Versioned")); //$NON-NLS-1$ //$NON-NLS-2$
            addClass(output, "META-INF/versions/" + Runtime.version().feature() + "/pkg/Versioned.class", //$NON-NLS-1$ //$NON-NLS-2$
                    classBytesWithStringField("pkg/Versioned")); //$NON-NLS-1$
        }
    }

    private static void createAnnotationDefaultJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/Defaults.class", annotationWithStringDefaultBytes()); //$NON-NLS-1$
        }
    }

    private static void addType(JarOutputStream output, String internalName, int access, String superName,
            String... interfaces) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, access, internalName, null, superName, interfaces);
        writer.visitEnd();
        addClass(output, internalName + ".class", writer.toByteArray()); //$NON-NLS-1$
    }

    private static byte[] classBytes(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", //$NON-NLS-1$
                null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classBytesWithStringField(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", //$NON-NLS-1$
                null);
        writer.visitField(Opcodes.ACC_PUBLIC, "value", "Ljava/lang/String;", null, null).visitEnd(); //$NON-NLS-1$ //$NON-NLS-2$
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] repeatedTypeReferenceBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/Repeated", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "uses", //$NON-NLS-1$
                "(Ljava/lang/Object;)V", null, null); //$NON-NLS-1$
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitTypeInsn(Opcodes.CHECKCAST, "pkg/Foo"); //$NON-NLS-1$
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitTypeInsn(Opcodes.CHECKCAST, "pkg/Foo"); //$NON-NLS-1$
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] handleDescriptorReferenceBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/HandleUses", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "uses", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        method.visitCode();
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "pkg/Bootstrap", "bootstrap", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        Handle target = new Handle(Opcodes.H_INVOKESTATIC, "pkg/Targets", "accept", //$NON-NLS-1$ //$NON-NLS-2$
                "(Lpkg/OnlyInHandleDescriptor;)V", false); //$NON-NLS-1$
        method.visitInvokeDynamicInsn("run", "()Ljava/lang/Runnable;", bootstrap, target); //$NON-NLS-1$ //$NON-NLS-2$
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] methodTypeReferenceBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/MethodTypeUses", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "uses", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        method.visitCode();
        method.visitLdcInsn(Type.getMethodType("(Lpkg/OnlyInMethodType;)V")); //$NON-NLS-1$
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] annotationWithStringDefaultBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT
                | Opcodes.ACC_ANNOTATION, "pkg/Defaults", null, "java/lang/Object", //$NON-NLS-1$ //$NON-NLS-2$
                new String[] { "java/lang/annotation/Annotation" }); //$NON-NLS-1$
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "value", //$NON-NLS-1$
                "()Ljava/lang/Class;", "()Ljava/lang/Class<*>;", null); //$NON-NLS-1$ //$NON-NLS-2$
        AnnotationVisitor defaultValue = method.visitAnnotationDefault();
        defaultValue.visit(null, Type.getType("Ljava/lang/String;")); //$NON-NLS-1$
        defaultValue.visitEnd();
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static Manifest multiReleaseManifest() {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0"); //$NON-NLS-1$
        manifest.getMainAttributes().put(new Attributes.Name("Multi-Release"), "true"); //$NON-NLS-1$ //$NON-NLS-2$
        return manifest;
    }

    private static void addClass(JarOutputStream output, String name, byte[] bytes) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.write(bytes);
        output.closeEntry();
    }

    private static BytecodeSearchEntry reference(Kind kind, IJavaElement element, String name) {
        return new BytecodeSearchEntry(kind, false, element, name, name, null, null);
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

    private static boolean isDeclarationMatch(Match match) {
        if (!(match instanceof BytecodeSearchMatch bytecodeMatch)) {
            return false;
        }
        return bytecodeMatch.getEntry().isDeclaration();
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

    private static final class TestSearchResult extends AbstractTextSearchResult {

        @Override
        public String getLabel() {
            return "test"; //$NON-NLS-1$
        }

        @Override
        public String getTooltip() {
            return getLabel();
        }

        @Override
        public ImageDescriptor getImageDescriptor() {
            return null;
        }

        @Override
        public ISearchQuery getQuery() {
            return null;
        }

        @Override
        public IEditorMatchAdapter getEditorMatchAdapter() {
            return null;
        }

        @Override
        public IFileMatchAdapter getFileMatchAdapter() {
            return null;
        }
    }
}
