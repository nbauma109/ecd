package org.sf.feeling.decompiler.source.attach.finder;

import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML;
import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_MAVEN_NUXEO_ORG_NEXUS_INDEX_HTML;
import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_NEXUS_XWIKI_ORG_NEXUS_INDEX_HTML;
import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_OSS_SONATYPE_ORG_INDEX_HTML;
import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_REPOSITORY_APACHE_ORG_INDEX_HTML;
import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_REPOSITORY_OW2_ORG_NEXUS_INDEX_HTML;

import org.junit.Test;

public class NexusSourceCodeFinderTest extends AbstractSourceCodeFinderTests {

	@Test
	public void testOSS() throws Exception {
		testFindSlf4jNop(HTTPS_OSS_SONATYPE_ORG_INDEX_HTML);
	}

	@Test
	public void testApache() throws Exception {
		testFindCommonsIo(HTTPS_REPOSITORY_APACHE_ORG_INDEX_HTML);
	}

	@Test
	public void testOW2() throws Exception {
		testFindAsmUtil(HTTPS_REPOSITORY_OW2_ORG_NEXUS_INDEX_HTML);
	}

	@Test
	public void testNuxeo() throws Exception {
		testFindSlf4jNop(HTTPS_MAVEN_NUXEO_ORG_NEXUS_INDEX_HTML);
	}

	@Test
	public void testAlfresco() throws Exception {
		testFindJunit(HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML);
	}

	@Test
	public void testXWiki() throws Exception {
		testFindAsmUtil(HTTPS_NEXUS_XWIKI_ORG_NEXUS_INDEX_HTML);
	}

	@Override
	protected AbstractSourceCodeFinder newSourceCodeFinder(String serviceUrl) {
		return new NexusSourceCodeFinder(serviceUrl);
	}
}
