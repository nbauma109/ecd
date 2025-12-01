package org.sf.feeling.decompiler.vineflower.decompiler;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.sf.feeling.decompiler.editor.BaseDecompilerSourceMapper;
import org.sf.feeling.decompiler.editor.IDecompiler;
import org.sf.feeling.decompiler.editor.IDecompilerDescriptor;
import org.sf.feeling.decompiler.vineflower.VineflowerDecompilerPlugin;
import org.sf.feeling.decompiler.vineflower.actions.DecompileWithVineflowerCoreAction;
import org.sf.feeling.decompiler.vineflower.i18n.Messages;

public class VineflowerDecompilerDescriptor implements IDecompilerDescriptor {
    private VineflowerDecompiler decompiler = null;
    private VineflowerSourceMapper sourceMapper = null;
    private Action decompileAction;

    @Override
    public String getDecompilerType() {
        return VineflowerDecompilerPlugin.decompilerType;
    }

    @Override
    public String getDecompilerPreferenceLabel() {
        return Messages.getString("VineflowerDecompilerDescriptor.PreferenceLabel"); //$NON-NLS-1$
    }

    @Override
    public IDecompiler getDecompiler() {
        if (decompiler == null) {
            decompiler = new VineflowerDecompiler();
        }
        return decompiler;

    }

    @Override
    public BaseDecompilerSourceMapper getDecompilerSourceMapper() {
        if (sourceMapper == null) {
            sourceMapper = new VineflowerSourceMapper();
        }
        return sourceMapper;
    }

    @Override
    public Action getDecompileAction() {
        if (decompileAction == null) {
            decompileAction = new DecompileWithVineflowerCoreAction();
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
        return VineflowerDecompilerPlugin.getImageDescriptor("icons/vineflower_16.png"); //$NON-NLS-1$
    }

}
