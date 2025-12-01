package org.sf.feeling.decompiler.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

public class CheckFieldEditor extends BooleanFieldEditor {

    public CheckFieldEditor(String name, String label, Composite parent) {
        super(name, label, parent);
    }

    @Override
    protected void fireStateChanged(String property, boolean oldValue, boolean newValue) {
        fireValueChanged(property, oldValue, newValue);
    }

    public void handleSelection(Composite parent) {
        boolean isSelected = getChangeControl(parent).getSelection();
        valueChanged(false, isSelected);
    }

    @Override
    protected void valueChanged(boolean oldValue, boolean newValue) {
        setPresentsDefaultValue(false);
        fireStateChanged(VALUE, oldValue, newValue);
    }

    @Override
    public Button getChangeControl(Composite parent) {
        return super.getChangeControl(parent);
    }
}
