/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.junit.Test;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.i18n.Messages;

public class JavaDecompilerPreferencePageTest {

    private static final String SKIP_LABEL = Messages
            .getString("JavaDecompilerPreferencePage.Label.SkipOpenSourceJars"); //$NON-NLS-1$

    @Test
    public void skipOpenSourceJarsCheckboxReflectsAndStoresThePreference() throws Throwable {
        IPreferenceStore store = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        boolean previous = store.getBoolean(JavaDecompilerPlugin.SKIP_OPEN_SOURCE_JARS);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault());
            try {
                store.setValue(JavaDecompilerPlugin.SKIP_OPEN_SOURCE_JARS, true);
                JavaDecompilerPreferencePage page = new JavaDecompilerPreferencePage();
                page.createControl(shell);
                Button checkbox = findCheckbox(page.getControl());
                assertNotNull("checkbox not found: " + SKIP_LABEL, checkbox); //$NON-NLS-1$
                assertEquals(true, checkbox.getSelection());

                checkbox.setSelection(false);
                checkbox.notifyListeners(SWT.Selection, new Event());
                assertEquals(true, store.getBoolean(JavaDecompilerPlugin.SKIP_OPEN_SOURCE_JARS));
                page.performOk();
                assertEquals(false, store.getBoolean(JavaDecompilerPlugin.SKIP_OPEN_SOURCE_JARS));

                Method performDefaults = JavaDecompilerPreferencePage.class.getDeclaredMethod("performDefaults"); //$NON-NLS-1$
                performDefaults.setAccessible(true);
                performDefaults.invoke(page);
                assertEquals(true, checkbox.getSelection());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                shell.dispose();
                store.setValue(JavaDecompilerPlugin.SKIP_OPEN_SOURCE_JARS, previous);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    private static Button findCheckbox(Control control) {
        if (control instanceof Button button && (button.getStyle() & SWT.CHECK) != 0
                && SKIP_LABEL.equals(button.getText())) {
            return button;
        }
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                Button found = findCheckbox(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
