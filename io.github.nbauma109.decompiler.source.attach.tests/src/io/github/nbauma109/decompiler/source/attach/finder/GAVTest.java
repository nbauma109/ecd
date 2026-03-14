package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GAVTest {

    @Test
    public void gav_isValidOnlyWhenAllCoordinatesArePresent() {
        GAV gav = new GAV();
        assertFalse(gav.isValid());

        gav.setGroupId("org.example");
        gav.setArtifactId("demo");
        assertFalse(gav.isValid());

        gav.setVersion("1.0.0");
        assertTrue(gav.isValid());
    }

    @Test
    public void gav_equalsHashCodeAndToStringReflectState() {
        GAV left = new GAV();
        left.setGroupId("org.example");
        left.setArtifactId("demo");
        left.setVersion("1.0.0");
        left.setArtifactLink("https://repo.example.org/demo");

        GAV right = new GAV();
        right.setGroupId("org.example");
        right.setArtifactId("demo");
        right.setVersion("1.0.0");
        right.setArtifactLink("https://repo.example.org/demo");

        GAV different = new GAV();
        different.setGroupId("org.example");
        different.setArtifactId("other");
        different.setVersion("1.0.0");

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertFalse(left.equals(different));
        assertFalse(left.equals(null));
        assertFalse("demo".equals(left));
        assertTrue(left.toString().contains("groupId=org.example"));
        assertTrue(left.toString().contains("artifactId=demo"));
    }
}
