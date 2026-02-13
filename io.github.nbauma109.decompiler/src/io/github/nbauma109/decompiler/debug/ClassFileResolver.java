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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;

public final class ClassFileResolver {

    private static final String TYPE_NAME_ATTRIBUTE = "org.eclipse.jdt.debug.core.typeName";

    private static final ConcurrentMap<String, Optional<IClassFile>> CACHE = new ConcurrentHashMap<>();

    private ClassFileResolver() {
    }

    public static Optional<IClassFile> resolveClassFile(Object element) {
        if (element instanceof IClassFile classFile) {
            return Optional.of(classFile);
        }

        Optional<TypeQuery> query = TypeQuery.from(element);
        if (!query.isPresent()) {
            return Optional.empty();
        }

        String cacheKey = query.get().cacheKey();
        Optional<IClassFile> cached = CACHE.get(cacheKey);
        // TODO FIXME Sonar java:S2789 Refactor this to avoid Optionals as values of map entries and take advantage of computeIfAbsent
        if (cached != null) {
            return cached;
        }

        Optional<IClassFile> resolved = findBinaryClassFile(query.get());
        CACHE.put(cacheKey, resolved);
        return resolved;
    }

    private static Optional<IClassFile> findBinaryClassFile(TypeQuery query) {
        List<String> candidates = typeNameCandidates(query.typeName);

        Optional<IJavaProject> primaryProject = javaProjectFromLaunch(query.launch);

        if (primaryProject.isPresent()) {
            Optional<IClassFile> inProject = findInProject(primaryProject.get(), candidates);
            if (inProject.isPresent()) {
                return inProject;
            }
        }

        Optional<IClassFile> inWorkspace = findInWorkspace(candidates);
        if (inWorkspace.isPresent()) {
            return inWorkspace;
        }

        return Optional.empty();
    }

    private static Optional<IClassFile> findInProject(IJavaProject project, List<String> candidates) {
        for (String candidate : candidates) {
            Optional<IClassFile> cf = findTypeAsBinaryClassFile(project, candidate);
            if (cf.isPresent()) {
                return cf;
            }
        }
        return Optional.empty();
    }

    private static Optional<IClassFile> findInWorkspace(List<String> candidates) {
        IJavaModel model = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
        try {
            IJavaProject[] projects = model.getJavaProjects();
            for (IJavaProject project : projects) {
                Optional<IClassFile> cf = findInProject(project, candidates);
                if (cf.isPresent()) {
                    return cf;
                }
            }
        } catch (JavaModelException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<IClassFile> findTypeAsBinaryClassFile(IJavaProject project, String typeName) {
        try {
            IType type = project.findType(typeName);
            if ((type == null) || !type.isBinary()) {
                return Optional.empty();
            }

            IClassFile classFile = type.getClassFile();
            if (classFile == null || !classFile.exists() || hasRealSource(classFile)) {
                return Optional.empty();
            }

            return Optional.of(classFile);
        } catch (JavaModelException e) {
            return Optional.empty();
        }
    }

    private static boolean hasRealSource(IClassFile classFile) {
        try {
            return classFile.getSource() != null;
        } catch (JavaModelException e) {
            return false;
        }
    }

    private static Optional<IJavaProject> javaProjectFromLaunch(ILaunch launch) {
        if (launch == null) {
            return Optional.empty();
        }

        ILaunchConfiguration configuration = launch.getLaunchConfiguration();
        if (configuration == null) {
            return Optional.empty();
        }

        String projectName;
        try {
            projectName = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, (String) null);
        } catch (CoreException e) {
            return Optional.empty();
        }

        if (projectName == null || projectName.trim().isEmpty()) {
            return Optional.empty();
        }

        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (project == null || !project.exists()) {
            return Optional.empty();
        }

        return Optional.of(JavaCore.create(project));
    }

    private static List<String> typeNameCandidates(String typeName) {
        List<String> list = new ArrayList<>();
        if (typeName == null || typeName.trim().isEmpty()) {
            return list;
        }

        list.add(typeName);

        if (typeName.indexOf('$') >= 0) {
            list.add(typeName.replace('$', '.'));

            int firstDollar = typeName.indexOf('$');
            if (firstDollar > 0) {
                list.add(typeName.substring(0, firstDollar));
            }
        }

        return list;
    }

    private record TypeQuery(String typeName, ILaunch launch) {

        private String cacheKey() {
            String launchId = launch == null ? "no-launch" : String.valueOf(System.identityHashCode(launch));
            return launchId + "::" + typeName;
        }

        private static Optional<TypeQuery> from(Object element) {
            if (element instanceof IJavaStackFrame frame) {
                try {
                    return Optional.of(new TypeQuery(frame.getDeclaringTypeName(), frame.getLaunch()));
                } catch (Exception e) {
                    return Optional.empty();
                }
            }

            if (element instanceof IStackFrame stackFrame) {
                return Optional.of(new TypeQuery(null, stackFrame.getLaunch()));
            }

            if (element instanceof IBreakpoint bp) {
                String typeName = typeNameFromBreakpoint(bp);
                return typeName == null ? Optional.<TypeQuery>empty() : Optional.of(new TypeQuery(typeName, null));
            }

            return Optional.empty();
        }

        private static String typeNameFromBreakpoint(IBreakpoint breakpoint) {
            IMarker marker = breakpoint.getMarker();
            if (marker == null) {
                return null;
            }
            return marker.getAttribute(TYPE_NAME_ATTRIBUTE, (String) null);
        }
    }
}
