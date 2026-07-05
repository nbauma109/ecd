/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Access;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.TypeCategory;

/**
 * Entry store backed by a shared SQLite database.
 * All data (rows and indexes) live on disk; RAM usage is limited to JDBC query results.
 * The connection is owned by {@link BytecodeSearchIndex} and must not be closed here.
 *
 * <p>All repeated strings (names, descriptors, element handles, etc.) are stored once in
 * the {@code strings} table and referenced by integer id in {@code entries}, which keeps
 * the entries table compact even when the same string appears in millions of rows.
 */
final class SqliteEntryStore implements EntryStore {

    static final String PLUGIN_ID = "io.github.nbauma109.decompiler"; //$NON-NLS-1$

    // Columns returned by every SELECT on entries (table alias e, strings aliases s_*).
    // Order must match rowToEntry() column positions 1..11.
    private static final String SELECT_COLS =
            "e.kind, e.declaration, e.access_flags, e.type_category, " + //$NON-NLS-1$
                    "s_eh.value, s_nm.value, s_qn.value, s_dt.value, s_ds.value, e.occurrence_count, s_fb.value"; //$NON-NLS-1$

    private static final String SELECT_FROM =
            "FROM entries e " + //$NON-NLS-1$
                    "LEFT JOIN strings s_eh ON s_eh.id = e.element_handle_id " + //$NON-NLS-1$
                    "LEFT JOIN strings s_nm ON s_nm.id = e.name_id " + //$NON-NLS-1$
                    "LEFT JOIN strings s_qn ON s_qn.id = e.qualified_name_id " + //$NON-NLS-1$
                    "LEFT JOIN strings s_dt ON s_dt.id = e.declaring_type_name_id " + //$NON-NLS-1$
                    "LEFT JOIN strings s_ds ON s_ds.id = e.descriptor_id " + //$NON-NLS-1$
                    "LEFT JOIN strings s_fb ON s_fb.id = e.fallback_handle_id"; //$NON-NLS-1$

    private static final String SELECT_ENTRIES_WHERE =
            "SELECT " + SELECT_COLS + " " + SELECT_FROM + " WHERE "; //$NON-NLS-1$ //$NON-NLS-2$

    private final Connection conn;
    private final Object dbLock;
    private final int jarId;
    private final int size;
    private final boolean ownsConnection;
    private final String indexedRootHandle;
    private final String requestedRootHandle;

    SqliteEntryStore(Connection conn, Object dbLock, int jarId) throws SQLException {
        this(conn, dbLock, jarId, false, null);
    }

    SqliteEntryStore(Connection conn, Object dbLock, int jarId, boolean ownsConnection) throws SQLException {
        this(conn, dbLock, jarId, ownsConnection, null);
    }

    SqliteEntryStore(Connection conn, Object dbLock, int jarId, boolean ownsConnection,
            String requestedRootHandle) throws SQLException {
        this.conn = conn;
        this.dbLock = dbLock;
        this.jarId = jarId;
        this.ownsConnection = ownsConnection;
        this.indexedRootHandle = queryIndexedRootHandle();
        this.requestedRootHandle = requestedRootHandle == null ? indexedRootHandle : requestedRootHandle;
        this.size = querySize();
    }

    static Connection openInMemoryDatabase() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC"); //$NON-NLS-1$
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e); //$NON-NLS-1$
        }
        Connection c = DriverManager.getConnection("jdbc:sqlite::memory:"); //$NON-NLS-1$
        initSchema(c);
        return c;
    }

    static void initSchema(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL"); //$NON-NLS-1$
            stmt.execute("PRAGMA synchronous=NORMAL"); //$NON-NLS-1$
            stmt.execute("PRAGMA foreign_keys=ON"); //$NON-NLS-1$
            // New databases get full auto-vacuum so freed pages are returned to the OS
            // automatically after each DELETE/prune. For existing databases VACUUM is
            // needed to activate the mode (handled by BytecodeSearchIndex.vacuumIfNeeded).
            stmt.execute("PRAGMA auto_vacuum = FULL"); //$NON-NLS-1$
        }
boolean needsReset = hasColumn(conn, "entries", "normalized_name") //$NON-NLS-1$ //$NON-NLS-2$
        || (hasTable(conn, "entries") || hasTable(conn, "jars")) //$NON-NLS-1$ //$NON-NLS-2$
                && (!hasTable(conn, "jar_locations") //$NON-NLS-1$ //$NON-NLS-2$
                        || !hasColumn(conn, "jars", "content_hash")); //$NON-NLS-1$ //$NON-NLS-2$
        if (needsReset) {
            try (var s = conn.createStatement()) {
                s.execute("DROP TABLE IF EXISTS entries"); //$NON-NLS-1$
                s.execute("DROP TABLE IF EXISTS jar_locations"); //$NON-NLS-1$
                s.execute("DROP TABLE IF EXISTS jars"); //$NON-NLS-1$
                s.execute("DROP TABLE IF EXISTS strings"); //$NON-NLS-1$
            }
        }
        try (var stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS strings (
                            id INTEGER PRIMARY KEY,
                            value TEXT NOT NULL UNIQUE
                            )"""); //$NON-NLS-1$
                    stmt.execute("""
                            CREATE TABLE IF NOT EXISTS jars (
                                    id INTEGER PRIMARY KEY,
                                    root_handle TEXT NOT NULL DEFAULT '',
                                    path TEXT NOT NULL,
                                    last_modified INTEGER NOT NULL,
                                    file_length INTEGER NOT NULL,
                                    runtime_version INTEGER NOT NULL DEFAULT 0,
                                    file_crc INTEGER NOT NULL DEFAULT 0,
                                    content_hash TEXT NOT NULL
                                    )"""); //$NON-NLS-1$
                            stmt.execute("""
                                    CREATE UNIQUE INDEX IF NOT EXISTS idx_jars
                                    ON jars(runtime_version, content_hash)"""); //$NON-NLS-1$
                                    stmt.execute("""
                                            CREATE TABLE IF NOT EXISTS jar_locations (
                                                    root_handle TEXT NOT NULL,
                                                    path TEXT NOT NULL,
                                                    last_modified INTEGER NOT NULL,
                                                    file_length INTEGER NOT NULL,
                                                    jar_id INTEGER NOT NULL REFERENCES jars(id) ON DELETE CASCADE,
                                                    PRIMARY KEY(root_handle, path)
                                                    ) WITHOUT ROWID"""); //$NON-NLS-1$
                                    // entries uses integer ids into strings instead of storing text inline,
                                    // cutting per-row size from ~280 bytes (7 text columns) to ~56 bytes (7 ints).
                                    stmt.execute("""
                                            CREATE TABLE IF NOT EXISTS entries (
                                                    id INTEGER PRIMARY KEY,
                                                    jar_id INTEGER NOT NULL REFERENCES jars(id) ON DELETE CASCADE,
                                                    kind INTEGER NOT NULL,
                                                    declaration INTEGER NOT NULL,
                                                    access_flags INTEGER NOT NULL,
                                                    type_category INTEGER NOT NULL,
                                                    element_handle_id INTEGER NOT NULL DEFAULT 0,
                                                    name_id INTEGER NOT NULL DEFAULT 0,
                                                    qualified_name_id INTEGER NOT NULL DEFAULT 0,
                                                    declaring_type_name_id INTEGER NOT NULL DEFAULT 0,
                                                    descriptor_id INTEGER NOT NULL DEFAULT 0,
                                                    occurrence_count INTEGER NOT NULL DEFAULT 1,
                                                    fallback_handle_id INTEGER NOT NULL DEFAULT 0
                                                    )"""); //$NON-NLS-1$
                                            stmt.execute("""
                                                    CREATE INDEX IF NOT EXISTS idx_entries_name
                                                    ON entries(jar_id, kind, name_id)"""); //$NON-NLS-1$
                                                    // Most member entries use the same value for name and
                                                    // qualified_name. Their qualified-name lookup is already
                                                    // covered by idx_entries_name, so do not duplicate those rows
                                                    // in a second, often very large, index. Dropping the old index
                                                    // also migrates existing databases on first startup.
                                                    stmt.execute("DROP INDEX IF EXISTS idx_entries_qname"); //$NON-NLS-1$
                                                    stmt.execute("""
                                                            CREATE INDEX IF NOT EXISTS idx_entries_distinct_qname
                                                            ON entries(jar_id, kind, qualified_name_id)
                                                            WHERE qualified_name_id != name_id"""); //$NON-NLS-1$
        }
    }

    private static boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM pragma_table_info(?) WHERE name = ?")) { //$NON-NLS-1$
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean hasTable(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?")) { //$NON-NLS-1$
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    static void pruneOrphanJarRows(Connection conn, Map<String, Integer> liveLocations) throws SQLException {
        try (PreparedStatement sel = conn.prepareStatement(
                "SELECT root_handle, path, jar_id FROM jar_locations"); //$NON-NLS-1$
                PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM jar_locations WHERE root_handle = ? AND path = ?")) { //$NON-NLS-1$
            try (ResultSet rs = sel.executeQuery()) {
                while (rs.next()) {
                    String rootHandle = rs.getString(1);
                    String path = rs.getString(2);
                    Integer liveJarId = liveLocations.get(rootHandle + '\0' + path);
                    if (liveJarId == null || liveJarId.intValue() != rs.getInt(3)) {
                        del.setString(1, rootHandle);
                        del.setString(2, path);
                        del.addBatch();
                    }
                }
            }
            del.executeBatch();
        }
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM jars WHERE id NOT IN (SELECT jar_id FROM jar_locations)")) { //$NON-NLS-1$
            del.executeUpdate();
        }
    }

    static void pruneOrphanStrings(Connection conn) throws SQLException {
        try (var s = conn.createStatement()) {
            s.execute("""
                    DELETE FROM strings WHERE id NOT IN (
                            SELECT element_handle_id FROM entries
                            UNION SELECT name_id FROM entries
                            UNION SELECT qualified_name_id FROM entries
                            UNION SELECT declaring_type_name_id FROM entries
                            UNION SELECT descriptor_id FROM entries
                            UNION SELECT fallback_handle_id FROM entries
                            )"""); //$NON-NLS-1$
        }
    }

    record JarKey(String rootHandle, String path, long lastModified, long fileLength, int runtimeVersion, long fileCrc,
            String contentHash) {
    }

    record JarRegistration(int jarId, boolean needsIndexing) {
    }

    /**
     * Returns the jar id if the jar is already in the database with matching metadata,
     * or -1 if it needs (re-)indexing.
     */
    static int findJar(Connection conn, Object dbLock, JarKey key) throws SQLException {
        final Object lock = dbLock;
        synchronized (lock) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT l.jar_id FROM jar_locations l JOIN jars j ON j.id = l.jar_id " + //$NON-NLS-1$
                            "WHERE l.root_handle = ? AND l.path = ? AND l.last_modified = ? " + //$NON-NLS-1$
                            "AND l.file_length = ? AND j.runtime_version = ? " + //$NON-NLS-1$
                            "AND j.file_crc = ? AND j.content_hash = ?")) { //$NON-NLS-1$
                ps.setString(1, key.rootHandle());
                ps.setString(2, key.path());
                ps.setLong(3, key.lastModified());
                ps.setLong(4, key.fileLength());
                ps.setInt(5, key.runtimeVersion());
                ps.setLong(6, key.fileCrc());
                ps.setString(7, key.contentHash());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : -1;
                }
            }
        }
    }

    /**
     * Reuses indexed content with the same SHA-256 content hash, or inserts a new canonical
     * jar row. The workspace location is then linked to that canonical row.
     * Must be called inside a write transaction.
     */
    static JarRegistration registerJar(Connection conn, String rootHandle, String path,
            long lastModified, long fileLength, int runtimeVersion, long fileCrc, String contentHash)
            throws SQLException {
        int jarId = -1;
        try (PreparedStatement find = conn.prepareStatement(
                "SELECT id FROM jars WHERE runtime_version = ? AND content_hash = ?")) { //$NON-NLS-1$
            find.setInt(1, runtimeVersion);
            find.setString(2, contentHash);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    jarId = rs.getInt(1);
                }
            }
        }
        boolean needsIndexing = jarId < 0;
        if (needsIndexing) {
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO jars(root_handle, path, last_modified, file_length, runtime_version, file_crc, content_hash) VALUES(?, ?, ?, ?, ?, ?, ?)", //$NON-NLS-1$
                    Statement.RETURN_GENERATED_KEYS)) {
                ins.setString(1, rootHandle);
                ins.setString(2, path);
                ins.setLong(3, lastModified);
                ins.setLong(4, fileLength);
                ins.setInt(5, runtimeVersion);
                ins.setLong(6, fileCrc);
                ins.setString(7, contentHash);
                ins.executeUpdate();
                try (ResultSet keys = ins.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("No generated key for jar row"); //$NON-NLS-1$
                    }
                    jarId = keys.getInt(1);
                }
            }
        }
        try (PreparedStatement link = conn.prepareStatement("""
                INSERT INTO jar_locations(root_handle, path, last_modified, file_length, jar_id)
                        VALUES(?, ?, ?, ?, ?)
                ON CONFLICT(root_handle, path) DO UPDATE SET
                        last_modified = excluded.last_modified,
                        file_length = excluded.file_length,
                        jar_id = excluded.jar_id""")) { //$NON-NLS-1$
            link.setString(1, rootHandle);
            link.setString(2, path);
            link.setLong(3, lastModified);
            link.setLong(4, fileLength);
            link.setInt(5, jarId);
            link.executeUpdate();
        }
        return new JarRegistration(jarId, needsIndexing);
    }

    int jarId() {
        return jarId;
    }

    boolean ownsConnection() {
        return ownsConnection;
    }

    private String queryIndexedRootHandle() throws SQLException {
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT root_handle FROM jars WHERE id = ?")) { //$NON-NLS-1$
                ps.setInt(1, jarId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : ""; //$NON-NLS-1$
                }
            }
        }
    }

    private int querySize() throws SQLException {
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM entries WHERE jar_id = ?")) { //$NON-NLS-1$
                ps.setInt(1, jarId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void collect(Kind kind, String name, String qualifiedName, boolean wildcard,
            EntryStore.EntryConsumer consumer) throws CoreException {
        try {
            if (wildcard) {
                collectAll(kind, consumer);
            } else {
                String searchName = name == null ? "" : name; //$NON-NLS-1$
                String searchQName = qualifiedName == null ? "" : qualifiedName; //$NON-NLS-1$
                if (searchName.equals(searchQName)) {
                    collectByName(kind, searchName, consumer);
                } else {
                    collectByNameOrQName(kind, searchName, searchQName, consumer);
                }
            }
        } catch (SQLException e) {
            throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, e.getMessage(), e));
        }
    }

    private void collectAll(Kind kind, EntryStore.EntryConsumer consumer) throws SQLException, CoreException {
        String sql = SELECT_ENTRIES_WHERE + "e.jar_id = ? AND e.kind = ?"; //$NON-NLS-1$
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jarId);
                ps.setInt(2, kind.ordinal());
                emit(ps, consumer);
            }
        }
    }

    private void collectByName(Kind kind, String searchName, EntryStore.EntryConsumer consumer)
            throws SQLException, CoreException {
        if (StringUtils.isBlank(searchName)) {
            return;
        }
        String sql = SELECT_ENTRIES_WHERE +
                "e.jar_id = ? AND e.kind = ? AND e.name_id IN (SELECT id FROM strings WHERE value = ?)"; //$NON-NLS-1$
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jarId);
                ps.setInt(2, kind.ordinal());
                ps.setString(3, searchName);
                emit(ps, consumer);
            }
        }
    }

    private void collectByNameOrQName(Kind kind, String searchName, String searchQName,
            EntryStore.EntryConsumer consumer) throws SQLException, CoreException {
        boolean hasName = !StringUtils.isBlank(searchName);
        boolean hasQName = !StringUtils.isBlank(searchQName);
        if (!hasName && !hasQName) {
            return;
        }
        if (!hasName) {
            collectByQName(kind, searchQName, consumer);
            return;
        }
        if (!hasQName) {
            collectByName(kind, searchName, consumer);
            return;
        }
        // Split the alternatives so SQLite can use the compact partial qname index.
        // The branches are disjoint, which avoids the temporary table required by UNION.
        String sql = SELECT_ENTRIES_WHERE +
                "e.jar_id = ? AND e.kind = ? AND " + //$NON-NLS-1$
                "e.name_id IN (SELECT id FROM strings WHERE value = ?) " + //$NON-NLS-1$
                "UNION ALL " + SELECT_ENTRIES_WHERE + //$NON-NLS-1$
                "e.jar_id = ? AND e.kind = ? AND e.qualified_name_id = e.name_id AND " + //$NON-NLS-1$
                "e.name_id IN (SELECT id FROM strings WHERE value = ?) " + //$NON-NLS-1$
                "UNION ALL " + SELECT_ENTRIES_WHERE + //$NON-NLS-1$
                "e.jar_id = ? AND e.kind = ? AND e.qualified_name_id != e.name_id AND " + //$NON-NLS-1$
                "e.qualified_name_id IN (SELECT id FROM strings WHERE value = ?) AND " + //$NON-NLS-1$
                "e.name_id NOT IN (SELECT id FROM strings WHERE value = ?)"; //$NON-NLS-1$
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jarId);
                ps.setInt(2, kind.ordinal());
                ps.setString(3, searchName);
                ps.setInt(4, jarId);
                ps.setInt(5, kind.ordinal());
                ps.setString(6, searchQName);
                ps.setInt(7, jarId);
                ps.setInt(8, kind.ordinal());
                ps.setString(9, searchQName);
                ps.setString(10, searchName);
                emit(ps, consumer);
            }
        }
    }

    private void collectByQName(Kind kind, String searchQName, EntryStore.EntryConsumer consumer)
            throws SQLException, CoreException {
        String sql = SELECT_ENTRIES_WHERE +
                "e.jar_id = ? AND e.kind = ? AND e.qualified_name_id = e.name_id AND " + //$NON-NLS-1$
                "e.name_id IN (SELECT id FROM strings WHERE value = ?) " + //$NON-NLS-1$
                "UNION ALL " + SELECT_ENTRIES_WHERE + //$NON-NLS-1$
                "e.jar_id = ? AND e.kind = ? AND e.qualified_name_id != e.name_id AND " + //$NON-NLS-1$
                "e.qualified_name_id IN (SELECT id FROM strings WHERE value = ?)"; //$NON-NLS-1$
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jarId);
                ps.setInt(2, kind.ordinal());
                ps.setString(3, searchQName);
                ps.setInt(4, jarId);
                ps.setInt(5, kind.ordinal());
                ps.setString(6, searchQName);
                emit(ps, consumer);
            }
        }
    }

    private void emit(PreparedStatement ps, EntryStore.EntryConsumer consumer)
            throws SQLException, CoreException {
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                consumer.accept(rowToEntry(rs));
            }
        }
    }

    private BytecodeSearchEntry rowToEntry(ResultSet rs) throws SQLException {
        Kind kind = Kind.values()[rs.getInt(1)];
        boolean declaration = rs.getInt(2) != 0;
        Access access = Access.values()[rs.getInt(3)];
        TypeCategory typeCategory = TypeCategory.values()[rs.getInt(4)];
        String elementHandle = translateHandle(emptyToNull(rs.getString(5)));
        String name = emptyToNull(rs.getString(6));
        String qualifiedName = emptyToNull(rs.getString(7));
        String declaringTypeName = emptyToNull(rs.getString(8));
        String descriptor = emptyToNull(rs.getString(9));
        int occurrenceCount = rs.getInt(10);
        String fallbackHandle = translateHandle(emptyToNull(rs.getString(11)));
        IJavaElement fallback = fallbackHandle == null ? null : JavaCore.create(fallbackHandle);
        return new BytecodeSearchEntry(kind, declaration,
                BytecodeSearchEntry.elementReference(elementHandle, fallback),
                BytecodeSearchEntry.symbolReference(name, qualifiedName, declaringTypeName, descriptor),
                access, typeCategory, occurrenceCount);
    }

    private String translateHandle(String handle) {
        if (handle == null || indexedRootHandle.isEmpty() || indexedRootHandle.equals(requestedRootHandle)
                || !handle.startsWith(indexedRootHandle)) {
            return handle;
        }
        return requestedRootHandle + handle.substring(indexedRootHandle.length());
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    @Override
    public void close() {
        if (ownsConnection) {
            synchronized (dbLock) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // in-memory connection cleanup is non-fatal
                }
            }
        }
    }
}
