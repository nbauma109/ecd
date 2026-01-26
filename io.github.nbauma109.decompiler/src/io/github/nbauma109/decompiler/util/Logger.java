/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.util;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;

public class Logger {

    public static void debug(String message, Throwable throwable) {
        try {
            if (message != null) {
                System.err.println(message);
            }
            if (throwable != null) {
                throwable.printStackTrace();
            }
        } catch (Exception localException) {
            if (message != null) {
                System.err.println(message);
            }
            if (throwable != null) {
                throwable.printStackTrace();
            }
        }
    }

    public static void info(String message) {
        if (message != null) {
            JavaDecompilerPlugin.logInfo(message);
        }
    }

    public static void warn(String message) {
        if (message != null) {
            JavaDecompilerPlugin.logWarn(message);
        }
    }

    public static void error(String message) {
        if (message != null) {
            JavaDecompilerPlugin.logError(null, message);
        }
    }

    public static void error(String message, Throwable t) {
        if (message != null) {
            JavaDecompilerPlugin.logError(t, message);
        }
    }

    public static void debug(Throwable throwable) {
        throwable.printStackTrace();
    }

    public static void error(Throwable t) {
        if (t != null) {
            JavaDecompilerPlugin.logError(t, t.getMessage());
        }
    }
}