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
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.debug.core.sourcelookup.ISourceContainerType;
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector;
import org.eclipse.debug.core.sourcelookup.ISourceLookupParticipant;
import org.eclipse.debug.core.sourcelookup.ISourcePathComputer;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.debug.ui.ISourcePresentation;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.ui.IEditorInput;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;

public final class DecompilerSourceLookupDirector implements ISourceLookupDirector, ISourcePresentation {

    private final ISourceLookupDirector delegate;

    public DecompilerSourceLookupDirector(ISourceLookupDirector delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object getSourceElement(IStackFrame stackFrame) {
        return getSourceElement((Object) stackFrame);
    }

    @Override
    public Object getSourceElement(Object element) {
        Optional<IClassFile> resolved = ClassFileResolver.resolveClassFile(element);
        if (resolved.isPresent()) {
            return resolved.get();
        }
        return delegate.getSourceElement(element);
    }

    @Override
    public Object[] findSourceElements(Object object) throws CoreException {
        Optional<IClassFile> resolved = ClassFileResolver.resolveClassFile(object);
        if (resolved.isPresent()) {
            return new Object[] { resolved.get() };
        }
        return delegate.findSourceElements(object);
    }

    @Override
    public IEditorInput getEditorInput(Object element) {
        Optional<IClassFile> resolved = ClassFileResolver.resolveClassFile(element);
        if (resolved.isPresent()) {
            return EditorUtility.getEditorInput(resolved.get());
        }
        return delegateEditorInput(element);
    }

    @Override
    public String getEditorId(IEditorInput input, Object element) {
        Optional<IClassFile> resolved = ClassFileResolver.resolveClassFile(element);
        if (resolved.isPresent()) {
            return JavaDecompilerPlugin.EDITOR_ID;
        }
        return delegateEditorId(input, element);
    }

    private IEditorInput delegateEditorInput(Object element) {
        return withDebugModelPresentation(element, presentation -> presentation.getEditorInput(element));
    }

    private String delegateEditorId(final IEditorInput input, final Object element) {
        return withDebugModelPresentation(element, presentation -> presentation.getEditorId(input, element));
    }

    private <T> T withDebugModelPresentation(Object element, PresentationQuery<T> query) {
        Optional<String> modelId = modelIdentifier(element);
        if (!modelId.isPresent()) {
            return null;
        }

        IDebugModelPresentation presentation = DebugUITools.newDebugModelPresentation(modelId.get());
        try {
            return query.query(presentation);
        } finally {
            presentation.dispose();
        }
    }

    private Optional<String> modelIdentifier(Object element) {
        if (element instanceof IDebugElement debugElement) {
            return Optional.of(debugElement.getModelIdentifier());
        }
        if (element instanceof IBreakpoint breakpoint) {
            return Optional.of(breakpoint.getModelIdentifier());
        }
        return Optional.empty();
    }

    private interface PresentationQuery<T> {
        T query(IDebugModelPresentation presentation);
    }

    // --- Pure delegation below ---

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
    public ILaunchConfiguration getLaunchConfiguration() {
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
    public void initializeDefaults(ILaunchConfiguration configuration) throws CoreException {
        delegate.initializeDefaults(configuration);
    }

    @Override
    public void initializeFromMemento(String memento) throws CoreException {
        delegate.initializeFromMemento(memento);
    }

    @Override
    public void initializeFromMemento(String memento, ILaunchConfiguration configuration) throws CoreException {
        delegate.initializeFromMemento(memento, configuration);
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }
}
