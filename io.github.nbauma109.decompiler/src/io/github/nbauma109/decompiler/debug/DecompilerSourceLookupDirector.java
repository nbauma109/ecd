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
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.debug.ui.ISourcePresentation;
import org.eclipse.debug.ui.sourcelookup.CommonSourceNotFoundEditorInput;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.ui.IEditorInput;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;

public final class DecompilerSourceLookupDirector implements ISourceLookupDirector, ISourcePresentation {

    private static final String JAVA_DEBUG_MODEL_ID = "org.eclipse.jdt.debug"; //$NON-NLS-1$

    private final ISourceLookupDirector delegate;
    private final ISourcePresentation delegatePresentation;

    public DecompilerSourceLookupDirector(ISourceLookupDirector delegate) {
        this.delegate = delegate;
        this.delegatePresentation = delegate instanceof ISourcePresentation presentation ? presentation : null;
    }

    @Override
    public Object getSourceElement(IStackFrame stackFrame) {
        Object fromDelegate = delegate.getSourceElement(stackFrame);
        if (isRealSourceResult(fromDelegate)) {
            return fromDelegate;
        }

        Optional<IClassFile> classFile = ClassFileResolver.resolveClassFile(stackFrame);
        if (!classFile.isPresent() && fromDelegate instanceof IClassFile fromDelegateClassFile) {
            classFile = Optional.of(fromDelegateClassFile);
        }
        if (!classFile.isPresent()) {
            return fromDelegate;
        }

        if (ClassFileResolver.hasRealSource(classFile.get())) {
            return classFile.get();
        }

        boolean decompiled = DecompileBufferPopulator.ensureDecompiled(classFile.get());
        if (decompiled) {
            delegate.clearSourceElements(stackFrame);
            return new DecompiledClassFileSourceElement(classFile.get());
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
        if (!classFile.isPresent() && fromDelegate instanceof IClassFile fromDelegateClassFile) {
            classFile = Optional.of(fromDelegateClassFile);
        }
        if (!classFile.isPresent()) {
            return fromDelegate;
        }

        if (ClassFileResolver.hasRealSource(classFile.get())) {
            return classFile.get();
        }

        boolean decompiled = DecompileBufferPopulator.ensureDecompiled(classFile.get());
        if (decompiled) {
            delegate.clearSourceElements(element);
            return new DecompiledClassFileSourceElement(classFile.get());
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
            classFile = firstClassFile(fromDelegate);
        }
        if (!classFile.isPresent()) {
            return fromDelegate;
        }

        if (ClassFileResolver.hasRealSource(classFile.get())) {
            return new Object[] { classFile.get() };
        }

        boolean decompiled = DecompileBufferPopulator.ensureDecompiled(classFile.get());
        if (decompiled) {
            delegate.clearSourceElements(object);
            return new Object[] { new DecompiledClassFileSourceElement(classFile.get()) };
        }

        return fromDelegate;
    }

    @Override
    public IEditorInput getEditorInput(Object sourceElement) {
        IClassFile classFile = extractClassFile(sourceElement);
        if (shouldUseDecompilerEditor(sourceElement) && classFile != null) {
            IEditorInput editorInput = EditorUtility.getEditorInput(classFile);
            if (editorInput != null) {
                return editorInput;
            }
        }

        Object unwrappedSourceElement = unwrapSourceElement(sourceElement);
        if (delegatePresentation != null) {
            IEditorInput editorInput = delegatePresentation.getEditorInput(unwrappedSourceElement);
            if (editorInput != null) {
                return editorInput;
            }
        }

        IDebugModelPresentation modelPresentation = DebugUITools.newDebugModelPresentation(JAVA_DEBUG_MODEL_ID);
        try {
            IEditorInput editorInput = modelPresentation.getEditorInput(unwrappedSourceElement);
            if (editorInput != null) {
                return editorInput;
            }
        } finally {
            modelPresentation.dispose();
        }

        modelPresentation = DebugUITools.newDebugModelPresentation();
        try {
            return modelPresentation.getEditorInput(unwrappedSourceElement);
        } finally {
            modelPresentation.dispose();
        }
    }

    @Override
    public String getEditorId(IEditorInput editorInput, Object sourceElement) {
        if (shouldUseDecompilerEditor(sourceElement)) {
            return JavaDecompilerPlugin.EDITOR_ID;
        }

        Object unwrappedSourceElement = unwrapSourceElement(sourceElement);
        if (delegatePresentation != null) {
            String editorId = delegatePresentation.getEditorId(editorInput, unwrappedSourceElement);
            if (editorId != null) {
                return editorId;
            }
        }

        IDebugModelPresentation modelPresentation = DebugUITools.newDebugModelPresentation(JAVA_DEBUG_MODEL_ID);
        try {
            String editorId = modelPresentation.getEditorId(editorInput, unwrappedSourceElement);
            if (editorId != null) {
                return editorId;
            }
        } finally {
            modelPresentation.dispose();
        }

        modelPresentation = DebugUITools.newDebugModelPresentation();
        try {
            return modelPresentation.getEditorId(editorInput, unwrappedSourceElement);
        } finally {
            modelPresentation.dispose();
        }
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

        if (result instanceof DecompiledClassFileSourceElement) {
            return true;
        }

        if (result instanceof IClassFile classFile) {
            return ClassFileResolver.hasRealSource(classFile) || DecompileBufferPopulator.wasDecompiled(classFile);
        }

        if (result.getClass().getName().endsWith(".SourceNotFoundEditorInput")) { //$NON-NLS-1$
            return false;
        }

        return !(result instanceof CommonSourceNotFoundEditorInput);
    }

    private boolean shouldUseDecompilerEditor(Object sourceElement) {
        if (sourceElement instanceof DecompiledClassFileSourceElement) {
            return true;
        }
        if (sourceElement instanceof IClassFile classFile) {
            return DecompileBufferPopulator.wasDecompiled(classFile) || !ClassFileResolver.hasAttachedSource(classFile);
        }
        return false;
    }

    private IClassFile extractClassFile(Object sourceElement) {
        if (sourceElement instanceof DecompiledClassFileSourceElement classFileSourceElement) {
            return classFileSourceElement.classFile();
        }
        if (sourceElement instanceof IClassFile classFile) {
            return classFile;
        }
        return null;
    }

    private Object unwrapSourceElement(Object sourceElement) {
        if (sourceElement instanceof DecompiledClassFileSourceElement classFileSourceElement) {
            return classFileSourceElement.classFile();
        }
        return sourceElement;
    }

    private Optional<IClassFile> firstClassFile(Object[] sourceElements) {
        if (sourceElements == null || sourceElements.length == 0) {
            return Optional.empty();
        }
        for (Object sourceElement : sourceElements) {
            if (sourceElement instanceof IClassFile classFile) {
                return Optional.of(classFile);
            }
        }
        return Optional.empty();
    }

    private static final class DecompiledClassFileSourceElement {
        private final IClassFile classFile;

        private DecompiledClassFileSourceElement(IClassFile classFile) {
            this.classFile = classFile;
        }

        private IClassFile classFile() {
            return classFile;
        }
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
