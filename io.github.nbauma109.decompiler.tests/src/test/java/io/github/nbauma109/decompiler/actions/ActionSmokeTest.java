/*******************************************************************************
 * (C) 2026 Claude (@Claude)
 * (C) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.actions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.core.commands.ExecutionException;
import java.lang.reflect.Proxy;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.junit.Before;
import org.junit.Test;

public class ActionSmokeTest {

    @Before
    public void setUp() {
        Display.getDefault().syncExec(() -> {
            if (PlatformUI.getWorkbench().getActiveWorkbenchWindow() != null
                    && PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage() != null) {
                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
            }
        });
    }

    @Test
    public void sourceCodeActionIsAlwaysCheckedAndSafeWithoutEditor() {
        SourceCodeAction action = new SourceCodeAction();

        assertTrue(action.isChecked());

        action.update();
        action.run();

        assertTrue(action.isChecked());
    }

    @Test
    public void sourceCodeHandlerExecuteReturnsNullWithoutEditor() throws ExecutionException {
        assertNull(new SourceCodeHandler().execute(null));
    }

    @Test
    public void sourceCodeMenuItemActionExposesNullMenusAndSafeNoOps() {
        SourceCodeMenuItemAction action = new SourceCodeMenuItemAction();
        IAction delegateAction = new Action() {
        };

        assertNull(action.getMenu((Control) null));
        assertNull(action.getMenu((Menu) null));

        action.init(null);
        action.selectionChanged(delegateAction, null);
        action.run(delegateAction);
        action.dispose();
    }

    @Test
    public void preferenceMenuItemActionExposesNullMenusAndSafeNoOps() {
        PreferenceMenuItemAction action = new PreferenceMenuItemAction();
        IAction delegateAction = new Action() {
        };

        assertNull(action.getMenu((Control) null));
        assertNull(action.getMenu((Menu) null));

        action.init(null);
        action.selectionChanged(delegateAction, null);
        action.dispose();
    }

    @Test
    public void delegatesIgnoreNonDecompilerEditors() {
        IAction action = new Action() {
        };
        IEditorPart editorPart = (IEditorPart) Proxy.newProxyInstance(IEditorPart.class.getClassLoader(),
                new Class<?>[] { IEditorPart.class }, (proxy, method, args) -> null);

        SourceCodeActionDelegate sourceDelegate = new SourceCodeActionDelegate();
        sourceDelegate.setActiveEditor(action, editorPart);
        assertFalse(action.isChecked());

        ExportSourceActionDelegate exportDelegate = new ExportSourceActionDelegate();
        exportDelegate.setActiveEditor(action, editorPart);
        exportDelegate.run(action);
    }
}
