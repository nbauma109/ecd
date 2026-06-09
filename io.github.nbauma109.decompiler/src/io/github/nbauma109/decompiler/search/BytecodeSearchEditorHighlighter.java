/*******************************************************************************
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

final class BytecodeSearchEditorHighlighter {

    private static final String SEARCH_ANNOTATION_TYPE = "org.eclipse.search.results"; //$NON-NLS-1$
    private static final Map<IAnnotationModel, List<Annotation>> ANNOTATIONS = new WeakHashMap<>();

    private BytecodeSearchEditorHighlighter() {
    }

    static void highlight(ITextEditor textEditor, List<BytecodeSourceRangeResolver.SourceRange> ranges) {
        IAnnotationModel model = annotationModel(textEditor);
        if (model == null || ranges.isEmpty()) {
            return;
        }
        Map<Annotation, Position> additions = new HashMap<>(ranges.size());
        List<Annotation> annotations = new ArrayList<>(ranges.size());
        for (BytecodeSourceRangeResolver.SourceRange range : ranges) {
            if (range.offset() >= 0 && range.length() > 0) {
                Annotation annotation = new BytecodeSearchAnnotation();
                annotations.add(annotation);
                additions.put(annotation, new Position(range.offset(), range.length()));
            }
        }
        if (annotations.isEmpty()) {
            return;
        }
        synchronized (ANNOTATIONS) {
            List<Annotation> previous = ANNOTATIONS.put(model, annotations);
            Annotation[] removals = previous == null ? new Annotation[0] : previous.toArray(Annotation[]::new);
            if (model instanceof IAnnotationModelExtension extension) {
                extension.replaceAnnotations(removals, additions);
            } else {
                if (previous != null) {
                    for (Annotation annotation : previous) {
                        model.removeAnnotation(annotation);
                    }
                }
                additions.forEach(model::addAnnotation);
            }
        }
    }

    private static IAnnotationModel annotationModel(ITextEditor textEditor) {
        IAnnotationModel model = textEditor.getAdapter(IAnnotationModel.class);
        if (model != null) {
            return model;
        }
        IDocumentProvider provider = textEditor.getDocumentProvider();
        return provider == null ? null : provider.getAnnotationModel(textEditor.getEditorInput());
    }

    private static final class BytecodeSearchAnnotation extends Annotation {

        private BytecodeSearchAnnotation() {
            super(SEARCH_ANNOTATION_TYPE, true, null);
        }
    }
}
