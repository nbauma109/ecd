/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.editor;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.env.IBinaryType;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.core.ClassFile;
import org.eclipse.jdt.internal.core.ImportContainer;
import org.eclipse.jdt.internal.core.ImportContainerInfo;
import org.eclipse.jdt.internal.core.ImportDeclaration;
import org.eclipse.jdt.internal.core.ImportDeclarationElementInfo;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.OpenableElementInfo;
import org.eclipse.jdt.internal.core.SourceMapper;
import org.sf.feeling.decompiler.util.DecompilerOutputUtil;
import org.sf.feeling.decompiler.util.Logger;
import org.sf.feeling.decompiler.util.ReflectionUtils;
import org.sf.feeling.decompiler.util.SourceMapperUtil;

public class ImportSourceMapper extends SourceMapper {

	private static Map<String, String> compilerOptions = new HashMap<>();
	static {
		compilerOptions = new CompilerOptions().getMap();
		compilerOptions.put(CompilerOptions.OPTION_Compliance, DecompilerOutputUtil.getMaxDecompileLevel());
		compilerOptions.put(CompilerOptions.OPTION_Source, DecompilerOutputUtil.getMaxDecompileLevel());
	}

	public ImportSourceMapper(IPath sourcePath, String rootPath) {

		this(sourcePath, rootPath, compilerOptions);
	}

	public ImportSourceMapper(IPath sourcePath, String rootPath, Map options) {
		super(sourcePath, rootPath, options);
	}

	protected Stack infoStack;
	protected HashMap<Object, List> children;
	protected Stack handleStack;
	protected ClassFile unit;
	protected OpenableElementInfo unitInfo;
	protected ImportContainerInfo importContainerInfo = null;
	protected ImportContainer importContainer;

	private JavaModelManager manager = JavaModelManager.getJavaModelManager();

	@Override
	public void enterCompilationUnit() {
		this.infoStack = new Stack();
		this.children = new HashMap<>();
		this.handleStack = new Stack();
		this.infoStack.push(this.unitInfo);
		this.handleStack.push(this.unit);
	}

	/**
	 * With the time this API has changed and the original method which accepted
	 * {@link IType} as first parameter has been superseded and replaced by the
	 * which does accept {@link NamedParameter} as first parameter.
	 * <p>
	 * But we do need to support both APIs here so we will try to invoke the correct
	 * method using reflection instead of a hard coded reference.
	 */
	public ISourceRange mapSourceSwitch(IType type, char[] contents, IBinaryType info, IJavaElement elementToFind) {
		this.unit = (ClassFile) type.getClassFile();
		try {
			this.unitInfo = (OpenableElementInfo) this.unit.getElementInfo();
		} catch (JavaModelException e) {
			Logger.debug(e);
		}

		try {
			SourceMapperUtil.mapSource(this, type, contents, info, elementToFind);
		} catch (Exception e) {
			// Method was found but invocation failed, this shouldn't happen.
		}

		return null;
	}

	@Override
	public void exitCompilationUnit(int declarationEnd) {
		IJavaElement[] oldChildren = (IJavaElement[]) ReflectionUtils.getFieldValue(this.unitInfo, "children"); //$NON-NLS-1$

		if (this.importContainerInfo != null) {
			ReflectionUtils.setFieldValue(this.importContainerInfo, "children", getChildren(this.importContainerInfo)); //$NON-NLS-1$
		}

		List<IJavaElement> children = new ArrayList<IJavaElement>();

		for (int i = 0; i < oldChildren.length; i++) {
			IJavaElement child = oldChildren[i];
			if (child instanceof ImportContainer)
				continue;
			children.add(child);
		}

		children.addAll(Arrays.asList(getChildren(this.unitInfo)));

		ReflectionUtils.setFieldValue(this.unitInfo, "children", children.toArray(new IJavaElement[0])); //$NON-NLS-1$

		if (this.importContainer != null) {
			manager.getTemporaryCache().put(this.importContainer, this.importContainerInfo);
		}
		ReflectionUtils.invokeMethod(manager, "putInfos", new Class[] { //$NON-NLS-1$
				IJavaElement.class, Object.class, boolean.class, Map.class },
				new Object[] { unit, unitInfo, false, manager.getTemporaryCache() });
	}

	private IJavaElement[] getChildren(Object info) {
		List childrenList = this.children.get(info);
		if (childrenList != null) {
			return (IJavaElement[]) childrenList.toArray(new IJavaElement[childrenList.size()]);
		}
		return new JavaElement[0];
	}

	private void addToChildren(Object parentInfo, JavaElement handle) {
		List childrenList = this.children.get(parentInfo);
		if (childrenList == null)
			this.children.put(parentInfo, childrenList = new ArrayList<>());
		childrenList.add(handle);
	}

	protected ImportDeclaration createImportDeclaration(ImportContainer parent, String name, boolean onDemand) {
		try {
			Constructor c = ImportDeclaration.class.getDeclaredConstructor(ImportContainer.class, String.class,
					boolean.class);
			c.setAccessible(true);
			ImportDeclaration dec = (ImportDeclaration) c.newInstance(parent, name, onDemand);
			return dec;
		} catch (Exception e) {
			Logger.debug(e);
		}
		return null;
	}

	@Override
	public void acceptImport(int declarationStart, int declarationEnd, int nameSourceStart, int nameSourceEnd,
			char[][] tokens, boolean onDemand, int modifiers) {
		JavaElement parentHandle = (JavaElement) this.handleStack.peek();
		if (!(parentHandle.getElementType() == IJavaElement.CLASS_FILE)) {
			Assert.isTrue(false); // Should not happen
		}

		ClassFile parentCU = (ClassFile) parentHandle;
		// create the import container and its info
		if (this.importContainer == null) {
			this.importContainer = createImportContainer(parentCU);
			this.importContainerInfo = new ImportContainerInfo();
			Object parentInfo = this.infoStack.peek();
			addToChildren(parentInfo, this.importContainer);
		}

		String elementName = JavaModelManager.getJavaModelManager()
				.intern(new String(CharOperation.concatWith(tokens, '.')));
		ImportDeclaration handle = createImportDeclaration(this.importContainer, elementName, onDemand);

		ImportDeclarationElementInfo info = new ImportDeclarationElementInfo();
		ReflectionUtils.invokeMethod(info, "setSourceRangeStart", int.class, declarationStart); //$NON-NLS-1$
		ReflectionUtils.invokeMethod(info, "setSourceRangeEnd", int.class, declarationEnd); //$NON-NLS-1$
		ReflectionUtils.invokeMethod(info, "setNameSourceStart", int.class, nameSourceStart); //$NON-NLS-1$
		ReflectionUtils.invokeMethod(info, "setNameSourceEnd", int.class, nameSourceEnd); //$NON-NLS-1$
		ReflectionUtils.invokeMethod(info, "setFlags", int.class, modifiers); //$NON-NLS-1$

		addToChildren(this.importContainerInfo, handle);
		manager.getTemporaryCache().put(handle, info);
	}
}
