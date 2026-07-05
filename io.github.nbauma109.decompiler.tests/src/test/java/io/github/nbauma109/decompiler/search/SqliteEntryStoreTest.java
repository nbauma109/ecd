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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;

@SuppressWarnings("restriction")
public class SqliteEntryStoreTest {

    @Test
    public void qualifiedNameIndexOnlyStoresDistinctQualifiedNames() throws Exception {
        try (Connection conn = databaseWithEntries();
                Statement stmt = conn.createStatement()) {
            String indexSql = null;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT sql FROM sqlite_master WHERE type = 'index' AND name = 'idx_entries_distinct_qname'")) { //$NON-NLS-1$
                if (rs.next()) {
                    indexSql = rs.getString(1);
                }
            }
            assertNotNull(indexSql);
            assertTrue(indexSql.contains("WHERE qualified_name_id != name_id")); //$NON-NLS-1$

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM entries INDEXED BY idx_entries_distinct_qname " + //$NON-NLS-1$
                            "WHERE qualified_name_id != name_id")) { //$NON-NLS-1$
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'idx_entries_qname'")) { //$NON-NLS-1$
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    public void qualifiedNameQueriesStillReturnEqualAndDistinctNamesOnce() throws Exception {
        try (Connection conn = databaseWithEntries(); SqliteEntryStore store = new SqliteEntryStore(conn, new Object(), 1)) {

            List<BytecodeSearchEntry> matches = new ArrayList<>();
            store.collect(Kind.METHOD, "alpha", "pkg.beta", false, matches::add); //$NON-NLS-1$ //$NON-NLS-2$
            assertEquals(3, matches.size());
            assertEquals(1, matches.stream().filter(entry -> "alpha".equals(entry.getName()) //$NON-NLS-1$
                    && "pkg.beta".equals(entry.getQualifiedName())).count()); //$NON-NLS-1$

            matches.clear();
            store.collect(Kind.METHOD, "", "pkg.beta", false, matches::add); //$NON-NLS-1$ //$NON-NLS-2$
            assertEquals(2, matches.size());
            assertTrue(matches.stream().allMatch(entry -> "pkg.beta".equals(entry.getQualifiedName()))); //$NON-NLS-1$

            matches.clear();
            store.collect(Kind.METHOD, "ignored", "pkg.Gamma", false, matches::add); //$NON-NLS-1$ //$NON-NLS-2$
            assertEquals(1, matches.size());
            assertEquals("pkg.Gamma", matches.get(0).getName()); //$NON-NLS-1$
            assertFalse(matches.get(0).isDeclaration());
        }
    }

    @Test
    public void identicalJarLocationsShareEntriesAndTranslateElementHandles() throws Exception {
        try (Connection conn = databaseWithEntries()) {
            SqliteEntryStore.JarRegistration first = SqliteEntryStore.registerJar(conn,
                    "=first", "/tmp/first.jar", 2, 200, 21, 22, "shared-hash"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            SqliteEntryStore.JarRegistration second = SqliteEntryStore.registerJar(conn,
                    "=second", "/tmp/second.jar", 3, 200, 21, 22, "shared-hash"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

            assertTrue(first.needsIndexing());
            assertFalse(second.needsIndexing());
            assertEquals(first.jarId(), second.jarId());
            assertEquals(second.jarId(), SqliteEntryStore.findJar(conn, new Object(),
                    new SqliteEntryStore.JarKey("=second", "/tmp/second.jar", 3, 200, 21, 22, "shared-hash"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT (SELECT COUNT(*) FROM jars), (SELECT COUNT(*) FROM jar_locations)")) { //$NON-NLS-1$
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1)); // fixture content plus one shared content row
                assertEquals(3, rs.getInt(2)); // fixture location plus both aliases
            }

            SqliteEntryStore aliasStore = new SqliteEntryStore(conn, new Object(), 1, false, "=alias"); //$NON-NLS-1$
            List<BytecodeSearchEntry> matches = new ArrayList<>();
            aliasStore.collect(Kind.METHOD, "alpha", "alpha", false, matches::add); //$NON-NLS-1$ //$NON-NLS-2$
            assertEquals(2, matches.size());
            assertTrue(matches.stream().anyMatch(
                    entry -> "=alias/pkg/Type.class".equals(entry.getElementHandle()))); //$NON-NLS-1$
        }
    }

    private static Connection databaseWithEntries() throws Exception {
        Connection conn = SqliteEntryStore.openInMemoryDatabase();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    INSERT INTO jars(id, root_handle, path, last_modified, file_length, runtime_version, file_crc,
                            content_hash)
                    VALUES(1, '=canonical', '/tmp/test.jar', 1, 1, 21, 1, 'fixture-hash')"""); //$NON-NLS-1$
            stmt.executeUpdate("""
                    INSERT INTO jar_locations(root_handle, path, last_modified, file_length, jar_id)
                    VALUES('=canonical', '/tmp/test.jar', 1, 1, 1)"""); //$NON-NLS-1$
            stmt.executeUpdate("""
                    INSERT INTO strings(id, value) VALUES
                    (1, 'alpha'), (2, 'beta'), (3, 'pkg.beta'), (4, 'pkg.Gamma'),
                    (5, '=canonical/pkg/Type.class')"""); //$NON-NLS-1$
            stmt.executeUpdate("""
                    INSERT INTO entries(jar_id, kind, declaration, access_flags, type_category,
                            element_handle_id, name_id, qualified_name_id) VALUES
                    (1, 1, 0, 0, 0, 5, 1, 1),
                    (1, 1, 0, 0, 0, 0, 2, 3),
                    (1, 1, 0, 0, 0, 0, 1, 3),
                    (1, 1, 0, 0, 0, 0, 4, 4)"""); //$NON-NLS-1$
        }
        return conn;
    }
}
