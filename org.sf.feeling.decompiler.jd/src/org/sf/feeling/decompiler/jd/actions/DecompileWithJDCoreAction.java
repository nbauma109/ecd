/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.jd.actions;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.action.Action;
import org.sf.feeling.decompiler.jd.JDCoreDecompilerPlugin;
import org.sf.feeling.decompiler.jd.i18n.Messages;
import org.sf.feeling.decompiler.util.UIUtil;

public class DecompileWithJDCoreAction extends Action {

    public DecompileWithJDCoreAction() {
        super(Messages.getString("JavaDecompilerActionBarContributor.Action.DecompileWithJDCore")); //$NON-NLS-1$
        this.setImageDescriptor(JDCoreDecompilerPlugin.getImageDescriptor("icons/jd_16.png")); //$NON-NLS-1$
    }

    @Override
    public void run() {
        try {
            new DecompileWithJDCoreHandler().execute(null);
        } catch (ExecutionException e) {
        }
    }

    @Override
    public boolean isEnabled() {
        return UIUtil.getActiveEditor() != null || UIUtil.getActiveSelection() != null;
    }
}