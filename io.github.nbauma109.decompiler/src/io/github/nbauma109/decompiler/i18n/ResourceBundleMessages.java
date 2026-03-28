/*******************************************************************************
 * Copyright (c) 2026.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.i18n;

import java.text.MessageFormat;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourceBundleMessages {

    private static final Map<String, ResourceBundle> RESOURCE_BUNDLES = new ConcurrentHashMap<>();

    private ResourceBundleMessages() {
    }

    public static String getString(String bundleName, String key) {
        try {
            return getBundle(bundleName).getString(key);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }

    public static String getFormattedString(String bundleName, String key, Object[] arguments) {
        return MessageFormat.format(getString(bundleName, key), arguments);
    }

    private static ResourceBundle getBundle(String bundleName) {
        return RESOURCE_BUNDLES.computeIfAbsent(bundleName, ResourceBundle::getBundle);
    }
}
