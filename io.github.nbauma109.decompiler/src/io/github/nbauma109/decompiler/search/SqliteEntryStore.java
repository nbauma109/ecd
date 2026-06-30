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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    SqliteEntryStore(Connection conn, Object dbLock, int jarId) throws SQLException {
        this(conn, dbLock, jarId, false);
    }

    SqliteEntryStore(Connection conn, Object dbLock, int jarId, boolean ownsConnection) throws SQLException {
        this.conn = conn;
        this.dbLock = dbLock;
        this.jarId = jarId;
        this.ownsConnection = ownsConnection;
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
        boolean needsReset = hasColumn(conn, "entries", "normalized_name"); //$NON-NLS-1$ //$NON-NLS-2$
        if (needsReset) {
            try (var s = conn.createStatement()) {
                s.execute("DROP TABLE IF EXISTS entries"); //$NON-NLS-1$
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
                                    file_crc INTEGER NOT NULL DEFAULT 0
                                    )"""); //$NON-NLS-1$
                            stmt.execute("""
                                    CREATE INDEX IF NOT EXISTS idx_jars
                                    ON jars(root_handle, path)"""); //$NON-NLS-1$
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
                                                    stmt.execute("""
                                                            CREATE INDEX IF NOT EXISTS idx_entries_qname
                                                            ON entries(jar_id, kind, qualified_name_id)"""); //$NON-NLS-1$
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

    static void pruneOrphanJarRows(Connection conn, Set<String> keepKeys,
            Set<Integer> liveJarIds) throws SQLException {
        List<int[]> ids = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        try (PreparedStatement sel = conn.prepareStatement("SELECT id, root_handle, path FROM jars")) { //$NON-NLS-1$
            try (ResultSet rs = sel.executeQuery()) {
                while (rs.next()) {
                    ids.add(new int[]{rs.getInt(1)});
                    keys.add(rs.getString(2) + '\0' + rs.getString(3));
                }
            }
        }
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM jars WHERE id = ?")) { //$NON-NLS-1$
            for (int i = 0; i < ids.size(); i++) {
                int id = ids.get(i)[0];
                String key = keys.get(i);
                // Delete if path is no longer in the current plan, or if this specific row
                // is not the one actually used by the newly published SqliteEntryStore.
                if (!keepKeys.contains(key) || !liveJarIds.contains(id)) {
                    del.setInt(1, id);
                    del.addBatch();
                }
            }
            del.executeBatch();
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

    record JarKey(String rootHandle, String path, long lastModified, long fileLength, int runtimeVersion, long fileCrc) {
    }

    record JarRegistration(int jarId) {
    }

    /**
     * Returns the jar id if the jar is already in the database with matching metadata,
     * or -1 if it needs (re-)indexing.
     */
    static int findJar(Connection conn, Object dbLock, JarKey key) throws SQLException {
        final Object lock = dbLock;
        synchronized (lock) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM jars WHERE root_handle = ? AND path = ? AND last_modified = ? AND file_length = ? AND runtime_version = ? AND file_crc = ?")) { //$NON-NLS-1$
                ps.setString(1, key.rootHandle());
                ps.setString(2, key.path());
                ps.setLong(3, key.lastModified());
                ps.setLong(4, key.fileLength());
                ps.setInt(5, key.runtimeVersion());
                ps.setLong(6, key.fileCrc());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : -1;
                }
            }
        }
    }

    /**
     * Inserts a new jar row and returns the new id.  Any pre-existing row for the same
     * (root_handle, path) is intentionally left in place so searches against the currently
     * published SqliteEntryStore continue to see valid data.  The superseded row is cleaned
     * up by {@link #pruneOrphanJarRows} on the next refresh cycle, by which time all
     * in-flight searches using the old snapshot have completed.
     * Must be called inside a write transaction.
     */
    static JarRegistration registerJar(Connection conn, String rootHandle, String path,
            long lastModified, long fileLength, int runtimeVersion, long fileCrc) throws SQLException {
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO jars(root_handle, path, last_modified, file_length, runtime_version, file_crc) VALUES(?, ?, ?, ?, ?, ?)", //$NON-NLS-1$
                Statement.RETURN_GENERATED_KEYS)) {
            ins.setString(1, rootHandle);
            ins.setString(2, path);
            ins.setLong(3, lastModified);
            ins.setLong(4, fileLength);
            ins.setInt(5, runtimeVersion);
            ins.setLong(6, fileCrc);
            ins.executeUpdate();
            try (ResultSet keys = ins.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key for jar row"); //$NON-NLS-1$
                }
                return new JarRegistration(keys.getInt(1));
            }
        }
    }

    int jarId() {
        return jarId;
    }

    boolean ownsConnection() {
        return ownsConnection;
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
        String sql = SELECT_ENTRIES_WHERE +
                "e.jar_id = ? AND e.kind = ? AND " + //$NON-NLS-1$
                "(e.name_id IN (SELECT id FROM strings WHERE value = ?) OR " + //$NON-NLS-1$
                "e.qualified_name_id IN (SELECT id FROM strings WHERE value = ?))"; //$NON-NLS-1$
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jarId);
                ps.setInt(2, kind.ordinal());
                ps.setString(3, searchName);
                ps.setString(4, searchQName);
                emit(ps, consumer);
            }
        }
    }

    private void collectByQName(Kind kind, String searchQName, EntryStore.EntryConsumer consumer)
            throws SQLException, CoreException {
        String sql = SELECT_ENTRIES_WHERE +
                "e.jar_id = ? AND e.kind = ? AND e.qualified_name_id IN (SELECT id FROM strings WHERE value = ?)"; //$NON-NLS-1$
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jarId);
                ps.setInt(2, kind.ordinal());
                ps.setString(3, searchQName);
                emit(ps, consumer);
            }
        }
    }

    private static void emit(PreparedStatement ps, EntryStore.EntryConsumer consumer)
            throws SQLException, CoreException {
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                consumer.accept(rowToEntry(rs));
            }
        }
    }

    private static BytecodeSearchEntry rowToEntry(ResultSet rs) throws SQLException {
        Kind kind = Kind.values()[rs.getInt(1)];
        boolean declaration = rs.getInt(2) != 0;
        Access access = Access.values()[rs.getInt(3)];
        TypeCategory typeCategory = TypeCategory.values()[rs.getInt(4)];
        String elementHandle = emptyToNull(rs.getString(5));
        String name = emptyToNull(rs.getString(6));
        String qualifiedName = emptyToNull(rs.getString(7));
        String declaringTypeName = emptyToNull(rs.getString(8));
        String descriptor = emptyToNull(rs.getString(9));
        int occurrenceCount = rs.getInt(10);
        String fallbackHandle = emptyToNull(rs.getString(11));
        IJavaElement fallback = fallbackHandle == null ? null : JavaCore.create(fallbackHandle);
        return new BytecodeSearchEntry(kind, declaration,
                BytecodeSearchEntry.elementReference(elementHandle, fallback),
                BytecodeSearchEntry.symbolReference(name, qualifiedName, declaringTypeName, descriptor),
                access, typeCategory, occurrenceCount);
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
