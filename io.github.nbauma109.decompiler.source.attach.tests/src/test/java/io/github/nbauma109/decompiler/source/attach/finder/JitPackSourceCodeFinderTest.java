/*******************************************************************************
 * (C) 2026 Claude (@Claude)
 * (C) 2026 Copilot (@Copilot)
 * (C) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.source.attach.finder;

import java.io.IOException;

import org.junit.Test;

public class JitPackSourceCodeFinderTest extends AbstractSourceCodeFinderTests {

    private static final String ASCII_TABLE_GAV_URL = "https://jitpack.io/com/github/freva/ascii-table/1.12.0/"; //$NON-NLS-1$
    private static final String ASCII_TABLE_FILE_NAME = "ascii-table-1.12.0"; //$NON-NLS-1$

    @Test
    public void testFind() throws IOException {
        testFind(null, ASCII_TABLE_GAV_URL, ASCII_TABLE_FILE_NAME);
    }

    @Override
    protected AbstractSourceCodeFinder newSourceCodeFinder(String serviceUrl) {
        return new JitPackSourceCodeFinder();
    }
}
