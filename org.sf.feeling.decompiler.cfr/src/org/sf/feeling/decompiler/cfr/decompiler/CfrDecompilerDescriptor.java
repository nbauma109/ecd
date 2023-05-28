
package org.sf.feeling.decompiler.cfr.decompiler;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.sf.feeling.decompiler.cfr.CfrDecompilerPlugin;
import org.sf.feeling.decompiler.cfr.actions.DecompileWithCfrAction;
import org.sf.feeling.decompiler.cfr.i18n.Messages;
import org.sf.feeling.decompiler.editor.BaseDecompilerSourceMapper;
import org.sf.feeling.decompiler.editor.IDecompiler;
import org.sf.feeling.decompiler.editor.IDecompilerDescriptor;

public class CfrDecompilerDescriptor implements IDecompilerDescriptor {

	private CfrDecompiler decompiler = null;

	private BaseDecompilerSourceMapper sourceMapper = null;

	private Action decompileAction = null;

	@Override
	public String getDecompilerType() {
		return CfrDecompilerPlugin.decompilerType;
	}

	@Override
	public String getDecompilerPreferenceLabel() {
		return Messages.getString("CfrDecompilerDescriptor.PreferenceLabel"); //$NON-NLS-1$
	}

	@Override
	public IDecompiler getDecompiler() {
		if (decompiler == null) {
			decompiler = new CfrDecompiler();
		}
		return decompiler;
	}

	@Override
	public BaseDecompilerSourceMapper getDecompilerSourceMapper() {
		if (sourceMapper == null) {
			sourceMapper = new CfrSourceMapper();
		}
		return sourceMapper;
	}

	@Override
	public Action getDecompileAction() {
		if (decompileAction == null) {
			decompileAction = new DecompileWithCfrAction();
		}
		return decompileAction;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public boolean isDefault() {
		return false;
	}

	@Override
	public ImageDescriptor getDecompilerIcon() {
		return CfrDecompilerPlugin.getImageDescriptor("icons/cfr_16.gif"); //$NON-NLS-1$ ;
	}

}
