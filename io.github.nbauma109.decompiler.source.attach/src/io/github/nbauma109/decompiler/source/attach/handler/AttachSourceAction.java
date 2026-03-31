/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ui.ISharedImages;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.Action;
import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.source.attach.i18n.Messages;

@SuppressWarnings("rawtypes")
public class AttachSourceAction extends Action {

    private List selection = null;

    public AttachSourceAction(List selection) {
        super(Messages.getString("AttachSourceAction.Action.Name")); //$NON-NLS-1$
        this.setImageDescriptor(
                JavaUI.getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJS_EXTERNAL_ARCHIVE_WITH_SOURCE));
        this.selection = selection;
    }

    @Override
    public void run() {
        List<IPackageFragmentRoot> selectedRoots = getSelectedRoots();
        if (selectedRoots.isEmpty()) {
            return;
        }
        JavaDecompilerPlugin.getDefault().attachSources(selectedRoots, true);
    }

    @Override
    public boolean isEnabled() {
        return selection != null;
    }

    private List<IPackageFragmentRoot> getSelectedRoots() {
        Map<String, IPackageFragmentRoot> roots = new LinkedHashMap<>();
        if (selection == null) {
            return new ArrayList<>(roots.values());
        }
        for (Object element : selection) {
            IPackageFragmentRoot root = toPackageFragmentRoot(element);
            if (root == null || root.getPath() == null) {
                continue;
            }
            roots.putIfAbsent(root.getPath().toOSString(), root);
        }
        return new ArrayList<>(roots.values());
    }

    private IPackageFragmentRoot toPackageFragmentRoot(Object element) {
        if (element instanceof IClassFile classFile) {
            return (IPackageFragmentRoot) classFile.getParent().getParent();
        }
        if (element instanceof IPackageFragmentRoot root) {
            return root;
        }
        if (element instanceof IPackageFragment packageFragment) {
            return (IPackageFragmentRoot) packageFragment.getParent();
        }
        return null;
    }
}
