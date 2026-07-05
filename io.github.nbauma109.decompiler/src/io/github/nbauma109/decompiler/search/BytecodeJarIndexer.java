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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Signature;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Access;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.TypeCategory;
import io.github.nbauma109.decompiler.util.HashUtils;
import io.github.nbauma109.decompiler.util.Logger;

public class BytecodeJarIndexer {

    private static final String CLASS_FILE_EXTENSION = ".class"; //$NON-NLS-1$
    private static final String CLASS_INITIALIZER = "<clinit>"; //$NON-NLS-1$
    private static final String CONSTRUCTOR = "<init>"; //$NON-NLS-1$
    private static final String LAMBDA_METHOD_PREFIX = "lambda$"; //$NON-NLS-1$
    private static final String META_INF_VERSIONS = "META-INF/versions/"; //$NON-NLS-1$
    private static final String MODULE_INFO = "module-info"; //$NON-NLS-1$
    private static final String ANNOTATION_INTERNAL_NAME = "java/lang/annotation/Annotation"; //$NON-NLS-1$
    private static final String ENUM_INTERNAL_NAME = "java/lang/Enum"; //$NON-NLS-1$
    private static final String OBJECT_INTERNAL_NAME = "java/lang/Object"; //$NON-NLS-1$
    private static final String FAILED_TO_INDEX_JAR = "Failed to index jar "; //$NON-NLS-1$
    private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF"; //$NON-NLS-1$
    private static final Attributes.Name MULTI_RELEASE = new Attributes.Name("Multi-Release"); //$NON-NLS-1$
    private static final int ZIP_LOCAL_FILE_HEADER_SIZE = 30;
    private static final int ZIP_CENTRAL_DIRECTORY_FILE_HEADER_SIZE = 46;

    private BytecodeJarIndexer() {
    }

    public static JarWork plan(File jar) {
        try (ZipFile zip = new ZipFile(jar)) {
            Map<String, EffectiveClassEntry> effectiveEntries = new LinkedHashMap<>();
            boolean multiRelease = isMultiReleaseJar(zip);
            int runtimeFeatureVersion = Runtime.version().feature();
            Enumeration<? extends ZipEntry> zipEntries = zip.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                EffectiveClassEntry candidate = effectiveClassEntry(entry, multiRelease, runtimeFeatureVersion);
                if (candidate != null) {
                    effectiveEntries.merge(candidate.logicalName(), candidate, EffectiveClassEntry::newer);
                }
            }
        } catch (IOException | java.io.UncheckedIOException | IllegalStateException e) {
            JavaDecompilerPlugin.logError(e, "Failed to inspect jar " + jar.getAbsolutePath()); //$NON-NLS-1$
            return null;
        }
    }

    private static JarWork jarWork(Collection<EffectiveClassEntry> effectiveEntries, String contentHash) {
        List<JarEntryWork> entries = new ArrayList<>(effectiveEntries.size());
        long totalImpact = 0L;
        long totalTicks = 0L;
        for (EffectiveClassEntry entry : effectiveEntries) {
            totalImpact += entry.impactBytes();
            totalTicks = Math.clamp(totalTicks + entry.ticks(), 0L, Integer.MAX_VALUE);
            entries.add(new JarEntryWork(entry.entryName(), entry.impactBytes(), entry.ticks()));
        }
        // Ordered fingerprint: sorted by logicalName so swapped or renamed entries produce a different hash
        List<EffectiveClassEntry> sorted = new ArrayList<>(effectiveEntries);
        sorted.sort(Comparator.comparing(EffectiveClassEntry::logicalName));
        CRC32 fingerprint = new CRC32();
        ByteBuffer buf = ByteBuffer.allocate(16);
        for (EffectiveClassEntry entry : sorted) {
            fingerprint.update(entry.logicalName().getBytes(StandardCharsets.UTF_8));
            buf.clear();
            buf.putLong(entry.impactBytes());
            buf.putLong(entry.entryCrc());
            fingerprint.update(buf.array());
        }
        return new JarWork(entries, totalImpact, (int) totalTicks, fingerprint.getValue(), contentHash);
    }

    private static boolean isMultiReleaseJar(ZipFile zip) throws IOException {
        ZipEntry manifestEntry = zip.getEntry(MANIFEST_NAME);
        if (manifestEntry == null) {
            return false;
        }
        try (InputStream input = zip.getInputStream(manifestEntry)) {
            Manifest manifest = new Manifest(input);
            String multiRelease = manifest.getMainAttributes().getValue(MULTI_RELEASE);
            return Boolean.parseBoolean(multiRelease);
        }
    }

    private static EffectiveClassEntry effectiveClassEntry(ZipEntry entry, boolean multiRelease,
            int runtimeFeatureVersion) {
        if (entry.isDirectory() || !Strings.CS.endsWith(entry.getName(), CLASS_FILE_EXTENSION)) {
            return null;
        }
        if (!Strings.CS.startsWith(entry.getName(), META_INF_VERSIONS)) {
            return newEffectiveClassEntry(entry.getName(), entry.getName(), 0, entry);
        }
        VersionedClassName versionedName = versionedClassName(entry.getName());
        if (versionedName == null || !multiRelease || versionedName.version() > runtimeFeatureVersion) {
            return null;
        }
        return newEffectiveClassEntry(versionedName.logicalName(), entry.getName(), versionedName.version(), entry);
    }

    private static EffectiveClassEntry newEffectiveClassEntry(String logicalName, String entryName, int version,
            ZipEntry entry) {
        long impact = entryImpactBytes(entry);
        return new EffectiveClassEntry(logicalName, entryName, version, impact, impactTicks(impact), entry.getCrc());
    }

    private static VersionedClassName versionedClassName(String entryName) {
        if (!Strings.CS.startsWith(entryName, META_INF_VERSIONS)) {
            return null;
        }
        String versionedPath = entryName.substring(META_INF_VERSIONS.length());
        int separator = versionedPath.indexOf('/');
        if (separator <= 0 || separator == versionedPath.length() - 1) {
            return null;
        }
        try {
            int version = Integer.parseInt(versionedPath.substring(0, separator));
            if (version < 9) {
                return null;
            }
            return new VersionedClassName(versionedPath.substring(separator + 1), version);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BytecodeSearchIndex.JarIndex indexToHeap(IPackageFragmentRoot root, File jar, JarWork work,
            IProgressMonitor monitor) {
        SubMonitor subMonitor = SubMonitor.convert(monitor, jar.getName(), work.totalTicks());
        EntryWriter heapWriter = new EntryWriter();
        try (ZipFile zip = new ZipFile(jar)) {
            Map<String, String> strings = new HashMap<>();
            IndexContext context = new IndexContext(root, jar, zip, heapWriter, strings, new HashMap<>());
            for (JarEntryWork entryWork : work.entries()) {
                if (subMonitor.isCanceled()) {
                    return null;
                }
                indexEntry(context, entryWork, subMonitor);
            }
        } catch (IOException | RuntimeException e) {
            JavaDecompilerPlugin.logError(e, FAILED_TO_INDEX_JAR + jar.getAbsolutePath()); //$NON-NLS-1$
            return null;
        }
        HeapEntryStore store = heapWriter.buildHeapStore();
        return new BytecodeSearchIndex.JarIndex(jar, store);
    }

    public static BytecodeSearchIndex.JarIndex index(IPackageFragmentRoot root, File jar, JarWork work,
            Connection conn, Object dbLock, IProgressMonitor monitor) {
        final boolean ownsConn = conn == null;
        final Connection activeConn;
        final Object lock;
        if (ownsConn) {
            try {
                activeConn = SqliteEntryStore.openInMemoryDatabase();
            } catch (SQLException e) {
                Logger.debug(e);
                return indexToHeap(root, jar, work, monitor); // SQLite driver unavailable; fall back to heap
            }
            lock = new Object();
        } else {
            activeConn = conn;
            lock = dbLock;
        }
        String rootHandle = root != null ? root.getHandleIdentifier() : ""; //$NON-NLS-1$
        Map<String, String> strings = new HashMap<>();
        String insertSql =
                "INSERT INTO entries(jar_id,kind,declaration,access_flags,type_category," + //$NON-NLS-1$
                        "element_handle_id,name_id,qualified_name_id," + //$NON-NLS-1$
                        "declaring_type_name_id,descriptor_id,occurrence_count,fallback_handle_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)"; //$NON-NLS-1$
        String updateSql = "UPDATE entries SET occurrence_count = occurrence_count + 1 WHERE id = ?"; //$NON-NLS-1$
        try {
            synchronized (lock) {
                activeConn.setAutoCommit(false);
            }
            SqliteEntryStore.JarRegistration reg;
            synchronized (lock) {
                reg = SqliteEntryStore.registerJar(activeConn, rootHandle,
                        jar.getAbsolutePath(), jar.lastModified(), jar.length(),
                        Runtime.version().feature(), work.fileCrc(), work.contentHash());
            }
            int jarId = reg.jarId();
            if (!reg.needsIndexing()) {
                synchronized (lock) {
                    activeConn.commit();
                    activeConn.setAutoCommit(true);
                }
                return new BytecodeSearchIndex.JarIndex(jar,
                        new SqliteEntryStore(activeConn, lock, jarId, ownsConn, rootHandle));
            }
            try (ZipFile zip = new ZipFile(jar);
                    PreparedStatement insertPs = prepareLocked(activeConn, lock, insertSql, Statement.RETURN_GENERATED_KEYS);
                    PreparedStatement updatePs = prepareLocked(activeConn, lock, updateSql);
                    PreparedStatement internInsPs = prepareLocked(activeConn, lock, "INSERT OR IGNORE INTO strings(value) VALUES(?)"); //$NON-NLS-1$
                    PreparedStatement internSelPs = prepareLocked(activeConn, lock, "SELECT id FROM strings WHERE value = ?")) { //$NON-NLS-1$
                EntryWriter writer = new EntryWriter(jarId, insertPs, updatePs, internInsPs, internSelPs, lock);
                Map<String, TypeCategory> typeCategoryCache = new HashMap<>();
                IndexContext context = new IndexContext(root, jar, zip, writer, strings, typeCategoryCache);
                SubMonitor subMonitor = SubMonitor.convert(monitor, work.totalTicks());
                for (JarEntryWork entryWork : work.entries()) {
                    if (subMonitor.isCanceled()) {
                        abortConn(activeConn, lock, ownsConn);
                        return null;
                    }
                    indexEntry(context, entryWork, subMonitor);
                }
                if (subMonitor.isCanceled()) {
                    abortConn(activeConn, lock, ownsConn);
                    return null;
                }
            }
            synchronized (lock) {
                activeConn.commit();
                activeConn.setAutoCommit(true);
            }
            return new BytecodeSearchIndex.JarIndex(jar,
                    new SqliteEntryStore(activeConn, lock, jarId, ownsConn, rootHandle));
        } catch (IOException | SQLException e) {
            JavaDecompilerPlugin.logError(e, FAILED_TO_INDEX_JAR + jar.getAbsolutePath()); //$NON-NLS-1$
            abortConn(activeConn, lock, ownsConn);
            return null;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            JavaDecompilerPlugin.logError(cause != null ? cause : e, FAILED_TO_INDEX_JAR + jar.getAbsolutePath()); //$NON-NLS-1$
            abortConn(activeConn, lock, ownsConn);
            return null;
        }
    }

    private static PreparedStatement prepareLocked(Connection conn, Object dbLock, String sql) throws SQLException {
        final Object lock = dbLock;
        synchronized (lock) {
            return conn.prepareStatement(sql);
        }
    }

    private static PreparedStatement prepareLocked(Connection conn, Object dbLock, String sql, int autoGeneratedKeys) throws SQLException {
        final Object lock = dbLock;
        synchronized (lock) {
            return conn.prepareStatement(sql, autoGeneratedKeys);
        }
    }

    private static void abortConn(Connection activeConn, Object dbLock, boolean ownsConn) {
        final Object lock = dbLock;
        try {
            synchronized (lock) {
                activeConn.rollback();
                activeConn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            Logger.debug(ex);
        }
        if (ownsConn) {
            try {
                activeConn.close();
            } catch (SQLException ex) {
                Logger.debug(ex);
            }
        }
    }

    private static void indexEntry(IndexContext context, JarEntryWork entryWork, SubMonitor subMonitor) {
        subMonitor.subTask(entryWork.name());
        try {
            ZipEntry entry = context.zip().getEntry(entryWork.name());
            if (entry != null) {
                indexZipEntry(context, entry);
            }
        } finally {
            subMonitor.worked(entryWork.ticks());
        }
    }

    private static void indexZipEntry(IndexContext context, ZipEntry entry) {
        try (InputStream input = context.zip().getInputStream(entry)) {
            indexClass(context.root(), input, context.writer(), context.strings(), context.typeCategoryCache());
        } catch (DbWriteException e) {
            throw e; // propagate to abort the whole JAR transaction
        } catch (IOException | RuntimeException e) {
            JavaDecompilerPlugin.logError(e, "Failed to index class file from " + context.jar().getAbsolutePath()); //$NON-NLS-1$
        }
    }

    private static final class DbWriteException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DbWriteException(SQLException cause) {
            super(cause);
        }
    }

    static int impactTicks(long impactBytes) {
        long kibibytes = (impactBytes + 1023L) / 1024L;
        return (int) Math.clamp(kibibytes, 1L, Integer.MAX_VALUE);
    }

    private static long entryImpactBytes(ZipEntry entry) {
        long compressedSize = entry.getCompressedSize();
        if (compressedSize < 0L) {
            compressedSize = Math.max(0L, entry.getSize());
        }
        int nameSize = entry.getName().getBytes(StandardCharsets.UTF_8).length;
        int extraSize = entry.getExtra() == null ? 0 : entry.getExtra().length;
        int commentSize = entry.getComment() == null ? 0 : entry.getComment().getBytes(StandardCharsets.UTF_8).length;
        return compressedSize
                + ZIP_LOCAL_FILE_HEADER_SIZE + nameSize + extraSize
                + ZIP_CENTRAL_DIRECTORY_FILE_HEADER_SIZE + nameSize + extraSize + commentSize;
    }

    private static void indexClass(IPackageFragmentRoot root, InputStream input, EntryWriter writer,
            Map<String, String> strings, Map<String, TypeCategory> typeCategoryCache) throws IOException {
        ClassReader reader = new ClassReader(input);
        ClassIndex classIndex = new ClassIndex(root, writer, strings, typeCategoryCache);
        reader.accept(classIndex.visitor, ClassReader.SKIP_FRAMES);

        if (classIndex.type == null && classIndex.moduleElement == null) {
            return;
        }

        classIndex.indexDescriptors();
        classIndex.flush();
    }

    private static final class ClassIndex {

        private final IPackageFragmentRoot root;
        private final EntryWriter writer;
        private final Map<String, String> strings;
        private final Map<String, TypeCategory> typeCategoryCache;
        private final Map<String, String> elementHandles = new HashMap<>();
        private final Map<String, IJavaElement> anonymousElementFallbacks = new HashMap<>();
        private final List<String> typeReferences = new ArrayList<>();
        private final Set<String> descriptorSet = new HashSet<>();
        private final Map<IJavaElement, List<String>> typeReferencesByElement = new HashMap<>();
        private final Map<IJavaElement, List<MemberReference>> methodReferencesByElement = new HashMap<>();
        private final Map<IJavaElement, List<MemberReference>> constructorReferencesByElement = new HashMap<>();
        private final Map<IJavaElement, List<MemberReference>> fieldReferencesByElement = new HashMap<>();
        private final Map<String, NestedTypeName> nestedTypeNames = new HashMap<>();
        private final Map<LambdaMethodKey, IJavaElement> lambdaBodyOwners = new HashMap<>();
        private final Set<String> moduleReferences = new HashSet<>();
        private final SignatureIndexer signatureIndexer = new SignatureIndexer();
        private final ClassVisitor visitor = new LightweightClassVisitor();

        private String className;
        private IType type;
        private TypeCategory typeCategory = TypeCategory.UNKNOWN;
        private IJavaElement moduleElement;
        private String enclosingClassName;

        private ClassIndex(IPackageFragmentRoot root, EntryWriter writer, Map<String, String> strings,
                Map<String, TypeCategory> typeCategoryCache) {
            this.root = root;
            this.writer = writer;
            this.strings = strings;
            this.typeCategoryCache = typeCategoryCache;
        }

        private void indexDescriptors() {
            for (String descriptor : descriptorSet) {
                addDescriptorReferences(descriptor);
            }
        }

        private void flush() {
            if (moduleElement != null) {
                for (String module : moduleReferences) {
                    add(Kind.MODULE, false, moduleElement, pool(module), pool(module), null, null);
                }
                for (String internalName : typeReferences) {
                    addTypeReferenceEntry(internalName, moduleElement);
                    addPackageReference(packageName(internalName), moduleElement);
                }
                return;
            }

            if (type == null) {
                return;
            }

            add(new EntrySpec(Kind.TYPE, true, pool(simpleTypeName(className)), pool(qualifiedTypeName(className)),
                    null, null, Access.NONE, typeCategory), type, false);
            addPackageDeclaration(packageName(className));
            for (String internalName : typeReferences) {
                addTypeReferenceEntry(internalName, type);
                addPackageReference(packageName(internalName), type);
            }
            flushTypeReferencesByElement();
            flushMemberReferences(fieldReferencesByElement, Kind.FIELD);
            flushMemberReferences(methodReferencesByElement, Kind.METHOD);
            flushMemberReferences(constructorReferencesByElement, Kind.CONSTRUCTOR);
        }

        private static String jdtTypeSignature(org.objectweb.asm.Type type) {
            if (type.getSort() == org.objectweb.asm.Type.ARRAY) {
                return type.getDescriptor().replace('/', '.');
            }
            if (type.getSort() == org.objectweb.asm.Type.OBJECT) {
                return Signature.createTypeSignature(type.getClassName(), true);
            }
            return type.getDescriptor();
        }

        private static String packageName(String internalName) {
            int separator = internalName.lastIndexOf('/');
            return separator < 0 ? "" : internalName.substring(0, separator).replace('/', '.'); //$NON-NLS-1$
        }

        private static String classFileName(String internalName) {
            int separator = internalName.lastIndexOf('/');
            String className = separator < 0 ? internalName : internalName.substring(separator + 1);
            return className + CLASS_FILE_EXTENSION;
        }

        private String simpleTypeName(String internalName) {
            NestedTypeName nestedType = nestedTypeNames.get(internalName);
            if (nestedType != null) {
                return nestedType.innerName();
            }
            int separator = internalName.lastIndexOf('/');
            return separator < 0 ? internalName : internalName.substring(separator + 1);
        }

        private String qualifiedTypeName(String internalName) {
            if (internalName == null) {
                return null;
            }
            NestedTypeName nestedType = nestedTypeNames.get(internalName);
            return nestedType == null ? internalName.replace('/', '.')
                    : qualifiedTypeName(nestedType.outerName()) + "." + nestedType.innerName(); //$NON-NLS-1$
        }

        private void addTypeReference(String internalName) {
            if (StringUtils.isBlank(internalName) || MODULE_INFO.equals(internalName)) {
                return;
            }
            if (internalName.charAt(0) == '[') {
                addDescriptorReferences(internalName);
            } else {
                typeReferences.add(internalName);
                addEnclosingTypeReferences(internalName, null);
            }
        }

        private void addTypeReference(String internalName, IJavaElement element) {
            if (element == null) {
                addTypeReference(internalName);
                return;
            }
            if (StringUtils.isBlank(internalName) || MODULE_INFO.equals(internalName)) {
                return;
            }
            if (internalName.charAt(0) == '[') {
                addDescriptorReferences(internalName, element);
            } else {
                typeReferencesByElement.computeIfAbsent(element, key -> new ArrayList<>()).add(internalName);
                addEnclosingTypeReferences(internalName, element);
            }
        }

        private void addEnclosingTypeReferences(String internalName, IJavaElement element) {
            NestedTypeName nestedType = nestedTypeNames.get(internalName);
            if (nestedType == null) {
                return;
            }
            if (element == null) {
                typeReferences.add(nestedType.outerName());
            } else {
                typeReferencesByElement.computeIfAbsent(element, key -> new ArrayList<>()).add(nestedType.outerName());
            }
            addEnclosingTypeReferences(nestedType.outerName(), element);
        }

        private void addTypeReference(Type asmType, IJavaElement element) {
            if (asmType == null) {
                return;
            }
            if (asmType.getSort() == Type.ARRAY) {
                addTypeReference(asmType.getElementType(), element);
            } else if (asmType.getSort() == Type.OBJECT) {
                addTypeReference(asmType.getInternalName(), element);
            } else if (asmType.getSort() == Type.METHOD) {
                addDescriptorReferences(asmType.getDescriptor(), element);
            }
        }

        private void addDescriptorReferences(String descriptor) {
            addDescriptorReferences(descriptor, null);
        }

        private void addDescriptorReferences(String descriptor, IJavaElement element) {
            if (StringUtils.isBlank(descriptor)) {
                return;
            }
            try {
                addJvmDescriptorReferences(descriptor, element);
            } catch (IllegalArgumentException e) {
                try {
                    addGenericSignatureReferences(descriptor, element);
                } catch (IllegalArgumentException signatureFailure) {
                    JavaDecompilerPlugin.logError(signatureFailure, "Failed to parse bytecode signature"); //$NON-NLS-1$
                }
            }
        }

        private void addJvmDescriptorReferences(String descriptor, IJavaElement element) {
            if (descriptor.indexOf('<') >= 0 || descriptor.charAt(0) == 'T' || descriptor.charAt(0) == '+'
                    || descriptor.charAt(0) == '-' || descriptor.charAt(0) == '*') {
                throw new IllegalArgumentException(descriptor);
            }
            if (descriptor.charAt(0) == '(') {
                for (Type argumentType : Type.getArgumentTypes(descriptor)) {
                    addTypeReference(argumentType, element);
                }
                addTypeReference(Type.getReturnType(descriptor), element);
            } else {
                addTypeReference(Type.getType(descriptor), element);
            }
        }

        private void addGenericSignatureReferences(String signature, IJavaElement element) {
            SignatureIndexer indexer = element == null ? signatureIndexer : new SignatureIndexer(element);
            SignatureReader reader = new SignatureReader(signature);
            if (element == null && isClassSignature(signature)) {
                reader.accept(indexer);
            } else if (isTypeSignature(signature)) {
                reader.acceptType(indexer);
            } else {
                reader.accept(indexer);
            }
        }

        private static boolean isTypeSignature(String signature) {
            char first = signature.charAt(0);
            return first == 'L' || first == '[' || first == 'T' || first == '+' || first == '-' || first == '*';
        }

        private static boolean isClassSignature(String signature) {
            char first = signature.charAt(0);
            return first == '<' || first == 'L';
        }

        private AnnotationVisitor annotationVisitor(IJavaElement element) {
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override
                public void visit(String name, Object value) {
                    addBootstrapArgumentReference(value, element);
                }

                @Override
                public void visitEnum(String name, String descriptor, String value) {
                    addDescriptorReferences(descriptor, element);
                    if (descriptor.startsWith("L") && descriptor.endsWith(";")) { //$NON-NLS-1$ //$NON-NLS-2$
                        String owner = descriptor.substring(1, descriptor.length() - 1);
                        addReference(Kind.FIELD, value, value, qualifiedTypeName(owner), descriptor, element,
                                Access.READ);
                    }
                }

                @Override
                public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                    addDescriptorReferences(descriptor, element);
                    return annotationVisitor(element);
                }

                @Override
                public AnnotationVisitor visitArray(String name) {
                    return annotationVisitor(element);
                }
            };
        }

        private AnnotationVisitor indexedAnnotation(String descriptor, IJavaElement element) {
            addDescriptorReferences(descriptor, element);
            return annotationVisitor(element);
        }

        private void addBootstrapArgumentReference(Object argument, IJavaElement element) {
            if (argument instanceof Type asmType) {
                addTypeReference(asmType, element);
            } else if (argument instanceof Handle handle) {
                addHandleReference(handle, element);
            } else if (argument instanceof ConstantDynamic constant) {
                addDescriptorReferences(constant.getDescriptor(), element);
                addHandleReference(constant.getBootstrapMethod(), element);
                for (int i = 0; i < constant.getBootstrapMethodArgumentCount(); i++) {
                    addBootstrapArgumentReference(constant.getBootstrapMethodArgument(i), element);
                }
            }
        }

        private void addHandleReference(Handle handle, IJavaElement element) {
            if (handle == null) {
                return;
            }
            addTypeReference(handle.getOwner(), element);
            addDescriptorReferences(handle.getDesc(), element);
            if (handle.getTag() == Opcodes.H_GETFIELD || handle.getTag() == Opcodes.H_GETSTATIC
                    || handle.getTag() == Opcodes.H_PUTFIELD || handle.getTag() == Opcodes.H_PUTSTATIC) {
                addReference(Kind.FIELD, handle.getName(), handle.getName(), qualifiedTypeName(handle.getOwner()),
                        handle.getDesc(), element, fieldAccess(handle.getTag()));
            } else if (CONSTRUCTOR.equals(handle.getName())) {
                addReference(Kind.CONSTRUCTOR, simpleTypeName(handle.getOwner()), qualifiedTypeName(handle.getOwner()),
                        qualifiedTypeName(handle.getOwner()), handle.getDesc(), element);
            } else {
                addReference(Kind.METHOD, handle.getName(), handle.getName(), qualifiedTypeName(handle.getOwner()),
                        handle.getDesc(), element);
            }
        }

        private MemberReference addReference(Kind kind, String name, String qualifiedName, String owner, String descriptor,
                IJavaElement element) {
            return addReference(kind, name, qualifiedName, owner, descriptor, element, true);
        }

        private MemberReference addReference(Kind kind, String name, String qualifiedName, String owner, String descriptor,
                IJavaElement element, boolean countable) {
            return addReference(new ReferenceSpec(kind, name, qualifiedName, owner, descriptor, Access.NONE, false),
                    element, countable);
        }

        private MemberReference addReference(Kind kind, String name, String qualifiedName, String owner, String descriptor,
                IJavaElement element, Access access) {
            return addReference(new ReferenceSpec(kind, name, qualifiedName, owner, descriptor, access, false), element);
        }

        private MemberReference addReference(ReferenceSpec reference, IJavaElement element) {
            return addReference(reference, element, true);
        }

        private MemberReference addReference(ReferenceSpec reference, IJavaElement element, boolean countable) {
            IJavaElement enclosingElement = element == null ? type : element;
            MemberReference member = new MemberReference(reference.name(), reference.owner(), reference.descriptor(),
                    reference.access(), reference.compoundCandidate(), countable);
            switch (reference.kind()) {
                case FIELD -> fieldReferencesByElement.computeIfAbsent(enclosingElement, key -> new ArrayList<>()).add(member);
                case METHOD -> methodReferencesByElement.computeIfAbsent(enclosingElement, key -> new ArrayList<>()).add(member);
                case CONSTRUCTOR -> constructorReferencesByElement.computeIfAbsent(enclosingElement, key -> new ArrayList<>()).add(member);
                default -> add(reference.kind(), false, enclosingElement, pool(reference.name()),
                        pool(reference.qualifiedName()), pool(reference.owner()), null);
            }
            return member;
        }

        private void addReferenceEntry(EntrySpec spec, IJavaElement element) {
            addReferenceEntry(spec, element, true);
        }

        private void addReferenceEntry(EntrySpec spec, IJavaElement element, boolean countable) {
            add(newEntry(element, spec), countable);
        }

        private void addTypeReferenceEntry(String internalName, IJavaElement element) {
            addReferenceEntry(new EntrySpec(Kind.TYPE, false, pool(simpleTypeName(internalName)),
                    pool(qualifiedTypeName(internalName)), null, null, Access.NONE, typeCategory(internalName)),
                    element);
        }

        private void addPackageDeclaration(String packageName) {
            if (StringUtils.isBlank(packageName)) {
                return;
            }
            IPackageFragment pkg = root.getPackageFragment(packageName);
            add(Kind.PACKAGE, true, pkg, pool(packageName), pool(packageName), null, null);
        }

        private void addPackageReference(String packageName, IJavaElement element) {
            if (StringUtils.isBlank(packageName)) {
                return;
            }
            addReferenceEntry(new EntrySpec(Kind.PACKAGE, false, pool(packageName), pool(packageName), null, null,
                    Access.NONE, TypeCategory.UNKNOWN), element);
        }

        private void flushTypeReferencesByElement() {
            for (Map.Entry<IJavaElement, List<String>> entry : typeReferencesByElement.entrySet()) {
                for (String internalName : entry.getValue()) {
                    addTypeReferenceEntry(internalName, entry.getKey());
                    addPackageReference(packageName(internalName), entry.getKey());
                }
            }
        }

        private void flushMemberReferences(Map<IJavaElement, List<MemberReference>> references, Kind kind) {
            for (Map.Entry<IJavaElement, List<MemberReference>> entry : references.entrySet()) {
                List<MemberReference> members = kind == Kind.FIELD
                        ? collapseCompoundFieldAccesses(entry.getValue()) : entry.getValue();
                for (MemberReference member : members) {
                    String qualifiedName = member.name();
                    if (kind == Kind.CONSTRUCTOR || kind == Kind.METHOD && CONSTRUCTOR.equals(member.name())) {
                        qualifiedName = member.owner();
                    }
                    addReferenceEntry(new EntrySpec(kind, false, pool(member.name()), pool(qualifiedName),
                            pool(member.owner()), pool(member.descriptor()), member.access(), TypeCategory.UNKNOWN),
                            entry.getKey(), member.countable());
                }
            }
        }

        /**
         * Collapses consecutive read/write pairs on the same field into one READ_WRITE entry.
         * Such pairs are emitted by javac for compound assignments ({@code holder.count++},
         * {@code holder.count += 1}) and produce two bytecode instructions that map to a single
         * source-level field occurrence.  Keeping both would cause REFERENCES and ALL_OCCURRENCES
         * searches to report the same occurrence twice.  The combined access keeps the occurrence
         * visible to both READ_ACCESSES and WRITE_ACCESSES searches.  A read is eligible only when
         * bytecode duplicated the receiver immediately before GETFIELD, or when a GETSTATIC read
         * remains on the operand stack instead of being stored to a local.
         */
        private static List<MemberReference> collapseCompoundFieldAccesses(List<MemberReference> members) {
            List<MemberReference> result = new ArrayList<>(members.size());
            ListIterator<MemberReference> iterator = members.listIterator();
            while (iterator.hasNext()) {
                MemberReference current = iterator.next();
                if (current.compoundCandidate() && current.access() == Access.READ) {
                    List<MemberReference> skipped = new ArrayList<>();
                    if (!tryCollapseIntoReadWrite(current, iterator, skipped, result)) {
                        result.add(current);
                        result.addAll(skipped);
                    }
                } else {
                    result.add(current);
                }
            }
            return result;
        }

        /**
         * Scans ahead for the WRITE that pairs with a compound-candidate READ, skipping
         * intervening reads from the RHS expression (e.g. {@code holder.count += other.value}
         * emits {@code GETFIELD other.value} between the compound GETFIELD/PUTFIELD pair).
         * Returns {@code true} and appends the collapsed READ_WRITE + skipped refs to
         * {@code result} when a matching WRITE is found; returns {@code false} otherwise
         * (leaving {@code skipped} populated for the caller to handle).
         */
        private static boolean tryCollapseIntoReadWrite(MemberReference candidate,
                ListIterator<MemberReference> iterator, List<MemberReference> skipped,
                List<MemberReference> result) {
            while (iterator.hasNext()) {
                MemberReference lookahead = iterator.next();
                if (lookahead.access() == Access.WRITE) {
                    if (lookahead.name().equals(candidate.name())
                            && lookahead.owner().equals(candidate.owner())
                            && lookahead.descriptor().equals(candidate.descriptor())) {
                        result.add(new MemberReference(candidate.name(), candidate.owner(), candidate.descriptor(),
                                Access.READ_WRITE, false, candidate.countable() && lookahead.countable()));
                        result.addAll(skipped);
                        return true;
                    }
                    iterator.previous();
                    return false;
                }
                skipped.add(lookahead);
            }
            return false;
        }

        private void add(Kind kind, boolean declaration, IJavaElement element, String name, String qualifiedName,
                String declaringTypeName, String descriptor) {
            add(new EntrySpec(kind, declaration, name, qualifiedName, declaringTypeName, descriptor, Access.NONE,
                    TypeCategory.UNKNOWN), element, false);
        }

        private void add(EntrySpec spec, IJavaElement element, boolean preserveDuplicate) {
            add(newEntry(element, spec), preserveDuplicate);
        }

        private BytecodeSearchEntry newEntry(IJavaElement element, EntrySpec spec) {
            String elementHandle = elementHandle(element);
            IJavaElement fallback = anonymousElementFallback(elementHandle, element);
            return new BytecodeSearchEntry(spec.kind(), spec.declaration(),
                    BytecodeSearchEntry.elementReference(elementHandle, fallback),
                    BytecodeSearchEntry.symbolReference(spec.name(), spec.qualifiedName(), spec.declaringTypeName(),
                            spec.descriptor()), spec.access(), spec.typeCategory());
        }

        private void add(BytecodeSearchEntry entry, boolean countOccurrences) {
            if (entry.getElementHandle() == null) {
                return;
            }
            EntryKey key = new EntryKey(entry.getKind(), entry.isDeclaration(), entry.getElementHandle(),
                    entry.getName(), entry.getQualifiedName(), entry.getDeclaringTypeName(), entry.getDescriptor(),
                    entry.getAccess(), entry.getTypeCategory());
            Long existingRowId = writer.seen.get(key);
            if (existingRowId != null) {
                if (countOccurrences) {
                    try {
                        writer.incrementCount(existingRowId);
                    } catch (SQLException e) {
                        throw new DbWriteException(e);
                    }
                }
                return;
            }
            try {
                long rowId = writer.insert(entry, countOccurrences ? 1 : 0);
                if (rowId >= 0) {
                    writer.seen.put(key, rowId);
                }
            } catch (SQLException e) {
                throw new DbWriteException(e);
            }
        }

        private static Access fieldAccess(int opcode) {
            return switch (opcode) {
                case Opcodes.GETFIELD, Opcodes.GETSTATIC, Opcodes.H_GETFIELD, Opcodes.H_GETSTATIC -> Access.READ;
                case Opcodes.PUTFIELD, Opcodes.PUTSTATIC, Opcodes.H_PUTFIELD, Opcodes.H_PUTSTATIC -> Access.WRITE;
                default -> Access.NONE;
            };
        }

        private String elementHandle(IJavaElement element) {
            if (element == null) {
                return null;
            }
            String handle = element.getHandleIdentifier();
            return handle == null ? null : elementHandles.computeIfAbsent(handle, key -> key);
        }

        private IJavaElement anonymousElementFallback(String elementHandle, IJavaElement element) {
            if (!Strings.CS.contains(elementHandle, "[~")) { //$NON-NLS-1$
                return null;
            }
            return anonymousElementFallbacks.computeIfAbsent(elementHandle, ignored -> element);
        }

        private String pool(String value) {
            if (value == null) {
                return null;
            }
            return strings.computeIfAbsent(value, key -> key);
        }

        private final class LightweightClassVisitor extends ClassVisitor {

            private final ModuleIndexer moduleIndexer = new ModuleIndexer();

            private LightweightClassVisitor() {
                super(Opcodes.ASM9);
            }

            private void addDescriptor(String descriptor) {
                if (StringUtils.isNotBlank(descriptor)) {
                    descriptorSet.add(descriptor);
                }
            }

            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                    String[] interfaces) {
                className = name;
                typeCategory = typeCategory(access);
                // Seed the cache with what we already know from ASM — free, no JDT call needed.
                typeCategoryCache.put(name, typeCategory);
                if (!MODULE_INFO.equals(name)) {
                    type = typeForInternalName(root, name);
                }
                addDescriptor(signature);
                if (StringUtils.isBlank(signature)) {
                    addRawDeclarationSupertypes(superName, interfaces);
                }
            }

            private void addRawDeclarationSupertypes(String superName, String[] interfaces) {
                if (isSourceVisibleSuperclass(superName)) {
                    addTypeReference(superName);
                }
                if (interfaces != null) {
                    for (String iface : interfaces) {
                        if (isSourceVisibleInterface(iface)) {
                            addTypeReference(iface);
                        }
                    }
                }
            }

            private boolean isSourceVisibleSuperclass(String superName) {
                // Object and Enum are compiler-mandated supertypes in these shapes.
                return !OBJECT_INTERNAL_NAME.equals(superName)
                        && !(typeCategory == TypeCategory.ENUM && ENUM_INTERNAL_NAME.equals(superName));
            }

            private boolean isSourceVisibleInterface(String iface) {
                // @interface types have this compiler-mandated interface even when it is not written in source.
                return !(typeCategory == TypeCategory.ANNOTATION && ANNOTATION_INTERNAL_NAME.equals(iface));
            }

            @Override
            public void visitOuterClass(String owner, String name, String descriptor) {
                enclosingClassName = owner;
            }

            @Override
            public ModuleVisitor visitModule(String name, int access, String version) {
                IModuleDescription module = root.getModuleDescription();
                moduleElement = module == null ? root : module;
                add(Kind.MODULE, true, moduleElement, pool(name), pool(name), null, null);
                return moduleIndexer;
            }

            @Override
            public void visitPermittedSubclass(String permittedSubclass) {
                addTypeReference(permittedSubclass);
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                if (outerName != null && innerName != null) {
                    nestedTypeNames.put(name, new NestedTypeName(outerName, innerName, access));
                }
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                addDescriptor(descriptor);
                return annotationVisitor(type);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath typePath,
                    String descriptor, boolean visible) {
                addDescriptor(descriptor);
                return annotationVisitor(type);
            }

            @Override
            public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
                addDescriptorReferences(signature == null ? descriptor : signature, type);
                return new RecordComponentIndexer(type);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
                    return null;
                }
                IField field = type == null ? null : type.getField(name);
                add(Kind.FIELD, true, field, pool(name), pool(name), pool(qualifiedTypeName(className)),
                        pool(descriptor));
                if ((access & Opcodes.ACC_ENUM) == 0) {
                    addDescriptorReferences(signature == null ? descriptor : signature, field);
                }
                return new FieldIndexer(field);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                if (!isIndexableMethod(access, name)) {
                    return null;
                }
                boolean syntheticLambda = isSyntheticLambdaMethod(access, name);
                IJavaElement methodOrType = syntheticLambda
                        ? lambdaBodyElement(name, descriptor)
                                : indexMethodDeclaration(name, descriptor);
                if (!syntheticLambda) {
                    indexMethodDeclarationReferences(name, descriptor, signature, exceptions, methodOrType);
                }
                return new MethodIndexer(methodOrType, firstLocalSlot(access, descriptor));
            }

            private static boolean isIndexableMethod(int access, String name) {
                if ((access & Opcodes.ACC_BRIDGE) != 0) {
                    return false;
                }
                return (access & Opcodes.ACC_SYNTHETIC) == 0 || name.startsWith(LAMBDA_METHOD_PREFIX);
            }

            private static boolean isSyntheticLambdaMethod(int access, String name) {
                return (access & Opcodes.ACC_SYNTHETIC) != 0 && name.startsWith(LAMBDA_METHOD_PREFIX);
            }

            private IJavaElement indexMethodDeclaration(String name, String descriptor) {
                if (CONSTRUCTOR.equals(name)) {
                    return indexConstructorDeclaration(descriptor);
                }
                if (CLASS_INITIALIZER.equals(name)) {
                    return type;
                }
                IMethod method = type == null ? null : type.getMethod(name, jdtParameterTypes(descriptor));
                add(Kind.METHOD, true, method, pool(name), pool(name), pool(qualifiedTypeName(className)),
                        pool(descriptor));
                return method != null ? method : type;
            }

            private IJavaElement indexConstructorDeclaration(String descriptor) {
                IMethod method = type == null ? null : type.getMethod(type.getElementName(), jdtParameterTypes(descriptor));
                add(Kind.CONSTRUCTOR, true, method, pool(simpleTypeName(className)), pool(simpleTypeName(className)),
                        pool(qualifiedTypeName(className)), pool(descriptor));
                return method != null ? method : type;
            }

            private void indexMethodDeclarationReferences(String name, String descriptor, String signature,
                    String[] exceptions, IJavaElement methodOrType) {
                addDescriptorReferences(declarationDescriptor(name, descriptor, signature), methodOrType);
                if (exceptions == null) {
                    return;
                }
                for (String exception : exceptions) {
                    addTypeReference(exception, methodOrType);
                }
            }

            private static int firstLocalSlot(int access, String descriptor) {
                int paramSlots = 0;
                for (Type argType : Type.getArgumentTypes(descriptor)) {
                    paramSlots += argType.getSize();
                }
                return paramSlots + ((access & Opcodes.ACC_STATIC) == 0 ? 1 : 0);
            }

            private IJavaElement lambdaBodyElement(String lambdaName, String descriptor) {
                IJavaElement owner = lambdaBodyOwners.get(new LambdaMethodKey(lambdaName, descriptor));
                if (owner != null) {
                    return owner;
                }
                String sourceMethodName = lambdaSourceMethodName(lambdaName);
                if (type == null || sourceMethodName == null) {
                    return type;
                }
                try {
                    IMethod matched = null;
                    for (IMethod candidate : type.getMethods()) {
                        if (!matchesLambdaSourceMethod(sourceMethodName, candidate)) {
                            continue;
                        }
                        if (matched != null) {
                            return type;
                        }
                        matched = candidate;
                    }
                    return matched == null ? type : matched;
                } catch (org.eclipse.jdt.core.JavaModelException e) {
                    JavaDecompilerPlugin.logError(e, "Failed to resolve lambda body owner"); //$NON-NLS-1$
                    return type;
                }
            }

            private static String lambdaSourceMethodName(String lambdaName) {
                int start = LAMBDA_METHOD_PREFIX.length();
                int end = lambdaName.lastIndexOf('$');
                return end <= start ? null : lambdaName.substring(start, end);
            }

            private static boolean matchesLambdaSourceMethod(String sourceMethodName, IMethod candidate)
                    throws org.eclipse.jdt.core.JavaModelException {
                return "new".equals(sourceMethodName) //$NON-NLS-1$
                        ? candidate.isConstructor()
                                : sourceMethodName.equals(candidate.getElementName());
            }

            private String declarationDescriptor(String name, String descriptor, String signature) {
                if (!CONSTRUCTOR.equals(name) || signature != null) {
                    return signature == null ? descriptor : signature;
                }
                String syntheticOwner = syntheticOuterConstructorOwner();
                if (syntheticOwner == null) {
                    return descriptor;
                }
                try {
                    Type[] arguments = Type.getArgumentTypes(descriptor);
                    if (arguments.length == 0 || arguments[0].getSort() != Type.OBJECT
                            || !syntheticOwner.equals(arguments[0].getInternalName())) {
                        return descriptor;
                    }
                    StringBuilder sourceDescriptor = new StringBuilder("("); //$NON-NLS-1$
                    for (int i = 1; i < arguments.length; i++) {
                        sourceDescriptor.append(arguments[i].getDescriptor());
                    }
                    return sourceDescriptor.append(')').append(Type.getReturnType(descriptor).getDescriptor()).toString();
                } catch (IllegalArgumentException e) {
                    return descriptor;
                }
            }

            private String syntheticOuterConstructorOwner() {
                NestedTypeName nestedType = nestedTypeNames.get(className);
                if (nestedType != null && (nestedType.access() & Opcodes.ACC_STATIC) == 0) {
                    return nestedType.outerName();
                }
                return enclosingClassName;
            }
        }

        private static IType typeForInternalName(IPackageFragmentRoot root, String internalName) {
            IPackageFragment pkg = root.getPackageFragment(packageName(internalName));
            IClassFile classFile = pkg.getClassFile(classFileName(internalName));
            return classFile instanceof IOrdinaryClassFile ordinaryClassFile ? ordinaryClassFile.getType() : null;
        }

        private TypeCategory typeCategory(String internalName) {
            TypeCategory cached = typeCategoryCache.get(internalName);
            if (cached != null) {
                return cached;
            }
            TypeCategory result = typeCategory(typeForInternalName(root, internalName));
            typeCategoryCache.put(internalName, result);
            return result;
        }

        private static TypeCategory typeCategory(IType type) {
            if (type == null || !type.exists()) {
                return TypeCategory.UNKNOWN;
            }
            try {
                if (type.isAnnotation()) {
                    return TypeCategory.ANNOTATION;
                }
                if (type.isEnum()) {
                    return TypeCategory.ENUM;
                }
                if (type.isInterface()) {
                    return TypeCategory.INTERFACE;
                }
                if (type.isClass()) {
                    return TypeCategory.CLASS;
                }
            } catch (org.eclipse.jdt.core.JavaModelException e) {
                JavaDecompilerPlugin.logError(e, "Failed to inspect indexed type category"); //$NON-NLS-1$
            }
            return TypeCategory.UNKNOWN;
        }

        private static TypeCategory typeCategory(int access) {
            if ((access & Opcodes.ACC_ANNOTATION) != 0) {
                return TypeCategory.ANNOTATION;
            }
            if ((access & Opcodes.ACC_ENUM) != 0) {
                return TypeCategory.ENUM;
            }
            if ((access & Opcodes.ACC_INTERFACE) != 0) {
                return TypeCategory.INTERFACE;
            }
            return TypeCategory.CLASS;
        }

        private static String[] jdtParameterTypes(String descriptor) {
            org.objectweb.asm.Type[] argumentTypes = org.objectweb.asm.Type.getArgumentTypes(descriptor);
            String[] result = new String[argumentTypes.length];
            for (int i = 0; i < argumentTypes.length; i++) {
                result[i] = jdtTypeSignature(argumentTypes[i]);
            }
            return result;
        }

        private final class SignatureIndexer extends SignatureVisitor {

            private final IJavaElement element;
            private String className;

            private SignatureIndexer() {
                super(Opcodes.ASM9);
                this.element = null;
            }

            private SignatureIndexer(IJavaElement element) {
                super(Opcodes.ASM9);
                this.element = element;
            }

            @Override
            public void visitClassType(String name) {
                className = name;
                addTypeReference(name, element);
            }

            @Override
            public void visitInnerClassType(String name) {
                className = className == null ? name : className + "$" + name; //$NON-NLS-1$
                addDirectTypeReference(className, element);
            }

            private void addDirectTypeReference(String internalName, IJavaElement targetElement) {
                if (targetElement == null) {
                    typeReferences.add(internalName);
                } else {
                    typeReferencesByElement.computeIfAbsent(targetElement, key -> new ArrayList<>()).add(internalName);
                }
            }

            @Override
            public SignatureVisitor visitTypeArgument(char wildcard) {
                return new SignatureIndexer(element);
            }

            @Override
            public void visitEnd() {
                className = null;
            }
        }

        private final class FieldIndexer extends FieldVisitor {

            private final IJavaElement field;

            private FieldIndexer(IJavaElement field) {
                super(Opcodes.ASM9);
                this.field = field;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                return indexedAnnotation(descriptor, field);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath typePath,
                    String descriptor, boolean visible) {
                return visitAnnotation(descriptor, visible);
            }
        }

        private final class RecordComponentIndexer extends RecordComponentVisitor {

            private final IJavaElement element;

            private RecordComponentIndexer(IJavaElement element) {
                super(Opcodes.ASM9);
                this.element = element;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                return indexedAnnotation(descriptor, element);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath typePath,
                    String descriptor, boolean visible) {
                return visitAnnotation(descriptor, visible);
            }
        }

        private final class MethodIndexer extends MethodVisitor {

            private final IJavaElement method;
            private final Map<String, Integer> pendingNewTypes = new HashMap<>();
            private final Map<Label, Set<String>> catchHandlerTypes = new HashMap<>();
            private final Set<Label> finallyHandlerLabels = new HashSet<>();
            private final Map<Label, Integer> finallyTryStartCounts = new HashMap<>();
            private final Map<Label, Integer> finallyTryEndCounts = new HashMap<>();
            private final Map<Label, Label[]> pendingFinallyStarts = new HashMap<>();
            private final Map<String, List<Runnable>> inlineFinallySuppressions = new HashMap<>();
            private final Set<String> handlerCallKeys = new HashSet<>();
            private final Map<Label, Set<Label>> endToHandlers = new HashMap<>();
            private final Set<Label> inlineFinallyActiveHandlers = new HashSet<>();
            private final int firstLocalSlot;
            private Label prevHandlerLabel = null;
            private MemberReference pendingStaticCompoundRead;
            private IJavaElement pendingStaticCompoundElement;
            private int pendingStaticCompoundStackDepth = -1;
            private int stackDepth = 0;
            private int previousOpcode = -1;
            private int currentLine = -1;
            private int finallyTryDepth = 0;
            private int pendingMonitorEnterCount = 0;
            private boolean inFinallyHandler = false;

            private MethodIndexer(IJavaElement method, int firstLocalSlot) {
                super(Opcodes.ASM9);
                this.method = method;
                this.firstLocalSlot = firstLocalSlot;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                addDescriptorReferences(descriptor, method);
                return annotationVisitor(method);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath typePath,
                    String descriptor, boolean visible) {
                return visitAnnotation(descriptor, visible);
            }

            @Override
            public AnnotationVisitor visitInsnAnnotation(int typeRef, org.objectweb.asm.TypePath typePath,
                    String descriptor, boolean visible) {
                return visitTypeAnnotation(typeRef, typePath, descriptor, visible);
            }

            @Override
            public AnnotationVisitor visitTryCatchAnnotation(int typeRef, org.objectweb.asm.TypePath typePath,
                    String descriptor, boolean visible) {
                return visitInsnAnnotation(typeRef, typePath, descriptor, visible);
            }

            @Override
            public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, org.objectweb.asm.TypePath typePath,
                    Label[] start, Label[] end, int[] index, String descriptor,
                    boolean visible) {
                return visitTryCatchAnnotation(typeRef, typePath, descriptor, visible);
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                return indexedAnnotation(descriptor, method);
            }

            @Override
            public AnnotationVisitor visitAnnotationDefault() {
                return annotationVisitor(method);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                if (opcode == Opcodes.NEW) {
                    pendingNewTypes.merge(type, 1, Integer::sum);
                    stackDepth++;
                }
                addTypeReference(type, method);
                previousOpcode = opcode;
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (opcode == Opcodes.GETSTATIC) {
                    clearPendingStaticCompoundRead();
                }
                if ((opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) && !owner.equals(className)) {
                    // Owner is source-visible only for qualified static accesses to a different class
                    // (e.g. MyClass.FIELD).  Same-class accesses (count++, CLASS_INITIALIZER, etc.)
                    // and static imports emit no owner token in source, so skip them.
                    // Instance field owners (GETFIELD/PUTFIELD) are never type tokens in source.
                    addTypeReference(owner, method);
                }
                if (opcode == Opcodes.GETSTATIC || opcode == Opcodes.GETFIELD) {
                    addDescriptorReferences(descriptor, method);
                }
                boolean compoundCandidate = opcode == Opcodes.GETSTATIC
                        || opcode == Opcodes.GETFIELD && previousOpcode == Opcodes.DUP;
                MemberReference member = addReference(new ReferenceSpec(Kind.FIELD, name, name, qualifiedTypeName(owner),
                        descriptor, fieldAccess(opcode), compoundCandidate), method);
                if (opcode == Opcodes.GETSTATIC) {
                    pendingStaticCompoundRead = member;
                    pendingStaticCompoundElement = method == null ? type : method;
                    pendingStaticCompoundStackDepth = stackDepth;
                } else if (opcode == Opcodes.PUTSTATIC) {
                    pendingStaticCompoundRead = null;
                    pendingStaticCompoundElement = null;
                    pendingStaticCompoundStackDepth = -1;
                }
                updateFieldStackDepth(opcode, descriptor);
                trackFieldFinallySuppression(name, owner, descriptor);
                previousOpcode = opcode;
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                int consumedSlots = methodConsumedSlots(opcode, descriptor);
                clearPendingStaticCompoundReadIfConsumed(consumedSlots);
                if (CONSTRUCTOR.equals(name)) {
                    consumePendingNew(owner);
                } else if (opcode == Opcodes.INVOKESTATIC && !owner.equals(className)) {
                    // Static calls to a different class are qualified with the declaring type in
                    // source (e.g. Math.abs()); same-class helpers and static imports are not.
                    // Instance calls (INVOKEVIRTUAL/INVOKEINTERFACE/INVOKESPECIAL) are never typed.
                    addTypeReference(owner, method);
                }
                // Descriptor holds parameter/return types, which are not source-visible tokens at
                // a call site — skip it here; it is stored in the member reference for matching.
                if (CONSTRUCTOR.equals(name)) {
                    addReference(Kind.CONSTRUCTOR, simpleTypeName(owner), qualifiedTypeName(owner),
                            qualifiedTypeName(owner), descriptor, method, true);
                } else {
                    addReference(Kind.METHOD, name, name, qualifiedTypeName(owner), descriptor, method, true);
                }
                // Only track finally suppression when line numbers are available; without them
                // all calls collapse to the same key and would suppress legitimate call sites.
                if (currentLine >= 0) {
                    String callKey = currentLine + "|" + name + "|" + owner + "|" + descriptor; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    if (inFinallyHandler) {
                        handlerCallKeys.add(callKey);
                    } else if (!inlineFinallyActiveHandlers.isEmpty()) {
                        // Register inline copies only — not try-body calls — as suppression candidates.
                        // A try-body call sharing the same line as the finally body would otherwise
                        // get the same key and be incorrectly marked uncountable.
                        registerInlineFinallyCandidate(callKey, name);
                    }
                }
                updateMethodStackDepth(consumedSlots, descriptor);
                previousOpcode = opcode;
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, org.objectweb.asm.Handle bootstrapMethodHandle,
                    Object... bootstrapMethodArguments) {
                int consumedSlots = argumentStackSize(descriptor);
                clearPendingStaticCompoundReadIfConsumed(consumedSlots);
                addDescriptorReferences(descriptor, method);
                if (bootstrapMethodArguments != null) {
                    for (Object argument : bootstrapMethodArguments) {
                        if (!recordLambdaBodyOwner(argument)) {
                            addBootstrapArgumentReference(argument, method);
                        }
                    }
                }
                updateMethodStackDepth(consumedSlots, descriptor);
                previousOpcode = Opcodes.INVOKEDYNAMIC;
            }

            private boolean recordLambdaBodyOwner(Object argument) {
                if (argument instanceof Handle handle && className.equals(handle.getOwner())
                        && handle.getName().startsWith(LAMBDA_METHOD_PREFIX)) {
                    lambdaBodyOwners.put(new LambdaMethodKey(handle.getName(), handle.getDesc()),
                            method == null ? type : method);
                    return true;
                }
                return false;
            }

            @Override
            public void visitLdcInsn(Object value) {
                addBootstrapArgumentReference(value, method);
                stackDepth += ldcStackSize(value);
                previousOpcode = Opcodes.LDC;
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                addDescriptorReferences(descriptor, method);
                previousOpcode = Opcodes.MULTIANEWARRAY;
            }

            @Override
            public void visitInsn(int opcode) {
                stackDepth = Math.max(0, stackDepth + stackDelta(opcode));
                if (opcode == Opcodes.MONITORENTER) {
                    pendingMonitorEnterCount++;
                }
                if (inFinallyHandler && isFinallyExitInsn(opcode)) {
                    inFinallyHandler = false;
                }
                previousOpcode = opcode;
            }

            private static boolean isFinallyExitInsn(int opcode) {
                return opcode == Opcodes.ATHROW
                        || (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN);
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                    stackDepth++;
                }
                previousOpcode = opcode;
            }

            @Override
            public void visitVarInsn(int opcode, int varIndex) {
                if (isStoreOpcode(opcode)) {
                    clearPendingStaticCompoundRead();
                }
                updateVarStackDepth(opcode);
                previousOpcode = opcode;
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                clearPendingStaticCompoundReadIfConsumed(jumpConsumedSlots(opcode));
                stackDepth = Math.max(0, stackDepth - jumpConsumedSlots(opcode));
                previousOpcode = opcode;
            }

            @Override
            public void visitIincInsn(int varIndex, int increment) {
                previousOpcode = Opcodes.IINC;
            }

            private void clearPendingStaticCompoundRead() {
                if (pendingStaticCompoundRead == null) {
                    return;
                }
                List<MemberReference> references = fieldReferencesByElement.get(pendingStaticCompoundElement);
                if (references != null && !references.isEmpty()
                        && references.get(references.size() - 1) == pendingStaticCompoundRead) {
                    references.set(references.size() - 1, new MemberReference(pendingStaticCompoundRead.name(),
                            pendingStaticCompoundRead.owner(), pendingStaticCompoundRead.descriptor(),
                            pendingStaticCompoundRead.access(), false, pendingStaticCompoundRead.countable()));
                }
                pendingStaticCompoundRead = null;
                pendingStaticCompoundElement = null;
                pendingStaticCompoundStackDepth = -1;
            }

            private static boolean isStoreOpcode(int opcode) {
                return opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE;
            }

            private void clearPendingStaticCompoundReadIfConsumed(int consumedSlots) {
                if (pendingStaticCompoundRead != null
                        && stackDepth - consumedSlots <= pendingStaticCompoundStackDepth) {
                    clearPendingStaticCompoundRead();
                }
            }

            private void updateFieldStackDepth(int opcode, String descriptor) {
                int fieldSize = stackSize(descriptor);
                if (opcode == Opcodes.GETSTATIC) {
                    stackDepth += fieldSize;
                } else if (opcode == Opcodes.PUTSTATIC) {
                    stackDepth = Math.max(0, stackDepth - fieldSize);
                } else if (opcode == Opcodes.GETFIELD) {
                    stackDepth = Math.max(0, stackDepth - 1) + fieldSize;
                } else if (opcode == Opcodes.PUTFIELD) {
                    stackDepth = Math.max(0, stackDepth - fieldSize - 1);
                }
            }

            private void updateMethodStackDepth(int consumedSlots, String descriptor) {
                stackDepth = Math.max(0, stackDepth - consumedSlots) + returnStackSize(descriptor);
            }

            private void updateVarStackDepth(int opcode) {
                if (opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD) {
                    stackDepth += opcode == Opcodes.LLOAD || opcode == Opcodes.DLOAD ? 2 : 1;
                } else if (isStoreOpcode(opcode)) {
                    stackDepth = Math.max(0, stackDepth - (opcode == Opcodes.LSTORE || opcode == Opcodes.DSTORE ? 2 : 1));
                }
            }

            private static int methodConsumedSlots(int opcode, String descriptor) {
                return argumentStackSize(descriptor) + (opcode == Opcodes.INVOKESTATIC ? 0 : 1);
            }

            private static int argumentStackSize(String descriptor) {
                int size = 0;
                for (Type argument : Type.getArgumentTypes(descriptor)) {
                    size += argument.getSize();
                }
                return size;
            }

            private static int returnStackSize(String descriptor) {
                return Type.getReturnType(descriptor).getSize();
            }

            private static int stackSize(String descriptor) {
                return Type.getType(descriptor).getSize();
            }

            private static int jumpConsumedSlots(int opcode) {
                return switch (opcode) {
                    case Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE,
                    Opcodes.IFNULL, Opcodes.IFNONNULL -> 1;
                    case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE,
                    Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE, Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> 2;
                    default -> 0;
                };
            }

            private static int ldcStackSize(Object value) {
                return value instanceof Long || value instanceof Double ? 2 : 1;
            }

            private static int stackDelta(int opcode) {
                return switch (opcode) {
                    case Opcodes.ACONST_NULL, Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2,
                    Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.FCONST_0, Opcodes.FCONST_1,
                    Opcodes.FCONST_2 -> 1;
                    case Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.DCONST_0, Opcodes.DCONST_1 -> 2;
                    case Opcodes.IADD, Opcodes.FADD, Opcodes.ISUB, Opcodes.FSUB, Opcodes.IMUL, Opcodes.FMUL,
                    Opcodes.IDIV, Opcodes.FDIV, Opcodes.IREM, Opcodes.FREM, Opcodes.IAND, Opcodes.IOR,
                    Opcodes.IXOR, Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR, Opcodes.L2I, Opcodes.L2F,
                    Opcodes.D2I, Opcodes.D2F, Opcodes.POP, Opcodes.IRETURN, Opcodes.FRETURN,
                    Opcodes.ARETURN -> -1;
                    case Opcodes.LADD, Opcodes.DADD, Opcodes.LSUB, Opcodes.DSUB, Opcodes.LMUL, Opcodes.DMUL,
                    Opcodes.LDIV, Opcodes.DDIV, Opcodes.LREM, Opcodes.DREM, Opcodes.LAND, Opcodes.LOR,
                    Opcodes.LXOR, Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR, Opcodes.POP2, Opcodes.LRETURN,
                    Opcodes.DRETURN -> -2;
                    case Opcodes.I2L, Opcodes.I2D, Opcodes.F2L, Opcodes.F2D, Opcodes.DUP -> 1;
                    case Opcodes.DUP2 -> 2;
                    default -> 0;
                };
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                previousOpcode = Opcodes.TABLESWITCH;
            }

            @Override
            public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                previousOpcode = Opcodes.LOOKUPSWITCH;
            }

            private boolean consumePendingNew(String owner) {
                int count = pendingNewTypes.getOrDefault(owner, 0);
                if (count == 0) {
                    return false;
                }
                if (count == 1) {
                    pendingNewTypes.remove(owner);
                } else {
                    pendingNewTypes.put(owner, count - 1);
                }
                return true;
            }

            @Override
            public void visitLineNumber(int line, Label start) {
                currentLine = line;
            }

            @Override
            public void visitLabel(Label label) {
                // Classify any pending type==null try region whose start label we just reached.
                // A MONITORENTER immediately before this label means it is a synchronized
                // monitor-exit cleanup region, not a source finally block.
                Label[] pending = pendingFinallyStarts.remove(label);
                if (pending != null) {
                    if (pendingMonitorEnterCount > 0) {
                        pendingMonitorEnterCount--;
                        finallyHandlerLabels.remove(pending[1]);
                    } else {
                        finallyTryStartCounts.merge(label, 1, Integer::sum);
                        finallyTryEndCounts.merge(pending[0], 1, Integer::sum);
                        endToHandlers.computeIfAbsent(pending[0], k -> new HashSet<>()).add(pending[1]);
                    }
                }
                // Process ends before starts so a label shared between an ending region and a
                // starting region doesn't transiently drop to zero and miscount.
                Integer endCount = finallyTryEndCounts.get(label);
                if (endCount != null) {
                    finallyTryDepth = Math.max(0, finallyTryDepth - endCount);
                    Set<Label> handlers = endToHandlers.get(label);
                    if (handlers != null) {
                        inlineFinallyActiveHandlers.addAll(handlers);
                    }
                }
                Integer startCount = finallyTryStartCounts.get(label);
                if (startCount != null) {
                    finallyTryDepth += startCount;
                }
                if (prevHandlerLabel != null) {
                    Set<String> types = catchHandlerTypes.get(prevHandlerLabel);
                    if (types != null) {
                        catchHandlerTypes.computeIfAbsent(label, k -> new HashSet<>()).addAll(types);
                    }
                    prevHandlerLabel = null;
                }
                if (catchHandlerTypes.containsKey(label)) {
                    prevHandlerLabel = label;
                }
                if (finallyHandlerLabels.contains(label)) {
                    inlineFinallyActiveHandlers.remove(label);
                    inFinallyHandler = true;
                }
            }

            @Override
            public void visitLocalVariable(String name, String descriptor, String signature,
                    Label start, Label end, int index) {
                if (index >= firstLocalSlot && !isCatchVariable(start, descriptor)) {
                    addDescriptorReferences(signature != null ? signature : descriptor, method);
                }
            }

            private boolean isCatchVariable(Label start, String descriptor) {
                Set<String> handlerTypes = catchHandlerTypes.get(start);
                if (handlerTypes == null || !descriptor.startsWith("L") || !descriptor.endsWith(";")) { //$NON-NLS-1$ //$NON-NLS-2$
                    return false;
                }
                return handlerTypes.contains(descriptor.substring(1, descriptor.length() - 1));
            }

            @Override
            public void visitTryCatchBlock(Label start, Label end,
                    Label handler, String type) {
                if (type != null) {
                    catchHandlerTypes.computeIfAbsent(handler, k -> new HashSet<>()).add(type);
                } else {
                    // type == null can mean either a source finally block OR a javac-generated
                    // synchronized monitor-exit cleanup region. Defer classification until
                    // visitLabel sees the try-start: a MONITORENTER immediately before the start
                    // label identifies the synchronized case; otherwise it is a true finally block.
                    finallyHandlerLabels.add(handler);
                    pendingFinallyStarts.put(start, new Label[]{end, handler});
                }
                addTypeReference(type, method);
            }

            private void registerInlineFinallyCandidate(String callKey, String name) {
                IJavaElement enclosingElement = method == null ? type : method;
                List<MemberReference> refs = CONSTRUCTOR.equals(name)
                        ? constructorReferencesByElement.get(enclosingElement)
                                : methodReferencesByElement.get(enclosingElement);
                if (refs != null && !refs.isEmpty()) {
                    int idx = refs.size() - 1;
                    inlineFinallySuppressions.computeIfAbsent(callKey, k -> new ArrayList<>())
                    .add(() -> {
                        MemberReference old = refs.get(idx);
                        refs.set(idx, new MemberReference(old.name(), old.owner(), old.descriptor(),
                                old.access(), old.compoundCandidate(), false));
                    });
                }
            }

            private void trackFieldFinallySuppression(String name, String owner, String descriptor) {
                if (currentLine < 0) {
                    return;
                }
                String fieldKey = currentLine + "|" + name + "|" + owner + "|" + descriptor; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                if (inFinallyHandler) {
                    handlerCallKeys.add(fieldKey);
                } else if (!inlineFinallyActiveHandlers.isEmpty()) {
                    registerInlineFinallyFieldCandidate(fieldKey);
                }
            }

            private void registerInlineFinallyFieldCandidate(String fieldKey) {
                IJavaElement enclosingElement = method == null ? type : method;
                List<MemberReference> refs = fieldReferencesByElement.get(enclosingElement);
                if (refs != null && !refs.isEmpty()) {
                    int idx = refs.size() - 1;
                    inlineFinallySuppressions.computeIfAbsent(fieldKey, k -> new ArrayList<>())
                    .add(() -> {
                        MemberReference old = refs.get(idx);
                        refs.set(idx, new MemberReference(old.name(), old.owner(), old.descriptor(),
                                old.access(), old.compoundCandidate(), false));
                    });
                }
            }

            @Override
            public void visitEnd() {
                for (String callKey : handlerCallKeys) {
                    List<Runnable> suppressors = inlineFinallySuppressions.remove(callKey);
                    if (suppressors != null) {
                        suppressors.forEach(Runnable::run);
                    }
                }
                super.visitEnd();
            }
        }

        private final class ModuleIndexer extends ModuleVisitor {

            private ModuleIndexer() {
                super(Opcodes.ASM9);
            }

            @Override
            public void visitMainClass(String mainClass) {
                addTypeReference(mainClass);
            }

            @Override
            public void visitRequire(String module, int access, String version) {
                moduleReferences.add(module);
            }

            @Override
            public void visitUse(String service) {
                addTypeReference(service);
            }

            @Override
            public void visitExport(String packaze, int access, String... modules) {
                addPackageReference(packaze.replace('/', '.'), moduleElement);
                addModuleReferences(modules);
            }

            @Override
            public void visitOpen(String packaze, int access, String... modules) {
                visitExport(packaze, access, modules);
            }

            @Override
            public void visitProvide(String service, String... providers) {
                addTypeReference(service);
                if (providers != null) {
                    for (String provider : providers) {
                        addTypeReference(provider);
                    }
                }
            }

            private void addModuleReferences(String[] modules) {
                if (modules != null) {
                    Collections.addAll(moduleReferences, modules);
                }
            }
        }
    }

    private record MemberReference(String name, String owner, String descriptor, Access access,
            boolean compoundCandidate, boolean countable) {
    }

    private record ReferenceSpec(Kind kind, String name, String qualifiedName, String owner, String descriptor,
            Access access, boolean compoundCandidate) {
    }

    private record NestedTypeName(String outerName, String innerName, int access) {
    }

    private record LambdaMethodKey(String name, String descriptor) {
    }

    private record EntryKey(Kind kind, boolean declaration, String elementHandle, String name, String qualifiedName,
            String declaringTypeName, String descriptor, Access access, TypeCategory typeCategory) {
    }

    private record EntrySpec(Kind kind, boolean declaration, String name, String qualifiedName,
            String declaringTypeName, String descriptor, Access access, TypeCategory typeCategory) {
    }

    private record IndexContext(IPackageFragmentRoot root, File jar, ZipFile zip, EntryWriter writer,
            Map<String, String> strings, Map<String, TypeCategory> typeCategoryCache) {
    }

    private static final class EntryWriter {

        private final int jarId;
        private final PreparedStatement insertPs;
        private final PreparedStatement updatePs;
        private final PreparedStatement internInsPs;
        private final PreparedStatement internSelPs;
        private final Map<String, Integer> stringCache;
        private final Object lock;
        final Map<EntryKey, Long> seen = new HashMap<>();
        // heap mode — non-null only when SQLite is unavailable
        private final List<BytecodeSearchEntry> heapEntries;
        private final List<Integer> heapCounts;

        EntryWriter(int jarId, PreparedStatement insertPs, PreparedStatement updatePs,
                PreparedStatement internInsPs, PreparedStatement internSelPs, Object lock) {
            this.jarId = jarId;
            this.insertPs = insertPs;
            this.updatePs = updatePs;
            this.internInsPs = internInsPs;
            this.internSelPs = internSelPs;
            this.lock = lock;
            this.stringCache = new HashMap<>();
            this.heapEntries = null;
            this.heapCounts = null;
        }

        EntryWriter() {
            this.jarId = -1;
            this.insertPs = null;
            this.updatePs = null;
            this.internInsPs = null;
            this.internSelPs = null;
            this.lock = null;
            this.stringCache = null;
            this.heapEntries = new ArrayList<>();
            this.heapCounts = new ArrayList<>();
        }

        HeapEntryStore buildHeapStore() {
            if (heapEntries == null) {
                return null;
            }
            int[] counts = heapCounts.stream().mapToInt(Integer::intValue).toArray();
            return HeapEntryStore.from(heapEntries, counts);
        }

        private int intern(String value) throws SQLException {
            if (value == null || value.isEmpty()) {
                return 0; // sentinel: id=0 means "no string" (SQLite ROWIDs start at 1)
            }
            Integer cached = stringCache.get(value);
            if (cached != null) {
                return cached;
            }
            int id;
            synchronized (lock) {
                internInsPs.setString(1, value);
                internInsPs.executeUpdate();
                internSelPs.setString(1, value);
                try (ResultSet rs = internSelPs.executeQuery()) {
                    id = rs.next() ? rs.getInt(1) : 0;
                }
            }
            stringCache.put(value, id);
            return id;
        }

        long insert(BytecodeSearchEntry entry, int count) throws SQLException {
            if (heapEntries != null) {
                int idx = heapEntries.size();
                heapEntries.add(entry);
                heapCounts.add(count);
                return idx;
            }
            int elementHandleId = intern(nullToEmpty(entry.getElementHandle()));
            int nameId = intern(nullToEmpty(entry.getName()));
            int qualNameId = intern(nullToEmpty(entry.getQualifiedName()));
            int declTypeId = intern(nullToEmpty(entry.getDeclaringTypeName()));
            int descId = intern(nullToEmpty(entry.getDescriptor()));
            int fallbackId = intern(fallbackHandle(entry));
            insertPs.setInt(1, jarId);
            insertPs.setInt(2, entry.getKind().ordinal());
            insertPs.setInt(3, entry.isDeclaration() ? 1 : 0);
            insertPs.setInt(4, entry.getAccess().ordinal());
            insertPs.setInt(5, entry.getTypeCategory().ordinal());
            insertPs.setInt(6, elementHandleId);
            insertPs.setInt(7, nameId);
            insertPs.setInt(8, qualNameId);
            insertPs.setInt(9, declTypeId);
            insertPs.setInt(10, descId);
            insertPs.setInt(11, count);
            insertPs.setInt(12, fallbackId);
            synchronized (lock) {
                insertPs.executeUpdate();
                try (ResultSet keys = insertPs.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            }
        }

        void incrementCount(long rowId) throws SQLException {
            if (heapCounts != null) {
                int idx = (int) rowId;
                heapCounts.set(idx, heapCounts.get(idx) + 1);
                return;
            }
            updatePs.setLong(1, rowId);
            synchronized (lock) {
                updatePs.executeUpdate();
            }
        }

        private static String fallbackHandle(BytecodeSearchEntry entry) {
            IJavaElement fallback = entry.getAnonymousElementFallback();
            if (fallback == null) {
                return ""; //$NON-NLS-1$
            }
            IJavaElement ancestor = fallback.getParent();
            while (ancestor != null) {
                String handle = ancestor.getHandleIdentifier();
                if (handle != null && !handle.contains("[~")) { //$NON-NLS-1$
                    return handle;
                }
                ancestor = ancestor.getParent();
            }
            return ""; //$NON-NLS-1$
        }

        private static String nullToEmpty(String s) {
            return s == null ? "" : s; //$NON-NLS-1$
        }
    }

    private record VersionedClassName(String logicalName, int version) {
    }

    private record EffectiveClassEntry(String logicalName, String entryName, int version, long impactBytes, int ticks,
            long entryCrc) {

        private static EffectiveClassEntry newer(EffectiveClassEntry left, EffectiveClassEntry right) {
            return left.version() >= right.version() ? left : right;
        }
    }

    public record JarWork(List<JarEntryWork> entries, long totalImpact, int totalTicks, long fileCrc,
            String contentHash) {
    }

    public record JarEntryWork(String name, long impactBytes, int ticks) {
    }
}
