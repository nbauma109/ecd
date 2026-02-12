package io.github.nbauma109.decompiler.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.prefs.BackingStoreException;

public class SortMemberUtilPluginTest {

    @Before
    public void setUp() throws Exception {
        resetCachedDecompilerSourceFolder();
        deleteDecompilerProjectIfPresent();
        configureMemberSortPreferences();
        waitForWorkspaceJobs();
    }

    @After
    public void tearDown() throws Exception {
        deleteDecompilerProjectIfPresent();
        resetCachedDecompilerSourceFolder();
        waitForWorkspaceJobs();
    }

    @Test
    public void sortMember_groupsFieldsBeforeMethods_andCleansUpCompilationUnit() throws Exception {
        String input =
                """
                package com.example;

        public class Example {
            public void bMethod() {}
            private int b;
            public void aMethod() {}
            private int a;
        }
        """;

        String sorted = SortMemberUtil.sortMember("com.example", "com/example/Example.class", input);

        assertTrue(sorted.contains("class Example"));
        assertTrue(sorted.contains("private int a"));
        assertTrue(sorted.contains("private int b"));
        assertTrue(sorted.contains("public void aMethod"));
        assertTrue(sorted.contains("public void bMethod"));

        int fieldA = sorted.indexOf("private int a");
        int fieldB = sorted.indexOf("private int b");
        assertTrue(fieldA >= 0);
        assertTrue(fieldB >= 0);

        int methodA = sorted.indexOf("public void aMethod");
        int methodB = sorted.indexOf("public void bMethod");
        assertTrue(methodA >= 0);
        assertTrue(methodB >= 0);

        int firstMethod = Math.min(methodA, methodB);
        assertTrue(fieldA < firstMethod);
        assertTrue(fieldB < firstMethod);

        assertTrue(methodA < methodB);

        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(".decompiler");
        assertTrue(project.exists());

        IFolder src = project.getFolder("src");
        assertTrue(src.exists());

        IFolder pkg = src.getFolder("com/example");
        assertTrue(pkg.exists());

        IResource[] members = pkg.members();
        int javaFiles = 0;
        for (IResource member : members) {
            if (member instanceof IFile) {
                IFile file = (IFile) member;
                if ("java".equalsIgnoreCase(file.getFileExtension())) {
                    javaFiles++;
                }
            }
        }
        assertEquals(0, javaFiles);
    }

    private static void configureMemberSortPreferences() throws BackingStoreException {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode("org.eclipse.jdt.ui");
        prefs.put(PreferenceConstants.APPEARANCE_MEMBER_SORT_ORDER, "T,SF,SI,SM,F,I,C,M");
        prefs.putBoolean(PreferenceConstants.APPEARANCE_ENABLE_VISIBILITY_SORT_ORDER, false);
        prefs.put(PreferenceConstants.APPEARANCE_VISIBILITY_SORT_ORDER, "B,R,D,V");
        prefs.flush();
    }

    private static void deleteDecompilerProjectIfPresent() throws Exception {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(".decompiler");
        if (!project.exists()) {
            return;
        }
        if (project.isOpen()) {
            project.close(new NullProgressMonitor());
        }
        project.delete(true, true, new NullProgressMonitor());
    }

    private static void resetCachedDecompilerSourceFolder() throws Exception {
        Field field = SortMemberUtil.class.getDeclaredField("decompilerSourceFolder");
        field.setAccessible(true);
        field.set(null, null);
    }

    private static void waitForWorkspaceJobs() throws Exception {
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, new NullProgressMonitor());
        Job.getJobManager().join(ResourcesPlugin.FAMILY_MANUAL_BUILD, new NullProgressMonitor());
    }
}
