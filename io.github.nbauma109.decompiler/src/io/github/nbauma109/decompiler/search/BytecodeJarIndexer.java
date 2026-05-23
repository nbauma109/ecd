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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Signature;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;

final class BytecodeJarIndexer {

    private static final String CLASS_FILE_EXTENSION = ".class"; //$NON-NLS-1$
    private static final String CLASS_INITIALIZER = "<clinit>"; //$NON-NLS-1$
    private static final String CONSTRUCTOR = "<init>"; //$NON-NLS-1$
    private static final String MODULE_INFO = "module-info"; //$NON-NLS-1$
    private static final int ZIP_LOCAL_FILE_HEADER_SIZE = 30;
    private static final int ZIP_CENTRAL_DIRECTORY_FILE_HEADER_SIZE = 46;

    private BytecodeJarIndexer() {
    }

    static JarWork plan(File jar) {
        List<JarEntryWork> entries = new ArrayList<>();
        long totalImpact = 0L;
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> zipEntries = zip.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(CLASS_FILE_EXTENSION)) {
                    continue;
                }
                long impact = entryImpactBytes(entry);
                totalImpact += impact;
                entries.add(new JarEntryWork(entry.getName(), impact));
            }
        } catch (IOException e) {
            JavaDecompilerPlugin.logError(e, "Failed to inspect jar " + jar.getAbsolutePath()); //$NON-NLS-1$
        }
        return new JarWork(entries, totalImpact);
    }

    static BytecodeSearchIndex.JarIndex index(IPackageFragmentRoot root, File jar, JarWork work,
            IProgressMonitor monitor) {
        List<BytecodeSearchEntry> entries = new ArrayList<>();
        Set<EntryKey> seen = new HashSet<>();
        Map<String, String> strings = new HashMap<>();

        try (ZipFile zip = new ZipFile(jar)) {
            SubMonitor subMonitor = SubMonitor.convert(monitor, impactTicks(work.totalImpact()));
            for (JarEntryWork entryWork : work.entries()) {
                if (subMonitor.isCanceled()) {
                    break;
                }
                subMonitor.subTask(entryWork.name());
                ZipEntry entry = zip.getEntry(entryWork.name());
                if (entry == null) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    indexClass(root, input, entries, seen, strings);
                } catch (IOException | RuntimeException e) {
                    JavaDecompilerPlugin.logError(e, "Failed to index class file from " + jar.getAbsolutePath()); //$NON-NLS-1$
                } finally {
                    subMonitor.worked(impactTicks(entryWork.impactBytes()));
                }
            }
        } catch (IOException e) {
            JavaDecompilerPlugin.logError(e, "Failed to index jar " + jar.getAbsolutePath()); //$NON-NLS-1$
        }
        return new BytecodeSearchIndex.JarIndex(jar, entries);
    }

    static int impactTicks(long impactBytes) {
        long kibibytes = (impactBytes + 1023L) / 1024L;
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, kibibytes));
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
        reader.accept(classIndex.visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

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
        private final Map<IJavaElement, Set<String>> typeReferencesByElement = new HashMap<>();
        private final Map<IJavaElement, Set<MemberName>> methodReferencesByElement = new HashMap<>();
        private final Map<IJavaElement, Set<MemberName>> constructorReferencesByElement = new HashMap<>();
        private final Map<IJavaElement, Set<MemberName>> fieldReferencesByElement = new HashMap<>();
        private final Set<String> moduleReferences = new HashSet<>();
        private final SignatureIndexer signatureIndexer = new SignatureIndexer();
        private final ClassVisitor visitor = new LightweightClassVisitor();

        private String className;
        private IType type;
        private IJavaElement moduleElement;

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

            add(Kind.TYPE, true, type, pool(simpleTypeName(className)), pool(qualifiedTypeName(className)), null, null);
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

        private void addTypeReference(String internalName) {
            if (internalName == null || internalName.isBlank() || MODULE_INFO.equals(internalName)) {
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
            if (internalName == null || internalName.isBlank() || MODULE_INFO.equals(internalName)) {
                return;
            }
            if (internalName.charAt(0) == '[') {
                addDescriptorReferences(internalName, element);
            } else {
                typeReferencesByElement.computeIfAbsent(element, key -> new HashSet<>()).add(internalName);
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
            }
        }

        private void addDescriptor(String descriptor) {
            if (descriptor != null && !descriptor.isBlank()) {
                descriptorSet.add(descriptor);
            }
        }

        private void addDescriptorReferences(String descriptor) {
            addDescriptorReferences(descriptor, null);
        }

        private void addDescriptorReferences(String descriptor, IJavaElement element) {
            if (descriptor == null || descriptor.isBlank()) {
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

        private AnnotationVisitor annotationVisitor(IJavaElement element) {
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override
                public void visit(String name, Object value) {
                    addBootstrapArgumentReference(value, element);
                }

                @Override
                public void visitEnum(String name, String descriptor, String value) {
                    addDescriptorReferences(descriptor, element);
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

        private void addBootstrapArgumentReference(Object argument, IJavaElement element) {
            if (argument instanceof Type asmType) {
                addTypeReference(asmType, element);
            } else if (argument instanceof Handle handle) {
                addHandleReference(handle, element);
            }
        }

        private void addHandleReference(Handle handle, IJavaElement element) {
            if (handle == null) {
                return;
            }
            addTypeReference(handle.getOwner(), element);
            if (handle.getTag() == Opcodes.H_GETFIELD || handle.getTag() == Opcodes.H_GETSTATIC
                    || handle.getTag() == Opcodes.H_PUTFIELD || handle.getTag() == Opcodes.H_PUTSTATIC) {
                addReference(Kind.FIELD, handle.getName(), handle.getName(), qualifiedTypeName(handle.getOwner()),
                        handle.getDesc(), element);
            } else if (CONSTRUCTOR.equals(handle.getName())) {
                addReference(Kind.CONSTRUCTOR, simpleTypeName(handle.getOwner()), qualifiedTypeName(handle.getOwner()),
                        qualifiedTypeName(handle.getOwner()), handle.getDesc(), element);
            } else {
                addReference(Kind.METHOD, handle.getName(), handle.getName(), qualifiedTypeName(handle.getOwner()),
                        handle.getDesc(), element);
            }
        }

        private void addReference(Kind kind, String name, String qualifiedName, String owner) {
            MemberName member = new MemberName(name, owner, null);
            if (kind == Kind.FIELD) {
                fieldReferencesByElement.computeIfAbsent(type, key -> new HashSet<>()).add(member);
            } else if (kind == Kind.METHOD) {
                methodReferencesByElement.computeIfAbsent(type, key -> new HashSet<>()).add(member);
            } else if (kind == Kind.CONSTRUCTOR) {
                constructorReferencesByElement.computeIfAbsent(type, key -> new HashSet<>()).add(member);
            } else {
                add(kind, false, type, pool(name), pool(qualifiedName), pool(owner), null);
            }
        }

        private void addReference(Kind kind, String name, String qualifiedName, String owner, IJavaElement element) {
            addReference(kind, name, qualifiedName, owner, null, element);
        }

        private void addReference(Kind kind, String name, String qualifiedName, String owner, String descriptor,
                IJavaElement element) {
            IJavaElement enclosingElement = element == null ? type : element;
            MemberName member = new MemberName(name, owner, descriptor);
            if (kind == Kind.FIELD) {
                fieldReferencesByElement.computeIfAbsent(enclosingElement, key -> new HashSet<>()).add(member);
            } else if (kind == Kind.METHOD) {
                methodReferencesByElement.computeIfAbsent(enclosingElement, key -> new HashSet<>()).add(member);
            } else if (kind == Kind.CONSTRUCTOR) {
                constructorReferencesByElement.computeIfAbsent(enclosingElement, key -> new HashSet<>()).add(member);
            } else {
                add(kind, false, enclosingElement, pool(name), pool(qualifiedName), pool(owner), null);
            }
        }

        private void addReferenceEntry(Kind kind, String name, String qualifiedName, String owner, IJavaElement element) {
            addReferenceEntry(kind, name, qualifiedName, owner, null, element);
        }

        private void addReferenceEntry(Kind kind, String name, String qualifiedName, String owner, String descriptor,
                IJavaElement element) {
            add(kind, false, element, pool(name), pool(qualifiedName), pool(owner), pool(descriptor));
        }

        private void addTypeReferenceEntry(String internalName, IJavaElement element) {
            addReferenceEntry(Kind.TYPE, simpleTypeName(internalName), qualifiedTypeName(internalName), null, null,
                    element);
        }

        private void addPackageDeclaration(String packageName) {
            if (packageName == null || packageName.isBlank()) {
                return;
            }
            IPackageFragment pkg = root.getPackageFragment(packageName);
            add(Kind.PACKAGE, true, pkg, pool(packageName), pool(packageName), null, null);
        }

        private void addPackageReference(String packageName, IJavaElement element) {
            if (packageName == null || packageName.isBlank()) {
                return;
            }
            add(Kind.PACKAGE, false, element, pool(packageName), pool(packageName), null, null);
        }

        private void flushTypeReferencesByElement() {
            for (Map.Entry<IJavaElement, Set<String>> entry : typeReferencesByElement.entrySet()) {
                for (String internalName : entry.getValue()) {
                    addTypeReferenceEntry(internalName, entry.getKey());
                    addPackageReference(packageName(internalName), entry.getKey());
                }
            }
        }

        private void flushMemberReferences(Map<IJavaElement, Set<MemberName>> references, Kind kind) {
            for (Map.Entry<IJavaElement, Set<MemberName>> entry : references.entrySet()) {
                for (MemberName member : entry.getValue()) {
                    addReferenceEntry(kind, member.name(), member.owner(), member.owner(), member.descriptor(),
                            entry.getKey());
                }
            }
        }

        private void add(Kind kind, boolean declaration, IJavaElement element, String name, String qualifiedName,
                String declaringTypeName, String descriptor) {
            String elementHandle = elementHandle(element);
            IJavaElement fallback = anonymousElementFallback(elementHandle, element);
            add(new BytecodeSearchEntry(kind, declaration, elementHandle, fallback, name, qualifiedName,
                    declaringTypeName, descriptor));
        }

        private void add(BytecodeSearchEntry entry) {
            if (entry.getElementHandle() == null) {
                return;
            }
            EntryKey key = new EntryKey(entry.getKind(), entry.isDeclaration(), entry.getElementHandle(), entry.getName(),
                    entry.getQualifiedName(), entry.getDeclaringTypeName(), entry.getDescriptor());
            if (seen.add(key)) {
                entries.add(entry);
            }
        }

        private String elementHandle(IJavaElement element) {
            if (element == null) {
                return null;
            }
            String handle = element.getHandleIdentifier();
            return handle == null ? null : elementHandles.computeIfAbsent(handle, key -> key);
        }

        private IJavaElement anonymousElementFallback(String elementHandle, IJavaElement element) {
            if (elementHandle == null || !elementHandle.contains("[~")) { //$NON-NLS-1$
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

            private final AnnotationIndexer annotationIndexer = new AnnotationIndexer();
            private final ModuleIndexer moduleIndexer = new ModuleIndexer();

            private LightweightClassVisitor() {
                super(Opcodes.ASM9);
            }

            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                    String[] interfaces) {
                className = name;
                if (!MODULE_INFO.equals(name)) {
                    type = typeForInternalName(root, name);
                }
                addDescriptor(signature);
                addTypeReference(superName);
                if (interfaces != null) {
                    Collections.addAll(typeReferences, interfaces);
                }
            }

            @Override
            public ModuleVisitor visitModule(String name, int access, String version) {
                IModuleDescription module = root.getModuleDescription();
                moduleElement = module == null ? root : module;
                add(Kind.MODULE, true, moduleElement, pool(name), pool(name), null, null);
                return moduleIndexer;
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
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                IField field = type == null ? null : type.getField(name);
                add(Kind.FIELD, true, field, pool(name), pool(name), pool(qualifiedTypeName(className)),
                        pool(descriptor));
                addDescriptorReferences(signature == null ? descriptor : signature, field);
                return new FieldIndexer(annotationIndexer, field);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
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
                addDescriptorReferences(signature == null ? descriptor : signature, method);
                if (exceptions != null) {
                    for (String exception : exceptions) {
                        addTypeReference(exception, method);
                    }
                }
                return new MethodIndexer(annotationIndexer, method);
            }
        }

        private final class SignatureIndexer extends SignatureVisitor {

            private final IJavaElement element;

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
                addTypeReference(name, element);
            }
        }

        private final class AnnotationIndexer extends AnnotationVisitor {

            private AnnotationIndexer() {
                super(Opcodes.ASM9);
            }

            @Override
            public void visitEnum(String name, String descriptor, String value) {
                addDescriptor(descriptor);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                addDescriptor(descriptor);
                return this;
            }
        }

        private final class FieldIndexer extends FieldVisitor {

            private final AnnotationIndexer annotationIndexer;
            private final IJavaElement field;

            private FieldIndexer(AnnotationIndexer annotationIndexer, IJavaElement field) {
                super(Opcodes.ASM9);
                this.annotationIndexer = annotationIndexer;
                this.field = field;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                addDescriptorReferences(descriptor, field);
                return annotationVisitor(field);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath typePath,
                    String descriptor, boolean visible) {
                addDescriptorReferences(descriptor, field);
                return annotationVisitor(field);
            }
        }

        private final class MethodIndexer extends MethodVisitor {

            private final AnnotationIndexer annotationIndexer;
            private final IJavaElement method;

            private MethodIndexer(AnnotationIndexer annotationIndexer, IJavaElement method) {
                super(Opcodes.ASM9);
                this.annotationIndexer = annotationIndexer;
                this.method = method;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                addDescriptorReferences(descriptor, method);
                return annotationVisitor(method);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath typePath,
                    String descriptor, boolean visible) {
                addDescriptorReferences(descriptor, method);
                return annotationVisitor(method);
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                addDescriptorReferences(descriptor, method);
                return annotationVisitor(method);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                addTypeReference(type, method);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                addTypeReference(owner, method);
                addDescriptorReferences(descriptor, method);
                addReference(Kind.FIELD, name, name, qualifiedTypeName(owner), descriptor, method);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                addTypeReference(owner, method);
                addDescriptorReferences(descriptor, method);
                if (CONSTRUCTOR.equals(name)) {
                    addReference(Kind.CONSTRUCTOR, simpleTypeName(owner), qualifiedTypeName(owner),
                            qualifiedTypeName(owner), descriptor, method);
                } else {
                    addReference(Kind.METHOD, name, name, qualifiedTypeName(owner), descriptor, method);
                }
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, org.objectweb.asm.Handle bootstrapMethodHandle,
                    Object... bootstrapMethodArguments) {
                addDescriptorReferences(descriptor, method);
                addHandleReference(bootstrapMethodHandle, method);
                if (bootstrapMethodArguments != null) {
                    for (Object argument : bootstrapMethodArguments) {
                        addBootstrapArgumentReference(argument, method);
                    }
                }
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Type asmType) {
                    addTypeReference(asmType, method);
                }
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                addDescriptorReferences(descriptor, method);
            }

            @Override
            public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end,
                    org.objectweb.asm.Label handler, String type) {
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
                addModuleReferences(modules);
            }

            @Override
            public void visitOpen(String packaze, int access, String... modules) {
                addModuleReferences(modules);
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

    private record MemberName(String name, String owner, String descriptor) {
    }

    private record EntryKey(Kind kind, boolean declaration, String elementHandle, String name, String qualifiedName,
            String declaringTypeName, String descriptor) {
    }

    record JarWork(List<JarEntryWork> entries, long totalImpact) {
    }

    record JarEntryWork(String name, long impactBytes) {
    }

    private static IType typeForInternalName(IPackageFragmentRoot root, String internalName) {
        IPackageFragment pkg = root.getPackageFragment(packageName(internalName));
        IClassFile classFile = pkg.getClassFile(classFileName(internalName));
        try {
            return classFile.getType();
        } catch (UnsupportedOperationException e) {
            JavaDecompilerPlugin.logError(e, "Unsupported class file model for " + internalName); //$NON-NLS-1$
            return null;
        }
    }

    private static String[] jdtParameterTypes(String descriptor) {
        org.objectweb.asm.Type[] argumentTypes = org.objectweb.asm.Type.getArgumentTypes(descriptor);
        String[] result = new String[argumentTypes.length];
        for (int i = 0; i < argumentTypes.length; i++) {
            result[i] = jdtTypeSignature(argumentTypes[i]);
        }
        return result;
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

    private static boolean isTypeSignature(String signature) {
        char first = signature.charAt(0);
        return first == 'L' || first == '[' || first == 'T' || first == '+' || first == '-' || first == '*';
    }

    private static boolean isClassSignature(String signature) {
        char first = signature.charAt(0);
        return first == '<' || first == 'L';
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

    private static String simpleTypeName(String internalName) {
        int separator = Math.max(internalName.lastIndexOf('/'), internalName.lastIndexOf('$'));
        return separator < 0 ? internalName : internalName.substring(separator + 1);
    }

    private static String qualifiedTypeName(String internalName) {
        return internalName == null ? null : internalName.replace('/', '.').replace('$', '.');
    }
}
