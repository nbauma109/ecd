/*******************************************************************************
 * (C) 2026 Claude (@Claude)
 * (C) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GAVTest {

    private static final String GROUP_ID = "org.example";
    private static final String VERSION = "1.0.0";

    @Test
    public void gavIsValidOnlyWhenAllCoordinatesArePresent() {
        GAV gav = new GAV();
        assertFalse(gav.isValid());

        gav.setGroupId(GROUP_ID);
        gav.setArtifactId("demo");
        assertFalse(gav.isValid());

        gav.setVersion(VERSION);
        assertTrue(gav.isValid());
    }

    @Test
    public void gavEqualsHashCodeAndToStringReflectState() {
        GAV left = new GAV();
        left.setGroupId(GROUP_ID);
        left.setArtifactId("demo");
        left.setVersion(VERSION);
        left.setArtifactLink("https://repo.example.org/demo");

        GAV right = new GAV();
        right.setGroupId(GROUP_ID);
        right.setArtifactId("demo");
        right.setVersion(VERSION);
        right.setArtifactLink("https://repo.example.org/demo");

        GAV different = new GAV();
        different.setGroupId(GROUP_ID);
        different.setArtifactId("other");
        different.setVersion(VERSION);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, different);
        assertNotEquals(left, null);
        assertNotEquals("demo", left);
        assertTrue(left.toString().contains("groupId=org.example"));
        assertTrue(left.toString().contains("artifactId=demo"));
    }
}
