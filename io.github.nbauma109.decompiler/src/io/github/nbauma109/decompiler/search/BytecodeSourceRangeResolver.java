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
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
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
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclaration;
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
        BytecodeSearchDebug.info("rangeFor started: sourceLength=" + (source == null ? -1 : source.length()) //$NON-NLS-1$
                + ", " + BytecodeSearchDebug.describeEntry(entry)); //$NON-NLS-1$
        List<SourceRange> ranges = rangesFor(entry, source, null);
        SourceRange range = selectRange(entry, ranges);
        BytecodeSearchDebug.info("rangeFor completed: candidateCount=" + ranges.size() + ", selected=" //$NON-NLS-1$ //$NON-NLS-2$
                + BytecodeSearchDebug.describeRange(range, source));
        return range;
    }

    Map<BytecodeSearchEntry, SourceRange> rangesFor(List<BytecodeSearchEntry> entries, String source) {
        ParsedClassFile parsed = source == null || source.isBlank() ? null : parse(source);
        Map<BytecodeSearchEntry, SourceRange> ranges = new IdentityHashMap<>(entries.size());
        for (BytecodeSearchEntry entry : entries) {
            ranges.put(entry, selectRange(entry, rangesFor(entry, source, parsed)));
        }
        return ranges;
    }

    private static SourceRange selectRange(BytecodeSearchEntry entry, List<SourceRange> ranges) {
        return ranges.isEmpty() ? enclosingRange(entry) : ranges.get(0);
    }

    private List<SourceRange> rangesFor(BytecodeSearchEntry entry, String source, ParsedClassFile parsedSource) {
        if (entry.isDeclaration()) {
            SourceRange declarationRange = declarationRange(entry);
            BytecodeSearchDebug.info("rangesFor declaration: " + BytecodeSearchDebug.describeRange(declarationRange, source)); //$NON-NLS-1$
            return List.of(declarationRange);
        }

        ParsedClassFile parsed = parsedSource != null ? parsedSource
                : source == null || source.isBlank() ? parsedClassFile(entry.getElement()) : parse(source);
        if (parsed == null) {
            SourceRange fallback = enclosingRange(entry);
            BytecodeSearchDebug.info("rangesFor fallback: no parsed source, using enclosing " //$NON-NLS-1$
                    + BytecodeSearchDebug.describeRange(fallback, source));
            return List.of(fallback);
        }

        List<SourceRange> ranges = parsed.references(entry);
        if (!ranges.isEmpty()) {
            BytecodeSearchDebug.info("rangesFor references matched: count=" + ranges.size() + ", first=" //$NON-NLS-1$ //$NON-NLS-2$
                    + BytecodeSearchDebug.describeRange(ranges.get(0), source));
            return ranges;
        }
        SourceRange fallback = enclosingRange(entry);
        BytecodeSearchDebug.info("rangesFor fallback: no AST references matched, using enclosing " //$NON-NLS-1$
                + BytecodeSearchDebug.describeRange(fallback, source));
        return List.of(fallback);
    }

    private ParsedClassFile parse(String source) {
        try {
            BytecodeSearchDebug.info("parse source started: length=" + (source == null ? -1 : source.length())); //$NON-NLS-1$
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(source.toCharArray());
            CompilationUnit unit = (CompilationUnit) parser.createAST(null);
            BytecodeSearchDebug.info("parse source completed: rootLength=" + unit.getLength()); //$NON-NLS-1$
            return new ParsedClassFile(source, unit);
        } catch (RuntimeException e) {
            BytecodeSearchDebug.info("parse source failed: " + e.getClass().getName() + ": " //$NON-NLS-1$ //$NON-NLS-2$
                    + BytecodeSearchDebug.safeText(e.getMessage()));
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
            BytecodeSearchDebug.info("parse class file source started: " //$NON-NLS-1$
                    + BytecodeSearchDebug.describeElement(classFile));
            String source = classFile.getSource();
            if (source == null || source.isBlank()) {
                BytecodeSearchDebug.info("parse class file source unavailable"); //$NON-NLS-1$
                return null;
            }
            return parse(source);
        } catch (JavaModelException | RuntimeException e) {
            BytecodeSearchDebug.info("parse class file source failed: " + e.getClass().getName() + ": " //$NON-NLS-1$ //$NON-NLS-2$
                    + BytecodeSearchDebug.safeText(e.getMessage()));
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
                BytecodeSearchDebug.info("computeReferences: no enclosing source range for " //$NON-NLS-1$
                        + BytecodeSearchDebug.describeElement(entry.getElement()));
                return List.of();
            }
            BytecodeSearchDebug.info("computeReferences: window=" + BytecodeSearchDebug.describeRange(enclosing, source)); //$NON-NLS-1$
            SourceWindow window = new SourceWindow(enclosing.offset(), enclosing.length());
            List<SourceRange> ranges = new ArrayList<>();
            unit.accept(new ReferenceVisitor(source, entry, window, ranges));
            ranges.sort(Comparator.comparingInt(SourceRange::offset));
            BytecodeSearchDebug.info("computeReferences: matched " + ranges.size() + " references for " //$NON-NLS-1$ //$NON-NLS-2$
                    + BytecodeSearchDebug.describeEntry(entry));
            return List.copyOf(ranges);
        }

        private static SourceRange enclosingSourceRange(IJavaElement element, CompilationUnit unit) {
            SourceRange declarationRange = AstDeclarationWindow.find(element, unit);
            if (declarationRange != null) {
                BytecodeSearchDebug.info("enclosingSourceRange: AST declaration window " //$NON-NLS-1$
                        + BytecodeSearchDebug.describeRange(declarationRange, null));
                return declarationRange;
            }
            ISourceRange sourceRange = sourceRange(element, false);
            if (isValid(sourceRange)) {
                SourceRange jdtRange = new SourceRange(sourceRange.getOffset(), sourceRange.getLength());
                BytecodeSearchDebug.info("enclosingSourceRange: JDT source range " //$NON-NLS-1$
                        + BytecodeSearchDebug.describeRange(jdtRange, null));
                return jdtRange;
            }
            BytecodeSearchDebug.info("enclosingSourceRange: no AST/JDT source range for " //$NON-NLS-1$
                    + BytecodeSearchDebug.describeElement(element));
            return null;
        }
    }

    private static final class AstDeclarationWindow extends ASTVisitor {

        private final IJavaElement element;
        private final TypePath typePath;
        private final List<String> typeStack = new ArrayList<>();
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
                long parameterCount = node.parameters().stream()
                        .filter(VariableDeclaration.class::isInstance)
                        .count();
                return method.getParameterTypes().length == parameterCount;
            } catch (JavaModelException e) {
                Logger.debug(e);
                return false;
            }
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
            return typePath == null || typePath.hasAnonymousSegment() || typePath.matches(typeStack);
        }

        private void popType() {
            if (!typeStack.isEmpty()) {
                typeStack.remove(typeStack.size() - 1);
            }
        }
    }

    private record TypePath(List<String> names, boolean hasAnonymousSegment) {

        private static TypePath from(IJavaElement element) {
            IClassFile classFile = classFile(element);
            if (classFile != null) {
                String classFileName = classFile.getElementName();
                if (classFileName.endsWith(".class")) { //$NON-NLS-1$
                    classFileName = classFileName.substring(0, classFileName.length() - ".class".length()); //$NON-NLS-1$
                }
                List<String> names = Arrays.asList(classFileName.split("\\$")); //$NON-NLS-1$
                return new TypePath(names, names.stream().anyMatch(TypePath::isAnonymousSegment));
            }
            IType type = element instanceof IType directType ? directType
                    : element == null ? null : (IType) element.getAncestor(IJavaElement.TYPE);
            if (type == null) {
                return null;
            }
            String typeName = type.getTypeQualifiedName('.');
            List<String> names = Arrays.asList(typeName.split("\\.")); //$NON-NLS-1$
            return new TypePath(names, names.stream().anyMatch(TypePath::isAnonymousSegment));
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

        private static boolean isAnonymousSegment(String segment) {
            return segment != null && !segment.isEmpty() && segment.chars().allMatch(Character::isDigit);
        }
    }

    private record ReferenceKey(String elementHandle, Kind kind, String name, String qualifiedName,
            String declaringTypeName, String descriptor) {

        private static ReferenceKey from(BytecodeSearchEntry entry) {
            return new ReferenceKey(entry.getElementHandle() == null ? "" : entry.getElementHandle(), entry.getKind(),
                    entry.getName(), entry.getQualifiedName(), entry.getDeclaringTypeName(), entry.getDescriptor());
        }
    }

    private record SourceWindow(int offset, int length) {

        private boolean contains(ASTNode node) {
            return contains(node.getStartPosition(), node.getLength());
        }

        private boolean contains(int nodeOffset, int nodeLength) {
            return nodeOffset >= offset && nodeOffset + Math.max(0, nodeLength) <= offset + length;
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
            String text = source.substring(node.getStartPosition(), node.getStartPosition() + node.getLength());
            int end = text.length();
            while (end > 0 && !Character.isJavaIdentifierPart(text.charAt(end - 1))) {
                end--;
            }
            int start = end;
            while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
                start--;
            }
            if (start < end) {
                return new LastName(node.getStartPosition() + start, end - start, text.substring(start, end));
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
