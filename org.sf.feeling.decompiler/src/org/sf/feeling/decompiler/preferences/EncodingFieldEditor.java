/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.preferences;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ide.IDEEncoding;
import org.eclipse.ui.ide.dialogs.AbstractEncodingFieldEditor;

public final class EncodingFieldEditor extends AbstractEncodingFieldEditor {

	public EncodingFieldEditor(String name, String labelText, String groupTitle, Composite parent) {
		init(name, labelText);
		setGroupTitle(groupTitle);
		createControl(parent);
	}

	public EncodingFieldEditor(String name, String labelText, Composite parent) {
		init(name, labelText);
		createControl(parent);
	}

	@Override
	protected String getStoredValue() {
		return getPreferenceStore().getString(getPreferenceName());
	}

	@Override
	protected void doStore() {
		String encoding = getSelectedEncoding();

		if (hasSameEncoding(encoding)) {
			return;
		}

		IDEEncoding.addIDEEncoding(encoding);

		if (encoding.equals(getDefaultEnc())) {
			getPreferenceStore().setToDefault(getPreferenceName());
		} else {
			getPreferenceStore().setValue(getPreferenceName(), encoding);
		}
	}

	@Override
	public void setPreferenceStore(IPreferenceStore store) {
		if (store != null) {
			super.setPreferenceStore(store);
		}
	}
}
