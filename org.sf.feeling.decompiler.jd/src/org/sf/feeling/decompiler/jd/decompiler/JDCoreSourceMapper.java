/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.jd.decompiler;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.core.runtime.Path;

import jd.ide.eclipse.editors.JDSourceMapper;

public class JDCoreSourceMapper extends JDSourceMapper {

    public JDCoreSourceMapper() {
        super(new File("."), new Path("."), "", Collections.<String, String>emptyMap()); //$NON-NLS-1$ //$NON-NLS-2$
        originalDecompiler = new JDCoreDecompiler(this);
    }

    @Override
    protected void printDecompileReport(StringBuffer source, String fileLocation, Collection<Exception> exceptions,
            long decompilationTime) {
        String location = "\tDecompiled from: " //$NON-NLS-1$
                + fileLocation;
        source.append("\n\n/*"); //$NON-NLS-1$
        source.append("\n\tDECOMPILATION REPORT\n\n"); //$NON-NLS-1$
        source.append(location).append("\n"); //$NON-NLS-1$
        source.append("\tTotal time: ") //$NON-NLS-1$
                .append(decompilationTime).append(" ms\n"); //$NON-NLS-1$
        source.append("\t" //$NON-NLS-1$
                + originalDecompiler.getLog().replace("\t", "") //$NON-NLS-1$ //$NON-NLS-2$
                        .replaceAll("\n\\s*", "\n\t")); //$NON-NLS-1$ //$NON-NLS-2$
        exceptions.addAll(originalDecompiler.getExceptions());
        try {
            source.append("\n\tDecompiled with JD-Core " + printer.getVersion()); //$NON-NLS-1$
        } catch (IOException e) {
            source.append("\n\tDecompiled with JD-Core SNAPSHOT"); //$NON-NLS-1$
            exceptions.add(e);
        }
        logExceptions(exceptions, source);
        source.append("\n*/"); //$NON-NLS-1$
    }

}
