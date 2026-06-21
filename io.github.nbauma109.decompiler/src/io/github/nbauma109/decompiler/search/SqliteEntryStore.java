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
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Access;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.TypeCategory;

/**
 * Entry store backed by a shared SQLite database.
 * All data (rows and indexes) live on disk; RAM usage is limited to JDBC query results.
 * The connection is owned by {@link BytecodeSearchIndex} and must not be closed here.
 */
final class SqliteEntryStore implements EntryStore {

    static final String PLUGIN_ID = "io.github.nbauma109.decompiler"; //$NON-NLS-1$

    private static final String SELECT_COLS =
            "kind, declaration, access_flags, type_category, element_handle, name, " + //$NON-NLS-1$
            "qualified_name, declaring_type_name, descriptor, occurrence_count"; //$NON-NLS-1$
    private static final String SELECT_ENTRIES_WHERE = "SELECT " + SELECT_COLS + " FROM entries WHERE "; //$NON-NLS-1$

    private final Connection conn;
    private final Object dbLock;
    private final int jarId;
    private final int size;

    SqliteEntryStore(Connection conn, Object dbLock, int jarId) throws SQLException {
        this.conn = conn;
        this.dbLock = dbLock;
        this.jarId = jarId;
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
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS jars (
                        id INTEGER PRIMARY KEY,
                        root_handle TEXT NOT NULL DEFAULT '',
                        path TEXT NOT NULL,
                        last_modified INTEGER NOT NULL,
                        file_length INTEGER NOT NULL
                    )"""); //$NON-NLS-1$
            stmt.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_jars
                        ON jars(root_handle, path)"""); //$NON-NLS-1$
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS entries (
                        id INTEGER PRIMARY KEY,
                        jar_id INTEGER NOT NULL REFERENCES jars(id) ON DELETE CASCADE,
                        kind INTEGER NOT NULL,
                        declaration INTEGER NOT NULL,
                        access_flags INTEGER NOT NULL,
                        type_category INTEGER NOT NULL,
                        element_handle TEXT NOT NULL DEFAULT '',
                        name TEXT NOT NULL DEFAULT '',
                        normalized_name TEXT NOT NULL DEFAULT '',
                        qualified_name TEXT NOT NULL DEFAULT '',
                        normalized_qualified_name TEXT NOT NULL DEFAULT '',
                        declaring_type_name TEXT NOT NULL DEFAULT '',
                        descriptor TEXT NOT NULL DEFAULT '',
                        occurrence_count INTEGER NOT NULL DEFAULT 1
                    )"""); //$NON-NLS-1$
            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_entries_name
                        ON entries(jar_id, kind, normalized_name)"""); //$NON-NLS-1$
            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_entries_qname
                        ON entries(jar_id, kind, normalized_qualified_name)"""); //$NON-NLS-1$
        }
    }

    /**
     * Returns the jar id if the jar is already in the database with matching metadata,
     * or -1 if it needs (re-)indexing.
     */
    static int findJar(Connection conn, Object dbLock, String rootHandle, String path,
            long lastModified, long fileLength) throws SQLException {
        final Object lock = dbLock;
        synchronized (lock) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM jars WHERE root_handle = ? AND path = ? AND last_modified = ? AND file_length = ?")) { //$NON-NLS-1$
                ps.setString(1, rootHandle);
                ps.setString(2, path);
                ps.setLong(3, lastModified);
                ps.setLong(4, fileLength);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : -1;
                }
            }
        }
    }

    /**
     * Deletes any existing record for the jar (cascades to entries) and inserts a new one.
     * Must be called inside a write transaction.
     */
    static int registerJar(Connection conn, String rootHandle, String path,
            long lastModified, long fileLength) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM jars WHERE root_handle = ? AND path = ?")) { //$NON-NLS-1$
            del.setString(1, rootHandle);
            del.setString(2, path);
            del.executeUpdate();
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO jars(root_handle, path, last_modified, file_length) VALUES(?, ?, ?, ?)", //$NON-NLS-1$
                Statement.RETURN_GENERATED_KEYS)) {
            ins.setString(1, rootHandle);
            ins.setString(2, path);
            ins.setLong(3, lastModified);
            ins.setLong(4, fileLength);
            ins.executeUpdate();
            try (ResultSet keys = ins.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key for jar row"); //$NON-NLS-1$
                }
                return keys.getInt(1);
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
                String normName = normalize(name);
                String normQName = normalize(qualifiedName);
                if (normName.equals(normQName)) {
                    collectByName(kind, normName, consumer);
                } else {
                    collectByNameOrQName(kind, normName, normQName, consumer);
                }
            }
        } catch (SQLException e) {
            throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, e.getMessage(), e));
        }
    }

    private void collectAll(Kind kind, EntryStore.EntryConsumer consumer) throws SQLException, CoreException {
        String sql = SELECT_ENTRIES_WHERE + "jar_id = ? AND kind = ?"; //$NON-NLS-1$
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jarId);
                ps.setInt(2, kind.ordinal());
                emit(ps, consumer);
            }
        }
    }

    private void collectByName(Kind kind, String normName, EntryStore.EntryConsumer consumer)
            throws SQLException, CoreException {
        if (StringUtils.isBlank(normName)) {
            return;
        }
        String sql = SELECT_ENTRIES_WHERE + "jar_id = ? AND kind = ? AND normalized_name = ?"; //$NON-NLS-1$
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jarId);
                ps.setInt(2, kind.ordinal());
                ps.setString(3, normName);
                emit(ps, consumer);
            }
        }
    }

    private void collectByNameOrQName(Kind kind, String normName, String normQName,
            EntryStore.EntryConsumer consumer) throws SQLException, CoreException {
        boolean hasName = !StringUtils.isBlank(normName);
        boolean hasQName = !StringUtils.isBlank(normQName);
        if (!hasName && !hasQName) {
            return;
        }
        if (!hasName) {
            collectByQName(kind, normQName, consumer);
            return;
        }
        if (!hasQName) {
            collectByName(kind, normName, consumer);
            return;
        }
        String sql = SELECT_ENTRIES_WHERE + "jar_id = ? AND kind = ? AND (normalized_name = ? OR normalized_qualified_name = ?)"; //$NON-NLS-1$
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jarId);
                ps.setInt(2, kind.ordinal());
                ps.setString(3, normName);
                ps.setString(4, normQName);
                emit(ps, consumer);
            }
        }
    }

    private void collectByQName(Kind kind, String normQName, EntryStore.EntryConsumer consumer)
            throws SQLException, CoreException {
        String sql = SELECT_ENTRIES_WHERE + "jar_id = ? AND kind = ? AND normalized_qualified_name = ?"; //$NON-NLS-1$
        synchronized (dbLock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jarId);
                ps.setInt(2, kind.ordinal());
                ps.setString(3, normQName);
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
        return new BytecodeSearchEntry(kind, declaration,
                BytecodeSearchEntry.elementReference(elementHandle, null),
                BytecodeSearchEntry.symbolReference(name, qualifiedName, declaringTypeName, descriptor),
                access, typeCategory, occurrenceCount);
    }

    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT); //$NON-NLS-1$
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    @Override
    public void close() {
        // Connection is owned by BytecodeSearchIndex; do not close here.
    }
}
