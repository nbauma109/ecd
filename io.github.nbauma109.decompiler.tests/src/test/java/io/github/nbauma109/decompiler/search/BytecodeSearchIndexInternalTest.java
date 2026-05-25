/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.jdt.core.IJavaElement;
import org.junit.Test;

import io.github.nbauma109.decompiler.search.BytecodeSearchIndex.JarIndex.CompactEntries.EntryColumns;
import io.github.nbauma109.decompiler.search.BytecodeSearchIndex.JarIndex.CompactEntries.StringTables;

/**
 * Tests the equals / hashCode / toString contracts on the public nested record
 * types {@code BytecodeSearchIndex.JarIndex.CompactEntries.StringTables} and
 * {@code BytecodeSearchIndex.JarIndex.CompactEntries.EntryColumns}.
 *
 * Both records customise equals/hashCode/toString via Apache Commons Builders
 * because their components are arrays.  Normal record equality (which uses
 * {@link Object#equals}) would compare array identity rather than content.
 */
@SuppressWarnings("restriction")
public class BytecodeSearchIndexInternalTest {

    // ------------------------------------------------------------------
    // StringTables(String[] strings, String[] elementHandles,
    //              IJavaElement[] anonymousElementFallbacks)
    // ------------------------------------------------------------------

    @Test
    public void stringTablesEqualsIsReflexiveNullSafeAndContentBased() {
        String[] strings = {"alpha", "beta"}; //$NON-NLS-1$ //$NON-NLS-2$
        String[] handles = {"h1", "h2"}; //$NON-NLS-1$ //$NON-NLS-2$
        IJavaElement[] fallbacks = new IJavaElement[0];

        StringTables a = new StringTables(strings, handles, fallbacks);
        StringTables b = new StringTables(strings.clone(), handles.clone(), new IJavaElement[0]);
        StringTables c = new StringTables(new String[]{"gamma"}, handles.clone(), new IJavaElement[0]); //$NON-NLS-1$

        assertEquals("reflexive", a, a); //$NON-NLS-1$
        assertNotEquals("null check", a, null); //$NON-NLS-1$
        assertNotEquals("wrong class", a, "other"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("equal arrays", a, b); //$NON-NLS-1$
        assertNotEquals("different strings[]", a, c); //$NON-NLS-1$
    }

    @Test
    public void stringTablesHashCodeIsConsistentWithEquals() {
        String[] strings = {"x"}; //$NON-NLS-1$
        String[] handles = {"h"}; //$NON-NLS-1$
        IJavaElement[] fallbacks = new IJavaElement[0];

        StringTables a = new StringTables(strings, handles, fallbacks);
        StringTables b = new StringTables(strings.clone(), handles.clone(), new IJavaElement[0]);

        assertEquals("equal objects must share hashCode", a.hashCode(), b.hashCode()); //$NON-NLS-1$
    }

    @Test
    public void stringTablesToStringContainsAllFieldNames() {
        StringTables t = new StringTables(new String[]{"a"}, new String[]{"b"}, new IJavaElement[0]); //$NON-NLS-1$ //$NON-NLS-2$
        String s = t.toString();

        assertTrue("toString must include 'strings'", s.contains("strings")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'elementHandles'", s.contains("elementHandles")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'anonymousElementFallbacks'", s.contains("anonymousElementFallbacks")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    // ------------------------------------------------------------------
    // EntryColumns(byte[] kindAndFlags, int[] elementHandleIds,
    //              int[] nameIds, int[] qualifiedNameIds,
    //              int[] declaringTypeNameIds, int[] descriptorIds)
    // ------------------------------------------------------------------

    @Test
    public void entryColumnsEqualsIsReflexiveNullSafeAndContentBased() {
        byte[] kf = {1, 2};
        int[] eh = {3};
        int[] ni = {4};
        int[] qi = {5};
        int[] di = {6};
        int[] ds = {7};

        EntryColumns a = new EntryColumns(kf, eh, ni, qi, di, ds);
        EntryColumns b = new EntryColumns(kf.clone(), eh.clone(), ni.clone(), qi.clone(), di.clone(), ds.clone());
        EntryColumns c = new EntryColumns(new byte[]{9}, eh.clone(), ni.clone(), qi.clone(), di.clone(), ds.clone());

        assertEquals("reflexive", a, a); //$NON-NLS-1$
        assertNotEquals("null check", a, null); //$NON-NLS-1$
        assertNotEquals("wrong class", a, "other"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("equal arrays", a, b); //$NON-NLS-1$
        assertNotEquals("different kindAndFlags[]", a, c); //$NON-NLS-1$
    }

    @Test
    public void entryColumnsHashCodeIsConsistentWithEquals() {
        byte[] kf = {1};
        int[] eh = {2};
        int[] ni = {3};
        int[] qi = {4};
        int[] di = {5};
        int[] ds = {6};

        EntryColumns a = new EntryColumns(kf, eh, ni, qi, di, ds);
        EntryColumns b = new EntryColumns(kf.clone(), eh.clone(), ni.clone(), qi.clone(), di.clone(), ds.clone());

        assertEquals("equal objects must share hashCode", a.hashCode(), b.hashCode()); //$NON-NLS-1$
    }

    @Test
    public void entryColumnToStringContainsAllFieldNames() {
        EntryColumns e = new EntryColumns(new byte[]{1}, new int[]{2}, new int[]{3},
                new int[]{4}, new int[]{5}, new int[]{6});
        String s = e.toString();

        assertTrue("toString must include 'kindAndFlags'", s.contains("kindAndFlags")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'elementHandleIds'", s.contains("elementHandleIds")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'nameIds'", s.contains("nameIds")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'qualifiedNameIds'", s.contains("qualifiedNameIds")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'declaringTypeNameIds'", s.contains("declaringTypeNameIds")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'descriptorIds'", s.contains("descriptorIds")); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
