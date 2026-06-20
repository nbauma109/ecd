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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Access;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.TypeCategory;

/**
 * Tests the file-backed MappedEntryStore: round-trip entry fidelity,
 * existing-file reuse, per-root isolation, and mismatch-triggered rebuild.
 * <p>
 * MappedEntryStore is package-private, so all access uses reflection via the
 * main bundle's classloader — consistent with {@link BytecodeJarIndexerTest}.
 * Likewise, BytecodeSearchEntry accessor methods require reflection in this
 * OSGi test environment because of the split classloader boundary.
 */
@SuppressWarnings("restriction")
public class MappedEntryStoreTest {

    private static final String ROOT_HANDLE = "=TestProject/src"; //$NON-NLS-1$

    private static final Class<?> STORE_CLASS;
    private static final Method OPEN_OR_CREATE;
    private static final Method SEGMENT_PATH;
    private static final Method SIZE;
    private static final Method ENTRY;
    private static final Method CLOSE;
    private static final Method GET_NAME;
    private static final Method GET_QUALIFIED_NAME;
    private static final Method GET_DECLARING_TYPE_NAME;
    private static final Method GET_KIND;
    private static final Method IS_DECLARATION;
    private static final Method GET_ACCESS;
    private static final Method GET_TYPE_CATEGORY;

    static {
        try {
            ClassLoader cl = BytecodeSearchIndex.class.getClassLoader();
            STORE_CLASS = Class.forName("io.github.nbauma109.decompiler.search.MappedEntryStore", true, cl); //$NON-NLS-1$
            OPEN_OR_CREATE = STORE_CLASS.getDeclaredMethod("openOrCreate", //$NON-NLS-1$
                    File.class, Path.class, List.class, int[].class, String.class);
            OPEN_OR_CREATE.setAccessible(true);
            SEGMENT_PATH = STORE_CLASS.getDeclaredMethod("segmentPath", Path.class, File.class, String.class); //$NON-NLS-1$
            SEGMENT_PATH.setAccessible(true);
            SIZE = STORE_CLASS.getDeclaredMethod("size"); //$NON-NLS-1$
            SIZE.setAccessible(true);
            ENTRY = STORE_CLASS.getDeclaredMethod("entry", int.class); //$NON-NLS-1$
            ENTRY.setAccessible(true);
            CLOSE = STORE_CLASS.getDeclaredMethod("close"); //$NON-NLS-1$
            CLOSE.setAccessible(true);
            GET_NAME = BytecodeSearchEntry.class.getDeclaredMethod("getName"); //$NON-NLS-1$
            GET_NAME.setAccessible(true);
            GET_QUALIFIED_NAME = BytecodeSearchEntry.class.getDeclaredMethod("getQualifiedName"); //$NON-NLS-1$
            GET_QUALIFIED_NAME.setAccessible(true);
            GET_DECLARING_TYPE_NAME = BytecodeSearchEntry.class.getDeclaredMethod("getDeclaringTypeName"); //$NON-NLS-1$
            GET_DECLARING_TYPE_NAME.setAccessible(true);
            GET_KIND = BytecodeSearchEntry.class.getDeclaredMethod("getKind"); //$NON-NLS-1$
            GET_KIND.setAccessible(true);
            IS_DECLARATION = BytecodeSearchEntry.class.getDeclaredMethod("isDeclaration"); //$NON-NLS-1$
            IS_DECLARATION.setAccessible(true);
            GET_ACCESS = BytecodeSearchEntry.class.getDeclaredMethod("getAccess"); //$NON-NLS-1$
            GET_ACCESS.setAccessible(true);
            GET_TYPE_CATEGORY = BytecodeSearchEntry.class.getDeclaredMethod("getTypeCategory"); //$NON-NLS-1$
            GET_TYPE_CATEGORY.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Path cacheDir;
    private File fakeJar;

    @Before
    public void setUp() throws IOException {
        cacheDir = Files.createTempDirectory("bsix-test"); //$NON-NLS-1$
        fakeJar = cacheDir.resolve("test.jar").toFile(); //$NON-NLS-1$
        fakeJar.createNewFile();
    }

    @After
    public void tearDown() throws IOException {
        try (var stream = Files.list(cacheDir)) {
            stream.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // ignore cleanup errors
                }
            });
        }
        Files.deleteIfExists(cacheDir);
    }

    private static BytecodeSearchEntry makeEntry(String simpleName, String qualName, String declaringType,
            Kind kind, Access access, TypeCategory typeCategory) {
        return new BytecodeSearchEntry(kind, true,
                BytecodeSearchEntry.elementReference("=Proj/src<com{" + simpleName + ".class[" + simpleName, null), //$NON-NLS-1$ //$NON-NLS-2$
                BytecodeSearchEntry.symbolReference(simpleName, qualName, declaringType, null),
                access, typeCategory);
    }

    private Object openOrCreate(List<BytecodeSearchEntry> entries, int[] counts)
            throws ReflectiveOperationException {
        return OPEN_OR_CREATE.invoke(null, fakeJar, cacheDir, entries, counts, ROOT_HANDLE);
    }

    private Path segmentPath() throws ReflectiveOperationException {
        return (Path) SEGMENT_PATH.invoke(null, cacheDir, fakeJar, ROOT_HANDLE);
    }

    private int storeSize(Object store) throws ReflectiveOperationException {
        return (int) SIZE.invoke(store);
    }

    private Object storeEntry(Object store, int id) throws ReflectiveOperationException {
        return ENTRY.invoke(store, id);
    }

    private void storeClose(Object store) throws ReflectiveOperationException {
        CLOSE.invoke(store);
    }

    private String name(Object entry) throws ReflectiveOperationException {
        return (String) GET_NAME.invoke(entry);
    }

    private String qualifiedName(Object entry) throws ReflectiveOperationException {
        return (String) GET_QUALIFIED_NAME.invoke(entry);
    }

    private String declaringTypeName(Object entry) throws ReflectiveOperationException {
        return (String) GET_DECLARING_TYPE_NAME.invoke(entry);
    }

    private Kind kind(Object entry) throws ReflectiveOperationException {
        return (Kind) GET_KIND.invoke(entry);
    }

    private boolean isDeclaration(Object entry) throws ReflectiveOperationException {
        return (boolean) IS_DECLARATION.invoke(entry);
    }

    private Access access(Object entry) throws ReflectiveOperationException {
        return (Access) GET_ACCESS.invoke(entry);
    }

    private TypeCategory typeCategory(Object entry) throws ReflectiveOperationException {
        return (TypeCategory) GET_TYPE_CATEGORY.invoke(entry);
    }

    @Test
    public void roundTripPreservesAllFields() throws Exception {
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Foo", "com.Foo", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts = {3};

        Object store = openOrCreate(entries, counts);
        try {
            assertEquals(1, storeSize(store));
            Object e = storeEntry(store, 0);
            assertEquals("Foo", name(e)); //$NON-NLS-1$
            assertEquals("com.Foo", qualifiedName(e)); //$NON-NLS-1$
            assertEquals("com", declaringTypeName(e)); //$NON-NLS-1$
            assertEquals(Kind.TYPE, kind(e));
            assertTrue(isDeclaration(e));
            assertEquals(Access.NONE, access(e));
            assertEquals(TypeCategory.CLASS, typeCategory(e));
        } finally {
            storeClose(store);
        }
    }

    @Test
    public void segmentFileIsCreated() throws Exception {
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Bar", "com.Bar", "com", Kind.TYPE, Access.NONE, TypeCategory.INTERFACE));
        int[] counts = {1};

        Object store = openOrCreate(entries, counts);
        storeClose(store);

        assertTrue("Segment file must exist after openOrCreate", Files.exists(segmentPath())); //$NON-NLS-1$
    }

    @Test
    public void secondCallReusesExistingFile() throws Exception {
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Baz", "com.Baz", "com", Kind.TYPE, Access.NONE, TypeCategory.UNKNOWN));
        int[] counts = {1};

        Object first = openOrCreate(entries, counts);
        storeClose(first);

        long bsixCount = Files.list(cacheDir).filter(p -> p.getFileName().toString().endsWith(".bsix")).count(); //$NON-NLS-1$

        Object second = openOrCreate(entries, counts);
        storeClose(second);

        long bsixCountAfter = Files.list(cacheDir).filter(p -> p.getFileName().toString().endsWith(".bsix")).count(); //$NON-NLS-1$
        assertEquals("No new segment file should be written on second call", bsixCount, bsixCountAfter); //$NON-NLS-1$
    }

    @Test
    public void differentRootsGetDifferentSegmentFiles() throws Exception {
        Path seg1 = (Path) SEGMENT_PATH.invoke(null, cacheDir, fakeJar, "=ProjectA/src"); //$NON-NLS-1$
        Path seg2 = (Path) SEGMENT_PATH.invoke(null, cacheDir, fakeJar, "=ProjectB/src"); //$NON-NLS-1$
        assertFalse("Different roots for the same jar must produce different segment paths", seg1.equals(seg2)); //$NON-NLS-1$
    }

    @Test
    public void badMagicInExistingFileTriggersRebuild() throws Exception {
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Qux", "com.Qux", "com", Kind.TYPE, Access.NONE, TypeCategory.ENUM));
        int[] counts = {2};

        // Pre-create a file at the segment path with wrong magic bytes (all zeros)
        Path seg = segmentPath();
        Files.write(seg, new byte[200]);
        assertTrue("Pre-created corrupt file must exist", Files.exists(seg)); //$NON-NLS-1$

        // openOrCreate must detect the bad header, delete the file, and rewrite it
        Object rebuilt = openOrCreate(entries, counts);
        try {
            assertEquals(1, storeSize(rebuilt));
            assertEquals("Qux", name(storeEntry(rebuilt, 0))); //$NON-NLS-1$
        } finally {
            storeClose(rebuilt);
        }
    }

    @Test
    public void multipleEntriesRoundTrip() throws Exception {
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Alpha", "pkg.Alpha", "pkg", Kind.TYPE, Access.NONE, TypeCategory.CLASS),
                makeEntry("beta", "pkg.Beta", "pkg", Kind.METHOD, Access.READ, TypeCategory.UNKNOWN),
                makeEntry("gamma", "pkg.Gamma", "pkg", Kind.FIELD, Access.WRITE, TypeCategory.UNKNOWN));
        int[] counts = {1, 7, 2};

        Object store = openOrCreate(entries, counts);
        try {
            assertEquals(3, storeSize(store));
            assertEquals("Alpha", name(storeEntry(store, 0))); //$NON-NLS-1$
            assertEquals("beta", name(storeEntry(store, 1))); //$NON-NLS-1$
            assertEquals(Access.READ, access(storeEntry(store, 1)));
            assertEquals("gamma", name(storeEntry(store, 2))); //$NON-NLS-1$
            assertEquals(Kind.FIELD, kind(storeEntry(store, 2)));
            assertEquals(Access.WRITE, access(storeEntry(store, 2)));
        } finally {
            storeClose(store);
        }
    }
}
