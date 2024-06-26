package org.sf.feeling.decompiler.source.attach.finder;

import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_REPO_GRAILS_ORG_GRAILS;

import java.io.IOException;

import org.junit.Test;

public class ArtifactorySourceCodeFinderTest extends AbstractSourceCodeFinderTests {

	@Test
	public void testFindGrails() throws IOException {
		testFindAsmUtil(HTTPS_REPO_GRAILS_ORG_GRAILS);
	}

	@Override
	protected AbstractSourceCodeFinder newSourceCodeFinder(String serviceUrl) {
		return new ArtifactorySourceCodeFinder(serviceUrl);
	}
}
