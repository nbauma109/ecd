/*******************************************************************************
 * Copyright (c) 2026 @nbauma109.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.debug;

import java.util.Optional;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.debug.core.sourcelookup.ISourceContainerType;
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector;
import org.eclipse.debug.core.sourcelookup.ISourceLookupParticipant;
import org.eclipse.debug.core.sourcelookup.ISourcePathComputer;
import org.eclipse.debug.ui.sourcelookup.CommonSourceNotFoundEditorInput;
import org.eclipse.jdt.core.IClassFile;

public final class DecompilerSourceLookupDirector implements ISourceLookupDirector {

    private final ISourceLookupDirector delegate;

    public DecompilerSourceLookupDirector(ISourceLookupDirector delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object getSourceElement(IStackFrame stackFrame) {
        Object fromDelegate = delegate.getSourceElement(stackFrame);
        if (isRealSourceResult(fromDelegate)) {
            return fromDelegate;
        }

        Optional<IClassFile> classFile = ClassFileResolver.resolveClassFile(stackFrame);
        if (!classFile.isPresent()) {
            return fromDelegate;
        }

        if (ClassFileResolver.hasRealSource(classFile.get())) {
            return classFile.get();
        }

        boolean decompiled = DecompileBufferPopulator.ensureDecompiled(classFile.get());
        if (decompiled && ClassFileResolver.hasRealSource(classFile.get())) {
            delegate.clearSourceElements(stackFrame);
            return classFile.get();
        }

        return fromDelegate;
    }

    @Override
    public Object getSourceElement(Object element) {
        Object fromDelegate = delegate.getSourceElement(element);
        if (isRealSourceResult(fromDelegate)) {
            return fromDelegate;
        }

        Optional<IClassFile> classFile = ClassFileResolver.resolveClassFile(element);
        if (!classFile.isPresent()) {
            return fromDelegate;
        }

        if (ClassFileResolver.hasRealSource(classFile.get())) {
            return classFile.get();
        }

        boolean decompiled = DecompileBufferPopulator.ensureDecompiled(classFile.get());
        if (decompiled && ClassFileResolver.hasRealSource(classFile.get())) {
            delegate.clearSourceElements(element);
            return classFile.get();
        }

        return fromDelegate;
    }

    @Override
    public Object[] findSourceElements(Object object) throws CoreException {
        Object[] fromDelegate = delegate.findSourceElements(object);
        if (containsRealSourceResult(fromDelegate)) {
            return fromDelegate;
        }

        Optional<IClassFile> classFile = ClassFileResolver.resolveClassFile(object);
        if (!classFile.isPresent()) {
            return fromDelegate;
        }

        if (ClassFileResolver.hasRealSource(classFile.get())) {
            return new Object[] { classFile.get() };
        }

        boolean decompiled = DecompileBufferPopulator.ensureDecompiled(classFile.get());
        if (decompiled && ClassFileResolver.hasRealSource(classFile.get())) {
            delegate.clearSourceElements(object);
            return new Object[] { classFile.get() };
        }

        return fromDelegate;
    }

    private boolean containsRealSourceResult(Object[] elements) {
        if (elements == null || elements.length == 0) {
            return false;
        }
        for (Object element : elements) {
            if (isRealSourceResult(element)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRealSourceResult(Object result) {
        if (result == null) {
            return false;
        }

        if (result instanceof IClassFile classFile) {
            return ClassFileResolver.hasRealSource(classFile);
        }

        return !(result instanceof CommonSourceNotFoundEditorInput);
    }

    // --- delegate everything else to preserve source lookup behavior ---

    @Override
    public void addParticipants(ISourceLookupParticipant[] participants) {
        delegate.addParticipants(participants);
    }

    @Override
    public void removeParticipants(ISourceLookupParticipant[] participants) {
        delegate.removeParticipants(participants);
    }

    @Override
    public void clearSourceElements(Object element) {
        delegate.clearSourceElements(element);
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public org.eclipse.debug.core.ILaunchConfiguration getLaunchConfiguration() {
        return delegate.getLaunchConfiguration();
    }

    @Override
    public ISourceLookupParticipant[] getParticipants() {
        return delegate.getParticipants();
    }

    @Override
    public ISourceContainer[] getSourceContainers() {
        return delegate.getSourceContainers();
    }

    @Override
    public ISourcePathComputer getSourcePathComputer() {
        return delegate.getSourcePathComputer();
    }

    @Override
    public void initializeParticipants() {
        delegate.initializeParticipants();
    }

    @Override
    public boolean isFindDuplicates() {
        return delegate.isFindDuplicates();
    }

    @Override
    public void setFindDuplicates(boolean findDuplicates) {
        delegate.setFindDuplicates(findDuplicates);
    }

    @Override
    public void setSourceContainers(ISourceContainer[] containers) {
        delegate.setSourceContainers(containers);
    }

    @Override
    public void setSourcePathComputer(ISourcePathComputer computer) {
        delegate.setSourcePathComputer(computer);
    }

    @Override
    public boolean supportsSourceContainerType(ISourceContainerType type) {
        return delegate.supportsSourceContainerType(type);
    }

    @Override
    public String getMemento() throws CoreException {
        return delegate.getMemento();
    }

    @Override
    public void initializeDefaults(org.eclipse.debug.core.ILaunchConfiguration configuration) throws CoreException {
        delegate.initializeDefaults(configuration);
    }

    @Override
    public void initializeFromMemento(String memento) throws CoreException {
        delegate.initializeFromMemento(memento);
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }

    @Override
    public void initializeFromMemento(String memento, org.eclipse.debug.core.ILaunchConfiguration configuration) throws CoreException {
        delegate.initializeFromMemento(memento, configuration);
    }
}
