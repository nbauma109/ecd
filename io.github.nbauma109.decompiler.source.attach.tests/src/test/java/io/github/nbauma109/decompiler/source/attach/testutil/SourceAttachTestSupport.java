package io.github.nbauma109.decompiler.source.attach.testutil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.osgi.framework.Bundle;

public final class SourceAttachTestSupport {

    private SourceAttachTestSupport() {
    }

    public static File resolveBundleEntryAsFile(String bundleId, String entryPath) throws IOException {
        Bundle bundle = Platform.getBundle(bundleId);
        if (bundle == null) {
            throw new IOException("Test bundle must be available: " + bundleId); //$NON-NLS-1$
        }

        URL entry = bundle.getEntry(entryPath);
        if (entry == null) {
            throw new IOException("Missing bundle entry: " + entryPath); //$NON-NLS-1$
        }

        URL resolved = FileLocator.toFileURL(entry);
        IPath path = new Path(resolved.getPath());
        File file = path.toFile();
        if (!file.exists()) {
            throw new IOException("Resolved bundle entry does not exist: " + file); //$NON-NLS-1$
        }
        return file;
    }

    public static File createTargetTempDir(String prefix) {
        File targetDir = new File("target"); //$NON-NLS-1$
        if (!targetDir.exists() && !targetDir.mkdirs() && !targetDir.exists()) {
            throw new IllegalStateException("Unable to create target directory: " + targetDir.getAbsolutePath()); //$NON-NLS-1$
        }

        File tempDir = new File(targetDir, prefix + File.separator + System.nanoTime());
        if (!tempDir.mkdirs() && !tempDir.exists()) {
            throw new IllegalStateException("Unable to create test directory: " + tempDir.getAbsolutePath()); //$NON-NLS-1$
        }
        return tempDir;
    }

    public static SingleLibrarySetup createSingleLibraryProjectFromBundleJar(String bundleId, String entryPath,
            String projectName, List<IProject> projectsToDelete) throws IOException, CoreException {
        File binaryJar = resolveBundleEntryAsFile(bundleId, entryPath);
        JavaProjectSetup setup = createJavaProjectWithLibraries(projectName, projectsToDelete, new LibrarySpec(binaryJar, null));
        return new SingleLibrarySetup(binaryJar, setup.project(), setup.javaProject(), setup.roots().get(0));
    }

    public static JavaProjectSetup createJavaProjectWithLibraries(String projectName, List<IProject> projectsToDelete,
            LibrarySpec... libs) throws CoreException {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);

        if (project.exists()) {
            project.delete(true, true, null);
        }
        project.create(null);
        project.open(null);
        projectsToDelete.add(project);

        IProjectDescription description = project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, null);

        IJavaProject javaProject = JavaCore.create(project);

        List<IClasspathEntry> entries = new ArrayList<>();
        for (LibrarySpec lib : libs) {
            IPath jarPath = new Path(lib.binaryJar().getAbsolutePath());
            IPath srcPath = null;
            if (lib.sourceJarOrZip() != null) {
                srcPath = new Path(lib.sourceJarOrZip().getAbsolutePath());
            }
            entries.add(JavaCore.newLibraryEntry(jarPath, srcPath, null));
        }

        javaProject.setRawClasspath(entries.toArray(new IClasspathEntry[0]), null);

        List<IPackageFragmentRoot> roots = new ArrayList<>();
        IPackageFragmentRoot[] allRoots = javaProject.getAllPackageFragmentRoots();
        for (LibrarySpec lib : libs) {
            IPath jarPath = new Path(lib.binaryJar().getAbsolutePath());
            IPackageFragmentRoot match = null;
            for (IPackageFragmentRoot candidate : allRoots) {
                IPath candidatePath = candidate.getPath();
                if (candidatePath != null && candidatePath.equals(jarPath)) {
                    match = candidate;
                    break;
                }
            }
            if (match == null) {
                throw new IllegalStateException("No package fragment root for jar: " + lib.binaryJar().getAbsolutePath()); //$NON-NLS-1$
            }
            match.open(null);
            roots.add(match);
        }

        return new JavaProjectSetup(project, javaProject, roots);
    }

    public static IClassFile findAnyClassFileInRoot(IPackageFragmentRoot root) throws JavaModelException {
        IJavaElement[] children = root.getChildren();
        for (IJavaElement child : children) {
            if (child instanceof IPackageFragment pkg) {
                IClassFile[] classFiles = pkg.getClassFiles();
                if (classFiles != null && classFiles.length > 0) {
                    return classFiles[0];
                }
            }
        }
        throw new IllegalStateException("No class file found in root: " + root.getElementName()); //$NON-NLS-1$
    }

    public static IPackageFragment findAnyPackageWithClasses(IPackageFragmentRoot root) throws JavaModelException {
        IJavaElement[] children = root.getChildren();
        for (IJavaElement child : children) {
            if (child instanceof IPackageFragment pkg) {
                IClassFile[] classFiles = pkg.getClassFiles();
                if (classFiles != null && classFiles.length > 0) {
                    return pkg;
                }
            }
        }
        throw new IllegalStateException("No package with class files found in root: " + root.getElementName()); //$NON-NLS-1$
    }

    public static File ensureTargetDir() throws IOException {
        File target = new File("target"); //$NON-NLS-1$
        if (!target.exists() && !target.mkdirs() && !target.exists()) {
            throw new IOException("Unable to create target directory: " + target.getAbsolutePath()); //$NON-NLS-1$
        }
        return target;
    }

    public record LibrarySpec(File binaryJar, File sourceJarOrZip) {
    }

    public record SingleLibrarySetup(File binaryJar, IProject project, IJavaProject javaProject,
            IPackageFragmentRoot root) {
    }

    public record JavaProjectSetup(IProject project, IJavaProject javaProject, List<IPackageFragmentRoot> roots) {
    }
}
