package org.sf.feeling.decompiler.util;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

public final class ASTParserUtil {

	private ASTParserUtil() {
	}

	public static CompilationUnit parse(String sourceCode) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		CompilerOptions option = new CompilerOptions();
		Map<String, String> options = option.getMap();
		options.put(CompilerOptions.OPTION_Compliance, JavaCore.latestSupportedJavaVersion());
		options.put(CompilerOptions.OPTION_Source, JavaCore.latestSupportedJavaVersion());
		parser.setCompilerOptions(options);
		parser.setSource(sourceCode.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}
}
