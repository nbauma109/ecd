/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.actions;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.sf.feeling.decompiler.i18n.Messages;
import org.sf.feeling.decompiler.util.UIUtil;

public class DecompileAction extends Action {

    public DecompileAction() {
        super(Messages.getString("JavaDecompilerActionBarContributor.Action.Decompile")); //$NON-NLS-1$
    }

    public DecompileAction(ImageDescriptor actionIcon) {
        super(Messages.getString("JavaDecompilerActionBarContributor.Action.Decompile"), //$NON-NLS-1$
                actionIcon);
    }

    @Override
    public void run() {
        try {
            new DecompileHandler().execute(null);
        } catch (ExecutionException e) {
        }
    }

    @Override
    public boolean isEnabled() {
        return UIUtil.getActiveEditor() != null || UIUtil.getActiveSelection() != null;
    }
}
