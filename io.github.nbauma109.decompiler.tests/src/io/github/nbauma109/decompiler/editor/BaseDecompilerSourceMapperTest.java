package io.github.nbauma109.decompiler.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.eclipse.core.runtime.CoreException;
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

    private static final String PACKAGE_COM_EXAMPLE_FOO_BAR = "com.example.foo.bar";
    private static final String VERSION_SEPARATOR = " version ";
    private static final String TRANSFORMER_API_LABEL = " as part of transformer-api ";
    private static final String TRANSFORMER_API_VERSION_KEY = "transformer-api-version";
    private static final String HELLO_WORLD_CLASS_PATH = "target/classes/HelloWorld.class";

    private IProject project;
    private IJavaProject javaProject;
    private BaseDecompilerSourceMapper vineflower;
    private BaseDecompilerSourceMapper jdCore;

    @Before
    public void setUp() throws CoreException {
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
    public void tearDown() throws CoreException {
        if (project != null && project.exists()) {
            project.delete(true, true, null);
        }
    }

    @Test
    public void testIsSourceLookupEligible() throws CoreException {
        IType typeInFooBar = createType(PACKAGE_COM_EXAMPLE_FOO_BAR, "SampleOne");

        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, new String[0]));
        assertFalse(vineflower.isSourceLookupEligible(typeInFooBar, new String[] { PACKAGE_COM_EXAMPLE_FOO_BAR }));
        assertFalse(vineflower.isSourceLookupEligible(typeInFooBar, new String[] { "com.example.foo" }));
        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, new String[] { "com.example.food" }));

        IType typeInFoobar = createType("com.foobar", "SampleTwo");
        assertTrue(vineflower.isSourceLookupEligible(typeInFoobar, new String[] { "com.foo" }));

        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, new String[] { "", "com.other" }));
        assertTrue(vineflower.isSourceLookupEligible(null, new String[] { "com.example" }));
    }


    @Test
    public void testIsSourceLookupEligibleStringOverload() throws CoreException {
        IType typeInFooBar = createType(PACKAGE_COM_EXAMPLE_FOO_BAR, "SampleThree");

        assertFalse(vineflower.isSourceLookupEligible(typeInFooBar, "com.other, com.example.foo"));
        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, "com.other,com.example.food"));
        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, "com.other,"));
        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, ""));
        assertTrue(vineflower.isSourceLookupEligible(typeInFooBar, (String) null));
    }

    @Test
    public void testPrintDecompileReport() {
        StringBuilder source = new StringBuilder("class A {}");
        vineflower.printDecompileReport(source, "path/to/file", Collections.emptyList(), RealignStatus.PARSE_ERROR);
        assertEquals("class A {}\n"
                + "\n"
                + "/*\n"
                + "\tDECOMPILATION REPORT\n"
                + "\n"
                + "\tDecompiled from: path/to/file\n"
                + "\tTotal time: "
                + vineflower.getDecompilationTime()
                + " ms\n"
                + "\t\n"
                + "\tDecompiled with "
                + vineflower.getDecompilerName()
                + VERSION_SEPARATOR
                + vineflower.getDecompilerVersion()
                + TRANSFORMER_API_LABEL
                + vineflower.getVersion(TRANSFORMER_API_VERSION_KEY)
                + " (https://github.com/nbauma109/transformer-api).\n"
                + "\tParse and realign phase failed with ParseException. Please report issue to https://github.com/nbauma109/jd-util/issues.\n"
                + "*/", source.toString());
    }

    @Test
    public void testDecompileRealignmentTurnedOff() {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        prefs.setValue(JavaDecompilerPlugin.PREF_DISPLAY_METADATA, true);
        prefs.setValue(JavaDecompilerPlugin.ALIGN, false);
        File file = new File(HELLO_WORLD_CLASS_PATH);
        String decompiledOutput = vineflower.decompile(file);
        assertEqualsIgnoreEOL("public class HelloWorld {\r\n"
                + "\tpublic static void main(String[] args) {\r\n"
                + "\t\tSystem.out.println(\"Hello World!\");// 3\r\n"
                + "\t}// 4\r\n"
                + "}\n"
                + "\n"
                + "/*\n"
                + "\tDECOMPILATION REPORT\n"
                + "\n"
                + "\tDecompiled from: "
                + file.getAbsolutePath()
                + "\n"
                + "\tTotal time: "
                + vineflower.getDecompilationTime()
                + " ms\n"
                + "\t\n"
                + "\tDecompiled with "
                + vineflower.getDecompilerName()
                + VERSION_SEPARATOR
                + vineflower.getDecompilerVersion()
                + TRANSFORMER_API_LABEL
                + vineflower.getVersion(TRANSFORMER_API_VERSION_KEY)
                + " (https://github.com/nbauma109/transformer-api).\n"
                + "\tRealignment is turned off.\n"
                + "*/", decompiledOutput);
    }

    @Test
    public void testDecompileParsedAndRealigned() {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        prefs.setValue(JavaDecompilerPlugin.PREF_DISPLAY_METADATA, true);
        prefs.setValue(JavaDecompilerPlugin.ALIGN, true);
        File file = new File(HELLO_WORLD_CLASS_PATH);
        String decompiledOutput = vineflower.decompile(file);
        assertEquals("/*   */ public class HelloWorld {\n"
                + "/*   */   public static void main(String[] args) {\n"
                + "/* 3 */     System.out.println(\"Hello World!\");\n"
                + "/*   */   }\n"
                + "/*   */ }\n"
                + "\n"
                + "\n"
                + "/*\n"
                + "\tDECOMPILATION REPORT\n"
                + "\n"
                + "\tDecompiled from: "
                + file.getAbsolutePath()
                + "\n"
                + "\tTotal time: "
                + vineflower.getDecompilationTime()
                + " ms\n"
                + "\t\n"
                + "\tDecompiled with "
                + vineflower.getDecompilerName()
                + VERSION_SEPARATOR
                + vineflower.getDecompilerVersion()
                + TRANSFORMER_API_LABEL
                + vineflower.getVersion(TRANSFORMER_API_VERSION_KEY)
                + " (https://github.com/nbauma109/transformer-api).\n"
                + "\tParsed and realigned with jd-util "
                + vineflower.getVersion("JD-Util-Version")
                + " (https://github.com/nbauma109/jd-util).\n"
                + "*/", decompiledOutput);
    }

    @Test
    public void testDecompileNativelyRealigned() {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        prefs.setValue(JavaDecompilerPlugin.PREF_DISPLAY_METADATA, true);
        prefs.setValue(JavaDecompilerPlugin.ALIGN, true);
        File file = new File(HELLO_WORLD_CLASS_PATH);
        String decompiledOutput = jdCore.decompile(file);
        assertEquals("/*   */ public class HelloWorld {\n"
                + "/*   */   public static void main(String[] args) {\n"
                + "/* 3 */     System.out.println(\"Hello World!\");\n"
                + "/*   */   }\n"
                + "/*   */ }\n"
                + "\n"
                + "\n"
                + "/*\n"
                + "\tDECOMPILATION REPORT\n"
                + "\n"
                + "\tDecompiled from: "
                + file.getAbsolutePath()
                + "\n"
                + "\tTotal time: "
                + jdCore.getDecompilationTime()
                + " ms\n"
                + "\t\n"
                + "\tDecompiled and natively realigned with "
                + jdCore.getDecompilerName()
                + VERSION_SEPARATOR
                + jdCore.getDecompilerVersion()
                + TRANSFORMER_API_LABEL
                + jdCore.getVersion(TRANSFORMER_API_VERSION_KEY)
                + " (https://github.com/nbauma109/transformer-api).\n"
                + "*/", decompiledOutput);
    }

    private static void assertEqualsIgnoreEOL(String expected, String actual) {
        String normalizedExpected = expected.replace("\r\n", "\n");
        String normalizedActual = actual.replace("\r\n", "\n");
        assertEquals(normalizedExpected, normalizedActual);
    }

    private IType createType(String packageName, String typeName) throws CoreException {
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
