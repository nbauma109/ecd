/*******************************************************************************
 * (C) 2024-2026 Nicolas Baumann (@nbauma109)
 * (C) 2026 Claude (@Claude)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.source.attach.finder;

import static io.github.nbauma109.decompiler.source.attach.finder.SourceCodeFinderFacade.HTTPS_REPO_GRAILS_ORG_GRAILS;

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
