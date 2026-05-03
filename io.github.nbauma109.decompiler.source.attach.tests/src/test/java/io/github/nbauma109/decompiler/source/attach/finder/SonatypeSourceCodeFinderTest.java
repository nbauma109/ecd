/*******************************************************************************
 * (C) 2024-2026 Nicolas Baumann (@nbauma109)
 * (C) 2026 Claude (@Claude)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.source.attach.finder;

import java.io.IOException;

import org.junit.Test;

public class SonatypeSourceCodeFinderTest extends AbstractSourceCodeFinderTests {

    @Test
    public void testFind() throws IOException {
        testFindOSGIServiceEvent(null);
    }

    @Override
    protected AbstractSourceCodeFinder newSourceCodeFinder(String serviceUrl) {
        return new SonatypeSourceCodeFinder();
    }
}
