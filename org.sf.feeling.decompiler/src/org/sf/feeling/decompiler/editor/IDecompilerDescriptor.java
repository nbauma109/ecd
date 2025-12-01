/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.editor;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;

public interface IDecompilerDescriptor {

    String getDecompilerType();

    String getDecompilerPreferenceLabel();

    IDecompiler getDecompiler();

    BaseDecompilerSourceMapper getDecompilerSourceMapper();

    Action getDecompileAction();

    boolean isEnabled();

    boolean isDefault();

    ImageDescriptor getDecompilerIcon();
}
