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

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IClassFile;
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
import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport;
import io.github.nbauma109.decompiler.testutil.PrintlnFixtureJarBuilder;

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

                class Inner {
                    Inner(int value) {}
                    Inner(String... values) {}
                }

                Owner() {
                    this(1);
                }

                Owner(int value) {
                    super();
                    this.field = value;
                }

                Owner(int value, String name) {
                    this(value);
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
                    new Owner(count, names[0]);
                }

                void target() {}
                static void staticTarget() {}
                void printLines(java.io.PrintWriter out, java.io.PrintWriter err) {}
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
                static void arrayOnly(Object[] values) {}
                void target() {}
            }
            """;

    private IProject project;
    private IType markerType;
    private IType modeType;
    private IType recType;
    private IType ownerType;
    private IType primsType;
    private IPackageFragment packageFragment;
    private IMethod takesMethod;
    private IMethod printLinesMethod;
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
        packageFragment = sourceRoot.createPackageFragment(PACKAGE_NAME, true, new NullProgressMonitor());
        ICompilationUnit unit = packageFragment.createCompilationUnit("Owner.java", SOURCE, true, new NullProgressMonitor()); //$NON-NLS-1$

        markerType = unit.getType("Marker"); //$NON-NLS-1$
        modeType = unit.getType("Mode"); //$NON-NLS-1$
        recType = unit.getType("Rec"); //$NON-NLS-1$
        ownerType = unit.getType(OWNER); //$NON-NLS-1$
        primsType = unit.getType("Prims"); //$NON-NLS-1$
        takesMethod = method(ownerType, "takes", 2); //$NON-NLS-1$
        printLinesMethod = method(ownerType, "printLines", 2); //$NON-NLS-1$
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
    public void typeQualifiedMethodInvocationsResolveToTheirDeclaringOwners() {
        String src = """
                package fixture;
                class A {
                    static void reset(int count) {}
                }
                class B {
                    static void reset(int count) {}
                }
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        A.reset(count);
                        B.reset(count);
                    }
                }
                """;

        BytecodeSearchEntry fromA = reference(Kind.METHOD, takesMethod, "reset", "reset", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.A", "(I)V"); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchEntry fromB = reference(Kind.METHOD, takesMethod, "reset", "reset", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.B", "(I)V"); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch fromAMatch = new BytecodeSearchMatch(fromA);
        BytecodeSearchMatch fromBMatch = new BytecodeSearchMatch(fromB);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                new BytecodeSourceRangeResolver().rangesFor(List.of(fromAMatch, fromBMatch), src);

        assertEquals(src.indexOf("reset", src.indexOf("A.")), ranges.get(fromAMatch).offset()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(src.indexOf("reset", src.indexOf("B.")), ranges.get(fromBMatch).offset()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void packageQualifiedReceiversRequireExactDeclaringOwner() {
        String src = """
                package fixture;
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        pkg1.Util.reset(count);
                        pkg2.Util.reset(count);
                        int left = pkg1.Util.field;
                        int right = pkg2.Util.field;
                        Runnable first = pkg1.Util::run;
                        Runnable second = pkg2.Util::run;
                    }
                }
                """;

        BytecodeSearchEntry method1 = reference(Kind.METHOD, takesMethod, "reset", "reset", //$NON-NLS-1$ //$NON-NLS-2$
                "pkg1.Util", "(I)V"); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchEntry method2 = reference(Kind.METHOD, takesMethod, "reset", "reset", //$NON-NLS-1$ //$NON-NLS-2$
                "pkg2.Util", "(I)V"); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchEntry field1 = reference(Kind.FIELD, takesMethod, FIELD, FIELD,
                "pkg1.Util", "I", Access.READ); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchEntry field2 = reference(Kind.FIELD, takesMethod, FIELD, FIELD,
                "pkg2.Util", "I", Access.READ); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchEntry reference1 = reference(Kind.METHOD, takesMethod, "run", "run", //$NON-NLS-1$ //$NON-NLS-2$
                "pkg1.Util", "()V"); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchEntry reference2 = reference(Kind.METHOD, takesMethod, "run", "run", //$NON-NLS-1$ //$NON-NLS-2$
                "pkg2.Util", "()V"); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch method1Match = new BytecodeSearchMatch(method1);
        BytecodeSearchMatch method2Match = new BytecodeSearchMatch(method2);
        BytecodeSearchMatch field1Match = new BytecodeSearchMatch(field1);
        BytecodeSearchMatch field2Match = new BytecodeSearchMatch(field2);
        BytecodeSearchMatch reference1Match = new BytecodeSearchMatch(reference1);
        BytecodeSearchMatch reference2Match = new BytecodeSearchMatch(reference2);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                new BytecodeSourceRangeResolver().rangesFor(
                        List.of(method1Match, method2Match, field1Match, field2Match, reference1Match, reference2Match), src);

        assertEquals(src.indexOf("reset", src.indexOf("pkg1.Util.reset")), ranges.get(method1Match).offset()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(src.indexOf("reset", src.indexOf("pkg2.Util.reset")), ranges.get(method2Match).offset()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(src.indexOf(FIELD, src.indexOf("pkg1.Util.field")), ranges.get(field1Match).offset()); //$NON-NLS-1$
        assertEquals(src.indexOf(FIELD, src.indexOf("pkg2.Util.field")), ranges.get(field2Match).offset()); //$NON-NLS-1$
        assertEquals(src.indexOf("run", src.indexOf("pkg1.Util::")), ranges.get(reference1Match).offset()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(src.indexOf("run", src.indexOf("pkg2.Util::")), ranges.get(reference2Match).offset()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void typeMethodReferencesResolveToTheirDeclaringOwners() {
        String src = """
                package fixture;
                class A {
                    static void reset(int count) {}
                }
                class B {
                    static void reset(int count) {}
                }
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        java.util.function.IntConsumer first = A::reset;
                        java.util.function.IntConsumer second = B::reset;
                        java.util.function.Consumer<java.util.ArrayList<String>> third =
                                java.util.ArrayList<String>::clear;
                        java.util.function.Consumer<java.util.LinkedList<String>> fourth =
                                java.util.LinkedList<String>::clear;
                    }
                }
                """;

        BytecodeSearchEntry fromA = reference(Kind.METHOD, takesMethod, "reset", "reset", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.A", "(I)V"); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchEntry fromB = reference(Kind.METHOD, takesMethod, "reset", "reset", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.B", "(I)V"); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchEntry fromArrayList = reference(Kind.METHOD, takesMethod, "clear", "clear", //$NON-NLS-1$ //$NON-NLS-2$
                "java.util.ArrayList", "()V"); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchEntry fromLinkedList = reference(Kind.METHOD, takesMethod, "clear", "clear", //$NON-NLS-1$ //$NON-NLS-2$
                "java.util.LinkedList", "()V"); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch fromAMatch = new BytecodeSearchMatch(fromA);
        BytecodeSearchMatch fromBMatch = new BytecodeSearchMatch(fromB);
        BytecodeSearchMatch fromArrayListMatch = new BytecodeSearchMatch(fromArrayList);
        BytecodeSearchMatch fromLinkedListMatch = new BytecodeSearchMatch(fromLinkedList);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                new BytecodeSourceRangeResolver().rangesFor(List.of(fromAMatch, fromBMatch, fromArrayListMatch, fromLinkedListMatch), src);

        assertEquals(src.indexOf("reset", src.indexOf("A::")), ranges.get(fromAMatch).offset()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(src.indexOf("reset", src.indexOf("B::")), ranges.get(fromBMatch).offset()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(src.indexOf("clear", src.indexOf("ArrayList<String>::")), //$NON-NLS-1$ //$NON-NLS-2$
                ranges.get(fromArrayListMatch).offset());
        assertEquals(src.indexOf("clear", src.indexOf("LinkedList<String>::")), //$NON-NLS-1$ //$NON-NLS-2$
                ranges.get(fromLinkedListMatch).offset());
    }

    @Test
    public void superMethodInvocationMatchesOnlyTheDirectSuperclass() {
        String src = """
                package fixture;
                class Base {
                    void reset(int count) {}
                }
                class Other {
                    static void reset(int count) {}
                }
                class Owner extends Base {
                    void takes(int count, java.lang.String... names) {
                        super.reset(count);
                        Other.reset(count);
                    }
                }
                """;

        BytecodeSearchEntry base = reference(Kind.METHOD, takesMethod, "reset", "reset", //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_BASE, "(I)V"); //$NON-NLS-1$
        BytecodeSearchEntry other = reference(Kind.METHOD, takesMethod, "reset", "reset", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.Other", "(I)V"); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch baseMatch = new BytecodeSearchMatch(base);
        BytecodeSearchMatch otherMatch = new BytecodeSearchMatch(other);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                new BytecodeSourceRangeResolver().rangesFor(List.of(baseMatch, otherMatch), src);

        int superOffset = src.indexOf("reset", src.indexOf("super.")); //$NON-NLS-1$ //$NON-NLS-2$
        int otherOffset = src.indexOf("reset", src.indexOf("Other.")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(superOffset, ranges.get(baseMatch).offset());
        assertEquals(otherOffset, ranges.get(otherMatch).offset());
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
        BytecodeSearchMatch m1 = new BytecodeSearchMatch(e1, 0);
        BytecodeSearchMatch m2 = new BytecodeSearchMatch(e2, 1);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(m1, m2), SOURCE);
        assertRangeText(ranges.get(m1), "printf(\"%d %s\", count, names.length)"); //$NON-NLS-1$
        assertRangeText(ranges.get(m2), "printf(\"%d\")"); //$NON-NLS-1$
    }

    @Test
    public void ordinaryArrayParametersRequireExactArgumentCount() {
        String src = """
                package fixture;
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        Prims.arrayOnly();
                        Prims.arrayOnly(new Object[0]);
                    }
                }
                """;

        BytecodeSearchEntry entry = reference(Kind.METHOD, takesMethod, "arrayOnly", "arrayOnly", //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_PRIMS, "([Ljava/lang/Object;)V"); //$NON-NLS-1$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        int expectedOffset = src.indexOf("arrayOnly", src.indexOf("arrayOnly") + 1); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(expectedOffset, range.offset());
    }

    /**
     * Verifies fix: {@code isTypePositionName} now accepts a {@link SimpleName} that is the
     * qualifier of a {@link org.eclipse.jdt.core.dom.QualifiedName} and looks like a type name
     * (first character upper-case). Before the fix, {@code System} in {@code System.out} was
     * rejected because the code only handled {@code MethodInvocation} and
     * {@code ExpressionMethodReference} parents.
     */
    @Test
    public void staticFieldTypeQualifierIsFoundAsTypeReference() {
        // Source must contain the "takes" method so AstDeclarationWindow can locate the enclosing
        // window for takesMethod within this custom source (same name and parameter signature).
        String src = """
                package fixture;
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        Object x = System.out;
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchEntry entry = reference(Kind.TYPE, takesMethod, "System", "java.lang.System", null, null); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        assertNotNull("Type qualifier in static field access must be resolved as a type reference", range); //$NON-NLS-1$
        assertEquals("System", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    /**
     * Verifies fix: when a static field reference is package-qualified ({@code pkg.Foo.CONST}),
     * the {@code Foo} node is the {@code name} part of the inner {@code pkg.Foo} QualifiedName,
     * not the direct qualifier of {@code Foo.CONST}. After the loop climbs to
     * {@code QualifiedName("pkg.Foo")}, the parent is {@code QualifiedName("pkg.Foo.CONST")}
     * whose qualifier is {@code QualifiedName("pkg.Foo")} — the new {@code instanceof QualifiedName}
     * check in the existing {@code isTypeLikeQualifier} block now accepts this case.
     */
    @Test
    public void packageQualifiedStaticFieldTypeQualifierIsFoundAsTypeReference() {
        String src = """
                package fixture;
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        Object x = java.lang.System.out;
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchEntry entry = reference(Kind.TYPE, takesMethod, "System", "java.lang.System", null, null); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        assertNotNull("package-qualified static field type qualifier must be resolved as a type reference", range); //$NON-NLS-1$
        assertEquals("System", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void singleSegmentPackageQualifierIsFoundAsPackageReference() {
        String src = """
                package fixture;
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        pkg.Foo value = null;
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchEntry entry = reference(Kind.PACKAGE, takesMethod, "pkg", "pkg", null, null); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        int packageOffset = src.indexOf("pkg.Foo"); //$NON-NLS-1$
        assertNotNull("single-segment package qualifier must resolve to the visible package token", range); //$NON-NLS-1$
        assertEquals(packageOffset, range.offset());
        assertEquals("pkg", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    /**
     * Verifies fix: for a CONSTRUCTOR entry whose bytecode descriptor has one extra
     * synthetic outer-instance parameter, {@code matchesArgumentCount} now accepts
     * {@code argumentCount == argTypes.length - 1} so that source-level construction
     * expressions with the correct visible argument count are resolved rather than
     * falling back to the enclosing method range.
     * <p>
     * The entry is attached to {@code takesMethod} (so the SourceWindow covers the
     * {@code takes} body), uses a 2-parameter descriptor {@code (Lfixture/Owner;I)V}
     * (synthetic outer-ref + visible int), and the source expression {@code new Inner(count)}
     * supplies only one argument.  Before the fix the argument-count check rejects the
     * call site; after the fix it resolves to the constructor type name {@code Inner}.
     */
    @Test
    public void constructorWithSyntheticParameterMatchesSourceArgCount() {
        // Entry element = takesMethod so the SourceWindow covers the takes() body
        BytecodeSearchEntry entry = reference(Kind.CONSTRUCTOR, takesMethod,
                "Inner", "fixture.Owner.Inner", "fixture.Owner.Inner", "(Lfixture/Owner;I)V"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        String src = """
                package fixture;
                class Owner {
                    class Inner {
                        Inner(int value) {}
                    }
                    void takes(int count, java.lang.String... names) {
                        new Inner(count);
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        // addLastName resolves to the type name ("Inner") within the new-expression, not the full expression
        assertNotNull("constructor with synthetic outer-ref must be resolved to the source call site", range); //$NON-NLS-1$
        assertEquals("resolved range must cover the constructor type name", //$NON-NLS-1$
                "Inner", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void varargsConstructorWithSyntheticParameterMatchesZeroSourceArgs() {
        BytecodeSearchEntry entry = reference(Kind.CONSTRUCTOR, takesMethod,
                "Inner", "fixture.Owner.Inner", "fixture.Owner.Inner", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "(Lfixture/Owner;[Ljava/lang/String;)V"); //$NON-NLS-1$

        String src = """
                package fixture;
                class Owner {
                    class Inner {
                        Inner(String... values) {}
                    }
                    void takes(int count, java.lang.String... names) {
                        new Inner();
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        assertNotNull("varargs constructor with synthetic outer-ref must resolve to the source call site", range); //$NON-NLS-1$
        assertEquals("Inner", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void topLevelConstructorsRequireExactArgumentCount() {
        BytecodeSearchEntry entry = reference(Kind.CONSTRUCTOR, takesMethod,
                OWNER, FIXTURE_OWNER, FIXTURE_OWNER, "(ILjava/lang/String;)V"); //$NON-NLS-1$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), SOURCE).get(match);

        assertEquals(SOURCE.indexOf(OWNER, SOURCE.indexOf("new Owner(count, names[0])")), range.offset()); //$NON-NLS-1$
    }

    @Test
    public void typeReferencesIgnoreSameNamedValueExpressions() {
        String src = """
                package fixture;
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        Object Foo = null;
                        consume(Foo);
                        new Foo();
                    }
                }
                """;

        BytecodeSearchEntry entry = reference(Kind.TYPE, takesMethod, "Foo", "fixture.Foo", null, null); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        assertEquals(src.indexOf("Foo", src.indexOf("new Foo")), range.offset()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void constructorReferencesResolveKeywordsAndTypeNames() {
        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();

        assertRangeText(resolver.rangeFor(reference(Kind.CONSTRUCTOR, defaultConstructor, OWNER, FIXTURE_OWNER, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "(I)V"), SOURCE), "this"); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.CONSTRUCTOR, intConstructor, "Base", FIXTURE_BASE, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_BASE, "()V"), SOURCE), "super"); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.CONSTRUCTOR, takesMethod, OWNER, FIXTURE_OWNER, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "()V"), SOURCE), OWNER); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.CONSTRUCTOR, takesMethod, OWNER, FIXTURE_OWNER, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "(I)V"), SOURCE), OWNER); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void superConstructorInvocationMatchesOnlyTheDirectSuperclass() {
        String src = """
                package fixture;
                class Base {
                    Base(int value) {}
                }
                class Other {
                    Other(int value) {}
                }
                class Owner extends Base {
                    Owner(int value) {
                        super(value);
                        new Other(value);
                    }
                }
                """;

        BytecodeSearchEntry base = reference(Kind.CONSTRUCTOR, intConstructor, "Base", FIXTURE_BASE, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_BASE, "(I)V"); //$NON-NLS-1$
        BytecodeSearchEntry other = reference(Kind.CONSTRUCTOR, intConstructor, "Other", "fixture.Other", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.Other", "(I)V"); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch baseMatch = new BytecodeSearchMatch(base);
        BytecodeSearchMatch otherMatch = new BytecodeSearchMatch(other);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                new BytecodeSourceRangeResolver().rangesFor(List.of(baseMatch, otherMatch), src);

        BytecodeSourceRangeResolver.SourceRange baseRange = ranges.get(baseMatch);
        BytecodeSourceRangeResolver.SourceRange otherRange = ranges.get(otherMatch);
        assertEquals("super", src.substring(baseRange.offset(), baseRange.offset() + baseRange.length())); //$NON-NLS-1$
        assertEquals("Other", src.substring(otherRange.offset(), otherRange.offset() + otherRange.length())); //$NON-NLS-1$
    }

    @Test
    public void parameterizedConstructorTypesResolveTheirRawTypeNames() {
        String src = """
                package fixture;
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        new java.util.ArrayList<String>();
                        java.util.function.Supplier<java.util.ArrayList<String>> created =
                                java.util.ArrayList<String>::new;
                    }
                }
                """;

        BytecodeSearchEntry invocation = reference(Kind.CONSTRUCTOR, takesMethod, "ArrayList", "java.util.ArrayList", //$NON-NLS-1$ //$NON-NLS-2$
                "java.util.ArrayList", "()V"); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchEntry methodReference = reference(Kind.CONSTRUCTOR, takesMethod, "ArrayList", "java.util.ArrayList", //$NON-NLS-1$ //$NON-NLS-2$
                "java.util.ArrayList", "()V"); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch invocationMatch = new BytecodeSearchMatch(invocation, 0);
        BytecodeSearchMatch methodReferenceMatch = new BytecodeSearchMatch(methodReference, 1);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                new BytecodeSourceRangeResolver().rangesFor(List.of(invocationMatch, methodReferenceMatch), src);

        int invocationOffset = src.indexOf("ArrayList", src.indexOf("new java.util.")); //$NON-NLS-1$ //$NON-NLS-2$
        int referenceOffset = src.indexOf("ArrayList", src.indexOf("java.util.ArrayList<String>::new")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(invocationOffset, ranges.get(invocationMatch).offset());
        assertEquals(referenceOffset, ranges.get(methodReferenceMatch).offset());
    }

    @Test
    public void parameterizedConstructorReferencesIgnoreAbsentSourceArguments() {
        String src = """
                package fixture;
                class Foo {
                    Foo(String value) {}
                }
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        java.util.function.Function<String, Foo> created = Foo::new;
                    }
                }
                """;

        BytecodeSearchEntry entry = reference(Kind.CONSTRUCTOR, takesMethod, "Foo", "fixture.Foo", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.Foo", "(Ljava/lang/String;)V"); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        int referenceOffset = src.indexOf("Foo", src.indexOf("Foo::new")); //$NON-NLS-1$ //$NON-NLS-2$
        assertNotNull("parameterized constructor reference must resolve", range); //$NON-NLS-1$
        assertEquals(referenceOffset, range.offset());
        assertEquals("Foo", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void packageQualifiedConstructorTypesRequireExactOwners() {
        String src = """
                package fixture;
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        new p1.Widget();
                        new p2.Widget();
                    }
                }
                """;

        BytecodeSearchEntry first = reference(Kind.CONSTRUCTOR, takesMethod, "Widget", "p1.Widget", //$NON-NLS-1$ //$NON-NLS-2$
                "p1.Widget", "()V"); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchEntry second = reference(Kind.CONSTRUCTOR, takesMethod, "Widget", "p2.Widget", //$NON-NLS-1$ //$NON-NLS-2$
                "p2.Widget", "()V"); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch firstMatch = new BytecodeSearchMatch(first);
        BytecodeSearchMatch secondMatch = new BytecodeSearchMatch(second);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                new BytecodeSourceRangeResolver().rangesFor(List.of(firstMatch, secondMatch), src);

        assertEquals(src.indexOf("Widget"), ranges.get(firstMatch).offset()); //$NON-NLS-1$
        assertEquals(src.lastIndexOf("Widget"), ranges.get(secondMatch).offset()); //$NON-NLS-1$
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
        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(match), src);

        BytecodeSourceRangeResolver.SourceRange range = ranges.get(match);
        assertNotNull("range must not be null", range); //$NON-NLS-1$
        // The resolver must find the "x" identifier inside target(), not fall back to
        // a broad enclosing-method range.
        assertEquals("x", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void explicitThisAndSuperFieldAccessesResolveToTheirDeclaringOwners() {
        String src = """
                package fixture;
                class Base {
                    int field;
                }
                class Owner extends Base {
                    int field;
                    void takes(int count, java.lang.String... names) {
                        this.field = count;
                        super.field = count;
                    }
                }
                """;

        BytecodeSearchEntry ownerField = reference(Kind.FIELD, takesMethod, FIELD, FIELD,
                FIXTURE_OWNER, "I", Access.WRITE); //$NON-NLS-1$
        BytecodeSearchEntry baseField = reference(Kind.FIELD, takesMethod, FIELD, FIELD,
                FIXTURE_BASE, "I", Access.WRITE); //$NON-NLS-1$

        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();
        BytecodeSearchMatch ownerMatch = new BytecodeSearchMatch(ownerField);
        BytecodeSearchMatch baseMatch = new BytecodeSearchMatch(baseField);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(ownerMatch, baseMatch), src);

        int thisFieldOffset = src.indexOf(FIELD, src.indexOf("this.")); //$NON-NLS-1$
        int superFieldOffset = src.indexOf(FIELD, src.indexOf("super.")); //$NON-NLS-1$
        assertEquals(thisFieldOffset, ranges.get(ownerMatch).offset());
        assertEquals(superFieldOffset, ranges.get(baseMatch).offset());
    }

    @Test
    public void expressionQualifiedFieldAccessIsNotAssignedWithoutReceiverBindings() {
        String src = """
                package fixture;
                class Base {
                    int field;
                }
                class Owner extends Base {
                    int field;
                    Owner receiver() { return this; }
                    void takes(int count, java.lang.String... names) {
                        receiver().field = count;
                    }
                }
                """;

        BytecodeSearchEntry wrongOwner = reference(Kind.FIELD, takesMethod, FIELD, FIELD,
                FIXTURE_BASE, "I", Access.WRITE); //$NON-NLS-1$

        BytecodeSearchMatch wrongOwnerMatch = new BytecodeSearchMatch(wrongOwner);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(wrongOwnerMatch), src).get(wrongOwnerMatch);
        int explicitAccessOffset = src.indexOf(FIELD, src.indexOf("receiver().")); //$NON-NLS-1$
        assertTrue("Expression-based access must not be assigned to an owner without receiver bindings", //$NON-NLS-1$
                range.offset() != explicitAccessOffset || range.length() != FIELD.length());
    }

    @Test
    public void expressionQualifiedMethodInvocationIsNotAssignedWithoutReceiverBindings() {
        String src = """
                package fixture;
                class First {
                    int size() { return 1; }
                }
                class Second {
                    int size() { return 2; }
                }
                class Owner {
                    void takes(First first, Second second) {
                        first.size();
                        second.size();
                    }
                }
                """;

        BytecodeSearchEntry secondSize = reference(Kind.METHOD, takesMethod, "size", "size", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.Second", "()I"); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch secondSizeMatch = new BytecodeSearchMatch(secondSize);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(secondSizeMatch), src).get(secondSizeMatch);
        int firstSizeOffset = src.indexOf("size", src.indexOf("first.")); //$NON-NLS-1$ //$NON-NLS-2$
        int secondSizeOffset = src.indexOf("size", src.indexOf("second.")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("Expression-based invocation must not be assigned to an owner without receiver bindings", //$NON-NLS-1$
                range.offset() != firstSizeOffset && range.offset() != secondSizeOffset);
    }

    @Test
    public void singleLocalVariableReceiverCallResolvesToTheOnlyCandidate() {
        String src = """
                package fixture;
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        java.io.PrintWriter pw = null;
                        pw.println(count);
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchEntry entry = reference(Kind.METHOD, takesMethod, "println", "println", //$NON-NLS-1$ //$NON-NLS-2$
                "java.io.PrintWriter", "(I)V"); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        int printlnOffset = src.indexOf("println"); //$NON-NLS-1$
        assertNotNull("local-variable receiver with single call must resolve to the call site", range); //$NON-NLS-1$
        assertEquals("println(count)", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
        assertEquals(printlnOffset, range.offset());
    }

    @Test
    public void multipleLocalVariableReceiverCallsWithImportResolveInOrder() {
        String src = """
                package fixture;
                import java.io.PrintWriter;
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        PrintWriter pw = null;
                        pw.println("first");
                        pw.println("second");
                        pw.println("third");
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchEntry e = reference(Kind.METHOD, takesMethod, "println", "println", //$NON-NLS-1$ //$NON-NLS-2$
                "java.io.PrintWriter", "(Ljava/lang/String;)V"); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchMatch m0 = new BytecodeSearchMatch(e, 0);
        BytecodeSearchMatch m1 = new BytecodeSearchMatch(e, 1);
        BytecodeSearchMatch m2 = new BytecodeSearchMatch(e, 2);

        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                new BytecodeSourceRangeResolver().rangesFor(List.of(m0, m1, m2), src);

        assertEquals("println(\"first\")", rangeText(ranges.get(m0), src)); //$NON-NLS-1$
        assertEquals("println(\"second\")", rangeText(ranges.get(m1), src)); //$NON-NLS-1$
        assertEquals("println(\"third\")", rangeText(ranges.get(m2), src)); //$NON-NLS-1$
    }

    @Test
    public void multipleParameterReceiverCallsResolveInOrder() {
        // Mirrors ComponentHelper.class: PrintWriter out is a method parameter (not a local var).
        // Two receivers 'out' and 'err' (both PrintWriter) are disambiguated by the type-scope
        // which maps each variable name to its declared type and filters against the declaring owner.
        String src = """
                package fixture;
                import java.io.PrintWriter;
                class Owner {
                    void printLines(java.io.PrintWriter out, java.io.PrintWriter err) {
                        out.println("alpha");
                        err.println("beta");
                        out.println("gamma");
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchEntry e = reference(Kind.METHOD, printLinesMethod, "println", "println", //$NON-NLS-1$ //$NON-NLS-2$
                "java.io.PrintWriter", "(Ljava/lang/String;)V"); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchMatch m0 = new BytecodeSearchMatch(e, 0);
        BytecodeSearchMatch m1 = new BytecodeSearchMatch(e, 1);
        BytecodeSearchMatch m2 = new BytecodeSearchMatch(e, 2);

        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                new BytecodeSourceRangeResolver().rangesFor(List.of(m0, m1, m2), src);

        assertEquals("println(\"alpha\")", rangeText(ranges.get(m0), src)); //$NON-NLS-1$
        assertEquals("println(\"beta\")", rangeText(ranges.get(m1), src)); //$NON-NLS-1$
        assertEquals("println(\"gamma\")", rangeText(ranges.get(m2), src)); //$NON-NLS-1$
    }

    @Test
    public void explicitThisMethodInvocationResolvesLikeImplicitReceiver() {
        String src = """
                package fixture;
                class Owner {
                    void helper() {}
                    void takes(int count, java.lang.String... names) {
                        this.helper();
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchEntry helper = reference(Kind.METHOD, takesMethod, "helper", "helper", //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "()V"); //$NON-NLS-1$

        BytecodeSearchMatch helperMatch = new BytecodeSearchMatch(helper);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(helperMatch), src).get(helperMatch);
        int helperOffset = src.indexOf("helper", src.indexOf("this.")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(helperOffset, range.offset());
        assertEquals("helper()", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void qualifiedThisMethodInvocationResolvesToDeclaringOwner() throws JavaModelException {
        String src = """
                package fixture;
                class QualifiedThisOwner {
                    void helper() {}
                    class Inner {
                        void takes(int count, java.lang.String... names) {
                            QualifiedThisOwner.this.helper();
                        }
                    }
                }
                """; //$NON-NLS-1$
        ICompilationUnit unit = packageFragment.createCompilationUnit("QualifiedThisOwner.java", src, true, //$NON-NLS-1$
                new NullProgressMonitor());
        IMethod innerTakes = method(unit.getType("QualifiedThisOwner").getType("Inner"), "takes", 2); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchEntry helper = reference(Kind.METHOD, innerTakes, "helper", "helper", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.QualifiedThisOwner", "()V"); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch helperMatch = new BytecodeSearchMatch(helper);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(helperMatch), src).get(helperMatch);
        int helperOffset = src.indexOf("helper", src.indexOf("QualifiedThisOwner.this.")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(helperOffset, range.offset());
        assertEquals("helper()", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void localVariablesAreNotMatchedAsFieldReferences() {
        String src = """
                package fixture;
                class Foo {
                    static int value;
                }
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        int value = count;
                        consume(value);
                        consume(Foo.value);
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchEntry entry = reference(Kind.FIELD, takesMethod, "value", "value", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.Foo", "I", Access.READ); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        assertNotNull("qualified field reference must still resolve", range); //$NON-NLS-1$
        assertEquals(src.indexOf("value", src.indexOf("Foo.value")), range.offset()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void forLoopVariablesDoNotHideLaterFieldReferences() {
        String src = """
                package fixture;
                class Foo {
                    static int value;
                }
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        for (int value = 0; value < count; value++) {
                            consume(value);
                        }
                        consume(value);
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchEntry entry = reference(Kind.FIELD, takesMethod, "value", "value", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.Foo", "I", Access.READ); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        int afterLoopValueOffset = src.indexOf("value", src.lastIndexOf("consume(value);")); //$NON-NLS-1$ //$NON-NLS-2$
        assertNotNull("field reference after loop must still resolve", range); //$NON-NLS-1$
        assertEquals(afterLoopValueOffset, range.offset());
        assertEquals("value", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void enhancedForVariablesDoNotHideLaterFieldReferences() {
        String src = """
                package fixture;
                class Foo {
                    static int value;
                }
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        for (int value : values()) {
                            consume(value);
                        }
                        consume(value);
                    }
                    int[] values() { return new int[0]; }
                }
                """; //$NON-NLS-1$

        BytecodeSearchEntry entry = reference(Kind.FIELD, takesMethod, "value", "value", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.Foo", "I", Access.READ); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        int afterLoopValueOffset = src.indexOf("value", src.lastIndexOf("consume(value);")); //$NON-NLS-1$ //$NON-NLS-2$
        assertNotNull("field reference after enhanced loop must still resolve", range); //$NON-NLS-1$
        assertEquals(afterLoopValueOffset, range.offset());
        assertEquals("value", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void catchParametersDoNotHideLaterFieldReferences() {
        String src = """
                package fixture;
                class Foo {
                    static int value;
                }
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        try {
                            consume(count);
                        } catch (Exception value) {
                            consume(value);
                        }
                        consume(value);
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchEntry entry = reference(Kind.FIELD, takesMethod, "value", "value", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.Foo", "I", Access.READ); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        int afterCatchValueOffset = src.indexOf("value", src.lastIndexOf("consume(value);")); //$NON-NLS-1$ //$NON-NLS-2$
        assertNotNull("field reference after catch must still resolve", range); //$NON-NLS-1$
        assertEquals(afterCatchValueOffset, range.offset());
        assertEquals("value", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void lambdaParametersDoNotHideLaterFieldReferences() {
        String src = """
                package fixture;
                import java.util.function.Consumer;
                class Foo {
                    static String value;
                }
                class Owner {
                    void takes(int count, java.lang.String... names) {
                        Consumer<String> consumer = (String value) -> consume(value);
                        consume(value);
                    }
                }
                """; //$NON-NLS-1$

        BytecodeSearchEntry entry = reference(Kind.FIELD, takesMethod, "value", "value", //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.Foo", "Ljava/lang/String;", Access.READ); //$NON-NLS-1$ //$NON-NLS-2$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        int afterLambdaValueOffset = src.indexOf("value", src.lastIndexOf("consume(value);")); //$NON-NLS-1$ //$NON-NLS-2$
        assertNotNull("field reference after lambda must still resolve", range); //$NON-NLS-1$
        assertEquals(afterLambdaValueOffset, range.offset());
        assertEquals("value", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
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
        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(match), src);

        BytecodeSourceRangeResolver.SourceRange range = ranges.get(match);
        assertNotNull("range must not be null", range); //$NON-NLS-1$
        // AstDeclarationWindow must use "int field = hashCode();" (the FieldDeclaration)
        // as the search window — not "int field = 0;" (the local-variable statement in
        // before()) — so that the hashCode() call site in the field initializer is found.
        assertEquals("hashCode()", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void recordMemberReferencesUseTheRecordDeclarationWindow() throws JavaModelException {
        String src = """
                package fixture;
                class Padding {
                    int one;
                    int two;
                    int three;
                }
                record Rec(int value) {
                    int doubled() { return value * 2; }
                }
                """;

        IMethod doubledMethod = method(recType, "doubled", 0); //$NON-NLS-1$
        BytecodeSearchEntry entry = reference(Kind.FIELD, doubledMethod,
                "value", "value", "fixture.Rec", "I", Access.READ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        int referenceOffset = src.indexOf("value", src.indexOf("return ")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(referenceOffset, range.offset());
        assertEquals("value", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
    }

    @Test
    public void localClassOrdinalsSelectTheMatchingDeclarationWindow() {
        String src = """
                package fixture;
                class Owner {
                    void first() {
                        class Local {
                            int marker = hit();
                        }
                    }

                    void second() {
                        class Local {
                            int marker = hit();
                        }
                    }

                    int hit() { return 0; }
                }
                """;

        IField marker = binaryLocalClassField("Owner$2Local.class", "marker"); //$NON-NLS-1$ //$NON-NLS-2$
        BytecodeSearchEntry entry = new BytecodeSearchEntry(
                Kind.METHOD,
                false,
                BytecodeSearchEntry.elementReference(null, marker),
                BytecodeSearchEntry.symbolReference("hit", "hit", FIXTURE_OWNER, "()I")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        BytecodeSourceRangeResolver.SourceRange range = new BytecodeSourceRangeResolver()
                .rangesFor(List.of(match), src).get(match);

        int secondLocalHit = src.indexOf("hit()", src.indexOf("void second")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(secondLocalHit, range.offset());
        assertEquals("hit()", src.substring(range.offset(), range.offset() + range.length())); //$NON-NLS-1$
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
        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(match), SOURCE);

        BytecodeSourceRangeResolver.SourceRange range = ranges.get(match);
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
        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(match), customSrc);

        BytecodeSourceRangeResolver.SourceRange range = ranges.get(match);
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
        BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
        Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                resolver.rangesFor(List.of(match), customSrc);

        BytecodeSourceRangeResolver.SourceRange range = ranges.get(match);
        assertNotNull("range must not be null", range); //$NON-NLS-1$
        assertTrue("range text must start with 'target'", //$NON-NLS-1$
                customSrc.substring(range.offset(), range.offset() + range.length()).startsWith(TARGET));
    }

    /**
     * Regression test for {@code generateFile(PrintWriter)} with 21 {@code println(String)} and
     * 15 {@code println()} call sites (36 total). The resolver must navigate to each distinct
     * occurrence in source order using the type-scope heuristic that resolves
     * {@code writer} → {@code PrintWriter}.
     */
    @Test
    public void destinationDotFileInterceptorGenerateFilePrintWriterResolvesAllOccurrencesInOrder() throws Exception {
        File jar = PrintlnFixtureJarBuilder.buildJar(DecompilerTestSupport.createTargetTempDir("println-fixture")); //$NON-NLS-1$
        DecompilerTestSupport.BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "destination-dot-file-test"); //$NON-NLS-1$
        try {
            IType type = setup.javaProject().findType(PrintlnFixtureJarBuilder.DESTINATION_CLASS);
            assertNotNull("DestinationDotFileFixture must be resolvable from fixture JAR", type); //$NON-NLS-1$

            IMethod generateFile = method(type, "generateFile", 1); //$NON-NLS-1$

            BytecodeSearchEntry printlnStr = reference(Kind.METHOD, generateFile, "println", "println", //$NON-NLS-1$ //$NON-NLS-2$
                    "java.io.PrintWriter", "(Ljava/lang/String;)V"); //$NON-NLS-1$ //$NON-NLS-2$
            BytecodeSearchEntry println = reference(Kind.METHOD, generateFile, "println", "println", //$NON-NLS-1$ //$NON-NLS-2$
                    "java.io.PrintWriter", "()V"); //$NON-NLS-1$ //$NON-NLS-2$

            int printlnStrCount = 21;
            int printlnCount = 15;
            List<BytecodeSearchMatch> matches = new ArrayList<>();
            for (int i = 0; i < printlnStrCount; i++) {
                matches.add(new BytecodeSearchMatch(printlnStr, i));
            }
            for (int i = 0; i < printlnCount; i++) {
                matches.add(new BytecodeSearchMatch(println, i));
            }

            Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                    new BytecodeSourceRangeResolver().rangesFor(matches, PrintlnFixtureJarBuilder.destinationSource());

            // All 21 println(String) ordinals must navigate to distinct, in-order positions.
            int prevOffset = -1;
            for (int i = 0; i < printlnStrCount; i++) {
                BytecodeSourceRangeResolver.SourceRange r = ranges.get(matches.get(i));
                assertNotNull("println(String) ordinal " + i + " must resolve", r); //$NON-NLS-1$ //$NON-NLS-2$
                assertTrue("println(String) ordinal " + i + " offset (" + r.offset() + ") must exceed prior (" + prevOffset + ")", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        r.offset() > prevOffset);
                prevOffset = r.offset();
            }
            // All 15 println() ordinals must navigate to distinct, in-order positions.
            prevOffset = -1;
            for (int i = 0; i < printlnCount; i++) {
                BytecodeSourceRangeResolver.SourceRange r = ranges.get(matches.get(printlnStrCount + i));
                assertNotNull("println() ordinal " + i + " must resolve", r); //$NON-NLS-1$ //$NON-NLS-2$
                assertTrue("println() ordinal " + i + " offset (" + r.offset() + ") must exceed prior (" + prevOffset + ")", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        r.offset() > prevOffset);
                prevOffset = r.offset();
            }
        } finally {
            if (setup.project().exists()) {
                setup.project().delete(true, true, new NullProgressMonitor());
            }
        }
    }

    /**
     * Regression test for {@code generateFile(PrintWriter)} with 4 {@code println(String)} and
     * 7 {@code println()} call sites (11 total). The resolver must navigate to each distinct
     * occurrence in source order using the type-scope heuristic.
     */
    @Test
    public void connectionDotFileInterceptorGenerateFilePrintWriterResolvesAllOccurrencesInOrder() throws Exception {
        File jar = PrintlnFixtureJarBuilder.buildJar(DecompilerTestSupport.createTargetTempDir("println-fixture")); //$NON-NLS-1$
        DecompilerTestSupport.BundleJarProjectSetup setup = DecompilerTestSupport.createJavaProjectWithJar(jar,
                "connection-dot-file-test"); //$NON-NLS-1$
        try {
            IType type = setup.javaProject().findType(PrintlnFixtureJarBuilder.CONNECTION_CLASS);
            assertNotNull("ConnectionDotFileFixture must be resolvable from fixture JAR", type); //$NON-NLS-1$

            IMethod generateFile = method(type, "generateFile", 1); //$NON-NLS-1$

            BytecodeSearchEntry printlnStr = reference(Kind.METHOD, generateFile, "println", "println", //$NON-NLS-1$ //$NON-NLS-2$
                    "java.io.PrintWriter", "(Ljava/lang/String;)V"); //$NON-NLS-1$ //$NON-NLS-2$
            BytecodeSearchEntry println = reference(Kind.METHOD, generateFile, "println", "println", //$NON-NLS-1$ //$NON-NLS-2$
                    "java.io.PrintWriter", "()V"); //$NON-NLS-1$ //$NON-NLS-2$

            int printlnStrCount = 4;
            int printlnCount = 7;
            List<BytecodeSearchMatch> matches = new ArrayList<>();
            for (int i = 0; i < printlnStrCount; i++) {
                matches.add(new BytecodeSearchMatch(printlnStr, i));
            }
            for (int i = 0; i < printlnCount; i++) {
                matches.add(new BytecodeSearchMatch(println, i));
            }

            Map<BytecodeSearchMatch, BytecodeSourceRangeResolver.SourceRange> ranges =
                    new BytecodeSourceRangeResolver().rangesFor(matches, PrintlnFixtureJarBuilder.connectionSource());

            // All 4 println(String) ordinals must navigate to distinct, in-order positions.
            int prevOffset = -1;
            for (int i = 0; i < printlnStrCount; i++) {
                BytecodeSourceRangeResolver.SourceRange r = ranges.get(matches.get(i));
                assertNotNull("println(String) ordinal " + i + " must resolve", r); //$NON-NLS-1$ //$NON-NLS-2$
                assertTrue("println(String) ordinal " + i + " offset (" + r.offset() + ") must exceed prior (" + prevOffset + ")", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        r.offset() > prevOffset);
                prevOffset = r.offset();
            }
            // All 7 println() ordinals must navigate to distinct, in-order positions.
            prevOffset = -1;
            for (int i = 0; i < printlnCount; i++) {
                BytecodeSourceRangeResolver.SourceRange r = ranges.get(matches.get(printlnStrCount + i));
                assertNotNull("println() ordinal " + i + " must resolve", r); //$NON-NLS-1$ //$NON-NLS-2$
                assertTrue("println() ordinal " + i + " offset (" + r.offset() + ") must exceed prior (" + prevOffset + ")", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        r.offset() > prevOffset);
                prevOffset = r.offset();
            }
        } finally {
            if (setup.project().exists()) {
                setup.project().delete(true, true, new NullProgressMonitor());
            }
        }
    }

    private static IField binaryLocalClassField(String classFileName, String fieldName) {
        IClassFile classFile = proxy(IClassFile.class, (proxy, method, args) -> {
            if ("getElementName".equals(method.getName())) { //$NON-NLS-1$
                return classFileName;
            }
            return defaultValue(method.getReturnType());
        });
        return proxy(IField.class, (proxy, method, args) -> {
            if ("getElementName".equals(method.getName())) { //$NON-NLS-1$
                return fieldName;
            }
            if ("getAncestor".equals(method.getName()) && args != null && args.length == 1 //$NON-NLS-1$
                    && Integer.valueOf(IJavaElement.CLASS_FILE).equals(args[0])) {
                return classFile;
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if ("toString".equals(method.getName())) { //$NON-NLS-1$
                return type.getSimpleName();
            }
            if ("hashCode".equals(method.getName())) { //$NON-NLS-1$
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(method.getName())) { //$NON-NLS-1$
                return Boolean.valueOf(proxy == args[0]);
            }
            return handler.invoke(proxy, method, args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return Boolean.FALSE;
        }
        if (type == Character.TYPE) {
            return Character.valueOf('\0');
        }
        if (type == Long.TYPE) {
            return Long.valueOf(0L);
        }
        if (type == Float.TYPE) {
            return Float.valueOf(0.0f);
        }
        if (type == Double.TYPE) {
            return Double.valueOf(0.0d);
        }
        if (type == Byte.TYPE) {
            return Byte.valueOf((byte) 0);
        }
        if (type == Short.TYPE) {
            return Short.valueOf((short) 0);
        }
        return Integer.valueOf(0);
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

    private static String rangeText(BytecodeSourceRangeResolver.SourceRange range, String src) {
        return src.substring(range.offset(), range.offset() + range.length());
    }

    private static void assertRangeText(BytecodeSourceRangeResolver.SourceRange range, String expected) {
        assertEquals(expected, SOURCE.substring(range.offset(), range.offset() + range.length()));
    }

    private static void assertRangeStartsWith(BytecodeSourceRangeResolver.SourceRange range, String expectedPrefix) {
        assertTrue(SOURCE.substring(range.offset(), range.offset() + range.length()).startsWith(expectedPrefix));
    }
}
