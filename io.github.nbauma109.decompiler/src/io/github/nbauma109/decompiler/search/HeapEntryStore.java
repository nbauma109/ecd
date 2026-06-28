/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;

import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Access;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.Kind;
import io.github.nbauma109.decompiler.search.BytecodeSearchEntry.TypeCategory;

final class HeapEntryStore implements EntryStore {

    static final int NULL_ID = -1;

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
    private final int[] occurrenceCounts;

    HeapEntryStore(EntryArrays arrays) {
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
        this.occurrenceCounts = arrays.columns().occurrenceCounts();
    }

    static HeapEntryStore from(List<BytecodeSearchEntry> entries, int[] counts) {
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
        int[] occurrenceCounts = new int[size];
        for (int i = 0; i < size; i++) {
            BytecodeSearchEntry entry = entries.get(i);
            kindAndFlags[i] = kindAndFlags(entry);
            elementHandleIds[i] = elements.id(entry.getElementHandle(), entry.getAnonymousElementFallback());
            nameIds[i] = strings.id(entry.getName());
            qualifiedNameIds[i] = strings.id(entry.getQualifiedName());
            declaringTypeNameIds[i] = strings.id(entry.getDeclaringTypeName());
            descriptorIds[i] = strings.id(entry.getDescriptor());
            typeCategoryIds[i] = (byte) entry.getTypeCategory().ordinal();
            occurrenceCounts[i] = counts[i];
        }
        StringTables tables = new StringTables(strings.values(), elements.handles(), elements.fallbacks());
        EntryColumns columns = new EntryColumns(kindAndFlags, elementHandleIds, nameIds, qualifiedNameIds,
                declaringTypeNameIds, descriptorIds, typeCategoryIds, occurrenceCounts);
        return new HeapEntryStore(new EntryArrays(tables, columns));
    }

    record EntryArrays(StringTables tables, EntryColumns columns) {
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
            int[] qualifiedNameIds, int[] declaringTypeNameIds, int[] descriptorIds, byte[] typeCategoryIds,
            int[] occurrenceCounts) {

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
                    .append(occurrenceCounts, that.occurrenceCounts)
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
                    .append(occurrenceCounts)
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
                    .append("occurrenceCounts", occurrenceCounts) //$NON-NLS-1$
                    .toString();
        }
    }

    @Override
    public int size() {
        return kindAndFlags.length;
    }

    BytecodeSearchEntry entry(int entryId) {
        int elementHandleId = elementHandleIds[entryId];
        return new BytecodeSearchEntry(kind(entryId), declaration(entryId),
                BytecodeSearchEntry.elementReference(elementHandle(elementHandleId),
                        anonymousElementFallback(elementHandleId)),
                BytecodeSearchEntry.symbolReference(string(nameIds[entryId]),
                        string(qualifiedNameIds[entryId]), string(declaringTypeNameIds[entryId]),
                        string(descriptorIds[entryId])),
                access(entryId), typeCategory(entryId), occurrenceCounts[entryId]);
    }

    @Override
    public void collect(Kind kind, String name, String qualifiedName, boolean wildcard,
            EntryStore.EntryConsumer consumer) throws CoreException {
        String searchName = name == null ? "" : name; //$NON-NLS-1$
        String searchQName = qualifiedName == null ? "" : qualifiedName; //$NON-NLS-1$
        for (int i = 0; i < kindAndFlags.length; i++) {
            if (Kind.values()[kindAndFlags[i] & 0x0F] == kind && (wildcard || matchesEntry(i, searchName, searchQName))) {
                consumer.accept(entry(i));
            }
        }
    }

    private boolean matchesEntry(int i, String searchName, String searchQName) {
        if (!searchName.isEmpty() && searchName.equals(string(nameIds[i]))) {
            return true;
        }
        return !searchQName.isEmpty() && !searchQName.equals(searchName)
                && searchQName.equals(string(qualifiedNameIds[i]));
    }

    @Override
    public void close() {
        // no resources to release
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

    static byte kindAndFlags(BytecodeSearchEntry entry) {
        int flags = entry.getKind().ordinal();
        if (entry.isDeclaration()) {
            flags |= 0x10;
        }
        flags |= entry.getAccess().ordinal() << 5;
        return (byte) flags;
    }

    static class Dictionary {

        private final Map<String, Integer> ids = new HashMap<>();
        private final List<String> values = new ArrayList<>();

        int id(String value) {
            if (value == null) {
                return NULL_ID;
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

        String[] values() {
            return values.toArray(String[]::new);
        }
    }

    static final class ElementDictionary extends Dictionary {

        private final List<IJavaElement> fallbacks = new ArrayList<>();

        int id(String handle, IJavaElement fallback) {
            int id = super.id(handle);
            if (id != NULL_ID) {
                while (fallbacks.size() <= id) {
                    fallbacks.add(null);
                }
                if (fallbacks.get(id) == null && fallback != null) {
                    fallbacks.set(id, fallback);
                }
            }
            return id;
        }

        String[] handles() {
            return values();
        }

        IJavaElement[] fallbacks() {
            return fallbacks.toArray(IJavaElement[]::new);
        }
    }
}
