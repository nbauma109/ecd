/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package io.github.nbauma109.decompiler.editor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.BufferManager;
import org.eclipse.jdt.internal.core.ClassFile;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.javaeditor.ClassFileEditor;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jdt.internal.ui.javaeditor.InternalClassFileEditorInput;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IFindReplaceTarget;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.texteditor.FindNextAction;
import org.eclipse.ui.texteditor.FindReplaceAction;
import org.eclipse.ui.texteditor.GotoLineAction;
import org.eclipse.ui.texteditor.IAbstractTextEditorHelpContextIds;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.IWorkbenchActionDefinitionIds;
import org.eclipse.ui.texteditor.IncrementalFindAction;
import org.eclipse.jdt.ui.IPackagesViewPart;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.StandardJavaElementContentProvider;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.actions.DecompileActionGroup;
import io.github.nbauma109.decompiler.util.ClassUtil;
import io.github.nbauma109.decompiler.util.DecompileUtil;
import io.github.nbauma109.decompiler.util.FileUtil;
import io.github.nbauma109.decompiler.util.Logger;
import io.github.nbauma109.decompiler.util.ReflectionUtils;
import io.github.nbauma109.decompiler.util.UIUtil;

public class JavaDecompilerClassFileEditor extends ClassFileEditor {

    private static final String NO_LINE_NUMBER = "// Warning: No line numbers available in class file"; //$NON-NLS-1$
    private static final Pattern LINE_NUMBER_COMMENT = Pattern.compile("/\\*\\s*\\d+\\s*\\*/");
    private static final String FIND_TARGET_FIELD = "fTarget"; //$NON-NLS-1$
    private static final String CLASS_FILE_EXTENSION = ".class"; //$NON-NLS-1$
    private static final String NESTED_CLASS_SEPARATOR = "$"; //$NON-NLS-1$

    private IBuffer classBuffer;
    private boolean sourceShown = false;
    private boolean selectionChange = false;
    private ISourceReference selectedElement = null;
    private String decompilerType = null;
    private Composite loadingComposite;
    private Canvas loadingSpinner;
    private int loadingSpinnerFrame = 0;
    private IJavaElement nestedOpenTarget;
    private IClassFile nestedOpenClassFile;
    private int packageExplorerRevealSequence;
    private Runnable pendingPackageExplorerReveal150;
    private Runnable pendingPackageExplorerReveal750;
    private Runnable pendingPackageExplorerReveal1500;

    public ISourceReference getSelectedElement() {
        return selectedElement;
    }

    public void setDecompilerType(String decompilerType) {
        this.decompilerType = decompilerType;
    }

    public void setEditorTitleImage(Image image) {
        super.setTitleImage(image);
    }

    private boolean doOpenBuffer(IEditorInput input, boolean force) throws JavaModelException {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        return doOpenBuffer(input, prefs.getString(JavaDecompilerPlugin.DECOMPILER_TYPE), force);
    }

    private boolean doOpenBuffer(IEditorInput input, String type, boolean force) throws JavaModelException {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        boolean reuseBuf = prefs.getBoolean(JavaDecompilerPlugin.REUSE_BUFFER);
        boolean always = prefs.getBoolean(JavaDecompilerPlugin.IGNORE_EXISTING);
        return doOpenBuffer(input, type, force, reuseBuf, always);
    }

    private boolean doOpenBuffer(IEditorInput input, String type, boolean force, boolean reuseBuf, boolean always)
            throws JavaModelException {
        if (JavaDecompilerPlugin.getDefault().isDebug() || UIUtil.isDebugPerspective()) {
            reuseBuf = false;
        }

        if (input instanceof IClassFileEditorInput in) {

            boolean opened = false;
            IClassFile cf = ClassUtil.getTopLevelClassFile(in.getClassFile());

            decompilerType = type;
            String origSrc = cf.getSource();
            if (origSrc == null || always || !reuseBuf || debugOptionChange(origSrc) || force) {
                DecompilerSourceMapper sourceMapper = JavaDecompilerPlugin.getDefault().getSourceMapper(decompilerType);
                IType typeToDecompile = getType(cf);
                char[] src = sourceMapper == null || typeToDecompile == null ? null : sourceMapper.findSource(typeToDecompile);
                if (src == null) {
                    return false;
                }
                char[] markedSrc = src;
                classBuffer = BufferManager.createBuffer(cf);
                classBuffer.setContents(markedSrc);
                getBufferManager().addBuffer(classBuffer);

                sourceMapper.mapSourceSwitch(typeToDecompile, markedSrc, true);

                ClassFileSourceMap.updateSource(getBufferManager(), (ClassFile) cf, markedSrc);

                opened = true;
            }
            return opened;

        }
        return false;
    }

    public void clearSelection() {
        if (getSourceViewer() != null && getSourceViewer().getTextWidget() != null
                && !getSourceViewer().getTextWidget().isDisposed()) {
            getSourceViewer().getTextWidget().setSelectionRange(0, 0);
        }
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    protected void selectionChanged() {
        selectionChange = true;
        super.selectionChanged();
        selectionChange = false;
    }

    @Override
    protected void setSelection(ISourceReference reference, boolean moveCursor) {
        ISourceReference selection = resolveSelectionTarget(reference);
        super.setSelection(selection, moveCursor);

        this.selectedElement = selection;
        syncPackageExplorer(selection);
    }

    @Override
    public void setHighlightRange(int offset, int length, boolean moveCursor) {
        super.setHighlightRange(offset, length, moveCursor);

        if (selectionChange) {
            return;
        }

        IClassFileEditorInput classFileEditorInput = (IClassFileEditorInput) getEditorInput();
        final IClassFile file = classFileEditorInput.getClassFile();

        Display.getDefault().asyncExec(() -> {
            try {
                DecompileUtil.updateBuffer(file, file.getBuffer().getContents());
            } catch (JavaModelException e) {
                Logger.debug(e);
            }
        });

        final StyledText widget = getSourceViewer().getTextWidget();
        widget.getDisplay().asyncExec(() -> {
            if (!widget.isDisposed() && widget.getVerticalBar() != null) {
                int selection = widget.getVerticalBar().getSelection();

                if (selection > 0 && selection < widget.getBounds().height / 2
                        && widget.getLocationAtOffset(widget.getSelection().x).y + selection
                        + widget.getLineHeight() * 2 < widget.getBounds().height) {
                    ReflectionUtils.invokeMethod(widget, "scrollVertical", new Class[] { //$NON-NLS-1$
                            int.class, boolean.class }, new Object[] { -selection, true });
                }
            }
        });
    }

    private static boolean debugOptionChange(String source) {
        return isDebug(source) != ClassUtil.isDebug();
    }

    public static boolean isDebug(String source) {
        if (source == null) {
            return false;
        }
        Matcher matcher = LINE_NUMBER_COMMENT.matcher(source);
        return matcher.find() || source.indexOf(NO_LINE_NUMBER) != -1;
    }

    public IBuffer getClassBuffer() {
        return classBuffer;
    }

    private void callSuperDoSetInput(IEditorInput input) throws CoreException {
        super.doSetInput(input);
        refreshSemanticHighlighting();
        updateTitleImage();
        revealNestedOpenTarget();
    }

    /**
     * Sets editor input only if buffer was actually opened.
     *
     * @param force if <code>true</code> initialize no matter what
     */
    public void doSetInput(boolean force) {
        IEditorInput input = getEditorInput();
        try {
            if (doOpenBuffer(input, force)) {
                callSuperDoSetInput(input);
            }
        } catch (Exception e) {
            JavaDecompilerPlugin.logError(e, ""); //$NON-NLS-1$
        }
    }

    public void doSetInput(String type, boolean force) {
        IEditorInput input = getEditorInput();
        try {
            if (doOpenBuffer(input, type, force)) {
                callSuperDoSetInput(input);
            }
        } catch (Exception e) {
            JavaDecompilerPlugin.logError(e, ""); //$NON-NLS-1$
        }
    }

    public boolean refreshContentIfNeeded() {
        IDocument document = getCurrentDocument();
        if (document == null || isBlank(document)) {
            showLoadingPlaceholder();
            doSetInput(true);
            showSource();
            return true;
        }

        String code = document.get();
        if (ClassUtil.isDebug() != isDebug(code)) {
            doSetInput(false);
            showSource();
            return true;
        }
        return false;
    }

    private IDocument getCurrentDocument() {
        if (getViewer() != null && getViewer().getDocument() != null) {
            return getViewer().getDocument();
        }
        if (getSourceViewer() != null && getSourceViewer().getDocument() != null) {
            return getSourceViewer().getDocument();
        }
        return null;
    }

    private boolean isBlank(IDocument document) {
        int length = document.getLength();
        if (length == 0) {
            return true;
        }
        try {
            for (int i = 0; i < length; i++) {
                if (!Character.isWhitespace(document.getChar(i))) {
                    return false;
                }
            }
            return true;
        } catch (BadLocationException e) {
            JavaDecompilerPlugin.logError(e, ""); //$NON-NLS-1$
            return document.get().isBlank();
        }
    }

    @Override
    protected void doSetInput(IEditorInput input) throws CoreException {
        nestedOpenTarget = getNestedOpenTarget(input);
        input = getTopLevelEditorInput(input);
        switch (input) {
            case IFileEditorInput in -> {
                String filePath = UIUtil.getPathLocation(in.getStorage().getFullPath());
                if (filePath == null || !new File(filePath).exists()) {
                    callSuperDoSetInput(input);
                } else {
                    doSetInput(new DecompilerClassEditorInput(EFS.getLocalFileSystem().getStore(new Path(filePath))));
                }
            }
            case FileStoreEditorInput storeInput -> {
                IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
                decompilerType = prefs.getString(JavaDecompilerPlugin.DECOMPILER_TYPE);
                String source = DecompileUtil.decompiler(storeInput, decompilerType);

                if (source != null) {
                    String packageName = DecompileUtil.getPackageName(source);
                    String classFullName = packageName == null ? storeInput.getName()
                            : packageName + "." //$NON-NLS-1$
                            + storeInput.getName().replaceAll("(?i)\\.class", //$NON-NLS-1$
                                    ""); //$NON-NLS-1$

                    File file = new File(System.getProperty("java.io.tmpdir"), //$NON-NLS-1$
                            storeInput.getName().replaceAll("(?i)\\.class", //$NON-NLS-1$
                                    System.currentTimeMillis() + ".java")); //$NON-NLS-1$
                    FileUtil.writeToFile(file, source, ResourcesPlugin.getEncoding());
                    file.deleteOnExit();

                    DecompilerClassEditorInput editorInput = new DecompilerClassEditorInput(
                            EFS.getLocalFileSystem().getStore(new Path(file.getAbsolutePath())));
                    editorInput.setToolTipText(classFullName);

                    IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                            .openEditor(editorInput, "org.eclipse.jdt.ui.CompilationUnitEditor"); //$NON-NLS-1$
                    try {
                        ReflectionUtils.invokeMethod(editor, "setPartName", //$NON-NLS-1$
                                new Class[] { String.class }, new String[] { getPartTitle(storeInput.getName()) });

                        Image decompilerImage = resolveDecompilerTitleImage();
                        if (isUsableImage(decompilerImage)) {
                            ReflectionUtils.invokeMethod(editor, "setTitleImage", //$NON-NLS-1$
                                    new Class[] { Image.class },
                                    new Object[] { decompilerImage });
                        }

                        ReflectionUtils.setFieldValue(editor, "fIsEditingDerivedFileAllowed", //$NON-NLS-1$
                                Boolean.valueOf(false));
                    } catch (Exception e) {
                        JavaDecompilerPlugin.logError(e, ""); //$NON-NLS-1$
                    }
                }
                Display.getDefault().asyncExec(() -> JavaDecompilerClassFileEditor.this.getEditorSite().getPage()
                        .closeEditor(JavaDecompilerClassFileEditor.this, false));

                throw new CoreException(new Status(8, JavaDecompilerPlugin.PLUGIN_ID, 1, "", //$NON-NLS-1$
                        null));
            }
            default -> {
                if (input instanceof InternalClassFileEditorInput classInput) {

                    if (classInput.getClassFile().getParent() instanceof PackageFragment) {
                        doOpenBuffer(input, false);
                    } else {
                        IPath relativePath = classInput.getClassFile().getParent().getPath();
                        String location = UIUtil.getPathLocation(relativePath);
                        if (!FileUtil.isZipFile(location) && !FileUtil.isZipFile(relativePath.toOSString())) {
                            String filePath = UIUtil.getPathLocation(classInput.getClassFile().getPath());
                            if (filePath != null) {
                                DecompilerClassEditorInput editorInput = new DecompilerClassEditorInput(
                                        EFS.getLocalFileSystem().getStore(new Path(filePath)));
                                doSetInput(editorInput);
                            } else {
                                doSetInput(new DecompilerClassEditorInput(
                                        EFS.getLocalFileSystem().getStore(classInput.getClassFile().getPath())));
                            }
                            return;
                        }
                    }
                }
                try {
                    doOpenBuffer(input, false);
                } catch (JavaModelException e) {
                    IClassFileEditorInput classFileEditorInput = (IClassFileEditorInput) input;
                    IClassFile file = classFileEditorInput.getClassFile();

                    if (file.getSourceRange() == null && file.getBytes() != null && ClassUtil.isClassFile(file.getBytes())) {
                        File classFile = new File(JavaDecompilerPlugin.getDefault().getPreferenceStore()
                                .getString(JavaDecompilerPlugin.TEMP_DIR), file.getElementName());
                        try {
                            try (FileOutputStream fos = new FileOutputStream(classFile)) {
                                fos.write(file.getBytes());
                            }

                            doSetInput(new DecompilerClassEditorInput(
                                    EFS.getLocalFileSystem().getStore(new Path(classFile.getAbsolutePath()))));
                            classFile.delete();
                            return;
                        } catch (IOException e1) {
                            JavaDecompilerPlugin.logError(e, ""); //$NON-NLS-1$
                        } finally {
                            if (classFile.exists()) {
                                classFile.delete();
                            }
                        }
                    }
                }

                callSuperDoSetInput(input);
            }
        }
    }

    private IEditorInput getTopLevelEditorInput(IEditorInput input) {
        if (input instanceof IClassFileEditorInput classFileInput) {
            IClassFile classFile = classFileInput.getClassFile();
            IClassFile topLevelClassFile = ClassUtil.getTopLevelClassFile(classFile);
            if (topLevelClassFile != null && !topLevelClassFile.equals(classFile)) {
                IEditorInput topLevelInput = EditorUtility.getEditorInput(topLevelClassFile);
                if (topLevelInput != null) {
                    return topLevelInput;
                }
            }
        }
        return input;
    }

    private IJavaElement getNestedOpenTarget(IEditorInput input) {
        if (input instanceof IClassFileEditorInput classFileInput) {
            IClassFile classFile = classFileInput.getClassFile();
            IClassFile topLevelClassFile = ClassUtil.getTopLevelClassFile(classFile);
            if (topLevelClassFile != null && !topLevelClassFile.equals(classFile)) {
                nestedOpenClassFile = classFile;
                return findNestedType(topLevelClassFile, classFile);
            }
        }
        nestedOpenClassFile = null;
        return null;
    }

    private ISourceReference resolveSelectionTarget(ISourceReference reference) {
        if (!(reference instanceof IJavaElement javaElement)) {
            return reference;
        }
        if (belongsToEditorInput(javaElement)) {
            rememberNestedSelection(javaElement);
            return reference;
        }

        IClassFile classFile = getClassFile(javaElement);
        IClassFile editorClassFile = getEditorClassFile();
        if (classFile == null || editorClassFile == null || editorClassFile.equals(classFile)) {
            return reference;
        }

        nestedOpenClassFile = classFile;
        IJavaElement nestedType = findNestedType(editorClassFile, classFile);
        nestedOpenTarget = nestedType;
        return nestedType instanceof ISourceReference sourceReference ? sourceReference : reference;
    }

    private void rememberNestedSelection(IJavaElement javaElement) {
        IClassFile editorClassFile = getEditorClassFile();
        IType editorType = getType(editorClassFile);
        if (!(javaElement instanceof IType type) || editorType == null) {
            return;
        }
        if (editorType.equals(type)) {
            nestedOpenTarget = null;
            nestedOpenClassFile = null;
            return;
        }
        nestedOpenTarget = javaElement;
        nestedOpenClassFile = findNestedClassFile(type, editorClassFile);
    }

    private boolean belongsToEditorInput(IJavaElement javaElement) {
        IClassFile editorClassFile = getEditorClassFile();
        IClassFile classFile = getClassFile(javaElement);
        return editorClassFile != null && editorClassFile.equals(classFile);
    }

    private void revealNestedOpenTarget() {
        IJavaElement target = nestedOpenTarget;
        if (!(target instanceof ISourceReference sourceReference)) {
            return;
        }

        Display.getDefault().asyncExec(() -> {
            if (isEditorControlDisposed()) {
                return;
            }
            setSelection(sourceReference, true);
        });
    }

    private IJavaElement findNestedType(IClassFile topLevelClassFile, IClassFile nestedClassFile) {
        if (topLevelClassFile == null || nestedClassFile == null) {
            return null;
        }

        IType type = getType(topLevelClassFile);
        IType nestedType = getType(nestedClassFile);
        String topLevelName = stripClassExtension(topLevelClassFile.getElementName());
        String nestedName = stripClassExtension(nestedClassFile.getElementName());
        if (type == null || !nestedName.startsWith(topLevelName + NESTED_CLASS_SEPARATOR)) {
            return nestedType;
        }

        String suffix = nestedName.substring(topLevelName.length() + 1);
        IType resolved = resolveNestedType(type, suffix);
        return resolved != null ? resolved : nestedType;
    }

    /**
     * Resolves a nested type by traversing the type hierarchy using the given
     * {@code $}-separated suffix. At each level the candidate name grows by one
     * more {@code $}-delimited part so that simple names that themselves contain
     * {@code $} (e.g. {@code Inner$Proxy} as a direct member) are matched
     * correctly before falling back to a deeper traversal.
     */
    private IType resolveNestedType(IType parent, String suffix) {
        if (suffix.isEmpty()) {
            return parent;
        }
        String[] parts = suffix.split("\\$"); //$NON-NLS-1$
        StringBuilder candidate = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                return null;
            }
            if (i > 0) {
                candidate.append(NESTED_CLASS_SEPARATOR);
            }
            candidate.append(parts[i]);
            IType child = parent.getType(candidate.toString());
            if (child != null && child.exists()) {
                String remaining = suffix.substring(candidate.length());
                if (remaining.isEmpty()) {
                    return child;
                }
                // remaining starts with '$'; skip it and recurse
                IType deeper = resolveNestedType(child, remaining.substring(1));
                if (deeper != null) {
                    return deeper;
                }
                // child matched but deeper resolution failed – try a longer candidate
            }
        }
        return null;
    }

    private String stripClassExtension(String name) {
        if (name != null && name.endsWith(CLASS_FILE_EXTENSION)) {
            return name.substring(0, name.length() - CLASS_FILE_EXTENSION.length());
        }
        return name == null ? "" : name; //$NON-NLS-1$
    }

    private IType getType(IClassFile classFile) {
        if (classFile instanceof IOrdinaryClassFile ordinaryClassFile) {
            return ordinaryClassFile.getType();
        }
        return null;
    }

    private IClassFile getEditorClassFile() {
        if (getEditorInput() instanceof IClassFileEditorInput classFileInput) {
            return classFileInput.getClassFile();
        }
        return null;
    }

    private IClassFile getClassFile(IJavaElement javaElement) {
        if (javaElement instanceof IClassFile classFile) {
            return classFile;
        }
        IJavaElement classFile = javaElement.getAncestor(IJavaElement.CLASS_FILE);
        return classFile instanceof IClassFile cf ? cf : null;
    }

    private IClassFile findNestedClassFile(IType type, IClassFile editorClassFile) {
        String classFileName = getNestedClassFileName(type, editorClassFile);
        if (classFileName == null) {
            return null;
        }

        IJavaElement parent = editorClassFile.getParent();
        if (parent instanceof IPackageFragment pkg) {
            IClassFile classFile = pkg.getClassFile(classFileName);
            if (classFile.exists()) {
                return classFile;
            }
        }
        return null;
    }

    private String getNestedClassFileName(IType type, IClassFile editorClassFile) {
        String topLevelName = stripClassExtension(editorClassFile.getElementName());
        StringBuilder suffix = new StringBuilder();
        IType editorType = getType(editorClassFile);
        if (editorType == null) {
            return null;
        }
        IJavaElement current = type;
        while (current instanceof IType currentType && !editorType.equals(currentType)) {
            String segment = currentType.getElementName();
            if (segment.isBlank()) {
                return null;
            }
            suffix.insert(0, segment).insert(0, NESTED_CLASS_SEPARATOR);
            current = currentType.getParent();
        }
        if (suffix.length() == 0) {
            return null;
        }
        return topLevelName + suffix + CLASS_FILE_EXTENSION;
    }

    private void syncPackageExplorer(ISourceReference selection) {
        if (!(selection instanceof IJavaElement javaElement)) {
            return;
        }
        if (!isPackageExplorerSyncEnabled()) {
            return;
        }

        IClassFile fallback = nestedOpenClassFile;
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return;
        }
        cancelPendingPackageExplorerReveals(display);

        int revealSequence = ++packageExplorerRevealSequence;
        display.asyncExec(createPackageExplorerRevealRunnable(revealSequence, javaElement, fallback));
        pendingPackageExplorerReveal150 = createPackageExplorerRevealRunnable(revealSequence, javaElement, fallback);
        display.timerExec(150, pendingPackageExplorerReveal150);
        pendingPackageExplorerReveal750 = createPackageExplorerRevealRunnable(revealSequence, javaElement, fallback);
        display.timerExec(750, pendingPackageExplorerReveal750);
        pendingPackageExplorerReveal1500 = createPackageExplorerRevealRunnable(revealSequence, javaElement, fallback);
        display.timerExec(1500, pendingPackageExplorerReveal1500);
    }

    private Runnable createPackageExplorerRevealRunnable(int revealSequence, IJavaElement javaElement,
            IClassFile fallback) {
        return () -> {
            if (revealSequence != packageExplorerRevealSequence || !isPackageExplorerSyncEnabled()) {
                return;
            }
            revealInPackageExplorer(javaElement, fallback);
        };
    }

    private void cancelPendingPackageExplorerReveals(Display display) {
        if (pendingPackageExplorerReveal150 != null) {
            display.timerExec(-1, pendingPackageExplorerReveal150);
            pendingPackageExplorerReveal150 = null;
        }
        if (pendingPackageExplorerReveal750 != null) {
            display.timerExec(-1, pendingPackageExplorerReveal750);
            pendingPackageExplorerReveal750 = null;
        }
        if (pendingPackageExplorerReveal1500 != null) {
            display.timerExec(-1, pendingPackageExplorerReveal1500);
            pendingPackageExplorerReveal1500 = null;
        }
    }

    private boolean isPackageExplorerSyncEnabled() {
        if (getSite() == null || getSite().getPage() == null) {
            return false;
        }
        IViewPart view = getSite().getPage().findView(JavaUI.ID_PACKAGES);
        return view instanceof IPackagesViewPart packageView && packageView.isLinkingEnabled();
    }

    private void revealInPackageExplorer(IJavaElement javaElement, IClassFile fallback) {
        if (getSite() == null || getSite().getPage() == null) {
            return;
        }
        IViewPart view = getSite().getPage().findView(JavaUI.ID_PACKAGES);
        if (view instanceof IPackagesViewPart packageView && packageView.isLinkingEnabled()) {
            ensurePackageExplorerViewShowsMembers(packageView);
            expandPackageExplorerParents(packageView, javaElement);
            packageView.selectAndReveal(javaElement);
            if (fallback != null && !isPackageExplorerSelection(packageView, javaElement)) {
                packageView.selectAndReveal(fallback);
            }
        }
    }

    private void expandPackageExplorerParents(IPackagesViewPart packageView, IJavaElement javaElement) {
        IJavaElement parent = javaElement.getParent();
        while (parent != null && parent.getElementType() != IJavaElement.CLASS_FILE
                && parent.getElementType() != IJavaElement.COMPILATION_UNIT) {
            parent = parent.getParent();
        }
        if (parent != null) {
            packageView.getTreeViewer().refresh(parent);
            packageView.getTreeViewer().expandToLevel(parent, 1);
        }
    }

    private void ensurePackageExplorerViewShowsMembers(IPackagesViewPart packageView) {
        if (packageView.getTreeViewer().getContentProvider() instanceof StandardJavaElementContentProvider provider
                && !provider.getProvideMembers()) {
            provider.setProvideMembers(true);
            packageView.getTreeViewer().refresh();
        }
    }

    private boolean isPackageExplorerSelection(IPackagesViewPart packageView, Object target) {
        if (!(packageView.getTreeViewer().getSelection() instanceof IStructuredSelection selection)) {
            return false;
        }
        Object selected = selection.getFirstElement();
        if (selected instanceof IJavaElement selectedJavaElement && target instanceof IJavaElement targetElement) {
            return selectedJavaElement.equals(targetElement)
                    || selectedJavaElement.getHandleIdentifier().equals(targetElement.getHandleIdentifier());
        }
        return target != null && target.equals(selected);
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        IJavaElement showInTarget = getShowInTarget();
        if (adapter == IShowInSource.class && showInTarget != null) {
            IShowInSource source = () -> new ShowInContext(getEditorInput(), new StructuredSelection(showInTarget));
            return adapter.cast(source);
        }
        return super.getAdapter(adapter);
    }

    private IJavaElement getShowInTarget() {
        if (selectedElement instanceof IJavaElement javaElement) {
            return javaElement;
        }
        return nestedOpenTarget;
    }

    @Override
    protected void setPartName(String partName) {
        super.setPartName(getPartTitle(partName));
    }

    private String getPartTitle(String title) {
        return title;
    }

    private boolean shouldShowDecompilerInTitle() {
        if (!(getEditorInput() instanceof IClassFileEditorInput in)) {
            return true;
        }
        IClassFile classFile = in.getClassFile();
        if (classFile == null || !classFile.exists()) {
            return true;
        }
        IJavaElement root = classFile.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
        if (!(root instanceof IPackageFragmentRoot packageRoot)) {
            return true;
        }
        try {
            return packageRoot.getSourceAttachmentPath() == null;
        } catch (JavaModelException e) {
            return true;
        }
    }

    public void updateTitleImage() {
        Image decompilerTitleImage = resolveDecompilerTitleImage();
        if (!isUsableImage(decompilerTitleImage)) {
            return;
        }

        Image currentTitleImage = getTitleImage();
        if (currentTitleImage != decompilerTitleImage) {
            setTitleImage(decompilerTitleImage);
        }
    }

    private boolean isUsableImage(Image image) {
        return image != null && !image.isDisposed();
    }

    private Image resolveDecompilerTitleImage() {
        if (decompilerType == null || !shouldShowDecompilerInTitle()) {
            return null;
        }
        return JavaDecompilerPlugin.getDecompilerImage(decompilerType);
    }

    @Override
    public void createPartControl(Composite parent) {
        super.createPartControl(parent);
        createLoadingControl();
        IDocument document = getCurrentDocument();
        if (document == null || isBlank(document)) {
            showLoadingPlaceholder();
            Display.getDefault().asyncExec(() -> {
                if (!isEditorControlDisposed()) {
                    refreshContentIfNeeded();
                }
            });
        } else {
            showSource();
        }
    }

    protected JavaDecompilerBufferManager getBufferManager() {
        JavaDecompilerBufferManager manager;
        BufferManager defManager = BufferManager.getDefaultBufferManager();
        if (defManager instanceof JavaDecompilerBufferManager javaDecompilerBufferManager) {
            manager = javaDecompilerBufferManager;
        } else {
            manager = new JavaDecompilerBufferManager(defManager);
        }
        return manager;
    }

    public void notifyPropertiesChange() {
        ReflectionUtils.invokeMethod(this.getViewer(), "fireSelectionChanged", //$NON-NLS-1$
                new Class[] { SelectionChangedEvent.class }, new Object[] {
                        new SelectionChangedEvent((ISelectionProvider) this.getViewer(), new StructuredSelection()) });
    }

    @Override
    public String[] collectContextMenuPreferencePages() {
        String[] inheritedPages = super.collectContextMenuPreferencePages();
        int length = 1;
        String[] result = new String[inheritedPages.length + length];
        result[0] = "io.github.nbauma109.decompiler.Main"; //$NON-NLS-1$
        System.arraycopy(inheritedPages, 0, result, length, inheritedPages.length);
        return result;
    }

    @Override
    protected void createActions() {
        super.createActions();

        setAction(ITextEditorActionConstants.COPY, null);

        final String BUNDLE_FOR_CONSTRUCTED_KEYS = "org.eclipse.ui.texteditor.ConstructedEditorMessages";//$NON-NLS-1$
        ResourceBundle fgBundleForConstructedKeys = ResourceBundle.getBundle(BUNDLE_FOR_CONSTRUCTED_KEYS);
        final IAction copyAction = new Action(fgBundleForConstructedKeys.getString("Editor.Copy.label")) { //$NON-NLS-1$

            @Override
            public void run() {
                ((SourceViewer) JavaDecompilerClassFileEditor.this.getSourceViewer()).getTextWidget().copy();
            }
        };
        copyAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_COPY);
        setAction(ITextEditorActionConstants.COPY, copyAction);

        setAction(ITextEditorActionConstants.SELECT_ALL, null);
        final IAction selectAllAction = new Action(fgBundleForConstructedKeys.getString("Editor.SelectAll.label")) { //$NON-NLS-1$

            @Override
            public void run() {
                ((SourceViewer) JavaDecompilerClassFileEditor.this.getSourceViewer()).getTextWidget().selectAll();
            }
        };
        selectAllAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_SELECT_ALL);
        setAction(ITextEditorActionConstants.SELECT_ALL, copyAction);

        setAction(ITextEditorActionConstants.FIND, null);
        FindReplaceAction findAction = new FindReplaceAction(fgBundleForConstructedKeys, "Editor.FindReplace.", //$NON-NLS-1$
                this) {

            @Override
            public void run() {
                ReflectionUtils.setFieldValue(this, FIND_TARGET_FIELD,
                        JavaDecompilerClassFileEditor.this.getAdapter(IFindReplaceTarget.class));

                super.run();
            }
        };
        findAction.setHelpContextId(IAbstractTextEditorHelpContextIds.FIND_ACTION);
        findAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_FIND_AND_REPLACE);
        setAction(ITextEditorActionConstants.FIND, findAction);

        setAction(ITextEditorActionConstants.FIND_NEXT, null);
        FindNextAction findNextAction = new FindNextAction(fgBundleForConstructedKeys, "Editor.FindNext.", //$NON-NLS-1$
                this, true) {

            @Override
            public void run() {
                ReflectionUtils.setFieldValue(this, FIND_TARGET_FIELD,
                        JavaDecompilerClassFileEditor.this.getAdapter(IFindReplaceTarget.class));

                super.run();
            }
        };
        findNextAction.setHelpContextId(IAbstractTextEditorHelpContextIds.FIND_NEXT_ACTION);
        findNextAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.FIND_NEXT);
        setAction(ITextEditorActionConstants.FIND_NEXT, findNextAction);

        setAction(ITextEditorActionConstants.FIND_PREVIOUS, null);
        FindNextAction findPreviousAction = new FindNextAction(fgBundleForConstructedKeys, "Editor.FindPrevious.", //$NON-NLS-1$
                this, false) {

            @Override
            public void run() {
                ReflectionUtils.setFieldValue(this, FIND_TARGET_FIELD,
                        JavaDecompilerClassFileEditor.this.getAdapter(IFindReplaceTarget.class));

                super.run();
            }
        };
        findPreviousAction.setHelpContextId(IAbstractTextEditorHelpContextIds.FIND_PREVIOUS_ACTION);
        findPreviousAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.FIND_PREVIOUS);
        setAction(ITextEditorActionConstants.FIND_PREVIOUS, findPreviousAction);

        setAction(ITextEditorActionConstants.FIND_INCREMENTAL, null);
        IncrementalFindAction incrementalFindAction = new IncrementalFindAction(fgBundleForConstructedKeys,
                "Editor.FindIncremental.", //$NON-NLS-1$
                this, true) {

            @Override
            public void run() {
                try {
                    Class<?> clazz = Class.forName("org.eclipse.ui.texteditor.IncrementalFindTarget"); //$NON-NLS-1$
                    ReflectionUtils.setFieldValue(this, FIND_TARGET_FIELD,
                            JavaDecompilerClassFileEditor.this.getAdapter(clazz));
                } catch (ClassNotFoundException e) {
                    Logger.debug(e);
                }

                super.run();
            }
        };
        incrementalFindAction.setHelpContextId(IAbstractTextEditorHelpContextIds.FIND_INCREMENTAL_ACTION);
        incrementalFindAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.FIND_INCREMENTAL);
        setAction(ITextEditorActionConstants.FIND_INCREMENTAL, incrementalFindAction);

        setAction(ITextEditorActionConstants.FIND_INCREMENTAL_REVERSE, null);
        IncrementalFindAction incrementalFindReverseAction = new IncrementalFindAction(fgBundleForConstructedKeys,
                "Editor.FindIncrementalReverse.", //$NON-NLS-1$
                this, false) {

            @Override
            public void run() {
                try {
                    Class<?> clazz = Class.forName("org.eclipse.ui.texteditor.IncrementalFindTarget"); //$NON-NLS-1$
                    ReflectionUtils.setFieldValue(this, FIND_TARGET_FIELD,
                            JavaDecompilerClassFileEditor.this.getAdapter(clazz));
                } catch (ClassNotFoundException e) {
                    Logger.debug(e);
                }

                super.run();
            }
        };
        incrementalFindReverseAction
        .setHelpContextId(IAbstractTextEditorHelpContextIds.FIND_INCREMENTAL_REVERSE_ACTION);
        incrementalFindReverseAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.FIND_INCREMENTAL_REVERSE);
        setAction(ITextEditorActionConstants.FIND_INCREMENTAL_REVERSE, incrementalFindReverseAction);

        setAction(ITextEditorActionConstants.GOTO_LINE, null);
        GotoLineAction gotoAction = new GotoLineAction(fgBundleForConstructedKeys, "Editor.GotoLine.", this) { //$NON-NLS-1$

            @Override
            protected ITextEditor getTextEditor() {
                return JavaDecompilerClassFileEditor.this;
            }
        };
        gotoAction.setHelpContextId(IAbstractTextEditorHelpContextIds.GOTO_LINE_ACTION);
        gotoAction.setActionDefinitionId(ITextEditorActionDefinitionIds.LINE_GOTO);
        setAction(ITextEditorActionConstants.GOTO_LINE, gotoAction);

        ReflectionUtils.setFieldValue(this, "fSourceCopyAction", copyAction); //$NON-NLS-1$
        ReflectionUtils.setFieldValue(this, "fSelectAllAction", selectAllAction); //$NON-NLS-1$

        final ActionGroup group = new DecompileActionGroup(this, ITextEditorActionConstants.GROUP_SAVE, true);
        CompositeActionGroup fContextMenuGroup = (CompositeActionGroup) ReflectionUtils.getFieldValue(this,
                "fContextMenuGroup"); //$NON-NLS-1$
        fContextMenuGroup.addGroup(group);
    }

    public void showSource() {
        if (sourceShown) {
            return;
        }
        Composite viewerComposite = (Composite) ReflectionUtils.getFieldValue(this, "fViewerComposite"); //$NON-NLS-1$
        if (viewerComposite == null || viewerComposite.isDisposed()) {
            return;
        }
        showTopControl(viewerComposite);
        sourceShown = true;
    }

    protected void showSource(IClassFileEditorInput classFileEditorInput) {
        showSource();
    }

    private void createLoadingControl() {
        if (loadingComposite != null && !loadingComposite.isDisposed()) {
            return;
        }
        try {
            Composite editorParent = (Composite) ReflectionUtils.getFieldValue(this, "fParent"); //$NON-NLS-1$
            if (editorParent == null || editorParent.isDisposed()) {
                return;
            }

            loadingComposite = new Composite(editorParent, SWT.NONE);
            GridLayout layout = new GridLayout(1, false);
            layout.marginWidth = 24;
            layout.marginHeight = 24;
            layout.verticalSpacing = 10;
            loadingComposite.setLayout(layout);

            Label loadingLabel = new Label(loadingComposite, SWT.CENTER);
            loadingLabel.setText("Loading decompiled source..."); //$NON-NLS-1$
            loadingLabel.setLayoutData(new GridData(SWT.CENTER, SWT.END, true, true));

            loadingSpinner = new Canvas(loadingComposite, SWT.DOUBLE_BUFFERED);
            GridData spinnerData = new GridData(SWT.CENTER, SWT.BEGINNING, false, false);
            spinnerData.widthHint = 32;
            spinnerData.heightHint = 32;
            loadingSpinner.setLayoutData(spinnerData);
            loadingSpinner.addPaintListener(this::paintLoadingSpinner);
        } catch (Exception e) {
            Logger.debug(e);
        }
    }

    private void showLoadingPlaceholder() {
        if (loadingComposite == null || loadingComposite.isDisposed()) {
            createLoadingControl();
        }
        showTopControl(loadingComposite);
        sourceShown = false;
        startLoadingSpinner();
    }

    private void showTopControl(Composite topControl) {
        if (topControl == null || topControl.isDisposed()) {
            return;
        }
        try {
            StackLayout stackLayout = (StackLayout) ReflectionUtils.getFieldValue(this, "fStackLayout"); //$NON-NLS-1$
            Composite editorParent = (Composite) ReflectionUtils.getFieldValue(this, "fParent"); //$NON-NLS-1$
            if (stackLayout != null && editorParent != null && !editorParent.isDisposed()) {
                stackLayout.topControl = topControl;
                editorParent.layout();
            }
        } catch (Exception e) {
            Logger.debug(e);
        }
    }

    private boolean isEditorControlDisposed() {
        if (getSourceViewer() == null || getSourceViewer().getTextWidget() == null) {
            return true;
        }
        return getSourceViewer().getTextWidget().isDisposed();
    }

    private void startLoadingSpinner() {
        if (loadingSpinner == null || loadingSpinner.isDisposed()) {
            return;
        }
        Display display = loadingSpinner.getDisplay();
        display.timerExec(0, new Runnable() {
            @Override
            public void run() {
                if (loadingSpinner == null || loadingSpinner.isDisposed() || sourceShown) {
                    return;
                }
                loadingSpinnerFrame = (loadingSpinnerFrame + 1) % 12;
                loadingSpinner.redraw();
                display.timerExec(80, this);
            }
        });
    }

    private void paintLoadingSpinner(PaintEvent event) {
        if (loadingSpinner == null || loadingSpinner.isDisposed()) {
            return;
        }
        Rectangle bounds = loadingSpinner.getClientArea();
        int size = Math.min(bounds.width, bounds.height) - 4;
        int x = (bounds.width - size) / 2;
        int y = (bounds.height - size) / 2;

        event.gc.setAdvanced(true);
        event.gc.setAntialias(SWT.ON);

        for (int i = 0; i < 12; i++) {
            int alpha = 35 + ((i + loadingSpinnerFrame) % 12) * 18;
            event.gc.setAlpha(Math.min(alpha, 255));
            event.gc.setLineWidth(3);
            int startAngle = i * 30;
            event.gc.drawArc(x, y, size, size, startAngle, 18);
        }
        event.gc.setAlpha(255);
    }

    @Override
    public void setTitleImage(Image titleImage) {
        super.setTitleImage(titleImage);
    }
}
