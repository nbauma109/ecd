/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach;

import java.io.File;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import io.github.nbauma109.decompiler.extension.DecompilerAdapterManager;
import io.github.nbauma109.decompiler.source.attach.attacher.SourceAttacher;
import io.github.nbauma109.decompiler.source.attach.utils.SourceBindingUtil;
import io.github.nbauma109.decompiler.source.attach.utils.SourceConstants;

/**
 * The activator class controls the plug-in life cycle
 */
public class SourceAttachPlugin extends AbstractUIPlugin {

    private static SourceAttachPlugin plugin;

    public SourceAttachPlugin() {
        plugin = this;
    }

    public static SourceAttachPlugin getDefault() {
        return plugin;
    }

    public SourceAttacher getSourceAttacher() {
        return (SourceAttacher) DecompilerAdapterManager.getAdapter(this, SourceAttacher.class);
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
        SourceBindingUtil.checkSourceBindingConfig();
        flagTempFileDeleteOnExit();
    }

    private void flagTempFileDeleteOnExit() {
        File dir = SourceConstants.getSourceTempDir();
        if (dir.exists()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    child.deleteOnExit();
                }
            }
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        super.stop(context);
        plugin = null;
    }
}
