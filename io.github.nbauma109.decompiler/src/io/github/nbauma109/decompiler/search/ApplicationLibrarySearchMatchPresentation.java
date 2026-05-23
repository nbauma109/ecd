/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.search.IMatchPresentation;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.search.ui.ISearchResultPage;
import org.eclipse.search.ui.ISearchResultViewPart;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchViewPage;
import org.eclipse.search.ui.text.Match;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.texteditor.ITextEditor;

import io.github.nbauma109.decompiler.editor.JavaDecompilerClassFileEditor;
import io.github.nbauma109.decompiler.util.ClassUtil;

final class ApplicationLibrarySearchMatchPresentation implements IMatchPresentation {

    private final BytecodeSourceRangeResolver sourceRangeResolver = new BytecodeSourceRangeResolver();

    @Override
    public ILabelProvider createLabelProvider() {
        return new BytecodeSearchLabelProvider();
    }

    @Override
    public void showMatch(Match match, int currentOffset, int currentLength, boolean activate)
            throws PartInitException {
        IJavaElement javaElement = javaElement(match);
        if (javaElement == null) {
            return;
        }

        IEditorPart editor = openJavaElement(editorOpenTarget(match, javaElement), activate);
        String source = documentText(editor);
        List<BytecodeSourceRangeResolver.SourceRange> highlights = null;
        if (match instanceof BytecodeSearchMatch bytecodeMatch) {
            ResolvedRanges resolved = editor instanceof ITextEditor textEditor
                    ? resolveRanges(bytecodeMatch, textEditor, source)
                    : new ResolvedRanges(sourceRangeResolver.rangeFor(bytecodeMatch.getEntry(), source), List.of());
            BytecodeSourceRangeResolver.SourceRange range = resolved.selected();
            highlights = resolved.highlights();
            bytecodeMatch.update(range);
            currentOffset = range.offset();
            currentLength = range.length();
        }
        if (editor instanceof ITextEditor textEditor && currentOffset >= 0 && currentLength > 0) {
            protectExternalTextSelection(editor);
            BytecodeSearchEditorHighlighter.highlight(textEditor, highlights == null || highlights.isEmpty()
                    ? List.of(new BytecodeSourceRangeResolver.SourceRange(currentOffset, currentLength))
                    : highlights);
            selectAndReveal(textEditor, currentOffset, currentLength);
        } else {
            JavaUI.revealInEditor(editor, javaElement);
        }
    }

    private ResolvedRanges resolveRanges(BytecodeSearchMatch selectedMatch, ITextEditor textEditor, String source) {
        List<BytecodeSearchMatch> matches = bytecodeMatchesInEditor(textEditor);
        if (!matches.contains(selectedMatch)) {
            matches = new ArrayList<>(matches);
            matches.add(selectedMatch);
        }
        List<BytecodeSearchEntry> entries = new ArrayList<>(matches.size());
        for (BytecodeSearchMatch match : matches) {
            entries.add(match.getEntry());
        }
        Map<BytecodeSearchEntry, BytecodeSourceRangeResolver.SourceRange> ranges =
                sourceRangeResolver.rangesFor(entries, source);
        List<BytecodeSourceRangeResolver.SourceRange> highlights = new ArrayList<>(matches.size());
        BytecodeSourceRangeResolver.SourceRange selectedRange = ranges.get(selectedMatch.getEntry());
        for (BytecodeSearchMatch match : matches) {
            BytecodeSourceRangeResolver.SourceRange range = ranges.get(match.getEntry());
            if (range != null) {
                match.update(range);
                highlights.add(range);
            }
        }
        if (selectedRange == null) {
            selectedRange = sourceRangeResolver.rangeFor(selectedMatch.getEntry(), source);
            highlights.add(selectedRange);
        }
        return new ResolvedRanges(selectedRange, highlights);
    }

    private static List<BytecodeSearchMatch> bytecodeMatchesInEditor(ITextEditor textEditor) {
        ISearchResultViewPart view = NewSearchUI.getSearchResultView();
        ISearchResultPage page = view == null ? null : view.getActivePage();
        if (!(page instanceof AbstractTextSearchViewPage textPage)) {
            return List.of();
        }
        AbstractTextSearchResult result = textPage.getInput();
        if (result == null) {
            return List.of();
        }
        IJavaElement editorElement = textEditor.getEditorInput().getAdapter(IJavaElement.class);
        IClassFile editorClassFile = classFile(editorElement);
        IClassFile editorTopLevelClassFile = ClassUtil.getTopLevelClassFile(editorClassFile);
        if (editorTopLevelClassFile == null) {
            return List.of();
        }
        List<BytecodeSearchMatch> matches = new ArrayList<>();
        for (Object element : result.getElements()) {
            if (element instanceof BytecodeSearchElement bytecodeElement
                    && isShownInSameTopLevelClass(editorTopLevelClassFile, bytecodeElement.getJavaElement())) {
                for (Match match : result.getMatches(element)) {
                    if (match instanceof BytecodeSearchMatch bytecodeMatch) {
                        matches.add(bytecodeMatch);
                    }
                }
            }
        }
        return matches;
    }

    static boolean isShownInSameTopLevelClass(IJavaElement editorElement, IJavaElement javaElement) {
        IClassFile editorClassFile = classFile(editorElement);
        IClassFile editorTopLevelClassFile = ClassUtil.getTopLevelClassFile(editorClassFile);
        return editorTopLevelClassFile != null && isShownInSameTopLevelClass(editorTopLevelClassFile, javaElement);
    }

    private static boolean isShownInSameTopLevelClass(IClassFile editorTopLevelClassFile, IJavaElement javaElement) {
        IClassFile classFile = classFile(javaElement);
        IClassFile topLevelClassFile = ClassUtil.getTopLevelClassFile(classFile);
        return editorTopLevelClassFile.equals(topLevelClassFile);
    }

    private record ResolvedRanges(BytecodeSourceRangeResolver.SourceRange selected,
            List<BytecodeSourceRangeResolver.SourceRange> highlights) {
    }

    private static void selectAndReveal(ITextEditor textEditor, int offset, int length) {
        textEditor.selectAndReveal(offset, length);
        textEditor.getSite().getShell().getDisplay().asyncExec(() -> {
            if (textEditor.getSite() != null && textEditor.getSite().getShell() != null
                    && !textEditor.getSite().getShell().isDisposed()) {
                textEditor.selectAndReveal(offset, length);
            }
        });
    }

    private static void protectExternalTextSelection(IEditorPart editor) {
        if (editor instanceof JavaDecompilerClassFileEditor decompilerEditor) {
            decompilerEditor.protectExternalTextSelection();
            return;
        }
        JavaDecompilerClassFileEditor decompilerEditor = editor.getAdapter(JavaDecompilerClassFileEditor.class);
        if (decompilerEditor != null) {
            decompilerEditor.protectExternalTextSelection();
        }
    }

    private static String documentText(IEditorPart editor) {
        if (!(editor instanceof ITextEditor textEditor)) {
            return null;
        }
        IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
        return document == null ? null : document.get();
    }

    private static IEditorPart openJavaElement(IJavaElement element, boolean activate) throws PartInitException {
        try {
            return JavaUI.openInEditor(element, activate, false);
        } catch (JavaModelException e) {
            throw new PartInitException(e.getMessage(), e);
        }
    }

    private static IJavaElement editorOpenTarget(Match match, IJavaElement javaElement) {
        if (!(match instanceof BytecodeSearchMatch)) {
            return javaElement;
        }
        IClassFile classFile = classFile(javaElement);
        IClassFile topLevelClassFile = ClassUtil.getTopLevelClassFile(classFile);
        return topLevelClassFile == null ? javaElement : topLevelClassFile;
    }

    private static IClassFile classFile(IJavaElement javaElement) {
        if (javaElement == null) {
            return null;
        }
        if (javaElement instanceof IClassFile classFile) {
            return classFile;
        }
        IJavaElement classFile = javaElement.getAncestor(IJavaElement.CLASS_FILE);
        return classFile instanceof IClassFile cf ? cf : null;
    }

    private static IJavaElement javaElement(Match match) {
        if (match instanceof BytecodeSearchMatch bytecodeMatch) {
            return bytecodeMatch.getEntry().getElement();
        }
        Object element = match.getElement();
        if (element instanceof BytecodeSearchElement bytecodeElement) {
            return bytecodeElement.getJavaElement();
        }
        return element instanceof IJavaElement javaElement ? javaElement : null;
    }

    private static final class BytecodeSearchLabelProvider implements ILabelProvider {

        private final JavaElementLabelProvider delegate =
                new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT);

        @Override
        public Image getImage(Object element) {
            return delegate.getImage(unwrap(element));
        }

        @Override
        public String getText(Object element) {
            return delegate.getText(unwrap(element));
        }

        @Override
        public void addListener(ILabelProviderListener listener) {
            delegate.addListener(listener);
        }

        @Override
        public void dispose() {
            delegate.dispose();
        }

        @Override
        public boolean isLabelProperty(Object element, String property) {
            return delegate.isLabelProperty(unwrap(element), property);
        }

        @Override
        public void removeListener(ILabelProviderListener listener) {
            delegate.removeListener(listener);
        }

        private Object unwrap(Object element) {
            return element instanceof BytecodeSearchElement bytecodeElement ? bytecodeElement.getJavaElement() : element;
        }
    }
}
