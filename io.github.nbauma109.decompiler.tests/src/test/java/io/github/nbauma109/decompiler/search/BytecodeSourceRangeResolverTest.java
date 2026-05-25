/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Access;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;

@SuppressWarnings("restriction")
public class BytecodeSourceRangeResolverTest {

    private static final String JAVA_LANG_OBJECT = "java.lang.Object";

	private static final String PRINTF = "printf";

	private static final String LJAVA_LANG_STRING_LJAVA_LANG_OBJECT_V = "(Ljava/lang/String;[Ljava/lang/Object;)V";

	private static final String FIXTURE_PRIMS = "fixture.Prims";

	private static final String FIXTURE_BASE = "fixture.Base";

	private static final String JAVA_IO_PRINT_STREAM = "java.io.PrintStream";

	private static final String FIELD = "field";

	private static final String FIXTURE_OWNER = "fixture.Owner";

	private static final String STATIC_TARGET = "staticTarget";

	private static final String INHERITED = "inherited";

	private static final String OWNER = "Owner";

	private static final String TARGET = "target";

	private static final String PACKAGE_NAME = "fixture"; //$NON-NLS-1$

    private static final String SOURCE = """
            package fixture;

            @interface Marker {
                String value();
            }

            enum Mode {
                ON;
                String text() { return name(); }
            }

            record Rec(int value) {
                int doubled() { return value * 2; }
            }

            class Base {
                Base() {}
                String inherited() { return "base"; }
            }

            class Owner extends Base {
                int field;

                Owner() {
                    this(1);
                }

                Owner(int value) {
                    super();
                    this.field = value;
                }

                void takes(int count, java.lang.String... names) {
                    field = count;
                    int local = field;
                    System.out.println(names.length);
                    System.out.printf("%d %s", count, names.length);
                    System.out.printf("%d");
                    super.inherited();
                    Runnable first = this::target;
                    Runnable second = Owner::staticTarget;
                    java.util.function.Supplier<Owner> third = Owner::new;
                    java.util.function.Supplier<String> fourth = super::inherited;
                    new Owner(count);
                }

                void target() {}
                static void staticTarget() {}
            }

            class Prims {
                void allPrims(boolean z, byte b, char c, double d, float f, long j, short s) {
                    target();
                }
                void strMethod(java.lang.String s) {
                    target();
                }
                void intMethod(String s) {
                    target();
                }
                void target() {}
            }
            """;

    private IProject project;
    private IType markerType;
    private IType modeType;
    private IType recType;
    private IType ownerType;
    private IType primsType;
    private IMethod takesMethod;
    private IMethod defaultConstructor;
    private IMethod intConstructor;
    private IMethod targetMethod;

    @Before
    public void setUp() throws CoreException {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject("bytecode-source-range-resolver-test-project"); //$NON-NLS-1$
        if (project.exists()) {
            project.delete(true, true, new NullProgressMonitor());
        }
        project.create(new NullProgressMonitor());
        project.open(new NullProgressMonitor());

        IProjectDescription description = project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, new NullProgressMonitor());

        IFolder sourceFolder = project.getFolder("src"); //$NON-NLS-1$
        sourceFolder.create(true, true, new NullProgressMonitor());

        IJavaProject javaProject = JavaCore.create(project);
        IClasspathEntry sourceEntry = JavaCore.newSourceEntry(sourceFolder.getFullPath());
        IClasspathEntry jreEntry = JavaRuntime.getDefaultJREContainerEntry();
        javaProject.setRawClasspath(new IClasspathEntry[] { sourceEntry, jreEntry },
                project.getFullPath().append("bin"), new NullProgressMonitor()); //$NON-NLS-1$

        IPackageFragmentRoot sourceRoot = javaProject.getPackageFragmentRoot(sourceFolder);
        IPackageFragment pkg = sourceRoot.createPackageFragment(PACKAGE_NAME, true, new NullProgressMonitor());
        ICompilationUnit unit = pkg.createCompilationUnit("Owner.java", SOURCE, true, new NullProgressMonitor()); //$NON-NLS-1$

        markerType = unit.getType("Marker"); //$NON-NLS-1$
        modeType = unit.getType("Mode"); //$NON-NLS-1$
        recType = unit.getType("Rec"); //$NON-NLS-1$
        ownerType = unit.getType(OWNER); //$NON-NLS-1$
        primsType = unit.getType("Prims"); //$NON-NLS-1$
        takesMethod = method(ownerType, "takes", 2); //$NON-NLS-1$
        defaultConstructor = constructor(ownerType, 0);
        intConstructor = constructor(ownerType, 1);
        targetMethod = method(ownerType, TARGET, 0); //$NON-NLS-1$
    }

    @After
    public void tearDown() throws CoreException {
        if (project != null && project.exists()) {
            project.delete(true, true, new NullProgressMonitor());
        }
    }

    @Test
    public void declarationsAndFallbacksUseJdtSourceRanges() {
        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();

        assertRangeText(resolver.rangeFor(declaration(Kind.METHOD, targetMethod, TARGET)), TARGET); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.METHOD, takesMethod, "missing", "missing", null, null), ""), //$NON-NLS-1$ //$NON-NLS-2$
                "void ta"); //$NON-NLS-1$
    }

    @Test
    public void methodReferencesResolvePreciseRanges() {
        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();

        assertRangeStartsWith(resolver.rangeFor(reference(Kind.METHOD, takesMethod, "println", "println", //$NON-NLS-1$ //$NON-NLS-2$
                JAVA_IO_PRINT_STREAM, "(I)V"), SOURCE), "println(names.length)"); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeStartsWith(resolver.rangeFor(reference(Kind.METHOD, takesMethod, INHERITED, INHERITED, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_BASE, "()Ljava/lang/String;"), SOURCE), "inherited()"); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.METHOD, takesMethod, TARGET, TARGET, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "()V"), SOURCE), TARGET); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.METHOD, takesMethod, STATIC_TARGET, STATIC_TARGET, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "()V"), SOURCE), STATIC_TARGET); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.METHOD, takesMethod, INHERITED, INHERITED, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_BASE, null), SOURCE), INHERITED + "()"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void varargMethodReferencesAreMatchedDespiteExpandedArgumentCount() {
        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();

        // printf(String, Object...) has 2 descriptor params but the source call
        // System.out.printf("%d %s", count, names.length) has 3 AST arguments.
        // matchesArgumentCount must accept the varargs expansion and resolve the call site.
        assertRangeText(resolver.rangeFor(reference(Kind.METHOD, takesMethod, PRINTF, PRINTF, //$NON-NLS-1$ //$NON-NLS-2$
                JAVA_IO_PRINT_STREAM, LJAVA_LANG_STRING_LJAVA_LANG_OBJECT_V), SOURCE), //$NON-NLS-1$
                "printf(\"%d %s\", count, names.length)"); //$NON-NLS-1$
    }

    @Test
    public void zeroArgVarargCallIsAlsoMatched() {
        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();

        // Two printf call sites in takes(): expanded (3 args) at ordinal 0, zero-varargs (1 arg) at ordinal 1.
        // matchesArgumentCount must accept 1 arg for printf(String, Object...) whose descriptor has 2 formal
        // params, because argumentCount (1) >= argTypes.length - 1 (2-1=1).
        BytecodeSearchEntry e1 = reference(Kind.METHOD, takesMethod, PRINTF, PRINTF, //$NON-NLS-1$ //$NON-NLS-2$
                JAVA_IO_PRINT_STREAM, LJAVA_LANG_STRING_LJAVA_LANG_OBJECT_V); //$NON-NLS-1$
        BytecodeSearchEntry e2 = reference(Kind.METHOD, takesMethod, PRINTF, PRINTF, //$NON-NLS-1$ //$NON-NLS-2$
                JAVA_IO_PRINT_STREAM, LJAVA_LANG_STRING_LJAVA_LANG_OBJECT_V); //$NON-NLS-1$
        Map<BytecodeSearchEntry, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(e1, e2), SOURCE);
        assertRangeText(ranges.get(e1), "printf(\"%d %s\", count, names.length)"); //$NON-NLS-1$
        assertRangeText(ranges.get(e2), "printf(\"%d\")"); //$NON-NLS-1$
    }

    @Test
    public void constructorReferencesResolveKeywordsAndTypeNames() {
        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();

        assertRangeText(resolver.rangeFor(reference(Kind.CONSTRUCTOR, defaultConstructor, OWNER, FIXTURE_OWNER, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "(I)V"), SOURCE), "this"); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.CONSTRUCTOR, intConstructor, "Object", JAVA_LANG_OBJECT, //$NON-NLS-1$ //$NON-NLS-2$
                JAVA_LANG_OBJECT, "()V"), SOURCE), "super"); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.CONSTRUCTOR, takesMethod, OWNER, FIXTURE_OWNER, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "()V"), SOURCE), OWNER); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.CONSTRUCTOR, takesMethod, OWNER, FIXTURE_OWNER, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "(I)V"), SOURCE), OWNER); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void fieldReferencesResolveByOwnerAndSkipDeclarations() {
        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();

        assertRangeText(resolver.rangeFor(reference(Kind.FIELD, takesMethod, FIELD, FIELD, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "I", Access.WRITE), SOURCE), FIELD); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.FIELD, takesMethod, "out", "out", //$NON-NLS-1$ //$NON-NLS-2$
                "java.lang.System", "Ljava/io/PrintStream;", Access.READ), SOURCE), "out"); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeStartsWith(resolver.rangeFor(reference(Kind.FIELD, takesMethod, "local", "local", //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "I", Access.READ), SOURCE), "void "); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Regression test for the inherited-field fix in {@code matchesFieldAccess}.
     * <p>
     * Before the fix, an unqualified field reference in a subclass method was tested
     * with {@code matchesFieldOwner(enclosingTypeName(element))}, where
     * {@code enclosingTypeName} returns the subclass name.  When the field is declared
     * on a superclass, the names differ and the reference is rejected — causing the
     * resolver to fall back to a broad enclosing-method range instead of the precise
     * identifier site.
     * <p>
     * After the fix, {@code matchesFieldOwner(null)} is used for unqualified accesses,
     * which short-circuits to {@code true} because ownership cannot be inferred from
     * source syntax alone.
     */
    @Test
    public void inheritedFieldReferenceIsResolvedInSubclassMethod() {
        // Custom source: Base declares "x"; Owner (subclass) reads it unqualified.
        String src = """
                package fixture;
                class Base {
                    int x;
                }
                class Owner extends Base {
                    void target() {
                        x = 1;
                    }
                }
                """;

        // targetMethod is void target() in Owner (0 parameters).
        // The bytecode entry says the field is declared on "fixture.Base".
        BytecodeSearchEntry entry = reference(Kind.FIELD, targetMethod,
                "x", "x", FIXTURE_BASE, "I", Access.WRITE); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();
        Map<BytecodeSearchEntry, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(entry), src);

        BytecodeSourceRangeResolver.SourceRange range = ranges.get(entry);
        assertNotNull("range must not be null", range); //$NON-NLS-1$
        // The resolver must find the "x" identifier inside target(), not fall back to
        // a broad enclosing-method range.
        assertEquals("x", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    /**
     * Regression test for the {@code VariableDeclarationFragment} parent-type guard
     * added to {@code AstDeclarationWindow.visit(VariableDeclarationFragment)}.
     * <p>
     * When {@code AstDeclarationWindow} looks for the declaration of {@code IField "field"},
     * it must skip {@code VariableDeclarationFragment} nodes whose parent is <em>not</em> a
     * {@code FieldDeclaration} (e.g. local-variable statements).  Without the guard the local
     * {@code int field = 0;} inside {@code before()} — which appears first in the class — would
     * be mistaken for the field declaration, confining the search window to the body of
     * {@code before()} and making the {@code hashCode()} call in the actual field initializer
     * invisible to the reference resolver.
     */
    @Test
    public void fieldDeclarationWindowIsNotConfusedByLocalVariableWithSameName() {
        // Source: method "before" comes before the field "field" and declares a local
        // variable also named "field".  The field initializer calls hashCode().
        String src = """
                package fixture;
                class Owner {
                    void before() {
                        int field = 0;
                    }
                    int field = hashCode();
                }
                """;

        IField fieldEl = ownerType.getField(FIELD); // IField "field" from the test project — declaring type "Owner"
        BytecodeSearchEntry entry = reference(Kind.METHOD, fieldEl,
                "hashCode", "hashCode", JAVA_LANG_OBJECT, "()I"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();
        Map<BytecodeSearchEntry, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(entry), src);

        BytecodeSourceRangeResolver.SourceRange range = ranges.get(entry);
        assertNotNull("range must not be null", range); //$NON-NLS-1$
        // AstDeclarationWindow must use "int field = hashCode();" (the FieldDeclaration)
        // as the search window — not "int field = 0;" (the local-variable statement in
        // before()) — so that the hashCode() call site in the field initializer is found.
        assertEquals("hashCode()", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void typeDeclarationNamesAreNotReportedAsReferences() {
        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();

        // Owner has internal uses (Owner::staticTarget, Owner::new, new Owner(...)) — the first use is returned,
        // confirming the type declaration name itself is filtered out and a reference site is found instead.
        assertRangeText(resolver.rangeFor(reference(Kind.TYPE, ownerType, OWNER, FIXTURE_OWNER, null, null), SOURCE), //$NON-NLS-1$ //$NON-NLS-2$
                OWNER); //$NON-NLS-1$
        // Rec / Mode / Marker have no internal uses of their own name → enclosing fallback,
        // clamped to entry name length (3 / 4 / 6 / 5 chars respectively).
        assertRangeStartsWith(resolver.rangeFor(reference(Kind.TYPE, recType, "Rec", "fixture.Rec", null, null), SOURCE), //$NON-NLS-1$ //$NON-NLS-2$
                "rec"); //$NON-NLS-1$
        assertRangeStartsWith(resolver.rangeFor(reference(Kind.TYPE, modeType, "Mode", "fixture.Mode", null, null), SOURCE), //$NON-NLS-1$ //$NON-NLS-2$
                "enum"); //$NON-NLS-1$
        assertRangeStartsWith(resolver.rangeFor(reference(Kind.FIELD, modeType.getField("ON"), "ON", "ON", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.Mode", null, Access.READ), SOURCE), "ON"); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeStartsWith(resolver.rangeFor(reference(Kind.TYPE, markerType, "Marker", "fixture.Marker", null, null), SOURCE), //$NON-NLS-1$ //$NON-NLS-2$
                "@inter"); //$NON-NLS-1$
        assertRangeStartsWith(resolver.rangeFor(reference(Kind.TYPE, markerType, "value", "value", null, null), SOURCE), //$NON-NLS-1$ //$NON-NLS-2$
                "@inte"); //$NON-NLS-1$
    }

    /**
     * Exercises the {@code StringUtils.isBlank(source) → return null} branch inside
     * {@code BytecodeSourceRangeResolver.parse(IClassFile)}.
     * <p>
     * When {@code rangeFor(entry)} is called without a source string and the entry's
     * element lives inside a class file that has no source attachment (here a JRE
     * class), {@code classFile.getSource()} returns null, the blank-source guard fires,
     * and the resolver falls back to an enclosing range.
     * @throws JavaModelException 
     */
    @Test
    public void parseReturnsNullAndFallsBackForClassFileWithNoSourceAttachment() throws JavaModelException {
        // The test project already has the default JRE on its classpath (set up in setUp()).
        // java.lang.String lives in a JRE JAR that has no source attachment in the test
        // environment, so classFile.getSource() returns null → isBlank(null) == true.
        IJavaProject javaProject = JavaCore.create(project);
        IType stringType = javaProject.findType("java.lang.String"); //$NON-NLS-1$
        assertNotNull("java.lang.String must be resolvable via JRE container", stringType); //$NON-NLS-1$

        IMethod[] methods = stringType.getMethods();
        assertTrue("java.lang.String must expose at least one method", methods.length > 0); //$NON-NLS-1$
        IMethod jreMethod = methods[0];

        // Verify the method lives inside a class file so the parse(IClassFile) path is taken
        assertNotNull("String method must have an IClassFile ancestor", //$NON-NLS-1$
                jreMethod.getAncestor(IJavaElement.CLASS_FILE));

        BytecodeSearchEntry entry = new BytecodeSearchEntry(
                Kind.METHOD, false,
                BytecodeSearchEntry.elementReference(jreMethod.getHandleIdentifier(), null),
                BytecodeSearchEntry.symbolReference(jreMethod.getElementName(),
                        jreMethod.getElementName(), "java.lang.String", null)); //$NON-NLS-1$

        // rangeFor(entry) → rangeFor(entry, null) → parsedClassFile(element)
        //   → parse(classFile) → isBlank(null) → return null  (covered branch)
        //   → fallback enclosing range is returned instead of null
        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();
        BytecodeSourceRangeResolver.SourceRange range = resolver.rangeFor(entry);
        assertNotNull("resolver must return a non-null fallback range when classFile has no source", range); //$NON-NLS-1$
    }

    /**
     * Exercises the {@code primitiveName} switch for every primitive descriptor except {@code I}
     * ({@code Z} boolean, {@code B} byte, {@code C} char, {@code D} double, {@code F} float,
     * {@code J} long, {@code S} short).
     * <p>
     * {@code allPrims} declares all seven remaining primitive parameters; matching its
     * {@code IMethod} against the fixture source forces {@code normalizeJdtType} to call
     * {@code primitiveName} for each descriptor letter.
     */
    @Test
    public void allPrimitivesAreNormalizedInParameterTypes() throws JavaModelException {
        IMethod allPrimsMethod = method(primsType, "allPrims", 7); //$NON-NLS-1$
        BytecodeSearchEntry entry = reference(Kind.METHOD, allPrimsMethod,
                TARGET, TARGET, FIXTURE_PRIMS, "()V"); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();
        Map<BytecodeSearchEntry, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(entry), SOURCE);

        BytecodeSourceRangeResolver.SourceRange range = ranges.get(entry);
        assertNotNull("range must not be null", range); //$NON-NLS-1$
        assertRangeStartsWith(range, TARGET);
    }

    /**
     * Exercises two uncovered branches of {@code sameType} in a single run:
     * <ol>
     *   <li>{@code leftQualified && rightQualified → return false}: {@code strMethod} in the
     *       fixture source declares {@code java.lang.Object o} (right = {@code "java.lang.object"},
     *       qualified) but the JDT element has {@code java.lang.String s} (left =
     *       {@code "java.lang.string"}, qualified) — both qualified and different, so
     *       {@code sameType} immediately returns {@code false}.</li>
     *   <li>{@code leftSimple} branch: the second overload {@code strMethod(String s)} produces
     *       right = {@code "string"} (unqualified); left is still {@code "java.lang.string"}
     *       (qualified), so {@code leftSimple = substringAfterLast(left, ".") = "string"} is
     *       computed and matched successfully.</li>
     * </ol>
     */
    @Test
    public void sameTypeQualificationBranchesAreCoveredByOverloadedMethods() throws JavaModelException {
        // strMethod(java.lang.String s) in SOURCE → JDT gives "Qjava.lang.String;" → left = "java.lang.string" (qualified)
        IMethod strMethodElement = method(primsType, "strMethod", 1); //$NON-NLS-1$
        BytecodeSearchEntry entry = reference(Kind.METHOD, strMethodElement,
                TARGET, TARGET, FIXTURE_PRIMS, "()V"); //$NON-NLS-1$ //$NON-NLS-2$

        // Custom source: wrong overload has java.lang.Object (both qualified, different → false)
        // correct overload has String (unqualified) so the leftSimple branch resolves the match.
        String customSrc = """
                package fixture;
                class Prims {
                    void strMethod(java.lang.Object o) {}
                    void strMethod(String s) { target(); }
                    void target() {}
                }
                """; //$NON-NLS-1$

        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();
        Map<BytecodeSearchEntry, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(entry), customSrc);

        BytecodeSourceRangeResolver.SourceRange range = ranges.get(entry);
        assertNotNull("range must not be null", range); //$NON-NLS-1$
        assertTrue("range text must start with 'target'", //$NON-NLS-1$
                customSrc.substring(range.offset(), range.offset() + range.length()).startsWith(TARGET));
    }

    /**
     * Exercises the {@code rightSimple} branch of {@code sameType}.
     * <p>
     * {@code intMethod(String s)} in SOURCE gives JDT parameter type {@code "QString;"} which
     * normalizes to {@code "string"} (unqualified, left).  The custom fixture source declares
     * {@code intMethod(java.lang.String s)}, so the AST normalizes to {@code "java.lang.string"}
     * (qualified, right).  {@code sameType("string", "java.lang.string")} reaches the branch
     * where {@code rightQualified = true} and computes
     * {@code rightSimple = substringAfterLast(right, ".") = "string"} — which matches left.
     */
    @Test
    public void sameTypeRightSimpleBranchIsCoveredByQualifiedAstVsSimpleJdtType() throws JavaModelException {
        // intMethod(String s) in SOURCE → JDT gives "QString;" → left = "string" (unqualified)
        IMethod intMethodElement = method(primsType, "intMethod", 1); //$NON-NLS-1$
        BytecodeSearchEntry entry = reference(Kind.METHOD, intMethodElement,
                TARGET, TARGET, FIXTURE_PRIMS, "()V"); //$NON-NLS-1$ //$NON-NLS-2$

        // Custom source uses the fully-qualified java.lang.String so AST right is "java.lang.string" (qualified).
        String customSrc = """
                package fixture;
                class Prims {
                    void intMethod(java.lang.String s) { target(); }
                    void target() {}
                }
                """; //$NON-NLS-1$

        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();
        Map<BytecodeSearchEntry, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(entry), customSrc);

        BytecodeSourceRangeResolver.SourceRange range = ranges.get(entry);
        assertNotNull("range must not be null", range); //$NON-NLS-1$
        assertTrue("range text must start with 'target'", //$NON-NLS-1$
                customSrc.substring(range.offset(), range.offset() + range.length()).startsWith(TARGET));
    }

    private static BytecodeSearchEntry declaration(Kind kind, IJavaElement element, String name) {
        return new BytecodeSearchEntry(kind, true, element, name, name, null, null);
    }

    private static BytecodeSearchEntry reference(Kind kind, IJavaElement element, String name, String qualifiedName,
            String declaringTypeName, String descriptor) {
        return reference(kind, element, name, qualifiedName, declaringTypeName, descriptor, Access.NONE);
    }

    private static BytecodeSearchEntry reference(Kind kind, IJavaElement element, String name, String qualifiedName,
            String declaringTypeName, String descriptor, Access access) {
        String handle = element == null ? null : element.getHandleIdentifier();
        return new BytecodeSearchEntry(kind, false, BytecodeSearchEntry.elementReference(handle, null),
                BytecodeSearchEntry.symbolReference(name, qualifiedName, declaringTypeName, descriptor), access);
    }

    private static IMethod method(IType type, String name, int parameterCount) throws JavaModelException {
        for (IMethod method : type.getMethods()) {
            if (!method.isConstructor() && name.equals(method.getElementName())
                    && method.getNumberOfParameters() == parameterCount) {
                return method;
            }
        }
        throw new AssertionError("Missing method " + name); //$NON-NLS-1$
    }

    private static IMethod constructor(IType type, int parameterCount) throws JavaModelException {
        for (IMethod method : type.getMethods()) {
            if (method.isConstructor() && method.getNumberOfParameters() == parameterCount) {
                return method;
            }
        }
        throw new AssertionError("Missing constructor with " + parameterCount + " parameters"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static void assertRangeText(BytecodeSourceRangeResolver.SourceRange range, String expected) {
        assertEquals(expected, SOURCE.substring(range.offset(), range.offset() + range.length()));
    }

    private static void assertRangeStartsWith(BytecodeSourceRangeResolver.SourceRange range, String expectedPrefix) {
        assertTrue(SOURCE.substring(range.offset(), range.offset() + range.length()).startsWith(expectedPrefix));
    }
}
