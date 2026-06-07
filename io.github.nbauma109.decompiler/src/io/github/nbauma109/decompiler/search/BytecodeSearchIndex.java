/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
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
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Access;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.TypeCategory;
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
        indexes.set(Collections.emptyMap());
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
        SubMonitor subMonitor = SubMonitor.convert(monitor, "Index application library bytecode", //$NON-NLS-1$
                totalTicks(plans));
        Map<RootKey, JarIndex> rebuilt = new LinkedHashMap<>();
        for (JarPlan plan : plans) {
            JarIndex index = rebuild(plan, subMonitor);
            if (index == null) {
                return new RebuildResult(false, Map.of());
            }
            rebuilt.put(plan.key(), index);
        }
        return new RebuildResult(true, rebuilt);
    }

    private JarIndex rebuild(JarPlan plan, SubMonitor subMonitor) {
        if (subMonitor.isCanceled()) {
            return null;
        }
        File jar = plan.jar();
        subMonitor.subTask(jar.getName());
        if (plan.existing() != null) {
            subMonitor.worked(1);
            return plan.existing();
        }
        JarIndex index = BytecodeJarIndexer.index(plan.root(), jar, plan.work(), subMonitor.split(plan.ticks()));
        return index == null || subMonitor.isCanceled() ? null : index;
    }

    private synchronized void publish(Map<RootKey, JarIndex> rebuilt) {
        if (started.get()) {
            indexes.set(Collections.unmodifiableMap(rebuilt));
            refreshCompleted.set(true);
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

    public static final class JarIndex {

        private static final int[] EMPTY_POSTINGS = new int[0];

        private final long lastModified;
        private final long length;
        private final CompactEntries entries;
        private final Map<Kind, Map<String, int[]>> byKindAndName;

        JarIndex(File jar, List<BytecodeSearchEntry> entries) {
            this.lastModified = jar.lastModified();
            this.length = jar.length();
            this.byKindAndName = buildNameIndex(entries);
            this.entries = CompactEntries.from(entries);
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

        public static final class CompactEntries {

            private static final int NULL_ID = -1;

            private final String[] strings;
            private final String[] elementHandles;
            private final IJavaElement[] anonymousElementFallbacks;
            private final byte[] kindAndFlags;
            private final int[] elementHandleIds;
            private final int[] nameIds;
            private final int[] qualifiedNameIds;
            private final int[] declaringTypeNameIds;
            private final int[] descriptorIds;
            private final byte[] typeCategoryIds;

            private CompactEntries(EntryArrays arrays) {
                this.strings = arrays.tables().strings();
                this.elementHandles = arrays.tables().elementHandles();
                this.anonymousElementFallbacks = arrays.tables().anonymousElementFallbacks();
                this.kindAndFlags = arrays.columns().kindAndFlags();
                this.elementHandleIds = arrays.columns().elementHandleIds();
                this.nameIds = arrays.columns().nameIds();
                this.qualifiedNameIds = arrays.columns().qualifiedNameIds();
                this.declaringTypeNameIds = arrays.columns().declaringTypeNameIds();
                this.descriptorIds = arrays.columns().descriptorIds();
                this.typeCategoryIds = arrays.columns().typeCategoryIds();
            }

            private static CompactEntries from(List<BytecodeSearchEntry> entries) {
                Dictionary strings = new Dictionary();
                ElementDictionary elements = new ElementDictionary();
                int size = entries.size();
                byte[] kindAndFlags = new byte[size];
                int[] elementHandleIds = new int[size];
                int[] nameIds = new int[size];
                int[] qualifiedNameIds = new int[size];
                int[] declaringTypeNameIds = new int[size];
                int[] descriptorIds = new int[size];
                byte[] typeCategoryIds = new byte[size];
                for (int i = 0; i < size; i++) {
                    BytecodeSearchEntry entry = entries.get(i);
                    kindAndFlags[i] = kindAndFlags(entry);
                    elementHandleIds[i] = elements.id(entry.getElementHandle(), entry.getAnonymousElementFallback());
                    nameIds[i] = strings.id(entry.getName());
                    qualifiedNameIds[i] = strings.id(entry.getQualifiedName());
                    declaringTypeNameIds[i] = strings.id(entry.getDeclaringTypeName());
                    descriptorIds[i] = strings.id(entry.getDescriptor());
                    typeCategoryIds[i] = (byte) entry.getTypeCategory().ordinal();
                }
                StringTables tables = new StringTables(strings.values(), elements.handles(), elements.fallbacks());
                EntryColumns columns = new EntryColumns(kindAndFlags, elementHandleIds, nameIds, qualifiedNameIds,
                        declaringTypeNameIds, descriptorIds, typeCategoryIds);
                return new CompactEntries(new EntryArrays(tables, columns));
            }

            private record EntryArrays(StringTables tables, EntryColumns columns) {
            }

            public record StringTables(String[] strings, String[] elementHandles,
                    IJavaElement[] anonymousElementFallbacks) {

                @Override
                public boolean equals(Object other) {
                    if (this == other) {
                        return true;
                    }
                    if (other == null || other.getClass() != getClass()) {
                        return false;
                    }
                    StringTables that = (StringTables) other;
                    return new EqualsBuilder()
                            .append(strings, that.strings)
                            .append(elementHandles, that.elementHandles)
                            .append(anonymousElementFallbacks, that.anonymousElementFallbacks)
                            .isEquals();
                }

                @Override
                public int hashCode() {
                    return new HashCodeBuilder(17, 37)
                            .append(strings)
                            .append(elementHandles)
                            .append(anonymousElementFallbacks)
                            .toHashCode();
                }

                @Override
                public String toString() {
                    return new ToStringBuilder(this)
                            .append("strings", strings) //$NON-NLS-1$
                            .append("elementHandles", elementHandles) //$NON-NLS-1$
                            .append("anonymousElementFallbacks", anonymousElementFallbacks) //$NON-NLS-1$
                            .toString();
                }
            }

            public record EntryColumns(byte[] kindAndFlags, int[] elementHandleIds, int[] nameIds,
                    int[] qualifiedNameIds, int[] declaringTypeNameIds, int[] descriptorIds, byte[] typeCategoryIds) {

                @Override
                public boolean equals(Object other) {
                    if (this == other) {
                        return true;
                    }
                    if (other == null || other.getClass() != getClass()) {
                        return false;
                    }
                    EntryColumns that = (EntryColumns) other;
                    return new EqualsBuilder()
                            .append(kindAndFlags, that.kindAndFlags)
                            .append(elementHandleIds, that.elementHandleIds)
                            .append(nameIds, that.nameIds)
                            .append(qualifiedNameIds, that.qualifiedNameIds)
                            .append(declaringTypeNameIds, that.declaringTypeNameIds)
                            .append(descriptorIds, that.descriptorIds)
                            .append(typeCategoryIds, that.typeCategoryIds)
                            .isEquals();
                }

                @Override
                public int hashCode() {
                    return new HashCodeBuilder(17, 37)
                            .append(kindAndFlags)
                            .append(elementHandleIds)
                            .append(nameIds)
                            .append(qualifiedNameIds)
                            .append(declaringTypeNameIds)
                            .append(descriptorIds)
                            .append(typeCategoryIds)
                            .toHashCode();
                }

                @Override
                public String toString() {
                    return new ToStringBuilder(this)
                            .append("kindAndFlags", kindAndFlags) //$NON-NLS-1$
                            .append("elementHandleIds", elementHandleIds) //$NON-NLS-1$
                            .append("nameIds", nameIds) //$NON-NLS-1$
                            .append("qualifiedNameIds", qualifiedNameIds) //$NON-NLS-1$
                            .append("declaringTypeNameIds", declaringTypeNameIds) //$NON-NLS-1$
                            .append("descriptorIds", descriptorIds) //$NON-NLS-1$
                            .append("typeCategoryIds", typeCategoryIds) //$NON-NLS-1$
                            .toString();
                }
            }

            private int size() {
                return kindAndFlags.length;
            }

            private BytecodeSearchEntry entry(int entryId) {
                int elementHandleId = elementHandleIds[entryId];
                return new BytecodeSearchEntry(kind(entryId), declaration(entryId),
                        BytecodeSearchEntry.elementReference(elementHandle(elementHandleId),
                                anonymousElementFallback(elementHandleId)),
                        BytecodeSearchEntry.symbolReference(string(nameIds[entryId]),
                                string(qualifiedNameIds[entryId]), string(declaringTypeNameIds[entryId]),
                                string(descriptorIds[entryId])),
                        access(entryId), typeCategory(entryId));
            }

            private Kind kind(int entryId) {
                return Kind.values()[kindAndFlags[entryId] & 0x0F];
            }

            private boolean declaration(int entryId) {
                return (kindAndFlags[entryId] & 0x10) != 0;
            }

            private Access access(int entryId) {
                return Access.values()[(kindAndFlags[entryId] >>> 5) & 0x03];
            }

            private TypeCategory typeCategory(int entryId) {
                return TypeCategory.values()[typeCategoryIds[entryId]];
            }

            private String string(int id) {
                return id == NULL_ID ? null : strings[id];
            }

            private String elementHandle(int id) {
                return id == NULL_ID ? null : elementHandles[id];
            }

            private IJavaElement anonymousElementFallback(int id) {
                return id == NULL_ID ? null : anonymousElementFallbacks[id];
            }

            private static byte kindAndFlags(BytecodeSearchEntry entry) {
                int flags = entry.getKind().ordinal();
                if (entry.isDeclaration()) {
                    flags |= 0x10;
                }
                flags |= entry.getAccess().ordinal() << 5;
                return (byte) flags;
            }
        }

        private static class Dictionary {

            private final Map<String, Integer> ids = new HashMap<>();
            private final List<String> values = new ArrayList<>();

            private int id(String value) {
                if (value == null) {
                    return CompactEntries.NULL_ID;
                }
                Integer existing = ids.get(value);
                if (existing != null) {
                    return existing.intValue();
                }
                int id = values.size();
                ids.put(value, Integer.valueOf(id));
                values.add(value);
                return id;
            }

            protected String[] values() {
                return values.toArray(String[]::new);
            }
        }

        private static final class ElementDictionary extends Dictionary {

            private final List<IJavaElement> fallbacks = new ArrayList<>();

            private int id(String handle, IJavaElement fallback) {
                int id = super.id(handle);
                if (id != CompactEntries.NULL_ID) {
                    while (fallbacks.size() <= id) {
                        fallbacks.add(null);
                    }
                    if (fallbacks.get(id) == null && fallback != null) {
                        fallbacks.set(id, fallback);
                    }
                }
                return id;
            }

            private String[] handles() {
                return values();
            }

            private IJavaElement[] fallbacks() {
                return fallbacks.toArray(IJavaElement[]::new);
            }
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
