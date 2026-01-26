/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.finder;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import io.github.nbauma109.decompiler.util.HashUtils;
import io.github.nbauma109.decompiler.util.Logger;

public class FinderManager {

    private Worker[] workers;

    public FinderManager() {
        this.workers = new Worker[10];
    }

    public boolean isRunning() {
        boolean result = false;
        for (Worker worker : this.workers) {
            if ((worker != null) && (worker.isAlive())) {
                result = true;
                break;
            }
        }
        return result;
    }

    public void cancel() {
        for (Worker worker : this.workers) {
            if ((worker != null) && (worker.isAlive())) {
                worker.cancel();
            }
        }
    }

    public void findSources(List<String> libs, List<SourceFileResult> results) {
        WorkQueue queue = new WorkQueue();

        for (int i = 0; i < this.workers.length; i++) {
            this.workers[i] = new Worker(queue, results);
            this.workers[i].start();
        }

        for (String lib : libs) {
            queue.addWork(lib);
        }

        for (int i = 0; i < this.workers.length; i++) {
            queue.addWork(Worker.NO_MORE_WORK);
        }
    }

    private static class WorkQueue {

        LinkedList<String> queue = new LinkedList<>();

        public synchronized void addWork(String o) {
            this.queue.addLast(o);
            notify();
        }

        public synchronized String getWork() throws InterruptedException {
            while (this.queue.isEmpty()) {
                wait();
            }
            return this.queue.removeFirst();
        }
    }

    private static class Worker extends Thread {

        private static final String NO_MORE_WORK = "NO_MORE_WORK"; //$NON-NLS-1$
        private FinderManager.WorkQueue q;
        private List<SourceFileResult> results;
        private boolean canceled;
        private SourceCodeFinder finder;

        public Worker(FinderManager.WorkQueue q, List<SourceFileResult> results) {
            this.q = q;
            this.results = results;
            this.finder = new SourceCodeFinderFacade();
        }

        public void cancel() {
            this.canceled = true;
            this.finder.cancel();
        }

        @Override
        public void run() {
            try {
                while (!this.canceled) {
                    String binFile = this.q.getWork();
                    if (Objects.equals(binFile, NO_MORE_WORK)) {
                        break;
                    }

                    String sha1 = HashUtils.sha1Hash(new File(binFile));
                    this.finder.find(binFile, sha1, this.results);
                }
            } catch (InterruptedException e) {
                Logger.debug(e);
            }
        }
    }
}
