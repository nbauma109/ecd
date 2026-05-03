/*******************************************************************************
 * © 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.popup;

import java.net.URI;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;

import io.github.nbauma109.decompiler.util.Logger;

public class NewerVersionAvailablePopup extends Window {

    private static final int POPUP_WIDTH = 420;
    private static final int PADDING_EDGE = 10;
    private static final String GITHUB_RELEASES_URL = "https://github.com/nbauma109/ecd/releases"; //$NON-NLS-1$
    private static final String COMMAND_P2_UPDATE = "org.eclipse.equinox.p2.ui.sdk.update"; //$NON-NLS-1$
    private static final String COMMAND_UPDATE_MANAGER = "org.eclipse.ui.update.findAndInstallUpdates"; //$NON-NLS-1$

    private final String version;

    public NewerVersionAvailablePopup(String version) {
        super((Shell) null);
        this.version = version;
        setShellStyle(SWT.DIALOG_TRIM | SWT.ON_TOP | SWT.TOOL);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("ECD++ - New version available"); //$NON-NLS-1$
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 12;
        layout.marginHeight = 10;
        layout.verticalSpacing = 8;
        composite.setLayout(layout);

        Label messageLabel = new Label(composite, SWT.WRAP);
        messageLabel.setText(
                "A newer version of ECD++ has been released: " + version + ".\n" //$NON-NLS-1$ //$NON-NLS-2$
                + "Feel free to check it out or trigger an update. In case no update is available in your IDE, " //$NON-NLS-1$
                + "the plugin might have been installed manually or is managed by your organization."); //$NON-NLS-1$
        GridData msgData = new GridData(SWT.FILL, SWT.FILL, true, true);
        msgData.widthHint = POPUP_WIDTH - 24;
        messageLabel.setLayoutData(msgData);

        Composite linksComposite = new Composite(composite, SWT.NONE);
        GridLayout linksLayout = new GridLayout(3, false);
        linksLayout.marginWidth = 0;
        linksLayout.horizontalSpacing = 12;
        linksComposite.setLayout(linksLayout);
        linksComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Link browserLink = new Link(linksComposite, SWT.NONE);
        browserLink.setText("<a>Check out in browser</a>"); //$NON-NLS-1$
        browserLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openBrowser(GITHUB_RELEASES_URL);
            }
        });

        Link updateLink = new Link(linksComposite, SWT.NONE);
        updateLink.setText("<a>Check for updates</a>"); //$NON-NLS-1$
        updateLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IHandlerService handlerService = PlatformUI.getWorkbench().getService(IHandlerService.class);
                if (handlerService != null && executeP2Update(handlerService)) {
                    close();
                }
            }
        });

        Link closeLink = new Link(linksComposite, SWT.NONE);
        closeLink.setText("<a>Close</a>"); //$NON-NLS-1$
        closeLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                close();
            }
        });

        return composite;
    }

    @Override
    protected void initializeBounds() {
        Shell shell = getShell();
        Point size = shell.computeSize(POPUP_WIDTH, SWT.DEFAULT);
        shell.setSize(size);

        Rectangle monitorArea = Display.getDefault().getPrimaryMonitor().getClientArea();
        int x = monitorArea.x + monitorArea.width - size.x - PADDING_EDGE;
        int y = monitorArea.y + monitorArea.height - size.y - PADDING_EDGE;
        shell.setLocation(x, y);
    }

    private void openBrowser(String url) {
        try {
            PlatformUI.getWorkbench().getBrowserSupport()
            .getExternalBrowser()
            .openURL(URI.create(url).toURL());
        } catch (Exception e) {
            Logger.debug(e);
        }
    }

    private boolean executeP2Update(IHandlerService handlerService) {
        try {
            handlerService.executeCommand(COMMAND_P2_UPDATE, null);
            return true;
        } catch (Exception e) {
            return executeUpdateManager(handlerService);
        }
    }

    private boolean executeUpdateManager(IHandlerService handlerService) {
        try {
            handlerService.executeCommand(COMMAND_UPDATE_MANAGER, null);
            return true;
        } catch (Exception e) {
            Logger.debug(e);
            return false;
        }
    }
}
