/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.editor.DecompilerType;
import org.sf.feeling.decompiler.editor.IDecompilerDescriptor;
import org.sf.feeling.decompiler.i18n.Messages;

public class JavaDecompilerPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	private CheckFieldEditor optionLncEditor;
	private CheckFieldEditor alignEditor;
	private CheckFieldEditor eclipseFormatter;
	private CheckFieldEditor eclipseSorter;
	private EncodingFieldEditor encodingEditor;
	private Group basicGroup;
	private Group formatGroup;
	private Group debugGroup;
	private StringChoiceFieldEditor defaultDecompiler;
	private CheckFieldEditor showReport;

	public JavaDecompilerPreferencePage() {
		super(FieldEditorPreferencePage.GRID);
		setPreferenceStore(JavaDecompilerPlugin.getDefault().getPreferenceStore());
	}

	@Override
	protected void createFieldEditors() {

		defaultDecompiler = new StringChoiceFieldEditor(JavaDecompilerPlugin.DECOMPILER_TYPE,
				Messages.getString("JavaDecompilerPreferencePage.Label.DefaultClassDecompiler"), //$NON-NLS-1$
				getFieldEditorParent()) {

			@Override
			protected void doFillIntoGrid(Composite parent, int numColumns) {
				super.doFillIntoGrid(parent, numColumns);
				GridData gd = (GridData) getControl().getLayoutData();
				gd.widthHint = 175;
				gd.grabExcessHorizontalSpace = false;
				gd.horizontalAlignment = SWT.BEGINNING;
				getControl().setLayoutData(gd);
			}
		};

		String fernLabel = Messages.getString("JavaDecompilerPreferencePage.Decompiler.FernFlower"); //$NON-NLS-1$

		boolean isAddFernFlower = false;

		for (int i = 0; i < DecompilerType.getDecompilerTypes().length; i++) {
			IDecompilerDescriptor descriptor = JavaDecompilerPlugin.getDefault()
					.getDecompilerDescriptor(DecompilerType.getDecompilerTypes()[i]);
			String label = descriptor.getDecompilerPreferenceLabel().trim();
			if (label.compareToIgnoreCase(fernLabel) > 0 && !isAddFernFlower) {
				defaultDecompiler.addItem(DecompilerType.FernFlower, fernLabel, DecompilerType.FernFlower);
				isAddFernFlower = true;
			}
			defaultDecompiler.addItem(descriptor.getDecompilerType(), label, descriptor.getDecompilerType());
		}

		if (!isAddFernFlower) {
			defaultDecompiler.addItem(DecompilerType.FernFlower, fernLabel, DecompilerType.FernFlower);
		}

		addField(defaultDecompiler);

		basicGroup = new Group(getFieldEditorParent(), SWT.NONE);
		basicGroup.setText(Messages.getString("JavaDecompilerPreferencePage.Label.DecompilerSettings")); //$NON-NLS-1$
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = defaultDecompiler.getNumberOfControls();
		basicGroup.setLayoutData(gd);

		BooleanFieldEditor reusebuf = new BooleanFieldEditor(JavaDecompilerPlugin.REUSE_BUFFER,
				Messages.getString("JavaDecompilerPreferencePage.Label.ReuseCodeBuffer"), //$NON-NLS-1$
				basicGroup);
		addField(reusebuf);

		if (JavaDecompilerPlugin.getDefault().enableAttachSourceSetting()) {
			createAttachSourceFieldEditor(basicGroup);
			createWaitForSourcesFieldEditor(basicGroup);
		}

		BooleanFieldEditor alwaysUse = new BooleanFieldEditor(JavaDecompilerPlugin.IGNORE_EXISTING,
				Messages.getString("JavaDecompilerPreferencePage.Label.IgnoreExistSource"), //$NON-NLS-1$
				basicGroup);
		addField(alwaysUse);

		showReport = new CheckFieldEditor(JavaDecompilerPlugin.PREF_DISPLAY_METADATA,
				Messages.getString("JavaDecompilerPreferencePage.Label.ShowDecompilerReport"), //$NON-NLS-1$
				basicGroup);
		addField(showReport);

		GridLayout layout = (GridLayout) basicGroup.getLayout();
		layout.marginWidth = layout.marginHeight = 5;
		basicGroup.layout();

		formatGroup = new Group(getFieldEditorParent(), SWT.NONE);
		formatGroup.setText(Messages.getString("JavaDecompilerPreferencePage.Label.FormatSettings")); //$NON-NLS-1$
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = defaultDecompiler.getNumberOfControls();
		formatGroup.setLayoutData(gd);

		eclipseFormatter = new CheckFieldEditor(JavaDecompilerPlugin.USE_ECLIPSE_FORMATTER,
				Messages.getString("JavaDecompilerPreferencePage.Label.UseEclipseFormat"), //$NON-NLS-1$
				formatGroup);
		addField(eclipseFormatter);

		eclipseSorter = new CheckFieldEditor(JavaDecompilerPlugin.USE_ECLIPSE_SORTER,
				Messages.getString("JavaDecompilerPreferencePage.Lable.UseEclipseSorter"), //$NON-NLS-1$
				formatGroup);
		addField(eclipseSorter);

		layout = (GridLayout) formatGroup.getLayout();
		layout.marginWidth = layout.marginHeight = 5;
		formatGroup.layout();

		debugGroup = new Group(getFieldEditorParent(), SWT.NONE);
		debugGroup.setText(Messages.getString("JavaDecompilerPreferencePage.Label.DebugSettings")); //$NON-NLS-1$
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = defaultDecompiler.getNumberOfControls();
		debugGroup.setLayoutData(gd);

		optionLncEditor = new CheckFieldEditor(JavaDecompilerPlugin.PREF_DISPLAY_LINE_NUMBERS,
				Messages.getString("JavaDecompilerPreferencePage.Label.OutputLineNumber"), //$NON-NLS-1$
				debugGroup);
		addField(optionLncEditor);

		alignEditor = new CheckFieldEditor(JavaDecompilerPlugin.ALIGN,
				Messages.getString("JavaDecompilerPreferencePage.Label.AlignCode"), //$NON-NLS-1$
				debugGroup);
		addField(alignEditor);

		layout = (GridLayout) debugGroup.getLayout();
		layout.marginWidth = layout.marginHeight = 5;
		debugGroup.layout();

		createEncodingFieldEditor(getFieldEditorParent());

		Group startupGroup = new Group(getFieldEditorParent(), SWT.NONE);
		startupGroup.setText(Messages.getString("JavaDecompilerPreferencePage.Label.Startup")); //$NON-NLS-1$ );
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = defaultDecompiler.getNumberOfControls();
		startupGroup.setLayoutData(gd);

		CheckFieldEditor defaultViewerEditor = new CheckFieldEditor(JavaDecompilerPlugin.DEFAULT_EDITOR,
				Messages.getString("JavaDecompilerPreferencePage.Label.DefaultEditor"), //$NON-NLS-1$
				startupGroup);
		addField(defaultViewerEditor);

		layout = (GridLayout) startupGroup.getLayout();
		layout.marginWidth = layout.marginHeight = 5;
		startupGroup.layout();

		getFieldEditorParent().layout();
	}

	private void createAttachSourceFieldEditor(Group group) {
		CheckFieldEditor attachSource = new CheckFieldEditor(JavaDecompilerPlugin.ATTACH_SOURCE,
				Messages.getString("JavaDecompilerPreferencePage.Label.Attach.Source"), //$NON-NLS-1$
				group);
		addField(attachSource);
	}

	private void createWaitForSourcesFieldEditor(Group group) {
		CheckFieldEditor waitForSources = new CheckFieldEditor(JavaDecompilerPlugin.WAIT_FOR_SOURCES,
				Messages.getString("JavaDecompilerPreferencePage.Label.Attach.WaitForSources"), //$NON-NLS-1$
				group);
		addField(waitForSources);
	}

	private void createEncodingFieldEditor(Composite composite) {
		Group encodingGroup = new Group(composite, SWT.NONE);
		encodingGroup.setText(Messages.getString("JavaDecompilerPreferencePage.Label.Export.Encoding")); //$NON-NLS-1$
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = defaultDecompiler.getNumberOfControls();
		encodingGroup.setLayoutData(gd);

		encodingEditor = new EncodingFieldEditor(JavaDecompilerPlugin.EXPORT_ENCODING, // $NON-NLS-1$
				"", //$NON-NLS-1$
				null, encodingGroup);
		addField(encodingEditor);

		GridLayout layout = (GridLayout) encodingGroup.getLayout();
		layout.marginWidth = layout.marginHeight = 5;
		encodingGroup.layout();
	}

	@Override
	public void init(IWorkbench arg0) {
		// TODO document why this method is empty
	}

	@Override
	protected void initialize() {
		super.initialize();
		boolean enabled = getPreferenceStore().getBoolean(JavaDecompilerPlugin.PREF_DISPLAY_LINE_NUMBERS);
		alignEditor.setEnabled(enabled, debugGroup);

		String defaultEncoding = JavaDecompilerPlugin.getDefault().getDefaultExportEncoding();
		String encoding = getPreferenceStore().getString(JavaDecompilerPlugin.EXPORT_ENCODING);
		encodingEditor.setPreferenceStore(getPreferenceStore());
		encodingEditor.load();

		if (encoding == null || encoding.equals(defaultEncoding) || encoding.length() == 0)
			encodingEditor.loadDefault();
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();
		boolean enabled = Boolean.valueOf(optionLncEditor.getBooleanValue()).equals(Boolean.TRUE);
		optionLncEditor.setEnabled(true, debugGroup);
		alignEditor.setEnabled(enabled, debugGroup);

		encodingEditor.getPreferenceStore().setValue(encodingEditor.getPreferenceName(),
				JavaDecompilerPlugin.getDefault().getDefaultExportEncoding());
		encodingEditor.load();
		encodingEditor.loadDefault();
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getSource() == optionLncEditor) {
			boolean enabled = event.getNewValue().equals(Boolean.TRUE);
			alignEditor.setEnabled(enabled, debugGroup);
			if (enabled) {
				eclipseFormatter.getChangeControl(formatGroup).setSelection(false);
				eclipseSorter.getChangeControl(formatGroup).setSelection(false);
				eclipseFormatter.handleSelection(formatGroup);
				eclipseSorter.handleSelection(formatGroup);
			}
			if (!enabled) {
				alignEditor.getChangeControl(debugGroup).setSelection(false);
				alignEditor.handleSelection(debugGroup);
			}
		}
		if (event.getSource() == alignEditor) {
			boolean enabled = event.getNewValue().equals(Boolean.TRUE);
			if (enabled) {
				eclipseFormatter.getChangeControl(formatGroup).setSelection(false);
				eclipseSorter.getChangeControl(formatGroup).setSelection(false);
				eclipseFormatter.handleSelection(formatGroup);
				eclipseSorter.handleSelection(formatGroup);
			}
		}
		if (event.getSource() == eclipseFormatter || event.getSource() == eclipseSorter) {
			boolean enabled = event.getNewValue().equals(Boolean.TRUE);
			if (enabled) {
				alignEditor.getChangeControl(debugGroup).setSelection(false);
				optionLncEditor.getChangeControl(debugGroup).setSelection(false);
				alignEditor.setEnabled(!enabled, debugGroup);
				alignEditor.handleSelection(debugGroup);
				optionLncEditor.handleSelection(debugGroup);
			}
		}
		super.propertyChange(event);
	}

}
