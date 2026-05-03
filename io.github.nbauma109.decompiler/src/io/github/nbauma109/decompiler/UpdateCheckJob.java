/*******************************************************************************
 * (C) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Version;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import io.github.nbauma109.decompiler.popup.NewerVersionAvailablePopup;
import io.github.nbauma109.decompiler.util.EcfHttpClient;
import io.github.nbauma109.decompiler.util.Logger;

public class UpdateCheckJob extends Job {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/nbauma109/ecd/releases/latest"; //$NON-NLS-1$
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final String apiUrl;

    public UpdateCheckJob() {
        this(GITHUB_API_URL);
    }

    public UpdateCheckJob(String apiUrl) {
        super("Checking for ECD++ updates"); //$NON-NLS-1$
        this.apiUrl = apiUrl;
        setSystem(true);
        setPriority(DECORATE);
    }

    @Override
    public IStatus run(IProgressMonitor monitor) {
        try {
            String latestVersion = fetchLatestVersion();
            if (latestVersion == null) {
                return Status.OK_STATUS;
            }
            Version current = JavaDecompilerPlugin.getDefault().getBundle().getVersion();
            Version latest = parseVersion(latestVersion);
            if (latest != null && latest.compareTo(current) > 0) {
                showPopup(latestVersion);
            }
        } catch (Exception | NoClassDefFoundError e) {
            Logger.debug(e);
        }
        return Status.OK_STATUS;
    }

    private String fetchLatestVersion() throws IOException {
        EcfHttpClient client = new EcfHttpClient();
        client.setConnectTimeout(CONNECT_TIMEOUT_MS);
        client.setReadTimeout(READ_TIMEOUT_MS);
        client.setRequestHeader("Accept", "application/vnd.github.v3+json"); //$NON-NLS-1$ //$NON-NLS-2$
        client.setRequestHeader("User-Agent", "ECD-UpdateChecker/1.0"); //$NON-NLS-1$ //$NON-NLS-2$
        byte[] bytes = client.downloadToBytes(apiUrl);
        String json = new String(bytes, StandardCharsets.UTF_8);
        JsonValue parsed = Json.parse(json);
        if (parsed.isObject()) {
            JsonObject obj = parsed.asObject();
            JsonValue tagName = obj.get("tag_name"); //$NON-NLS-1$
            if (tagName != null && tagName.isString()) {
                return tagName.asString();
            }
        }
        return null;
    }

    private Version parseVersion(String versionStr) {
        try {
            String v = versionStr.startsWith("v") ? versionStr.substring(1) : versionStr; //$NON-NLS-1$
            return Version.parseVersion(v);
        } catch (Exception e) {
            return null;
        }
    }

    private void showPopup(String version) {
        if (!PlatformUI.isWorkbenchRunning()) {
            return;
        }
        Display display = PlatformUI.getWorkbench().getDisplay();
        if (display == null || display.isDisposed()) {
            return;
        }
        display.asyncExec(() -> {
            if (!display.isDisposed()) {
                new NewerVersionAvailablePopup(version).open();
            }
        });
    }
}
