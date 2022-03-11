package org.sf.feeling.decompiler.cfr.decompiler;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public final class CfrInnerClassVisitor extends ASTVisitor {
	private final Map<String, String> options;
	private final Map<Integer, Integer> lineMapping;
	private final String className;
	private final CompilationUnit unit;
	private final File workingDir;
	private StringBuilder sbTypeDeclaration = new StringBuilder();
	private String packageName = "";

	public CfrInnerClassVisitor(Map<String, String> options, Map<Integer, Integer> lineMapping, String className,
			CompilationUnit unit, File workingDir) {
		this.options = options;
		this.lineMapping = lineMapping;
		this.className = className;
		this.unit = unit;
		this.workingDir = workingDir;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		enterTypeDeclaration(node);
		return super.visit(node);
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		enterTypeDeclaration(node);
		return super.visit(node);
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		enterTypeDeclaration(node);
		return super.visit(node);
	}

	@Override
	public void endVisit(TypeDeclaration node) {
		exitTypeDeclaration();
	}

	@Override
	public void endVisit(EnumDeclaration node) {
		exitTypeDeclaration();
	}

	@Override
	public void endVisit(AnnotationTypeDeclaration node) {
		exitTypeDeclaration();
	}

	@Override
	public boolean visit(PackageDeclaration node) {
		if (!packageName.isEmpty()) {
			sbTypeDeclaration.append(packageName).append('/');
		}
		return super.visit(node);
	}

	private void enterTypeDeclaration(AbstractTypeDeclaration node) {
		String typeName = node.getName().getIdentifier();
		int length = sbTypeDeclaration.length();

		if (length == 0 || sbTypeDeclaration.charAt(length - 1) == '/') {
			sbTypeDeclaration.append(typeName);
		} else {
			sbTypeDeclaration.append('$').append(typeName);
		}

		if (className.equals(typeName + ".class")) {
			return;
		}
		final NavigableMap<Integer, Integer> innerLineMapping = new TreeMap<>();
		StringBuilder innerResultCode = new StringBuilder();
		OutputSinkFactory innerSink = new CfrOutputSinkFactory(innerResultCode, innerLineMapping);
		CfrDriver innerDriver = new CfrDriver.Builder().withOptions(options).withOutputSink(innerSink).build();
		File innerClassFile = new File(workingDir, sbTypeDeclaration.toString() + ".class");
		innerDriver.analyse(Collections.singletonList(innerClassFile.getAbsolutePath()));
		for (Map.Entry<Integer, Integer> innerLineMappingEntry : innerLineMapping.entrySet()) {
			Integer innerOutputLine = innerLineMappingEntry.getKey();
			Integer sourceLine = innerLineMappingEntry.getValue();
			Integer firstTypeLineNumber = CfrDecompiler.getFirstTypeLineNumber(innerResultCode.toString());
			int innerTypeLineNumber = unit.getLineNumber(node.getStartPosition());
			lineMapping.put(innerOutputLine + innerTypeLineNumber - firstTypeLineNumber, sourceLine);
		}
	}

	private void exitTypeDeclaration() {
		int index = sbTypeDeclaration.lastIndexOf("$");

		if (index == -1) {
			index = sbTypeDeclaration.lastIndexOf("/") + 1;
		}

		if (index == -1) {
			sbTypeDeclaration.setLength(0);
		} else {
			sbTypeDeclaration.setLength(index);
		}
	}
}