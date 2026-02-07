package io.github.nbauma109.decompiler.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BaseDecompilerSourceMapperTest {

    private IProject project;
    private IJavaProject javaProject;
    private BaseDecompilerSourceMapper subject;

    @Before
    public void setUp() throws Exception {
        subject = new BaseDecompilerSourceMapper("");

        String projectName = "BaseDecompilerSourceMapperTestProject_" + System.currentTimeMillis();
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();

        project = workspaceRoot.getProject(projectName);
        if (project.exists()) {
            project.delete(true, true, null);
        }

        project.create(null);
        project.open(null);

        IProjectDescription description = project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, null);

        javaProject = JavaCore.create(project);

        IFolder sourceFolder = project.getFolder("src");
        if (!sourceFolder.exists()) {
            sourceFolder.create(true, true, null);
        }

        IFolder outputFolder = project.getFolder("bin");
        if (!outputFolder.exists()) {
            outputFolder.create(true, true, null);
        }

        javaProject.setOutputLocation(outputFolder.getFullPath(), null);

        IClasspathEntry[] classpath = new IClasspathEntry[] {
                JavaCore.newSourceEntry(sourceFolder.getFullPath())
        };
        javaProject.setRawClasspath(classpath, null);
    }

    @After
    public void tearDown() throws Exception {
        if (project != null && project.exists()) {
            project.delete(true, true, null);
        }
    }

    @Test
    public void testIsSourceLookupEligible() throws Exception {
        IType typeInFooBar = createType("com.example.foo.bar", "SampleOne");

        assertTrue(subject.isSourceLookupEligible(typeInFooBar, new String[0]));
        assertFalse(subject.isSourceLookupEligible(typeInFooBar, new String[] { "com.example.foo.bar" }));
        assertFalse(subject.isSourceLookupEligible(typeInFooBar, new String[] { "com.example.foo" }));
        assertTrue(subject.isSourceLookupEligible(typeInFooBar, new String[] { "com.example.food" }));

        IType typeInFoobar = createType("com.foobar", "SampleTwo");
        assertTrue(subject.isSourceLookupEligible(typeInFoobar, new String[] { "com.foo" }));

        assertTrue(subject.isSourceLookupEligible(typeInFooBar, new String[] { "", "com.other" }));
        assertTrue(subject.isSourceLookupEligible(null, new String[] { "com.example" }));
    }


    @Test
    public void testIsSourceLookupEligibleStringOverload() throws Exception {
        IType typeInFooBar = createType("com.example.foo.bar", "SampleThree");

        assertFalse(subject.isSourceLookupEligible(typeInFooBar, "com.other, com.example.foo"));
        assertTrue(subject.isSourceLookupEligible(typeInFooBar, "com.other,com.example.food"));
        assertTrue(subject.isSourceLookupEligible(typeInFooBar, "com.other,"));
        assertTrue(subject.isSourceLookupEligible(typeInFooBar, ""));
        assertTrue(subject.isSourceLookupEligible(typeInFooBar, (String) null));
    }

    @Test
    public void testBuildRealignSuccessReport() throws Exception {
    	assertTrue(BaseDecompilerSourceMapper.buildRealignSuccessReport().matches("Parsed and realigned by jd-util \\d+\\.\\d+\\.\\d+"));
    }

    @Test
    public void testPrintDecompileReport() throws Exception {
    	StringBuilder source = new StringBuilder("class A {}");
    	subject.printDecompileReport(source, "path/to/file", Collections.emptyList(), null);
    	assertEquals("class A {}\n"
    			+ "\n"
    			+ "/*\n"
    			+ "	DECOMPILATION REPORT\n"
    			+ "\n"
    			+ "	Decompiled from: path/to/file\n"
    			+ "	Total time: 0 ms\n"
    			+ "	\n"
    			+ "	Decompiled with "
    			+ subject.getDecompilerName()
    			+ " version "
    			+ subject.getDecompilerVersion()
    			+ ".\n"
    			+ "*/", source.toString());
    }

    private IType createType(String packageName, String typeName) throws Exception {
        IFolder sourceFolder = project.getFolder("src");
        IPackageFragmentRoot root = javaProject.getPackageFragmentRoot(sourceFolder);

        IPackageFragment packageFragment = root.createPackageFragment(packageName, true, null);

        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        source.append("public class ").append(typeName).append(" {\n");
        source.append("}\n");

        ICompilationUnit unit = packageFragment.createCompilationUnit(typeName + ".java", source.toString(), true, null);
        return unit.getType(typeName);
    }
}
