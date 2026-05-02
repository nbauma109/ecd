/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.popup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Link;
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

    /**
     * Fires selection events on all three links to cover the {@code openBrowser},
     * {@code executeP2Update}/{@code executeUpdateManager} catch branches, and the
     * close-link handler.
     */
    @Test
    public void linkHandlersCoverBrowserUpdateAndCloseCodePaths() {
        Display display = Display.getDefault();
        display.syncExec(() -> {
            NewerVersionAvailablePopup popup = new NewerVersionAvailablePopup("v99.0.0"); //$NON-NLS-1$
            popup.create();
            Shell shell = popup.getShell();
            assertNotNull(shell);

            List<Link> links = collectLinks(shell);
            assertEquals("expected 3 links (browser, update, close)", 3, links.size()); //$NON-NLS-1$

            // Fire "Check out in browser" link — exercises openBrowser() catch branch
            // (external browser not available in test env); popup stays open
            fireSelection(links.get(0));
            assertFalse("shell should remain open after browser-link click", shell.isDisposed()); //$NON-NLS-1$

            // Fire "Check for updates" link — exercises executeP2Update and executeUpdateManager
            // catch branches (commands not available in test env); popup stays open
            fireSelection(links.get(1));
            assertFalse("shell should remain open after update-link click", shell.isDisposed()); //$NON-NLS-1$

            // Fire "Close" link — exercises the close() handler; shell becomes disposed
            fireSelection(links.get(2));
            assertTrue("shell should be disposed after close-link click", shell.isDisposed()); //$NON-NLS-1$
        });
    }

    private static void fireSelection(Link link) {
        Event event = new Event();
        event.type = SWT.Selection;
        link.notifyListeners(SWT.Selection, event);
    }

    private static List<Link> collectLinks(Composite parent) {
        List<Link> result = new ArrayList<>();
        for (Control child : parent.getChildren()) {
            if (child instanceof Link) {
                result.add((Link) child);
            } else if (child instanceof Composite) {
                result.addAll(collectLinks((Composite) child));
            }
        }
        return result;
    }
}
