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
import java.util.stream.Stream;
import java.util.zip.CRC32;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Access;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.TypeCategory;
import io.github.nbauma109.decompiler.util.Logger;

/**
 * Off-heap entry store backed by a memory-mapped file.
 *
 * <h3>File format</h3>
 * <pre>
 * Header (124 bytes, big-endian):
 *   [0]   int  magic            = 0x42534558
 *   [4]   int  version          = 4
 *   [8]   long jarLastModified
 *   [16]  long jarLength
 *   [24]  int  entryCount
 *   [28]  int  stringCount
 *   [32]  int  handleCount
 *   [36]  int  fingerprint       -- CRC32 covering all per-entry persisted fields
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
 *   [120] int  bodyCrc32    -- CRC32 over all bytes from offset 124 to end of file
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
 * All JARs are indexed here. Entries with {@code [~} anonymous element handles are stored
 * with a {@code null} {@link org.eclipse.jdt.core.IJavaElement} fallback.
 */
final class MappedEntryStore implements EntryStore {

    private static final int MAGIC = 0x42534558;
    private static final int VERSION = 4;
    private static final int HEADER_SIZE = 124;
    private static final String SEGMENT_EXT = ".bsix"; //$NON-NLS-1$
    private static final int NULL_ID = HeapEntryStore.NULL_ID;

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

    private MappedEntryStore(MappedByteBuffer buf, int entryCount,
            SectionLayout layout, int[] stringsDataOffsets, int[] handlesDataOffsets) {
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
     * for falling back to {@link HeapEntryStore}.
     * <p>
     * The segment filename encodes the root handle, jar path, lastModified, and length so that
     * each jar version gets its own file and no active mapping is ever replaced.
     * When a stale segment cannot be deleted (Windows: MappedByteBuffer keeps the file locked),
     * the new segment is written under a fingerprint-suffixed sibling name so the caller always
     * gets a fresh mapped store. The locked stale file becomes a sibling and is pruned by
     * {@link #pruneOldSegments} once its mapping is released by GC.
     */
    static MappedEntryStore openOrCreate(File jar, Path cacheDir,
            List<BytecodeSearchEntry> entries, int[] counts, String rootHandle) throws IOException {
        Path file = segmentPath(cacheDir, jar, rootHandle);
        if (Files.exists(file)) {
            if (headerOk(file, jar, entries, counts)) {
                try {
                    MappedEntryStore store = map(file, jar);
                    // Retry pruning stale siblings on reuse: by this point any previously
                    // published mapping for an older version may have been GC'd on Windows.
                    pruneOldSegments(file, cacheDir);
                    return store;
                } catch (IOException | RuntimeException e) {
                    Logger.debug(e);
                    deleteQuietly(file);
                }
            } else if (!deleteQuietly(file)) {
                // Stale segment is locked (Windows MappedByteBuffer): fall through to a
                // fingerprint-named sibling so the current caller gets a usable mapped store.
                // pruneOldSegments will clean up the stale file once its mapping is released.
                file = alternateSegmentPath(file, fingerprintEntries(entries, counts));
                if (Files.exists(file) && headerOk(file, jar, entries, counts)) {
                    try {
                        MappedEntryStore store = map(file, jar);
                        pruneOldSegments(file, cacheDir);
                        return store;
                    } catch (IOException | RuntimeException e) {
                        Logger.debug(e);
                        deleteQuietly(file);
                    }
                }
            }
        }
        write(file, jar, entries, counts);
        pruneOldSegments(file, cacheDir);
        return map(file, jar);
    }

    /**
     * Returns an alternate segment path by appending the fingerprint as a hex suffix before
     * {@code .bsix}. Used when the canonical segment path is locked on Windows so that the new
     * segment can be written without touching the active mapping.
     */
    private static Path alternateSegmentPath(Path file, int fingerprint) {
        String name = file.getFileName().toString();
        String stem = name.substring(0, name.length() - SEGMENT_EXT.length());
        return file.resolveSibling(stem + "-" + Integer.toUnsignedString(fingerprint, 16) + SEGMENT_EXT); //$NON-NLS-1$
    }

    /**
     * Validates the header of an existing segment file via a sequential read, without creating
     * a memory mapping. On Windows, an invalid mapped file cannot be deleted while the
     * {@link java.nio.MappedByteBuffer} is alive; this pre-check avoids that situation.
     * Validates magic, version, jar metadata, entry count, a CRC32 fingerprint of all
     * persisted entry fields, and the section layout: all 8 fixed-width section offsets must
     * exactly match the values computed from entryCount, {@code stringCount} and
     * {@code handleCount} must be non-negative and within bounds derivable from the section
     * offsets, and the file must be large enough to hold both variable-length index arrays.
     */
    private static boolean headerOk(Path file, File jar, List<BytecodeSearchEntry> entries, int[] counts) {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileSize = ch.size();
            if (fileSize < HEADER_SIZE) {
                return false;
            }
            ByteBuffer h = ByteBuffer.allocate(HEADER_SIZE);
            h.order(ByteOrder.BIG_ENDIAN);
            while (h.hasRemaining()) {
                if (ch.read(h) <= 0) {
                    return false;
                }
            }
            h.flip();
            return validateContents(h, fileSize, jar, entries, counts, ch);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean validateContents(ByteBuffer h, long fileSize, File jar,
            List<BytecodeSearchEntry> entries, int[] counts, FileChannel ch) throws IOException {
        if (h.getInt() != MAGIC || h.getInt() != VERSION) return false;
        if (h.getLong() != jar.lastModified() || h.getLong() != jar.length()) return false;
        int n = entries.size();
        if (h.getInt() != n) return false;
        int sc = h.getInt();
        int hc = h.getInt();
        if (sc < 0 || hc < 0 || hc > n) return false;
        if (h.getInt() != fingerprintEntries(entries, counts)) return false;
        if (!validateFixedOffsets(h, n)) return false;
        long stringsOff = h.getLong();
        long handlesOff = h.getLong();
        int storedBodyCrc = h.getInt();
        long minStringsOff = HEADER_SIZE + 2 * pad4(n) + 6L * n * 4;
        if (stringsOff < minStringsOff || stringsOff > fileSize) return false;
        if (handlesOff < stringsOff + (long) sc * 4 || handlesOff > fileSize) return false;
        if (fileSize < handlesOff + (long) hc * 4) return false;
        if (!validateLengthTables(ch, stringsOff, sc, handlesOff, hc, fileSize)) return false;
        return verifyBodyCrc(ch, storedBodyCrc);
    }

    /** Reads the 8 fixed-width section offsets from {@code h} and returns false if any mismatch. */
    private static boolean validateFixedOffsets(ByteBuffer h, int n) {
        long off = HEADER_SIZE;
        if (h.getLong() != off) return false;
        off += pad4(n);
        if (h.getLong() != off) return false;
        off += pad4(n);
        if (h.getLong() != off) return false;
        off += (long) n * 4;
        if (h.getLong() != off) return false;
        off += (long) n * 4;
        if (h.getLong() != off) return false;
        off += (long) n * 4;
        if (h.getLong() != off) return false;
        off += (long) n * 4;
        if (h.getLong() != off) return false;
        off += (long) n * 4;
        return h.getLong() == off;
    }

    /** Reads string and handle length tables; returns false if any length is negative or the sums
     *  don't exactly account for the space between the sections and end-of-file. */
    private static boolean validateLengthTables(FileChannel ch, long stringsOff, int sc,
            long handlesOff, int hc, long fileSize) throws IOException {
        long stringSum = lengthSum(ch, stringsOff, sc);
        if (stringSum < 0 || stringsOff + (long) sc * 4 + stringSum != handlesOff) return false;
        long handleSum = lengthSum(ch, handlesOff, hc);
        return handleSum >= 0 && handlesOff + (long) hc * 4 + handleSum == fileSize;
    }

    /** Reads {@code count} big-endian int lengths from {@code ch} starting at {@code offset};
     *  returns their sum, or -1 if any length is negative or an I/O error occurs. */
    private static long lengthSum(FileChannel ch, long offset, int count) throws IOException {
        if (count == 0) return 0;
        ByteBuffer lens = ByteBuffer.allocate((int) ((long) count * 4));
        lens.order(ByteOrder.BIG_ENDIAN);
        long pos = offset;
        while (lens.hasRemaining()) {
            int r = ch.read(lens, pos);
            if (r <= 0) return -1;
            pos += r;
        }
        lens.flip();
        long sum = 0;
        for (int i = 0; i < count; i++) {
            int len = lens.getInt();
            if (len < 0) return -1;
            sum += len;
        }
        return sum;
    }

    /** Streams body bytes from {@code HEADER_SIZE} to EOF and compares CRC32 to {@code stored}. */
    private static boolean verifyBodyCrc(FileChannel ch, int stored) throws IOException {
        CRC32 crc = new CRC32();
        ByteBuffer chunk = ByteBuffer.allocate(65536);
        ch.position(HEADER_SIZE);
        while (ch.read(chunk) > 0) {
            chunk.flip();
            crc.update(chunk);
            chunk.clear();
        }
        return (int) crc.getValue() == stored;
    }

    private static int fingerprintEntries(List<BytecodeSearchEntry> entries, int[] counts) {
        CRC32 crc = new CRC32();
        byte[] buf4 = new byte[4];
        for (int i = 0; i < entries.size(); i++) {
            BytecodeSearchEntry e = entries.get(i);
            crc.update(HeapEntryStore.kindAndFlags(e));
            crc.update(e.getTypeCategory().ordinal());
            updateInt(crc, buf4, counts[i]);
            updateString(crc, buf4, e.getElementHandle());
            updateString(crc, buf4, e.getName());
            updateString(crc, buf4, e.getQualifiedName());
            updateString(crc, buf4, e.getDeclaringTypeName());
            updateString(crc, buf4, e.getDescriptor());
        }
        return (int) crc.getValue();
    }

    private static void updateInt(CRC32 crc, byte[] buf4, int v) {
        buf4[0] = (byte) (v >>> 24); buf4[1] = (byte) (v >>> 16);
        buf4[2] = (byte) (v >>> 8);  buf4[3] = (byte) v;
        crc.update(buf4);
    }

    private static void updateString(CRC32 crc, byte[] buf4, String s) {
        if (s == null) {
            updateInt(crc, buf4, -1);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        updateInt(crc, buf4, bytes.length);
        crc.update(bytes);
    }

    static Path segmentPath(Path cacheDir, File jar, String rootHandle) {
        String hash = sha256Hex(rootHandle + "|" + jar.getAbsolutePath()).substring(0, 32); //$NON-NLS-1$
        return cacheDir.resolve("bsi-" + hash + "-" + jar.lastModified() + "-" + jar.length() + SEGMENT_EXT); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
            writeInt(os, fingerprintEntries(entries, counts));
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
            writeInt(os, 0); // bodyCrc32 placeholder — patched below after body is written

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
        // Compute CRC32 over the body (bytes from HEADER_SIZE to EOF) and patch into the header.
        try (FileChannel fc = FileChannel.open(tmp, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            CRC32 crc = new CRC32();
            ByteBuffer chunk = ByteBuffer.allocate(65536);
            fc.position(HEADER_SIZE);
            while (fc.read(chunk) > 0) {
                chunk.flip();
                crc.update(chunk);
                chunk.clear();
            }
            ByteBuffer crcBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
            crcBuf.putInt((int) crc.getValue()).flip();
            fc.write(crcBuf, 120);
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
        // Close the channel immediately after mapping: MappedByteBuffer stays valid
        // independently of the channel, so keeping the channel open wastes a file descriptor.
        MappedByteBuffer b;
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            b = ch.map(FileChannel.MapMode.READ_ONLY, 0L, ch.size());
        }
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

        return new MappedEntryStore(b, entryCount,
                new SectionLayout(kindAndFlagsOff, typeCategoryIdsOff, elementHandleIdsOff,
                        nameIdsOff, qualifiedNameIdsOff, declaringTypeNameIdsOff,
                        descriptorIdsOff, occurrenceCountsOff),
                stringsDataOffsets, handlesDataOffsets);
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

    @Override
    public void close() {
        // Channel is closed immediately after map(); MappedByteBuffer is reclaimed by GC.
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
                return n.startsWith(prefix) && n.endsWith(SEGMENT_EXT) && !p.equals(newFile);
            }).forEach(MappedEntryStore::deleteQuietly);
        } catch (IOException e) {
            Logger.debug(e);
        }
    }

    static boolean deleteQuietly(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            Logger.debug(e);
            return false;
        }
    }
}
