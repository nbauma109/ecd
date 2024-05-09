package org.sf.feeling.decompiler.source.attach.finder;

import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_REPOSITORY_CLOUDERA_COM_ARTIFACTORY;

import org.junit.Test;

public class Nexus3SourceCodeFinderTest extends AbstractSourceCodeFinderTests {

	@Test
	public void testFindCloudera() throws Exception {
		testFindCommonsIo(HTTPS_REPOSITORY_CLOUDERA_COM_ARTIFACTORY);
	}

	@Override
	protected AbstractSourceCodeFinder newSourceCodeFinder(String serviceUrl) {
		return new Nexus3SourceCodeFinder(serviceUrl);
	}
}
