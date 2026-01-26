/*******************************************************************************
 * Copyright (c) 2019 ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.util;

import java.lang.reflect.Method;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.compiler.env.IBinaryType;
import org.eclipse.jdt.internal.core.NamedMember;
import org.eclipse.jdt.internal.core.SourceMapper;

public class SourceMapperUtil {
    private static final String MAP_SOURCE_METHOD_NAME = "mapSource"; //$NON-NLS-1$

    public static void mapSource(SourceMapper sourceMapper, IType type, char[] source, IBinaryType info) {
        mapSource(sourceMapper, type, source, info, null);
    }

    public static void mapSource(SourceMapper sourceMapper, IType type, char[] source, IBinaryType info,
            IJavaElement elementToFind) {
        Method mapSourceMethod = getMapSourceMethod(sourceMapper);

        // API changed with Java 9 support (#daa227e4f5b7af888572a286c4f973b7a167ff2e)
        if (mapSourceMethod == null) {
            mapSourceMethod = getLegacyMapSourceMethod(sourceMapper);
        }

        if (mapSourceMethod == null) {
            throw new IllegalStateException("Unable to invoke mapSource method on sourceMapper"); //$NON-NLS-1$
        }
        Object[] parameters = { type, source, info, elementToFind };
        ReflectionUtils.invokeMethod(mapSourceMethod, sourceMapper, parameters);
    }

    private static Method getMapSourceMethod(SourceMapper sourceMapper) {
        return ReflectionUtils.getDeclaredMethod(sourceMapper, MAP_SOURCE_METHOD_NAME,
                new Class[] { NamedMember.class, char[].class, IBinaryType.class, IJavaElement.class });
    }

    private static Method getLegacyMapSourceMethod(SourceMapper sourceMapper) {
        return ReflectionUtils.getDeclaredMethod(sourceMapper, MAP_SOURCE_METHOD_NAME,
                new Class[] { IType.class, char[].class, IBinaryType.class, IJavaElement.class });
    }
}
