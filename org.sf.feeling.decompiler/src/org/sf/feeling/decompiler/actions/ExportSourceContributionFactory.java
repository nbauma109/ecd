/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.actions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.menus.ExtensionContributionFactory;
import org.eclipse.ui.menus.IContributionRoot;
import org.eclipse.ui.services.IServiceLocator;

public class ExportSourceContributionFactory extends ExtensionContributionFactory {

    @Override
    public void createContributionItems(IServiceLocator serviceLocator, IContributionRoot additions) {
        final ISelectionService selService = (ISelectionService) serviceLocator.getService(ISelectionService.class);
        final List selectedJars = getSelectedElements(selService, IPackageFragmentRoot.class);
        boolean exportRoot = (selectedJars.size() == 1);
        if (exportRoot) {
            additions.addContributionItem(new ActionContributionItem(new ExportSourceAction(selectedJars)),
                    new Expression() {

                        @Override
                        public EvaluationResult evaluate(IEvaluationContext context) throws CoreException {
                            return EvaluationResult.TRUE;
                        }
                    });
            return;
        }

        if (selectedJars.size() > 1)
            return;

        final List selectedPackages = getSelectedElements(selService, IPackageFragment.class);
        final List selectedClasses = getSelectedElements(selService, IClassFile.class);
        selectedClasses.addAll(selectedPackages);
        boolean exportClasses = (!selectedClasses.isEmpty());
        if (exportClasses) {
            additions.addContributionItem(new ActionContributionItem(new ExportSourceAction(selectedClasses)),
                    new Expression() {

                        @Override
                        public EvaluationResult evaluate(IEvaluationContext context) throws CoreException {
                            boolean menuVisible = isMenuVisible(selectedClasses);

                            if (menuVisible)
                                return EvaluationResult.TRUE;

                            return EvaluationResult.FALSE;
                        }
                    });
            return;
        }

    }

    private boolean isMenuVisible(List selection) {
        IPackageFragmentRoot root = null;
        for (int i = 0; i < selection.size(); i++) {
            IPackageFragmentRoot packRoot = null;
            Object obj = selection.get(i);
            if (obj instanceof IPackageFragment) {
                IPackageFragment packageFragment = (IPackageFragment) obj;
                packRoot = (IPackageFragmentRoot) packageFragment.getParent();
            } else if (obj instanceof IClassFile) {
                IClassFile classFile = (IClassFile) obj;
                packRoot = (IPackageFragmentRoot) classFile.getParent().getParent();
            } else
                return false;
            if (root == null) {
                root = packRoot;
            } else {
                if (root != packRoot)
                    return false;
            }
        }
        return true;
    }

    private List<Object> getSelectedElements(ISelectionService selService, Class eleClass) {

        Iterator<?> selections = getSelections(selService);
        List<Object> elements = new ArrayList<>();

        while ((selections != null) && selections.hasNext()) {
            Object select = selections.next();

            if (eleClass.isInstance(select))
                elements.add(select);
        }

        return elements;
    }

    private Iterator<Object> getSelections(ISelectionService selService) {
        ISelection selection = selService.getSelection();

        if (selection != null) {
            if (selection instanceof IStructuredSelection) {
                IStructuredSelection structuredSelection = (IStructuredSelection) selection;
                return structuredSelection.iterator();
            }
        }

        return null;
    }

}
