/*******************************************************************************
 * (C) 2022-2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.preferences;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.i18n.Messages;

public class PublicRepositoriesPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private static final String HTTPS_SEARCH_MAVEN_ORG = "https://search.maven.org"; // $NON-NLS-1$
    private static final String HTTPS_CENTRAL_SONATYPE_ORG = "https://sonatype.central.com"; // $NON-NLS-1$
    private static final String HTTPS_JITPACK_IO = "https://jitpack.io"; // $NON-NLS-1$
    private static final String HTTPS_REPOSITORY_CLOUDERA = "https://repository.cloudera.com/artifactory/webapp/home.html"; // $NON-NLS-1$
    private static final String HTTPS_MAVEN_ALFRESCO = "https://maven.alfresco.com/nexus"; // $NON-NLS-1$
    private static final String HTTPS_REPOSITORY_APACHE_ORG = "https://repository.apache.org"; // $NON-NLS-1$
    private static final String HTTPS_REPO_GRAILS_ORG = "https://repo.grails.org/grails/webapp/home.html"; // $NON-NLS-1$

    public PublicRepositoriesPreferencePage() {
        super(FieldEditorPreferencePage.GRID);
        setPreferenceStore(JavaDecompilerPlugin.getDefault().getPreferenceStore());
    }

    @Override
    protected void createFieldEditors() {
        Group g = new Group(getFieldEditorParent(), SWT.NONE);
        g.setText(Messages.getString("PublicRepositoriesPreferencePage.Label.PublicRepositoriesSettings")); //$NON-NLS-1$
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        g.setLayoutData(gd);

        addField(new CheckFieldEditor(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_CENTRAL, HTTPS_SEARCH_MAVEN_ORG, g));
        addField(new CheckFieldEditor(JavaDecompilerPlugin.PUBLIC_REPO_SONATYPE_CENTRAL, HTTPS_CENTRAL_SONATYPE_ORG, g));
        addField(new CheckFieldEditor(JavaDecompilerPlugin.PUBLIC_REPO_JITPACK, HTTPS_JITPACK_IO, g));
        addField(new CheckFieldEditor(JavaDecompilerPlugin.PUBLIC_REPO_CLOUDERA, HTTPS_REPOSITORY_CLOUDERA, g));
        addField(new CheckFieldEditor(JavaDecompilerPlugin.PUBLIC_REPO_MAVEN_ALFRESCO, HTTPS_MAVEN_ALFRESCO, g));
        addField(new CheckFieldEditor(JavaDecompilerPlugin.PUBLIC_REPO_APACHE_ORG, HTTPS_REPOSITORY_APACHE_ORG, g));
        addField(new CheckFieldEditor(JavaDecompilerPlugin.PUBLIC_REPO_GRAILS_ORG, HTTPS_REPO_GRAILS_ORG, g));

        GridLayout layout = (GridLayout) g.getLayout();
        layout.marginWidth = layout.marginHeight = 5;
        g.layout();

        getFieldEditorParent().layout();
    }

    @Override
    public void init(IWorkbench arg0) {
        // TODO document why this method is empty
    }
}
