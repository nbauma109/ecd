/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.util.Logger;

final class BytecodeSourceRangeResolver {

    private static final int MAX_PARSED_CLASS_FILES = 8;

    private final Map<String, ParsedClassFile> classFiles = new LinkedHashMap<>(MAX_PARSED_CLASS_FILES, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ParsedClassFile> eldest) {
            return size() > MAX_PARSED_CLASS_FILES;
        }
    };

    SourceRange rangeFor(BytecodeSearchEntry entry) {
        return rangeFor(entry, null);
    }

    SourceRange rangeFor(BytecodeSearchEntry entry, String source) {
        List<SourceRange> ranges = rangesFor(entry, source, null);
        return selectRange(entry, ranges);
    }

    Map<BytecodeSearchEntry, SourceRange> rangesFor(List<BytecodeSearchEntry> entries, String source) {
        ParsedClassFile parsed = source == null || source.isBlank() ? null : parse(source);
        Map<BytecodeSearchEntry, SourceRange> ranges = new IdentityHashMap<>(entries.size());
        Map<ReferenceKey, Integer> ordinals = new HashMap<>();
        for (BytecodeSearchEntry entry : entries) {
            ReferenceKey key = ReferenceKey.from(entry);
            int ordinal = ordinals.merge(key, Integer.valueOf(1), Integer::sum).intValue() - 1;
            ranges.put(entry, selectRange(entry, rangesFor(entry, source, parsed), ordinal));
        }
        return ranges;
    }

    private static SourceRange selectRange(BytecodeSearchEntry entry, List<SourceRange> ranges) {
        return selectRange(entry, ranges, 0);
    }

    private static SourceRange selectRange(BytecodeSearchEntry entry, List<SourceRange> ranges, int ordinal) {
        if (ranges.isEmpty()) {
            return enclosingRange(entry);
        }
        return ranges.get(Math.min(Math.max(0, ordinal), ranges.size() - 1));
    }

    private List<SourceRange> rangesFor(BytecodeSearchEntry entry, String source, ParsedClassFile parsedSource) {
        if (entry.isDeclaration()) {
            SourceRange declarationRange = declarationRange(entry);
            return List.of(declarationRange);
        }

        ParsedClassFile parsed = parsedSource;
        if (parsed == null) {
            parsed = source == null || source.isBlank() ? parsedClassFile(entry.getElement()) : parse(source);
        }
        if (parsed == null) {
            SourceRange fallback = enclosingRange(entry);
            return List.of(fallback);
        }

        List<SourceRange> ranges = parsed.references(entry);
        if (!ranges.isEmpty()) {
            return ranges;
        }
        SourceRange fallback = enclosingRange(entry);
        return List.of(fallback);
    }

    private ParsedClassFile parse(String source) {
        try {
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(source.toCharArray());
            CompilationUnit unit = (CompilationUnit) parser.createAST(null);
            return new ParsedClassFile(source, unit);
        } catch (RuntimeException e) {
            Logger.debug(e);
            return null;
        }
    }

    private ParsedClassFile parsedClassFile(IJavaElement element) {
        IClassFile classFile = classFile(element);
        if (classFile == null) {
            return null;
        }
        return classFiles.computeIfAbsent(classFile.getHandleIdentifier(), ignored -> parse(classFile));
    }

    private ParsedClassFile parse(IClassFile classFile) {
        try {
            String source = classFile.getSource();
            if (source == null || source.isBlank()) {
                return null;
            }
            return parse(source);
        } catch (JavaModelException | RuntimeException e) {
            Logger.debug(e);
            return null;
        }
    }

    private static IClassFile classFile(IJavaElement element) {
        if (element == null) {
            return null;
        }
        IJavaElement ancestor = element.getAncestor(IJavaElement.CLASS_FILE);
        return ancestor instanceof IClassFile classFile ? classFile : null;
    }

    private static SourceRange declarationRange(BytecodeSearchEntry entry) {
        ISourceRange nameRange = sourceRange(entry.getElement(), true);
        if (isValid(nameRange)) {
            return new SourceRange(nameRange.getOffset(), nameRange.getLength());
        }
        return enclosingRange(entry);
    }

    private static SourceRange enclosingRange(BytecodeSearchEntry entry) {
        ISourceRange range = sourceRange(entry.getElement(), false);
        if (isValid(range)) {
            return new SourceRange(range.getOffset(), Math.max(1, Math.min(range.getLength(), entry.getName().length())));
        }
        return new SourceRange(0, Math.max(1, entry.getName().length()));
    }

    private static ISourceRange sourceRange(IJavaElement element, boolean nameRange) {
        if (!(element instanceof ISourceReference sourceReference)) {
            return null;
        }
        try {
            return nameRange ? sourceReference.getNameRange() : sourceReference.getSourceRange();
        } catch (JavaModelException e) {
            Logger.debug(e);
            return null;
        }
    }

    private static boolean isValid(ISourceRange range) {
        return range != null && range.getOffset() >= 0 && range.getLength() > 0;
    }

    record SourceRange(int offset, int length) {
        SourceRange {
            if (offset < 0) {
                offset = 0;
            }
            if (length <= 0) {
                length = 1;
            }
        }
    }

    private static final class ParsedClassFile {

        private final String source;
        private final CompilationUnit unit;
        private final Map<ReferenceKey, List<SourceRange>> rangesByKey = new HashMap<>();

        private ParsedClassFile(String source, CompilationUnit unit) {
            this.source = source;
            this.unit = unit;
        }

        private List<SourceRange> references(BytecodeSearchEntry entry) {
            return rangesByKey.computeIfAbsent(ReferenceKey.from(entry), key -> computeReferences(entry));
        }

        private List<SourceRange> computeReferences(BytecodeSearchEntry entry) {
            SourceRange enclosing = enclosingSourceRange(entry.getElement(), unit);
            if (enclosing == null) {
                return List.of();
            }
            SourceWindow window = new SourceWindow(enclosing.offset(), enclosing.length());
            List<SourceRange> ranges = new ArrayList<>();
            unit.accept(new ReferenceVisitor(source, entry, window, ranges));
            ranges.sort(Comparator.comparingInt(SourceRange::offset));
            return List.copyOf(ranges);
        }

        private static SourceRange enclosingSourceRange(IJavaElement element, CompilationUnit unit) {
            SourceRange declarationRange = AstDeclarationWindow.find(element, unit);
            if (declarationRange != null) {
                return declarationRange;
            }
            ISourceRange sourceRange = sourceRange(element, false);
            if (isValid(sourceRange)) {
                return new SourceRange(sourceRange.getOffset(), sourceRange.getLength());
            }
            return null;
        }
    }

    private static final class AstDeclarationWindow extends ASTVisitor {

        private final IJavaElement element;
        private final TypePath typePath;
        private final List<String> typeStack = new ArrayList<>();
        private int anonymousTypeCounter;
        private SourceRange range;

        private AstDeclarationWindow(IJavaElement element) {
            this.element = element;
            this.typePath = TypePath.from(element);
        }

        static SourceRange find(IJavaElement element, CompilationUnit unit) {
            if (element == null || unit == null) {
                return null;
            }
            AstDeclarationWindow finder = new AstDeclarationWindow(element);
            unit.accept(finder);
            return finder.range;
        }

        @Override
        public boolean visit(TypeDeclaration node) {
            typeStack.add(node.getName().getIdentifier());
            if (element instanceof IType type && sameName(type.getElementName(), node.getName())
                    && matchesCurrentType()) {
                range = range(node);
                return false;
            }
            return range == null;
        }

        @Override
        public void endVisit(TypeDeclaration node) {
            popType();
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            typeStack.add(node.getName().getIdentifier());
            if (element instanceof IType type && sameName(type.getElementName(), node.getName())
                    && matchesCurrentType()) {
                range = range(node);
                return false;
            }
            return range == null;
        }

        @Override
        public void endVisit(EnumDeclaration node) {
            popType();
        }

        @Override
        public boolean visit(AnnotationTypeDeclaration node) {
            typeStack.add(node.getName().getIdentifier());
            if (element instanceof IType type && sameName(type.getElementName(), node.getName())
                    && matchesCurrentType()) {
                range = range(node);
                return false;
            }
            return range == null;
        }

        @Override
        public void endVisit(AnnotationTypeDeclaration node) {
            popType();
        }

        @Override
        public boolean visit(AnonymousClassDeclaration node) {
            typeStack.add(Integer.toString(++anonymousTypeCounter));
            return range == null;
        }

        @Override
        public void endVisit(AnonymousClassDeclaration node) {
            popType();
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            if (element instanceof IMethod method && matchesCurrentType() && matches(method, node)) {
                range = range(node);
                return false;
            }
            return range == null;
        }

        @Override
        public boolean visit(VariableDeclarationFragment node) {
            if (element instanceof IField field && matchesCurrentType() && sameName(field.getElementName(), node.getName())) {
                ASTNode parent = node.getParent();
                range = parent == null ? range(node) : range(parent);
                return false;
            }
            return range == null;
        }

        private static boolean matches(IMethod method, MethodDeclaration node) {
            try {
                if (method.isConstructor() != node.isConstructor()) {
                    return false;
                }
                if (!sameName(method.getElementName(), node.getName())) {
                    return false;
                }
                return sameParameterTypes(method, node);
            } catch (JavaModelException e) {
                Logger.debug(e);
                return false;
            }
        }

        private static boolean sameParameterTypes(IMethod method, MethodDeclaration node) {
            String[] methodParameterTypes = method.getParameterTypes();
            List<?> nodeParameters = node.parameters();
            if (methodParameterTypes.length != nodeParameters.size()) {
                return false;
            }
            for (int i = 0; i < methodParameterTypes.length; i++) {
                if (!(nodeParameters.get(i) instanceof SingleVariableDeclaration parameter)
                        || !sameType(normalizeJdtType(methodParameterTypes[i]), normalizeAstType(parameter))) {
                    return false;
                }
            }
            return true;
        }

        private static String normalizeJdtType(String signature) {
            int arrayDepth = Signature.getArrayCount(signature);
            String elementType = Signature.getElementType(signature);
            String normalized = switch (Signature.getTypeSignatureKind(elementType)) {
            case Signature.BASE_TYPE_SIGNATURE -> primitiveName(elementType);
            case Signature.CLASS_TYPE_SIGNATURE -> Signature.toString(elementType).replace('$', '.');
            default -> Signature.getSignatureSimpleName(elementType);
            };
            return normalized.toLowerCase(java.util.Locale.ROOT) + "[]".repeat(arrayDepth); //$NON-NLS-1$
        }

        private static String normalizeAstType(SingleVariableDeclaration parameter) {
            Type type = parameter.getType();
            String text = type == null ? "" : type.toString(); //$NON-NLS-1$
            int genericStart = text.indexOf('<');
            if (genericStart >= 0) {
                text = text.substring(0, genericStart);
            }
            String normalized = text.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT); //$NON-NLS-1$ //$NON-NLS-2$
            return parameter.isVarargs() ? normalized + "[]" : normalized; //$NON-NLS-1$
        }

        private static String primitiveName(String signature) {
            return switch (signature) {
            case "Z" -> "boolean"; //$NON-NLS-1$ //$NON-NLS-2$
            case "B" -> "byte"; //$NON-NLS-1$ //$NON-NLS-2$
            case "C" -> "char"; //$NON-NLS-1$ //$NON-NLS-2$
            case "D" -> "double"; //$NON-NLS-1$ //$NON-NLS-2$
            case "F" -> "float"; //$NON-NLS-1$ //$NON-NLS-2$
            case "I" -> "int"; //$NON-NLS-1$ //$NON-NLS-2$
            case "J" -> "long"; //$NON-NLS-1$ //$NON-NLS-2$
            case "S" -> "short"; //$NON-NLS-1$ //$NON-NLS-2$
            default -> signature;
            };
        }

        private static boolean sameType(String left, String right) {
            if (sameName(left, right)) {
                return true;
            }
            int leftSeparator = left.lastIndexOf('.');
            int rightSeparator = right.lastIndexOf('.');
            String leftSimple = leftSeparator < 0 ? left : left.substring(leftSeparator + 1);
            String rightSimple = rightSeparator < 0 ? right : right.substring(rightSeparator + 1);
            return sameName(leftSimple, rightSimple);
        }

        private static boolean sameName(String name, SimpleName simpleName) {
            return simpleName != null && sameName(name, simpleName.getIdentifier());
        }

        private static boolean sameName(String left, String right) {
            return left != null && right != null && left.equals(right);
        }

        private static SourceRange range(ASTNode node) {
            return new SourceRange(node.getStartPosition(), node.getLength());
        }

        private boolean matchesCurrentType() {
            return typePath == null || typePath.matches(typeStack);
        }

        private void popType() {
            if (!typeStack.isEmpty()) {
                typeStack.remove(typeStack.size() - 1);
            }
        }
    }

    private record TypePath(List<String> names) {

        private static TypePath from(IJavaElement element) {
            IClassFile classFile = classFile(element);
            if (classFile != null) {
                String classFileName = classFile.getElementName();
                if (classFileName.endsWith(".class")) { //$NON-NLS-1$
                    classFileName = classFileName.substring(0, classFileName.length() - ".class".length()); //$NON-NLS-1$
                }
                List<String> names = Arrays.asList(classFileName.split("\\$")); //$NON-NLS-1$
                return new TypePath(names);
            }
            IType type = null;
            if (element instanceof IType directType) {
                type = directType;
            } else if (element != null) {
                type = (IType) element.getAncestor(IJavaElement.TYPE);
            }
            if (type == null) {
                return null;
            }
            String typeName = type.getTypeQualifiedName('.');
            List<String> names = Arrays.asList(typeName.split("\\.")); //$NON-NLS-1$
            return new TypePath(names);
        }

        private boolean matches(List<String> currentTypeStack) {
            if (names.isEmpty() || currentTypeStack.size() < names.size()) {
                return false;
            }
            int offset = currentTypeStack.size() - names.size();
            for (int i = 0; i < names.size(); i++) {
                if (!names.get(i).equals(currentTypeStack.get(offset + i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private record ReferenceKey(String elementHandle, Kind kind, String name, String qualifiedName,
            String declaringTypeName, String descriptor, BytecodeSearchEntry.Access access) {

        private static ReferenceKey from(BytecodeSearchEntry entry) {
            return new ReferenceKey(entry.getElementHandle() == null ? "" : entry.getElementHandle(), entry.getKind(),
                    entry.getName(), entry.getQualifiedName(), entry.getDeclaringTypeName(), entry.getDescriptor(),
                    entry.getAccess());
        }
    }

    private record SourceWindow(int offset, int length) {

        private boolean contains(ASTNode node) {
            return contains(node.getStartPosition(), node.getLength());
        }

        private boolean contains(int nodeOffset, int nodeLength) {
            return nodeOffset >= offset && nodeLength > 0 && nodeOffset + nodeLength <= offset + length;
        }
    }

    private static final class ReferenceVisitor extends ASTVisitor {

        private final String source;
        private final BytecodeSearchEntry entry;
        private final SourceWindow window;
        private final List<SourceRange> ranges;

        private ReferenceVisitor(String source, BytecodeSearchEntry entry, SourceWindow window,
                List<SourceRange> ranges) {
            this.source = source;
            this.entry = entry;
            this.window = window;
            this.ranges = ranges;
        }

        @Override
        public boolean visit(SimpleName node) {
            if ((entry.getKind() == Kind.TYPE || entry.getKind() == Kind.FIELD) && matches(node)
                    && !isDeclarationName(node)) {
                add(node);
            }
            return true;
        }

        @Override
        public boolean visit(QualifiedName node) {
            if (entry.getKind() == Kind.PACKAGE && sameName(node.getFullyQualifiedName(), entry.getName())) {
                add(node);
            }
            return true;
        }

        @Override
        public boolean visit(MethodInvocation node) {
            if (entry.getKind() == Kind.METHOD && matches(node.getName())) {
                addFromNameThroughNode(node.getName(), node);
            }
            return true;
        }

        @Override
        public boolean visit(SuperMethodInvocation node) {
            if (entry.getKind() == Kind.METHOD && matches(node.getName())) {
                addFromNameThroughNode(node.getName(), node);
            }
            return true;
        }

        @Override
        public boolean visit(ExpressionMethodReference node) {
            if (entry.getKind() == Kind.METHOD && matches(node.getName())) {
                add(node.getName());
            }
            return true;
        }

        @Override
        public boolean visit(TypeMethodReference node) {
            if (entry.getKind() == Kind.METHOD && matches(node.getName())) {
                add(node.getName());
            }
            return true;
        }

        @Override
        public boolean visit(SuperMethodReference node) {
            if (entry.getKind() == Kind.METHOD && matches(node.getName())) {
                add(node.getName());
            }
            return true;
        }

        @Override
        public boolean visit(ClassInstanceCreation node) {
            if (entry.getKind() == Kind.CONSTRUCTOR && node.getType() != null && matchesLastName(node.getType())) {
                addLastName(node.getType());
            }
            return true;
        }

        @Override
        public boolean visit(CreationReference node) {
            if (entry.getKind() == Kind.CONSTRUCTOR && node.getType() != null && matchesLastName(node.getType())) {
                addLastName(node.getType());
            }
            return true;
        }

        @Override
        public boolean visit(ConstructorInvocation node) {
            if (entry.getKind() == Kind.CONSTRUCTOR) {
                addKeyword(node, "this"); //$NON-NLS-1$
            }
            return true;
        }

        @Override
        public boolean visit(SuperConstructorInvocation node) {
            if (entry.getKind() == Kind.CONSTRUCTOR) {
                addKeyword(node, "super"); //$NON-NLS-1$
            }
            return true;
        }

        private boolean matches(SimpleName node) {
            return sameName(node.getIdentifier(), entry.getName()) || sameName(node.getIdentifier(), simpleName());
        }

        private String simpleName() {
            String qualifiedName = entry.getQualifiedName();
            int separator = qualifiedName == null ? -1 : qualifiedName.lastIndexOf('.');
            return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
        }

        private boolean sameName(String left, String right) {
            return left != null && right != null && left.equals(right);
        }

        private void add(ASTNode node) {
            if (window.contains(node)) {
                ranges.add(new SourceRange(node.getStartPosition(), node.getLength()));
            }
        }

        private void addFromNameThroughNode(SimpleName name, ASTNode node) {
            if (!window.contains(node)) {
                return;
            }
            int start = name.getStartPosition();
            int end = node.getStartPosition() + node.getLength();
            if (start >= 0 && end > start) {
                ranges.add(new SourceRange(start, end - start));
            }
        }

        private void addLastName(ASTNode node) {
            if (!window.contains(node)) {
                return;
            }
            LastName lastName = lastName(node);
            if (lastName != null) {
                ranges.add(new SourceRange(lastName.offset(), lastName.length()));
            }
        }

        private boolean matchesLastName(ASTNode node) {
            LastName lastName = lastName(node);
            return lastName != null && sameName(lastName.name(), entry.getName());
        }

        private LastName lastName(ASTNode node) {
            int nodeOffset = node.getStartPosition();
            int nodeLength = node.getLength();
            if (nodeOffset < 0 || nodeLength <= 0 || nodeOffset > source.length()) {
                return null;
            }
            int nodeEnd = nodeOffset + nodeLength;
            if (nodeEnd < nodeOffset || nodeEnd > source.length()) {
                return null;
            }
            String text = source.substring(nodeOffset, nodeEnd);
            int end = text.length();
            while (end > 0 && !Character.isJavaIdentifierPart(text.charAt(end - 1))) {
                end--;
            }
            int start = end;
            while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
                start--;
            }
            if (start < end) {
                return new LastName(nodeOffset + start, end - start, text.substring(start, end));
            }
            return null;
        }

        private void addKeyword(ASTNode node, String keyword) {
            if (!window.contains(node)) {
                return;
            }
            int start = node.getStartPosition();
            int end = Math.min(source.length(), start + node.getLength());
            int offset = source.indexOf(keyword, start);
            if (offset >= start && offset < end) {
                ranges.add(new SourceRange(offset, keyword.length()));
            }
        }

        private boolean isDeclarationName(SimpleName node) {
            ASTNode parent = node.getParent();
            return parent instanceof TypeDeclaration type && type.getName() == node
                    || parent instanceof MethodDeclaration method && method.getName() == node
                    || parent instanceof VariableDeclarationFragment variable && variable.getName() == node
                    || parent instanceof SingleVariableDeclaration variable && variable.getName() == node
                    || parent instanceof EnumDeclaration enumDeclaration && enumDeclaration.getName() == node
                    || parent instanceof EnumConstantDeclaration enumConstant && enumConstant.getName() == node
                    || parent instanceof AnnotationTypeDeclaration annotation && annotation.getName() == node
                    || parent instanceof AnnotationTypeMemberDeclaration member && member.getName() == node;
        }
    }

    private record LastName(int offset, int length, String name) {
    }
}
