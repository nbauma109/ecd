package org.sf.feeling.decompiler.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.BlockComment;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;

public final class CommentUtil {

	public static final Pattern LINE_NUMBER_COMMENT = Pattern.compile("/\\*\\s*\\d+\\s*\\*/");

	private CommentUtil() {
	}

	public static String clearComments(String input) {
		CompilationUnit unit = ASTParserUtil.parse(input);
		AST ast = unit.getAST();
		ASTRewrite rewriter = ASTRewrite.create(ast);
		Document document = new Document(input);
		TextEdit edits = rewriter.rewriteAST(document, null);
		List<TextEdit> textEdits = new ArrayList<>();
		List<Comment> commentList = unit.getCommentList();
		for (Comment comment : commentList) {
			if (comment.isBlockComment()) {
				BlockComment blockComment = (BlockComment) comment;
				int commentStart = blockComment.getStartPosition();
				int commentLength = blockComment.getLength();
				int commentEnd = commentStart + commentLength;
				String commentText = input.substring(commentStart, commentEnd);
				if (!LINE_NUMBER_COMMENT.matcher(commentText.trim()).matches()) {
					textEdits.add(new DeleteEdit(commentStart, commentLength));
				}
			}
		}
		edits.addChildren(textEdits.toArray(TextEdit[]::new));
		try {
			edits.apply(document);
		} catch (MalformedTreeException | BadLocationException e) {
			JavaDecompilerPlugin.logError(e, e.getMessage());
		}
		return document.get();
	}
}
