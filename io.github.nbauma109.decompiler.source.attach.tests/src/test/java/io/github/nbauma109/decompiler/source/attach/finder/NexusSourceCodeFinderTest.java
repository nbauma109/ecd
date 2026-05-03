/*******************************************************************************
 * (C) 2024-2026 Nicolas Baumann (@nbauma109)
 * (C) 2026 Claude (@Claude)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.source.attach.finder;

import static io.github.nbauma109.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML;
import static io.github.nbauma109.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_REPOSITORY_APACHE_ORG_INDEX_HTML;
import static io.github.nbauma109.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_REPOSITORY_CLOUDERA_COM_ARTIFACTORY;

import java.io.IOException;

import org.junit.Test;

public class NexusSourceCodeFinderTest extends AbstractSourceCodeFinderTests {

    @Test
    public void testApache() throws IOException {
        testFindCommonsIo(HTTPS_REPOSITORY_APACHE_ORG_INDEX_HTML);
    }

    @Test
    public void testAlfresco() throws IOException {
        testFindJunit(HTTPS_MAVEN_ALFRESCO_COM_NEXUS_INDEX_HTML);
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
