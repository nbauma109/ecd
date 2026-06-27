/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.concurrent.LazyInitializer;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.swt.widgets.Display;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.util.Logger;

public final class BytecodeSearchIndex {

    private static final LazyInitializer<BytecodeSearchIndex> INSTANCE =
            new LazyInitializer<>() {
                @Override
                protected BytecodeSearchIndex initialize() {
                    return new BytecodeSearchIndex();
                }
            };
    private static final long STARTUP_DELAY = 5000L;
    private static final long REFRESH_DELAY = 2000L;
    private static final int CLASSPATH_FLAGS = IJavaElementDelta.F_CLASSPATH_CHANGED
            | IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED
            | IJavaElementDelta.F_ADDED_TO_CLASSPATH
            | IJavaElementDelta.F_REMOVED_FROM_CLASSPATH
            | IJavaElementDelta.F_SOURCEATTACHED
            | IJavaElementDelta.F_SOURCEDETACHED
            | IJavaElementDelta.F_ARCHIVE_CONTENT_CHANGED
            | IJavaElementDelta.F_CLASSPATH_ATTRIBUTES;

    private final IElementChangedListener classpathListener = this::classpathChanged;

    private final AtomicReference<Map<RootKey, JarIndex>> indexes =
            new AtomicReference<>(Collections.emptyMap());
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean refreshCompleted = new AtomicBoolean();
    private final Object dbLock = new Object();
    // Coordinates forEachEntry() reads against pruneOrphanJarRows() writes so that
    // in-flight searches always complete before superseded jar rows are deleted.
    private final ReentrantReadWriteLock searchLock = new ReentrantReadWriteLock();
    private boolean refreshRequested;
    private Job indexJob;
    private Connection conn;
    private int generation = 0;

    private BytecodeSearchIndex() {
    }

    public static BytecodeSearchIndex getDefault() {
        try {
            return INSTANCE.get();
        } catch (ConcurrentException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized void start() {
        if (started.get()) {
            return;
        }
        started.set(true);
        try {
            conn = openDatabase();
            vacuumIfNeeded();
        } catch (SQLException | IOException e) {
            JavaDecompilerPlugin.logError(e, "Failed to open search index database"); //$NON-NLS-1$
        }
        JavaCore.addElementChangedListener(classpathListener, ElementChangedEvent.POST_CHANGE);
        scheduleRefresh(STARTUP_DELAY);
    }

    public void stop() {
        Map<RootKey, JarIndex> oldMap;
        Connection connToClose;
        synchronized (this) {
            if (!started.get()) {
                return;
            }
            JavaCore.removeElementChangedListener(classpathListener);
            started.set(false);
            generation++;
            if (indexJob != null) {
                indexJob.cancel();
                indexJob = null;
            }
            oldMap = indexes.getAndSet(Collections.emptyMap());
            refreshCompleted.set(false);
            refreshRequested = false;
            connToClose = conn;
            conn = null;
        }
        // Close stores and connection under write lock so any in-flight forEachEntry()
        // search finishes before its SqliteEntryStore (or owned connection) is torn down.
        searchLock.writeLock().lock();
        try {
            oldMap.values().forEach(JarIndex::close);
            if (connToClose != null) {
                synchronized (dbLock) {
                    try {
                        connToClose.close();
                    } catch (SQLException e) {
                        Logger.debug(e);
                    }
                }
            }
        } finally {
            searchLock.writeLock().unlock();
        }
    }

    void forEachEntry(Kind kind, String name, String qualifiedName, boolean wildcard, IProgressMonitor monitor,
            EntryStore.EntryConsumer consumer) throws CoreException {
        waitForInitialRefresh(monitor);
        searchLock.readLock().lock();
        try {
            for (JarIndex index : indexes.get().values()) {
                if (monitor != null && monitor.isCanceled()) {
                    return;
                }
                index.collect(kind, name, qualifiedName, wildcard, consumer);
            }
        } finally {
            searchLock.readLock().unlock();
        }
    }

    public int entryCount() {
        int count = 0;
        for (JarIndex index : indexes.get().values()) {
            count += index.entryCount();
        }
        return count;
    }

    synchronized void scheduleRefresh() {
        scheduleRefresh(REFRESH_DELAY);
    }

    private synchronized void scheduleRefresh(long delay) {
        if (!started.get()) {
            return;
        }
        if (indexJob != null && indexJob.getState() != Job.NONE) {
            refreshRequested = true;
            return;
        }
        indexJob = Job.create("Index application library bytecode", this::refresh); //$NON-NLS-1$
        indexJob.setPriority(Job.BUILD);
        indexJob.schedule(delay);
    }

    private void classpathChanged(ElementChangedEvent event) {
        if (containsClasspathChange(event.getDelta())) {
            scheduleRefresh();
        }
    }

    private boolean containsClasspathChange(IJavaElementDelta delta) {
        if (delta == null) {
            return false;
        }
        if ((delta.getFlags() & CLASSPATH_FLAGS) != 0) {
            return true;
        }
        IJavaElement element = delta.getElement();
        if ((delta.getKind() == IJavaElementDelta.ADDED || delta.getKind() == IJavaElementDelta.REMOVED)
                && element != null
                && (element.getElementType() == IJavaElement.JAVA_PROJECT
                        || element.getElementType() == IJavaElement.PACKAGE_FRAGMENT_ROOT)) {
            return true;
        }
        for (IJavaElementDelta child : delta.getAffectedChildren()) {
            if (containsClasspathChange(child)) {
                return true;
            }
        }
        return false;
    }

    private void refresh(IProgressMonitor monitor) {
        int myGeneration;
        synchronized (this) {
            myGeneration = generation;
        }
        boolean scheduleAgain;
        try {
            Map<RootKey, IPackageFragmentRoot> roots = collectApplicationLibraryRootsWithoutSource();
            List<JarPlan> plans = plan(roots);
            RebuildResult rebuilt = rebuild(plans, monitor);
            if (!rebuilt.completed()) {
                return;
            }
            Optional<Map<RootKey, JarIndex>> oldMapOpt = publishAndGetOld(rebuilt.indexes(), myGeneration);
            if (oldMapOpt.isPresent()) {
                Map<RootKey, JarIndex> oldMap = oldMapOpt.get();
                Set<JarIndex> kept = Collections.newSetFromMap(new IdentityHashMap<>());
                kept.addAll(rebuilt.indexes().values());
                searchLock.writeLock().lock();
                try {
                    oldMap.values().stream()
                            .filter(idx -> !kept.contains(idx))
                            .forEach(JarIndex::close);
                    pruneOrphanJarRows(plans, rebuilt.indexes());
                } finally {
                    searchLock.writeLock().unlock();
                }
            }
        } catch (OperationCanceledException e) {
            // normal job cancellation; nothing to log
        } catch (CoreException | RuntimeException e) {
            JavaDecompilerPlugin.logError(e, "Failed to index application library bytecode"); //$NON-NLS-1$
        } finally {
            monitor.done();
            synchronized (this) {
                indexJob = null;
                scheduleAgain = refreshRequested && started.get();
                refreshRequested = false;
            }
        }
        if (scheduleAgain) {
            scheduleRefresh();
        }
    }

    private RebuildResult rebuild(List<JarPlan> plans, IProgressMonitor monitor) {
        Connection activeConn = getConn();
        SubMonitor subMonitor = SubMonitor.convert(monitor, "Index application library bytecode", //$NON-NLS-1$
                totalTicks(plans));
        Map<RootKey, JarIndex> rebuilt = new LinkedHashMap<>();
        Set<JarIndex> reused = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            for (JarPlan plan : plans) {
                JarIndex index = rebuild(plan, activeConn, subMonitor);
                if (index == null) {
                    rebuilt.values().stream().filter(idx -> !reused.contains(idx)).forEach(JarIndex::close);
                    return new RebuildResult(false, Map.of());
                }
                if (index == plan.existing()) {
                    reused.add(index);
                }
                rebuilt.put(plan.key(), index);
            }
        } catch (RuntimeException e) {
            rebuilt.values().stream().filter(idx -> !reused.contains(idx)).forEach(JarIndex::close);
            throw e;
        }
        return new RebuildResult(true, rebuilt);
    }

    private JarIndex rebuild(JarPlan plan, Connection activeConn, SubMonitor subMonitor) {
        if (subMonitor.isCanceled()) {
            return null;
        }
        File jar = plan.jar();
        subMonitor.subTask(jar.getName());
        if (plan.existing() != null) {
            subMonitor.worked(1);
            return plan.existing();
        }
        if (plan.dbJarId() >= 0) {
            subMonitor.worked(1);
            try {
                return new JarIndex(jar, new SqliteEntryStore(activeConn, dbLock, plan.dbJarId()));
            } catch (SQLException e) {
                JavaDecompilerPlugin.logError(e, "Failed to open cached index for " + jar.getName()); //$NON-NLS-1$
                return null;
            }
        }
        JarIndex index = BytecodeJarIndexer.index(plan.root(), jar, plan.work(), activeConn, dbLock,
                subMonitor.split(plan.ticks()));
        if (index != null && subMonitor.isCanceled()) {
            index.close();
            return null;
        }
        return index;
    }

    // Called while searchLock.writeLock() is held by the caller.
    private void pruneOrphanJarRows(List<JarPlan> plans, Map<RootKey, JarIndex> publishedIndexes) {
        Connection activeConn = getConn();
        if (activeConn == null) {
            return;
        }
        Set<String> keepKeys = new HashSet<>();
        for (JarPlan plan : plans) {
            keepKeys.add(plan.key().rootHandle() + '\0' + plan.jar().getAbsolutePath());
        }
        // Collect only shared-connection jar_ids; in-memory stores have ids in a separate
        // database and must not be confused with shared-DB row ids.
        Set<Integer> liveJarIds = new HashSet<>();
        for (JarIndex idx : publishedIndexes.values()) {
            if (idx.entries instanceof SqliteEntryStore ses && !ses.ownsConnection()) {
                liveJarIds.add(ses.jarId());
            }
        }
        try {
            SqliteEntryStore.pruneOrphanJarRows(activeConn, dbLock, keepKeys, liveJarIds);
        } catch (SQLException e) {
            Logger.debug(e);
        }
    }

    private synchronized Connection getConn() {
        return conn;
    }

    // Returns the old indexes map if the publish succeeded (same generation), or empty if rejected.
    // Old stores are NOT closed here; the caller closes them under searchLock.writeLock() so that
    // in-flight searches finish before any owned (in-memory) connection is torn down.
    private synchronized Optional<Map<RootKey, JarIndex>> publishAndGetOld(Map<RootKey, JarIndex> rebuilt, int myGeneration) {
        if (started.get() && generation == myGeneration) {
            Map<RootKey, JarIndex> oldMap = indexes.getAndSet(Collections.unmodifiableMap(rebuilt));
            refreshCompleted.set(true);
            return Optional.of(oldMap);
        } else {
            rebuilt.values().forEach(JarIndex::close);
            return Optional.empty();
        }
    }

    private List<JarPlan> plan(Map<RootKey, IPackageFragmentRoot> roots) {
        Connection activeConn = getConn();
        List<JarPlan> plans = new ArrayList<>(roots.size());
        for (Map.Entry<RootKey, IPackageFragmentRoot> rootEntry : roots.entrySet()) {
            RootKey key = rootEntry.getKey();
            IPath path = key.path();
            File jar = path.toFile();
            JarIndex existing = indexes.get().get(key);
            if (existing != null && existing.matches(jar)) {
                plans.add(new JarPlan(key, rootEntry.getValue(), jar, existing, null, -1, 1));
            } else {
                BytecodeJarIndexer.JarWork work = BytecodeJarIndexer.plan(jar);
                if (work == null) {
                    continue;
                }
                int dbJarId = -1;
                if (activeConn != null) {
                    try {
                        dbJarId = SqliteEntryStore.findJar(activeConn, dbLock,
                                new SqliteEntryStore.JarKey(key.rootHandle(), jar.getAbsolutePath(),
                                        jar.lastModified(), jar.length(),
                                        Runtime.version().feature(), work.fileCrc()));
                    } catch (SQLException e) {
                        Logger.debug(e);
                    }
                }
                plans.add(new JarPlan(key, rootEntry.getValue(), jar, null, work, dbJarId, work.totalTicks()));
            }
        }
        return plans;
    }

    private static int totalTicks(List<JarPlan> plans) {
        long total = 0L;
        for (JarPlan plan : plans) {
            total += plan.ticks();
        }
        return (int) Math.clamp(total, 1L, Integer.MAX_VALUE);
    }

    private void waitForInitialRefresh(IProgressMonitor monitor) {
        start();
        if (refreshCompleted.get() || Display.getCurrent() != null) {
            return;
        }
        Job job = prepareInitialRefreshJob();
        if (job == null) {
            return;
        }
        waitForJob(job, monitor);
    }

    private synchronized Job prepareInitialRefreshJob() {
        if (!refreshCompleted.get() && indexJob != null && indexJob.getState() == Job.SLEEPING) {
            indexJob.cancel();
            indexJob = null;
            refreshRequested = false;
        }
        if (!refreshCompleted.get() && indexJob == null) {
            scheduleRefresh(0L);
        }
        return indexJob;
    }

    private void waitForJob(Job job, IProgressMonitor monitor) {
        try {
            while (!refreshCompleted.get() && job.getState() != Job.NONE) {
                if (monitor != null && monitor.isCanceled()) {
                    return;
                }
                job.join(250L, monitor);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (OperationCanceledException e) {
            if (monitor != null) {
                monitor.setCanceled(true);
            }
        }
    }

    private static Map<RootKey, IPackageFragmentRoot> collectApplicationLibraryRootsWithoutSource()
            throws JavaModelException {
        IJavaModel javaModel = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
        Map<RootKey, IPackageFragmentRoot> candidates = new LinkedHashMap<>();

        for (IJavaProject project : javaModel.getJavaProjects()) {
            if (!project.exists() || !project.getProject().isOpen()) {
                continue;
            }
            for (IPackageFragmentRoot root : project.getAllPackageFragmentRoots()) {
                collectRoot(root, candidates);
            }
        }

        return candidates;
    }

    private static void collectRoot(IPackageFragmentRoot root, Map<RootKey, IPackageFragmentRoot> candidates)
            throws JavaModelException {
        if (root.getKind() != IPackageFragmentRoot.K_BINARY || !root.isArchive() || isJreRoot(root)) {
            return;
        }

        IPath path = archivePath(root);
        if (path == null || !path.toFile().isFile()) {
            return;
        }

        boolean hasSource = root.getSourceAttachmentPath() != null;
        if (!hasSource) {
            candidates.putIfAbsent(new RootKey(root.getHandleIdentifier(), path), root);
        }
    }

    private static IPath archivePath(IPackageFragmentRoot root) {
        IResource resource = root.getResource();
        if (resource != null && resource.getLocation() != null) {
            return resource.getLocation();
        }
        return root.getPath();
    }

    private static boolean isJreRoot(IPackageFragmentRoot root) {
        try {
            IClasspathEntry entry = root.getRawClasspathEntry();
            if (entry != null && entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER && entry.getPath() != null
                    && JavaRuntime.JRE_CONTAINER.equals(entry.getPath().segment(0))) {
                return true;
            }
            IClasspathEntry resolved = root.getResolvedClasspathEntry();
            return resolved != null && resolved.getEntryKind() == IClasspathEntry.CPE_CONTAINER
                    && resolved.getPath() != null && JavaRuntime.JRE_CONTAINER.equals(resolved.getPath().segment(0));
        } catch (JavaModelException e) {
            Logger.debug(e);
            return false;
        }
    }

    // Schedule a background VACUUM when the freelist is large, which indicates that orphaned
    // jar/entry rows from before the prune-on-refresh implementation have not been reclaimed
    // yet.  PRAGMA auto_vacuum = FULL (set in initSchema) keeps new databases compact, but
    // existing databases need a one-time VACUUM to activate the mode and compact the file.
    private static final long VACUUM_FREE_BYTES_THRESHOLD = 10L * 1024 * 1024; // 10 MB

    private void vacuumIfNeeded() {
        if (conn == null || freeListBytes() <= VACUUM_FREE_BYTES_THRESHOLD) {
            return;
        }
        Job job = Job.create("Compacting search index database", monitor -> { //$NON-NLS-1$
            Connection c = getConn();
            if (c == null) {
                return Status.OK_STATUS;
            }
            synchronized (dbLock) {
                try (java.sql.Statement stmt = c.createStatement()) {
                    stmt.execute("VACUUM"); //$NON-NLS-1$
                } catch (SQLException e) {
                    Logger.debug(e);
                }
            }
            return Status.OK_STATUS;
        });
        job.setSystem(true);
        job.setPriority(Job.BUILD);
        job.schedule();
    }

    private long freeListBytes() {
        synchronized (dbLock) {
            try (java.sql.Statement stmt = conn.createStatement()) {
                long pageSize;
                try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA page_size")) { //$NON-NLS-1$
                    pageSize = rs.next() ? rs.getLong(1) : 4096L;
                }
                try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA freelist_count")) { //$NON-NLS-1$
                    long freePages = rs.next() ? rs.getLong(1) : 0L;
                    return freePages * pageSize;
                }
            } catch (SQLException e) {
                Logger.debug(e);
                return 0L;
            }
        }
    }

    private Connection openDatabase() throws SQLException, IOException {
        Path dir = getCacheDirectory();
        if (dir == null) {
            throw new IOException("No cache directory available"); //$NON-NLS-1$
        }
        Path dbFile = dir.resolve("search-index.db"); //$NON-NLS-1$
        try {
            Class.forName("org.sqlite.JDBC"); //$NON-NLS-1$
        } catch (ClassNotFoundException e) {
            throw new IOException("SQLite JDBC driver not found", e); //$NON-NLS-1$
        }
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath()); //$NON-NLS-1$
        try {
            SqliteEntryStore.initSchema(c);
        } catch (SQLException e) {
            try { c.close(); } catch (SQLException ex) { Logger.debug(ex); }
            throw e;
        }
        return c;
    }

    private static Path getCacheDirectory() {
        JavaDecompilerPlugin plugin = JavaDecompilerPlugin.getDefault();
        if (plugin == null) {
            return null;
        }
        try {
            Path dir = plugin.getStateLocation().toFile().toPath().resolve("search-index"); //$NON-NLS-1$
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            Logger.debug(e);
            return null;
        }
    }

    public static final class JarIndex {

        private final long lastModified;
        private final long length;
        private final EntryStore entries;

        JarIndex(File jar, EntryStore entries) {
            this.lastModified = jar.lastModified();
            this.length = jar.length();
            this.entries = entries;
        }

        void close() {
            entries.close();
        }

        boolean matches(File jar) {
            return jar.lastModified() == lastModified && jar.length() == length;
        }

        int entryCount() {
            return entries.size();
        }

        void collect(Kind kind, String name, String qualifiedName, boolean wildcard, EntryStore.EntryConsumer consumer)
                throws CoreException {
            entries.collect(kind, name, qualifiedName, wildcard, consumer);
        }
    }

    private record RootKey(String rootHandle, IPath path) {
    }

    private record RebuildResult(boolean completed, Map<RootKey, JarIndex> indexes) {
    }

    private record JarPlan(RootKey key, IPackageFragmentRoot root, File jar, JarIndex existing,
            BytecodeJarIndexer.JarWork work, int dbJarId, int ticks) {
    }
}
