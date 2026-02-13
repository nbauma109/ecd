/*******************************************************************************
 * Copyright (c) 2026 @nbauma109.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.debug;

import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchListener;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector;

public final class DecompilerSourceLookupBridge {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private DecompilerSourceLookupBridge() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
        launchManager.addLaunchListener(new BridgeLaunchListener());

        ILaunch[] launches = launchManager.getLaunches();
        for (ILaunch element : launches) {
            installOnLaunch(element);
        }
    }

    private static void installOnLaunch(ILaunch launch) {
        if (launch == null) {
            return;
        }

        ISourceLocator locator = launch.getSourceLocator();
        if (!(locator instanceof ISourceLookupDirector) || (locator instanceof DecompilerSourceLookupDirector)) {
            return;
        }

        launch.setSourceLocator(new DecompilerSourceLookupDirector((ISourceLookupDirector) locator));
    }

    private static final class BridgeLaunchListener implements ILaunchListener {
        @Override
        public void launchAdded(ILaunch launch) {
            installOnLaunch(launch);
        }

        @Override
        public void launchChanged(ILaunch launch) {
            installOnLaunch(launch);
        }

        @Override
        public void launchRemoved(ILaunch launch) {
        }
    }
}
