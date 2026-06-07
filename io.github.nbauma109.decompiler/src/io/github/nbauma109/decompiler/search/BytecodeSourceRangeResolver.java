/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
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
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.util.Logger;

public class BytecodeSourceRangeResolver {

    private static final int MAX_PARSED_CLASS_FILES = 8;

    private final Map<String, ParsedClassFile> classFiles = new LinkedHashMap<>(MAX_PARSED_CLASS_FILES, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ParsedClassFile> eldest) {
            return size() > MAX_PARSED_CLASS_FILES;
        }
    };

    public SourceRange rangeFor(BytecodeSearchEntry entry) {
        return rangeFor(entry, null);
    }

    public SourceRange rangeFor(BytecodeSearchEntry entry, String source) {
        List<SourceRange> ranges = rangesFor(entry, source, null);
        return selectRange(entry, ranges);
    }

    public Map<BytecodeSearchEntry, SourceRange> rangesFor(List<BytecodeSearchEntry> entries, String source) {
        ParsedClassFile parsed = StringUtils.isBlank(source) ? null : parse(source);
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
        return ranges.get(Math.clamp(ordinal, 0, ranges.size() - 1));
    }

    private List<SourceRange> rangesFor(BytecodeSearchEntry entry, String source, ParsedClassFile parsedSource) {
        if (entry.isDeclaration()) {
            SourceRange declarationRange = declarationRange(entry);
            return List.of(declarationRange);
        }

        ParsedClassFile parsed = parsedSource;
        if (parsed == null) {
            parsed = StringUtils.isBlank(source) ? parsedClassFile(entry.getElement()) : parse(source);
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
            if (StringUtils.isBlank(source)) {
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
            return new SourceRange(range.getOffset(), Math.clamp(range.getLength(), 1, Math.max(1, entry.getName().length())));
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

    public record SourceRange(int offset, int length) {
    	public SourceRange {
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
        private final Deque<Integer> anonymousCounterStack = new ArrayDeque<>();
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
            pushNamedType(node, node.getName().getIdentifier());
            if (element instanceof IType type && sameName(type.getElementName(), node.getName())
                    && matchesCurrentType()) {
                range = range(node);
                return false;
            }
            return range == null;
        }

        @Override
        public void endVisit(TypeDeclaration node) {
            anonymousTypeCounter = anonymousCounterStack.pop();
            popType();
        }

        @Override
        public boolean visit(RecordDeclaration node) {
            pushNamedType(node, node.getName().getIdentifier());
            if (element instanceof IType type && sameName(type.getElementName(), node.getName())
                    && matchesCurrentType()) {
                range = range(node);
                return false;
            }
            return range == null;
        }

        @Override
        public void endVisit(RecordDeclaration node) {
            anonymousTypeCounter = anonymousCounterStack.pop();
            popType();
        }

        @Override
        public boolean visit(EnumDeclaration node) {
            pushNamedType(node, node.getName().getIdentifier());
            if (element instanceof IType type && sameName(type.getElementName(), node.getName())
                    && matchesCurrentType()) {
                range = range(node);
                return false;
            }
            return range == null;
        }

        @Override
        public void endVisit(EnumDeclaration node) {
            anonymousTypeCounter = anonymousCounterStack.pop();
            popType();
        }

        @Override
        public boolean visit(AnnotationTypeDeclaration node) {
            pushNamedType(node, node.getName().getIdentifier());
            if (element instanceof IType type && sameName(type.getElementName(), node.getName())
                    && matchesCurrentType()) {
                range = range(node);
                return false;
            }
            return range == null;
        }

        @Override
        public void endVisit(AnnotationTypeDeclaration node) {
            anonymousTypeCounter = anonymousCounterStack.pop();
            popType();
        }

        @Override
        public boolean visit(AnonymousClassDeclaration node) {
            typeStack.add(Integer.toString(++anonymousTypeCounter));
            anonymousCounterStack.push(anonymousTypeCounter);
            anonymousTypeCounter = 0;
            return range == null;
        }

        @Override
        public void endVisit(AnonymousClassDeclaration node) {
            anonymousTypeCounter = anonymousCounterStack.pop();
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
            if (element instanceof IField field && matchesCurrentType() && node.getParent() instanceof FieldDeclaration
                    && sameName(field.getElementName(), node.getName())) {
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
            boolean leftQualified = Strings.CS.contains(left, "."); //$NON-NLS-1$
            boolean rightQualified = Strings.CS.contains(right, "."); //$NON-NLS-1$
            if (leftQualified && rightQualified) {
                return false;
            }
            String leftSimple = leftQualified ? StringUtils.substringAfterLast(left, ".") : left; //$NON-NLS-1$
            String rightSimple = rightQualified ? StringUtils.substringAfterLast(right, ".") : right; //$NON-NLS-1$
            return sameName(leftSimple, rightSimple);
        }

        private static boolean sameName(String name, SimpleName simpleName) {
            return simpleName != null && sameName(name, simpleName.getIdentifier());
        }

        private static boolean sameName(String left, String right) {
            return Strings.CS.equals(left, right);
        }

        private static SourceRange range(ASTNode node) {
            return new SourceRange(node.getStartPosition(), node.getLength());
        }

        private boolean matchesCurrentType() {
            return typePath == null || typePath.matches(typeStack);
        }

        private void pushNamedType(ASTNode node, String name) {
            String typeName = isLocalTypeDeclaration(node) ? Integer.toString(++anonymousTypeCounter) + name : name;
            anonymousCounterStack.push(anonymousTypeCounter);
            anonymousTypeCounter = 0;
            typeStack.add(typeName);
        }

        private static boolean isLocalTypeDeclaration(ASTNode node) {
            ASTNode parent = node.getParent();
            return parent != null && parent.getNodeType() == ASTNode.TYPE_DECLARATION_STATEMENT;
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
                if (Strings.CS.endsWith(classFileName, ".class")) { //$NON-NLS-1$
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
                if (!Strings.CS.equals(names.get(i), currentTypeStack.get(offset + i))) {
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
        private final Deque<Set<String>> localNameScopes = new ArrayDeque<>();

        private ReferenceVisitor(String source, BytecodeSearchEntry entry, SourceWindow window,
                List<SourceRange> ranges) {
            this.source = source;
            this.entry = entry;
            this.window = window;
            this.ranges = ranges;
        }

        @Override
        public boolean visit(MethodDeclaration node) {
            localNameScopes.push(new HashSet<>());
            for (Object parameter : node.parameters()) {
                if (parameter instanceof SingleVariableDeclaration variable) {
                    localNameScopes.peek().add(variable.getName().getIdentifier());
                }
            }
            return true;
        }

        @Override
        public void endVisit(MethodDeclaration node) {
            localNameScopes.pop();
        }

        @Override
        public boolean visit(Block node) {
            localNameScopes.push(new HashSet<>());
            return true;
        }

        @Override
        public void endVisit(Block node) {
            localNameScopes.pop();
        }

        @Override
        public boolean visit(ForStatement node) {
            localNameScopes.push(new HashSet<>());
            return true;
        }

        @Override
        public void endVisit(ForStatement node) {
            localNameScopes.pop();
        }

        @Override
        public boolean visit(EnhancedForStatement node) {
            localNameScopes.push(new HashSet<>());
            return true;
        }

        @Override
        public void endVisit(EnhancedForStatement node) {
            localNameScopes.pop();
        }

        @Override
        public boolean visit(CatchClause node) {
            localNameScopes.push(new HashSet<>());
            return true;
        }

        @Override
        public void endVisit(CatchClause node) {
            localNameScopes.pop();
        }

        @Override
        public boolean visit(SingleVariableDeclaration node) {
            addLocalName(node.getName());
            return true;
        }

        @Override
        public boolean visit(VariableDeclarationFragment node) {
            if (!(node.getParent() instanceof FieldDeclaration)) {
                addLocalName(node.getName());
            }
            return true;
        }

        @Override
        public boolean visit(SimpleName node) {
            if (matchesSimpleNameReference(node)) {
                add(node);
            }
            return true;
        }

        private boolean matchesSimpleNameReference(SimpleName node) {
            return entry.getKind() == Kind.TYPE && matches(node) && isTypePositionName(node) && matchesQualifiedContext(node)
                    || entry.getKind() == Kind.FIELD && matchesFieldAccess(node);
        }

        private boolean matchesQualifiedContext(SimpleName node) {
            if (!(node.getParent() instanceof QualifiedName qualifiedName) || qualifiedName.getName() != node) {
                return true;
            }
            String qualifiedTypeName = entry.getQualifiedName();
            if (StringUtils.isBlank(qualifiedTypeName)) {
                return true;
            }
            return sameName(qualifiedName.getFullyQualifiedName(), qualifiedTypeName);
        }

        private static boolean isTypePositionName(SimpleName node) {
            ASTNode current = node;
            ASTNode parent = node.getParent();
            while (parent instanceof Name outerName) {
                // Accept type-like qualifier at any depth: Foo in Foo.CONST, or System in java.lang.System.out
                if (parent instanceof QualifiedName qn && qn.getQualifier() == current
                        && current instanceof Name currentName && isTypeLikeQualifier(currentName)) {
                    return true;
                }
                current = outerName;
                parent = outerName.getParent();
            }
            if (parent instanceof Type || parent instanceof Annotation) {
                return true;
            }
            // Also accept type-like names used as static-member qualifiers
            if (current instanceof Name name && isTypeLikeQualifier(name)) {
                if (parent instanceof MethodInvocation mi && mi.getExpression() == name) {
                    return true;
                }
                if (parent instanceof ExpressionMethodReference emr && emr.getExpression() == name) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean visit(QualifiedName node) {
            if (entry.getKind() == Kind.PACKAGE) {
                addPackageReference(node);
            }
            return true;
        }

        private void addPackageReference(QualifiedName node) {
            String packageName = entry.getName();
            if (sameName(node.getFullyQualifiedName(), packageName)) {
                add(node);
                return;
            }
            Name qualifier = node.getQualifier();
            if (!(qualifier instanceof QualifiedName) && sameName(qualifier.getFullyQualifiedName(), packageName)) {
                add(qualifier);
            }
        }

        @Override
        public boolean visit(MethodInvocation node) {
            if (entry.getKind() == Kind.METHOD && matchesMethodInvocation(node)) {
                addFromNameThroughNode(node.getName(), node);
            }
            return true;
        }

        @Override
        public boolean visit(SuperMethodInvocation node) {
            if (entry.getKind() == Kind.METHOD && targetsDirectSuperclass()
                    && matches(node.getName(), node.arguments().size())) {
                addFromNameThroughNode(node.getName(), node);
            }
            return true;
        }

        @Override
        public boolean visit(ExpressionMethodReference node) {
            if (entry.getKind() == Kind.METHOD && matchesExpressionMethodReference(node)) {
                add(node.getName());
            }
            return true;
        }

        @Override
        public boolean visit(TypeMethodReference node) {
            if (entry.getKind() == Kind.METHOD && matchesTypeMethodReference(node)) {
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
            if (entry.getKind() == Kind.CONSTRUCTOR && node.getType() != null && matchesLastName(node.getType())
                    && matchesArgumentCount(node.arguments().size())) {
                addLastName(node.getType());
            }
            return true;
        }

        @Override
        public boolean visit(CreationReference node) {
            if (entry.getKind() == Kind.CONSTRUCTOR && node.getType() != null && matchesLastName(node.getType())
                    && matchesCreationReference(node)) {
                addLastName(node.getType());
            }
            return true;
        }

        @Override
        public boolean visit(ConstructorInvocation node) {
            if (entry.getKind() == Kind.CONSTRUCTOR && targetsEnclosingType() && matchesArgumentCount(node.arguments().size())) {
                addKeyword(node, "this"); //$NON-NLS-1$
            }
            return true;
        }

        @Override
        public boolean visit(SuperConstructorInvocation node) {
            if (entry.getKind() == Kind.CONSTRUCTOR && targetsDirectSuperclass()
                    && matchesArgumentCount(node.arguments().size())) {
                addKeyword(node, "super"); //$NON-NLS-1$
            }
            return true;
        }

        private boolean matches(SimpleName node) {
            return sameName(node.getIdentifier(), entry.getName()) || sameName(node.getIdentifier(), simpleName());
        }

        private boolean matchesFieldAccess(SimpleName node) {
            if (!matches(node) || isDeclarationName(node)) {
                return false;
            }
            ASTNode parent = node.getParent();
            if (parent instanceof QualifiedName qualifiedName && qualifiedName.getName() == node) {
                return matchesDeclaringOwner(qualifiedName.getQualifier().getFullyQualifiedName());
            }
            if (parent instanceof FieldAccess fieldAccess && fieldAccess.getName() == node) {
                if (fieldAccess.getExpression() instanceof ThisExpression) {
                    return matchesImplicitReceiverField();
                }
                // Resolving an arbitrary receiver expression requires bindings, which are not
                // available for decompiled text. Do not assign it to an arbitrary same-named field.
                return false;
            }
            if (parent instanceof SuperFieldAccess superFieldAccess && superFieldAccess.getName() == node) {
                return matchesSuperReceiverField();
            }
            if (isLocalName(node)) {
                return false;
            }
            return matchesImplicitReceiverField();
        }

        private void addLocalName(SimpleName node) {
            if (!localNameScopes.isEmpty()) {
                localNameScopes.peek().add(node.getIdentifier());
            }
        }

        private boolean isLocalName(SimpleName node) {
            String identifier = node.getIdentifier();
            for (Set<String> scope : localNameScopes) {
                if (scope.contains(identifier)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesImplicitReceiverField() {
            String enclosingTypeName = enclosingTypeName(entry.getElement());
            if (declaresFieldInEnclosingType()) {
                return matchesDeclaringOwner(enclosingTypeName);
            }
            // A bare or this-qualified field may be inherited when the enclosing type does not
            // declare a same-named field, so its declaring owner cannot be narrowed further.
            return true;
        }

        private boolean matchesSuperReceiverField() {
            String enclosingTypeName = enclosingTypeName(entry.getElement());
            return enclosingTypeName == null || !matchesDeclaringOwner(enclosingTypeName);
        }

        private boolean declaresFieldInEnclosingType() {
            IJavaElement element = entry.getElement();
            IJavaElement ancestor = element == null ? null : element.getAncestor(IJavaElement.TYPE);
            return ancestor instanceof IType type && type.getField(entry.getName()).exists();
        }

        private boolean matchesMethodInvocation(MethodInvocation node) {
            if (!matches(node.getName(), node.arguments().size())) {
                return false;
            }
            if (node.getExpression() instanceof Name receiver && isTypeLikeQualifier(receiver)) {
                return matchesDeclaringOwner(receiver.getFullyQualifiedName());
            }
            if (node.getExpression() instanceof CastExpression cast) {
                return matchesDeclaringOwner(sourceName(rawConstructorType(cast.getType())));
            }
            // Receiver expression types are unavailable because decompiled text is parsed
            // without bindings; use literal argument types as a conservative discriminator.
            return matchesArgumentTypeSyntax(node.arguments());
        }

        private boolean matchesArgumentTypeSyntax(List<?> arguments) {
            String descriptor = entry.getDescriptor();
            if (StringUtils.isBlank(descriptor)) {
                return true;
            }
            try {
                org.objectweb.asm.Type[] argTypes = org.objectweb.asm.Type.getArgumentTypes(descriptor);
                if (argTypes.length != arguments.size()) {
                    return true;
                }
                for (int i = 0; i < argTypes.length; i++) {
                    if (!compatibleArgumentSyntax(arguments.get(i), argTypes[i])) {
                        return false;
                    }
                }
                return true;
            } catch (IllegalArgumentException e) {
                return true;
            }
        }

        private static boolean compatibleArgumentSyntax(Object arg, org.objectweb.asm.Type expectedType) {
            if (arg instanceof StringLiteral) {
                return expectedType.getSort() == org.objectweb.asm.Type.OBJECT
                        && "java/lang/String".equals(expectedType.getInternalName()); //$NON-NLS-1$
            }
            if (arg instanceof BooleanLiteral) {
                return expectedType.getSort() == org.objectweb.asm.Type.BOOLEAN;
            }
            if (arg instanceof NumberLiteral nl) {
                String token = nl.getToken();
                if (token.endsWith("L") || token.endsWith("l")) { //$NON-NLS-1$ //$NON-NLS-2$
                    return expectedType.getSort() == org.objectweb.asm.Type.LONG;
                }
                if (token.endsWith("F") || token.endsWith("f")) { //$NON-NLS-1$ //$NON-NLS-2$
                    return expectedType.getSort() == org.objectweb.asm.Type.FLOAT;
                }
                if (token.endsWith("D") || token.endsWith("d")) { //$NON-NLS-1$ //$NON-NLS-2$
                    return expectedType.getSort() == org.objectweb.asm.Type.DOUBLE;
                }
            }
            return true;
        }

        private boolean matchesExpressionMethodReference(ExpressionMethodReference node) {
            if (!matches(node.getName())) {
                return false;
            }
            if (node.getExpression() instanceof Name receiver && isTypeLikeQualifier(receiver)) {
                return matchesDeclaringOwner(receiver.getFullyQualifiedName());
            }
            return true;
        }

        private boolean matchesTypeMethodReference(TypeMethodReference node) {
            return matches(node.getName()) && matchesDeclaringOwner(sourceName(rawConstructorType(node.getType())));
        }

        private static boolean isTypeLikeQualifier(Name receiver) {
            String receiverName = simpleName(receiver.getFullyQualifiedName());
            return !receiverName.isEmpty() && Character.isUpperCase(receiverName.charAt(0));
        }

        private boolean matchesDeclaringOwner(String ownerName) {
            String declaringTypeName = entry.getDeclaringTypeName();
            if (declaringTypeName == null || ownerName == null) {
                return true;
            }
            String normalizedDeclaringType = StringUtils.replaceChars(declaringTypeName, '$', '.');
            String normalizedOwner = StringUtils.replaceChars(ownerName, '$', '.');
            if (sameName(normalizedDeclaringType, normalizedOwner)) {
                return true;
            }
            if (Strings.CS.contains(normalizedOwner, ".")) { //$NON-NLS-1$
                return false;
            }
            return sameName(simpleName(normalizedDeclaringType), simpleName(normalizedOwner));
        }

        private boolean matches(SimpleName node, int argumentCount) {
            return matches(node) && matchesArgumentCount(argumentCount);
        }

        private boolean matchesCreationReference(CreationReference node) {
            Integer argumentCount = creationReferenceArgumentCount(node);
            return argumentCount == null || matchesArgumentCount(argumentCount.intValue());
        }

        private Integer creationReferenceArgumentCount(CreationReference node) {
            Type targetType = creationReferenceTargetType(node);
            return targetType == null ? null : functionalInterfaceArgumentCount(targetType);
        }

        private Type creationReferenceTargetType(CreationReference node) {
            ASTNode parent = node.getParent();
            if (parent instanceof VariableDeclarationFragment fragment) {
                ASTNode declaration = fragment.getParent();
                if (declaration instanceof VariableDeclarationStatement statement) {
                    return statement.getType();
                }
                if (declaration instanceof VariableDeclarationExpression expression) {
                    return expression.getType();
                }
                if (declaration instanceof FieldDeclaration field) {
                    return field.getType();
                }
            }
            if (parent instanceof CastExpression cast) {
                return cast.getType();
            }
            return null;
        }

        private static Integer functionalInterfaceArgumentCount(Type targetType) {
            String name = rawTypeName(targetType);
            String simpleName = simpleName(name);
            return switch (simpleName) {
            case "Callable", "Supplier" -> 0; //$NON-NLS-1$ //$NON-NLS-2$
            case "Function", "IntFunction", "LongFunction", "DoubleFunction", "UnaryOperator" -> 1; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            case "BiFunction", "BinaryOperator" -> 2; //$NON-NLS-1$ //$NON-NLS-2$
            default -> null;
            };
        }

        private static String rawTypeName(Type targetType) {
            if (targetType instanceof ParameterizedType parameterizedType) {
                return parameterizedType.getType().toString();
            }
            return targetType.toString();
        }

        private boolean matchesArgumentCount(int argumentCount) {
            String descriptor = entry.getDescriptor();
            if (StringUtils.isBlank(descriptor)) {
                return true;
            }
            try {
                org.objectweb.asm.Type[] argTypes = org.objectweb.asm.Type.getArgumentTypes(descriptor);
                if (argumentCount == argTypes.length) {
                    return true;
                }
                // Non-static member, local, and anonymous class constructors may carry one
                // synthetic parameter that has no counterpart in source.
                if (entry.getKind() == Kind.CONSTRUCTOR && mayHaveSyntheticConstructorParameter()
                        && argumentCount == argTypes.length - 1) {
                    return true;
                }
                return isVarargsTarget(argTypes)
                        && argumentCount >= argTypes.length - 1;
            } catch (IllegalArgumentException e) {
                return true;
            }
        }

        private boolean mayHaveSyntheticConstructorParameter() {
            IJavaElement element = entry.getElement();
            String declaringTypeName = entry.getDeclaringTypeName();
            if (element == null || element.getJavaProject() == null || StringUtils.isBlank(declaringTypeName)) {
                return false;
            }
            try {
                IType type = findType(element, declaringTypeName);
                return type != null
                        && (type.getDeclaringType() != null && !Flags.isStatic(type.getFlags())
                                || type.isLocal() || type.isAnonymous());
            } catch (JavaModelException e) {
                Logger.debug(e);
                return false;
            }
        }

        private static IType findType(IJavaElement element, String owner) throws JavaModelException {
            IType type = element.getJavaProject().findType(owner);
            if (type != null) {
                return type;
            }
            StringBuilder binaryName = new StringBuilder(owner);
            for (int separator = binaryName.lastIndexOf("."); separator >= 0; //$NON-NLS-1$
                    separator = binaryName.lastIndexOf(".", separator - 1)) { //$NON-NLS-1$
                binaryName.setCharAt(separator, '$');
                type = element.getJavaProject().findType(binaryName.toString());
                if (type != null) {
                    return type;
                }
            }
            return null;
        }

        private boolean isVarargsTarget(org.objectweb.asm.Type[] argumentTypes) {
            if (argumentTypes.length == 0
                    || argumentTypes[argumentTypes.length - 1].getSort() != org.objectweb.asm.Type.ARRAY) {
                return false;
            }
            IJavaElement element = entry.getElement();
            IJavaProject project = element == null ? null : element.getJavaProject();
            String declaringTypeName = entry.getDeclaringTypeName();
            if (project == null || StringUtils.isBlank(declaringTypeName)) {
                return false;
            }
            try {
                IType declaringType = project.findType(declaringTypeName);
                if (declaringType == null) {
                    return false;
                }
                for (IMethod method : declaringType.getMethods()) {
                    if (matchesTargetMethod(method, argumentTypes) && Flags.isVarargs(method.getFlags())) {
                        return true;
                    }
                }
            } catch (JavaModelException e) {
                Logger.debug(e);
            }
            return false;
        }

        private boolean matchesTargetMethod(IMethod method, org.objectweb.asm.Type[] argumentTypes)
                throws JavaModelException {
            if (method.isConstructor() != (entry.getKind() == Kind.CONSTRUCTOR)
                    || !method.isConstructor() && !sameName(method.getElementName(), entry.getName())) {
                return false;
            }
            String[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != argumentTypes.length) {
                return false;
            }
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!AstDeclarationWindow.sameType(AstDeclarationWindow.normalizeJdtType(parameterTypes[i]),
                        normalizeAsmType(argumentTypes[i]))) {
                    return false;
                }
            }
            return true;
        }

        private static String normalizeAsmType(org.objectweb.asm.Type type) {
            int arrayDepth = type.getDimensions();
            org.objectweb.asm.Type elementType = type.getElementType();
            return elementType.getClassName().toLowerCase(java.util.Locale.ROOT) + "[]".repeat(arrayDepth); //$NON-NLS-1$
        }

        private boolean targetsEnclosingType() {
            String declaringTypeName = entry.getDeclaringTypeName();
            if (declaringTypeName == null) {
                return true;
            }
            String enclosingTypeName = enclosingTypeName(entry.getElement());
            return enclosingTypeName == null || sameName(declaringTypeName, enclosingTypeName)
                    || sameName(simpleName(declaringTypeName), simpleName(enclosingTypeName));
        }

        private boolean targetsDirectSuperclass() {
            IJavaElement element = entry.getElement();
            IJavaElement ancestor = element == null ? null : element.getAncestor(IJavaElement.TYPE);
            if (!(ancestor instanceof IType type)) {
                return true;
            }
            try {
                String superclassName = type.getSuperclassName();
                if (superclassName == null) {
                    return true;
                }
                int typeArgumentsStart = superclassName.indexOf('<');
                String rawSuperclassName = typeArgumentsStart < 0
                        ? superclassName : superclassName.substring(0, typeArgumentsStart);
                return matchesDeclaringOwner(rawSuperclassName);
            } catch (JavaModelException e) {
                Logger.debug(e);
                return true;
            }
        }

        private static String enclosingTypeName(IJavaElement element) {
            IJavaElement ancestor = element == null ? null : element.getAncestor(IJavaElement.TYPE);
            if (ancestor instanceof IType type) {
                return type.getFullyQualifiedName('.').replace('$', '.');
            }
            return null;
        }

        private String simpleName() {
            return simpleName(entry.getQualifiedName());
        }

        private static String simpleName(String qualifiedName) {
            if (!Strings.CS.contains(qualifiedName, ".")) { //$NON-NLS-1$
                return qualifiedName;
            }
            return StringUtils.substringAfterLast(qualifiedName, "."); //$NON-NLS-1$
        }

        private boolean sameName(String left, String right) {
            return Strings.CS.equals(left, right);
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
            ASTNode rawType = rawConstructorType(node);
            if (!window.contains(rawType)) {
                return;
            }
            LastName lastName = lastName(rawType);
            if (lastName != null) {
                ranges.add(new SourceRange(lastName.offset(), lastName.length()));
            }
        }

        private boolean matchesLastName(ASTNode node) {
            ASTNode rawType = rawConstructorType(node);
            LastName lastName = lastName(rawType);
            return lastName != null && sameName(lastName.name(), entry.getName()) && matchesConstructorType(rawType);
        }

        private static ASTNode rawConstructorType(ASTNode node) {
            while (node instanceof ParameterizedType parameterizedType) {
                node = parameterizedType.getType();
            }
            return node;
        }

        private boolean matchesConstructorType(ASTNode node) {
            String declaringTypeName = entry.getDeclaringTypeName();
            if (declaringTypeName == null) {
                return true;
            }
            String sourceTypeName = sourceName(node);
            if (sourceTypeName == null) {
                return true;
            }
            String normalizedDeclaringType = StringUtils.replaceChars(declaringTypeName, '$', '.');
            String normalizedSourceType = StringUtils.replaceChars(sourceTypeName, '$', '.');
            if (sameName(normalizedDeclaringType, normalizedSourceType)
                    || Strings.CS.endsWith(normalizedDeclaringType, "." + normalizedSourceType)) { //$NON-NLS-1$
                return true;
            }
            return !Strings.CS.contains(normalizedSourceType, ".") //$NON-NLS-1$
                    && sameName(simpleName(normalizedDeclaringType), normalizedSourceType);
        }

        private String sourceName(ASTNode node) {
            int nodeOffset = node.getStartPosition();
            int nodeLength = node.getLength();
            if (nodeOffset < 0 || nodeLength <= 0 || nodeOffset + nodeLength > source.length()) {
                return null;
            }
            return source.substring(nodeOffset, nodeOffset + nodeLength).replaceAll("\\s+", ""); //$NON-NLS-1$ //$NON-NLS-2$
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
            int end = Math.clamp(start + (long) node.getLength(), 0, source.length());
            int offset = source.indexOf(keyword, start);
            if (offset >= start && offset < end) {
                ranges.add(new SourceRange(offset, keyword.length()));
            }
        }

        private boolean isDeclarationName(SimpleName node) {
            ASTNode parent = node.getParent();
            return isTypeDeclarationName(parent, node)
                    || isExecutableDeclarationName(parent, node)
                    || isVariableDeclarationName(parent, node)
                    || isEnumDeclarationName(parent, node)
                    || isAnnotationDeclarationName(parent, node);
        }

        private boolean isTypeDeclarationName(ASTNode parent, SimpleName node) {
            return parent instanceof TypeDeclaration type && type.getName() == node
                    || parent instanceof RecordDeclaration r && r.getName() == node;
        }

        private boolean isExecutableDeclarationName(ASTNode parent, SimpleName node) {
            return parent instanceof MethodDeclaration method && method.getName() == node;
        }

        private boolean isVariableDeclarationName(ASTNode parent, SimpleName node) {
            return parent instanceof VariableDeclarationFragment v && v.getName() == node
                    || parent instanceof SingleVariableDeclaration s && s.getName() == node;
        }

        private boolean isEnumDeclarationName(ASTNode parent, SimpleName node) {
            return parent instanceof EnumDeclaration enumDeclaration && enumDeclaration.getName() == node
                    || parent instanceof EnumConstantDeclaration enumConstant && enumConstant.getName() == node;
        }

        private boolean isAnnotationDeclarationName(ASTNode parent, SimpleName node) {
            return parent instanceof AnnotationTypeDeclaration annotation && annotation.getName() == node
                    || parent instanceof AnnotationTypeMemberDeclaration member && member.getName() == node;
        }
    }

    private record LastName(int offset, int length, String name) {
    }
}
