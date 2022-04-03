/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.preferences;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.i18n.Messages;

public class NexusPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public NexusPreferencePage() {
		super(FieldEditorPreferencePage.GRID);
		setPreferenceStore(JavaDecompilerPlugin.getDefault().getPreferenceStore());
	}

	@Override
	protected void createFieldEditors() {
		Group basicGroup = new Group(getFieldEditorParent(), SWT.NONE);
		basicGroup.setText(Messages.getString("NexusPreferencePage.Label.NexusSettings")); //$NON-NLS-1$
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		basicGroup.setLayoutData(gd);

		StringFieldEditor nexusUrl = new StringFieldEditor(JavaDecompilerPlugin.NEXUS_URL,
				Messages.getString("NexusPreferencePage.Label.NexusUrl"), //$NON-NLS-1$
				basicGroup);

		StringFieldEditor nexusUser = new StringFieldEditor(JavaDecompilerPlugin.NEXUS_USER,
				Messages.getString("NexusPreferencePage.Label.NexusUser"), //$NON-NLS-1$
				basicGroup);

		StringFieldEditor nexusPassword = new StringFieldEditor(JavaDecompilerPlugin.NEXUS_PASSWORD,
				Messages.getString("NexusPreferencePage.Label.NexusPassword"), //$NON-NLS-1$
				basicGroup) {

			@Override
			protected void doFillIntoGrid(Composite parent, int numColumns) {
				super.doFillIntoGrid(parent, numColumns);

				getTextControl().setEchoChar('*');
			}

		};

		addField(nexusUrl);
		addField(nexusUser);
		addField(nexusPassword);

		GridLayout layout = (GridLayout) basicGroup.getLayout();
		layout.marginWidth = layout.marginHeight = 5;
		basicGroup.layout();

		getFieldEditorParent().layout();
	}

	@Override
	public void init(IWorkbench arg0) {
		// TODO document why this method is empty
	}
}
