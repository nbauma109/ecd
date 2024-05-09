package org.sf.feeling.decompiler.source.attach.finder;

import java.io.IOException;

import org.junit.Test;

public class MavenRepoSourceCodeFinderTest extends AbstractSourceCodeFinderTests {

	@Test
	public void testFind() throws IOException {
		testFindAsmUtil(null);
	}

	@Override
	protected AbstractSourceCodeFinder newSourceCodeFinder(String serviceUrl) {
		return new MavenRepoSourceCodeFinder();
	}
}
