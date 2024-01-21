
package org.sf.feeling.decompiler.vineflower;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;

public class VineflowerDecompilerPlugin extends AbstractUIPlugin implements IPropertyChangeListener {

	public static final String PLUGIN_ID = "org.sf.feeling.decompiler.vineflower"; //$NON-NLS-1$

	public static final String CMD = "org.sf.feeling.decompiler.vineflower.cmd"; //$NON-NLS-1$

	public static final String decompilerType = "Vineflower"; //$NON-NLS-1$

	public static final String decompilerVersion = "1.9"; //$NON-NLS-1$

	private static VineflowerDecompilerPlugin plugin;

	private IPreferenceStore preferenceStore;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext )
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		getPreferenceStore().addPropertyChangeListener(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
		super.stop(context);
		getPreferenceStore().removePropertyChangeListener(this);
		plugin = null;
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {

	}

	@Override
	public IPreferenceStore getPreferenceStore() {
		if (preferenceStore == null) {
			preferenceStore = JavaDecompilerPlugin.getDefault().getPreferenceStore();
		}
		return preferenceStore;
	}

	public static VineflowerDecompilerPlugin getDefault() {
		return plugin;
	}

	public VineflowerDecompilerPlugin() {
		plugin = this;
	}

	public static ImageDescriptor getImageDescriptor(String path) {
		URL base = VineflowerDecompilerPlugin.getDefault().getBundle().getEntry("/"); //$NON-NLS-1$
		URL url = null;
		try {
			url = new URL(base, path); // $NON-NLS-1$
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
		ImageDescriptor actionIcon = null;
		if (url != null) {
			actionIcon = ImageDescriptor.createFromURL(url);
		}
		return actionIcon;
	}

}
