/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnicodeUtil {

    static final Pattern unicodePattern = Pattern.compile("\\\\u([0-9a-zA-Z]{4})"); //$NON-NLS-1$

    public static String decode(String s) {
        Matcher m = unicodePattern.matcher(s);
        StringBuffer sb = new StringBuffer(s.length());
        while (m.find()) {
            m.appendReplacement(sb, Character.toString((char) Integer.parseInt(m.group(1), 16)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
