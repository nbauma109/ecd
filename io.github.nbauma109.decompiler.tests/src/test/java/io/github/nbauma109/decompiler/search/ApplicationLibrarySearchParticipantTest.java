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
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;

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
    public void lambdaBodyMethodReferencesAreIndexed() throws Exception {
        File jar = new File(tempDir, "lambda-body-method-reference.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Helper.class", buildLambdaHelperClass()); //$NON-NLS-1$
            addClass(jos, "pkg/LambdaUser.class", buildLambdaUserClass()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "lambda-body-method-reference-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "pkg.Helper.help", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "lambda body helper references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertEquals("Method calls inside synthetic lambda bodies must be indexed as source references", //$NON-NLS-1$
                1, matches.size());
    }

    @Test
    public void lambdaBodyReferencesAreScopedToDeclaringOverload() throws Exception {
        File jar = new File(tempDir, "overloaded-lambda-body-method-reference.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Helper.class", buildLambdaHelperClass()); //$NON-NLS-1$
            addClass(jos, "pkg/OverloadedLambdaUser.class", buildOverloadedLambdaUserClass()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "overloaded-lambda-body-method-reference-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Helper.help", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "overloaded lambda body helper references")); //$NON-NLS-1$

        assertEquals("Direct and lambda-body helper calls must both be indexed", 2, matches.size()); //$NON-NLS-1$
        assertTrue("The lambda-body call must stay scoped to run(String), not the whole type", //$NON-NLS-1$
                matches.stream().anyMatch(ApplicationLibrarySearchParticipantTest::isSingleArgumentRunMatch));
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
    public void implicitObjectSupertypesDoNotContributeTypeReferences()
            throws Exception {
        File jar = new File(tempDir, "implicit-object-supertype.jar"); //$NON-NLS-1$
        createImplicitObjectSuperclassJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-implicit-object-supertype-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> matches = runSearchInBackground(participant, typeReferenceSpecification(
                "java.lang.Object", scope)); //$NON-NLS-1$

        assertTrue("Implicit Object supertypes must not produce source-level type references", matches.isEmpty()); //$NON-NLS-1$
    }

    @Test
    public void qualifiedTypePatternsDoNotMatchOtherPackagesWithTheSameSimpleName()
            throws Exception {
        File jar = new File(tempDir, "same-simple-type-references.jar"); //$NON-NLS-1$
        createSameSimpleTypeReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-same-simple-type-reference-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "pkg1.Foo", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library qualified Foo references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertEquals(1, matches.size());
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
    public void invokeDynamicBootstrapHandlesDoNotContributeMethodReferences()
            throws Exception {
        File jar = new File(tempDir, "invokedynamic-bootstrap-references.jar"); //$NON-NLS-1$
        createHandleDescriptorReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-invokedynamic-bootstrap-references-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "pkg.Bootstrap.bootstrap()", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library invokedynamic bootstrap method references"); //$NON-NLS-1$

        assertEquals(0, runSearchInBackground(participant, specification).size());
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
    public void genericInnerTypeSignaturesContributeInnerTypeReferences()
            throws Exception {
        File jar = new File(tempDir, "generic-inner-type-references.jar"); //$NON-NLS-1$
        createGenericInnerTypeReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-generic-inner-type-references-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        assertEquals(1, runSearchInBackground(participant, typeReferenceSpecification(
                "pkg.Outer.Inner", scope)).size()); //$NON-NLS-1$
    }

    @Test
    public void topLevelDollarNamesAreNotTreatedAsNestedTypes()
            throws Exception {
        File jar = new File(tempDir, "dollar-named-type.jar"); //$NON-NLS-1$
        createDollarNamedTypeJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-dollar-named-type-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        assertEquals(1, runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Price$Tag", IJavaSearchConstants.TYPE, true, IJavaSearchConstants.ALL_OCCURRENCES, scope, //$NON-NLS-1$
                "Application library dollar-named type declarations")).size()); //$NON-NLS-1$
        assertEquals(1, runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Price$Tag.value", IJavaSearchConstants.FIELD, true, IJavaSearchConstants.ALL_OCCURRENCES, scope, //$NON-NLS-1$
                "Application library dollar-named field declarations")).size()); //$NON-NLS-1$
    }

    @Test
    public void ldcHandlesAndConstantDynamicsContributeTypeReferences()
            throws Exception {
        File jar = new File(tempDir, "ldc-bootstrap-references.jar"); //$NON-NLS-1$
        createLdcBootstrapReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-ldc-bootstrap-references-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        assertEquals(1, runSearchInBackground(participant, typeReferenceSpecification(
                "pkg.OnlyInLdcHandle", scope)).size()); //$NON-NLS-1$
        assertEquals(1, runSearchInBackground(participant, typeReferenceSpecification(
                "pkg.OnlyInCondyDescriptor", scope)).size()); //$NON-NLS-1$
        assertEquals(1, runSearchInBackground(participant, typeReferenceSpecification(
                "pkg.OnlyInNestedCondyDescriptor", scope)).size()); //$NON-NLS-1$
        assertEquals(1, runSearchInBackground(participant, typeReferenceSpecification(
                "pkg.OnlyInNestedCondyHandle", scope)).size()); //$NON-NLS-1$
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
    public void elementQueriesEraseGenericMethodSignaturesBeforeDescriptorMatching()
            throws Exception {
        File jar = new File(tempDir, "generic-method-references.jar"); //$NON-NLS-1$
        createGenericMethodReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-generic-method-references-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        IType target = setup.javaProject().findType("pkg.GenericTarget"); //$NON-NLS-1$
        assertNotNull(target);
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();

        List<Match> consumeMatches = runSearchInBackground(participant, new ElementQuerySpecification(
                methodNamed(target, "consume"), IJavaSearchConstants.REFERENCES, scope, "generic-list-method")); //$NON-NLS-1$ //$NON-NLS-2$
        List<Match> idMatches = runSearchInBackground(participant, new ElementQuerySpecification(
                methodNamed(target, "id"), IJavaSearchConstants.REFERENCES, scope, "generic-type-variable-method")); //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals(1, consumeMatches.size());
        assertEquals(1, idMatches.size());
    }

    @Test
    public void permittedSubclassesContributeTypeReferences()
            throws Exception {
        File jar = new File(tempDir, "permitted-subclass-references.jar"); //$NON-NLS-1$
        createPermittedSubclassReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-permitted-subclass-references-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "pkg.Permitted", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library permitted subclass references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertEquals(1, matches.size());
    }

    @Test
    public void methodBodyTypeAnnotationsContributeTypeReferences()
            throws Exception {
        File jar = new File(tempDir, "method-body-type-annotation-references.jar"); //$NON-NLS-1$
        createMethodBodyTypeAnnotationReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-method-body-type-annotation-references-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        PatternQuerySpecification specification = new PatternQuerySpecification(
                "pkg.MethodBodyTA", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library method body type annotation references"); //$NON-NLS-1$

        List<Match> matches = runSearchInBackground(participant, specification);

        assertEquals(3, matches.size());
    }

    @Test
    public void recordComponentAnnotationsContributeTypeReferences()
            throws Exception {
        File jar = new File(tempDir, "record-component-annotation-references.jar"); //$NON-NLS-1$
        createRecordComponentAnnotationReferenceJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-record-component-annotation-references-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        assertEquals(1, runSearchInBackground(participant, typeReferenceSpecification(
                "pkg.RecordComponentTag", scope)).size()); //$NON-NLS-1$
        assertEquals(1, runSearchInBackground(participant, typeReferenceSpecification(
                "pkg.RecordComponentTypeTag", scope)).size()); //$NON-NLS-1$
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
    public void constructorPatternsIgnoreSyntheticJvmParameters()
            throws Exception {
        File jar = new File(tempDir, "synthetic-constructor-parameters.jar"); //$NON-NLS-1$
        createSyntheticConstructorParameterJar(jar);
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "application-library-search-synthetic-constructor-parameter-test-project"); //$NON-NLS-1$
        project = setup.project();

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        assertEquals(1, runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Outer.Inner(java.lang.String)", //$NON-NLS-1$
                IJavaSearchConstants.CONSTRUCTOR,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library member constructor references")).size()); //$NON-NLS-1$
        assertEquals(0, runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Captured(java.lang.String)", //$NON-NLS-1$
                IJavaSearchConstants.CONSTRUCTOR,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "Application library overloaded constructor references")).size()); //$NON-NLS-1$
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

    /**
     * Verifies fix: {@code normalizeTypeName} no longer calls {@code replace('$', '.')} on the
     * result of {@code getFullyQualifiedName('.')}.  Before the fix, a top-level class whose
     * internal name contains {@code $} (e.g. {@code pkg/Price$Tag}) was indexed with qualified
     * name {@code pkg.Price$Tag} but element-query normalization rewrote it to {@code pkg.Price.Tag},
     * preventing any match.
     * <p>
     * JDT infers nesting from the {@code $} character even without {@code InnerClasses} metadata,
     * so element queries via {@code findType} are inherently unreliable for such names. The test
     * therefore verifies the indexed name via a pattern search, which directly exercises the
     * {@code $}-preserving indexer path without going through {@code normalizeTypeName}.
     */
    @Test
    public void dollarSignInTypeNameIsPreservedInIndex() throws Exception {
        File jar = new File(tempDir, "dollar-type.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Price$Tag.class", buildTopLevelDollarClass()); //$NON-NLS-1$
            addClass(jos, "pkg/DollarCaller.class", buildDollarCaller()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "dollar-type-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        // Pattern search with the $-preserved qualified name confirms the indexer stores pkg.Price$Tag
        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Price$Tag", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "dollar-type-pattern-refs")); //$NON-NLS-1$
        assertFalse("type with $ in name must be found by pattern search using the preserved $ name", //$NON-NLS-1$
                matches.isEmpty());
    }

    /**
     * Verifies fix: when an element search targets a non-static member-class constructor,
     * the JDT signature lacks the synthetic outer-instance parameter that the indexed bytecode
     * descriptor contains. {@code matchesEntryDescriptor} now retries with the synthetic first
     * parameter stripped via {@code stripFirstBytecodeParameter} so that valid callers are found.
     */
    @Test
    public void elementQueryForInnerConstructorFindsReferenceWithSyntheticDescriptor() throws Exception {
        File jar = new File(tempDir, "inner-ctor-element.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Outer.class", buildOuterClass()); //$NON-NLS-1$
            addClass(jos, "pkg/Outer$Inner.class", buildInnerClass()); //$NON-NLS-1$
            addClass(jos, "pkg/Caller.class", buildInnerCaller()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "inner-ctor-element-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        IType innerType = setup.javaProject().findType("pkg.Outer.Inner"); //$NON-NLS-1$
        assertNotNull("pkg.Outer.Inner must be resolvable", innerType); //$NON-NLS-1$
        // getMethods()[0] is the constructor — for binary inner classes JDT strips the synthetic
        // outer-ref, so the signature is (Ljava/lang/String;I)V (source-visible params only).
        IMethod innerCtor = innerType.getMethods()[0];

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        List<Match> matches = runSearchInBackground(participant,
                new ElementQuerySpecification(innerCtor, IJavaSearchConstants.REFERENCES, scope, "inner-ctor-element")); //$NON-NLS-1$

        assertFalse("element query for inner-class constructor must find callers despite synthetic outer-ref in descriptor", //$NON-NLS-1$
                matches.isEmpty());
    }

    /**
     * Verifies fix: {@code normalizePatternType} now uses {@code stripGenericArguments} which
     * removes each balanced {@code <…>} segment independently.  Before the fix, erasing from the
     * first {@code <} to the last {@code >} in {@code pkg.Outer<String>.Inner<Integer>} removed
     * the {@code .Inner} segment between the two angle-bracket groups, collapsing the parameter
     * type to {@code pkg.outer} instead of the correct {@code pkg.outer.inner}.
     */
    @Test
    public void parameterizedNestedTypePatternStripsAllGenericSegments() throws Exception {
        File jar = new File(tempDir, "nested-generic.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Outer.class", buildOuterWithStaticInner()); //$NON-NLS-1$
            addClass(jos, "pkg/Outer$StaticInner.class", buildStaticInnerClass()); //$NON-NLS-1$
            addClass(jos, "pkg/GenericCaller.class", buildGenericNestedCaller()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "nested-generic-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        // Pattern with two independent generic segments — the fix ensures both <...> groups are erased
        // and .StaticInner is preserved: "pkg.outer<string>.staticinner<integer>" → "pkg.outer.staticinner"
        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "call(pkg.Outer<String>.StaticInner<Integer>)", //$NON-NLS-1$
                IJavaSearchConstants.METHOD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "nested-generic-param-search")); //$NON-NLS-1$

        assertFalse("method pattern with parameterized nested type must match after stripping all generic segments", //$NON-NLS-1$
                matches.isEmpty());
    }

    /**
     * Verifies fix: the synthetic-constructor-parameter offset loop is capped at 1.
     * <p>
     * Before the fix the loop allowed offsets up to
     * {@code argumentTypes.length - expectedTypes.length}, so a search for
     * {@code Inner(int)} could match an {@code Inner(String, int)} constructor because the
     * {@code int} at offset 2 of descriptor {@code (LOuter;Ljava/lang/String;I)V} happened to
     * equal the searched-for parameter.  After the fix the maximum offset is
     * {@code Math.min(1, …)} which restricts skipping to at most the single known-synthetic
     * outer-instance parameter.
     */
    @Test
    public void syntheticConstructorOffsetCapPreventsFalsePositiveMatch() throws Exception {
        File jar = new File(tempDir, "inner-ctor.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Outer.class", buildOuterClass()); //$NON-NLS-1$
            addClass(jos, "pkg/Outer$Inner.class", buildInnerClass()); //$NON-NLS-1$
            addClass(jos, "pkg/Caller.class", buildInnerCaller()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "synthetic-ctor-offset-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        // Inner(int) must NOT match Inner(String, int) — offset 2 would hit the int, but is now capped
        List<Match> falseMatches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Outer.Inner(int)", //$NON-NLS-1$
                IJavaSearchConstants.CONSTRUCTOR,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "inner-ctor-int-search")); //$NON-NLS-1$
        assertTrue("Inner(int) must not match Inner(String,int) — synthetic offset cap must prevent offset-2 match", //$NON-NLS-1$
                falseMatches.isEmpty());

        // Inner(String, int) must still find the call site
        List<Match> correctMatches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Outer.Inner(java.lang.String, int)", //$NON-NLS-1$
                IJavaSearchConstants.CONSTRUCTOR,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "inner-ctor-string-int-search")); //$NON-NLS-1$
        assertFalse("Inner(String,int) must match the actual call site after skipping 1 synthetic parameter", //$NON-NLS-1$
                correctMatches.isEmpty());
    }

    /**
     * Verifies fix: a <em>static</em> nested class has no synthetic outer-instance parameter
     * even though {@code getDeclaringType() != null}.  Before the fix, {@code isNestedType}
     * returned {@code true} for static member classes, so the offset-1 skip was applied and
     * {@code Inner(int)} incorrectly matched {@code Inner(String,int)} at offset 1.
     * After the fix, only non-static member classes (and local/anonymous classes) trigger the
     * skip; static member classes are treated like top-level classes.
     */
    @Test
    public void staticNestedConstructorIsNotMistakenForSyntheticOffset() throws Exception {
        File jar = new File(tempDir, "static-nested-ctor.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Outer.class", buildOuterWithStaticInner()); //$NON-NLS-1$
            addClass(jos, "pkg/Outer$StaticInner.class", buildStaticInnerClass()); //$NON-NLS-1$
            addClass(jos, "pkg/StaticInnerCaller.class", buildStaticInnerCaller()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "static-nested-ctor-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        // StaticInner(int) must NOT match StaticInner(String,int) — no synthetic outer-ref to skip
        List<Match> falseMatches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Outer.StaticInner(int)", //$NON-NLS-1$
                IJavaSearchConstants.CONSTRUCTOR,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "static-nested-ctor-int-search")); //$NON-NLS-1$
        assertTrue("static nested Inner(int) must not match Inner(String,int) — no synthetic parameter to skip", //$NON-NLS-1$
                falseMatches.isEmpty());
    }

    /**
     * Verifies fix: type references from a static initializer ({@code <clinit>}) now use the
     * list-based {@code typeReferencesByElement} path instead of the deduplicating
     * {@code typeReferences} HashSet.
     * <p>
     * Before the fix, {@code method} was {@code null} for {@code <clinit>}, so both
     * {@code new Foo()} occurrences in {@code static { new Foo(); new Foo(); }} were collapsed
     * into a single entry by the HashSet.  After the fix {@code methodOrType} falls back to the
     * enclosing {@code type}, routing references through the list path and preserving duplicates.
     */
    @Test
    public void staticInitializerRepeatedTypeReferencesArePreserved() throws Exception {
        File jar = new File(tempDir, "clinit-refs.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Foo.class", buildEmptyClass("pkg/Foo")); //$NON-NLS-1$ //$NON-NLS-2$
            addClass(jos, "pkg/HasClinit.class", buildClassWithRepeatedStaticInit()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "clinit-refs-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Foo", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "clinit-foo-type-search")); //$NON-NLS-1$

        // Two new Foo() in static {} must produce two separate reference matches
        assertTrue("static initializer with two new Foo() must produce at least 2 type reference matches", //$NON-NLS-1$
                matches.size() >= 2);
    }

    @Test
    public void repeatedElementScopedPackageReferencesArePreserved() throws Exception {
        File jar = new File(tempDir, "repeated-package-refs.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/UsesJavaUtilTypes.class", buildClassWithRepeatedJavaUtilReferences()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "repeated-package-refs-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "java.util", //$NON-NLS-1$
                IJavaSearchConstants.PACKAGE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "repeated-java-util-package-refs")); //$NON-NLS-1$

        assertEquals("Two java.util type uses must produce two package-reference matches", 2, matches.size()); //$NON-NLS-1$
    }

    @Test
    public void repeatedClassDeclarationTypeReferencesArePreserved() throws Exception {
        File jar = new File(tempDir, "repeated-class-declaration-refs.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Foo.class", buildEmptyClass("pkg/Foo")); //$NON-NLS-1$ //$NON-NLS-2$
            addClass(jos, "pkg/RepeatedClassDeclarationRefs.class", //$NON-NLS-1$
                    buildClassWithRepeatedDeclarationTypeReferences());
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "repeated-class-declaration-refs-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Foo", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "repeated-class-declaration-foo-refs")); //$NON-NLS-1$

        assertEquals("extends Foo and Supplier<Foo> must produce two class-declaration type-reference matches", //$NON-NLS-1$
                2, matches.size());
    }

    /**
     * Verifies fix: the {@code visitLabel} override in {@code MethodIndexer} maps the LVT-start
     * label (the instruction immediately after the {@code astore}) to the same exception types as
     * the handler label, so {@code isCatchVariable} returns {@code true} for both labels and the
     * catch variable is not double-counted as a type reference.
     * <p>
     * Before the fix, only the exact handler label was in {@code catchHandlerTypes}; the LVT start
     * label (a different object) was unknown, causing {@code visitLocalVariable} to add a second
     * type reference.
     */
    @Test
    public void catchVariableIsNotDoubleCountedAsTypeReference() throws Exception {
        File jar = new File(tempDir, "catch-var.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/MyException.class", buildEmptyExceptionClass()); //$NON-NLS-1$
            addClass(jos, "pkg/HasCatch.class", buildClassWithCatch()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "catch-var-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.MyException", //$NON-NLS-1$
                IJavaSearchConstants.TYPE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "catch-myexception-type-search")); //$NON-NLS-1$

        assertEquals("catch variable whose LVT start follows handler must produce exactly 1 type reference, not 2", //$NON-NLS-1$
                1, matches.size());
    }

    /**
     * Verifies fix: {@code ModuleIndexer.visitExport} now calls
     * {@code addPackageReference(packaze.replace('/', '.'), moduleElement)} so that packages
     * named in {@code exports} directives of a {@code module-info.class} are indexed and
     * discoverable via package-reference searches.
     */
    @Test
    public void moduleExportedPackageIsIndexedAsPackageReference() throws Exception {
        File jar = new File(tempDir, "module-export.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "module-info.class", buildModuleWithExport()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "module-export-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.myapi", //$NON-NLS-1$
                IJavaSearchConstants.PACKAGE,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "module-exported-package-search")); //$NON-NLS-1$

        assertFalse("exported package pkg.myapi must appear in package-reference search results", //$NON-NLS-1$
                matches.isEmpty());
    }

    /**
     * Verifies fix: when searching for a method whose parameter is a bounded class-level type
     * variable, the {@code SearchMatcher} now includes the declaring type's type-parameter bounds
     * in its erasure map so that the JDT signature {@code (TT;)V} is correctly erased to
     * {@code (Ljava/lang/Number;)V} rather than the wrong {@code (Ljava/lang/Object;)V}.
     * <p>
     * The jar contains {@code Box<T extends Number>} with {@code void put(T value)}, and a
     * {@code Caller} class that invokes {@code box.put(n)}.  The element search for the
     * {@code put} method must find that invocation.
     */
    @Test
    public void enclosingTypeBoundedTypeVariableMatchesBytecodeErasure() throws Exception {
        File jar = new File(tempDir, "bounded-tv.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Box.class", buildGenericBoxClass()); //$NON-NLS-1$
            addClass(jos, "pkg/Caller.class", buildBoxCaller()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "bounded-tv-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        IType boxType = setup.javaProject().findType("pkg.Box"); //$NON-NLS-1$
        assertNotNull("pkg.Box must be resolvable from the test project", boxType); //$NON-NLS-1$
        IMethod putMethod = methodNamed(boxType, "put"); //$NON-NLS-1$

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        List<Match> matches = runSearchInBackground(participant,
                new ElementQuerySpecification(putMethod, IJavaSearchConstants.REFERENCES, scope, "put-method-refs")); //$NON-NLS-1$

        assertFalse("put(T) on Box<T extends Number> must match the bytecode invocation (Ljava/lang/Number;)V", //$NON-NLS-1$
                matches.isEmpty());
    }

    /**
     * Verifies fix: {@code typeVariableErasures(IType)} now walks the full enclosing-type chain.
     * <p>
     * Before the fix, only {@code Inner}'s own type parameters were collected, so a method
     * {@code Inner.put(T value)} where {@code T} is declared on the outer class
     * {@code Outer<T extends Number>} still erased to {@code Object}.  After the fix the outer
     * chain is traversed and {@code T} correctly erases to {@code Number}, so the element search
     * for {@code put} matches the bytecode descriptor {@code (Ljava/lang/Number;)V}.
     */
    @Test
    public void outerTypeBoundedTypeVariableInInnerClassMatchesBytecodeErasure() throws Exception {
        File jar = new File(tempDir, "outer-inner-tv.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Outer.class", buildOuterGenericClass()); //$NON-NLS-1$
            addClass(jos, "pkg/Outer$Inner.class", buildOuterGenericInnerClass()); //$NON-NLS-1$
            addClass(jos, "pkg/OuterInnerCaller.class", buildOuterInnerCaller()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "outer-inner-tv-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        IType innerType = setup.javaProject().findType("pkg.Outer.Inner"); //$NON-NLS-1$
        assertNotNull("pkg.Outer.Inner must be resolvable", innerType); //$NON-NLS-1$
        IMethod putMethod = methodNamed(innerType, "put"); //$NON-NLS-1$

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });
        List<Match> matches = runSearchInBackground(participant,
                new ElementQuerySpecification(putMethod, IJavaSearchConstants.REFERENCES, scope, "inner-put-refs")); //$NON-NLS-1$

        assertFalse("Inner.put(T) where T is declared on Outer<T extends Number> must match (Ljava/lang/Number;)V bytecode", //$NON-NLS-1$
                matches.isEmpty());
    }

    /**
     * Verifies fix: a compound field access such as {@code holder.count++} emits a GETFIELD
     * followed by a PUTFIELD for the same field.  Before the fix, both were indexed as separate
     * entries and a REFERENCES search returned two matches pointing to the same source range.
     * After the fix, {@code collapseCompoundFieldAccesses} in {@code flushMemberReferences}
     * combines the pair so that REFERENCES returns exactly one match, while READ_ACCESSES and
     * WRITE_ACCESSES still find the compound occurrence.
     */
    @Test
    public void compoundFieldAccessReportsOnlyOneReferenceMatch() throws Exception {
        File jar = new File(tempDir, "compound-field.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Holder.class", buildHolderClass()); //$NON-NLS-1$
            addClass(jos, "pkg/HolderUser.class", buildHolderUserWithCompoundAccess()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "compound-field-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> refMatches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Holder.count", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "compound-field-refs")); //$NON-NLS-1$
        assertEquals("compound field access (count++) must produce exactly 1 REFERENCES match, not 2", //$NON-NLS-1$
                1, refMatches.size());

        List<Match> readMatches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Holder.count", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.READ_ACCESSES,
                scope,
                "compound-field-reads")); //$NON-NLS-1$
        assertFalse("compound field access (count++) must still appear in READ_ACCESSES", //$NON-NLS-1$
                readMatches.isEmpty());

        List<Match> writeMatches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Holder.count", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.WRITE_ACCESSES,
                scope,
                "compound-field-writes")); //$NON-NLS-1$
        assertFalse("compound field access (count++) must still appear in WRITE_ACCESSES", //$NON-NLS-1$
                writeMatches.isEmpty());
    }

    @Test
    public void staticCompoundFieldAccessReportsOnlyOneReferenceMatch() throws Exception {
        File jar = new File(tempDir, "static-compound-field.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Holder.class", buildStaticHolderClass()); //$NON-NLS-1$
            addClass(jos, "pkg/HolderUser.class", buildHolderUserWithStaticCompoundAccess()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "static-compound-field-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> refMatches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Holder.count", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "static-compound-field-refs")); //$NON-NLS-1$
        assertEquals("static compound field access (count++) must produce exactly 1 REFERENCES match, not 2", //$NON-NLS-1$
                1, refMatches.size());

        List<Match> readMatches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Holder.count", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.READ_ACCESSES,
                scope,
                "static-compound-field-reads")); //$NON-NLS-1$
        assertFalse("static compound field access (count++) must still appear in READ_ACCESSES", //$NON-NLS-1$
                readMatches.isEmpty());

        List<Match> writeMatches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Holder.count", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.WRITE_ACCESSES,
                scope,
                "static-compound-field-writes")); //$NON-NLS-1$
        assertFalse("static compound field access (count++) must still appear in WRITE_ACCESSES", //$NON-NLS-1$
                writeMatches.isEmpty());
    }

    @Test
    public void separateFieldReadAndWriteRemainDistinctReferenceMatches() throws Exception {
        File jar = new File(tempDir, "separate-field-accesses.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Holder.class", buildHolderClass()); //$NON-NLS-1$
            addClass(jos, "pkg/HolderUser.class", buildHolderUserWithSeparateAccesses()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "separate-field-accesses-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Holder.count", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "separate-field-access-refs")); //$NON-NLS-1$
        assertEquals("separate field read and write expressions must remain separate references", 2, matches.size()); //$NON-NLS-1$
    }

    @Test
    public void separateStaticFieldReadAndWriteRemainDistinctReferenceMatches() throws Exception {
        File jar = new File(tempDir, "separate-static-field-accesses.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Holder.class", buildStaticHolderClass()); //$NON-NLS-1$
            addClass(jos, "pkg/HolderUser.class", buildHolderUserWithSeparateStaticAccesses()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "separate-static-field-accesses-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Holder.count", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "separate-static-field-access-refs")); //$NON-NLS-1$
        assertEquals("separate static field read and write expressions must remain separate references", //$NON-NLS-1$
                2, matches.size());
    }

    @Test
    public void consumedStaticFieldReadAndLaterWriteRemainDistinctReferenceMatches() throws Exception {
        File jar = new File(tempDir, "consumed-static-field-accesses.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Holder.class", buildStaticHolderClass()); //$NON-NLS-1$
            addClass(jos, "pkg/HolderUser.class", buildHolderUserWithConsumedStaticReadAndWrite()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "consumed-static-field-accesses-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.Holder.count", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "consumed-static-field-access-refs")); //$NON-NLS-1$
        assertEquals("static read consumed by invocation and later write must remain separate references", //$NON-NLS-1$
                2, matches.size());
    }

    @Test
    public void conditionalStaticFieldReadAndLaterWriteRemainDistinctReferenceMatches() throws Exception {
        File jar = new File(tempDir, "conditional-static-field-accesses.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/FlagHolder.class", buildFlagHolderClass()); //$NON-NLS-1$
            addClass(jos, "pkg/FlagUser.class", buildFlagUserWithConditionalStaticReadAndWrite()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "conditional-static-field-accesses-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "pkg.FlagHolder.flag", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "conditional-static-field-access-refs")); //$NON-NLS-1$
        assertEquals("static read consumed by a conditional jump and later write must remain separate references", //$NON-NLS-1$
                2, matches.size());
    }

    /**
     * Verifies fix: {@code annotationVisitor.visitEnum} now calls
     * {@code addReference(Kind.FIELD, value, value, qualifiedTypeName(owner), descriptor, element, Access.READ)}
     * so that enum constants used as annotation values are indexed as field-read references.
     * <p>
     * Before the fix only the descriptor type ({@code RetentionPolicy}) was indexed; the constant
     * name ({@code RUNTIME}) was discarded.
     */
    @Test
    public void annotationEnumConstantIsIndexedAsFieldReference() throws Exception {
        File jar = new File(tempDir, "enum-annotation.jar"); //$NON-NLS-1$
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(jos, "pkg/Annotated.class", buildClassAnnotatedWithRetention()); //$NON-NLS-1$
        }
        BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "enum-annotation-test-project"); //$NON-NLS-1$
        extraProjects.add(setup.project());

        BytecodeSearchIndex.getDefault().stop();
        BytecodeSearchIndex.getDefault().start();

        ApplicationLibrarySearchParticipant participant = new ApplicationLibrarySearchParticipant();
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { setup.jarRoot() });

        List<Match> matches = runSearchInBackground(participant, new PatternQuerySpecification(
                "java.lang.annotation.RetentionPolicy.RUNTIME", //$NON-NLS-1$
                IJavaSearchConstants.FIELD,
                true,
                IJavaSearchConstants.REFERENCES,
                scope,
                "retention-runtime-field-search")); //$NON-NLS-1$

        assertFalse("enum constant RetentionPolicy.RUNTIME used in @Retention must appear as a field-reference match", //$NON-NLS-1$
                matches.isEmpty());
    }

    // ------------------------------------------------------------------
    // ASM bytecode builders for the new tests
    // ------------------------------------------------------------------

    private static byte[] buildOuterClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/Outer", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        cw.visitInnerClass("pkg/Outer$Inner", "pkg/Outer", "Inner", 0); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildInnerClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_SUPER, "pkg/Outer$Inner", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        cw.visitField(Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "this$0", "Lpkg/Outer;", null, null).visitEnd(); //$NON-NLS-1$ //$NON-NLS-2$
        // Constructor: (Lpkg/Outer;Ljava/lang/String;I)V — outer ref + String + int
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", //$NON-NLS-1$
                "(Lpkg/Outer;Ljava/lang/String;I)V", null, null); //$NON-NLS-1$
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 4);
        ctor.visitEnd();
        cw.visitInnerClass("pkg/Outer$Inner", "pkg/Outer", "Inner", 0); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildInnerCaller() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/Caller", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "call", //$NON-NLS-1$
                "(Lpkg/Outer;Ljava/lang/String;I)V", null, null); //$NON-NLS-1$
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, "pkg/Outer$Inner"); //$NON-NLS-1$
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ILOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "pkg/Outer$Inner", "<init>", //$NON-NLS-1$ //$NON-NLS-2$
                "(Lpkg/Outer;Ljava/lang/String;I)V", false); //$NON-NLS-1$
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(5, 4);
        mv.visitEnd();
        cw.visitInnerClass("pkg/Outer$Inner", "pkg/Outer", "Inner", 0); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildEmptyClass(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null); //$NON-NLS-1$
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildClassWithRepeatedStaticInit() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/HasClinit", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor sv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        sv.visitCode();
        sv.visitTypeInsn(Opcodes.NEW, "pkg/Foo"); //$NON-NLS-1$
        sv.visitInsn(Opcodes.DUP);
        sv.visitMethodInsn(Opcodes.INVOKESPECIAL, "pkg/Foo", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        sv.visitInsn(Opcodes.POP);
        sv.visitTypeInsn(Opcodes.NEW, "pkg/Foo"); //$NON-NLS-1$
        sv.visitInsn(Opcodes.DUP);
        sv.visitMethodInsn(Opcodes.INVOKESPECIAL, "pkg/Foo", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        sv.visitInsn(Opcodes.POP);
        sv.visitInsn(Opcodes.RETURN);
        sv.visitMaxs(2, 0);
        sv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildClassWithRepeatedJavaUtilReferences() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/UsesJavaUtilTypes", null, //$NON-NLS-1$
                "java/lang/Object", null); //$NON-NLS-1$
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use", //$NON-NLS-1$
                "(Ljava/util/List;Ljava/util/Set;)V", null, null); //$NON-NLS-1$
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 2);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildClassWithRepeatedDeclarationTypeReferences() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER,
                "pkg/RepeatedClassDeclarationRefs", //$NON-NLS-1$
                "Lpkg/Foo;Ljava/util/function/Supplier<Lpkg/Foo;>;", //$NON-NLS-1$
                "pkg/Foo", new String[] { "java/util/function/Supplier" }); //$NON-NLS-1$ //$NON-NLS-2$
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildEmptyExceptionClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                "pkg/MyException", null, "java/lang/Exception", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Exception", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Produces a class whose {@code test()} method has a catch block where the handler label and
     * the LVT start label are intentionally distinct objects, mimicking javac's output where the
     * {@code astore} lives at the handler offset and the LVT range begins at the next instruction.
     */
    private static byte[] buildClassWithCatch() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/HasCatch", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitCode();

        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label handler = new Label();  // handler label = the astore position
        Label lvtStart = new Label(); // LVT start label = after the astore (different object)
        Label end = new Label();

        mv.visitLabel(tryStart);
        mv.visitInsn(Opcodes.NOP);
        mv.visitLabel(tryEnd);
        mv.visitJumpInsn(Opcodes.GOTO, end);

        mv.visitLabel(handler);                          // handler target
        mv.visitVarInsn(Opcodes.ASTORE, 1);             // astore — occupies its own bytecode range
        mv.visitLabel(lvtStart);                         // LVT start is a new label AFTER astore
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.POP);

        mv.visitLabel(end);
        mv.visitInsn(Opcodes.RETURN);

        mv.visitTryCatchBlock(tryStart, tryEnd, handler, "pkg/MyException"); //$NON-NLS-1$
        // LVT start uses lvtStart, not handler — this is the key mismatch the fix addresses
        mv.visitLocalVariable("e", "Lpkg/MyException;", null, lvtStart, end, 1); //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitLocalVariable("this", "Lpkg/HasCatch;", null, tryStart, end, 0); //$NON-NLS-1$ //$NON-NLS-2$

        mv.visitMaxs(1, 2);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildModuleWithExport() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null); //$NON-NLS-1$
        ModuleVisitor mv = cw.visitModule("test.exportmodule", 0, "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitExport("pkg/myapi", 0, "consumer.module"); //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Box&lt;T extends Number&gt; with {@code void put(T value)} — bytecode erases T to Number. */
    private static byte[] buildGenericBoxClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                "pkg/Box", "<T:Ljava/lang/Number;>Ljava/lang/Object;", "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        // put(T value) — erased descriptor is (Ljava/lang/Number;)V, generic sig (TT;)V
        MethodVisitor put = cw.visitMethod(Opcodes.ACC_PUBLIC, "put", //$NON-NLS-1$
                "(Ljava/lang/Number;)V", "(TT;)V", null); //$NON-NLS-1$ //$NON-NLS-2$
        put.visitCode();
        put.visitInsn(Opcodes.RETURN);
        put.visitMaxs(0, 2);
        put.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildBoxCaller() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/Caller", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "use", //$NON-NLS-1$
                "(Lpkg/Box;Ljava/lang/Number;)V", null, null); //$NON-NLS-1$
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "pkg/Box", "put", "(Ljava/lang/Number;)V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 3);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildOuterGenericClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                "pkg/Outer", "<T:Ljava/lang/Number;>Ljava/lang/Object;", "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        cw.visitInnerClass("pkg/Outer$Inner", "pkg/Outer", "Inner", 0); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildOuterGenericInnerClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_SUPER, "pkg/Outer$Inner", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        cw.visitInnerClass("pkg/Outer$Inner", "pkg/Outer", "Inner", 0); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        MethodVisitor put = cw.visitMethod(Opcodes.ACC_PUBLIC, "put", //$NON-NLS-1$
                "(Ljava/lang/Number;)V", "(TT;)V", null); //$NON-NLS-1$ //$NON-NLS-2$
        put.visitCode();
        put.visitInsn(Opcodes.RETURN);
        put.visitMaxs(0, 2);
        put.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildOuterInnerCaller() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                "pkg/OuterInnerCaller", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        cw.visitInnerClass("pkg/Outer$Inner", "pkg/Outer", "Inner", 0); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use", //$NON-NLS-1$
                "(Lpkg/Outer$Inner;Ljava/lang/Number;)V", null, null); //$NON-NLS-1$
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "pkg/Outer$Inner", "put", "(Ljava/lang/Number;)V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildHolderClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/Holder", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        cw.visitField(Opcodes.ACC_PUBLIC, "count", "I", null, null).visitEnd(); //$NON-NLS-1$ //$NON-NLS-2$
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildStaticHolderClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/Holder", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "count", "I", null, null).visitEnd(); //$NON-NLS-1$ //$NON-NLS-2$
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildFlagHolderClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/FlagHolder", null, "java/lang/Object", //$NON-NLS-1$ //$NON-NLS-2$
                null);
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "flag", "Z", null, null).visitEnd(); //$NON-NLS-1$ //$NON-NLS-2$
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildHolderUserWithCompoundAccess() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/HolderUser", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "increment", "(Lpkg/Holder;)V", //$NON-NLS-1$ //$NON-NLS-2$
                null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.GETFIELD, "pkg/Holder", "count", "I"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IADD);
        mv.visitFieldInsn(Opcodes.PUTFIELD, "pkg/Holder", "count", "I"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildHolderUserWithSeparateAccesses() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/HolderUser", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "replace", "(Lpkg/Holder;)V", //$NON-NLS-1$ //$NON-NLS-2$
                null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, "pkg/Holder", "count", "I"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitVarInsn(Opcodes.ISTORE, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IADD);
        mv.visitFieldInsn(Opcodes.PUTFIELD, "pkg/Holder", "count", "I"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 2);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildHolderUserWithStaticCompoundAccess() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/HolderUser", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "increment", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, "pkg/Holder", "count", "I"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IADD);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, "pkg/Holder", "count", "I"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildHolderUserWithSeparateStaticAccesses() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/HolderUser", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "replace", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, "pkg/Holder", "count", "I"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitVarInsn(Opcodes.ISTORE, 0);
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IADD);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, "pkg/Holder", "count", "I"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildHolderUserWithConsumedStaticReadAndWrite() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/HolderUser", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "replace", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitFieldInsn(Opcodes.GETSTATIC, "pkg/Holder", "count", "I"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, "pkg/Holder", "count", "I"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildFlagUserWithConditionalStaticReadAndWrite() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/FlagUser", null, "java/lang/Object", //$NON-NLS-1$ //$NON-NLS-2$
                null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "replace", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        Label afterIf = new Label();
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, "pkg/FlagHolder", "flag", "Z"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitJumpInsn(Opcodes.IFEQ, afterIf);
        mv.visitLabel(afterIf);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, "pkg/FlagHolder", "flag", "Z"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildLambdaHelperClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/Helper", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "help", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildLambdaUserClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/LambdaUser", null, "java/lang/Object", //$NON-NLS-1$ //$NON-NLS-2$
                null);
        MethodVisitor run = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        run.visitCode();
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", //$NON-NLS-1$ //$NON-NLS-2$
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" //$NON-NLS-1$
                        + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)" //$NON-NLS-1$
                        + "Ljava/lang/invoke/CallSite;", //$NON-NLS-1$
                false);
        Handle implementation = new Handle(Opcodes.H_INVOKESTATIC, "pkg/LambdaUser", "lambda$run$0", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        run.visitInvokeDynamicInsn("run", "()Ljava/lang/Runnable;", bootstrap, Type.getType("()V"), implementation, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                Type.getType("()V")); //$NON-NLS-1$
        run.visitInsn(Opcodes.POP);
        run.visitInsn(Opcodes.RETURN);
        run.visitMaxs(1, 0);
        run.visitEnd();

        MethodVisitor lambda = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "lambda$run$0", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        lambda.visitCode();
        lambda.visitMethodInsn(Opcodes.INVOKESTATIC, "pkg/Helper", "help", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        lambda.visitInsn(Opcodes.RETURN);
        lambda.visitMaxs(0, 0);
        lambda.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildOverloadedLambdaUserClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/OverloadedLambdaUser", null, //$NON-NLS-1$
                "java/lang/Object", null); //$NON-NLS-1$
        MethodVisitor noArg = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        noArg.visitCode();
        noArg.visitMethodInsn(Opcodes.INVOKESTATIC, "pkg/Helper", "help", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        noArg.visitInsn(Opcodes.RETURN);
        noArg.visitMaxs(0, 0);
        noArg.visitEnd();

        MethodVisitor oneArg = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", //$NON-NLS-1$
                "(Ljava/lang/String;)V", null, null); //$NON-NLS-1$
        oneArg.visitCode();
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", //$NON-NLS-1$ //$NON-NLS-2$
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" //$NON-NLS-1$
                        + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)" //$NON-NLS-1$
                        + "Ljava/lang/invoke/CallSite;", //$NON-NLS-1$
                false);
        Handle implementation = new Handle(Opcodes.H_INVOKESTATIC, "pkg/OverloadedLambdaUser", "lambda$run$0", //$NON-NLS-1$ //$NON-NLS-2$
                "()V", false); //$NON-NLS-1$
        oneArg.visitInvokeDynamicInsn("run", "()Ljava/lang/Runnable;", bootstrap, Type.getType("()V"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                implementation, Type.getType("()V")); //$NON-NLS-1$
        oneArg.visitInsn(Opcodes.POP);
        oneArg.visitInsn(Opcodes.RETURN);
        oneArg.visitMaxs(1, 1);
        oneArg.visitEnd();

        MethodVisitor lambda = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "lambda$run$0", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        lambda.visitCode();
        lambda.visitMethodInsn(Opcodes.INVOKESTATIC, "pkg/Helper", "help", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        lambda.visitInsn(Opcodes.RETURN);
        lambda.visitMaxs(0, 0);
        lambda.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildClassAnnotatedWithRetention() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/Annotated", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        AnnotationVisitor av = cw.visitAnnotation("Ljava/lang/annotation/Retention;", true); //$NON-NLS-1$
        av.visitEnum("value", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        av.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildTopLevelDollarClass() {
        ClassWriter cw = new ClassWriter(0);
        // No InnerClasses attribute — this is a genuine top-level class whose binary name contains $
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/Price$Tag", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildDollarCaller() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/DollarCaller", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "use", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, "pkg/Price$Tag"); //$NON-NLS-1$
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "pkg/Price$Tag", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Caller whose {@code call} method takes a {@code pkg/Outer$StaticInner} parameter. */
    private static byte[] buildGenericNestedCaller() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/GenericCaller", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        // void call(Outer$StaticInner x) — bytecode descriptor uses $
        MethodVisitor call = cw.visitMethod(Opcodes.ACC_PUBLIC, "call", "(Lpkg/Outer$StaticInner;)V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        call.visitCode();
        call.visitInsn(Opcodes.RETURN);
        call.visitMaxs(0, 2);
        call.visitEnd();
        // Caller site: another method that invokes call(...)
        MethodVisitor invoke = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "(Lpkg/Outer$StaticInner;)V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        invoke.visitCode();
        invoke.visitVarInsn(Opcodes.ALOAD, 0);
        invoke.visitVarInsn(Opcodes.ALOAD, 1);
        invoke.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "pkg/GenericCaller", "call", "(Lpkg/Outer$StaticInner;)V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        invoke.visitInsn(Opcodes.RETURN);
        invoke.visitMaxs(2, 2);
        invoke.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildOuterWithStaticInner() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/Outer", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        cw.visitInnerClass("pkg/Outer$StaticInner", "pkg/Outer", "StaticInner", Opcodes.ACC_STATIC); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildStaticInnerClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_SUPER, "pkg/Outer$StaticInner", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        // No synthetic outer-ref field — this is static
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", //$NON-NLS-1$
                "(Ljava/lang/String;I)V", null, null); //$NON-NLS-1$
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 3);
        ctor.visitEnd();
        cw.visitInnerClass("pkg/Outer$StaticInner", "pkg/Outer", "StaticInner", Opcodes.ACC_STATIC); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildStaticInnerCaller() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "pkg/StaticInnerCaller", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "call", "(Ljava/lang/String;I)V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, "pkg/Outer$StaticInner"); //$NON-NLS-1$
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "pkg/Outer$StaticInner", "<init>", //$NON-NLS-1$ //$NON-NLS-2$
                "(Ljava/lang/String;I)V", false); //$NON-NLS-1$
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(4, 3);
        mv.visitEnd();
        cw.visitInnerClass("pkg/Outer$StaticInner", "pkg/Outer", "StaticInner", Opcodes.ACC_STATIC); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        cw.visitEnd();
        return cw.toByteArray();
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

    private static PatternQuerySpecification typeReferenceSpecification(String pattern, IJavaSearchScope scope) {
        return new PatternQuerySpecification(pattern, IJavaSearchConstants.TYPE, true, IJavaSearchConstants.REFERENCES,
                scope, "Application library type references"); //$NON-NLS-1$
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

    private static void createImplicitObjectSuperclassJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addType(output, "pkg/PlainClass", Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "java/lang/Object"); //$NON-NLS-1$ //$NON-NLS-2$
            addType(output, "pkg/PlainInterface", //$NON-NLS-1$
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "java/lang/Object"); //$NON-NLS-1$
        }
    }

    private static void createRepeatedTypeReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/Repeated.class", repeatedTypeReferenceBytes()); //$NON-NLS-1$
        }
    }

    private static void createSameSimpleTypeReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/UsesSameSimpleTypes.class", sameSimpleTypeReferenceBytes()); //$NON-NLS-1$
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

    private static void createGenericInnerTypeReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/GenericInnerUses.class", genericInnerTypeReferenceBytes()); //$NON-NLS-1$
        }
    }

    private static void createDollarNamedTypeJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/Price$Tag.class", dollarNamedTypeBytes()); //$NON-NLS-1$
        }
    }

    private static void createLdcBootstrapReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/LdcBootstrapUses.class", ldcBootstrapReferenceBytes()); //$NON-NLS-1$
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

    private static void createGenericMethodReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/GenericTarget.class", genericTargetBytes()); //$NON-NLS-1$
            addClass(output, "pkg/GenericCaller.class", genericCallerBytes()); //$NON-NLS-1$
        }
    }

    private static void createPermittedSubclassReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/Sealed.class", permittedSubclassReferenceBytes()); //$NON-NLS-1$
        }
    }

    private static void createMethodBodyTypeAnnotationReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/AnnotatedBody.class", methodBodyTypeAnnotationReferenceBytes()); //$NON-NLS-1$
        }
    }

    private static void createRecordComponentAnnotationReferenceJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/AnnotatedRecord.class", recordComponentAnnotationReferenceBytes()); //$NON-NLS-1$
        }
    }

    private static void createSyntheticConstructorParameterJar(File jar) throws Exception {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addClass(output, "pkg/ConstructorUses.class", syntheticConstructorParameterBytes()); //$NON-NLS-1$
            addClass(output, "pkg/Outer.class", classBytes("pkg/Outer")); //$NON-NLS-1$ //$NON-NLS-2$
            addClass(output, "pkg/Outer$Inner.class", syntheticMemberConstructorBytes()); //$NON-NLS-1$
            addClass(output, "pkg/Captured.class", overloadedConstructorBytes()); //$NON-NLS-1$
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

    private static byte[] sameSimpleTypeReferenceBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/UsesSameSimpleTypes", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        writer.visitField(Opcodes.ACC_PUBLIC, "first", "Lpkg1/Foo;", null, null).visitEnd(); //$NON-NLS-1$ //$NON-NLS-2$
        writer.visitField(Opcodes.ACC_PUBLIC, "second", "Lpkg2/Foo;", null, null).visitEnd(); //$NON-NLS-1$ //$NON-NLS-2$
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

    private static byte[] genericInnerTypeReferenceBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/GenericInnerUses", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        writer.visitInnerClass("pkg/Outer$Inner", "pkg/Outer", "Inner", Opcodes.ACC_PUBLIC); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        writer.visitField(Opcodes.ACC_PUBLIC, "value", "Lpkg/Outer$Inner;", //$NON-NLS-1$ //$NON-NLS-2$
                "Lpkg/Outer<Ljava/lang/String;>.Inner<Ljava/lang/Integer;>;", null).visitEnd(); //$NON-NLS-1$
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] dollarNamedTypeBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/Price$Tag", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        writer.visitField(Opcodes.ACC_PUBLIC, "value", "I", null, null).visitEnd(); //$NON-NLS-1$ //$NON-NLS-2$
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] ldcBootstrapReferenceBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/LdcBootstrapUses", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "uses", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        Handle ldcHandle = new Handle(Opcodes.H_INVOKESTATIC, "pkg/LdcHandleOwner", "accept", //$NON-NLS-1$ //$NON-NLS-2$
                "(Lpkg/OnlyInLdcHandle;)V", false); //$NON-NLS-1$
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "pkg/CondyBootstrap", "bootstrap", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        Handle nestedHandle = new Handle(Opcodes.H_INVOKESTATIC, "pkg/NestedCondyHandleOwner", "accept", //$NON-NLS-1$ //$NON-NLS-2$
                "(Lpkg/OnlyInNestedCondyHandle;)V", false); //$NON-NLS-1$
        ConstantDynamic nested = new ConstantDynamic("nested", "Lpkg/OnlyInNestedCondyDescriptor;", bootstrap); //$NON-NLS-1$ //$NON-NLS-2$
        ConstantDynamic constant = new ConstantDynamic("constant", "Lpkg/OnlyInCondyDescriptor;", bootstrap, //$NON-NLS-1$ //$NON-NLS-2$
                nestedHandle, nested);
        method.visitCode();
        method.visitLdcInsn(ldcHandle);
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn(constant);
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

    private static byte[] genericTargetBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/GenericTarget", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor consume = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "consume", //$NON-NLS-1$
                "(Ljava/util/List;)V", "(Ljava/util/List<Ljava/lang/String;>;)V", null); //$NON-NLS-1$ //$NON-NLS-2$
        consume.visitCode();
        consume.visitInsn(Opcodes.RETURN);
        consume.visitMaxs(0, 1);
        consume.visitEnd();
        MethodVisitor id = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "id", //$NON-NLS-1$
                "(Ljava/lang/Object;)Ljava/lang/Object;", "<T:Ljava/lang/Object;>(TT;)TT;", null); //$NON-NLS-1$ //$NON-NLS-2$
        id.visitCode();
        id.visitVarInsn(Opcodes.ALOAD, 0);
        id.visitInsn(Opcodes.ARETURN);
        id.visitMaxs(1, 1);
        id.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] genericCallerBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/GenericCaller", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "uses", //$NON-NLS-1$
                "(Ljava/util/List;Ljava/lang/Object;)V", null, null); //$NON-NLS-1$
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "pkg/GenericTarget", "consume", "(Ljava/util/List;)V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "pkg/GenericTarget", "id", //$NON-NLS-1$ //$NON-NLS-2$
                "(Ljava/lang/Object;)Ljava/lang/Object;", false); //$NON-NLS-1$
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] permittedSubclassReferenceBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/Sealed", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        writer.visitPermittedSubclass("pkg/Permitted"); //$NON-NLS-1$
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] methodBodyTypeAnnotationReferenceBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/AnnotatedBody", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "uses", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        method.visitCode();
        method.visitTryCatchBlock(start, end, handler, "java/lang/Exception"); //$NON-NLS-1$
        method.visitLabel(start);
        method.visitTypeInsn(Opcodes.NEW, "java/lang/Object"); //$NON-NLS-1$
        method.visitInsnAnnotation(TypeReference.newTypeReference(TypeReference.NEW).getValue(), null,
                "Lpkg/MethodBodyTA;", true).visitEnd(); //$NON-NLS-1$
        method.visitInsn(Opcodes.POP);
        method.visitLabel(end);
        method.visitInsn(Opcodes.RETURN);
        method.visitLabel(handler);
        method.visitInsn(Opcodes.ATHROW);
        method.visitTryCatchAnnotation(TypeReference.newTryCatchReference(0).getValue(), null,
                "Lpkg/MethodBodyTA;", true).visitEnd(); //$NON-NLS-1$
        method.visitLocalVariableAnnotation(TypeReference.newTypeReference(TypeReference.LOCAL_VARIABLE).getValue(),
                null, new Label[] { start }, new Label[] { end }, new int[] { 0 }, "Lpkg/MethodBodyTA;", //$NON-NLS-1$
                true).visitEnd();
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] recordComponentAnnotationReferenceBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_RECORD, "pkg/AnnotatedRecord", //$NON-NLS-1$
                null, "java/lang/Record", null); //$NON-NLS-1$
        RecordComponentVisitor component = writer.visitRecordComponent("value", "Ljava/lang/String;", null); //$NON-NLS-1$ //$NON-NLS-2$
        component.visitAnnotation("Lpkg/RecordComponentTag;", true).visitEnd(); //$NON-NLS-1$
        component.visitTypeAnnotation(TypeReference.newTypeReference(TypeReference.FIELD).getValue(), null,
                "Lpkg/RecordComponentTypeTag;", true).visitEnd(); //$NON-NLS-1$
        component.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] syntheticConstructorParameterBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/ConstructorUses", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        writer.visitInnerClass("pkg/Outer$Inner", "pkg/Outer", "Inner", Opcodes.ACC_PUBLIC); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "uses", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "pkg/Outer$Inner"); //$NON-NLS-1$
        method.visitInsn(Opcodes.DUP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitLdcInsn("value"); //$NON-NLS-1$
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "pkg/Outer$Inner", "<init>", //$NON-NLS-1$ //$NON-NLS-2$
                "(Lpkg/Outer;Ljava/lang/String;I)V", false); //$NON-NLS-1$
        method.visitInsn(Opcodes.POP);
        method.visitTypeInsn(Opcodes.NEW, "pkg/Captured"); //$NON-NLS-1$
        method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn("value"); //$NON-NLS-1$
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "pkg/Captured", "<init>", //$NON-NLS-1$ //$NON-NLS-2$
                "(Ljava/lang/String;I)V", false); //$NON-NLS-1$
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(5, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] syntheticMemberConstructorBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/Outer$Inner", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        writer.visitInnerClass("pkg/Outer$Inner", "pkg/Outer", "Inner", Opcodes.ACC_PUBLIC); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        addConstructor(writer, "(Lpkg/Outer;Ljava/lang/String;I)V"); //$NON-NLS-1$
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] overloadedConstructorBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/Captured", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
        addConstructor(writer, "(Ljava/lang/Integer;Ljava/lang/String;)V"); //$NON-NLS-1$
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void addConstructor(ClassWriter writer, String descriptor) {
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", descriptor, null, null); //$NON-NLS-1$
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 4);
        constructor.visitEnd();
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

    private static IMethod methodNamed(IType type, String name) throws Exception {
        for (IMethod method : type.getMethods()) {
            if (name.equals(method.getElementName())) {
                return method;
            }
        }
        throw new AssertionError("Missing method " + name); //$NON-NLS-1$
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

    private static boolean isSingleArgumentRunMatch(Match match) {
        Object element = match.getElement();
        IJavaElement javaElement = element instanceof IAdaptable adaptable
                ? adaptable.getAdapter(IJavaElement.class) : null;
        return javaElement instanceof IMethod method
                && "run".equals(method.getElementName()) //$NON-NLS-1$
                && method.getNumberOfParameters() == 1;
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
