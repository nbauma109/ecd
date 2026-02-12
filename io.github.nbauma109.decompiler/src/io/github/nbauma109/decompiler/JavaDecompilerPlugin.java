/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import io.github.nbauma109.decompiler.actions.BaseDecompilerHandler;
import io.github.nbauma109.decompiler.editor.BaseDecompilerSourceMapper;
import io.github.nbauma109.decompiler.editor.DecompilerSourceMapper;
import io.github.nbauma109.decompiler.editor.JavaDecompilerBufferManager;
import io.github.nbauma109.decompiler.extension.DecompilerAdapterManager;
import io.github.nbauma109.decompiler.source.attach.IAttachSourceHandler;
import io.github.nbauma109.decompiler.util.FileUtil;
import io.github.nbauma109.decompiler.util.Logger;
import io.github.nbauma109.decompiler.util.SortMemberUtil;
import io.github.nbauma109.decompiler.util.UIUtil;

public class JavaDecompilerPlugin extends AbstractUIPlugin implements IPropertyChangeListener {

    public static final String EDITOR_ID = "io.github.nbauma109.decompiler.ClassFileEditor"; //$NON-NLS-1$
    public static final String PLUGIN_ID = "io.github.nbauma109.decompiler"; //$NON-NLS-1$
    public static final String TEMP_DIR = "io.github.nbauma109.decompiler.tempd"; //$NON-NLS-1$

    public static final String REUSE_BUFFER = "io.github.nbauma109.decompiler.reusebuff"; //$NON-NLS-1$
    public static final String IGNORE_EXISTING = "io.github.nbauma109.decompiler.alwaysuse"; //$NON-NLS-1$
    public static final String USE_ECLIPSE_FORMATTER = "io.github.nbauma109.decompiler.use_eclipse_formatter"; //$NON-NLS-1$
    public static final String USE_ECLIPSE_SORTER = "io.github.nbauma109.decompiler.use_eclipse_sorter"; //$NON-NLS-1$
    public static final String DECOMPILER_TYPE = "io.github.nbauma109.decompiler.type"; //$NON-NLS-1$
    public static final String PREF_DISPLAY_LINE_NUMBERS = "jd.ide.eclipse.prefs.DisplayLineNumbers"; //$NON-NLS-1$
    public static final String PREF_DISPLAY_METADATA = "jd.ide.eclipse.prefs.DisplayMetadata"; //$NON-NLS-1$
    public static final String ALIGN = "jd.ide.eclipse.prefs.RealignLineNumbers"; //$NON-NLS-1$
    public static final String DEFAULT_EDITOR = "io.github.nbauma109.decompiler.default_editor"; //$NON-NLS-1$ ;
    public static final String EXPORT_ENCODING = "io.github.nbauma109.decompiler.export.encoding"; //$NON-NLS-1$ ;
    public static final String ATTACH_SOURCE = "io.github.nbauma109.decompiler.attach_source"; //$NON-NLS-1$ ;
    public static final String WAIT_FOR_SOURCES = "io.github.nbauma109.decompiler.wait_for_sources"; //$NON-NLS-1$ ;
    public static final String EXCLUDE_PACKAGES = "io.github.nbauma109.decompiler.exclude_packages"; //$NON-NLS-1$ ;

    private static final String classFileAttributePreferencesPrefix = "CLASS_FILE_ATTR_"; //$NON-NLS-1$
    private static final String CLASS_FILE_ATTR_SHOW_CONSTANT_POOL = classFileAttributePreferencesPrefix
            + "show_constantPool"; //$NON-NLS-1$
    private static final String CLASS_FILE_ATTR_SHOW_LINE_NUMBER_TABLE = classFileAttributePreferencesPrefix
            + "show_lineNumberTable"; //$NON-NLS-1$
    private static final String CLASS_FILE_ATTR_SHOW_VARIABLE_TABLE = classFileAttributePreferencesPrefix
            + "show_localVariableTable"; //$NON-NLS-1$
    private static final String CLASS_FILE_ATTR_SHOW_EXCEPTION_TABLE = classFileAttributePreferencesPrefix
            + "show_exceptionTable"; //$NON-NLS-1$
    private static final String CLASS_FILE_ATTR_SHOW_MAXS = classFileAttributePreferencesPrefix + "show_maxs"; //$NON-NLS-1$
    private static final String CLASS_FILE_ATTR_RENDER_TRYCATCH_BLOCKS = classFileAttributePreferencesPrefix
            + "render_tryCatchBlocks"; //$NON-NLS-1$
    private static final String CLASS_FILE_ATTR_SHOW_SOURCE_LINE_NUMBERS = classFileAttributePreferencesPrefix
            + "render_sourceLineNumbers"; //$NON-NLS-1$
    private static final String BRANCH_TARGET_ADDRESS_RENDERING = "BRANCH_TARGET_ADDRESS_RENDERING"; //$NON-NLS-1$
    private static final String BRANCH_TARGET_ADDRESS_RELATIVE = BRANCH_TARGET_ADDRESS_RENDERING + "_RELATIVE"; //$NON-NLS-1$

    public static final String NEXUS_URL = "NEXUS_URL"; //$NON-NLS-1$
    public static final String NEXUS_USER = "NEXUS_USER"; //$NON-NLS-1$
    public static final String NEXUS_PASSWORD = "NEXUS_PASSWORD"; //$NON-NLS-1$

    public static final String PUBLIC_REPO_MAVEN_CENTRAL = "PUBLIC_REPO_MAVEN_CENTRAL"; //$NON-NLS-1$
    public static final String PUBLIC_REPO_CLOUDERA = "PUBLIC_REPO_CLOUDERA"; //$NON-NLS-1$
    public static final String PUBLIC_REPO_NEXUS_XWIKI_ORG = "PUBLIC_REPO_NEXUS_XWIKI_ORG"; //$NON-NLS-1$
    public static final String PUBLIC_REPO_MAVEN_ALFRESCO = "PUBLIC_REPO_MAVEN_ALFRESCO"; //$NON-NLS-1$
    public static final String PUBLIC_REPO_APACHE_ORG = "PUBLIC_REPO_APACHE_ORG"; //$NON-NLS-1$
    public static final String PUBLIC_REPO_GRAILS_ORG = "PUBLIC_REPO_GRAILS_ORG"; //$NON-NLS-1$
    public static final String PUBLIC_REPO_OSS_SONATYPE_ORG = "PUBLIC_REPO_OSS_SONATYPE_ORG"; //$NON-NLS-1$

    private static JavaDecompilerPlugin plugin;

    private IPreferenceStore preferenceStore;

    public static JavaDecompilerPlugin getDefault() {
        return plugin;
    }

    public static void logError(Throwable t, String message) {
        JavaDecompilerPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, 0, message, t));
    }

    public static void logInfo(String message) {
        JavaDecompilerPlugin.getDefault().getLog().log(new Status(IStatus.INFO, PLUGIN_ID, 0, message, null));
    }

    public static void logWarn(String message) {
        JavaDecompilerPlugin.getDefault().getLog().log(new Status(IStatus.WARNING, PLUGIN_ID, 0, message, null));
    }

    public static void log(int severity, Throwable t, String message) {
        JavaDecompilerPlugin.getDefault().getLog().log(new Status(severity, PLUGIN_ID, 0, message, t));
    }

    public static ImageDescriptor getImageDescriptor(String path) {
        URL base = JavaDecompilerPlugin.getDefault().getBundle().getEntry("/"); //$NON-NLS-1$
        URL url = null;
        try {
            url = new URL(base, path); // $NON-NLS-1$
        } catch (MalformedURLException e) {
            Logger.debug(e);
        }
        ImageDescriptor actionIcon = null;
        if (url != null) {
            actionIcon = ImageDescriptor.createFromURL(url);
        }
        return actionIcon;
    }

    public JavaDecompilerPlugin() {
        plugin = this;
    }

    @Override
    protected void initializeDefaultPreferences(IPreferenceStore store) {
        store.setDefault(TEMP_DIR, System.getProperty("java.io.tmpdir") //$NON-NLS-1$
                + File.separator + ".io.github.nbauma109.decompiler" //$NON-NLS-1$
                + System.currentTimeMillis());
        store.setDefault(REUSE_BUFFER, true);
        store.setDefault(IGNORE_EXISTING, false);
        store.setDefault(USE_ECLIPSE_FORMATTER, true);
        store.setDefault(USE_ECLIPSE_SORTER, false);
        store.setDefault(PREF_DISPLAY_METADATA, false);
        store.setDefault(DEFAULT_EDITOR, true);
        store.setDefault(ATTACH_SOURCE, true);
        store.setDefault(WAIT_FOR_SOURCES, true);
        store.setDefault(EXPORT_ENCODING, StandardCharsets.UTF_8.name());

        store.setDefault(CLASS_FILE_ATTR_SHOW_CONSTANT_POOL, false);
        store.setDefault(CLASS_FILE_ATTR_SHOW_LINE_NUMBER_TABLE, false);
        store.setDefault(CLASS_FILE_ATTR_SHOW_VARIABLE_TABLE, false);
        store.setDefault(CLASS_FILE_ATTR_SHOW_EXCEPTION_TABLE, false);
        store.setDefault(CLASS_FILE_ATTR_SHOW_MAXS, false);
        store.setDefault(BRANCH_TARGET_ADDRESS_RENDERING, BRANCH_TARGET_ADDRESS_RELATIVE);
        store.setDefault(CLASS_FILE_ATTR_RENDER_TRYCATCH_BLOCKS, true);
        store.setDefault(CLASS_FILE_ATTR_SHOW_SOURCE_LINE_NUMBERS, true);
        store.setDefault(CLASS_FILE_ATTR_SHOW_MAXS, false);
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (event.getProperty().equals(IGNORE_EXISTING)) {
            JavaDecompilerBufferManager.closeDecompilerBuffers(false);
        }
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        getPreferenceStore().addPropertyChangeListener(this);
        SortMemberUtil.deleteDecompilerProject();
        Display.getDefault().asyncExec(new SetupRunnable());
    }

    @Override
    public IPreferenceStore getPreferenceStore() {
        if (preferenceStore == null) {
            preferenceStore = super.getPreferenceStore();
        }
        return preferenceStore;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        FileUtil.deltree(new File(getPreferenceStore().getString(JavaDecompilerPlugin.TEMP_DIR)));

        super.stop(context);

        getPreferenceStore().removePropertyChangeListener(this);

        plugin = null;
    }

    public boolean isDisplayLineNumber() {
        return getPreferenceStore().getBoolean(PREF_DISPLAY_LINE_NUMBERS);
    }

    public boolean isDebug() {
        return getPreferenceStore().getBoolean(ALIGN);
    }

    public void displayLineNumber(Boolean display) {
        getPreferenceStore().setValue(PREF_DISPLAY_LINE_NUMBERS, display);
    }

    public void setExportEncoding(String encoding) {
        getPreferenceStore().setValue(EXPORT_ENCODING, encoding);
    }

    public String getExportEncoding() {
        return getPreferenceStore().getString(EXPORT_ENCODING);
    }

    public boolean enableAttachSourceSetting() {
        return getAttachSourceHandler() != null;
    }

    private Set<String> libraries = new ConcurrentSkipListSet<>();

    public Thread attachSource(IPackageFragmentRoot library, boolean force) {
        final IAttachSourceHandler attachSourceAdapter = getAttachSourceHandler();
        if (attachSourceAdapter != null && (!libraries.contains(library.getPath().toOSString()) || force)) {
            libraries.add(library.getPath().toOSString());
            return attachSourceAdapter.execute(library, force);
        }
        return null;
    }

    public void syncLibrarySource(IPackageFragmentRoot library) {
        try {
            if (library.getPath() != null && library.getSourceAttachmentPath() != null
                    && !libraries.contains(library.getPath().toOSString())) {
                final IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
                if (prefs.getBoolean(JavaDecompilerPlugin.DEFAULT_EDITOR)) {
                    final IAttachSourceHandler attachSourceAdapter = getAttachSourceHandler();
                    if (attachSourceAdapter != null) {
                        libraries.add(library.getPath().toOSString());
                        if (!attachSourceAdapter.syncAttachSource(library)) {
                            libraries.remove(library.getPath().toOSString());
                        }
                    }
                }
            }
        } catch (JavaModelException e) {
            Logger.debug(e);
        }
    }

    private IAttachSourceHandler getAttachSourceHandler() {
        return (IAttachSourceHandler) DecompilerAdapterManager.getAdapter(this, IAttachSourceHandler.class);
    }

    public boolean isAutoAttachSource() {
        if (!enableAttachSourceSetting()) {
            return false;
        }

        return getPreferenceStore().getBoolean(ATTACH_SOURCE);
    }

    public String getDefaultExportEncoding() {
        return getPreferenceStore().getDefaultString(JavaDecompilerPlugin.EXPORT_ENCODING);
    }

    public Action getDecompileAction(String decompilerType) {
        return new Action(decompilerType, JavaDecompilerPlugin.getDecompilerImageDescriptor(decompilerType)) {
            @Override
            public void run() {
                new BaseDecompilerHandler().handleDecompile(decompilerType);
            }

            @Override
            public boolean isEnabled() {
                return UIUtil.getActiveEditor() != null || UIUtil.getActiveSelection() != null;
            }
        };
    }

    public DecompilerSourceMapper getSourceMapper(String decompilerType) {
        return new BaseDecompilerSourceMapper(decompilerType);
    }

    public static ImageDescriptor getDecompilerImageDescriptor(String decompilerType) {
        return getImageDescriptor("icons/" + decompilerType.toLowerCase().replace(' ', '_') + "_16.png");
    }

}