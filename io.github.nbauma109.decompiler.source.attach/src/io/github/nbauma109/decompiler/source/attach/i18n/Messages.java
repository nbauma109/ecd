/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.i18n;

import java.io.UnsupportedEncodingException;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class Messages {

    private static final String BUNDLE_NAME = "io.github.nbauma109.decompiler.source.attach.i18n.messages"; //$NON-NLS-1$

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

    private Messages() {
    }

    public static String getString(String key) {

        try {
            String result = RESOURCE_BUNDLE.getString(key);
            try {
                result = new String(result.getBytes("ISO-8859-1"), "utf-8"); //$NON-NLS-1$ //$NON-NLS-2$
            } catch (UnsupportedEncodingException e) {
                return '!' + key + '!';
            }
            return result;
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }

}
