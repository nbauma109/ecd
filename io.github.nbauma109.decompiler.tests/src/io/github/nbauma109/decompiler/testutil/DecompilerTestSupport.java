package io.github.nbauma109.decompiler.testutil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;
import org.osgi.framework.Bundle;

public final class DecompilerTestSupport {

    private DecompilerTestSupport() {
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

    public static BundleJarProjectSetup createJavaProjectWithBundleJar(String bundleId, String entryPath,
            String projectName) throws IOException, CoreException {
        File jarFile = resolveBundleEntryAsFile(bundleId, entryPath);

        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);

        if (project.exists()) {
            project.delete(true, true, null);
        }

        project.create(null);
        project.open(null);

        IProjectDescription description = project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, null);

        IJavaProject javaProject = JavaCore.create(project);
        configureClasspathWithJre(javaProject);
        IPackageFragmentRoot jarRoot = addJarToClasspathAndGetRoot(javaProject, jarFile);

        return new BundleJarProjectSetup(project, javaProject, jarRoot, jarFile);
    }

    public static void configureClasspathWithJre(IJavaProject project) throws JavaModelException {
        IClasspathEntry[] classpath = { JavaRuntime.getDefaultJREContainerEntry() };
        project.setRawClasspath(classpath, null);
    }

    public static IPackageFragmentRoot addJarToClasspathAndGetRoot(IJavaProject project, File jar)
            throws JavaModelException {
        IPath jarPath = new Path(jar.getAbsolutePath());

        IClasspathEntry[] existing = project.getRawClasspath();
        IClasspathEntry[] updated = new IClasspathEntry[existing.length + 1];

        int i = 0;
        for (; i < existing.length; i++) {
            updated[i] = existing[i];
        }
        updated[i] = JavaCore.newLibraryEntry(jarPath, null, null);

        project.setRawClasspath(updated, null);

        return findJarPackageFragmentRoot(project, jarPath).orElseThrow(() -> new IllegalStateException(
                "Unable to locate package fragment root for jar: " + jarPath.toOSString())); //$NON-NLS-1$
    }

    private static Optional<IPackageFragmentRoot> findJarPackageFragmentRoot(IJavaProject project, IPath jarPath)
            throws JavaModelException {
        IPackageFragmentRoot[] roots = project.getAllPackageFragmentRoots();
        for (IPackageFragmentRoot root : roots) {
            IPath rootPath = root.getPath();
            if (rootPath != null && rootPath.equals(jarPath)) {
                root.open(null);
                return Optional.of(root);
            }
        }
        return Optional.empty();
    }

    public record BundleJarProjectSetup(IProject project, IJavaProject javaProject, IPackageFragmentRoot jarRoot,
            File jarFile) {
    }
}
