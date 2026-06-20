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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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

    // MappedEntryStore reflection handles
    private static final Class<?> STORE_CLASS;
    private static final Method OPEN_OR_CREATE;
    private static final Method SEGMENT_PATH;
    private static final Method SIZE;
    private static final Method ENTRY;
    private static final Method CLOSE;
    private static final Method DELETE_QUIETLY;

    // BytecodeSearchEntry accessor handles
    private static final Method GET_NAME;
    private static final Method GET_QUALIFIED_NAME;
    private static final Method GET_DECLARING_TYPE_NAME;
    private static final Method GET_ELEMENT_HANDLE;
    private static final Method GET_KIND;
    private static final Method IS_DECLARATION;
    private static final Method GET_ACCESS;
    private static final Method GET_TYPE_CATEGORY;

    // BytecodeSearchIndex.JarIndex reflection handles
    private static final Constructor<?> JAR_INDEX_CTOR;
    private static final Field MAPPED_THRESHOLD_FIELD;
    private static final Method JAR_INDEX_CLOSE;
    private static final Method JAR_INDEX_MATCHES;
    private static final Method JAR_INDEX_ENTRY_COUNT;
    private static final Field JAR_INDEX_ENTRIES;
    private static final Method ESTIMATE_BYTES;
    private static final Method STRING_ESTIMATE;

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
            DELETE_QUIETLY = STORE_CLASS.getDeclaredMethod("deleteQuietly", Path.class); //$NON-NLS-1$
            DELETE_QUIETLY.setAccessible(true);

            GET_NAME = BytecodeSearchEntry.class.getDeclaredMethod("getName"); //$NON-NLS-1$
            GET_NAME.setAccessible(true);
            GET_QUALIFIED_NAME = BytecodeSearchEntry.class.getDeclaredMethod("getQualifiedName"); //$NON-NLS-1$
            GET_QUALIFIED_NAME.setAccessible(true);
            GET_DECLARING_TYPE_NAME = BytecodeSearchEntry.class.getDeclaredMethod("getDeclaringTypeName"); //$NON-NLS-1$
            GET_DECLARING_TYPE_NAME.setAccessible(true);
            GET_ELEMENT_HANDLE = BytecodeSearchEntry.class.getDeclaredMethod("getElementHandle"); //$NON-NLS-1$
            GET_ELEMENT_HANDLE.setAccessible(true);
            GET_KIND = BytecodeSearchEntry.class.getDeclaredMethod("getKind"); //$NON-NLS-1$
            GET_KIND.setAccessible(true);
            IS_DECLARATION = BytecodeSearchEntry.class.getDeclaredMethod("isDeclaration"); //$NON-NLS-1$
            IS_DECLARATION.setAccessible(true);
            GET_ACCESS = BytecodeSearchEntry.class.getDeclaredMethod("getAccess"); //$NON-NLS-1$
            GET_ACCESS.setAccessible(true);
            GET_TYPE_CATEGORY = BytecodeSearchEntry.class.getDeclaredMethod("getTypeCategory"); //$NON-NLS-1$
            GET_TYPE_CATEGORY.setAccessible(true);

            Class<?> jarIndexClass = BytecodeSearchIndex.JarIndex.class;
            JAR_INDEX_CTOR = jarIndexClass.getDeclaredConstructor(
                    File.class, Path.class, String.class, List.class, int[].class);
            JAR_INDEX_CTOR.setAccessible(true);
            MAPPED_THRESHOLD_FIELD = jarIndexClass.getDeclaredField("MAPPED_THRESHOLD"); //$NON-NLS-1$
            MAPPED_THRESHOLD_FIELD.setAccessible(true);
            JAR_INDEX_CLOSE = jarIndexClass.getDeclaredMethod("close"); //$NON-NLS-1$
            JAR_INDEX_CLOSE.setAccessible(true);
            JAR_INDEX_MATCHES = jarIndexClass.getDeclaredMethod("matches", File.class); //$NON-NLS-1$
            JAR_INDEX_MATCHES.setAccessible(true);
            JAR_INDEX_ENTRY_COUNT = jarIndexClass.getDeclaredMethod("entryCount"); //$NON-NLS-1$
            JAR_INDEX_ENTRY_COUNT.setAccessible(true);
            JAR_INDEX_ENTRIES = jarIndexClass.getDeclaredField("entries"); //$NON-NLS-1$
            JAR_INDEX_ENTRIES.setAccessible(true);
            ESTIMATE_BYTES = jarIndexClass.getDeclaredMethod("estimateBytes", List.class); //$NON-NLS-1$
            ESTIMATE_BYTES.setAccessible(true);
            STRING_ESTIMATE = jarIndexClass.getDeclaredMethod("stringEstimate", String.class); //$NON-NLS-1$
            STRING_ESTIMATE.setAccessible(true);
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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

    private void deleteQuietly(Path file) throws ReflectiveOperationException {
        DELETE_QUIETLY.invoke(null, file);
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

    private String elementHandle(Object entry) throws ReflectiveOperationException {
        return (String) GET_ELEMENT_HANDLE.invoke(entry);
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

    private Object newJarIndex(File jar, Path cache, String rootHandle,
            List<BytecodeSearchEntry> entries, int[] counts) throws ReflectiveOperationException {
        return JAR_INDEX_CTOR.newInstance(jar, cache, rootHandle, entries, counts);
    }

    private void jarIndexClose(Object index) throws ReflectiveOperationException {
        JAR_INDEX_CLOSE.invoke(index);
    }

    private boolean jarIndexMatches(Object index, File jar) throws ReflectiveOperationException {
        return (boolean) JAR_INDEX_MATCHES.invoke(index, jar);
    }

    private int jarIndexEntryCount(Object index) throws ReflectiveOperationException {
        return (int) JAR_INDEX_ENTRY_COUNT.invoke(index);
    }

    private Object jarIndexEntries(Object index) throws ReflectiveOperationException {
        return JAR_INDEX_ENTRIES.get(index);
    }

    private long estimateBytes(List<BytecodeSearchEntry> entries) throws ReflectiveOperationException {
        return (long) ESTIMATE_BYTES.invoke(null, entries);
    }

    private long stringEstimate(String s) throws ReflectiveOperationException {
        return (long) STRING_ESTIMATE.invoke(null, s);
    }

    /** Builds a 128-byte (HEADER_SIZE) big-endian header for pre-creating test segment files. */
    private static byte[] buildFakeHeader(int magic, int version, long lastModified, long jarLength) {
        byte[] h = new byte[128];
        putInt(h, 0, magic);
        putInt(h, 4, version);
        putLong(h, 8, lastModified);
        putLong(h, 16, jarLength);
        return h;
    }

    private static void putInt(byte[] buf, int off, int v) {
        buf[off]     = (byte) (v >>> 24);
        buf[off + 1] = (byte) (v >>> 16);
        buf[off + 2] = (byte) (v >>> 8);
        buf[off + 3] = (byte)  v;
    }

    private static void putLong(byte[] buf, int off, long v) {
        putInt(buf, off,     (int) (v >>> 32));
        putInt(buf, off + 4, (int)  v);
    }

    // -------------------------------------------------------------------------
    // Original tests
    // -------------------------------------------------------------------------

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

        long bsixCount;
        try (Stream<Path> s = Files.list(cacheDir)) {
            bsixCount = s.filter(p -> p.getFileName().toString().endsWith(".bsix")).count(); //$NON-NLS-1$
        }

        Object second = openOrCreate(entries, counts);
        storeClose(second);

        long bsixCountAfter;
        try (Stream<Path> s = Files.list(cacheDir)) {
            bsixCountAfter = s.filter(p -> p.getFileName().toString().endsWith(".bsix")).count(); //$NON-NLS-1$
        }
        assertEquals("No new segment file should be written on second call", bsixCount, bsixCountAfter); //$NON-NLS-1$
    }

    @Test
    public void differentRootsGetDifferentSegmentFiles() throws Exception {
        Path seg1 = (Path) SEGMENT_PATH.invoke(null, cacheDir, fakeJar, "=ProjectA/src"); //$NON-NLS-1$
        Path seg2 = (Path) SEGMENT_PATH.invoke(null, cacheDir, fakeJar, "=ProjectB/src"); //$NON-NLS-1$
        assertNotEquals("Different roots for the same jar must produce different segment paths", seg1, seg2); //$NON-NLS-1$
    }

    @Test
    public void obsoleteSegmentsArePrunedOnWrite() throws Exception {
        // Compute the hash prefix of the current segment path and create a fake stale sibling
        Path currentSeg = segmentPath();
        String segName = currentSeg.getFileName().toString();
        // "bsi-{hash32}-" is the first 37 chars; append a distinct fake timestamp/length
        Path staleSeg = cacheDir.resolve(segName.substring(0, 37) + "0-0.bsix"); //$NON-NLS-1$
        Files.write(staleSeg, new byte[0]);

        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("New", "com.New", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts = {1};

        Object store = openOrCreate(entries, counts);
        storeClose(store);

        assertFalse("Stale segment must be pruned after new write", Files.exists(staleSeg)); //$NON-NLS-1$
        assertTrue("New segment must exist", Files.exists(currentSeg)); //$NON-NLS-1$
    }

    @Test
    public void obsoleteSegmentsArePrunedOnReuse() throws Exception {
        // Write the segment once so the valid file exists
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Cached", "com.Cached", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts = {1};
        Object first = openOrCreate(entries, counts);
        storeClose(first);

        // Simulate a stale sibling that survived a previous failed prune (e.g. Windows lock)
        Path currentSeg = segmentPath();
        String segName = currentSeg.getFileName().toString();
        Path staleSeg = cacheDir.resolve(segName.substring(0, 37) + "0-0.bsix"); //$NON-NLS-1$
        Files.write(staleSeg, new byte[0]);

        // Second openOrCreate hits the cache-hit (reuse) path and should prune the stale sibling
        Object second = openOrCreate(entries, counts);
        storeClose(second);

        assertFalse("Stale sibling must be pruned on cache reuse", Files.exists(staleSeg)); //$NON-NLS-1$
        assertTrue("Current segment must still exist", Files.exists(currentSeg)); //$NON-NLS-1$
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

    // -------------------------------------------------------------------------
    // New tests: additional MappedEntryStore validation paths
    // -------------------------------------------------------------------------

    @Test
    public void truncatedExistingFileTriggersRebuild() throws Exception {
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Tiny", "com.Tiny", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts = {1};

        // Pre-create a file smaller than HEADER_SIZE (120 bytes) → headerOk() size check
        Path seg = segmentPath();
        Files.write(seg, new byte[10]);

        Object rebuilt = openOrCreate(entries, counts);
        try {
            assertEquals(1, storeSize(rebuilt));
            assertEquals("Tiny", name(storeEntry(rebuilt, 0))); //$NON-NLS-1$
        } finally {
            storeClose(rebuilt);
        }
    }

    @Test
    public void badVersionInExistingFileTriggersRebuild() throws Exception {
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Foo", "com.Foo", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts = {1};

        // Pre-create a file with correct magic but wrong version (99)
        Path seg = segmentPath();
        Files.write(seg, buildFakeHeader(0x42534558, 99, fakeJar.lastModified(), fakeJar.length()));

        // openOrCreate must detect the bad version, delete and rewrite
        Object rebuilt = openOrCreate(entries, counts);
        try {
            assertEquals(1, storeSize(rebuilt));
            assertEquals("Foo", name(storeEntry(rebuilt, 0))); //$NON-NLS-1$
        } finally {
            storeClose(rebuilt);
        }
    }

    @Test
    public void contentFingerprintMismatchTriggersRebuild() throws Exception {
        // Write initial segment with one entry
        List<BytecodeSearchEntry> original = List.of(
                makeEntry("Original", "com.Original", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts = {1};
        Object first = openOrCreate(original, counts);
        storeClose(first);

        // Call again with same entry count but a different entry (simulates same-metadata JAR replacement)
        List<BytecodeSearchEntry> updated = List.of(
                makeEntry("Updated", "com.Updated", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        Object rebuilt = openOrCreate(updated, counts);
        try {
            assertEquals(1, storeSize(rebuilt));
            assertEquals("Updated", name(storeEntry(rebuilt, 0))); //$NON-NLS-1$
        } finally {
            storeClose(rebuilt);
        }
    }

    @Test
    public void occurrenceCountMismatchTriggersRebuild() throws Exception {
        // Write initial segment with occurrenceCount=1
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Same", "com.Same", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts1 = {1};
        Object first = openOrCreate(entries, counts1);
        storeClose(first);

        // Same entry, same handle, but different occurrence count
        int[] counts2 = {99};
        Object rebuilt = openOrCreate(entries, counts2);
        try {
            // The rebuilt store must reflect the new occurrence count (fingerprint differs)
            assertEquals(1, storeSize(rebuilt));
            assertEquals("Same", name(storeEntry(rebuilt, 0))); //$NON-NLS-1$
        } finally {
            storeClose(rebuilt);
        }
    }

    @Test
    public void staleEntryCountInSegmentFileTriggersRebuild() throws Exception {
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Stale", "com.Stale", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts = {1};

        // Pre-create a file with correct magic + version + jar metadata but entryCount=0 (stale)
        Path seg = segmentPath();
        Files.write(seg, buildFakeHeader(0x42534558, 2, fakeJar.lastModified(), fakeJar.length()));

        Object rebuilt = openOrCreate(entries, counts);
        try {
            assertEquals(1, storeSize(rebuilt));
            assertEquals("Stale", name(storeEntry(rebuilt, 0))); //$NON-NLS-1$
        } finally {
            storeClose(rebuilt);
        }
    }

    @Test
    public void jarMetadataMismatchInExistingFileTriggersRebuild() throws Exception {
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Bar", "com.Bar", "com", Kind.TYPE, Access.READ, TypeCategory.INTERFACE));
        int[] counts = {1};

        // Pre-create a file with correct magic + version but wrong lastModified (999L)
        Path seg = segmentPath();
        Files.write(seg, buildFakeHeader(0x42534558, 2, 999L, fakeJar.length()));

        Object rebuilt = openOrCreate(entries, counts);
        try {
            assertEquals(1, storeSize(rebuilt));
            assertEquals(Access.READ, access(storeEntry(rebuilt, 0)));
        } finally {
            storeClose(rebuilt);
        }
    }

    // -------------------------------------------------------------------------
    // New tests: additional field/enum coverage
    // -------------------------------------------------------------------------

    @Test
    public void nullElementHandleEntry() throws Exception {
        BytecodeSearchEntry entry = new BytecodeSearchEntry(Kind.METHOD, false,
                BytecodeSearchEntry.elementReference(null, null),
                BytecodeSearchEntry.symbolReference("doWork", "com.Svc.doWork", "com.Svc", null), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                Access.NONE, TypeCategory.UNKNOWN);
        int[] counts = {1};

        Object store = openOrCreate(List.of(entry), counts);
        try {
            Object e = storeEntry(store, 0);
            assertEquals("doWork", name(e)); //$NON-NLS-1$
            assertNull("Null element handle should round-trip as null", elementHandle(e)); //$NON-NLS-1$
            assertFalse("isDeclaration=false should round-trip correctly", isDeclaration(e)); //$NON-NLS-1$
        } finally {
            storeClose(store);
        }
    }

    @Test
    public void stringCacheHitReturnsSameValue() throws Exception {
        // Calling entry() twice for the same id exercises the cache-hit path in str() and handle()
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Cached", "com.Cached", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts = {1};

        Object store = openOrCreate(entries, counts);
        try {
            Object e1 = storeEntry(store, 0);
            Object e2 = storeEntry(store, 0); // second call → cache hit
            assertEquals(name(e1), name(e2));
            assertEquals(qualifiedName(e1), qualifiedName(e2));
            assertEquals(elementHandle(e1), elementHandle(e2));
        } finally {
            storeClose(store);
        }
    }

    @Test
    public void allEnumValuesRoundTrip() throws Exception {
        // Covers remaining Kind values (CONSTRUCTOR, PACKAGE, MODULE),
        // remaining Access value (READ_WRITE), and remaining TypeCategory (ANNOTATION)
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("MyClass", "p.MyClass", "p", Kind.CONSTRUCTOR, Access.READ_WRITE, TypeCategory.ANNOTATION),
                makeEntry("p.example", "p.example", null, Kind.PACKAGE, Access.NONE, TypeCategory.UNKNOWN),
                makeEntry("my.module", "my.module", null, Kind.MODULE, Access.NONE, TypeCategory.UNKNOWN));
        int[] counts = {1, 1, 1};

        Object store = openOrCreate(entries, counts);
        try {
            assertEquals(Kind.CONSTRUCTOR, kind(storeEntry(store, 0)));
            assertEquals(Access.READ_WRITE, access(storeEntry(store, 0)));
            assertEquals(TypeCategory.ANNOTATION, typeCategory(storeEntry(store, 0)));
            assertEquals(Kind.PACKAGE, kind(storeEntry(store, 1)));
            assertEquals(Kind.MODULE, kind(storeEntry(store, 2)));
        } finally {
            storeClose(store);
        }
    }

    // -------------------------------------------------------------------------
    // New tests: utility method coverage
    // -------------------------------------------------------------------------

    @Test
    public void deleteQuietlyOnNonExistentFile() throws Exception {
        Path nonExistent = cacheDir.resolve("no-such-file.bsix"); //$NON-NLS-1$
        assertFalse(Files.exists(nonExistent));
        deleteQuietly(nonExistent); // must not throw
        assertFalse(Files.exists(nonExistent));
    }

    // -------------------------------------------------------------------------
    // New tests: BytecodeSearchIndex.JarIndex coverage
    // -------------------------------------------------------------------------

    @Test
    public void stringEstimateReturnsZeroForNull() throws Exception {
        assertEquals(0L, stringEstimate(null));
        assertEquals(48L + (long) "hello".length() * 2L, stringEstimate("hello")); //$NON-NLS-1$
    }

    @Test
    public void estimateBytesReturnsPositiveValue() throws Exception {
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Foo", "com.Foo", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        assertTrue(estimateBytes(entries) > 0);
    }

    @Test
    public void jarIndexWithSmallJarUsesHeapStore() throws Exception {
        // Estimate is small → createEntryStore picks HeapEntryStore; covers estimateBytes() call path
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Small", "com.Small", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts = {1};

        Object jarIndex = newJarIndex(fakeJar, cacheDir, ROOT_HANDLE, entries, counts);
        try {
            assertEquals(1, jarIndexEntryCount(jarIndex));
            assertFalse("Small jar should use HeapEntryStore", STORE_CLASS.isInstance(jarIndexEntries(jarIndex))); //$NON-NLS-1$
            assertTrue(jarIndexMatches(jarIndex, fakeJar));
        } finally {
            jarIndexClose(jarIndex);
        }
    }

    @Test
    public void jarIndexUsesMappedStoreWhenAboveThreshold() throws Exception {
        // Temporarily lower MAPPED_THRESHOLD to 0 so any estimate triggers the mapped path
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("BigClass", "com.BigClass", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts = {1};

        long old = MAPPED_THRESHOLD_FIELD.getLong(null);
        MAPPED_THRESHOLD_FIELD.setLong(null, 0L);
        try {
            Object jarIndex = newJarIndex(fakeJar, cacheDir, ROOT_HANDLE, entries, counts);
            try {
                assertTrue("Above threshold: should use MappedEntryStore", //$NON-NLS-1$
                        STORE_CLASS.isInstance(jarIndexEntries(jarIndex)));
                assertEquals(1, jarIndexEntryCount(jarIndex));
            } finally {
                jarIndexClose(jarIndex);
            }
        } finally {
            MAPPED_THRESHOLD_FIELD.setLong(null, old);
        }
    }

    @Test
    public void jarIndexFallsBackToHeapOnMappedStoreFailure() throws Exception {
        // Non-existent parent dir → openOrCreate() throws NoSuchFileException →
        // createEntryStore() catches it and falls back to HeapEntryStore
        Path parentDir = Files.createTempDirectory("bsix-parent"); //$NON-NLS-1$
        Path badCacheDir = parentDir.resolve("sub"); // sub directory does not exist //$NON-NLS-1$

        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("Fallback", "com.Fallback", "com", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts = {1};

        long old = MAPPED_THRESHOLD_FIELD.getLong(null);
        MAPPED_THRESHOLD_FIELD.setLong(null, 0L);
        try {
            Object jarIndex = newJarIndex(fakeJar, badCacheDir, ROOT_HANDLE, entries, counts);
            try {
                assertFalse("Should fall back to HeapEntryStore on I/O failure", //$NON-NLS-1$
                        STORE_CLASS.isInstance(jarIndexEntries(jarIndex)));
                assertEquals(1, jarIndexEntryCount(jarIndex));
            } finally {
                jarIndexClose(jarIndex);
            }
        } finally {
            MAPPED_THRESHOLD_FIELD.setLong(null, old);
            Files.deleteIfExists(parentDir);
        }
    }

    @Test
    public void jarIndexMatchesOnSameJarButNotOnDifferentMetadata() throws Exception {
        List<BytecodeSearchEntry> entries = List.of(
                makeEntry("M", "p.M", "p", Kind.TYPE, Access.NONE, TypeCategory.CLASS));
        int[] counts = {1};

        Object jarIndex = newJarIndex(fakeJar, null, ROOT_HANDLE, entries, counts);
        try {
            assertTrue("Same jar: matches() must return true", jarIndexMatches(jarIndex, fakeJar)); //$NON-NLS-1$

            // A non-existent file has lastModified()=0 which differs from fakeJar.lastModified()
            File ghostJar = cacheDir.resolve("ghost.jar").toFile(); //$NON-NLS-1$
            assertFalse("Non-existent jar (lastModified=0) must not match", //$NON-NLS-1$
                    jarIndexMatches(jarIndex, ghostJar));
        } finally {
            jarIndexClose(jarIndex);
        }
    }
}
