/*******************************************************************************
 * Copyright (c) 2026 @nbauma109.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.core.IJavaBreakpoint;
import org.eclipse.jdt.debug.core.IJavaStackFrame;

public final class ClassFileResolver {

    private static final String NO_LINE_NUMBER = "// Warning: No line numbers available in class file";

    private ClassFileResolver() {
    }

    public static Optional<IClassFile> resolveClassFile(Object debugArtifact) {
        Optional<IJavaStackFrame> frame = adapt(debugArtifact, IJavaStackFrame.class);
        if (frame.isPresent()) {
            return resolveClassFileByTypeName(getDeclaringTypeName(frame.get()));
        }

        Optional<IJavaBreakpoint> breakpoint = adapt(debugArtifact, IJavaBreakpoint.class);
        if (breakpoint.isPresent()) {
            return resolveClassFileByTypeName(getBreakpointTypeName(breakpoint.get()));
        }

        Optional<IStackFrame> plain = adapt(debugArtifact, IStackFrame.class);
        if (plain.isPresent()) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public static Optional<IClassFile> resolveClassFileByTypeName(String declaringTypeName) {
        if (declaringTypeName == null || declaringTypeName.isBlank()) {
            return Optional.empty();
        }
        List<String> candidates = typeNameCandidates(declaringTypeName);
        return findBinaryClassFileInWorkspace(candidates);
    }

    private static String getDeclaringTypeName(IJavaStackFrame frame) {
        try {
            return frame.getDeclaringTypeName();
        } catch (DebugException e) {
            return null;
        }
    }

    private static String getBreakpointTypeName(IJavaBreakpoint breakpoint) {
        try {
            return breakpoint.getTypeName();
        } catch (CoreException e) {
            return null;
        }
    }

    public static boolean hasRealSource(IClassFile classFile) {
        if (classFile == null || !classFile.exists()) {
            return false;
        }
        try {
            String source = classFile.getSource();
            if (source == null) {
                return false;
            }
            if (source.indexOf(NO_LINE_NUMBER) >= 0) {
                return true;
            }
            return !source.trim().isEmpty();
        } catch (JavaModelException e) {
            return false;
        }
    }

    public static boolean hasAttachedSource(IClassFile classFile) {
        if (classFile == null || !classFile.exists()) {
            return false;
        }
        IJavaElement root = classFile.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
        if (!(root instanceof IPackageFragmentRoot packageRoot)) {
            return false;
        }
        try {
            return packageRoot.getSourceAttachmentPath() != null;
        } catch (JavaModelException e) {
            return false;
        }
    }

    private static Optional<IClassFile> findBinaryClassFileInWorkspace(List<String> candidates) {
        IJavaModel model = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
        try {
            IJavaProject[] projects = model.getJavaProjects();
            for (IJavaProject project : projects) {
                Optional<IClassFile> classFile = findBinaryClassFile(project, candidates);
                if (classFile.isPresent()) {
                    return classFile;
                }
            }
        } catch (JavaModelException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<IClassFile> findBinaryClassFile(IJavaProject project, List<String> candidates) {
        for (String candidate : candidates) {
            Optional<IClassFile> classFile = findBinaryClassFile(project, candidate);
            if (classFile.isPresent()) {
                return classFile;
            }
        }
        return Optional.empty();
    }

    private static Optional<IClassFile> findBinaryClassFile(IJavaProject project, String typeName) {
        try {
            IType type = project.findType(typeName);
            if (type == null || !type.exists() || !type.isBinary()) {
                return Optional.empty();
            }
            IClassFile classFile = type.getClassFile();
            if (classFile == null || !classFile.exists()) {
                return Optional.empty();
            }
            return Optional.of(classFile);
        } catch (JavaModelException e) {
            return Optional.empty();
        }
    }

    private static List<String> typeNameCandidates(String rawTypeName) {
        List<String> list = new ArrayList<>();
        if (rawTypeName == null) {
            return list;
        }
        String name = rawTypeName.trim();
        if (name.isEmpty()) {
            return list;
        }

        addIfMissing(list, name);

        if (name.indexOf('/') >= 0) {
            addIfMissing(list, name.replace('/', '.'));
        }

        if (name.indexOf('$') >= 0) {
            addIfMissing(list, name.replace('$', '.'));
        }

        return list;
    }

    private static void addIfMissing(List<String> list, String value) {
        for (String element : list) {
            if (value.equals(element)) {
                return;
            }
        }
        list.add(value);
    }

    private static <T> Optional<T> adapt(Object element, Class<T> type) {
        if (type.isInstance(element)) {
            return Optional.of(type.cast(element));
        }
        if (element instanceof IAdaptable adaptable) {
            Object adapted = adaptable.getAdapter(type);
            if (type.isInstance(adapted)) {
                return Optional.of(type.cast(adapted));
            }
        }
        return Optional.empty();
    }
}
