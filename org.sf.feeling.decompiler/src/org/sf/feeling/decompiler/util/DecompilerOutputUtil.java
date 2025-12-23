/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.util;

import org.jd.core.v1.parser.ParseException;
import org.jd.core.v1.util.ParserRealigner;

public class DecompilerOutputUtil {
 
    public static final String NO_LINE_NUMBER = "// Warning: No line numbers available in class file"; //$NON-NLS-1$

    private DecompilerOutputUtil() {
    }

    public static String realign(String input) {
        ParserRealigner parserRealigner = new ParserRealigner();
        try {
            return parserRealigner.realign(input);
        } catch (ParseException e) {
            Logger.error(e);
        }
        return input;
    }
}
