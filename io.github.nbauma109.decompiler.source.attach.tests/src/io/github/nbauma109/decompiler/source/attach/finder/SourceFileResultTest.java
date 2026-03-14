package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

public class SourceFileResultTest {

    @Test
    public void stringBasedConstructorAndMutatorsExposeStoredState() {
        StubSourceCodeFinder finder = new StubSourceCodeFinder();
        SourceFileResult result = new SourceFileResult(finder, "bin.jar", "src.jar", "demo-sources.jar", 75);

        assertSame(finder, result.getFinder());
        assertEquals("bin.jar", result.getBinFile());
        assertEquals("src.jar", result.getSource());
        assertEquals("demo-sources.jar", result.getSuggestedSourceFileName());
        assertEquals(75, result.getAccuracy());
        assertNull(result.getTempSource());

        result.setBinFile("bin-2.jar");
        result.setSource("src-2.jar");
        result.setSuggestedSourceFileName("other-sources.jar");
        result.setAccuracy(80);
        result.setFinder(null);
        result.setTempSource("temp.zip");

        assertEquals("bin-2.jar", result.getBinFile());
        assertEquals("src-2.jar", result.getSource());
        assertEquals("other-sources.jar", result.getSuggestedSourceFileName());
        assertEquals(80, result.getAccuracy());
        assertNull(result.getFinder());
        assertEquals("temp.zip", result.getTempSource());
        assertTrue(result.toString().contains("src-2.jar"));
        assertTrue(result.toString().contains("accuracy = 80"));
    }

    @Test
    public void fileBasedConstructorDerivesPathsAndNamesFromFiles() {
        StubSourceCodeFinder finder = new StubSourceCodeFinder();
        File source = new File("target/demo-sources.jar");
        File temp = new File("target/demo-temp.jar");

        SourceFileResult result = new SourceFileResult(finder, "bin.jar", source, temp, 100);

        assertSame(finder, result.getFinder());
        assertEquals("bin.jar", result.getBinFile());
        assertEquals(source.getAbsolutePath(), result.getSource());
        assertEquals("demo-sources.jar", result.getSuggestedSourceFileName());
        assertEquals(temp.getAbsolutePath(), result.getTempSource());
        assertEquals(100, result.getAccuracy());
    }

    private static final class StubSourceCodeFinder implements SourceCodeFinder {
        @Override
        public void find(String binFile, String sha1, java.util.List<SourceFileResult> resultList) {
        }

        @Override
        public void cancel() {
        }

        @Override
        public String getDownloadUrl() {
            return null;
        }
    }
}
