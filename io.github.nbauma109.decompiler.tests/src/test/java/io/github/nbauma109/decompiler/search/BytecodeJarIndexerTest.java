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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport;

@SuppressWarnings("restriction")
public class BytecodeJarIndexerTest {

    @Test
    public void planSkipsMultiReleaseVersionedClassEntries()
            throws IOException {
        File tempDir = DecompilerTestSupport.createTargetTempDir("bytecode-jar-indexer"); //$NON-NLS-1$
        File jar = new File(tempDir, "multi-release.jar"); //$NON-NLS-1$
        try {
            createJar(jar);

            BytecodeJarIndexer.JarWork work = BytecodeJarIndexer.plan(jar);

            assertEquals(1, work.entries().size());
            assertEquals("pkg/Base.class", work.entries().get(0).name()); //$NON-NLS-1$
        } finally {
            org.apache.commons.io.FileUtils.deleteQuietly(tempDir);
        }
    }

    /**
     * Indexes a JAR that contains a class annotated with four distinct kinds of
     * annotation element values:
     * <ul>
     *   <li>a {@code Class}-typed value (triggers {@code annotationVisitor.visit(name, Type)})</li>
     *   <li>an enum-constant value (triggers {@code annotationVisitor.visitEnum})</li>
     *   <li>an array of enum values (triggers {@code annotationVisitor.visitArray} and the nested
     *       {@code visitEnum} inside the array visitor)</li>
     *   <li>a nested-annotation value (triggers {@code annotationVisitor.visitAnnotation})</li>
     * </ul>
     * Exercises all four callback methods on the anonymous {@code AnnotationVisitor} returned by
     * {@code BytecodeJarIndexer.ClassIndex.annotationVisitor()}.
     */
    @Test
    public void annotationVisitorCallbacksAreCoveredByAnnotatedClass() throws Exception {
        File tempDir = DecompilerTestSupport.createTargetTempDir("bytecode-jar-indexer-annotation"); //$NON-NLS-1$
        IProject project = ResourcesPlugin.getWorkspace().getRoot()
                .getProject("bytecode-jar-indexer-annotation-test-project"); //$NON-NLS-1$
        try {
            // 1 — Build annotated class bytes with all four annotation-element flavours
            byte[] classBytes = buildAnnotatedClassBytes();

            // 2 — Package in a JAR
            File jar = new File(tempDir, "annotated.jar"); //$NON-NLS-1$
            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
                jos.putNextEntry(new JarEntry("pkg/Annotated.class")); //$NON-NLS-1$
                jos.write(classBytes);
                jos.closeEntry();
            }

            // 3 — Create a minimal Java project and add the JAR to its classpath
            if (project.exists()) {
                project.delete(true, true, new NullProgressMonitor());
            }
            project.create(new NullProgressMonitor());
            project.open(new NullProgressMonitor());
            IProjectDescription desc = project.getDescription();
            desc.setNatureIds(new String[]{JavaCore.NATURE_ID});
            project.setDescription(desc, new NullProgressMonitor());
            IJavaProject javaProject = JavaCore.create(project);
            DecompilerTestSupport.configureClasspathWithJre(javaProject);
            IPackageFragmentRoot root = DecompilerTestSupport.addJarToClasspathAndGetRoot(javaProject, jar);

            // 4 — Index: reader.accept() fires visitAnnotation on the class visitor which
            //     returns annotationVisitor(type); ASM then calls visit/visitEnum/visitArray/
            //     visitAnnotation on that visitor for each annotation element.
            BytecodeJarIndexer.JarWork work = BytecodeJarIndexer.plan(jar);
            BytecodeSearchIndex.JarIndex index = BytecodeJarIndexer.index(root, jar, work,
                    new NullProgressMonitor());

            assertNotNull(index);
        } finally {
            if (project.exists()) {
                project.delete(true, true, new NullProgressMonitor());
            }
            org.apache.commons.io.FileUtils.deleteQuietly(tempDir);
        }
    }

    /**
     * Indexes a JAR that contains a synthetic {@code module-info.class} with every
     * kind of module directive: requires, exports (with named targets), opens (with
     * named targets), uses, provides, and a main-class attribute.
     * <p>
     * Exercises all methods in {@code BytecodeJarIndexer.ClassIndex.ModuleIndexer}:
     * {@code visitMainClass}, {@code visitRequire}, {@code visitExport},
     * {@code visitOpen}, {@code visitUse}, {@code visitProvide}, and
     * {@code addModuleReferences}.
     */
    @Test
    public void moduleIndexerCallbacksAreCoveredByModuleInfoClass() throws Exception {
        File tempDir = DecompilerTestSupport.createTargetTempDir("bytecode-jar-indexer-module"); //$NON-NLS-1$
        IProject project = ResourcesPlugin.getWorkspace().getRoot()
                .getProject("bytecode-jar-indexer-module-test-project"); //$NON-NLS-1$
        try {
            // 1 — Build module-info class bytes with all directive types
            byte[] moduleBytes = buildModuleInfoBytes();

            // 2 — Package in a JAR
            File jar = new File(tempDir, "module.jar"); //$NON-NLS-1$
            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
                jos.putNextEntry(new JarEntry("module-info.class")); //$NON-NLS-1$
                jos.write(moduleBytes);
                jos.closeEntry();
            }

            // 3 — Create a minimal Java project and add the JAR to its classpath
            if (project.exists()) {
                project.delete(true, true, new NullProgressMonitor());
            }
            project.create(new NullProgressMonitor());
            project.open(new NullProgressMonitor());
            IProjectDescription desc = project.getDescription();
            desc.setNatureIds(new String[]{JavaCore.NATURE_ID});
            project.setDescription(desc, new NullProgressMonitor());
            IJavaProject javaProject = JavaCore.create(project);
            DecompilerTestSupport.configureClasspathWithJre(javaProject);
            IPackageFragmentRoot root = DecompilerTestSupport.addJarToClasspathAndGetRoot(javaProject, jar);

            // 4 — Index: visitModule() sets moduleElement; the returned ModuleIndexer
            //     receives all directive callbacks; flush() processes the module branch.
            BytecodeJarIndexer.JarWork work = BytecodeJarIndexer.plan(jar);
            BytecodeSearchIndex.JarIndex index = BytecodeJarIndexer.index(root, jar, work,
                    new NullProgressMonitor());

            assertNotNull(index);
        } finally {
            if (project.exists()) {
                project.delete(true, true, new NullProgressMonitor());
            }
            org.apache.commons.io.FileUtils.deleteQuietly(tempDir);
        }
    }

    // ------------------------------------------------------------------
    // ASM class-file generators
    // ------------------------------------------------------------------

    /**
     * Produces a valid class file for {@code pkg.Annotated} whose class-level
     * annotations exercise every callback path on the anonymous AnnotationVisitor
     * returned by {@code ClassIndex.annotationVisitor()}:
     * <ul>
     *   <li>{@code @Retention(RUNTIME)} → {@code visitEnum("value", descriptor, "RUNTIME")}</li>
     *   <li>{@code @Target({METHOD})} → {@code visitArray("value")} then inner
     *       {@code visitEnum(null, descriptor, "METHOD")}</li>
     *   <li>custom annotation with {@code Class}-valued element → {@code visit("type", Type)}</li>
     *   <li>custom annotation with a nested annotation element →
     *       {@code visitAnnotation("meta", descriptor)}</li>
     * </ul>
     */
    private static byte[] buildAnnotatedClassBytes() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/Annotated", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$

        // visitEnum: @Retention(RUNTIME)
        AnnotationVisitor retAv = cw.visitAnnotation("Ljava/lang/annotation/Retention;", true); //$NON-NLS-1$
        retAv.visitEnum("value", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        retAv.visitEnd();

        // visitArray + inner visitEnum: @Target({METHOD})
        AnnotationVisitor tgtAv = cw.visitAnnotation("Ljava/lang/annotation/Target;", true); //$NON-NLS-1$
        AnnotationVisitor arrAv = tgtAv.visitArray("value"); //$NON-NLS-1$
        arrAv.visitEnum(null, "Ljava/lang/annotation/ElementType;", "METHOD"); //$NON-NLS-1$ //$NON-NLS-2$
        arrAv.visitEnd();
        tgtAv.visitEnd();

        // visit(name, Type): custom annotation with a Class-valued element
        AnnotationVisitor clsAv = cw.visitAnnotation("Lpkg/ClassAnnotation;", false); //$NON-NLS-1$
        clsAv.visit("type", Type.getType("Ljava/lang/String;")); //$NON-NLS-1$ //$NON-NLS-2$
        clsAv.visitEnd();

        // visitAnnotation(name, descriptor): custom annotation with a nested-annotation element
        AnnotationVisitor outerAv = cw.visitAnnotation("Lpkg/OuterAnnotation;", false); //$NON-NLS-1$
        AnnotationVisitor nestedAv = outerAv.visitAnnotation("meta", "Lpkg/InnerAnnotation;"); //$NON-NLS-1$ //$NON-NLS-2$
        nestedAv.visitEnd();
        outerAv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Produces a valid {@code module-info.class} for the module {@code test.mymodule}
     * with one directive of every kind so that all ModuleIndexer callbacks fire:
     * <ul>
     *   <li>{@code visitMainClass} — main class attribute</li>
     *   <li>{@code visitRequire} — one requires directive</li>
     *   <li>{@code visitExport} with named target → {@code addModuleReferences}</li>
     *   <li>{@code visitOpen} with named target → {@code addModuleReferences}</li>
     *   <li>{@code visitUse} — uses directive</li>
     *   <li>{@code visitProvide} with a provider class</li>
     * </ul>
     */
    private static byte[] buildModuleInfoBytes() {
        ClassWriter cw = new ClassWriter(0);
        // ACC_MODULE marks a module-info compilation unit; class version must be >= V9
        cw.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null); //$NON-NLS-1$

        ModuleVisitor mv = cw.visitModule("test.mymodule", 0, "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitMainClass("pkg/Main");                                          // visitMainClass //$NON-NLS-1$
        mv.visitRequire("java.base", Opcodes.ACC_MANDATED, null);               // visitRequire //$NON-NLS-1$
        mv.visitExport("pkg/exported", 0, "other.module");                      // visitExport + addModuleReferences //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitOpen("pkg/opened", 0, "other.module");                          // visitOpen + addModuleReferences //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitUse("pkg/MyService");                                           // visitUse //$NON-NLS-1$
        mv.visitProvide("pkg/MyService", "pkg/MyServiceImpl");                  // visitProvide //$NON-NLS-1$ //$NON-NLS-2$
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    // ------------------------------------------------------------------
    // Shared JAR helpers (original test)
    // ------------------------------------------------------------------

    private static void createJar(File jar)
            throws IOException {
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            addEntry(output, "pkg/Base.class"); //$NON-NLS-1$
            addEntry(output, "META-INF/versions/11/pkg/Base.class"); //$NON-NLS-1$
            addEntry(output, "META-INF/versions/21/pkg/Other.class"); //$NON-NLS-1$
        }
    }

    private static void addEntry(JarOutputStream output, String name)
            throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(new byte[] { 0, 0, 0, 0 });
        output.closeEntry();
    }
}
