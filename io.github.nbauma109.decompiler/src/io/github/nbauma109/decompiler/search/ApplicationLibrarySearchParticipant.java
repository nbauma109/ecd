/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.ui.search.ElementQuerySpecification;
import org.eclipse.jdt.ui.search.IMatchPresentation;
import org.eclipse.jdt.ui.search.IQueryParticipant;
import org.eclipse.jdt.ui.search.ISearchRequestor;
import org.eclipse.jdt.ui.search.PatternQuerySpecification;
import org.eclipse.jdt.ui.search.QuerySpecification;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Access;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.TypeCategory;
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

    private static final int LIMIT_TO_KIND_MASK = 0x0F;

    private static final String OBJECT_SIGNATURE = "Ljava/lang/Object;"; //$NON-NLS-1$

    private static final class SearchMatcher {

        private final int limitTo;
        private final Kind kind;
        private final TypeFilter typeFilter;
        private final String name;
        private final String qualifiedName;
        private final String declaringTypeName;
        private final String descriptor;
        private final String returnType;
        private final String fieldType;
        private final String[] parameterTypes;
        private final boolean matchParameterTypes;
        private final boolean matchReturnType;
        private final boolean caseSensitive;
        private final Pattern wildcardPattern;
        private final Pattern declaringTypePattern;
        private final Map<String, Boolean> syntheticConstructorParametersByOwner = new HashMap<>();
        private final Map<String, String> enclosingTypeErasures;

        private SearchMatcher(int limitTo, Kind kind, TypeFilter typeFilter, SearchPattern searchPattern) {
            this(limitTo, kind, typeFilter, searchPattern, Collections.emptyMap());
        }

        private SearchMatcher(int limitTo, Kind kind, TypeFilter typeFilter, SearchPattern searchPattern,
                Map<String, String> enclosingTypeErasures) {
            this.limitTo = limitTo;
            this.kind = kind;
            this.typeFilter = typeFilter;
            this.name = searchPattern.name();
            this.qualifiedName = searchPattern.qualifiedName();
            this.declaringTypeName = searchPattern.declaringTypeName();
            this.descriptor = searchPattern.descriptor();
            this.returnType = searchPattern.returnType();
            this.fieldType = searchPattern.fieldType();
            this.parameterTypes = searchPattern.parameterTypes();
            this.matchParameterTypes = searchPattern.matchParameterTypes();
            this.matchReturnType = searchPattern.matchReturnType(limitTo);
            this.caseSensitive = searchPattern.caseSensitive();
            this.wildcardPattern = searchPattern.wildcardPattern();
            this.declaringTypePattern = searchPattern.declaringTypePattern();
            this.enclosingTypeErasures = enclosingTypeErasures;
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
                        typeFilterFor(patternSpecification.getSearchFor()), parsePattern(kind,
                                patternSpecification.getPattern(), patternSpecification.isCaseSensitive()));
            }
            if (specification instanceof ElementQuerySpecification elementSpecification) {
                return forElement(specification.getLimitTo(), elementSpecification.getElement());
            }
            return null;
        }

        private static SearchMatcher forElement(int limitTo, IJavaElement element) {
            if (element instanceof IType type) {
                return new SearchMatcher(limitTo, Kind.TYPE, TypeFilter.ALL,
                        new SearchPattern(type.getElementName(), normalizeTypeName(type), null, null, null, null,
                                ParameterPattern.NONE, MatchPatterns.exact(true)));
            }
            if (element instanceof IField field) {
                return new SearchMatcher(limitTo, Kind.FIELD, TypeFilter.ALL,
                        new SearchPattern(field.getElementName(), field.getElementName(), normalizeDeclaringType(field),
                                null, null, null, ParameterPattern.NONE, MatchPatterns.exact(true)));
            }
            if (element instanceof IMethod method) {
                try {
                    boolean constructor = method.isConstructor();
                    Map<String, String> enclosingErasures = typeVariableErasures(method.getDeclaringType());
                    return new SearchMatcher(limitTo, constructor ? Kind.CONSTRUCTOR : Kind.METHOD, TypeFilter.ALL,
                            new SearchPattern(constructor ? declaringSimpleName(method) : method.getElementName(),
                                    method.getElementName(), normalizeDeclaringType(method), method.getSignature(),
                                    null, null, ParameterPattern.NONE, MatchPatterns.exact(true)),
                            enclosingErasures);
                } catch (JavaModelException e) {
                    Logger.debug(e);
                    return null;
                }
            }
            if (element instanceof IPackageFragment pkg) {
                return new SearchMatcher(limitTo, Kind.PACKAGE, TypeFilter.ALL,
                        new SearchPattern(pkg.getElementName(), pkg.getElementName(), null, null, null, null,
                                ParameterPattern.NONE, MatchPatterns.exact(true)));
            }
            if (element instanceof IModuleDescription module) {
                return new SearchMatcher(limitTo, Kind.MODULE, TypeFilter.ALL,
                        new SearchPattern(module.getElementName(), module.getElementName(), null, null, null, null,
                                ParameterPattern.NONE, MatchPatterns.exact(true)));
            }
            return null;
        }

        boolean matches(BytecodeSearchEntry entry) {
            return kind == entry.getKind()
                    && matchesLimit(entry)
                    && matchesTypeCategory(entry)
                    && matchesEntryDeclaringType(entry)
                    && matchesEntryDescriptor(entry)
                    && matchesEntryParameterTypes(entry)
                    && matchesEntryReturnType(entry)
                    && matchesEntryFieldType(entry)
                    && matchesEntryName(entry);
        }

        private boolean matchesTypeCategory(BytecodeSearchEntry entry) {
            return kind != Kind.TYPE || !entry.isDeclaration() && entry.getTypeCategory() == TypeCategory.UNKNOWN
                    || typeFilter.matches(entry.getTypeCategory());
        }

        private boolean matchesEntryDeclaringType(BytecodeSearchEntry entry) {
            return !matchesDeclaringType() || matchesDeclaringType(entry.getDeclaringTypeName());
        }

        private boolean matchesEntryDescriptor(BytecodeSearchEntry entry) {
            return descriptor == null || entry.getDescriptor() == null || sameDescriptor(descriptor, entry.getDescriptor());
        }

        private boolean matchesEntryParameterTypes(BytecodeSearchEntry entry) {
            return !matchParameterTypes || sameParameterTypes(parameterTypes, entry);
        }

        private boolean matchesEntryReturnType(BytecodeSearchEntry entry) {
            return !matchReturnType || sameReturnType(returnType, entry.getDescriptor());
        }

        private boolean matchesEntryFieldType(BytecodeSearchEntry entry) {
            return fieldType == null || sameFieldType(fieldType, entry.getDescriptor());
        }

        private boolean matchesEntryName(BytecodeSearchEntry entry) {
            if (wildcardPattern != null) {
                return wildcardPattern.matcher(entry.getName()).matches()
                        || wildcardPattern.matcher(entry.getQualifiedName()).matches();
            }
            if (kind == Kind.TYPE && Strings.CS.contains(qualifiedName, ".")) { //$NON-NLS-1$
                return sameName(qualifiedName, entry.getQualifiedName());
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
            int baseLimit = baseLimitTo(limitTo);
            if (baseLimit == IJavaSearchConstants.ALL_OCCURRENCES) {
                return true;
            }
            if (baseLimit == IJavaSearchConstants.DECLARATIONS) {
                return false;
            }
            if (baseLimit == IJavaSearchConstants.REFERENCES) {
                return !entry.isDeclaration();
            }
            if (baseLimit == IJavaSearchConstants.READ_ACCESSES) {
                return !entry.isDeclaration() && entry.getKind() == Kind.FIELD && entry.getAccess() == Access.READ;
            }
            if (baseLimit == IJavaSearchConstants.WRITE_ACCESSES) {
                return !entry.isDeclaration() && entry.getKind() == Kind.FIELD && entry.getAccess() == Access.WRITE;
            }
            return false;
        }

        private boolean sameName(String left, String right) {
            return caseSensitive ? Strings.CS.equals(left, right) : Strings.CI.equals(left, right);
        }

        private boolean sameDescriptor(String jdtSignature, String bytecodeDescriptor) {
            try {
                Map<String, String> erasures = new HashMap<>(enclosingTypeErasures);
                erasures.putAll(typeVariableErasures(jdtSignature));
                String[] jdtTypes = normalizeJdtMethodParameterTypes(jdtSignature, erasures);
                String[] bytecodeTypes = normalizeBytecodeMethodParameterTypes(bytecodeDescriptor);
                if (jdtTypes.length != bytecodeTypes.length) {
                    return false;
                }
                for (int i = 0; i < jdtTypes.length; i++) {
                    if (!sameType(jdtTypes[i], bytecodeTypes[i])) {
                        return false;
                    }
                }
                // Also compare return types so that bridge methods and covariant-return
                // overrides (which share the same parameter types but differ in return type)
                // are not incorrectly reported as references to the searched method.
                String jdtReturnType = normalizeJdtMethodReturnType(jdtSignature, erasures);
                String bytecodeReturnType = normalizeAsmType(org.objectweb.asm.Type.getReturnType(bytecodeDescriptor));
                return sameType(jdtReturnType, bytecodeReturnType);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        private boolean sameParameterTypes(String[] expectedTypes, BytecodeSearchEntry entry) {
            String bytecodeDescriptor = entry.getDescriptor();
            if (bytecodeDescriptor == null) {
                return false;
            }
            try {
                org.objectweb.asm.Type[] argumentTypes = org.objectweb.asm.Type.getArgumentTypes(bytecodeDescriptor);
                if (kind == Kind.CONSTRUCTOR && mayHaveSyntheticConstructorParameters(entry)) {
                    for (int offset = 0; offset <= Math.min(1, argumentTypes.length - expectedTypes.length); offset++) {
                        if (sameParameterTypes(expectedTypes, argumentTypes, offset)) {
                            return true;
                        }
                    }
                    return false;
                }
                return argumentTypes.length == expectedTypes.length
                        && sameParameterTypes(expectedTypes, argumentTypes, 0);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        private boolean mayHaveSyntheticConstructorParameters(BytecodeSearchEntry entry) {
            String owner = entry.getDeclaringTypeName();
            return owner != null && syntheticConstructorParametersByOwner.computeIfAbsent(owner,
                    ignored -> isNestedType(entry.getElement(), owner));
        }

        private static boolean isNestedType(IJavaElement element, String owner) {
            if (element == null || element.getJavaProject() == null) {
                return false;
            }
            try {
                IType type = findType(element, owner);
                return type != null && (isNonStaticMemberType(type) || type.isLocal() || type.isAnonymous());
            } catch (JavaModelException e) {
                Logger.debug(e);
                return false;
            }
        }

        private static boolean isNonStaticMemberType(IType type) throws JavaModelException {
            return type.getDeclaringType() != null && !Flags.isStatic(type.getFlags());
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

        private boolean sameParameterTypes(String[] expectedTypes, org.objectweb.asm.Type[] argumentTypes, int offset) {
            for (int i = 0; i < expectedTypes.length; i++) {
                if (!sameType(expectedTypes[i], normalizeAsmType(argumentTypes[offset + i]))) {
                    return false;
                }
            }
            return true;
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

        private boolean sameFieldType(String expectedType, String bytecodeDescriptor) {
            if (bytecodeDescriptor == null) {
                return false;
            }
            try {
                return sameType(expectedType, normalizeAsmType(org.objectweb.asm.Type.getType(bytecodeDescriptor)));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        private boolean sameType(String expectedType, String actualType) {
            if (sameName(expectedType, actualType)) {
                return true;
            }
            if (Strings.CS.contains(expectedType, ".")) { //$NON-NLS-1$
                return false;
            }
            return Strings.CS.contains(actualType, ".") //$NON-NLS-1$
                    && sameName(expectedType, StringUtils.substringAfterLast(actualType, ".")); //$NON-NLS-1$
        }

        private static boolean supportsLimitTo(int limitTo) {
            int baseLimit = baseLimitTo(limitTo);
            return baseLimit == IJavaSearchConstants.ALL_OCCURRENCES
                    || baseLimit == IJavaSearchConstants.REFERENCES
                    || baseLimit == IJavaSearchConstants.READ_ACCESSES
                    || baseLimit == IJavaSearchConstants.WRITE_ACCESSES;
        }

        private static int baseLimitTo(int limitTo) {
            return limitTo & LIMIT_TO_KIND_MASK;
        }

        private static SearchPattern parsePattern(Kind kind, String pattern, boolean caseSensitive) {
            String text = StringUtils.trimToEmpty(pattern);
            String memberPattern = text;
            ParameterPattern parameterPattern = ParameterPattern.NONE;
            String returnType = null;
            String fieldType = null;
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
                fieldType = parseFieldType(memberPattern);
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
            return new SearchPattern(name, qualifiedName, normalizedDeclaringTypeName, null, returnType, fieldType,
                    parameterPattern, matchPatterns);
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

        private static String parseFieldType(String pattern) {
            String type = StringUtils.trimToNull(StringUtils.substringAfter(StringUtils.trimToEmpty(pattern), " ")); //$NON-NLS-1$
            return type == null ? null : normalizePatternType(type);
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
                int genericEnd = normalized.lastIndexOf('>');
                String suffix = genericEnd >= 0 ? normalized.substring(genericEnd + 1) : ""; //$NON-NLS-1$
                normalized = normalized.substring(0, genericStart) + suffix;
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

        private static TypeFilter typeFilterFor(int searchFor) {
            return switch (searchFor) {
            case IJavaSearchConstants.CLASS -> TypeFilter.CLASS;
            case IJavaSearchConstants.INTERFACE -> TypeFilter.INTERFACE;
            case IJavaSearchConstants.ENUM -> TypeFilter.ENUM;
            case IJavaSearchConstants.ANNOTATION_TYPE -> TypeFilter.ANNOTATION;
            case IJavaSearchConstants.CLASS_AND_ENUM -> TypeFilter.CLASS_AND_ENUM;
            case IJavaSearchConstants.CLASS_AND_INTERFACE -> TypeFilter.CLASS_AND_INTERFACE;
            case IJavaSearchConstants.INTERFACE_AND_ANNOTATION -> TypeFilter.INTERFACE_AND_ANNOTATION;
            default -> TypeFilter.ALL;
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

        private static String[] normalizeJdtMethodParameterTypes(String signature, Map<String, String> typeVariableErasures) {
            if (signature == null) {
                return new String[0];
            }
            String[] parameterTypes = Signature.getParameterTypes(signature);
            String[] normalized = new String[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                normalized[i] = normalizeJdtTypeSignature(parameterTypes[i], typeVariableErasures);
            }
            return normalized;
        }

        private static String normalizeJdtMethodReturnType(String signature, Map<String, String> typeVariableErasures) {
            return normalizeJdtTypeSignature(Signature.getReturnType(signature), typeVariableErasures);
        }

        private static Map<String, String> typeVariableErasures(String signature) {
            Map<String, String> erasures = new HashMap<>();
            for (String typeParameter : Signature.getTypeParameters(signature)) {
                String[] bounds = Signature.getTypeParameterBounds(typeParameter);
                erasures.put(Signature.getTypeVariable(typeParameter),
                        bounds.length == 0 ? OBJECT_SIGNATURE : bounds[0]);
            }
            return erasures;
        }

        private static Map<String, String> typeVariableErasures(IType declaringType) throws JavaModelException {
            if (declaringType == null) {
                return Collections.emptyMap();
            }
            Map<String, String> erasures = new HashMap<>();
            for (ITypeParameter tp : declaringType.getTypeParameters()) {
                String[] bounds = tp.getBoundsSignatures();
                erasures.put(tp.getElementName(), bounds.length == 0 ? OBJECT_SIGNATURE : bounds[0]);
            }
            return erasures;
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

        private static String normalizeJdtTypeSignature(String signature, Map<String, String> typeVariableErasures) {
            int arrayDepth = Signature.getArrayCount(signature);
            String elementType = eraseJdtType(Signature.getElementType(signature), typeVariableErasures);
            String normalized = switch (Signature.getTypeSignatureKind(elementType)) {
            case Signature.BASE_TYPE_SIGNATURE -> elementType;
            case Signature.CLASS_TYPE_SIGNATURE -> Signature.toString(elementType).replace('/', '.').replace('$', '.');
            default -> Signature.getSignatureSimpleName(elementType);
            };
            return normalized.toLowerCase(Locale.ROOT) + "[]".repeat(arrayDepth); //$NON-NLS-1$
        }

        private static String eraseJdtType(String signature, Map<String, String> typeVariableErasures) {
            String erased = Signature.getTypeErasure(signature);
            for (int i = 0; i <= typeVariableErasures.size()
                    && Signature.getTypeSignatureKind(erased) == Signature.TYPE_VARIABLE_SIGNATURE; i++) {
                erased = typeVariableErasures.getOrDefault(Signature.getTypeVariable(erased), OBJECT_SIGNATURE);
            }
            return Signature.getTypeErasure(erased);
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

    private enum TypeFilter {
        ALL {
            @Override
            boolean matches(TypeCategory category) {
                return true;
            }
        },
        CLASS {
            @Override
            boolean matches(TypeCategory category) {
                return category == TypeCategory.CLASS;
            }
        },
        INTERFACE {
            @Override
            boolean matches(TypeCategory category) {
                return category == TypeCategory.INTERFACE;
            }
        },
        ENUM {
            @Override
            boolean matches(TypeCategory category) {
                return category == TypeCategory.ENUM;
            }
        },
        ANNOTATION {
            @Override
            boolean matches(TypeCategory category) {
                return category == TypeCategory.ANNOTATION;
            }
        },
        CLASS_AND_ENUM {
            @Override
            boolean matches(TypeCategory category) {
                return category == TypeCategory.CLASS || category == TypeCategory.ENUM;
            }
        },
        CLASS_AND_INTERFACE {
            @Override
            boolean matches(TypeCategory category) {
                return category == TypeCategory.CLASS || category == TypeCategory.INTERFACE;
            }
        },
        INTERFACE_AND_ANNOTATION {
            @Override
            boolean matches(TypeCategory category) {
                return category == TypeCategory.INTERFACE || category == TypeCategory.ANNOTATION;
            }
        };

        abstract boolean matches(TypeCategory category);
    }

    public record SearchPattern(String name, String qualifiedName, String declaringTypeName, String descriptor,
            String returnType, String fieldType, ParameterPattern parameterPattern, MatchPatterns matchPatterns) {

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
                    .append(fieldType, that.fieldType)
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
                    .append(fieldType)
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
                    .append("fieldType", fieldType) //$NON-NLS-1$
                    .append("parameterPattern", parameterPattern) //$NON-NLS-1$
                    .append("matchPatterns", matchPatterns) //$NON-NLS-1$
                    .toString();
        }
    }

    public static class MatchPatterns {

        private final boolean caseSensitive;
        private final Pattern wildcardPattern;
        private final Pattern declaringTypePattern;

        public MatchPatterns(boolean caseSensitive, Pattern wildcardPattern, Pattern declaringTypePattern) {
            this.caseSensitive = caseSensitive;
            this.wildcardPattern = wildcardPattern;
            this.declaringTypePattern = declaringTypePattern;
        }

        public static MatchPatterns exact(boolean caseSensitive) {
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

    public static class ParameterPattern {

        public static final ParameterPattern NONE = new ParameterPattern(new String[0], false);

        private final String[] types;
        private final boolean match;

        public ParameterPattern(String[] types, boolean match) {
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
}
