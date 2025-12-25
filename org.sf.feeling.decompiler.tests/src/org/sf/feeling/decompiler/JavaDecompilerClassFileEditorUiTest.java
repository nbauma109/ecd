package org.sf.feeling.decompiler;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sf.feeling.decompiler.editor.JavaDecompilerClassFileEditor;

public class JavaDecompilerClassFileEditorUiTest {

    private static final String PROJECT_NAME = "ecd-decompiler-editor-test";
    private static final String SRC_FOLDER = "src";
    private static final String BIN_FOLDER = "bin";
    private static final String EDITOR_ID = "org.sf.feeling.decompiler.ClassFileEditor";

    private IProject project;

    @Before
    public void setUp() throws Exception {
        project = createJavaProject(PROJECT_NAME);
    }

    @After
    public void tearDown() throws Exception {
        safeCloseAllEditors();
        if (project != null && project.exists()) {
            project.delete(true, true, new NullProgressMonitor());
        }
    }

    @Test
    public void opensClassFileInDecompilerEditorAndPopulatesBuffer() throws Exception {
        IClassFile classFile = createAndBuildClass(
                "p",
                "C",
                "package p;\n" +
                "public class C {\n" +
                "  public int m() { return 42; }\n" +
                "}\n");

        IEditorPart editorPart = openClassFileWithDecompilerEditor(classFile);

        assertTrue("Expected JavaDecompilerClassFileEditor but got: " + editorPart.getClass().getName(),
                editorPart instanceof JavaDecompilerClassFileEditor);

        JavaDecompilerClassFileEditor editor = (JavaDecompilerClassFileEditor) editorPart;

        assertNotNull("Expected class buffer to be created", editor.getClassBuffer());
        String contents = new String(editor.getClassBuffer().getCharacters());

        assertTrue("Expected decompiled source to contain package declaration", contents.contains("package p"));
        assertTrue("Expected decompiled source to contain class name", contents.contains("class C"));
        assertTrue("Expected decompiled source to contain method signature", contents.contains("int m()"));
        assertTrue("Expected decompiled source to contain return statement", contents.contains("return 42"));
        assertTrue("Expected decompiled source to be non-trivial", contents.length() > 200);

        assertInOrder(contents, "package p", "class C", "int m()", "return 42");
    }

    private static IProject createJavaProject(String name) throws Exception {
        IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(name);

        if (!p.exists()) {
            p.create(new NullProgressMonitor());
        }
        p.open(new NullProgressMonitor());

        IProjectDescription description = p.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        p.setDescription(description, new NullProgressMonitor());

        IJavaProject javaProject = JavaCore.create(p);

        IFolder srcFolder = p.getFolder(SRC_FOLDER);
        if (!srcFolder.exists()) {
            srcFolder.create(true, true, new NullProgressMonitor());
        }

        IFolder binFolder = p.getFolder(BIN_FOLDER);
        if (!binFolder.exists()) {
            binFolder.create(true, true, new NullProgressMonitor());
        }

        javaProject.setOutputLocation(binFolder.getFullPath(), new NullProgressMonitor());
        JavaProjectClasspath.configure(javaProject, srcFolder.getFullPath());

        return p;
    }

    private IClassFile createAndBuildClass(String packageName, String typeName, String source) throws Exception {
        IJavaProject javaProject = JavaCore.create(project);

        IPackageFragmentRoot srcRoot = javaProject.getPackageFragmentRoot(project.getFolder(SRC_FOLDER));
        IPackageFragment pkg = srcRoot.createPackageFragment(packageName, true, new NullProgressMonitor());
        pkg.createCompilationUnit(typeName + ".java", source, true, new NullProgressMonitor());

        project.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());

        IFolder binFolder = project.getFolder(BIN_FOLDER);
        IPath classPath = binFolder.getFullPath()
                .append(packageName.replace('.', '/'))
                .append(typeName + ".class");

        IFile classFileResource = ResourcesPlugin.getWorkspace().getRoot().getFile(classPath);
        if (!classFileResource.exists()) {
            throw new AssertionError("Expected compiled class file to exist at: " + classPath.toString());
        }

        IPackageFragmentRoot binRoot = javaProject.getPackageFragmentRoot(binFolder);
        IPackageFragment binPkg = binRoot.getPackageFragment(packageName);
        IClassFile classFile = binPkg.getClassFile(typeName + ".class");

        if (classFile == null || !classFile.exists()) {
            throw new AssertionError("Expected IClassFile to exist for: " + packageName + "." + typeName);
        }

        return classFile;
    }

    private static IEditorPart openClassFileWithDecompilerEditor(final IClassFile classFile) {
        final IEditorPart[] result = new IEditorPart[1];

        Display.getDefault().syncExec(() -> {
            try {
                IWorkbenchPage page = requireWorkbenchPage();

                IEditorPart jdtEditor = JavaUI.openInEditor(classFile);
                if (jdtEditor == null) {
                    throw new AssertionError("JavaUI.openInEditor returned null for class file");
                }

                result[0] = page.openEditor(jdtEditor.getEditorInput(), EDITOR_ID, true);

                if (jdtEditor != result[0]) {
                    page.closeEditor(jdtEditor, false);
                }
            } catch (Exception e) {
                throw new AssertionError("Failed to open class file using editor id: " + EDITOR_ID, e);
            }
        });

        return result[0];
    }

    private static IWorkbenchPage requireWorkbenchPage() {
        if (!PlatformUI.isWorkbenchRunning()) {
            throw new AssertionError("Workbench is not running. Launch as a UI JUnit Plug-in Test.");
        }

        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
            if (windows != null && windows.length > 0) {
                window = windows[0];
            }
        }

        if (window == null) {
            throw new AssertionError("No workbench window available. Launch as a UI JUnit Plug-in Test.");
        }

        IWorkbenchPage page = window.getActivePage();
        if (page == null) {
            throw new AssertionError("No workbench page available. Launch as a UI JUnit Plug-in Test.");
        }

        return page;
    }

    private static void safeCloseAllEditors() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return;
        }

        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return;
        }

        display.syncExec(() -> {
            if (!PlatformUI.isWorkbenchRunning()) {
                return;
            }
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window != null && window.getActivePage() != null) {
                window.getActivePage().closeAllEditors(false);
            }
        });
    }

    private static void assertInOrder(String text, String first, String second, String third, String fourth) {
        int p1 = indexOrFail(text, first, 0);
        int p2 = indexOrFail(text, second, p1 + first.length());
        int p3 = indexOrFail(text, third, p2 + second.length());
        indexOrFail(text, fourth, p3 + third.length());
    }

    private static int indexOrFail(String text, String token, int fromIndex) {
        int idx = text.indexOf(token, fromIndex);
        assertTrue("Missing token: " + token, idx >= 0);
        return idx;
    }

    static final class JavaProjectClasspath {

        private JavaProjectClasspath() {
        }

        static void configure(IJavaProject javaProject, IPath sourceFolderPath) throws JavaModelException {
            org.eclipse.jdt.core.IClasspathEntry jre = JavaCore.newContainerEntry(
                    new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER"));

            org.eclipse.jdt.core.IClasspathEntry src = JavaCore.newSourceEntry(sourceFolderPath);

            javaProject.setRawClasspath(
                    new org.eclipse.jdt.core.IClasspathEntry[] { jre, src },
                    new NullProgressMonitor());
        }
    }
}
