/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.ResourcesPlugin;
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

    private static final BytecodeSearchIndex INSTANCE = new BytecodeSearchIndex();
    private static final long STARTUP_DELAY = 5000L;
    private static final long REFRESH_DELAY = 2000L;
    private static final int CLASSPATH_FLAGS = IJavaElementDelta.F_CLASSPATH_CHANGED
            | IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED
            | IJavaElementDelta.F_ADDED_TO_CLASSPATH
            | IJavaElementDelta.F_REMOVED_FROM_CLASSPATH
            | IJavaElementDelta.F_CLASSPATH_REORDER
            | IJavaElementDelta.F_SOURCEATTACHED
            | IJavaElementDelta.F_SOURCEDETACHED
            | IJavaElementDelta.F_ARCHIVE_CONTENT_CHANGED
            | IJavaElementDelta.F_CLASSPATH_ATTRIBUTES;

    private final IElementChangedListener classpathListener = this::classpathChanged;

    private volatile Map<IPath, JarIndex> indexes = Collections.emptyMap();
    private volatile boolean started;
    private volatile boolean refreshCompleted;
    private boolean refreshRequested;
    private Job indexJob;

    private BytecodeSearchIndex() {
    }

    public static BytecodeSearchIndex getDefault() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        JavaCore.addElementChangedListener(classpathListener, ElementChangedEvent.POST_CHANGE);
        scheduleRefresh(STARTUP_DELAY);
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }
        JavaCore.removeElementChangedListener(classpathListener);
        started = false;
        if (indexJob != null) {
            indexJob.cancel();
            indexJob = null;
        }
        indexes = Collections.emptyMap();
        refreshCompleted = false;
        refreshRequested = false;
    }

    List<BytecodeSearchEntry> entries(Kind kind, String name, String qualifiedName, boolean wildcard,
            IProgressMonitor monitor) throws CoreException {
        waitForInitialRefresh(monitor);
        List<BytecodeSearchEntry> result = new ArrayList<>();
        for (JarIndex index : indexes.values()) {
            index.collect(kind, name, qualifiedName, wildcard, result);
        }
        return result;
    }

    int entryCount() {
        int count = 0;
        for (JarIndex index : indexes.values()) {
            count += index.entryCount();
        }
        return count;
    }

    synchronized void scheduleRefresh() {
        scheduleRefresh(REFRESH_DELAY);
    }

    private synchronized void scheduleRefresh(long delay) {
        if (!started) {
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
            Map<IPath, IPackageFragmentRoot> roots = collectApplicationLibraryRootsWithoutSource();
            List<JarPlan> plans = plan(roots);
            SubMonitor subMonitor = SubMonitor.convert(monitor, "Index application library bytecode", //$NON-NLS-1$
                    totalTicks(plans));
            Map<IPath, JarIndex> rebuilt = new LinkedHashMap<>();
            for (JarPlan plan : plans) {
                if (subMonitor.isCanceled()) {
                    return;
                }
                File jar = plan.jar();
                subMonitor.subTask(jar.getName());
                if (plan.existing() != null) {
                    rebuilt.put(plan.path(), plan.existing());
                    subMonitor.worked(1);
                } else {
                    rebuilt.put(plan.path(), BytecodeJarIndexer.index(plan.root(), jar, plan.work(),
                            subMonitor.split(plan.ticks())));
                }
            }
            indexes = Collections.unmodifiableMap(rebuilt);
            refreshCompleted = true;
        } catch (CoreException | RuntimeException e) {
            JavaDecompilerPlugin.logError(e, "Failed to index application library bytecode"); //$NON-NLS-1$
        } finally {
            monitor.done();
            synchronized (this) {
                indexJob = null;
                scheduleAgain = refreshRequested && started;
                refreshRequested = false;
            }
        }
        if (scheduleAgain) {
            scheduleRefresh();
        }
    }

    private List<JarPlan> plan(Map<IPath, IPackageFragmentRoot> roots) {
        List<JarPlan> plans = new ArrayList<>(roots.size());
        for (Map.Entry<IPath, IPackageFragmentRoot> rootEntry : roots.entrySet()) {
            IPath path = rootEntry.getKey();
            File jar = path.toFile();
            JarIndex existing = indexes.get(path);
            if (existing != null && existing.matches(jar)) {
                plans.add(new JarPlan(rootEntry.getValue(), jar, path, existing, null, 1));
            } else {
                BytecodeJarIndexer.JarWork work = BytecodeJarIndexer.plan(jar);
                plans.add(new JarPlan(rootEntry.getValue(), jar, path, null, work,
                        BytecodeJarIndexer.impactTicks(work.totalImpact())));
            }
        }
        return plans;
    }

    private static int totalTicks(List<JarPlan> plans) {
        long total = 0L;
        for (JarPlan plan : plans) {
            total += plan.ticks();
        }
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, total));
    }

    private void waitForInitialRefresh(IProgressMonitor monitor) throws CoreException {
        start();
        if (refreshCompleted || Display.getCurrent() != null) {
            return;
        }
        Job job;
        synchronized (this) {
            if (!refreshCompleted && indexJob != null && indexJob.getState() == Job.SLEEPING) {
                indexJob.cancel();
                indexJob = null;
                refreshRequested = false;
            }
            if (!refreshCompleted && indexJob == null) {
                scheduleRefresh(0L);
            }
            job = indexJob;
        }
        if (job == null) {
            return;
        }
        try {
            while (!refreshCompleted && job.getState() != Job.NONE) {
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

    private static Map<IPath, IPackageFragmentRoot> collectApplicationLibraryRootsWithoutSource()
            throws JavaModelException {
        IJavaModel javaModel = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
        Map<IPath, IPackageFragmentRoot> candidates = new LinkedHashMap<>();
        Map<IPath, Boolean> sourceAttached = new HashMap<>();

        for (IJavaProject project : javaModel.getJavaProjects()) {
            if (!project.exists() || !project.getProject().isOpen()) {
                continue;
            }
            for (IPackageFragmentRoot root : project.getAllPackageFragmentRoots()) {
                collectRoot(root, candidates, sourceAttached);
            }
        }

        candidates.keySet().removeIf(path -> Boolean.TRUE.equals(sourceAttached.get(path)));
        return candidates;
    }

    private static void collectRoot(IPackageFragmentRoot root, Map<IPath, IPackageFragmentRoot> candidates,
            Map<IPath, Boolean> sourceAttached) throws JavaModelException {
        if (root.getKind() != IPackageFragmentRoot.K_BINARY || !root.isArchive() || isJreRoot(root)) {
            return;
        }

        IPath path = root.getPath();
        if (path == null || !"jar".equalsIgnoreCase(path.getFileExtension()) || !path.toFile().isFile()) { //$NON-NLS-1$
            return;
        }

        boolean hasSource = root.getSourceAttachmentPath() != null;
        sourceAttached.merge(path, Boolean.valueOf(hasSource), Boolean::logicalOr);
        if (!hasSource) {
            candidates.putIfAbsent(path, root);
        }
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

    static final class JarIndex {

        private final long lastModified;
        private final long length;
        private final Map<Kind, Map<String, List<BytecodeSearchEntry>>> byKindAndName;
        private final int entryCount;

        JarIndex(File jar, List<BytecodeSearchEntry> entries) {
            this.lastModified = jar.lastModified();
            this.length = jar.length();
            this.entryCount = entries.size();
            this.byKindAndName = buildNameIndex(entries);
        }

        boolean matches(File jar) {
            return jar.lastModified() == lastModified && jar.length() == length;
        }

        int entryCount() {
            return entryCount;
        }

        void collect(Kind kind, String name, String qualifiedName, boolean wildcard, List<BytecodeSearchEntry> result) {
            if (wildcard) {
                collectWildcard(kind, result);
                return;
            }
            Map<String, List<BytecodeSearchEntry>> nameIndex = byKindAndName.get(kind);
            if (nameIndex == null) {
                return;
            }
            Set<BytecodeSearchEntry> seen = new HashSet<>();
            collectExact(nameIndex, name, result, seen);
            collectExact(nameIndex, qualifiedName, result, seen);
        }

        private static void collectExact(Map<String, List<BytecodeSearchEntry>> nameIndex, String name,
                List<BytecodeSearchEntry> result, Set<BytecodeSearchEntry> seen) {
            if (name == null || name.isBlank()) {
                return;
            }
            for (BytecodeSearchEntry entry : nameIndex.getOrDefault(normalizeKey(name), Collections.emptyList())) {
                if (seen.add(entry)) {
                    result.add(entry);
                }
            }
        }

        private void collectWildcard(Kind kind, List<BytecodeSearchEntry> result) {
            Map<String, List<BytecodeSearchEntry>> nameIndex = byKindAndName.get(kind);
            if (nameIndex == null) {
                return;
            }
            Set<BytecodeSearchEntry> seen = new HashSet<>();
            for (List<BytecodeSearchEntry> bucket : nameIndex.values()) {
                for (BytecodeSearchEntry entry : bucket) {
                    if (seen.add(entry)) {
                        result.add(entry);
                    }
                }
            }
        }

        private static Map<Kind, Map<String, List<BytecodeSearchEntry>>> buildNameIndex(
                List<BytecodeSearchEntry> entries) {
            Map<Kind, Map<String, List<BytecodeSearchEntry>>> result = new EnumMap<>(Kind.class);
            for (BytecodeSearchEntry entry : entries) {
                Map<String, List<BytecodeSearchEntry>> nameIndex = result.computeIfAbsent(entry.getKind(),
                        key -> new HashMap<>());
                addToNameIndex(nameIndex, entry.getName(), entry);
                if (indexesQualifiedName(entry.getKind())
                        && !normalizeKey(entry.getName()).equals(normalizeKey(entry.getQualifiedName()))) {
                    addToNameIndex(nameIndex, entry.getQualifiedName(), entry);
                }
            }
            freeze(result);
            return result;
        }

        private static void addToNameIndex(Map<String, List<BytecodeSearchEntry>> nameIndex, String name,
                BytecodeSearchEntry entry) {
            if (name == null || name.isBlank()) {
                return;
            }
            nameIndex.computeIfAbsent(normalizeKey(name), key -> new ArrayList<>()).add(entry);
        }

        private static void freeze(Map<Kind, Map<String, List<BytecodeSearchEntry>>> index) {
            ArrayDeque<Kind> keys = new ArrayDeque<>(index.keySet());
            while (!keys.isEmpty()) {
                Kind kind = keys.removeFirst();
                Map<String, List<BytecodeSearchEntry>> nameIndex = index.get(kind);
                for (Map.Entry<String, List<BytecodeSearchEntry>> entry : nameIndex.entrySet()) {
                    entry.setValue(List.copyOf(entry.getValue()));
                }
                index.put(kind, Collections.unmodifiableMap(nameIndex));
            }
        }

        private static String normalizeKey(String name) {
            return name.toLowerCase(java.util.Locale.ROOT);
        }

        private static boolean indexesQualifiedName(Kind kind) {
            return kind == Kind.TYPE || kind == Kind.PACKAGE || kind == Kind.MODULE;
        }
    }

    private record JarPlan(IPackageFragmentRoot root, File jar, IPath path, JarIndex existing,
            BytecodeJarIndexer.JarWork work, int ticks) {
    }
}
