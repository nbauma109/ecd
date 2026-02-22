/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.source.attach.handler;

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
import io.github.nbauma109.decompiler.source.attach.utils.SourceAttachUtil;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class AttachSourceContributionFactory extends ExtensionContributionFactory {

    @Override
    public void createContributionItems(IServiceLocator serviceLocator, IContributionRoot additions) {
        final ISelectionService selService = serviceLocator.getService(ISelectionService.class);
        final List selectedJars = getSelectedElements(selService, IPackageFragmentRoot.class);
        boolean attachRoot = (selectedJars.size() == 1);
        if (attachRoot) {
            additions.addContributionItem(new ActionContributionItem(new AttachSourceAction(selectedJars)),
                    new Expression() {

                @Override
                public EvaluationResult evaluate(IEvaluationContext context) throws CoreException {
                    boolean menuVisible = SourceAttachUtil.needDownloadSource(selectedJars);

                    if (menuVisible) {
                        return EvaluationResult.TRUE;
                    }

                    return EvaluationResult.FALSE;
                }
            });
            return;
        }

        if (selectedJars.size() > 1) {
            return;
        }

        final List selectedPackages = getSelectedElements(selService, IPackageFragment.class);
        final List selectedClasses = getSelectedElements(selService, IClassFile.class);
        selectedClasses.addAll(selectedPackages);
        boolean attachClasses = (!selectedClasses.isEmpty());
        if (attachClasses) {
            additions.addContributionItem(new ActionContributionItem(new AttachSourceAction(selectedClasses)),
                    new Expression() {

                @Override
                public EvaluationResult evaluate(IEvaluationContext context) throws CoreException {
                    boolean menuVisible = SourceAttachUtil.needDownloadSource(selectedClasses);

                    if (menuVisible) {
                        return EvaluationResult.TRUE;
                    }

                    return EvaluationResult.FALSE;
                }
            });
        }

    }

    private List getSelectedElements(ISelectionService selService, Class eleClass) {

        Iterator selections = getSelections(selService);
        List elements = new ArrayList();

        while ((selections != null) && selections.hasNext()) {
            Object select = selections.next();

            if (eleClass.isInstance(select)) {
                elements.add(select);
            }
        }

        return elements;
    }

    private Iterator getSelections(ISelectionService selService) {
        ISelection selection = selService.getSelection();

        if ((selection != null) && (selection instanceof IStructuredSelection structuredSelection)) {
            return structuredSelection.iterator();
        }

        return null;
    }

}
