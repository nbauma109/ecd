/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.util.Locale;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.ui.search.ElementQuerySpecification;
import org.eclipse.jdt.ui.search.IMatchPresentation;
import org.eclipse.jdt.ui.search.IQueryParticipant;
import org.eclipse.jdt.ui.search.ISearchRequestor;
import org.eclipse.jdt.ui.search.PatternQuerySpecification;
import org.eclipse.jdt.ui.search.QuerySpecification;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Access;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.util.Logger;

public class ApplicationLibrarySearchParticipant implements IQueryParticipant {

    @Override
    public void search(ISearchRequestor requestor, QuerySpecification querySpecification, IProgressMonitor monitor)
            throws CoreException {
        BytecodeSearchIndex.getDefault().start();
        SearchMatcher matcher = SearchMatcher.create(querySpecification);
        if (matcher == null) {
            return;
        }
        BytecodeSearchIndex.getDefault().forEachEntry(matcher.kind(), matcher.name(), matcher.qualifiedName(),
                matcher.isWildcard(), monitor, entry -> {
            if (monitor != null && monitor.isCanceled()) {
                throw new OperationCanceledException();
            }
            IJavaElement element = entry.getElement();
            if (element != null && querySpecification.getScope().encloses(element) && matcher.matches(entry)) {
                BytecodeSearchMatch match = new BytecodeSearchMatch(entry);
                requestor.reportMatch(match);
            }
        });
    }

    @Override
    public int estimateTicks(QuerySpecification specification) {
        int entryCount = BytecodeSearchIndex.getDefault().entryCount();
        return entryCount == 0 ? 50 : Math.min(1000, Math.max(100, entryCount / 200));
    }

    @Override
    public IMatchPresentation getUIParticipant() {
        return new ApplicationLibrarySearchMatchPresentation();
    }

    private static final class SearchMatcher {

        private final int limitTo;
        private final Kind kind;
        private final String name;
        private final String qualifiedName;
        private final String declaringTypeName;
        private final String descriptor;
        private final String[] parameterTypes;
        private final boolean caseSensitive;
        private final Pattern wildcardPattern;

        private SearchMatcher(int limitTo, Kind kind, SearchPattern searchPattern) {
            this.limitTo = limitTo;
            this.kind = kind;
            this.name = searchPattern.name();
            this.qualifiedName = searchPattern.qualifiedName();
            this.declaringTypeName = searchPattern.declaringTypeName();
            this.descriptor = searchPattern.descriptor();
            this.parameterTypes = searchPattern.parameterTypes();
            this.caseSensitive = searchPattern.caseSensitive();
            this.wildcardPattern = searchPattern.wildcardPattern();
        }

        static SearchMatcher create(QuerySpecification specification) {
            if (!supportsLimitTo(specification.getLimitTo())) {
                return null;
            }
            if (specification instanceof PatternQuerySpecification patternSpecification) {
                Kind kind = kindFor(patternSpecification.getSearchFor());
                if (kind == null) {
                    return null;
                }
                return new SearchMatcher(specification.getLimitTo(), kind,
                        parsePattern(kind, patternSpecification.getPattern(), patternSpecification.isCaseSensitive()));
            }
            if (specification instanceof ElementQuerySpecification elementSpecification) {
                return forElement(specification.getLimitTo(), elementSpecification.getElement());
            }
            return null;
        }

        private static SearchMatcher forElement(int limitTo, IJavaElement element) {
            if (element instanceof IType type) {
                return new SearchMatcher(limitTo, Kind.TYPE,
                        new SearchPattern(type.getElementName(), normalizeTypeName(type), null, null, null, true, null));
            }
            if (element instanceof IField field) {
                return new SearchMatcher(limitTo, Kind.FIELD,
                        new SearchPattern(field.getElementName(), field.getElementName(), normalizeDeclaringType(field),
                                null, null, true, null));
            }
            if (element instanceof IMethod method) {
                try {
                    boolean constructor = method.isConstructor();
                    return new SearchMatcher(limitTo, constructor ? Kind.CONSTRUCTOR : Kind.METHOD,
                            new SearchPattern(constructor ? declaringSimpleName(method) : method.getElementName(),
                                    method.getElementName(), normalizeDeclaringType(method), method.getSignature(), null,
                                    true, null));
                } catch (JavaModelException e) {
                    Logger.debug(e);
                    return null;
                }
            }
            if (element instanceof IPackageFragment pkg) {
                return new SearchMatcher(limitTo, Kind.PACKAGE,
                        new SearchPattern(pkg.getElementName(), pkg.getElementName(), null, null, null, true, null));
            }
            if (element instanceof IModuleDescription module) {
                return new SearchMatcher(limitTo, Kind.MODULE,
                        new SearchPattern(module.getElementName(), module.getElementName(), null, null, null, true, null));
            }
            return null;
        }

        private record SearchPattern(String name, String qualifiedName, String declaringTypeName, String descriptor,
                String[] parameterTypes, boolean caseSensitive, Pattern wildcardPattern) {
        }

        boolean matches(BytecodeSearchEntry entry) {
            if (kind != entry.getKind() || !matchesLimit(entry)) {
                return false;
            }
            if (declaringTypeName != null && !sameName(declaringTypeName, entry.getDeclaringTypeName())) {
                return false;
            }
            if (descriptor != null && entry.getDescriptor() != null && !sameDescriptor(descriptor, entry.getDescriptor())) {
                return false;
            }
            if (parameterTypes != null && !sameParameterTypes(parameterTypes, entry.getDescriptor())) {
                return false;
            }
            if (wildcardPattern != null) {
                return wildcardPattern.matcher(entry.getName()).matches()
                        || wildcardPattern.matcher(entry.getQualifiedName()).matches();
            }
            return sameName(name, entry.getName()) || sameName(qualifiedName, entry.getQualifiedName());
        }

        Kind kind() {
            return kind;
        }

        String name() {
            return name;
        }

        String qualifiedName() {
            return qualifiedName;
        }

        boolean isWildcard() {
            return wildcardPattern != null;
        }

        private boolean matchesLimit(BytecodeSearchEntry entry) {
            int maskedLimit = limitTo & ~(IJavaSearchConstants.IGNORE_DECLARING_TYPE
                    | IJavaSearchConstants.IGNORE_RETURN_TYPE);
            if (maskedLimit == IJavaSearchConstants.ALL_OCCURRENCES) {
                return !entry.isDeclaration();
            }
            if (maskedLimit == IJavaSearchConstants.DECLARATIONS) {
                return false;
            }
            if (maskedLimit == IJavaSearchConstants.REFERENCES) {
                return !entry.isDeclaration();
            }
            if (maskedLimit == IJavaSearchConstants.READ_ACCESSES) {
                return !entry.isDeclaration() && entry.getKind() == Kind.FIELD && entry.getAccess() == Access.READ;
            }
            if (maskedLimit == IJavaSearchConstants.WRITE_ACCESSES) {
                return !entry.isDeclaration() && entry.getKind() == Kind.FIELD && entry.getAccess() == Access.WRITE;
            }
            return false;
        }

        private boolean sameName(String left, String right) {
            if (left == null || right == null) {
                return false;
            }
            if (caseSensitive) {
                return left.equals(right);
            }
            return left.equalsIgnoreCase(right);
        }

        private boolean sameDescriptor(String jdtSignature, String bytecodeDescriptor) {
            String normalizedJdt = normalizeJdtMethodSignature(jdtSignature);
            String normalizedBytecode = normalizeBytecodeMethodDescriptor(bytecodeDescriptor);
            return normalizedJdt.equals(normalizedBytecode);
        }

        private boolean sameParameterTypes(String[] expectedTypes, String bytecodeDescriptor) {
            if (bytecodeDescriptor == null) {
                return false;
            }
            try {
                org.objectweb.asm.Type[] argumentTypes = org.objectweb.asm.Type.getArgumentTypes(bytecodeDescriptor);
                if (argumentTypes.length != expectedTypes.length) {
                    return false;
                }
                for (int i = 0; i < argumentTypes.length; i++) {
                    if (!sameType(expectedTypes[i], normalizeAsmType(argumentTypes[i]))) {
                        return false;
                    }
                }
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        private boolean sameType(String expectedType, String actualType) {
            if (sameName(expectedType, actualType)) {
                return true;
            }
            int separator = actualType.lastIndexOf('.');
            return separator >= 0 && sameName(expectedType, actualType.substring(separator + 1));
        }

        private static boolean supportsLimitTo(int limitTo) {
            int maskedLimit = limitTo & ~(IJavaSearchConstants.IGNORE_DECLARING_TYPE
                    | IJavaSearchConstants.IGNORE_RETURN_TYPE);
            return maskedLimit == IJavaSearchConstants.ALL_OCCURRENCES
                    || maskedLimit == IJavaSearchConstants.REFERENCES
                    || maskedLimit == IJavaSearchConstants.READ_ACCESSES
                    || maskedLimit == IJavaSearchConstants.WRITE_ACCESSES;
        }

        private static SearchPattern parsePattern(Kind kind, String pattern, boolean caseSensitive) {
            String text = pattern == null ? "" : pattern.trim(); //$NON-NLS-1$
            String memberPattern = text;
            String[] parameterTypes = null;
            if ((kind == Kind.METHOD || kind == Kind.CONSTRUCTOR) && text.indexOf('(') >= 0) {
                int openParen = text.indexOf('(');
                int closeParen = text.lastIndexOf(')');
                memberPattern = text.substring(0, openParen).trim();
                if (closeParen > openParen) {
                    parameterTypes = parseParameterTypes(text.substring(openParen + 1, closeParen));
                }
            }

            String name = memberPattern;
            String qualifiedName = memberPattern;
            String declaringTypeName = null;
            if (kind == Kind.METHOD || kind == Kind.FIELD) {
                int separator = memberPattern.lastIndexOf('.');
                if (separator >= 0) {
                    declaringTypeName = memberPattern.substring(0, separator);
                    name = memberPattern.substring(separator + 1);
                    qualifiedName = name;
                }
            } else if (kind == Kind.CONSTRUCTOR) {
                declaringTypeName = memberPattern;
                name = simpleName(memberPattern);
                qualifiedName = memberPattern;
            } else if (kind == Kind.TYPE) {
                name = simpleName(memberPattern);
            }

            return new SearchPattern(name, qualifiedName, emptyToNull(declaringTypeName), null, parameterTypes,
                    caseSensitive, wildcardPattern(name, caseSensitive));
        }

        private static String[] parseParameterTypes(String parameters) {
            String trimmed = parameters.trim();
            if (trimmed.isEmpty()) {
                return new String[0];
            }
            if ("*".equals(trimmed)) { //$NON-NLS-1$
                return null;
            }
            return splitParameterTypes(trimmed);
        }

        private static String[] splitParameterTypes(String parameters) {
            java.util.List<String> types = new java.util.ArrayList<>();
            int depth = 0;
            int start = 0;
            for (int i = 0; i < parameters.length(); i++) {
                char ch = parameters.charAt(i);
                if (ch == '<') {
                    depth++;
                } else if (ch == '>') {
                    depth = Math.max(0, depth - 1);
                } else if (ch == ',' && depth == 0) {
                    types.add(normalizePatternType(parameters.substring(start, i)));
                    start = i + 1;
                }
            }
            types.add(normalizePatternType(parameters.substring(start)));
            return types.toArray(String[]::new);
        }

        private static String normalizePatternType(String type) {
            String normalized = type.trim().replace("...", "[]"); //$NON-NLS-1$ //$NON-NLS-2$
            int genericStart = normalized.indexOf('<');
            if (genericStart >= 0) {
                normalized = normalized.substring(0, genericStart);
            }
            normalized = normalized.replace("[]", " array ").replaceAll("\\s+", "") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .replace("array", "[]").toLowerCase(Locale.ROOT); //$NON-NLS-1$ //$NON-NLS-2$
            return primitiveDescriptor(normalized);
        }

        private static String primitiveDescriptor(String type) {
            int arrayStart = type.indexOf("[]"); //$NON-NLS-1$
            String baseType = arrayStart < 0 ? type : type.substring(0, arrayStart);
            String suffix = arrayStart < 0 ? "" : type.substring(arrayStart); //$NON-NLS-1$
            return switch (baseType) {
            case "boolean" -> "z"; //$NON-NLS-1$ //$NON-NLS-2$
            case "byte" -> "b"; //$NON-NLS-1$ //$NON-NLS-2$
            case "char" -> "c"; //$NON-NLS-1$ //$NON-NLS-2$
            case "double" -> "d"; //$NON-NLS-1$ //$NON-NLS-2$
            case "float" -> "f"; //$NON-NLS-1$ //$NON-NLS-2$
            case "int" -> "i"; //$NON-NLS-1$ //$NON-NLS-2$
            case "long" -> "j"; //$NON-NLS-1$ //$NON-NLS-2$
            case "short" -> "s"; //$NON-NLS-1$ //$NON-NLS-2$
            default -> baseType;
            } + suffix;
        }

        private static String simpleName(String name) {
            int separator = name.lastIndexOf('.');
            return separator < 0 ? name : name.substring(separator + 1);
        }

        private static String emptyToNull(String value) {
            return value == null || value.isBlank() ? null : value;
        }

        private static Kind kindFor(int searchFor) {
            return switch (searchFor) {
            case IJavaSearchConstants.TYPE, IJavaSearchConstants.CLASS, IJavaSearchConstants.INTERFACE,
                    IJavaSearchConstants.ENUM, IJavaSearchConstants.ANNOTATION_TYPE,
                    IJavaSearchConstants.CLASS_AND_ENUM, IJavaSearchConstants.CLASS_AND_INTERFACE,
                    IJavaSearchConstants.INTERFACE_AND_ANNOTATION -> Kind.TYPE;
            case IJavaSearchConstants.METHOD -> Kind.METHOD;
            case IJavaSearchConstants.CONSTRUCTOR -> Kind.CONSTRUCTOR;
            case IJavaSearchConstants.FIELD -> Kind.FIELD;
            case IJavaSearchConstants.PACKAGE -> Kind.PACKAGE;
            case IJavaSearchConstants.MODULE -> Kind.MODULE;
            default -> null;
            };
        }

        private static Pattern wildcardPattern(String pattern, boolean caseSensitive) {
            if (pattern == null || pattern.indexOf('*') < 0 && pattern.indexOf('?') < 0) {
                return null;
            }
            StringBuilder regex = new StringBuilder(pattern.length() * 2);
            for (int i = 0; i < pattern.length(); i++) {
                char ch = pattern.charAt(i);
                if (ch == '*') {
                    regex.append(".*"); //$NON-NLS-1$
                } else if (ch == '?') {
                    regex.append('.');
                } else {
                    regex.append(Pattern.quote(String.valueOf(ch)));
                }
            }
            return Pattern.compile(regex.toString(), caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
        }

        private static String normalizeTypeName(IType type) {
            return type.getFullyQualifiedName('.').replace('$', '.');
        }

        private static String normalizeDeclaringType(IJavaElement element) {
            IJavaElement parent = element.getParent();
            if (parent instanceof IType type) {
                return normalizeTypeName(type);
            }
            return null;
        }

        private static String declaringSimpleName(IMethod method) {
            IJavaElement parent = method.getParent();
            if (parent instanceof IType type) {
                String typeName = normalizeTypeName(type);
                int separator = typeName.lastIndexOf('.');
                return separator < 0 ? typeName : typeName.substring(separator + 1);
            }
            return method.getElementName();
        }

        private static String normalizeJdtMethodSignature(String signature) {
            if (signature == null) {
                return ""; //$NON-NLS-1$
            }
            try {
                StringBuilder builder = new StringBuilder();
                for (String parameterType : Signature.getParameterTypes(signature)) {
                    builder.append(normalizeJdtTypeSignature(parameterType)).append(';');
                }
                return builder.toString();
            } catch (IllegalArgumentException e) {
                return signature.replace('/', '.').toLowerCase(Locale.ROOT);
            }
        }

        private static String normalizeBytecodeMethodDescriptor(String descriptor) {
            if (descriptor == null) {
                return ""; //$NON-NLS-1$
            }
            try {
                StringBuilder builder = new StringBuilder();
                for (org.objectweb.asm.Type parameterType : org.objectweb.asm.Type.getArgumentTypes(descriptor)) {
                    builder.append(normalizeAsmType(parameterType)).append(';');
                }
                return builder.toString();
            } catch (IllegalArgumentException e) {
                return descriptor.replace('/', '.').toLowerCase(Locale.ROOT);
            }
        }

        private static String normalizeJdtTypeSignature(String signature) {
            int arrayDepth = Signature.getArrayCount(signature);
            String elementType = Signature.getElementType(signature);
            String normalized = switch (Signature.getTypeSignatureKind(elementType)) {
            case Signature.BASE_TYPE_SIGNATURE -> elementType;
            case Signature.CLASS_TYPE_SIGNATURE -> Signature.toString(elementType).replace('$', '.');
            default -> Signature.getSignatureSimpleName(elementType);
            };
            return normalized.toLowerCase(Locale.ROOT) + "[]".repeat(arrayDepth); //$NON-NLS-1$
        }

        private static String normalizeAsmType(org.objectweb.asm.Type type) {
            if (type.getSort() == org.objectweb.asm.Type.ARRAY) {
                return normalizeAsmType(type.getElementType()) + "[]".repeat(type.getDimensions()); //$NON-NLS-1$
            }
            if (type.getSort() == org.objectweb.asm.Type.OBJECT) {
                return type.getClassName().replace('$', '.').toLowerCase(Locale.ROOT);
            }
            return type.getDescriptor().toLowerCase(Locale.ROOT);
        }
    }
}
