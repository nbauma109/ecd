/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.sf.feeling.decompiler.editor.DecompilerType;
import org.sf.feeling.decompiler.editor.IDecompilerDescriptor;
import org.sf.feeling.decompiler.editor.JavaDecompilerBufferManager;
import org.sf.feeling.decompiler.extension.DecompilerAdapterManager;
import org.sf.feeling.decompiler.source.attach.IAttachSourceHandler;
import org.sf.feeling.decompiler.util.FileUtil;
import org.sf.feeling.decompiler.util.Logger;
import org.sf.feeling.decompiler.util.SortMemberUtil;

public class JavaDecompilerPlugin extends AbstractUIPlugin implements IPropertyChangeListener {

    public static final String EDITOR_ID = "org.sf.feeling.decompiler.ClassFileEditor"; //$NON-NLS-1$
    public static final String PLUGIN_ID = "org.sf.feeling.decompiler"; //$NON-NLS-1$
    public static final String TEMP_DIR = "org.sf.feeling.decompiler.tempd"; //$NON-NLS-1$

    public static final String REUSE_BUFFER = "org.sf.feeling.decompiler.reusebuff"; //$NON-NLS-1$
    public static final String IGNORE_EXISTING = "org.sf.feeling.decompiler.alwaysuse"; //$NON-NLS-1$
    public static final String USE_ECLIPSE_FORMATTER = "org.sf.feeling.decompiler.use_eclipse_formatter"; //$NON-NLS-1$
    public static final String USE_ECLIPSE_SORTER = "org.sf.feeling.decompiler.use_eclipse_sorter"; //$NON-NLS-1$
    public static final String DECOMPILER_TYPE = "org.sf.feeling.decompiler.type"; //$NON-NLS-1$
    public static final String PREF_DISPLAY_LINE_NUMBERS = "jd.ide.eclipse.prefs.DisplayLineNumbers"; //$NON-NLS-1$
    public static final String PREF_DISPLAY_METADATA = "jd.ide.eclipse.prefs.DisplayMetadata"; //$NON-NLS-1$
    public static final String ALIGN = "jd.ide.eclipse.prefs.RealignLineNumbers"; //$NON-NLS-1$
    public static final String DEFAULT_EDITOR = "org.sf.feeling.decompiler.default_editor"; //$NON-NLS-1$ ;
    public static final String EXPORT_ENCODING = "org.sf.feeling.decompiler.export.encoding"; //$NON-NLS-1$ ;
    public static final String ATTACH_SOURCE = "org.sf.feeling.decompiler.attach_source"; //$NON-NLS-1$ ;
    public static final String WAIT_FOR_SOURCES = "org.sf.feeling.decompiler.wait_for_sources"; //$NON-NLS-1$ ;

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
    private TreeMap<String, IDecompilerDescriptor> decompilerDescriptorMap = new TreeMap<>();

    private boolean isDebugMode = false;

    public Map<String, IDecompilerDescriptor> getDecompilerDescriptorMap() {
        return decompilerDescriptorMap;
    }

    public String[] getDecompilerDescriptorTypes() {
        return decompilerDescriptorMap.keySet().toArray(new String[0]);
    }

    public IDecompilerDescriptor getDecompilerDescriptor(String decompilerType) {
        return decompilerDescriptorMap.get(decompilerType);
    }

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
                + File.separator + ".org.sf.feeling.decompiler" //$NON-NLS-1$
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

    private void setDefaultDecompiler(IPreferenceStore store) {
        Object[] decompilerAdapters = DecompilerAdapterManager.getAdapters(this, IDecompilerDescriptor.class);

        if (decompilerAdapters != null) {
            for (Object adapter : decompilerAdapters) {
                if ((adapter instanceof IDecompilerDescriptor descriptor) && descriptor.isEnabled()) {
                    decompilerDescriptorMap.put(descriptor.getDecompilerType(), descriptor);
                }
            }
        }
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
        setDefaultDecompiler(getPreferenceStore());
        getPreferenceStore().addPropertyChangeListener(this);
        SortMemberUtil.deleteDecompilerProject();
        Display.getDefault().asyncExec(new SetupRunnable());
    }

    @Override
    public IPreferenceStore getPreferenceStore() {
        if (preferenceStore == null) {
            preferenceStore = super.getPreferenceStore();

            String decompilerType = preferenceStore.getString(DECOMPILER_TYPE);
            if (!DecompilerType.FernFlower.equals(decompilerType)) {
                IDecompilerDescriptor descriptor = getDecompilerDescriptor(decompilerType);
                if (descriptor == null) {
                    preferenceStore.setDefault(DECOMPILER_TYPE, getDefaultDecompilerType());
                }
            }

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

    public Boolean isDisplayLineNumber() {
        return Boolean.valueOf(getPreferenceStore().getBoolean(PREF_DISPLAY_LINE_NUMBERS));
    }

    public Boolean isDebug() {
        return Boolean.valueOf(getPreferenceStore().getBoolean(ALIGN));
    }

    public void displayLineNumber(Boolean display) {
        getPreferenceStore().setValue(PREF_DISPLAY_LINE_NUMBERS, display.booleanValue());
    }

    public void setExportEncoding(String encoding) {
        getPreferenceStore().setValue(EXPORT_ENCODING, encoding);
    }

    public String getExportEncoding() {
        return getPreferenceStore().getString(EXPORT_ENCODING);
    }

    public boolean enableAttachSourceSetting() {
        Object attachSourceAdapter = DecompilerAdapterManager.getAdapter(this, IAttachSourceHandler.class);
        return attachSourceAdapter instanceof IAttachSourceHandler;
    }

    private Set<String> libraries = new ConcurrentSkipListSet<>();

    public Thread attachSource(IPackageFragmentRoot library, boolean force) {
        Object attachSourceAdapter = DecompilerAdapterManager.getAdapter(this, IAttachSourceHandler.class);
        if ((attachSourceAdapter instanceof IAttachSourceHandler) && (!libraries.contains(library.getPath().toOSString()) || force)) {
            libraries.add(library.getPath().toOSString());
            return ((IAttachSourceHandler) attachSourceAdapter).execute(library, force);
        }
        return null;
    }

    public void syncLibrarySource(IPackageFragmentRoot library) {
        try {
            if (library.getPath() != null && library.getSourceAttachmentPath() != null
                    && !libraries.contains(library.getPath().toOSString())) {
                final IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
                if (prefs.getBoolean(JavaDecompilerPlugin.DEFAULT_EDITOR)) {
                    final Object attachSourceAdapter = DecompilerAdapterManager
                            .getAdapter(JavaDecompilerPlugin.getDefault(), IAttachSourceHandler.class);
                    if (attachSourceAdapter instanceof IAttachSourceHandler) {
                        libraries.add(library.getPath().toOSString());
                        if (!((IAttachSourceHandler) attachSourceAdapter).syncAttachSource(library)) {
                            libraries.remove(library.getPath().toOSString());
                        }
                    }
                }
            }
        } catch (JavaModelException e) {
            Logger.debug(e);
        }
    }

    public boolean isAutoAttachSource() {
        if (!enableAttachSourceSetting()) {
            return false;
        }

        return getPreferenceStore().getBoolean(ATTACH_SOURCE);
    }

    public String getDefaultDecompilerType() {
        Collection<IDecompilerDescriptor> descriptors = JavaDecompilerPlugin.getDefault().getDecompilerDescriptorMap()
                .values();
        if (descriptors != null) {
            for (IDecompilerDescriptor iDecompilerDescriptor : descriptors) {
                if (iDecompilerDescriptor.isDefault()) {
                    return iDecompilerDescriptor.getDecompilerType();
                }
            }
        }
        return DecompilerType.FernFlower;
    }

    public boolean isDebugMode() {
        return isDebugMode;
    }

    public void setDebugMode(boolean isDebugMode) {
        this.isDebugMode = isDebugMode;
    }

    public String getDefaultExportEncoding() {
        return getPreferenceStore().getDefaultString(JavaDecompilerPlugin.EXPORT_ENCODING);
    }

}