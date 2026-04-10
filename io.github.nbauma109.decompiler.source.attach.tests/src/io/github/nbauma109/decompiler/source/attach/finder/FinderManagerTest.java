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
        // Poll until all workers finish or the deadline passes.
        long deadline = System.currentTimeMillis() + 5_000;
        while (manager.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertFalse("All workers should have finished within 5 seconds", manager.isRunning());
        assertTrue("No sources should be found for an empty lib list", results.isEmpty());
    }

    @Test
    public void cancelAfterFindSourcesWithEmptyListDoesNotThrow() {
        FinderManager manager = new FinderManager();
        List<SourceFileResult> results = new ArrayList<>();

        manager.findSources(Collections.emptyList(), results);
        manager.cancel(); // cancel while workers may still be winding down
    }
}
