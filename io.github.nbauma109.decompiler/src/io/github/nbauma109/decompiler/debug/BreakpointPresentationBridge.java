/*******************************************************************************
 * Copyright (c) 2026 @nbauma109.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.debug;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.osgi.framework.Bundle;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;

public final class BreakpointPresentationBridge {

    private static final String JAVA_DEBUG_MODEL_ID = "org.eclipse.jdt.debug"; //$NON-NLS-1$
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private BreakpointPresentationBridge() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            Bundle debugUiBundle = Platform.getBundle("org.eclipse.debug.ui"); //$NON-NLS-1$
            if (debugUiBundle == null) {
                return;
            }
            Class<?> actionClass = debugUiBundle.loadClass(
                    "org.eclipse.debug.internal.ui.actions.breakpoints.OpenBreakpointMarkerAction"); //$NON-NLS-1$
            Field presentationField = actionClass.getDeclaredField("fgPresentation"); //$NON-NLS-1$
            presentationField.setAccessible(true);
            Object presentation = presentationField.get(null);
            if (presentation == null) {
                return;
            }

            Field labelsField = presentation.getClass().getDeclaredField("fLabelProviders"); //$NON-NLS-1$
            labelsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, IDebugModelPresentation> labels = (Map<String, IDebugModelPresentation>) labelsField.get(presentation);
            IDebugModelPresentation javaPresentation = labels.get(JAVA_DEBUG_MODEL_ID);
            if (javaPresentation == null || javaPresentation instanceof BreakpointDecompilerModelPresentation) {
                return;
            }

            labels.put(JAVA_DEBUG_MODEL_ID, new BreakpointDecompilerModelPresentation(javaPresentation));
        } catch (ReflectiveOperationException | RuntimeException e) {
            JavaDecompilerPlugin.logError(e, "Unable to install breakpoint presentation bridge."); //$NON-NLS-1$
        }
    }
}
