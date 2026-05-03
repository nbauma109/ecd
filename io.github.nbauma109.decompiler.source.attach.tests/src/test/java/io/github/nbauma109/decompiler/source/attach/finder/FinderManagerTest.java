/*******************************************************************************
 * © 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class FinderManagerTest {

    @Test
    public void isRunningReturnsFalseWhenNoWorkersHaveBeenStarted() {
        FinderManager manager = new FinderManager();
        assertFalse(manager.isRunning());
    }

    @Test
    public void cancelOnFreshManagerDoesNotThrow() {
        FinderManager manager = new FinderManager();
        manager.cancel(); // should be a no-op on an unstarted manager
        assertFalse(manager.isRunning());
    }

    @Test
    public void findSourcesWithEmptyLibsListCompletesAndLeavesResultsEmpty() throws InterruptedException {
        FinderManager manager = new FinderManager();
        List<SourceFileResult> results = new ArrayList<>();

        manager.findSources(Collections.emptyList(), results);

        // With no libs, each worker receives NO_MORE_WORK immediately and exits.
        manager.awaitCompletion();

        assertFalse("All workers should have finished", manager.isRunning());
        assertTrue("No sources should be found for an empty lib list", results.isEmpty());
    }

    @Test
    public void cancelAfterFindSourcesWithEmptyListDoesNotThrow() {
        FinderManager manager = new FinderManager();
        List<SourceFileResult> results = new ArrayList<>();

        manager.findSources(Collections.emptyList(), results);
        manager.cancel(); // cancel while workers may still be winding down
        assertTrue(results.isEmpty());
    }
}
