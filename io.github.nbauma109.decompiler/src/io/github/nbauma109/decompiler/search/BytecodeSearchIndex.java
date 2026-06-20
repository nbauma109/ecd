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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.concurrent.LazyInitializer;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
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
    private boolean refreshRequested;
    private Job indexJob;

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
        JavaCore.addElementChangedListener(classpathListener, ElementChangedEvent.POST_CHANGE);
        scheduleRefresh(STARTUP_DELAY);
    }

    public synchronized void stop() {
        if (!started.get()) {
            return;
        }
        JavaCore.removeElementChangedListener(classpathListener);
        started.set(false);
        if (indexJob != null) {
            indexJob.cancel();
            indexJob = null;
        }
        closeAll(indexes.getAndSet(Collections.emptyMap()).values());
        refreshCompleted.set(false);
        refreshRequested = false;
    }

    void forEachEntry(Kind kind, String name, String qualifiedName, boolean wildcard, IProgressMonitor monitor,
            EntryConsumer consumer) throws CoreException {
        waitForInitialRefresh(monitor);
        for (JarIndex index : indexes.get().values()) {
            if (monitor != null && monitor.isCanceled()) {
                return;
            }
            index.collect(kind, name, qualifiedName, wildcard, consumer);
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
        boolean scheduleAgain;
        try {
            Map<RootKey, IPackageFragmentRoot> roots = collectApplicationLibraryRootsWithoutSource();
            List<JarPlan> plans = plan(roots);
            RebuildResult rebuilt = rebuild(plans, monitor);
            if (!rebuilt.completed()) {
                return;
            }
            publish(rebuilt.indexes());
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
        Path cacheDir = getCacheDirectory();
        SubMonitor subMonitor = SubMonitor.convert(monitor, "Index application library bytecode", //$NON-NLS-1$
                totalTicks(plans));
        Map<RootKey, JarIndex> rebuilt = new LinkedHashMap<>();
        for (JarPlan plan : plans) {
            JarIndex index = rebuild(plan, cacheDir, subMonitor);
            if (index == null) {
                // Close newly-built indexes (not reused from current state) to release file channels
                Set<JarIndex> current = new HashSet<>(indexes.get().values());
                closeAll(rebuilt.values().stream().filter(idx -> !current.contains(idx)).toList());
                return new RebuildResult(false, Map.of());
            }
            rebuilt.put(plan.key(), index);
        }
        return new RebuildResult(true, rebuilt);
    }

    private JarIndex rebuild(JarPlan plan, Path cacheDir, SubMonitor subMonitor) {
        if (subMonitor.isCanceled()) {
            return null;
        }
        File jar = plan.jar();
        subMonitor.subTask(jar.getName());
        if (plan.existing() != null) {
            subMonitor.worked(1);
            return plan.existing();
        }
        JarIndex index = BytecodeJarIndexer.index(plan.root(), jar, plan.work(), cacheDir, subMonitor.split(plan.ticks()));
        if (index != null && subMonitor.isCanceled()) {
            index.close();
            return null;
        }
        return index;
    }

    private synchronized void publish(Map<RootKey, JarIndex> rebuilt) {
        if (started.get()) {
            Map<RootKey, JarIndex> old = indexes.getAndSet(Collections.unmodifiableMap(rebuilt));
            refreshCompleted.set(true);
            Set<JarIndex> reused = new HashSet<>(rebuilt.values());
            closeAll(old.values().stream().filter(idx -> !reused.contains(idx)).toList());
        } else {
            // stop() ran before publish() — close freshly-built indexes to release file channels
            Set<JarIndex> current = new HashSet<>(indexes.get().values());
            closeAll(rebuilt.values().stream().filter(idx -> !current.contains(idx)).toList());
        }
    }

    private static void closeAll(Iterable<JarIndex> toClose) {
        for (JarIndex idx : toClose) {
            idx.close();
        }
    }

    private List<JarPlan> plan(Map<RootKey, IPackageFragmentRoot> roots) {
        List<JarPlan> plans = new ArrayList<>(roots.size());
        for (Map.Entry<RootKey, IPackageFragmentRoot> rootEntry : roots.entrySet()) {
            RootKey key = rootEntry.getKey();
            IPath path = key.path();
            File jar = path.toFile();
            JarIndex existing = indexes.get().get(key);
            if (existing != null && existing.matches(jar)) {
                plans.add(new JarPlan(key, rootEntry.getValue(), jar, existing, null, 1));
            } else {
                BytecodeJarIndexer.JarWork work = BytecodeJarIndexer.plan(jar);
                if (work == null) {
                    continue; // corrupt/unreadable JAR — already logged; skip and index the rest
                }
                plans.add(new JarPlan(key, rootEntry.getValue(), jar, null, work, work.totalTicks()));
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

        private static final int[] EMPTY_POSTINGS = new int[0];
        private static long MAPPED_THRESHOLD = 32L * 1024L * 1024L;

        private final long lastModified;
        private final long length;
        private final EntryStore entries;
        private final Map<Kind, Map<String, int[]>> byKindAndName;

        JarIndex(File jar, Path cacheDir, String rootHandle, List<BytecodeSearchEntry> entries, int[] counts) {
            this.lastModified = jar.lastModified();
            this.length = jar.length();
            this.byKindAndName = buildNameIndex(entries);
            this.entries = createEntryStore(jar, cacheDir, entries, counts, rootHandle);
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

        void collect(Kind kind, String name, String qualifiedName, boolean wildcard, EntryConsumer consumer)
                throws CoreException {
            if (wildcard) {
                collectWildcard(kind, consumer);
                return;
            }
            Map<String, int[]> nameIndex = byKindAndName.get(kind);
            if (nameIndex == null) {
                return;
            }
            int[] firstPostings = postings(nameIndex, name);
            collectPostings(firstPostings, consumer);
            if (!sameKey(name, qualifiedName)) {
                collectPostingsSkipping(postings(nameIndex, qualifiedName), firstPostings, consumer);
            }
        }

        private static int[] postings(Map<String, int[]> nameIndex, String name) {
            if (StringUtils.isBlank(name)) {
                return EMPTY_POSTINGS;
            }
            return nameIndex.getOrDefault(normalizeKey(name), EMPTY_POSTINGS);
        }

        private void collectPostings(int[] postings, EntryConsumer consumer) throws CoreException {
            if (postings == null || postings.length == 0) {
                return;
            }
            for (int entryId : postings) {
                consumer.accept(entries.entry(entryId));
            }
        }

        private void collectPostingsSkipping(int[] postings, int[] skip, EntryConsumer consumer) throws CoreException {
            if (postings == null || postings.length == 0) {
                return;
            }
            for (int entryId : postings) {
                if (skip.length == 0 || java.util.Arrays.binarySearch(skip, entryId) < 0) {
                    consumer.accept(entries.entry(entryId));
                }
            }
        }

        private void collectWildcard(Kind kind, EntryConsumer consumer) throws CoreException {
            Map<String, int[]> nameIndex = byKindAndName.get(kind);
            if (nameIndex == null) {
                return;
            }
            BitSet emitted = new BitSet(entries.size());
            for (int[] postings : nameIndex.values()) {
                for (int entryId : postings) {
                    if (!emitted.get(entryId)) {
                        emitted.set(entryId);
                        consumer.accept(entries.entry(entryId));
                    }
                }
            }
        }

        private static Map<Kind, Map<String, int[]>> buildNameIndex(
                List<BytecodeSearchEntry> entries) {
            Map<Kind, Map<String, IntPostings>> builders = new EnumMap<>(Kind.class);
            for (int entryId = 0; entryId < entries.size(); entryId++) {
                BytecodeSearchEntry entry = entries.get(entryId);
                Map<String, IntPostings> nameIndex = builders.computeIfAbsent(entry.getKind(),
                        key -> new HashMap<>());
                addToNameIndex(nameIndex, entry.getName(), entryId);
                if (indexesQualifiedName(entry.getKind())
                        && !Strings.CS.equals(normalizeKey(entry.getName()), normalizeKey(entry.getQualifiedName()))) {
                    addToNameIndex(nameIndex, entry.getQualifiedName(), entryId);
                }
            }
            return freeze(builders);
        }

        private static void addToNameIndex(Map<String, IntPostings> nameIndex, String name, int entryId) {
            if (StringUtils.isBlank(name)) {
                return;
            }
            nameIndex.computeIfAbsent(normalizeKey(name), key -> new IntPostings()).add(entryId);
        }

        private static Map<Kind, Map<String, int[]>> freeze(Map<Kind, Map<String, IntPostings>> builders) {
            Map<Kind, Map<String, int[]>> frozen = new EnumMap<>(Kind.class);
            for (Map.Entry<Kind, Map<String, IntPostings>> kindEntry : builders.entrySet()) {
                Map<String, int[]> nameIndex = new HashMap<>();
                for (Map.Entry<String, IntPostings> entry : kindEntry.getValue().entrySet()) {
                    nameIndex.put(entry.getKey(), entry.getValue().toArray());
                }
                frozen.put(kindEntry.getKey(), Collections.unmodifiableMap(nameIndex));
            }
            return Collections.unmodifiableMap(frozen);
        }

        private static String normalizeKey(String name) {
            return StringUtils.lowerCase(name, java.util.Locale.ROOT);
        }

        private static boolean sameKey(String left, String right) {
            return Strings.CS.equals(normalizeKey(left), normalizeKey(right));
        }

        private static boolean indexesQualifiedName(Kind kind) {
            return kind == Kind.TYPE || kind == Kind.PACKAGE || kind == Kind.MODULE;
        }

        private static EntryStore createEntryStore(File jar, Path cacheDir,
                List<BytecodeSearchEntry> entries, int[] counts, String rootHandle) {
            if (cacheDir != null && rootHandle != null && estimateBytes(entries) > MAPPED_THRESHOLD
                    && !hasAnonymousHandles(entries)) {
                Path segmentFile = MappedEntryStore.segmentPath(cacheDir, jar, rootHandle);
                try {
                    return MappedEntryStore.openOrCreate(jar, cacheDir, entries, counts, rootHandle);
                } catch (IOException | RuntimeException e) {
                    JavaDecompilerPlugin.logError(e, "Mapped entry store failed; rebuilding as heap for " //$NON-NLS-1$
                            + jar.getName());
                    MappedEntryStore.deleteQuietly(segmentFile);
                }
            }
            return HeapEntryStore.from(entries, counts);
        }

        private static boolean hasAnonymousHandles(List<BytecodeSearchEntry> entries) {
            for (BytecodeSearchEntry e : entries) {
                String h = e.getElementHandle();
                if (h != null && h.contains("[~")) { //$NON-NLS-1$
                    return true;
                }
            }
            return false;
        }

        private static long estimateBytes(List<BytecodeSearchEntry> entries) {
            long total = entries.size() * 26L; // column bytes per entry
            for (BytecodeSearchEntry e : entries) {
                total += stringEstimate(e.getName());
                total += stringEstimate(e.getQualifiedName());
                total += stringEstimate(e.getDeclaringTypeName());
                total += stringEstimate(e.getDescriptor());
                total += stringEstimate(e.getElementHandle());
            }
            return total;
        }

        private static long stringEstimate(String s) {
            return s == null ? 0L : 48L + s.length() * 2L;
        }

        private static final class IntPostings {

            private int[] values = new int[4];
            private int size;

            private void add(int value) {
                if (size == values.length) {
                    values = java.util.Arrays.copyOf(values, values.length * 2);
                }
                values[size++] = value;
            }

            private int[] toArray() {
                return java.util.Arrays.copyOf(values, size);
            }
        }
    }

    @FunctionalInterface
    interface EntryConsumer {
        void accept(BytecodeSearchEntry entry) throws CoreException;
    }

    private record RootKey(String rootHandle, IPath path) {
    }

    private record RebuildResult(boolean completed, Map<RootKey, JarIndex> indexes) {
    }

    private record JarPlan(RootKey key, IPackageFragmentRoot root, File jar, JarIndex existing,
            BytecodeJarIndexer.JarWork work, int ticks) {
    }
}
