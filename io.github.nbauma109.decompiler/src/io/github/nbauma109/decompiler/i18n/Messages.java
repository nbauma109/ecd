/*******************************************************************************
 * (C) 2017 cnfree (@cnfree)
 * (C) 2017 Pascal Bihler
 * (C) 2021 Jan S. (@jpstotz)
 * (C) 2025-2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.i18n;

public class Messages {

    private static final String BUNDLE_NAME = "io.github.nbauma109.decompiler.i18n.messages"; //$NON-NLS-1$

    private Messages() {
    }

    public static String getString(String key) {
        return ResourceBundleMessages.getString(BUNDLE_NAME, key, Messages.class);
    }

    /**
     * Gets formatted translation for current local
     *
     * @param key the key
     * @return translated value string
     */
    public static String getFormattedString(String key, Object[] arguments) {
        return ResourceBundleMessages.getFormattedString(BUNDLE_NAME, key, arguments, Messages.class);
    }

}
