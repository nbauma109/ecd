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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport;

@SuppressWarnings("restriction")
public class BytecodeSearchIndexPlanTest {

    @Test
    public void duplicateJarBetweenProjectsIsScannedAndHashedOnce() throws Exception {
        File tempDir = DecompilerTestSupport.createTargetTempDir("bytecode-search-plan-dedup"); //$NON-NLS-1$
        File jar = new File(tempDir, "shared.jar"); //$NON-NLS-1$
        DecompilerTestSupport.BundleJarProjectSetup first = null;
        DecompilerTestSupport.BundleJarProjectSetup second = null;
        try {
            createJar(jar);
            first = DecompilerTestSupport.createJavaProjectWithJar(jar, "bytecode-search-plan-dedup-one"); //$NON-NLS-1$
            second = DecompilerTestSupport.createJavaProjectWithJar(jar, "bytecode-search-plan-dedup-two"); //$NON-NLS-1$

            BytecodeSearchIndex index = newSearchIndex(null);
            Map<Object, IPackageFragmentRoot> roots = roots(first, second);
            List<?> plans = plan(index, roots);

            assertEquals(2, plans.size());
            Object firstWork = work(plans.get(0));
            Object secondWork = work(plans.get(1));
            assertNotNull(firstWork);
            assertNotNull(secondWork);
            assertSame(firstWork, secondWork);
            assertEquals(-1, dbJarId(plans.get(0)));
            assertEquals(-1, dbJarId(plans.get(1)));
        } finally {
            deleteProject(first);
            deleteProject(second);
            org.apache.commons.io.FileUtils.deleteQuietly(tempDir);
        }
    }

    @Test
    public void unchangedJarUsesDatabaseFingerprintWithoutKeepingWorkOrHash() throws Exception {
        File tempDir = DecompilerTestSupport.createTargetTempDir("bytecode-search-plan-fastpath"); //$NON-NLS-1$
        File jar = new File(tempDir, "cached.jar"); //$NON-NLS-1$
        DecompilerTestSupport.BundleJarProjectSetup first = null;
        DecompilerTestSupport.BundleJarProjectSetup second = null;
        try {
            createJar(jar);
            first = DecompilerTestSupport.createJavaProjectWithJar(jar, "bytecode-search-plan-fastpath-one"); //$NON-NLS-1$
            second = DecompilerTestSupport.createJavaProjectWithJar(jar, "bytecode-search-plan-fastpath-two"); //$NON-NLS-1$

            try (Connection conn = SqliteEntryStore.openInMemoryDatabase()) {
                Object lock = new Object();
                BytecodeJarIndexer.JarWork work = BytecodeJarIndexer.scan(jar, null);
                assertNotNull(BytecodeJarIndexer.index(first.jarRoot(), jar, work, conn, lock,
                        new NullProgressMonitor()));
                assertNotNull(BytecodeJarIndexer.index(second.jarRoot(), jar, work, conn, lock,
                        new NullProgressMonitor()));

                List<?> plans = plan(newSearchIndex(conn), roots(first, second));

                assertEquals(2, plans.size());
                assertNull(work(plans.get(0)));
                assertNull(work(plans.get(1)));
                int jarId = dbJarId(plans.get(0));
                assertTrue(jarId >= 0);
                assertEquals(jarId, dbJarId(plans.get(1)));
            }
        } finally {
            deleteProject(first);
            deleteProject(second);
            org.apache.commons.io.FileUtils.deleteQuietly(tempDir);
        }
    }

    @Test
    public void unreadableJarDoesNotKeepTheOthersFromBeingPublished() throws Exception {
        File tempDir = DecompilerTestSupport.createTargetTempDir("bytecode-search-rebuild-failure"); //$NON-NLS-1$
        File good = new File(tempDir, "good.jar"); //$NON-NLS-1$
        File bad = new File(tempDir, "bad.jar"); //$NON-NLS-1$
        DecompilerTestSupport.BundleJarProjectSetup first = null;
        DecompilerTestSupport.BundleJarProjectSetup second = null;
        try {
            createJar(good);
            createJar(bad);
            first = DecompilerTestSupport.createJavaProjectWithJar(good, "bytecode-search-rebuild-good"); //$NON-NLS-1$
            second = DecompilerTestSupport.createJavaProjectWithJar(bad, "bytecode-search-rebuild-bad"); //$NON-NLS-1$

            BytecodeSearchIndex index = newSearchIndex(null);
            List<?> plans = plan(index, roots(first, second));
            assertEquals(2, plans.size());
            // the jar becomes unreadable after it was planned
            java.nio.file.Files.write(bad.toPath(), new byte[] { 1, 2, 3 });

            Method rebuild = BytecodeSearchIndex.class.getDeclaredMethod("rebuild", List.class, //$NON-NLS-1$
                    org.eclipse.core.runtime.SubMonitor.class);
            rebuild.setAccessible(true);
            Object result = rebuild.invoke(index, plans,
                    org.eclipse.core.runtime.SubMonitor.convert(new NullProgressMonitor(), 100));
            Method completed = result.getClass().getDeclaredMethod("completed"); //$NON-NLS-1$
            Method indexes = result.getClass().getDeclaredMethod("indexes"); //$NON-NLS-1$
            completed.setAccessible(true);
            indexes.setAccessible(true);
            assertTrue((Boolean) completed.invoke(result));
            Map<?, ?> published = (Map<?, ?>) indexes.invoke(result);
            assertEquals(1, published.size());
            assertTrue(published.keySet().iterator().next().toString().contains("good.jar")); //$NON-NLS-1$
        } finally {
            deleteProject(first);
            deleteProject(second);
            org.apache.commons.io.FileUtils.deleteQuietly(tempDir);
        }
    }

    private static BytecodeSearchIndex newSearchIndex(Connection conn) throws Exception {
        Constructor<BytecodeSearchIndex> constructor = BytecodeSearchIndex.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        BytecodeSearchIndex index = constructor.newInstance();
        if (conn != null) {
            Field connection = BytecodeSearchIndex.class.getDeclaredField("conn"); //$NON-NLS-1$
            connection.setAccessible(true);
            connection.set(index, conn);
        }
        return index;
    }

    private static Map<Object, IPackageFragmentRoot> roots(DecompilerTestSupport.BundleJarProjectSetup... setups)
            throws Exception {
        Map<Object, IPackageFragmentRoot> roots = new LinkedHashMap<>();
        for (DecompilerTestSupport.BundleJarProjectSetup setup : setups) {
            roots.put(newRootKey(setup.jarRoot()), setup.jarRoot());
        }
        return roots;
    }

    private static Object newRootKey(IPackageFragmentRoot root) throws Exception {
        Class<?> rootKey = Class.forName(BytecodeSearchIndex.class.getName() + "$RootKey", true, //$NON-NLS-1$
                BytecodeSearchIndex.class.getClassLoader());
        Constructor<?> constructor = rootKey.getDeclaredConstructor(String.class, IPath.class);
        constructor.setAccessible(true);
        return constructor.newInstance(root.getHandleIdentifier(), root.getPath());
    }

    private static List<?> plan(BytecodeSearchIndex index, Map<Object, IPackageFragmentRoot> roots)
            throws Exception {
        Method plan = BytecodeSearchIndex.class.getDeclaredMethod("plan", Map.class, //$NON-NLS-1$
                org.eclipse.core.runtime.IProgressMonitor.class);
        plan.setAccessible(true);
        return (List<?>) plan.invoke(index, roots, new NullProgressMonitor());
    }

    private static Object work(Object plan) throws Exception {
        Method work = plan.getClass().getDeclaredMethod("work"); //$NON-NLS-1$
        work.setAccessible(true);
        return work.invoke(plan);
    }

    private static int dbJarId(Object plan) throws Exception {
        Method dbJarId = plan.getClass().getDeclaredMethod("dbJarId"); //$NON-NLS-1$
        dbJarId.setAccessible(true);
        return (int) dbJarId.invoke(plan);
    }

    private static void deleteProject(DecompilerTestSupport.BundleJarProjectSetup setup) throws Exception {
        if (setup != null && setup.project().exists()) {
            setup.project().delete(true, true, new NullProgressMonitor());
        }
    }

    private static void createJar(File jar) throws IOException {
        try (FileOutputStream file = new FileOutputStream(jar);
                java.util.jar.JarOutputStream output = new java.util.jar.JarOutputStream(file)) {
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/Type", null, "java/lang/Object", null); //$NON-NLS-1$ //$NON-NLS-2$
            writer.visitEnd();
            output.putNextEntry(new java.util.jar.JarEntry("pkg/Type.class")); //$NON-NLS-1$
            output.write(writer.toByteArray());
            output.closeEntry();
        }
    }
}
