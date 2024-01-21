/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.vineflower.actions;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.action.Action;
import org.sf.feeling.decompiler.vineflower.VineflowerDecompilerPlugin;
import org.sf.feeling.decompiler.vineflower.i18n.Messages;
import org.sf.feeling.decompiler.util.UIUtil;

public class DecompileWithVineflowerCoreAction extends Action {

	public DecompileWithVineflowerCoreAction() {
		super(Messages.getString("JavaDecompilerActionBarContributor.Action.DecompileWithVineflower")); //$NON-NLS-1$
		this.setImageDescriptor(VineflowerDecompilerPlugin.getImageDescriptor("icons/vineflower_16.png")); //$NON-NLS-1$
	}

	@Override
	public void run() {
		try {
			new DecompileWithVineflowerCoreHandler().execute(null);
		} catch (ExecutionException e) {
		}
	}

	@Override
	public boolean isEnabled() {
		return UIUtil.getActiveEditor() != null || UIUtil.getActiveSelection() != null;
	}
}