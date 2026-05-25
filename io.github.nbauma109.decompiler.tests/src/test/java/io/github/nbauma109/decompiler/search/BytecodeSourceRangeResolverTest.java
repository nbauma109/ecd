/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
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
            """;

    private IProject project;
    private IType markerType;
    private IType modeType;
    private IType recType;
    private IType ownerType;
    private IMethod takesMethod;
    private IMethod defaultConstructor;
    private IMethod intConstructor;
    private IMethod targetMethod;

    @Before
    public void setUp() throws Exception {
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
                "java.io.PrintStream", "(I)V"), SOURCE), "println(names.length)"); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeStartsWith(resolver.rangeFor(reference(Kind.METHOD, takesMethod, INHERITED, INHERITED, //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.Base", "()Ljava/lang/String;"), SOURCE), "inherited()"); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.METHOD, takesMethod, TARGET, TARGET, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "()V"), SOURCE), TARGET); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.METHOD, takesMethod, STATIC_TARGET, STATIC_TARGET, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "()V"), SOURCE), STATIC_TARGET); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.METHOD, takesMethod, INHERITED, INHERITED, //$NON-NLS-1$ //$NON-NLS-2$
                "fixture.Base", null), SOURCE), INHERITED + "()"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void varargMethodReferencesAreMatchedDespiteExpandedArgumentCount() {
        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();

        // printf(String, Object...) has 2 descriptor params but the source call
        // System.out.printf("%d %s", count, names.length) has 3 AST arguments.
        // matchesArgumentCount must accept the varargs expansion and resolve the call site.
        assertRangeText(resolver.rangeFor(reference(Kind.METHOD, takesMethod, "printf", "printf", //$NON-NLS-1$ //$NON-NLS-2$
                "java.io.PrintStream", "(Ljava/lang/String;[Ljava/lang/Object;)V"), SOURCE), //$NON-NLS-1$
                "printf(\"%d %s\", count, names.length)"); //$NON-NLS-1$
    }

    @Test
    public void constructorReferencesResolveKeywordsAndTypeNames() {
        BytecodeSourceRangeResolver resolver = new BytecodeSourceRangeResolver();

        assertRangeText(resolver.rangeFor(reference(Kind.CONSTRUCTOR, defaultConstructor, OWNER, FIXTURE_OWNER, //$NON-NLS-1$ //$NON-NLS-2$
                FIXTURE_OWNER, "(I)V"), SOURCE), "this"); //$NON-NLS-1$ //$NON-NLS-2$
        assertRangeText(resolver.rangeFor(reference(Kind.CONSTRUCTOR, intConstructor, "Object", "java.lang.Object", //$NON-NLS-1$ //$NON-NLS-2$
                "java.lang.Object", "()V"), SOURCE), "super"); //$NON-NLS-1$ //$NON-NLS-2$
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
