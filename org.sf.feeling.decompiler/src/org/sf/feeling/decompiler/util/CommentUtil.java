package org.sf.feeling.decompiler.util;

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

public final class CommentUtil {

	public static final Pattern LINE_NUMBER_COMMENT = Pattern.compile("/\\*\\s*\\d+\\s*\\*/");

	private CommentUtil() {
	}

	public static String clearComments(String input) throws MalformedTreeException, BadLocationException {
		CompilationUnit unit = ASTParserUtil.parse(input);
		AST ast = unit.getAST();
		ASTRewrite rewriter = ASTRewrite.create(ast);
		Document document = new Document(input);
		TextEdit edits = rewriter.rewriteAST(document, null);
		List<Comment> commentList = unit.getCommentList();
		for (Comment comment : commentList) {
			if (comment.isBlockComment()) {
				BlockComment blockComment = (BlockComment) comment;
				int commentStart = blockComment.getStartPosition();
				int commentLength = blockComment.getLength();
				int commentEnd = commentStart + commentLength;
				String commentText = input.substring(commentStart, commentEnd);
				if (!LINE_NUMBER_COMMENT.matcher(commentText.trim()).matches()) {
					edits.addChild(new DeleteEdit(commentStart, commentLength));
				}
			}
		}
		edits.apply(document);
		return document.get();
	}
}
