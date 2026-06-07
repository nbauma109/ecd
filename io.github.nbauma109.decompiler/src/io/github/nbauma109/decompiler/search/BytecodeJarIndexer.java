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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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

public class BytecodeJarIndexer {

    private static final String CLASS_FILE_EXTENSION = ".class"; //$NON-NLS-1$
    private static final String CLASS_INITIALIZER = "<clinit>"; //$NON-NLS-1$
    private static final String CONSTRUCTOR = "<init>"; //$NON-NLS-1$
    private static final String META_INF_VERSIONS = "META-INF/versions/"; //$NON-NLS-1$
    private static final String MODULE_INFO = "module-info"; //$NON-NLS-1$
    private static final String OBJECT_INTERNAL_NAME = "java/lang/Object"; //$NON-NLS-1$
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
            return jarWork(effectiveEntries.values());
        } catch (IOException e) {
            JavaDecompilerPlugin.logError(e, "Failed to inspect jar " + jar.getAbsolutePath()); //$NON-NLS-1$
            return null;
        }
    }

    private static JarWork jarWork(Iterable<EffectiveClassEntry> effectiveEntries) {
        List<JarEntryWork> entries = new ArrayList<>();
        long totalImpact = 0L;
        long totalTicks = 0L;
        for (EffectiveClassEntry entry : effectiveEntries) {
            totalImpact += entry.impactBytes();
            totalTicks = Math.clamp(totalTicks + entry.ticks(), 0L, Integer.MAX_VALUE);
            entries.add(new JarEntryWork(entry.entryName(), entry.impactBytes(), entry.ticks()));
        }
        return new JarWork(entries, totalImpact, (int) totalTicks);
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
        return new EffectiveClassEntry(logicalName, entryName, version, impact, impactTicks(impact));
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

    public static BytecodeSearchIndex.JarIndex index(IPackageFragmentRoot root, File jar, JarWork work,
            IProgressMonitor monitor) {
        List<BytecodeSearchEntry> entries = new ArrayList<>();
        Set<EntryKey> seen = new HashSet<>();
        Map<String, String> strings = new HashMap<>();

        try (ZipFile zip = new ZipFile(jar)) {
            IndexContext context = new IndexContext(root, jar, zip, entries, seen, strings);
            SubMonitor subMonitor = SubMonitor.convert(monitor, work.totalTicks());
            for (JarEntryWork entryWork : work.entries()) {
                if (subMonitor.isCanceled()) {
                    return null;
                }
                indexEntry(context, entryWork, subMonitor);
            }
            if (subMonitor.isCanceled()) {
                return null;
            }
        } catch (IOException e) {
            JavaDecompilerPlugin.logError(e, "Failed to index jar " + jar.getAbsolutePath()); //$NON-NLS-1$
        }
        return new BytecodeSearchIndex.JarIndex(jar, entries);
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
            indexClass(context.root(), input, context.entries(), context.seen(), context.strings());
        } catch (IOException | RuntimeException e) {
            JavaDecompilerPlugin.logError(e, "Failed to index class file from " + context.jar().getAbsolutePath()); //$NON-NLS-1$
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

    private static void indexClass(IPackageFragmentRoot root, InputStream input, List<BytecodeSearchEntry> entries,
            Set<EntryKey> seen, Map<String, String> strings) throws IOException {
        ClassReader reader = new ClassReader(input);
        ClassIndex classIndex = new ClassIndex(root, entries, seen, strings);
        reader.accept(classIndex.visitor, ClassReader.SKIP_FRAMES);

        if (classIndex.type == null && classIndex.moduleElement == null) {
            return;
        }

        classIndex.indexDescriptors();
        classIndex.flush();
    }

    private static final class ClassIndex {

        private final IPackageFragmentRoot root;
        private final List<BytecodeSearchEntry> entries;
        private final Set<EntryKey> seen;
        private final Map<String, String> strings;
        private final Map<String, String> elementHandles = new HashMap<>();
        private final Map<String, IJavaElement> anonymousElementFallbacks = new HashMap<>();
        private final Set<String> typeReferences = new HashSet<>();
        private final Set<String> descriptorSet = new HashSet<>();
        private final Map<IJavaElement, List<String>> typeReferencesByElement = new HashMap<>();
        private final Map<IJavaElement, List<MemberReference>> methodReferencesByElement = new HashMap<>();
        private final Map<IJavaElement, List<MemberReference>> constructorReferencesByElement = new HashMap<>();
        private final Map<IJavaElement, List<MemberReference>> fieldReferencesByElement = new HashMap<>();
        private final Map<String, NestedTypeName> nestedTypeNames = new HashMap<>();
        private final Set<String> moduleReferences = new HashSet<>();
        private final SignatureIndexer signatureIndexer = new SignatureIndexer();
        private final ClassVisitor visitor = new LightweightClassVisitor();

        private String className;
        private IType type;
        private TypeCategory typeCategory = TypeCategory.UNKNOWN;
        private IJavaElement moduleElement;
        private String enclosingClassName;

        private ClassIndex(IPackageFragmentRoot root, List<BytecodeSearchEntry> entries, Set<EntryKey> seen,
                Map<String, String> strings) {
            this.root = root;
            this.entries = entries;
            this.seen = seen;
            this.strings = strings;
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
            }
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
            return addReference(new ReferenceSpec(kind, name, qualifiedName, owner, descriptor, Access.NONE, false),
                    element);
        }

        private MemberReference addReference(Kind kind, String name, String qualifiedName, String owner, String descriptor,
                IJavaElement element, Access access) {
            return addReference(new ReferenceSpec(kind, name, qualifiedName, owner, descriptor, access, false), element);
        }

        private MemberReference addReference(ReferenceSpec reference, IJavaElement element) {
            IJavaElement enclosingElement = element == null ? type : element;
            MemberReference member = new MemberReference(reference.name(), reference.owner(), reference.descriptor(),
                    reference.access(), reference.compoundCandidate());
            switch (reference.kind()) {
              case FIELD -> fieldReferencesByElement.computeIfAbsent(enclosingElement, key -> new ArrayList<>()).add(member);
              case METHOD -> methodReferencesByElement.computeIfAbsent(enclosingElement, key -> new ArrayList<>()).add(member);
              case CONSTRUCTOR -> constructorReferencesByElement.computeIfAbsent(enclosingElement, key -> new ArrayList<>()).add(member);
              default -> add(reference.kind(), false, enclosingElement, pool(reference.name()),
                      pool(reference.qualifiedName()), pool(reference.owner()), null);
            }
            return member;
        }

        private void addReferenceEntry(Kind kind, String name, String qualifiedName, String owner, String descriptor,
                IJavaElement element, Access access) {
            addReferenceEntry(new EntrySpec(kind, false, pool(name), pool(qualifiedName), pool(owner),
                    pool(descriptor), access, TypeCategory.UNKNOWN), element);
        }

        private void addReferenceEntry(EntrySpec spec, IJavaElement element) {
            add(newEntry(element, spec), true);
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
            add(Kind.PACKAGE, false, element, pool(packageName), pool(packageName), null, null);
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
                    addReferenceEntry(kind, member.name(), qualifiedName, member.owner(), member.descriptor(),
                            entry.getKey(), member.access());
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
                                Access.READ_WRITE, false));
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

        private void add(BytecodeSearchEntry entry, boolean preserveDuplicate) {
            if (entry.getElementHandle() == null) {
                return;
            }
            EntryKey key = new EntryKey(entry.getKind(), entry.isDeclaration(), entry.getElementHandle(),
                    entry.getName(), entry.getQualifiedName(), entry.getDeclaringTypeName(), entry.getDescriptor(),
                    entry.getAccess(), entry.getTypeCategory());
            if (preserveDuplicate || seen.add(key)) {
                entries.add(entry);
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
                if (!MODULE_INFO.equals(name)) {
                    type = typeForInternalName(root, name);
                }
                addDescriptor(signature);
                if (!OBJECT_INTERNAL_NAME.equals(superName)) {
                    addTypeReference(superName);
                }
                if (interfaces != null) {
                    Collections.addAll(typeReferences, interfaces);
                }
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
                addDescriptorReferences(signature == null ? descriptor : signature, field);
                return new FieldIndexer(field);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                if ((access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) {
                    return null;
                }
                IMethod method = null;
                if (CONSTRUCTOR.equals(name)) {
                    method = type == null ? null : type.getMethod(type.getElementName(), jdtParameterTypes(descriptor));
                    add(Kind.CONSTRUCTOR, true, method, pool(simpleTypeName(className)), pool(simpleTypeName(className)),
                            pool(qualifiedTypeName(className)), pool(descriptor));
                } else if (!CLASS_INITIALIZER.equals(name)) {
                    method = type == null ? null : type.getMethod(name, jdtParameterTypes(descriptor));
                    add(Kind.METHOD, true, method, pool(name), pool(name), pool(qualifiedTypeName(className)),
                            pool(descriptor));
                }
                IJavaElement methodOrType = method != null ? method : type;
                addDescriptorReferences(declarationDescriptor(name, descriptor, signature), methodOrType);
                if (exceptions != null) {
                    for (String exception : exceptions) {
                        addTypeReference(exception, methodOrType);
                    }
                }
                int paramSlots = 0;
                for (org.objectweb.asm.Type argType : org.objectweb.asm.Type.getArgumentTypes(descriptor)) {
                    paramSlots += argType.getSize();
                }
                boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
                return new MethodIndexer(methodOrType, paramSlots + (isStatic ? 0 : 1));
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
            return typeCategory(typeForInternalName(root, internalName));
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
                addTypeReference(className, element);
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
            private final int firstLocalSlot;
            private Label prevHandlerLabel = null;
            private MemberReference pendingStaticCompoundRead;
            private IJavaElement pendingStaticCompoundElement;
            private int previousOpcode = -1;

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
                }
                addTypeReference(type, method);
                previousOpcode = opcode;
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (opcode == Opcodes.GETSTATIC) {
                    clearPendingStaticCompoundRead();
                }
                if (opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) {
                    // Owner is source-visible only for qualified static accesses (e.g. MyClass.FIELD).
                    // Instance field owners (GETFIELD/PUTFIELD) are not written as type tokens in source.
                    addTypeReference(owner, method);
                }
                addDescriptorReferences(descriptor, method);
                boolean compoundCandidate = opcode == Opcodes.GETSTATIC
                        || opcode == Opcodes.GETFIELD && previousOpcode == Opcodes.DUP;
                MemberReference member = addReference(new ReferenceSpec(Kind.FIELD, name, name, qualifiedTypeName(owner),
                        descriptor, fieldAccess(opcode), compoundCandidate), method);
                if (opcode == Opcodes.GETSTATIC) {
                    pendingStaticCompoundRead = member;
                    pendingStaticCompoundElement = method == null ? type : method;
                } else if (opcode == Opcodes.PUTSTATIC) {
                    pendingStaticCompoundRead = null;
                    pendingStaticCompoundElement = null;
                }
                previousOpcode = opcode;
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                clearPendingStaticCompoundRead();
                if (!CONSTRUCTOR.equals(name) || !consumePendingNew(owner)) {
                    addTypeReference(owner, method);
                }
                addDescriptorReferences(descriptor, method);
                if (CONSTRUCTOR.equals(name)) {
                    addReference(Kind.CONSTRUCTOR, simpleTypeName(owner), qualifiedTypeName(owner),
                            qualifiedTypeName(owner), descriptor, method);
                } else {
                    addReference(Kind.METHOD, name, name, qualifiedTypeName(owner), descriptor, method);
                }
                previousOpcode = opcode;
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, org.objectweb.asm.Handle bootstrapMethodHandle,
                    Object... bootstrapMethodArguments) {
                clearPendingStaticCompoundRead();
                addDescriptorReferences(descriptor, method);
                if (bootstrapMethodArguments != null) {
                    for (Object argument : bootstrapMethodArguments) {
                        addBootstrapArgumentReference(argument, method);
                    }
                }
                previousOpcode = Opcodes.INVOKEDYNAMIC;
            }

            @Override
            public void visitLdcInsn(Object value) {
                addBootstrapArgumentReference(value, method);
                previousOpcode = Opcodes.LDC;
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                addDescriptorReferences(descriptor, method);
                previousOpcode = Opcodes.MULTIANEWARRAY;
            }

            @Override
            public void visitInsn(int opcode) {
                previousOpcode = opcode;
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                previousOpcode = opcode;
            }

            @Override
            public void visitVarInsn(int opcode, int varIndex) {
                if (isStoreOpcode(opcode)) {
                    clearPendingStaticCompoundRead();
                }
                previousOpcode = opcode;
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
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
                            pendingStaticCompoundRead.access(), false));
                }
                pendingStaticCompoundRead = null;
                pendingStaticCompoundElement = null;
            }

            private static boolean isStoreOpcode(int opcode) {
                return opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE;
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
            public void visitLabel(Label label) {
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
                }
                addTypeReference(type, method);
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
                    Collections.addAll(typeReferences, providers);
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
            boolean compoundCandidate) {
    }

    private record ReferenceSpec(Kind kind, String name, String qualifiedName, String owner, String descriptor,
            Access access, boolean compoundCandidate) {
    }

    private record NestedTypeName(String outerName, String innerName, int access) {
    }

    private record EntryKey(Kind kind, boolean declaration, String elementHandle, String name, String qualifiedName,
            String declaringTypeName, String descriptor, Access access, TypeCategory typeCategory) {
    }

    private record EntrySpec(Kind kind, boolean declaration, String name, String qualifiedName,
            String declaringTypeName, String descriptor, Access access, TypeCategory typeCategory) {
    }

    private record IndexContext(IPackageFragmentRoot root, File jar, ZipFile zip, List<BytecodeSearchEntry> entries,
            Set<EntryKey> seen, Map<String, String> strings) {
    }

    private record VersionedClassName(String logicalName, int version) {
    }

    private record EffectiveClassEntry(String logicalName, String entryName, int version, long impactBytes, int ticks) {

        private static EffectiveClassEntry newer(EffectiveClassEntry left, EffectiveClassEntry right) {
            return left.version() >= right.version() ? left : right;
        }
    }

    public record JarWork(List<JarEntryWork> entries, long totalImpact, int totalTicks) {
    }

    public record JarEntryWork(String name, long impactBytes, int ticks) {
    }
}
