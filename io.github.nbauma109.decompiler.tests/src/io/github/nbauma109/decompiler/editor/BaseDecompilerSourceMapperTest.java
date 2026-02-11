package io.github.nbauma109.decompiler.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
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
import org.eclipse.jface.preference.IPreferenceStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;

public class BaseDecompilerSourceMapperTest {

    private IProject project;
    private IJavaProject javaProject;
    private BaseDecompilerSourceMapper vineflower;
    private BaseDecompilerSourceMapper jdCore;

    @Before
    public void setUp() throws Exception {
        vineflower = new BaseDecompilerSourceMapper("");
        jdCore = new BaseDecompilerSourceMapper("JD-Core");

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

        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, new String[0]));
        assertFalse(vineflower.isSourceLookupEligible(typeInFooBar, new String[] { "com.example.foo.bar" }));
        assertFalse(vineflower.isSourceLookupEligible(typeInFooBar, new String[] { "com.example.foo" }));
        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, new String[] { "com.example.food" }));

        IType typeInFoobar = createType("com.foobar", "SampleTwo");
        assertTrue(vineflower.isSourceLookupEligible(typeInFoobar, new String[] { "com.foo" }));

        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, new String[] { "", "com.other" }));
        assertTrue(vineflower.isSourceLookupEligible(null, new String[] { "com.example" }));
    }


    @Test
    public void testIsSourceLookupEligibleStringOverload() throws Exception {
        IType typeInFooBar = createType("com.example.foo.bar", "SampleThree");

        assertFalse(vineflower.isSourceLookupEligible(typeInFooBar, "com.other, com.example.foo"));
        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, "com.other,com.example.food"));
        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, "com.other,"));
        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, ""));
        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, (String) null));
    }

    @Test
    public void testPrintDecompileReport() throws Exception {
        StringBuilder source = new StringBuilder("class A {}");
        vineflower.printDecompileReport(source, "path/to/file", Collections.emptyList(), RealignStatus.PARSE_ERROR);
        assertEquals("class A {}\n"
                + "\n"
                + "/*\n"
                + "	DECOMPILATION REPORT\n"
                + "\n"
                + "	Decompiled from: path/to/file\n"
                + "	Total time: "
                + vineflower.getDecompilationTime() 
                + " ms\n"
                + "	\n"
                + "	Decompiled with "
                + vineflower.getDecompilerName()
                + " version "
                + vineflower.getDecompilerVersion()
                + " as part of transformer-api 4.2.0 (https://github.com/nbauma109/transformer-api).\n"
                + "	Parse and realign phase failed with ParseException. Please report issue to https://github.com/nbauma109/jd-util/issues.\n"
                + "*/", source.toString());
    }

    @Test
    public void testDecompileRealignmentTurnedOff() throws Exception {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        prefs.setValue(JavaDecompilerPlugin.PREF_DISPLAY_METADATA, true);
        prefs.setValue(JavaDecompilerPlugin.ALIGN, false);
        File file = new File("target/classes/HelloWorld.class");
        String decompiledOutput = vineflower.decompile(file);
        assertEquals("public class HelloWorld {\r\n"
                + "	public static void main(String[] args) {\r\n"
                + "		System.out.println(\"Hello World!\");// 3\r\n"
                + "	}// 4\r\n"
                + "}\n"
                + "\n"
                + "/*\n"
                + "	DECOMPILATION REPORT\n"
                + "\n"
                + "	Decompiled from: "
                + file.getAbsolutePath()
                + "\n"
                + "	Total time: "
                + vineflower.getDecompilationTime()
                + " ms\n"
                + "	\n"
                + "	Decompiled with "
                + vineflower.getDecompilerName()
                + " version "
                + vineflower.getDecompilerVersion()
                + " as part of transformer-api "
                + vineflower.getVersion("transformer-api-version")
                + " (https://github.com/nbauma109/transformer-api).\n"
                + "	Realignment is turned off.\n"
                + "*/", decompiledOutput);
    }

    @Test
    public void testDecompileParsedAndRealigned() throws Exception {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        prefs.setValue(JavaDecompilerPlugin.PREF_DISPLAY_METADATA, true);
        prefs.setValue(JavaDecompilerPlugin.ALIGN, true);
        File file = new File("target/classes/HelloWorld.class");
        String decompiledOutput = vineflower.decompile(file);
        assertEquals("/*   */ public class HelloWorld {\n"
                + "/*   */   public static void main(String[] args) {\n"
                + "/* 3 */     System.out.println(\"Hello World!\");\n"
                + "/*   */   }\n"
                + "/*   */ }\n"
                + "\n"
                + "\n"
                + "/*\n"
                + "	DECOMPILATION REPORT\n"
                + "\n"
                + "	Decompiled from: "
                + file.getAbsolutePath()
                + "\n"
                + "	Total time: "
                + vineflower.getDecompilationTime()
                + " ms\n"
                + "	\n"
                + "	Decompiled with "
                + vineflower.getDecompilerName()
                + " version "
                + vineflower.getDecompilerVersion()
                + " as part of transformer-api "
                + vineflower.getVersion("transformer-api-version")
                + " (https://github.com/nbauma109/transformer-api).\n"
                + "	Parsed and realigned with jd-util "
                + vineflower.getVersion("JD-Util-Version")
                + " (https://github.com/nbauma109/jd-util).\n"
                + "*/", decompiledOutput);
    }

    @Test
    public void testDecompileNativelyRealigned() throws Exception {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        prefs.setValue(JavaDecompilerPlugin.PREF_DISPLAY_METADATA, true);
        prefs.setValue(JavaDecompilerPlugin.ALIGN, true);
        File file = new File("target/classes/HelloWorld.class");
        String decompiledOutput = jdCore.decompile(file);
        assertEquals("/*   */ public class HelloWorld {\n"
                + "/*   */   public static void main(String[] args) {\n"
                + "/* 3 */     System.out.println(\"Hello World!\");\n"
                + "/*   */   }\n"
                + "/*   */ }\n"
                + "\n"
                + "\n"
                + "/*\n"
                + "	DECOMPILATION REPORT\n"
                + "\n"
                + "	Decompiled from: "
                + file.getAbsolutePath()
                + "\n"
                + "	Total time: "
                + jdCore.getDecompilationTime()
                + " ms\n"
                + "	\n"
                + "	Decompiled and natively realigned with "
                + jdCore.getDecompilerName()
                + " version "
                + jdCore.getDecompilerVersion()
                + " as part of transformer-api "
                + jdCore.getVersion("transformer-api-version")
                + " (https://github.com/nbauma109/transformer-api).\n"
                + "*/", decompiledOutput);
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
