package org.sf.feeling.decompiler.util;

import java.util.Map;

import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

public final class ASTParserUtil {

	private ASTParserUtil() {
	}

	public static CompilationUnit parse(String sourceCode) {
		ASTParser parser = ASTParser.newParser(DecompilerOutputUtil.getMaxJSLLevel());
		CompilerOptions option = new CompilerOptions();
		Map<String, String> options = option.getMap();
		options.put(CompilerOptions.OPTION_Compliance, DecompilerOutputUtil.getMaxDecompileLevel()); // $NON-NLS-1$
		options.put(CompilerOptions.OPTION_Source, DecompilerOutputUtil.getMaxDecompileLevel()); // $NON-NLS-1$
		parser.setCompilerOptions(options);
		parser.setSource(sourceCode.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}
}
