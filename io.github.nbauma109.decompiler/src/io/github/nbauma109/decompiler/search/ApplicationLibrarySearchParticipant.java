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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
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
        return entryCount == 0 ? 50 : Math.clamp(entryCount / 200, 100, 1000);
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
        private final String returnType;
        private final String[] parameterTypes;
        private final boolean matchParameterTypes;
        private final boolean matchReturnType;
        private final boolean caseSensitive;
        private final Pattern wildcardPattern;
        private final Pattern declaringTypePattern;

        private SearchMatcher(int limitTo, Kind kind, SearchPattern searchPattern) {
            this.limitTo = limitTo;
            this.kind = kind;
            this.name = searchPattern.name();
            this.qualifiedName = searchPattern.qualifiedName();
            this.declaringTypeName = searchPattern.declaringTypeName();
            this.descriptor = searchPattern.descriptor();
            this.returnType = searchPattern.returnType();
            this.parameterTypes = searchPattern.parameterTypes();
            this.matchParameterTypes = searchPattern.matchParameterTypes();
            this.matchReturnType = searchPattern.matchReturnType(limitTo);
            this.caseSensitive = searchPattern.caseSensitive();
            this.wildcardPattern = searchPattern.wildcardPattern();
            this.declaringTypePattern = searchPattern.declaringTypePattern();
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
                        new SearchPattern(type.getElementName(), normalizeTypeName(type), null, null, null,
                                ParameterPattern.NONE, MatchPatterns.exact(true)));
            }
            if (element instanceof IField field) {
                return new SearchMatcher(limitTo, Kind.FIELD,
                        new SearchPattern(field.getElementName(), field.getElementName(), normalizeDeclaringType(field),
                                null, null, ParameterPattern.NONE, MatchPatterns.exact(true)));
            }
            if (element instanceof IMethod method) {
                try {
                    boolean constructor = method.isConstructor();
                    return new SearchMatcher(limitTo, constructor ? Kind.CONSTRUCTOR : Kind.METHOD,
                            new SearchPattern(constructor ? declaringSimpleName(method) : method.getElementName(),
                                    method.getElementName(), normalizeDeclaringType(method), method.getSignature(),
                                    null, ParameterPattern.NONE, MatchPatterns.exact(true)));
                } catch (JavaModelException e) {
                    Logger.debug(e);
                    return null;
                }
            }
            if (element instanceof IPackageFragment pkg) {
                return new SearchMatcher(limitTo, Kind.PACKAGE,
                        new SearchPattern(pkg.getElementName(), pkg.getElementName(), null, null, null,
                                ParameterPattern.NONE, MatchPatterns.exact(true)));
            }
            if (element instanceof IModuleDescription module) {
                return new SearchMatcher(limitTo, Kind.MODULE,
                        new SearchPattern(module.getElementName(), module.getElementName(), null, null, null,
                                ParameterPattern.NONE, MatchPatterns.exact(true)));
            }
            return null;
        }

        private record SearchPattern(String name, String qualifiedName, String declaringTypeName, String descriptor,
                String returnType, ParameterPattern parameterPattern, MatchPatterns matchPatterns) {

            private String[] parameterTypes() {
                return parameterPattern.types();
            }

            private boolean matchParameterTypes() {
                return parameterPattern.match();
            }

            private boolean matchReturnType(int limitTo) {
                return returnType != null && (limitTo & IJavaSearchConstants.IGNORE_RETURN_TYPE) == 0;
            }

            private boolean caseSensitive() {
                return matchPatterns.caseSensitive();
            }

            private Pattern wildcardPattern() {
                return matchPatterns.wildcardPattern();
            }

            private Pattern declaringTypePattern() {
                return matchPatterns.declaringTypePattern();
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) {
                    return true;
                }
                if (other == null || other.getClass() != getClass()) {
                    return false;
                }
                SearchPattern that = (SearchPattern) other;
                return new EqualsBuilder()
                        .append(name, that.name)
                        .append(qualifiedName, that.qualifiedName)
                        .append(declaringTypeName, that.declaringTypeName)
                        .append(descriptor, that.descriptor)
                        .append(returnType, that.returnType)
                        .append(parameterPattern, that.parameterPattern)
                        .append(matchPatterns, that.matchPatterns)
                        .isEquals();
            }

            @Override
            public int hashCode() {
                return new HashCodeBuilder(17, 37)
                        .append(name)
                        .append(qualifiedName)
                        .append(declaringTypeName)
                        .append(descriptor)
                        .append(returnType)
                        .append(parameterPattern)
                        .append(matchPatterns)
                        .toHashCode();
            }

            @Override
            public String toString() {
                return new ToStringBuilder(this)
                        .append("name", name) //$NON-NLS-1$
                        .append("qualifiedName", qualifiedName) //$NON-NLS-1$
                        .append("declaringTypeName", declaringTypeName) //$NON-NLS-1$
                        .append("descriptor", descriptor) //$NON-NLS-1$
                        .append("returnType", returnType) //$NON-NLS-1$
                        .append("parameterPattern", parameterPattern) //$NON-NLS-1$
                        .append("matchPatterns", matchPatterns) //$NON-NLS-1$
                        .toString();
            }
        }

        private static class MatchPatterns {

            private final boolean caseSensitive;
            private final Pattern wildcardPattern;
            private final Pattern declaringTypePattern;

            private MatchPatterns(boolean caseSensitive, Pattern wildcardPattern, Pattern declaringTypePattern) {
                this.caseSensitive = caseSensitive;
                this.wildcardPattern = wildcardPattern;
                this.declaringTypePattern = declaringTypePattern;
            }

            private static MatchPatterns exact(boolean caseSensitive) {
                return new MatchPatterns(caseSensitive, null, null);
            }

            private boolean caseSensitive() {
                return caseSensitive;
            }

            private Pattern wildcardPattern() {
                return wildcardPattern;
            }

            private Pattern declaringTypePattern() {
                return declaringTypePattern;
            }
        }

        private static class ParameterPattern {

            private static final ParameterPattern NONE = new ParameterPattern(new String[0], false);

            private final String[] types;
            private final boolean match;

            private ParameterPattern(String[] types, boolean match) {
                this.types = types;
                this.match = match;
            }

            private String[] types() {
                return types;
            }

            private boolean match() {
                return match;
            }
        }

        boolean matches(BytecodeSearchEntry entry) {
            if (kind != entry.getKind() || !matchesLimit(entry)) {
                return false;
            }
            if (matchesDeclaringType() && !matchesDeclaringType(entry.getDeclaringTypeName())) {
                return false;
            }
            if (descriptor != null && entry.getDescriptor() != null && !sameDescriptor(descriptor, entry.getDescriptor())) {
                return false;
            }
            if (matchParameterTypes && !sameParameterTypes(parameterTypes, entry.getDescriptor())) {
                return false;
            }
            if (matchReturnType && !sameReturnType(returnType, entry.getDescriptor())) {
                return false;
            }
            if (wildcardPattern != null) {
                return wildcardPattern.matcher(entry.getName()).matches()
                        || wildcardPattern.matcher(entry.getQualifiedName()).matches();
            }
            return sameName(name, entry.getName()) || sameName(qualifiedName, entry.getQualifiedName());
        }

        private boolean matchesDeclaringType(String actual) {
            if (actual == null) {
                return false;
            }
            if (declaringTypePattern != null) {
                return declaringTypePattern.matcher(actual).matches()
                        || declaringTypePattern.matcher(simpleName(actual)).matches();
            }
            return sameName(declaringTypeName, actual)
                    || kind == Kind.CONSTRUCTOR && sameName(declaringTypeName, simpleName(actual));
        }

        private boolean matchesDeclaringType() {
            return declaringTypeName != null && (limitTo & IJavaSearchConstants.IGNORE_DECLARING_TYPE) == 0;
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
            return caseSensitive ? Strings.CS.equals(left, right) : Strings.CI.equals(left, right);
        }

        private boolean sameDescriptor(String jdtSignature, String bytecodeDescriptor) {
            try {
                String[] jdtTypes = normalizeJdtMethodParameterTypes(jdtSignature);
                String[] bytecodeTypes = normalizeBytecodeMethodParameterTypes(bytecodeDescriptor);
                if (jdtTypes.length != bytecodeTypes.length) {
                    return false;
                }
                for (int i = 0; i < jdtTypes.length; i++) {
                    if (!sameType(jdtTypes[i], bytecodeTypes[i])) {
                        return false;
                    }
                }
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
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

        private boolean sameReturnType(String expectedType, String bytecodeDescriptor) {
            if (bytecodeDescriptor == null) {
                return false;
            }
            try {
                return sameType(expectedType, normalizeAsmType(org.objectweb.asm.Type.getReturnType(bytecodeDescriptor)));
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
            String text = StringUtils.trimToEmpty(pattern);
            String memberPattern = text;
            ParameterPattern parameterPattern = ParameterPattern.NONE;
            String returnType = null;
            if (isMethodOrConstructor(kind, text)) {
                int openParen = text.indexOf('(');
                int closeParen = text.lastIndexOf(')');
                memberPattern = stripLeadingTypeArguments(StringUtils.trim(text.substring(0, openParen)));
                if (closeParen > openParen) {
                    String parameters = text.substring(openParen + 1, closeParen);
                    parameterPattern = new ParameterPattern(parseParameterTypes(parameters),
                            !isAnyParameterPattern(parameters));
                    String trailing = StringUtils.trimToNull(text.substring(closeParen + 1));
                    if (kind == Kind.METHOD && trailing != null) {
                        returnType = normalizePatternType(trailing);
                    }
                }
            }

            if (kind == Kind.FIELD) {
                memberPattern = stripFieldType(memberPattern);
            } else if (kind == Kind.TYPE) {
                memberPattern = stripTypeArguments(memberPattern);
            }

            String name = memberPattern;
            String qualifiedName = memberPattern;
            String declaringTypeName = null;
            switch (kind) {
                case METHOD, FIELD:
                    if (Strings.CS.contains(memberPattern, ".")) { //$NON-NLS-1$
                        declaringTypeName = StringUtils.substringBeforeLast(memberPattern, "."); //$NON-NLS-1$
                        name = StringUtils.substringAfterLast(memberPattern, "."); //$NON-NLS-1$
                        qualifiedName = name;
                    }
                    break;
                case CONSTRUCTOR:
                    declaringTypeName = memberPattern;
                    name = simpleName(memberPattern);
                    break;
                case TYPE:
                    name = simpleName(memberPattern);
                    break;
                default:
                    break;
            }

            String normalizedDeclaringTypeName = emptyToNull(declaringTypeName);
            String wildcardTarget = hasWildcard(qualifiedName) ? qualifiedName : name;
            MatchPatterns matchPatterns = new MatchPatterns(caseSensitive, wildcardPattern(wildcardTarget, caseSensitive),
                    declaringTypePattern(normalizedDeclaringTypeName, caseSensitive));
            return new SearchPattern(name, qualifiedName, normalizedDeclaringTypeName, null, returnType, parameterPattern,
                    matchPatterns);
        }

        private static boolean isMethodOrConstructor(Kind kind, String text) {
            return (kind == Kind.METHOD || kind == Kind.CONSTRUCTOR) && text.indexOf('(') >= 0;
        }

        private static boolean hasWildcard(String pattern) {
            return StringUtils.containsAny(pattern, '*', '?');
        }

        private static String stripFieldType(String pattern) {
            return StringUtils.substringBefore(StringUtils.trimToEmpty(pattern), " "); //$NON-NLS-1$
        }

        private static String stripLeadingTypeArguments(String pattern) {
            String trimmed = StringUtils.trimToEmpty(pattern);
            if (!Strings.CS.startsWith(trimmed, "<")) { //$NON-NLS-1$
                return trimmed;
            }
            int end = closingTypeArgumentOffset(trimmed, 0);
            return end < 0 ? trimmed : StringUtils.trim(trimmed.substring(end + 1));
        }

        private static String stripTypeArguments(String pattern) {
            String text = StringUtils.trimToEmpty(pattern);
            StringBuilder stripped = new StringBuilder(text.length());
            int depth = 0;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '<') {
                    depth++;
                } else if (ch == '>') {
                    depth = Math.max(0, depth - 1);
                } else if (depth == 0) {
                    stripped.append(ch);
                }
            }
            return StringUtils.trim(stripped.toString());
        }

        private static int closingTypeArgumentOffset(String text, int openOffset) {
            int depth = 0;
            for (int i = openOffset; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '<') {
                    depth++;
                } else if (ch == '>') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
            return -1;
        }

        private static String[] parseParameterTypes(String parameters) {
            String trimmed = StringUtils.trimToEmpty(parameters);
            if (StringUtils.isEmpty(trimmed) || isAnyParameterPattern(trimmed)) {
                return new String[0];
            }
            return splitParameterTypes(trimmed);
        }

        private static boolean isAnyParameterPattern(String parameters) {
            return Strings.CS.equals("*", StringUtils.trimToEmpty(parameters)); //$NON-NLS-1$
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
            String normalized = Strings.CS.replace(StringUtils.trimToEmpty(type), "...", "[]"); //$NON-NLS-1$ //$NON-NLS-2$
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
            case "void" -> "v"; //$NON-NLS-1$ //$NON-NLS-2$
            default -> baseType;
            } + suffix;
        }

        private static String simpleName(String name) {
            return Strings.CS.contains(name, ".") ? StringUtils.substringAfterLast(name, ".") : name; //$NON-NLS-1$ //$NON-NLS-2$
        }

        private static String emptyToNull(String value) {
            return StringUtils.isBlank(value) ? null : value;
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
            if (!StringUtils.containsAny(pattern, '*', '?')) {
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

        private static Pattern declaringTypePattern(String pattern, boolean caseSensitive) {
            if (pattern == null) {
                return null;
            }
            Pattern wildcard = wildcardPattern(pattern, caseSensitive);
            return wildcard == null ? exactPattern(pattern, caseSensitive) : wildcard;
        }

        private static Pattern exactPattern(String pattern, boolean caseSensitive) {
            return Pattern.compile(Pattern.quote(pattern), caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
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

        private static String[] normalizeJdtMethodParameterTypes(String signature) {
            if (signature == null) {
                return new String[0];
            }
            String[] parameterTypes = Signature.getParameterTypes(signature);
            String[] normalized = new String[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                normalized[i] = normalizeJdtTypeSignature(parameterTypes[i]);
            }
            return normalized;
        }

        private static String[] normalizeBytecodeMethodParameterTypes(String descriptor) {
            if (descriptor == null) {
                return new String[0];
            }
            org.objectweb.asm.Type[] parameterTypes = org.objectweb.asm.Type.getArgumentTypes(descriptor);
            String[] normalized = new String[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                normalized[i] = normalizeAsmType(parameterTypes[i]);
            }
            return normalized;
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
