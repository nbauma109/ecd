
package org.sf.feeling.decompiler.jd.decompiler;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.sf.feeling.decompiler.editor.IDecompiler;
import org.sf.feeling.decompiler.editor.IDecompilerDescriptor;
import org.sf.feeling.decompiler.jd.JDCoreDecompilerPlugin;
import org.sf.feeling.decompiler.jd.actions.DecompileWithJDCoreAction;
import org.sf.feeling.decompiler.jd.i18n.Messages;

import jd.ide.eclipse.editors.JDSourceMapper;

public class JDCoreDecompilerDescriptor implements IDecompilerDescriptor {

    private JDCoreDecompiler decompiler = null;

    private JDSourceMapper sourceMapper = null;

    private Action decompileAction = null;

    @Override
    public String getDecompilerType() {
        return JDCoreDecompilerPlugin.decompilerType;
    }

    @Override
    public String getDecompilerPreferenceLabel() {
        return Messages.getString("JDCoreDecompilerDescriptor.PreferenceLabel"); //$NON-NLS-1$
    }

    @Override
    public IDecompiler getDecompiler() {
        if (decompiler == null) {
            decompiler = new JDCoreDecompiler(getDecompilerSourceMapper());
        }
        return decompiler;
    }

    @Override
    public JDSourceMapper getDecompilerSourceMapper() {
        if (sourceMapper == null) {
            sourceMapper = new JDCoreSourceMapper();
        }
        return sourceMapper;
    }

    @Override
    public Action getDecompileAction() {
        if (decompileAction == null) {
            decompileAction = new DecompileWithJDCoreAction();
        }
        return decompileAction;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isDefault() {
        return true;
    }

    @Override
    public ImageDescriptor getDecompilerIcon() {
        return JDCoreDecompilerPlugin.getImageDescriptor("icons/jd_16.png"); //$NON-NLS-1$
    }
}
