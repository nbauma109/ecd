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

import java.util.regex.Pattern;

import org.junit.Test;

import io.github.nbauma109.decompiler.search.ApplicationLibrarySearchParticipant.MatchPatterns;
import io.github.nbauma109.decompiler.search.ApplicationLibrarySearchParticipant.ParameterPattern;
import io.github.nbauma109.decompiler.search.ApplicationLibrarySearchParticipant.SearchPattern;

/**
 * Tests the equals / hashCode / toString contracts on the public nested record
 * {@code ApplicationLibrarySearchParticipant.SearchPattern}.
 *
 * {@code SearchPattern} delegates to Apache Commons Builders because its
 * {@code ParameterPattern} and {@code MatchPatterns} components do not override
 * {@link Object#equals}.  The tests use the same component instances for both
 * "equal" patterns so that identity-based sub-comparisons always agree.
 */
@SuppressWarnings("restriction")
public class SearchPatternInternalTest {

    @Test
    public void searchPatternEqualsIsReflexiveNullSafeAndFieldBased() {
        ParameterPattern pp = new ParameterPattern(new String[]{"java.lang.String"}, true); //$NON-NLS-1$
        MatchPatterns mp = new MatchPatterns(true, null, null);

        // sp1 and sp2 share the same pp/mp instances → equals must be true
        SearchPattern sp1 = new SearchPattern("run", "pkg.Task.run", "pkg.Task", "(I)V", "void", null, pp, mp); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        SearchPattern sp2 = new SearchPattern("run", "pkg.Task.run", "pkg.Task", "(I)V", "void", null, pp, mp); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        // sp3 differs in name
        SearchPattern sp3 = new SearchPattern("stop", "pkg.Task.stop", "pkg.Task", "()V", null, null, pp, mp); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        assertEquals("reflexive", sp1, sp1); //$NON-NLS-1$
        assertNotEquals("null check", sp1, null); //$NON-NLS-1$
        assertNotEquals("wrong class", sp1, "string"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("same fields + same component instances", sp1, sp2); //$NON-NLS-1$
        assertNotEquals("different name", sp1, sp3); //$NON-NLS-1$
    }

    @Test
    public void searchPatternHashCodeIsConsistentWithEquals() {
        ParameterPattern pp = new ParameterPattern(new String[0], false);
        MatchPatterns mp = new MatchPatterns(false, null, null);

        SearchPattern sp1 = new SearchPattern("go", "a.B.go", "a.B", "()V", null, null, pp, mp); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        SearchPattern sp2 = new SearchPattern("go", "a.B.go", "a.B", "()V", null, null, pp, mp); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        assertEquals("equal objects must share hashCode", sp1.hashCode(), sp2.hashCode()); //$NON-NLS-1$
    }

    @Test
    public void searchPatternToStringContainsAllFieldNames() {
        ParameterPattern pp = new ParameterPattern(new String[]{"int"}, false); //$NON-NLS-1$
        MatchPatterns mp = new MatchPatterns(true, Pattern.compile("foo.*"), null); //$NON-NLS-1$

        SearchPattern sp = new SearchPattern("doIt", "x.Y.doIt", "x.Y", "(I)V", "void", null, pp, mp); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        String s = sp.toString();

        assertTrue("toString must include 'name'", s.contains("name")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'qualifiedName'", s.contains("qualifiedName")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'declaringTypeName'", s.contains("declaringTypeName")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'descriptor'", s.contains("descriptor")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'returnType'", s.contains("returnType")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'fieldType'", s.contains("fieldType")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'parameterPattern'", s.contains("parameterPattern")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("toString must include 'matchPatterns'", s.contains("matchPatterns")); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
