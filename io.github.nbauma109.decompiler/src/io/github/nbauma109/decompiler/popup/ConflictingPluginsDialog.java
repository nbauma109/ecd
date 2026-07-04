/*******************************************************************************
 * © 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.popup;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.engine.IProfileRegistry;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.operations.ProvisioningJob;
import org.eclipse.equinox.p2.operations.ProvisioningSession;
import org.eclipse.equinox.p2.operations.UninstallOperation;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

import io.github.nbauma109.decompiler.util.Logger;

public class ConflictingPluginsDialog extends TitleAreaDialog {

    private static final int UNINSTALL_ID = IDialogConstants.CLIENT_ID + 1;

    // columns: display name | OSGi bundle symbolic name | P2 IU prefix
    private static final String[][] KNOWN_CONFLICTS = {
            {"Enhanced Class Decompiler", "org.sf.feeling.decompiler",  "org.sf.feeling.decompiler"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            {"JD-Eclipse",                "org.jd.ide.eclipse.plugin",  "org.jd.ide.eclipse"},        //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    };

    public record ConflictInfo(String name, String bundleId, String version, String p2Prefix) {}

    private final List<ConflictInfo> detectedConflicts;
    private CheckboxTableViewer tableViewer;

    public ConflictingPluginsDialog(Shell parentShell, List<ConflictInfo> conflicts) {
        super(parentShell);
        this.detectedConflicts = conflicts;
        setHelpAvailable(false);
    }

    public static List<ConflictInfo> detectConflicts() {
        List<ConflictInfo> conflicts = new ArrayList<>();
        for (String[] entry : KNOWN_CONFLICTS) {
            Bundle bundle = Platform.getBundle(entry[1]);
            if (bundle != null) {
                conflicts.add(new ConflictInfo(entry[0], entry[1], bundle.getVersion().toString(), entry[2]));
            }
        }
        return conflicts;
    }

    public static void openIfNeeded(Shell shell) {
        List<ConflictInfo> conflicts = detectConflicts();
        if (conflicts.isEmpty()) {
            return;
        }
        Shell parent = (shell != null && !shell.isDisposed()) ? shell : Display.getDefault().getActiveShell();
        new ConflictingPluginsDialog(parent, conflicts).open();
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("ECD++ - Conflicting Plugins Detected"); //$NON-NLS-1$
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        setTitle("Conflicting plugins detected"); //$NON-NLS-1$
        setMessage("The following plugins conflict with ECD++ and should be uninstalled to avoid issues."); //$NON-NLS-1$

        Composite container = new Composite(area, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 12;
        layout.marginHeight = 8;
        container.setLayout(layout);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        tableViewer = CheckboxTableViewer.newCheckList(container, SWT.BORDER | SWT.FULL_SELECTION);
        Table table = tableViewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.heightHint = 120;
        table.setLayoutData(tableData);

        TableViewerColumn nameCol = new TableViewerColumn(tableViewer, SWT.NONE);
        nameCol.getColumn().setText("Plugin"); //$NON-NLS-1$
        nameCol.getColumn().setWidth(240);
        nameCol.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return element instanceof ConflictInfo ci ? ci.name() : ""; //$NON-NLS-1$
            }
        });

        TableViewerColumn bundleCol = new TableViewerColumn(tableViewer, SWT.NONE);
        bundleCol.getColumn().setText("Bundle ID"); //$NON-NLS-1$
        bundleCol.getColumn().setWidth(220);
        bundleCol.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return element instanceof ConflictInfo ci ? ci.bundleId() : ""; //$NON-NLS-1$
            }
        });

        TableViewerColumn versionCol = new TableViewerColumn(tableViewer, SWT.NONE);
        versionCol.getColumn().setText("Version"); //$NON-NLS-1$
        versionCol.getColumn().setWidth(100);
        versionCol.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return element instanceof ConflictInfo ci ? ci.version() : ""; //$NON-NLS-1$
            }
        });

        tableViewer.setContentProvider(ArrayContentProvider.getInstance());
        tableViewer.setInput(detectedConflicts);
        tableViewer.setAllChecked(true);

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, UNINSTALL_ID, "Uninstall Selected", true); //$NON-NLS-1$
        createButton(parent, IDialogConstants.CANCEL_ID, "Dismiss", false); //$NON-NLS-1$
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == UNINSTALL_ID) {
            uninstallSelected();
        } else {
            super.buttonPressed(buttonId);
        }
    }

    private void uninstallSelected() {
        Object[] checked = tableViewer.getCheckedElements();
        if (checked.length == 0) {
            return;
        }
        List<ConflictInfo> selected = new ArrayList<>();
        for (Object obj : checked) {
            if (obj instanceof ConflictInfo ci) {
                selected.add(ci);
            }
        }
        try {
            if (tryP2Uninstall(selected)) {
                var uninstallButton = getButton(UNINSTALL_ID);
                if (uninstallButton != null) {
                    uninstallButton.setEnabled(false);
                }
                if (tableViewer != null) {
                    tableViewer.getTable().setEnabled(false);
                }
                setMessage("Uninstall scheduled. Please wait for completion..."); //$NON-NLS-1$
                return;
            }
            showManualInstructions();
        } catch (Exception | NoClassDefFoundError e) {
            Logger.debug(e);
            showManualInstructions();
        }
    }

    public boolean tryP2Uninstall(List<ConflictInfo> selected) {
        if (selected == null || selected.isEmpty()) {
            return false;
        }
        Bundle bundle = FrameworkUtil.getBundle(ConflictingPluginsDialog.class);
        if (bundle == null || bundle.getBundleContext() == null) {
            return false;
        }
        ServiceTracker<IProvisioningAgent, IProvisioningAgent> tracker =
                new ServiceTracker<>(bundle.getBundleContext(), IProvisioningAgent.class, null);
        tracker.open();
        try {
            IProvisioningAgent agent = tracker.getService();
            if (agent == null) {
                return false;
            }
            IProfile profile = resolveP2Profile(agent);
            if (profile == null) {
                return false;
            }
            List<IInstallableUnit> ius = collectIUsToUninstall(profile, selected);
            if (ius.isEmpty()) {
                return false;
            }
            return scheduleUninstallJob(agent, ius);
        } finally {
            tracker.close();
        }
    }

    private static IProfile resolveP2Profile(IProvisioningAgent agent) {
        IProfileRegistry registry = (IProfileRegistry) agent.getService(IProfileRegistry.SERVICE_NAME);
        if (registry == null) {
            return null;
        }
        return registry.getProfile(IProfileRegistry.SELF);
    }

    private static List<IInstallableUnit> collectIUsToUninstall(IProfile profile, List<ConflictInfo> selected) {
        List<IInstallableUnit> ius = new ArrayList<>();
        for (ConflictInfo ci : selected) {
            addMatchingIUs(ius, profile, ci.p2Prefix());
        }
        return ius;
    }

    private boolean scheduleUninstallJob(IProvisioningAgent agent, List<IInstallableUnit> ius) {
        ProvisioningSession session = new ProvisioningSession(agent);
        UninstallOperation operation = new UninstallOperation(session, ius);
        IStatus resolveStatus = operation.resolveModal(new NullProgressMonitor());
        int severity = resolveStatus.getSeverity();
        if (severity == IStatus.ERROR || severity == IStatus.CANCEL) {
            Logger.debug(resolveStatus.getMessage(), null);
            return false;
        }
        ProvisioningJob job = operation.getProvisioningJob(new NullProgressMonitor());
        if (job == null) {
            return false;
        }
        job.addJobChangeListener(new JobChangeAdapter() {
            @Override
            public void done(IJobChangeEvent event) {
                onJobDone(event);
            }
        });
        job.schedule();
        return true;
    }

    private void onJobDone(IJobChangeEvent event) {
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return;
        }
        int severity = event.getResult().getSeverity();
        if (severity == IStatus.OK || severity == IStatus.WARNING) {
            display.asyncExec(this::closeAndPromptRestart);
        } else {
            display.asyncExec(this::showManualInstructionsIfOpen);
        }
    }

    private void closeAndPromptRestart() {
        Shell s = getShell();
        if (s != null && !s.isDisposed()) {
            close();
        }
        promptRestart();
    }

    private void showManualInstructionsIfOpen() {
        Shell s = getShell();
        if (s != null && !s.isDisposed()) {
            showManualInstructions();
        }
    }

    private static void addMatchingIUs(List<IInstallableUnit> ius, IProfile profile, String prefix) {
        for (IInstallableUnit iu : profile.query(QueryUtil.createIUAnyQuery(), null)) {
            String id = iu.getId();
            if (id.startsWith(prefix) && id.endsWith(".feature.group") && !ius.contains(iu)) { //$NON-NLS-1$
                ius.add(iu);
            }
        }
    }

    private static void promptRestart() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return;
        }
        var window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            return;
        }
        Shell shell = window.getShell();
        if (shell == null || shell.isDisposed()) {
            return;
        }
        boolean restart = MessageDialog.openQuestion(shell,
                "Restart Required", //$NON-NLS-1$
                "The selected plugins have been uninstalled.\nEclipse must restart to apply the changes. Restart now?"); //$NON-NLS-1$
        if (restart) {
            PlatformUI.getWorkbench().restart();
        }
    }

    private void showManualInstructions() {
        MessageDialog.openInformation(getShell(),
                "Manual Uninstall Required", //$NON-NLS-1$
                """
                Automatic uninstall is not available.

                To uninstall manually: Help → About Eclipse → Installation Details → Installed Software
                Select the conflicting plugins and click 'Uninstall'.
                """.stripIndent()); //$NON-NLS-1$
    }
}
