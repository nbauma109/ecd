/*******************************************************************************
 * Copyright (c) 2026.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourceBundleMessages {

    private static final Map<BundleCacheKey, ResourceBundle> RESOURCE_BUNDLES = new ConcurrentHashMap<>();

    private ResourceBundleMessages() {
    }

    public static String getString(String bundleName, String key) {
        return getString(bundleName, key, ResourceBundleMessages.class);
    }

    public static String getString(String bundleName, String key, Class<?> scope) {
        try {
            return getBundle(bundleName, scope).getString(key);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }

    public static String getFormattedString(String bundleName, String key, Object[] arguments) {
        return getFormattedString(bundleName, key, arguments, ResourceBundleMessages.class);
    }

    public static String getFormattedString(String bundleName, String key, Object[] arguments, Class<?> scope) {
        return MessageFormat.format(getString(bundleName, key, scope), arguments);
    }

    private static ResourceBundle getBundle(String bundleName, Class<?> scope) {
        BundleCacheKey cacheKey = new BundleCacheKey(bundleName, resolveClassLoader(scope));
        return RESOURCE_BUNDLES.computeIfAbsent(cacheKey,
                key -> ResourceBundle.getBundle(key.bundleName(), Locale.getDefault(), key.classLoader()));
    }

    private static ClassLoader resolveClassLoader(Class<?> scope) {
        ClassLoader classLoader = scope.getClassLoader();
        return classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    private record BundleCacheKey(String bundleName, ClassLoader classLoader) {
    }
}
