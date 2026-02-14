/*******************************************************************************
 * Copyright (c) 2026 @nbauma109.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.debug;

import java.util.Optional;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.debug.ui.IDebugModelPresentationExtension;
import org.eclipse.debug.ui.IValueDetailListener;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IEditorInput;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;

public final class BreakpointDecompilerModelPresentation implements IDebugModelPresentationExtension {

    private final IDebugModelPresentation delegate;

    public BreakpointDecompilerModelPresentation(IDebugModelPresentation delegate) {
        this.delegate = delegate;
    }

    @Override
    public Image getImage(Object element) {
        return delegate.getImage(element);
    }

    @Override
    public String getText(Object element) {
        return delegate.getText(element);
    }

    @Override
    public void addListener(ILabelProviderListener listener) {
        delegate.addListener(listener);
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }

    @Override
    public boolean isLabelProperty(Object element, String property) {
        return delegate.isLabelProperty(element, property);
    }

    @Override
    public void removeListener(ILabelProviderListener listener) {
        delegate.removeListener(listener);
    }

    @Override
    public void computeDetail(IValue value, IValueDetailListener listener) {
        delegate.computeDetail(value, listener);
    }

    @Override
    public IEditorInput getEditorInput(Object element) {
        IEditorInput defaultEditorInput = delegate.getEditorInput(element);
        Optional<IClassFile> classFile = resolveClassFile(element, defaultEditorInput);
        if (shouldUseDecompilerEditor(classFile)) {
            IEditorInput editorInput = EditorUtility.getEditorInput(classFile.get());
            if (editorInput != null) {
                return editorInput;
            }
        }
        return defaultEditorInput;
    }

    @Override
    public String getEditorId(IEditorInput input, Object element) {
        Optional<IClassFile> classFile = resolveClassFile(element, input);
        if (shouldUseDecompilerEditor(classFile)) {
            return JavaDecompilerPlugin.EDITOR_ID;
        }
        return delegate.getEditorId(input, element);
    }

    @Override
    public void setAttribute(String attribute, Object value) {
        delegate.setAttribute(attribute, value);
    }

    @Override
    public boolean requiresUIThread(Object element) {
        if (delegate instanceof IDebugModelPresentationExtension extension) {
            return extension.requiresUIThread(element);
        }
        return false;
    }

    private Optional<IClassFile> resolveClassFile(Object element, IEditorInput input) {
        if (element instanceof IBreakpoint breakpoint && pointsToJavaSource(breakpoint)) {
            return Optional.empty();
        }
        if (input instanceof IClassFileEditorInput classFileEditorInput) {
            return Optional.of(classFileEditorInput.getClassFile());
        }
        if (element instanceof IBreakpoint breakpoint) {
            return ClassFileResolver.resolveClassFile(breakpoint);
        }
        return Optional.empty();
    }

    private boolean shouldUseDecompilerEditor(Optional<IClassFile> classFile) {
        return classFile.isPresent()
                && (DecompileBufferPopulator.wasDecompiled(classFile.get()) || !ClassFileResolver.hasAttachedSource(classFile.get()));
    }

    private boolean pointsToJavaSource(IBreakpoint breakpoint) {
        IMarker marker = breakpoint.getMarker();
        if (marker == null || !marker.exists()) {
            return false;
        }
        IResource resource = marker.getResource();
        return resource != null && "java".equalsIgnoreCase(resource.getFileExtension()); //$NON-NLS-1$
    }
}
