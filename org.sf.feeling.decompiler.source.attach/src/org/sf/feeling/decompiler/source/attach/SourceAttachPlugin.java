/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach;

import java.io.File;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.sf.feeling.decompiler.source.attach.utils.SourceBindingUtil;
import org.sf.feeling.decompiler.source.attach.utils.SourceConstants;

/**
 * The activator class controls the plug-in life cycle
 */
public class SourceAttachPlugin extends AbstractUIPlugin {

    // The plug-in ID
    public static final String PLUGIN_ID = "org.sf.feeling.decompiler.source.attach"; //$NON-NLS-1$

    // The shared instance
    private static SourceAttachPlugin plugin;

    /**
     * The constructor
     */
    public SourceAttachPlugin() {
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.
     * BundleContext)
     */
    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        SourceBindingUtil.checkSourceBindingConfig();
        flagTempFileDeleteOnExit();
    }

    private void flagTempFileDeleteOnExit() {
        File dir = SourceConstants.getSourceTempDir();
        if (dir.exists()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    children[i].deleteOnExit();
                }
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.
     * BundleContext)
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static SourceAttachPlugin getDefault() {
        return plugin;
    }

}
