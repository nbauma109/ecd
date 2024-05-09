package org.sf.feeling.decompiler.source.attach.finder;

import org.junit.Test;

public class MavenRepoSourceCodeFinderTest extends AbstractSourceCodeFinderTests {

	@Test
	public void testFindSlf4jNop() throws Exception {
		testFindAsmUtil(null);
	}

	@Override
	protected AbstractSourceCodeFinder newSourceCodeFinder(String serviceUrl) {
		return new MavenRepoSourceCodeFinder();
	}
}
