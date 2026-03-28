package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

public class SourceFileResultTest {

    private static final String BIN_JAR = "bin.jar";
    private static final String DEMO_SOURCES_JAR = "demo-sources.jar";
    private static final String SRC_2_JAR = "src-2.jar";

    @Test
    public void stringBasedConstructorAndMutatorsExposeStoredState() {
        StubSourceCodeFinder finder = new StubSourceCodeFinder();
        SourceFileResult result = new SourceFileResult(finder, BIN_JAR, "src.jar", DEMO_SOURCES_JAR, 75);

        assertSame(finder, result.getFinder());
        assertEquals(BIN_JAR, result.getBinFile());
        assertEquals("src.jar", result.getSource());
        assertEquals(DEMO_SOURCES_JAR, result.getSuggestedSourceFileName());
        assertEquals(75, result.getAccuracy());
        assertNull(result.getTempSource());

        result.setBinFile("bin-2.jar");
        result.setSource(SRC_2_JAR);
        result.setSuggestedSourceFileName("other-sources.jar");
        result.setAccuracy(80);
        result.setFinder(null);
        result.setTempSource("temp.zip");

        assertEquals("bin-2.jar", result.getBinFile());
        assertEquals(SRC_2_JAR, result.getSource());
        assertEquals("other-sources.jar", result.getSuggestedSourceFileName());
        assertEquals(80, result.getAccuracy());
        assertNull(result.getFinder());
        assertEquals("temp.zip", result.getTempSource());
        assertTrue(result.toString().contains(SRC_2_JAR));
        assertTrue(result.toString().contains("accuracy = 80"));
    }

    @Test
    public void fileBasedConstructorDerivesPathsAndNamesFromFiles() {
        StubSourceCodeFinder finder = new StubSourceCodeFinder();
        File source = new File("target/" + DEMO_SOURCES_JAR);
        File temp = new File("target/demo-temp.jar");

        SourceFileResult result = new SourceFileResult(finder, BIN_JAR, source, temp, 100);

        assertSame(finder, result.getFinder());
        assertEquals(BIN_JAR, result.getBinFile());
        assertEquals(source.getAbsolutePath(), result.getSource());
        assertEquals(DEMO_SOURCES_JAR, result.getSuggestedSourceFileName());
        assertEquals(temp.getAbsolutePath(), result.getTempSource());
        assertEquals(100, result.getAccuracy());
    }

    private static final class StubSourceCodeFinder implements SourceCodeFinder {
        @Override
        public void find(String binFile, String sha1, java.util.List<SourceFileResult> resultList) {
            // The finder is only stored for identity checks in this test, so no lookup is required.
        }

        @Override
        public void cancel() {
            // The stub never schedules work, so cancellation is intentionally a no-op.
        }

        @Override
        public String getDownloadUrl() {
            return null;
        }
    }
}
