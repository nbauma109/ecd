/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.i18n;

import io.github.nbauma109.decompiler.i18n.ResourceBundleMessages;

public class Messages {

    private static final String BUNDLE_NAME = "io.github.nbauma109.decompiler.source.attach.i18n.messages"; //$NON-NLS-1$

    private Messages() {
    }

    public static String getString(String key) {
        return ResourceBundleMessages.getString(BUNDLE_NAME, key, Messages.class);
    }

}
