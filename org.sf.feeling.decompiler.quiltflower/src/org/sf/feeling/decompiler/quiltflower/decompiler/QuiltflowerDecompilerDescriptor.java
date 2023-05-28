package org.sf.feeling.decompiler.quiltflower.decompiler;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.sf.feeling.decompiler.editor.BaseDecompilerSourceMapper;
import org.sf.feeling.decompiler.editor.IDecompiler;
import org.sf.feeling.decompiler.editor.IDecompilerDescriptor;
import org.sf.feeling.decompiler.quiltflower.QuiltflowerDecompilerPlugin;
import org.sf.feeling.decompiler.quiltflower.actions.DecompileWithQuiltflowerCoreAction;
import org.sf.feeling.decompiler.quiltflower.i18n.Messages;

public class QuiltflowerDecompilerDescriptor implements IDecompilerDescriptor {
	private QuiltflowerDecompiler decompiler = null;
	private QuiltflowerSourceMapper sourceMapper = null;
	private Action decompileAction;

	@Override
	public String getDecompilerType() {
		return QuiltflowerDecompilerPlugin.decompilerType;
	}

	@Override
	public String getDecompilerPreferenceLabel() {
		return Messages.getString("QuiltflowerDecompilerDescriptor.PreferenceLabel"); //$NON-NLS-1$
	}

	@Override
	public IDecompiler getDecompiler() {
		if (decompiler == null) {
			decompiler = new QuiltflowerDecompiler();
		}
		return decompiler;

	}

	@Override
	public BaseDecompilerSourceMapper getDecompilerSourceMapper() {
		if (sourceMapper == null) {
			sourceMapper = new QuiltflowerSourceMapper();
		}
		return sourceMapper;
	}

	@Override
	public Action getDecompileAction() {
		if (decompileAction == null) {
			decompileAction = new DecompileWithQuiltflowerCoreAction();
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
		return QuiltflowerDecompilerPlugin.getImageDescriptor("icons/quiltflower_16.png"); //$NON-NLS-1$
	}

}
