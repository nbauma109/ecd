package org.sf.feeling.decompiler.source.attach.finder;

import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML;
import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_NEXUS_XWIKI_ORG_NEXUS_INDEX_HTML;
import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_OSS_SONATYPE_ORG_INDEX_HTML;
import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_REPOSITORY_APACHE_ORG_INDEX_HTML;
import static org.sf.feeling.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_REPOSITORY_CLOUDERA_COM_ARTIFACTORY;

import java.io.IOException;

import org.junit.Test;

public class NexusSourceCodeFinderTest extends AbstractSourceCodeFinderTests {

    @Test
    public void testOSS() throws IOException {
        testFindSlf4jNop(HTTPS_OSS_SONATYPE_ORG_INDEX_HTML);
    }

    @Test
    public void testApache() throws IOException {
        testFindCommonsIo(HTTPS_REPOSITORY_APACHE_ORG_INDEX_HTML);
    }

    @Test
    public void testAlfresco() throws IOException {
        testFindJunit(HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML);
    }

    @Test
    public void testXWiki() throws IOException {
        testFindAsmUtil(HTTPS_NEXUS_XWIKI_ORG_NEXUS_INDEX_HTML);
    }

    @Test
    public void testFindCloudera() throws IOException {
        testFindCommonsIo(HTTPS_REPOSITORY_CLOUDERA_COM_ARTIFACTORY);
    }

    @Override
    protected AbstractSourceCodeFinder newSourceCodeFinder(String serviceUrl) {
        return new NexusSourceCodeFinder(serviceUrl);
    }
}
