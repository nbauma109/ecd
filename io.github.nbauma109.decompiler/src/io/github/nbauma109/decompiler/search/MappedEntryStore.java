/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Access;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.TypeCategory;
import io.github.nbauma109.decompiler.util.Logger;

/**
 * Off-heap entry store backed by a memory-mapped file.
 *
 * <h3>File format</h3>
 * <pre>
 * Header (120 bytes, big-endian):
 *   [0]   int  magic            = 0x42534558
 *   [4]   int  version          = 1
 *   [8]   long jarLastModified
 *   [16]  long jarLength
 *   [24]  int  entryCount
 *   [28]  int  stringCount
 *   [32]  int  handleCount
 *   [36]  int  reserved         = 0
 *   [40]  long kindAndFlagsOff
 *   [48]  long typeCategoryIdsOff
 *   [56]  long elementHandleIdsOff
 *   [64]  long nameIdsOff
 *   [72]  long qualifiedNameIdsOff
 *   [80]  long declaringTypeNameIdsOff
 *   [88]  long descriptorIdsOff
 *   [96]  long occurrenceCountsOff
 *   [104] long stringsOff   -- int[stringCount] byte-lengths, then concatenated UTF-8
 *   [112] long handlesOff   -- int[handleCount] byte-lengths, then concatenated UTF-8
 *
 * Fixed-width sections (layout computed at write time):
 *   byte[entryCount]   kindAndFlags        (padded to 4-byte boundary)
 *   byte[entryCount]   typeCategoryIds     (padded to 4-byte boundary)
 *   int[entryCount]    elementHandleIds
 *   int[entryCount]    nameIds
 *   int[entryCount]    qualifiedNameIds
 *   int[entryCount]    declaringTypeNameIds
 *   int[entryCount]    descriptorIds
 *   int[entryCount]    occurrenceCounts
 *
 * Variable-width sections:
 *   int[stringCount]   string byte-lengths
 *   byte[]             string UTF-8 data (all strings concatenated)
 *   int[handleCount]   handle byte-lengths
 *   byte[]             handle UTF-8 data (all handles concatenated)
 * </pre>
 *
 * Anonymous element fallbacks are not persisted; {@link BytecodeSearchEntry#getElement()}
 * falls back to {@code JavaCore.create(handle)} which is the standard resolution path.
 */
final class MappedEntryStore implements EntryStore {

    private static final int MAGIC = 0x42534558;
    private static final int VERSION = 1;
    private static final int HEADER_SIZE = 120;
    private static final int NULL_ID = HeapEntryStore.NULL_ID;

    private final FileChannel channel;
    private final MappedByteBuffer buf;
    private final int entryCount;
    private final long kindAndFlagsOff;
    private final long typeCategoryIdsOff;
    private final long elementHandleIdsOff;
    private final long nameIdsOff;
    private final long qualifiedNameIdsOff;
    private final long declaringTypeNameIdsOff;
    private final long descriptorIdsOff;
    private final long occurrenceCountsOff;
    private final int[] stringsDataOffsets; // [i]=start, [count]=end (sentinel), no buf read needed
    private final int[] handlesDataOffsets;
    private final String[] stringCache;
    private final String[] handleCache;

    private record SectionLayout(long kindAndFlagsOff, long typeCategoryIdsOff, long elementHandleIdsOff,
            long nameIdsOff, long qualifiedNameIdsOff, long declaringTypeNameIdsOff,
            long descriptorIdsOff, long occurrenceCountsOff) {}

    private MappedEntryStore(FileChannel channel, MappedByteBuffer buf, int entryCount,
            SectionLayout layout, int[] stringsDataOffsets, int[] handlesDataOffsets) {
        this.channel = channel;
        this.buf = buf;
        this.entryCount = entryCount;
        this.kindAndFlagsOff = layout.kindAndFlagsOff();
        this.typeCategoryIdsOff = layout.typeCategoryIdsOff();
        this.elementHandleIdsOff = layout.elementHandleIdsOff();
        this.nameIdsOff = layout.nameIdsOff();
        this.qualifiedNameIdsOff = layout.qualifiedNameIdsOff();
        this.declaringTypeNameIdsOff = layout.declaringTypeNameIdsOff();
        this.descriptorIdsOff = layout.descriptorIdsOff();
        this.occurrenceCountsOff = layout.occurrenceCountsOff();
        this.stringsDataOffsets = stringsDataOffsets;
        this.handlesDataOffsets = handlesDataOffsets;
        this.stringCache = new String[stringsDataOffsets.length - 1];
        this.handleCache = new String[handlesDataOffsets.length - 1];
    }

    /**
     * Returns a cached MappedEntryStore for {@code jar} if one exists, or writes a new segment
     * file and maps it. Throws {@link IOException} on any I/O failure; the caller is responsible
     * for deleting the segment file and falling back to {@link HeapEntryStore}.
     * <p>
     * The segment filename encodes the root handle, jar path, lastModified, and length so that
     * each jar version gets its own file and no active mapping is ever replaced.
     */
    static MappedEntryStore openOrCreate(File jar, Path cacheDir,
            List<BytecodeSearchEntry> entries, int[] counts, String rootHandle) throws IOException {
        Path file = segmentPath(cacheDir, jar, rootHandle);
        if (Files.exists(file)) {
            if (headerOk(file, jar, entries.size())) {
                try {
                    MappedEntryStore store = map(file, jar);
                    if (store.matchesSample(entries)) {
                        // Retry pruning stale siblings on reuse: by this point any previously
                        // published mapping for an older version may have been GC'd on Windows.
                        pruneOldSegments(file, cacheDir);
                        return store;
                    }
                    // Content fingerprint mismatch (e.g. same-metadata JAR replacement)
                    store.close();
                    deleteQuietly(file);
                } catch (IOException | RuntimeException e) {
                    Logger.debug(e);
                    deleteQuietly(file);
                }
            } else {
                // Bad header detected via sequential read — safe to delete without mapping
                deleteQuietly(file);
            }
        }
        write(file, jar, entries, counts);
        pruneOldSegments(file, cacheDir);
        return map(file, jar);
    }

    /**
     * Validates the header of an existing segment file via a sequential read, without creating
     * a memory mapping. On Windows, an invalid mapped file cannot be deleted while the
     * {@link java.nio.MappedByteBuffer} is alive; this pre-check avoids that situation.
     * Also validates {@code entryCount} so a zeroed or stale table cannot silently return
     * garbage data when the postings are looked up against the current entry list.
     */
    private static boolean headerOk(Path file, File jar, int expectedEntryCount) {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            if (ch.size() < HEADER_SIZE) {
                return false;
            }
            ByteBuffer h = ByteBuffer.allocate(28); // magic + version + lastModified + length + entryCount
            h.order(ByteOrder.BIG_ENDIAN);
            while (h.hasRemaining()) {
                if (ch.read(h) <= 0) {
                    return false;
                }
            }
            h.flip();
            return h.getInt() == MAGIC && h.getInt() == VERSION
                    && h.getLong() == jar.lastModified() && h.getLong() == jar.length()
                    && h.getInt() == expectedEntryCount;
        } catch (IOException e) {
            return false;
        }
    }

    static Path segmentPath(Path cacheDir, File jar, String rootHandle) {
        String hash = sha256Hex(rootHandle + "|" + jar.getAbsolutePath()).substring(0, 32); //$NON-NLS-1$
        return cacheDir.resolve("bsi-" + hash + "-" + jar.lastModified() + "-" + jar.length() + ".bsix"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    private static void write(Path file, File jar, List<BytecodeSearchEntry> entries, int[] counts)
            throws IOException {
        // Build dictionaries and column arrays (transient; GC'd after mapping)
        HeapEntryStore.Dictionary strings = new HeapEntryStore.Dictionary();
        HeapEntryStore.ElementDictionary handles = new HeapEntryStore.ElementDictionary();
        int n = entries.size();
        byte[] kindAndFlags = new byte[n];
        byte[] typeCategoryIds = new byte[n];
        int[] handleIds = new int[n];
        int[] nameIds = new int[n];
        int[] qualifiedNameIds = new int[n];
        int[] declaringTypeNameIds = new int[n];
        int[] descriptorIds = new int[n];

        for (int i = 0; i < n; i++) {
            BytecodeSearchEntry e = entries.get(i);
            kindAndFlags[i] = HeapEntryStore.kindAndFlags(e);
            typeCategoryIds[i] = (byte) e.getTypeCategory().ordinal();
            handleIds[i] = handles.id(e.getElementHandle(), e.getAnonymousElementFallback());
            nameIds[i] = strings.id(e.getName());
            qualifiedNameIds[i] = strings.id(e.getQualifiedName());
            declaringTypeNameIds[i] = strings.id(e.getDeclaringTypeName());
            descriptorIds[i] = strings.id(e.getDescriptor());
        }

        byte[][] stringBytes = encodeAll(strings.values());
        byte[][] handleBytes = encodeAll(handles.handles());

        int sc = stringBytes.length;
        int hc = handleBytes.length;

        // Layout
        long off = HEADER_SIZE;
        long kindAndFlagsOff = off; off += pad4(n);
        long typeCategoryIdsOff = off; off += pad4(n);
        long elementHandleIdsOff = off; off += (long) n * 4;
        long nameIdsOff = off; off += (long) n * 4;
        long qualifiedNameIdsOff = off; off += (long) n * 4;
        long declaringTypeNameIdsOff = off; off += (long) n * 4;
        long descriptorIdsOff = off; off += (long) n * 4;
        long occurrenceCountsOff = off; off += (long) n * 4;
        long stringsOff = off; off += (long) sc * 4 + totalBytes(stringBytes);
        long handlesOff = off; // remaining = hc*4 + totalBytes(handleBytes)

        Path tmp = file.resolveSibling(file.getFileName() + ".tmp"); //$NON-NLS-1$
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(tmp,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE), 65536)) {
            // Header
            writeInt(os, MAGIC);
            writeInt(os, VERSION);
            writeLong(os, jar.lastModified());
            writeLong(os, jar.length());
            writeInt(os, n);
            writeInt(os, sc);
            writeInt(os, hc);
            writeInt(os, 0); // reserved
            writeLong(os, kindAndFlagsOff);
            writeLong(os, typeCategoryIdsOff);
            writeLong(os, elementHandleIdsOff);
            writeLong(os, nameIdsOff);
            writeLong(os, qualifiedNameIdsOff);
            writeLong(os, declaringTypeNameIdsOff);
            writeLong(os, descriptorIdsOff);
            writeLong(os, occurrenceCountsOff);
            writeLong(os, stringsOff);
            writeLong(os, handlesOff);

            // kindAndFlags (padded)
            os.write(kindAndFlags);
            writePad(os, pad4(n) - n);
            // typeCategoryIds (padded)
            os.write(typeCategoryIds);
            writePad(os, pad4(n) - n);
            // int columns
            writeIntArray(os, handleIds);
            writeIntArray(os, nameIds);
            writeIntArray(os, qualifiedNameIds);
            writeIntArray(os, declaringTypeNameIds);
            writeIntArray(os, descriptorIds);
            writeIntArray(os, counts);
            // strings section: lengths then data
            for (byte[] s : stringBytes) {
                writeInt(os, s.length);
            }
            for (byte[] s : stringBytes) {
                os.write(s);
            }
            // handles section: lengths then data
            for (byte[] h : handleBytes) {
                writeInt(os, h.length);
            }
            for (byte[] h : handleBytes) {
                os.write(h);
            }
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // -------------------------------------------------------------------------
    // Map / open
    // -------------------------------------------------------------------------

    private static MappedEntryStore map(Path file, File jar) throws IOException {
        FileChannel ch = FileChannel.open(file, StandardOpenOption.READ);
        try {
            MappedByteBuffer b = ch.map(FileChannel.MapMode.READ_ONLY, 0L, ch.size());
            b.order(ByteOrder.BIG_ENDIAN);

            if (b.getInt(0) != MAGIC) {
                throw new IOException("Bad magic in " + file); //$NON-NLS-1$
            }
            if (b.getInt(4) != VERSION) {
                throw new IOException("Unsupported version in " + file); //$NON-NLS-1$
            }
            if (b.getLong(8) != jar.lastModified() || b.getLong(16) != jar.length()) {
                throw new IOException("Jar metadata mismatch for " + file); //$NON-NLS-1$
            }

            int entryCount = b.getInt(24);
            int stringCount = b.getInt(28);
            int handleCount = b.getInt(32);

            long kindAndFlagsOff       = b.getLong(40);
            long typeCategoryIdsOff    = b.getLong(48);
            long elementHandleIdsOff   = b.getLong(56);
            long nameIdsOff            = b.getLong(64);
            long qualifiedNameIdsOff   = b.getLong(72);
            long declaringTypeNameIdsOff = b.getLong(80);
            long descriptorIdsOff      = b.getLong(88);
            long occurrenceCountsOff   = b.getLong(96);
            long stringsOff            = b.getLong(104);
            long handlesOff            = b.getLong(112);

            int[] stringsDataOffsets = buildDataOffsets(b, stringsOff, stringCount);
            int[] handlesDataOffsets = buildDataOffsets(b, handlesOff, handleCount);

            return new MappedEntryStore(ch, b, entryCount,
                    new SectionLayout(kindAndFlagsOff, typeCategoryIdsOff, elementHandleIdsOff,
                            nameIdsOff, qualifiedNameIdsOff, declaringTypeNameIdsOff,
                            descriptorIdsOff, occurrenceCountsOff),
                    stringsDataOffsets, handlesDataOffsets);
        } catch (Exception e) {
            ch.close();
            throw e;
        }
    }

    /**
     * Builds a sentinel-terminated array of absolute buffer positions for the UTF-8 data of each
     * entry. {@code result[i]} is the start of entry i's bytes; {@code result[count]} is the
     * end of the last entry, so the length of entry i is {@code result[i+1] - result[i]}.
     * The section at {@code sectionOff} begins with {@code count} int byte-lengths followed
     * immediately by the concatenated UTF-8 data.
     */
    private static int[] buildDataOffsets(MappedByteBuffer b, long sectionOff, int count) {
        int[] offsets = new int[count + 1]; // +1 sentinel
        int pos = (int) (sectionOff + (long) count * 4);
        for (int i = 0; i < count; i++) {
            offsets[i] = pos;
            pos += b.getInt((int) (sectionOff + (long) i * 4));
        }
        offsets[count] = pos;
        return offsets;
    }

    // -------------------------------------------------------------------------
    // EntryStore
    // -------------------------------------------------------------------------

    @Override
    public int size() {
        return entryCount;
    }

    @Override
    public BytecodeSearchEntry entry(int id) {
        int handleId = buf.getInt((int) (elementHandleIdsOff + (long) id * 4));
        return new BytecodeSearchEntry(
                kind(id),
                declaration(id),
                BytecodeSearchEntry.elementReference(handle(handleId), null),
                BytecodeSearchEntry.symbolReference(
                        str(buf.getInt((int) (nameIdsOff + (long) id * 4))),
                        str(buf.getInt((int) (qualifiedNameIdsOff + (long) id * 4))),
                        str(buf.getInt((int) (declaringTypeNameIdsOff + (long) id * 4))),
                        str(buf.getInt((int) (descriptorIdsOff + (long) id * 4)))),
                access(id),
                typeCategory(id),
                buf.getInt((int) (occurrenceCountsOff + (long) id * 4)));
    }

    /**
     * Compares up to 4 element handles from the mapped segment against the freshly-parsed
     * {@code expected} list. Returns {@code false} when any handle differs, indicating that the
     * JAR content changed despite identical metadata (mtime/size/entryCount), so the segment
     * must be rebuilt rather than reused.
     */
    private boolean matchesSample(List<BytecodeSearchEntry> expected) {
        int samples = Math.min(4, entryCount);
        for (int i = 0; i < samples; i++) {
            int handleId = buf.getInt((int) (elementHandleIdsOff + (long) i * 4));
            if (!Objects.equals(handle(handleId), expected.get(i).getElementHandle())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            Logger.debug(e);
        }
    }

    // -------------------------------------------------------------------------
    // Field accessors
    // -------------------------------------------------------------------------

    private Kind kind(int id) {
        return Kind.values()[buf.get((int) (kindAndFlagsOff + id)) & 0x0F];
    }

    private boolean declaration(int id) {
        return (buf.get((int) (kindAndFlagsOff + id)) & 0x10) != 0;
    }

    private Access access(int id) {
        return Access.values()[(buf.get((int) (kindAndFlagsOff + id)) & 0xFF) >>> 5];
    }

    private TypeCategory typeCategory(int id) {
        return TypeCategory.values()[buf.get((int) (typeCategoryIdsOff + id)) & 0xFF];
    }

    private String str(int id) {
        if (id == NULL_ID) {
            return null;
        }
        String cached = stringCache[id];
        if (cached != null) {
            return cached;
        }
        byte[] utf8 = new byte[stringsDataOffsets[id + 1] - stringsDataOffsets[id]];
        buf.get(stringsDataOffsets[id], utf8);
        cached = new String(utf8, StandardCharsets.UTF_8);
        stringCache[id] = cached;
        return cached;
    }

    private String handle(int id) {
        if (id == NULL_ID) {
            return null;
        }
        String cached = handleCache[id];
        if (cached != null) {
            return cached;
        }
        byte[] utf8 = new byte[handlesDataOffsets[id + 1] - handlesDataOffsets[id]];
        buf.get(handlesDataOffsets[id], utf8);
        cached = new String(utf8, StandardCharsets.UTF_8);
        handleCache[id] = cached;
        return cached;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static byte[][] encodeAll(String[] values) {
        byte[][] result = new byte[values.length][];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i] == null ? new byte[0] : values[i].getBytes(StandardCharsets.UTF_8);
        }
        return result;
    }

    private static long totalBytes(byte[][] arrays) {
        long total = 0L;
        for (byte[] a : arrays) {
            total += a.length;
        }
        return total;
    }

    /** Rounds {@code n} up to the next multiple of 4. */
    private static long pad4(int n) {
        return (n + 3L) & ~3L;
    }

    private static void writePad(OutputStream os, long count) throws IOException {
        for (long i = 0; i < count; i++) {
            os.write(0);
        }
    }

    private static void writeInt(OutputStream os, int v) throws IOException {
        os.write((v >>> 24) & 0xFF);
        os.write((v >>> 16) & 0xFF);
        os.write((v >>> 8) & 0xFF);
        os.write(v & 0xFF);
    }

    private static void writeLong(OutputStream os, long v) throws IOException {
        writeInt(os, (int) (v >>> 32));
        writeInt(os, (int) v);
    }

    private static void writeIntArray(OutputStream os, int[] arr) throws IOException {
        for (int v : arr) {
            writeInt(os, v);
        }
    }

    private static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256") //$NON-NLS-1$
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e); // SHA-256 is always available
        }
    }

    /**
     * Deletes any sibling {@code .bsix} files that share the same root/jar hash prefix as
     * {@code newFile} but have a different (stale) timestamp/length suffix. Called after
     * writing a new segment so disk usage does not grow without bound when JARs are rebuilt.
     */
    private static void pruneOldSegments(Path newFile, Path cacheDir) {
        String name = newFile.getFileName().toString();
        if (name.length() < 37) { // "bsi-" (4) + hash32 (32) + "-" (1)
            return;
        }
        String prefix = name.substring(0, 37); // "bsi-" + 32-char hash + "-"
        try (Stream<Path> stream = Files.list(cacheDir)) {
            stream.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(prefix) && n.endsWith(".bsix") && !p.equals(newFile); //$NON-NLS-1$
            }).forEach(MappedEntryStore::deleteQuietly);
        } catch (IOException e) {
            Logger.debug(e);
        }
    }

    static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            Logger.debug(e);
        }
    }
}
