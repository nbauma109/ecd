/*******************************************************************************
 * © 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MavenRepoSourceCodeFinderNullCoordinateTest {

    private static final String ORG_EXAMPLE = "org.example"; //$NON-NLS-1$

    @Parameters(name = "groupId={0},artifactId={1},version={2}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { null, "lib", "1.0" }, //$NON-NLS-1$ //$NON-NLS-2$
            { ORG_EXAMPLE, null, "1.0" }, //$NON-NLS-1$
            { ORG_EXAMPLE, "lib", null }, //$NON-NLS-1$
        });
    }

    private final String groupId;
    private final String artifactId;
    private final String version;

    public MavenRepoSourceCodeFinderNullCoordinateTest(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    @Test
    public void getMavenRepoSourceFileReturnsNullWhenAnyCoordinateIsNull() {
        MavenRepoSourceCodeFinder finder = new MavenRepoSourceCodeFinder();
        GAV gav = new GAV();
        gav.setGroupId(groupId);
        gav.setArtifactId(artifactId);
        gav.setVersion(version);
        assertNull(finder.getMavenRepoSourceFile(gav));
    }
}
