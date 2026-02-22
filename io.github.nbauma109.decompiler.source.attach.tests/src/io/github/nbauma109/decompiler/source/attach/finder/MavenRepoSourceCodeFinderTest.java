package io.github.nbauma109.decompiler.source.attach.finder;

import java.io.IOException;

import org.junit.Test;

public class MavenRepoSourceCodeFinderTest extends AbstractSourceCodeFinderTests {

    @Test
    public void testFind() throws IOException {
        testFindAsmUtil(null);
    }

    @Test
    public void testFindWithLeadingZeroSha1() throws IOException {
        testFindOsgiServiceEventWithLeadingZeroSha1(null);
    }

    @Override
    protected AbstractSourceCodeFinder newSourceCodeFinder(String serviceUrl) {
        return new MavenRepoSourceCodeFinder();
    }
}
