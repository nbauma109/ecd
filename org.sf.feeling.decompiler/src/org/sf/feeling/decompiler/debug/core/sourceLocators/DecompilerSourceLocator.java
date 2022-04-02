package org.sf.feeling.decompiler.debug.core.sourceLocators;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IPersistableSourceLocator;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.debug.core.sourcelookup.ISourceContainerType;
import org.eclipse.debug.core.sourcelookup.ISourceLookupParticipant;
import org.eclipse.debug.core.sourcelookup.ISourcePathComputer;
import org.eclipse.debug.core.sourcelookup.containers.LocalFileStorage;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.launching.JavaSourceLookupDirector;
import org.eclipse.jface.preference.IPreferenceStore;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.editor.DecompilerSourceMapper;
import org.sf.feeling.decompiler.editor.SourceMapperFactory;
import org.sf.feeling.decompiler.util.Logger;

public class DecompilerSourceLocator implements IPersistableSourceLocator {

	private JavaSourceLookupDirector javaSourceLocator = new JavaSourceLookupDirector();

	/**
	 * @return
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return javaSourceLocator.hashCode();
	}

	/**
	 * 
	 * @see org.eclipse.jdt.internal.launching.JavaSourceLookupDirector#initializeParticipants()
	 */
	public void initializeParticipants() {
		javaSourceLocator.initializeParticipants();
	}

	/**
	 * @param type
	 * @return
	 * @see org.eclipse.jdt.internal.launching.JavaSourceLookupDirector#supportsSourceContainerType(org.eclipse.debug.core.sourcelookup.ISourceContainerType)
	 */
	public boolean supportsSourceContainerType(ISourceContainerType type) {
		return javaSourceLocator.supportsSourceContainerType(type);
	}

	/**
	 * @param o1
	 * @param o2
	 * @return
	 * @see org.eclipse.jdt.internal.launching.JavaSourceLookupDirector#equalSourceElements(java.lang.Object,
	 *      java.lang.Object)
	 */
	public boolean equalSourceElements(Object o1, Object o2) {
		return javaSourceLocator.equalSourceElements(o1, o2);
	}

	/**
	 * @param obj
	 * @return
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		return javaSourceLocator.equals(obj);
	}

	/**
	 * @param id
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#setId(java.lang.String)
	 */
	public void setId(String id) {
		javaSourceLocator.setId(id);
	}

	/**
	 * 
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#dispose()
	 */
	public void dispose() {
		javaSourceLocator.dispose();
	}

	/**
	 * @return
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return javaSourceLocator.toString();
	}

	/**
	 * @return
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#getSourceContainers()
	 */
	public ISourceContainer[] getSourceContainers() {
		return javaSourceLocator.getSourceContainers();
	}

	/**
	 * @return
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#isFindDuplicates()
	 */
	public boolean isFindDuplicates() {
		return javaSourceLocator.isFindDuplicates();
	}

	/**
	 * @param duplicates
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#setFindDuplicates(boolean)
	 */
	public void setFindDuplicates(boolean duplicates) {
		javaSourceLocator.setFindDuplicates(duplicates);
	}

	/**
	 * @param configuration
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#launchConfigurationAdded(org.eclipse.debug.core.ILaunchConfiguration)
	 */
	public void launchConfigurationAdded(ILaunchConfiguration configuration) {
		javaSourceLocator.launchConfigurationAdded(configuration);
	}

	/**
	 * @param configuration
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#launchConfigurationChanged(org.eclipse.debug.core.ILaunchConfiguration)
	 */
	public void launchConfigurationChanged(ILaunchConfiguration configuration) {
		javaSourceLocator.launchConfigurationChanged(configuration);
	}

	/**
	 * @param configuration
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#launchConfigurationRemoved(org.eclipse.debug.core.ILaunchConfiguration)
	 */
	public void launchConfigurationRemoved(ILaunchConfiguration configuration) {
		javaSourceLocator.launchConfigurationRemoved(configuration);
	}

	/**
	 * @return
	 * @throws CoreException
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#getMemento()
	 */
	public String getMemento() throws CoreException {
		return javaSourceLocator.getMemento();
	}

	/**
	 * @param memento
	 * @throws CoreException
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#initializeFromMemento(java.lang.String)
	 */
	public void initializeFromMemento(String memento) throws CoreException {
		javaSourceLocator.initializeFromMemento(memento);
	}

	/**
	 * @param containers
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#setSourceContainers(org.eclipse.debug.core.sourcelookup.ISourceContainer[])
	 */
	public void setSourceContainers(ISourceContainer[] containers) {
		javaSourceLocator.setSourceContainers(containers);
	}

	/**
	 * @param stackFrame
	 * @return
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#getSourceElement(org.eclipse.debug.core.model.IStackFrame)
	 */
	public Object getSourceElement(IStackFrame stackFrame) {
		Object sourceElement = javaSourceLocator.getSourceElement(stackFrame);
		if (sourceElement instanceof IClassFile) {
			IClassFile cf = (IClassFile) sourceElement;
			IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
			String decompilerType = prefs.getString(JavaDecompilerPlugin.DECOMPILER_TYPE);
			DecompilerSourceMapper sourceMapper = SourceMapperFactory.getSourceMapper(decompilerType);
			try {
				if (sourceMapper != null) {
					char[] src = sourceMapper.findSource(cf.getType());
					if (src != null) {
						String tmpDir = System.getProperty("java.io.tmpdir");
						String destFileName = cf.getElementName().replaceAll("\\.class$", ".java");
						File tempFile = new File(tmpDir, destFileName);
						tempFile.deleteOnExit();
						Files.writeString(tempFile.toPath(), new String(src), StandardOpenOption.CREATE);
						return new LocalFileStorage(tempFile);
					}
				}
			} catch (JavaModelException | IOException e) {
				Logger.error(e);
			}
		}
		return sourceElement;
	}

	/**
	 * @param element
	 * @param sources
	 * @return
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#resolveSourceElement(java.lang.Object,
	 *      java.util.List)
	 */
	public Object resolveSourceElement(Object element, List<Object> sources) {
		return javaSourceLocator.resolveSourceElement(element, sources);
	}

	/**
	 * @param memento
	 * @param configuration
	 * @throws CoreException
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#initializeFromMemento(java.lang.String,
	 *      org.eclipse.debug.core.ILaunchConfiguration)
	 */
	public void initializeFromMemento(String memento, ILaunchConfiguration configuration) throws CoreException {
		javaSourceLocator.initializeFromMemento(memento, configuration);
	}

	/**
	 * @param configuration
	 * @throws CoreException
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#initializeDefaults(org.eclipse.debug.core.ILaunchConfiguration)
	 */
	public void initializeDefaults(ILaunchConfiguration configuration) throws CoreException {
		javaSourceLocator.initializeDefaults(configuration);
	}

	/**
	 * @return
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#getLaunchConfiguration()
	 */
	public ILaunchConfiguration getLaunchConfiguration() {
		return javaSourceLocator.getLaunchConfiguration();
	}

	/**
	 * @param launch
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#launchAdded(org.eclipse.debug.core.ILaunch)
	 */
	public void launchAdded(ILaunch launch) {
		javaSourceLocator.launchAdded(launch);
	}

	/**
	 * @param launch
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#launchChanged(org.eclipse.debug.core.ILaunch)
	 */
	public void launchChanged(ILaunch launch) {
		javaSourceLocator.launchChanged(launch);
	}

	/**
	 * @param launch
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#launchRemoved(org.eclipse.debug.core.ILaunch)
	 */
	public void launchRemoved(ILaunch launch) {
		javaSourceLocator.launchRemoved(launch);
	}

	/**
	 * @return
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#getParticipants()
	 */
	public ISourceLookupParticipant[] getParticipants() {
		return javaSourceLocator.getParticipants();
	}

	/**
	 * @param element
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#clearSourceElements(java.lang.Object)
	 */
	public void clearSourceElements(Object element) {
		javaSourceLocator.clearSourceElements(element);
	}

	/**
	 * @param participants
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#addParticipants(org.eclipse.debug.core.sourcelookup.ISourceLookupParticipant[])
	 */
	public void addParticipants(ISourceLookupParticipant[] participants) {
		javaSourceLocator.addParticipants(participants);
	}

	/**
	 * @param participants
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#removeParticipants(org.eclipse.debug.core.sourcelookup.ISourceLookupParticipant[])
	 */
	public void removeParticipants(ISourceLookupParticipant[] participants) {
		javaSourceLocator.removeParticipants(participants);
	}

	/**
	 * @return
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#getId()
	 */
	public String getId() {
		return javaSourceLocator.getId();
	}

	/**
	 * @return
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#getSourcePathComputer()
	 */
	public ISourcePathComputer getSourcePathComputer() {
		return javaSourceLocator.getSourcePathComputer();
	}

	/**
	 * @param computer
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#setSourcePathComputer(org.eclipse.debug.core.sourcelookup.ISourcePathComputer)
	 */
	public void setSourcePathComputer(ISourcePathComputer computer) {
		javaSourceLocator.setSourcePathComputer(computer);
	}

	/**
	 * @param object
	 * @return
	 * @throws CoreException
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#findSourceElements(java.lang.Object)
	 */
	public Object[] findSourceElements(Object object) throws CoreException {
		return javaSourceLocator.findSourceElements(object);
	}

	/**
	 * @param element
	 * @return
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#getSourceElement(java.lang.Object)
	 */
	public Object getSourceElement(Object element) {
		return javaSourceLocator.getSourceElement(element);
	}

	/**
	 * @return
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#getCurrentParticipant()
	 */
	public ISourceLookupParticipant getCurrentParticipant() {
		return javaSourceLocator.getCurrentParticipant();
	}
}
