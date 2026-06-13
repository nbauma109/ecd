/*******************************************************************************
 * © 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.testutil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Builds a minimal JAR containing two fixture classes that replicate the
 * {@code println} call patterns found in the ActiveMQ dot-file interceptors,
 * without requiring the full {@code activemq-broker} JAR as a test resource.
 *
 * <ul>
 *   <li>{@code fixture.ConnectionDotFileFixture.generateFile(PrintWriter)}:
 *       4 {@code println(String)} + 7 {@code println()}</li>
 *   <li>{@code fixture.DestinationDotFileFixture.generateFile(PrintWriter)}:
 *       21 {@code println(String)} + 15 {@code println()}</li>
 * </ul>
 *
 * The emission order in the bytecode mirrors the source text order: pairs of
 * {@code (println(String), println())} followed by any trailing calls of the
 * longer sequence. This guarantees that ordinal K maps to the K-th source
 * occurrence when the resolver sorts matches by offset.
 */
public final class PrintlnFixtureJarBuilder {

    public static final String CONNECTION_CLASS = "fixture.ConnectionDotFileFixture"; //$NON-NLS-1$
    public static final String DESTINATION_CLASS = "fixture.DestinationDotFileFixture"; //$NON-NLS-1$

    private static final String FIXTURE_PACKAGE = "fixture"; //$NON-NLS-1$
    private static final String PRINT_WRITER_INTERNAL = "java/io/PrintWriter"; //$NON-NLS-1$
    private static final String PRINTLN_STR_DESC = "(Ljava/lang/String;)V"; //$NON-NLS-1$
    private static final String PRINTLN_DESC = "()V"; //$NON-NLS-1$

    private PrintlnFixtureJarBuilder() {
    }

    /**
     * Builds the fixture JAR in {@code outputDir} and returns the resulting file.
     * The JAR contains both fixture classes.
     */
    public static File buildJar(File outputDir) throws IOException {
        File jar = new File(outputDir, "println-fixture.jar"); //$NON-NLS-1$
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
            addClassEntry(out, "ConnectionDotFileFixture", 4, 7); //$NON-NLS-1$
            addClassEntry(out, "DestinationDotFileFixture", 21, 15); //$NON-NLS-1$
        }
        return jar;
    }

    /**
     * Returns the Java source text for {@code ConnectionDotFileFixture}.
     * The call order matches the bytecode emitted by {@link #buildJar}: pairs of
     * {@code println(String) / println()} for the overlapping range, then trailing
     * {@code println()} calls.
     */
    public static String connectionSource() {
        return source("ConnectionDotFileFixture", 4, 7); //$NON-NLS-1$
    }

    /**
     * Returns the Java source text for {@code DestinationDotFileFixture}.
     * The call order matches the bytecode: pairs, then trailing {@code println(String)} calls.
     */
    public static String destinationSource() {
        return source("DestinationDotFileFixture", 21, 15); //$NON-NLS-1$
    }

    // ---- internal helpers ---------------------------------------------------

    private static void addClassEntry(JarOutputStream out, String simpleName, int strCount, int emptyCount)
            throws IOException {
        String internalName = FIXTURE_PACKAGE + "/" + simpleName; //$NON-NLS-1$
        JarEntry entry = new JarEntry(internalName + ".class"); //$NON-NLS-1$
        out.putNextEntry(entry);
        out.write(generateClass(internalName, strCount, emptyCount));
        out.closeEntry();
    }

    private static byte[] generateClass(String internalName, int strCount, int emptyCount) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null); //$NON-NLS-1$

        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); //$NON-NLS-1$ //$NON-NLS-2$
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "generateFile", //$NON-NLS-1$
                "(Ljava/io/PrintWriter;)V", null, null); //$NON-NLS-1$
        mv.visitCode();
        emitPrintlnPattern(mv, strCount, emptyCount);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Emits {@code strCount} INVOKEVIRTUAL {@code println(String)} calls and
     * {@code emptyCount} INVOKEVIRTUAL {@code println()} calls. Pairs come first
     * (interleaved), then the remaining calls of the longer sequence.
     */
    private static void emitPrintlnPattern(MethodVisitor mv, int strCount, int emptyCount) {
        int pairs = Math.min(strCount, emptyCount);
        int strEmitted = 0;
        int emptyEmitted = 0;
        for (int i = 0; i < pairs; i++) {
            emitPrintlnStr(mv, "line" + (++strEmitted)); //$NON-NLS-1$
            emitPrintln(mv);
            emptyEmitted++;
        }
        while (strEmitted < strCount) {
            emitPrintlnStr(mv, "line" + (++strEmitted)); //$NON-NLS-1$
        }
        while (emptyEmitted < emptyCount) {
            emitPrintln(mv);
            emptyEmitted++;
        }
    }

    private static void emitPrintlnStr(MethodVisitor mv, String value) {
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitLdcInsn(value);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PRINT_WRITER_INTERNAL, "println", PRINTLN_STR_DESC, false); //$NON-NLS-1$
    }

    private static void emitPrintln(MethodVisitor mv) {
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PRINT_WRITER_INTERNAL, "println", PRINTLN_DESC, false); //$NON-NLS-1$
    }

    /**
     * Builds the Java source text whose call order exactly mirrors
     * {@link #emitPrintlnPattern}: interleaved pairs, then the remaining tail.
     */
    private static String source(String simpleName, int strCount, int emptyCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("package fixture;\n"); //$NON-NLS-1$
        sb.append("import java.io.PrintWriter;\n"); //$NON-NLS-1$
        sb.append("public class ").append(simpleName).append(" {\n"); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("    public void generateFile(PrintWriter writer) {\n"); //$NON-NLS-1$
        int pairs = Math.min(strCount, emptyCount);
        int strEmitted = 0;
        int emptyEmitted = 0;
        for (int i = 0; i < pairs; i++) {
            sb.append("        writer.println(\"line").append(++strEmitted).append("\");\n"); //$NON-NLS-1$ //$NON-NLS-2$
            sb.append("        writer.println();\n"); //$NON-NLS-1$
            emptyEmitted++;
        }
        while (strEmitted < strCount) {
            sb.append("        writer.println(\"line").append(++strEmitted).append("\");\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        while (emptyEmitted < emptyCount) {
            sb.append("        writer.println();\n"); //$NON-NLS-1$
            emptyEmitted++;
        }
        sb.append("    }\n"); //$NON-NLS-1$
        sb.append("}\n"); //$NON-NLS-1$
        return sb.toString();
    }
}
