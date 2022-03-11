package org.sf.feeling.decompiler.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public final class SortMembersVisitor extends ASTVisitor {
	private final String resultCode;
	private final CompilationUnit unitWithLineNumbers;
	private final String className;
	private AbstractTypeDeclaration rootType;

	public SortMembersVisitor(String resultCode, CompilationUnit unitWithLineNumbers, String className) {
		this.resultCode = resultCode;
		this.unitWithLineNumbers = unitWithLineNumbers;
		this.className = className;
	}

	@Override
	public void endVisit(TypeDeclaration node) {
		exitTypeDeclaration(node);
	}

	@Override
	public void endVisit(EnumDeclaration node) {
		exitTypeDeclaration(node);
	}

	@Override
	public void endVisit(AnnotationTypeDeclaration node) {
		exitTypeDeclaration(node);
	}

	private void exitTypeDeclaration(AbstractTypeDeclaration node) {
		String typeName = node.getName().getIdentifier();
		if (className.equals(typeName + ".class")) {
			rootType = node;
		}
	}

	private Integer getFirstSourceLineNumber(ASTNode node) {
		int bodyStart = node.getStartPosition();
		int bodyEnd = bodyStart + node.getLength();
		List<Comment> commentList = unitWithLineNumbers.getCommentList();
		for (Comment comment : commentList) {
			if (comment.isBlockComment()) {
				int commentStart = comment.getStartPosition();
				int commentEnd = commentStart + comment.getLength();
				if (bodyStart < commentStart && commentEnd < bodyEnd) {
					String commentText = resultCode.substring(commentStart, commentEnd);
					return DecompilerOutputUtil.parseJavaLineNumber(commentText);
				}
			}
		}
		return -1;
	}

	private List<BodyDeclaration> buildSortedList(List<BodyDeclaration> bodyDeclarationList) {
		List<BodyDeclaration> bodyDeclarationSortedList = new ArrayList<>(bodyDeclarationList);
		bodyDeclarationSortedList.sort(Comparator.comparing(this::getFirstSourceLineNumber));
		return bodyDeclarationSortedList;
	}

	public String sortMembers() throws MalformedTreeException, BadLocationException {
		Document document = new Document(resultCode);
		AST ast = unitWithLineNumbers.getAST();
		ASTRewrite rewriter = ASTRewrite.create(ast);
		List<BodyDeclaration> bodyDeclarationList = rootType.bodyDeclarations();
		List<BodyDeclaration> bodyDeclarationSortedList = buildSortedList(bodyDeclarationList);
		TextEdit edits = rewriter.rewriteAST(document, null);
		for (int i = 0; i < bodyDeclarationList.size(); i++) {
			BodyDeclaration oldBody = bodyDeclarationList.get(i);
			BodyDeclaration newBody = bodyDeclarationSortedList.get(i);
			int newBodyStart = newBody.getStartPosition();
			String newBodyText = resultCode.substring(newBodyStart, newBodyStart + newBody.getLength());
			edits.addChild(new ReplaceEdit(oldBody.getStartPosition(), oldBody.getLength(), newBodyText));
		}
		try {
			edits.apply(document);
		} catch (MalformedTreeException | BadLocationException e) {
			e.printStackTrace();
		}
		return document.get();
	}
}