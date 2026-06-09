/*******************************************************************************
 * © 2026 Nicolas Baumann (@nbauma109)
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
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.junit.Test;

import io.github.nbauma109.decompiler.popup.ConflictingPluginsDialog.ConflictInfo;

public class ConflictingPluginsDialogTest {

    private static final ConflictInfo ECD_CONFLICT =
        new ConflictInfo("Enhanced Class Decompiler", "org.sf.feeling.decompiler", "3.5.2", "org.sf.feeling.decompiler"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    private static final ConflictInfo JDE_CONFLICT =
        new ConflictInfo("JD-Eclipse", "org.jd.ide.eclipse.plugin", "2.0.0", "org.jd.ide.eclipse"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    // -------------------------------------------------------------------------
    // detectConflicts / openIfNeeded (no UI)
    // -------------------------------------------------------------------------

    @Test
    public void detectConflictsOnlyReturnsKnownConflicts() {
        List<ConflictInfo> conflicts = ConflictingPluginsDialog.detectConflicts();
        assertNotNull("detectConflicts() must never return null", conflicts); //$NON-NLS-1$
        for (ConflictInfo ci : conflicts) {
            assertTrue("unexpected conflict detected: " + ci.bundleId(), //$NON-NLS-1$
                "org.sf.feeling.decompiler".equals(ci.bundleId()) //$NON-NLS-1$
                    || "org.jd.ide.eclipse.plugin".equals(ci.bundleId())); //$NON-NLS-1$
        }
    }

    @Test
    public void openIfNeededDoesNothingWhenNoConflictsDetected() {
        Display.getDefault().syncExec(() -> {
            if (!ConflictingPluginsDialog.detectConflicts().isEmpty()) {
                return; // skip: a real modal dialog would block the test thread
            }
            Shell parent = new Shell(Display.getDefault());
            try {
                int shellsBefore = Display.getDefault().getShells().length;
                ConflictingPluginsDialog.openIfNeeded(parent);
                assertEquals("no dialog shell should be opened when there are no conflicts", //$NON-NLS-1$
                    shellsBefore, Display.getDefault().getShells().length);
            } finally {
                parent.dispose();
            }
        });
    }

    // -------------------------------------------------------------------------
    // ConflictInfo record
    // -------------------------------------------------------------------------

    @Test
    public void conflictInfoRecordStoresAllFourFields() {
        ConflictInfo ci = new ConflictInfo("My Plugin", "com.example.plugin", "1.2.3", "com.example"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        assertEquals("My Plugin", ci.name()); //$NON-NLS-1$
        assertEquals("com.example.plugin", ci.bundleId()); //$NON-NLS-1$
        assertEquals("1.2.3", ci.version()); //$NON-NLS-1$
        assertEquals("com.example", ci.p2Prefix()); //$NON-NLS-1$
    }

    @Test
    public void conflictInfoEquality() {
        ConflictInfo a = new ConflictInfo("P", "b", "v", "p"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        ConflictInfo b = new ConflictInfo("P", "b", "v", "p"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        assertEquals(a, b);
    }

    // -------------------------------------------------------------------------
    // Dialog construction
    // -------------------------------------------------------------------------

    @Test
    public void dialogCreatesShellWithoutThrowing() {
        Display.getDefault().syncExec(() -> {
            ConflictingPluginsDialog dialog = new ConflictingPluginsDialog(null, List.of(ECD_CONFLICT));
            dialog.create();
            Shell shell = dialog.getShell();
            assertNotNull("shell should be non-null after create()", shell); //$NON-NLS-1$
            assertFalse("shell should not be disposed after create()", shell.isDisposed()); //$NON-NLS-1$
            dialog.close();
        });
    }

    @Test
    public void tableHasThreeColumnsWithCorrectHeaders() {
        Display.getDefault().syncExec(() -> {
            ConflictingPluginsDialog dialog = new ConflictingPluginsDialog(null, List.of(ECD_CONFLICT));
            dialog.create();
            Table table = findTable(dialog.getShell());
            assertNotNull("a Table widget should exist in the dialog", table); //$NON-NLS-1$
            TableColumn[] columns = table.getColumns();
            assertEquals("table should have 3 columns", 3, columns.length); //$NON-NLS-1$
            assertEquals("Plugin", columns[0].getText()); //$NON-NLS-1$
            assertEquals("Bundle ID", columns[1].getText()); //$NON-NLS-1$
            assertEquals("Version", columns[2].getText()); //$NON-NLS-1$
            dialog.close();
        });
    }

    @Test
    public void tableShowsOneRowPerConflict() {
        Display.getDefault().syncExec(() -> {
            List<ConflictInfo> two = List.of(ECD_CONFLICT, JDE_CONFLICT);
            ConflictingPluginsDialog dialog = new ConflictingPluginsDialog(null, two);
            dialog.create();
            Table table = findTable(dialog.getShell());
            assertNotNull(table);
            assertEquals("table should have one row per conflict", 2, table.getItemCount()); //$NON-NLS-1$
            dialog.close();
        });
    }

    @Test
    public void allTableItemsArePreChecked() {
        Display.getDefault().syncExec(() -> {
            List<ConflictInfo> two = List.of(ECD_CONFLICT, JDE_CONFLICT);
            ConflictingPluginsDialog dialog = new ConflictingPluginsDialog(null, two);
            dialog.create();
            Table table = findTable(dialog.getShell());
            assertNotNull(table);
            for (int i = 0; i < table.getItemCount(); i++) {
                assertTrue("item " + i + " should be pre-checked", table.getItem(i).getChecked()); //$NON-NLS-1$ //$NON-NLS-2$
            }
            dialog.close();
        });
    }

    // -------------------------------------------------------------------------
    // Button bar
    // -------------------------------------------------------------------------

    @Test
    public void dialogHasUninstallAndDismissButtons() {
        Display.getDefault().syncExec(() -> {
            ConflictingPluginsDialog dialog = new ConflictingPluginsDialog(null, List.of(ECD_CONFLICT));
            dialog.create();
            List<Button> buttons = collectButtons(dialog.getShell());
            boolean hasUninstall = buttons.stream().anyMatch(b -> "Uninstall Selected".equals(b.getText())); //$NON-NLS-1$
            boolean hasDismiss   = buttons.stream().anyMatch(b -> "Dismiss".equals(b.getText())); //$NON-NLS-1$
            assertTrue("Uninstall Selected button should be present", hasUninstall); //$NON-NLS-1$
            assertTrue("Dismiss button should be present", hasDismiss); //$NON-NLS-1$
            dialog.close();
        });
    }

    @Test
    public void dismissButtonClosesDialog() {
        Display.getDefault().syncExec(() -> {
            ConflictingPluginsDialog dialog = new ConflictingPluginsDialog(null, List.of(ECD_CONFLICT));
            dialog.create();
            Shell shell = dialog.getShell();
            Button dismiss = collectButtons(shell).stream()
                .filter(b -> "Dismiss".equals(b.getText())) //$NON-NLS-1$
                .findFirst().orElse(null);
            assertNotNull("Dismiss button must exist", dismiss); //$NON-NLS-1$
            dismiss.notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());
            assertTrue("shell should be disposed after Dismiss", shell.isDisposed()); //$NON-NLS-1$
        });
    }

    @Test
    public void uninstallButtonWithNothingCheckedDoesNotCloseDialog() {
        Display.getDefault().syncExec(() -> {
            ConflictingPluginsDialog dialog = new ConflictingPluginsDialog(null, List.of(ECD_CONFLICT));
            dialog.create();
            Shell shell = dialog.getShell();

            // uncheck everything first
            Table table = findTable(shell);
            assertNotNull(table);
            for (int i = 0; i < table.getItemCount(); i++) {
                table.getItem(i).setChecked(false);
            }

            Button uninstall = collectButtons(shell).stream()
                .filter(b -> "Uninstall Selected".equals(b.getText())) //$NON-NLS-1$
                .findFirst().orElse(null);
            assertNotNull("Uninstall Selected button must exist", uninstall); //$NON-NLS-1$
            uninstall.notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());

            assertFalse("dialog should remain open when nothing is checked", shell.isDisposed()); //$NON-NLS-1$
            dialog.close();
        });
    }

    // -------------------------------------------------------------------------
    // Title and message
    // -------------------------------------------------------------------------

    @Test
    public void shellTitleIdentifiesEcdPlusPlus() {
        Display.getDefault().syncExec(() -> {
            ConflictingPluginsDialog dialog = new ConflictingPluginsDialog(null, List.of(ECD_CONFLICT));
            dialog.create();
            assertTrue("shell title should mention ECD++", //$NON-NLS-1$
                dialog.getShell().getText().contains("ECD++")); //$NON-NLS-1$
            dialog.close();
        });
    }

    @Test
    public void uninstallSelectedWithCheckedItemsShowsManualInstructions() {
        Display display = Display.getDefault();
        display.syncExec(() -> {
            ConflictingPluginsDialog dialog = new ConflictingPluginsDialog(null, List.of(ECD_CONFLICT));
            dialog.create();
            Shell shell = dialog.getShell();

            // The MessageDialog opened by showManualInstructions() runs its own event loop.
            // A daemon thread queues an asyncExec to dismiss it so the test does not hang.
            Thread closer = new Thread(() -> {
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                display.asyncExec(() -> {
                    for (Shell s : display.getShells()) {
                        if (!s.isDisposed() && "Manual Uninstall Required".equals(s.getText())) { //$NON-NLS-1$
                            s.close();
                        }
                    }
                });
            });
            closer.setDaemon(true);
            closer.start();

            Button uninstall = collectButtons(shell).stream()
                .filter(b -> "Uninstall Selected".equals(b.getText())) //$NON-NLS-1$
                .findFirst().orElse(null);
            assertNotNull("Uninstall Selected button must exist", uninstall); //$NON-NLS-1$
            uninstall.notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());

            assertFalse("dialog should remain open after failed P2 uninstall", shell.isDisposed()); //$NON-NLS-1$
            dialog.close();
        });
    }

    // -------------------------------------------------------------------------
    // tryP2Uninstall
    // -------------------------------------------------------------------------

    @Test
    public void tryP2UninstallReturnsFalseWhenConflictingPluginsAreNotInProfile() {
        Display.getDefault().syncExec(() -> {
            ConflictInfo fake = new ConflictInfo("Fake Plugin", "com.example.fake", "0.0.0", "com.example.__definitely_not_installed__"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            ConflictingPluginsDialog dialog = new ConflictingPluginsDialog(null, List.of(fake));
            dialog.create();
            assertFalse("should return false when IUs are absent from the P2 profile", //$NON-NLS-1$
                dialog.tryP2Uninstall(List.of(fake)));
            dialog.close();
        });
    }

    @Test
    public void tryP2UninstallReturnsFalseForEmptySelectionList() {
        Display.getDefault().syncExec(() -> {
            ConflictingPluginsDialog dialog = new ConflictingPluginsDialog(null, List.of(ECD_CONFLICT));
            dialog.create();
            assertFalse("should return false when nothing is selected", //$NON-NLS-1$
                dialog.tryP2Uninstall(List.of()));
            dialog.close();
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Table findTable(Composite parent) {
        for (Control child : parent.getChildren()) {
            if (child instanceof Table t) {
                return t;
            }
            if (child instanceof Composite c) {
                Table found = findTable(c);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<Button> collectButtons(Composite parent) {
        List<Button> result = new ArrayList<>();
        for (Control child : parent.getChildren()) {
            if (child instanceof Button b) {
                result.add(b);
            } else if (child instanceof Composite c) {
                result.addAll(collectButtons(c));
            }
        }
        return result;
    }
}
