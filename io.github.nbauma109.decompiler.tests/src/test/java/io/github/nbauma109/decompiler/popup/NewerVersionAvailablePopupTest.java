/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.popup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.Test;

public class NewerVersionAvailablePopupTest {

    /**
     * Verifies that the popup can be created (all SWT widgets instantiated) and
     * disposed without throwing. This exercises the constructor,
     * {@code configureShell()}, {@code createContents()}, and
     * {@code initializeBounds()} code paths.
     */
    @Test
    public void popupCreatesAllWidgetsAndDisposesCleanly() {
        Display display = Display.getDefault();
        display.syncExec(() -> {
            NewerVersionAvailablePopup popup = new NewerVersionAvailablePopup("v99.0.0"); //$NON-NLS-1$
            // create() builds the shell + contents without opening/blocking
            popup.create();
            Shell shell = popup.getShell();
            assertNotNull("popup shell should be non-null after create()", shell); //$NON-NLS-1$
            assertFalse("popup shell should not be disposed after create()", shell.isDisposed()); //$NON-NLS-1$
            popup.close();
        });
    }

    /**
     * Verifies that two separate popup instances can coexist and both be disposed,
     * exercising the full creation path with different version strings.
     */
    @Test
    public void multiplePopupInstancesCanBeCreatedAndDisposed() {
        Display display = Display.getDefault();
        display.syncExec(() -> {
            NewerVersionAvailablePopup popup1 = new NewerVersionAvailablePopup("v1.0.0"); //$NON-NLS-1$
            NewerVersionAvailablePopup popup2 = new NewerVersionAvailablePopup("2026.5.0"); //$NON-NLS-1$
            popup1.create();
            popup2.create();
            assertNotNull("first popup shell should be non-null", popup1.getShell()); //$NON-NLS-1$
            assertNotNull("second popup shell should be non-null", popup2.getShell()); //$NON-NLS-1$
            assertFalse("first popup shell should not be disposed", popup1.getShell().isDisposed()); //$NON-NLS-1$
            assertFalse("second popup shell should not be disposed", popup2.getShell().isDisposed()); //$NON-NLS-1$
            popup1.close();
            popup2.close();
        });
    }
}
