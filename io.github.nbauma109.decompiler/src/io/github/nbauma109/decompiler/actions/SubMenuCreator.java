/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.actions;

import java.util.List;

import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.editor.DecompilerType;
import io.github.nbauma109.decompiler.util.UIUtil;

public class SubMenuCreator implements IMenuCreator {

    private MenuManager dropDownMenuMgr;

    @Override
    public Menu getMenu(final Menu parent) {
        final Menu menu = new Menu(parent);
        fillMenu(menu);
        menu.addMenuListener(new MenuAdapter() {

            @Override
            public void menuShown(MenuEvent e) {
                while (menu.getItemCount() > 0) {
                    menu.getItem(0).dispose();
                }
                fillMenu(menu);
            }
        });
        return menu;
    }

    private void fillMenu(final Menu menu) {
        final MenuManager menuMgr = new MenuManager();

        for (String decompilerType : DecompilerType.getDecompilerTypes()) {
            menuMgr.add(JavaDecompilerPlugin.getDefault().getDecompileAction(decompilerType));
        }

        IContributionItem[] items = menuMgr.getItems();
        for (IContributionItem item : items) {
            IContributionItem newItem = item;
            if (item instanceof ActionContributionItem acItem) {
                newItem = new ActionContributionItem(acItem.getAction());
            }
            newItem.fill(menu, -1);
        }
    }

    @Override
    public Menu getMenu(Control parent) {
        createDropDownMenuMgr();
        return dropDownMenuMgr.createContextMenu(parent);
    }

    @Override
    public void dispose() {
        if (null != dropDownMenuMgr) {
            dropDownMenuMgr.dispose();
            dropDownMenuMgr = null;
        }
    }

    class PreferenceActionContributionItem extends ActionContributionItem {

        public PreferenceActionContributionItem(IAction action) {
            super(action);
        }

        @Override
        public boolean isEnabledAllowed() {
            return true;
        }
    }

    private void createDropDownMenuMgr() {
        if (dropDownMenuMgr == null) {
            dropDownMenuMgr = new MenuManager();

            for (String decompilerType : DecompilerType.getDecompilerTypes()) {
                dropDownMenuMgr.add(JavaDecompilerPlugin.getDefault().getDecompileAction(decompilerType));
            }

            dropDownMenuMgr.add(new Separator());

            dropDownMenuMgr.add(new SourceCodeAction());

            dropDownMenuMgr.add(new Separator());

            List list = UIUtil.getExportSelections();
            if (list != null) {
                dropDownMenuMgr.add(new ExportSourceAction(list));
            } else {
                dropDownMenuMgr.add(new ExportEditorSourceAction());
            }

            dropDownMenuMgr.add(new Separator());
            dropDownMenuMgr.add(new PreferenceActionContributionItem(new DecompilerPreferenceAction()));
        }
    }
}
