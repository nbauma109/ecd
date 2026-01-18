/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.editor;

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
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.BufferManager;
import org.eclipse.jdt.internal.core.ClassFile;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.javaeditor.ClassFileEditor;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jdt.internal.ui.javaeditor.InternalClassFileEditorInput;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IFindReplaceTarget;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.texteditor.FindNextAction;
import org.eclipse.ui.texteditor.FindReplaceAction;
import org.eclipse.ui.texteditor.GotoLineAction;
import org.eclipse.ui.texteditor.IAbstractTextEditorHelpContextIds;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.IWorkbenchActionDefinitionIds;
import org.eclipse.ui.texteditor.IncrementalFindAction;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.actions.DecompileActionGroup;
import org.sf.feeling.decompiler.util.ClassUtil;
import org.sf.feeling.decompiler.util.CommentUtil;
import org.sf.feeling.decompiler.util.DecompileUtil;
import org.sf.feeling.decompiler.util.FileUtil;
import org.sf.feeling.decompiler.util.Logger;
import org.sf.feeling.decompiler.util.ReflectionUtils;
import org.sf.feeling.decompiler.util.UIUtil;

public class JavaDecompilerClassFileEditor extends ClassFileEditor {

    private static final String NO_LINE_NUMBER = "// Warning: No line numbers available in class file"; //$NON-NLS-1$

    private IBuffer classBuffer;
    private boolean sourceShown = false;
    private boolean selectionChange = false;
    private ISourceReference selectedElement = null;
    private String decompilerType = null;

    public ISourceReference getSelectedElement() {
        return selectedElement;
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
            IClassFile cf = in.getClassFile();

            decompilerType = type;
            String origSrc = cf.getSource();
            if (origSrc == null || always || !reuseBuf || debugOptionChange(origSrc) || force) {
                DecompilerSourceMapper sourceMapper = JavaDecompilerPlugin.getDefault().getSourceMapper(decompilerType);
                char[] src = sourceMapper == null ? null : sourceMapper.findSource(cf.getType());
                if (src == null) {
                    return false;
                }
                char[] markedSrc = src;
                classBuffer = BufferManager.createBuffer(cf);
                classBuffer.setContents(markedSrc);
                getBufferManager().addBuffer(classBuffer);

                sourceMapper.mapSourceSwitch(cf.getType(), markedSrc, true);

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
        super.setSelection(reference, moveCursor);

        this.selectedElement = reference;
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
        Pattern pattern = CommentUtil.LINE_NUMBER_COMMENT; // $NON-NLS-1$
        Matcher matcher = pattern.matcher(source);
        return matcher.find() || source.indexOf(NO_LINE_NUMBER) != -1;
    }

    public IBuffer getClassBuffer() {
        return classBuffer;
    }

    private void callSuperDoSetInput(IEditorInput input) throws CoreException {
        super.doSetInput(input);
        refreshSemanticHighlighting();
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

    @Override
    protected void doSetInput(IEditorInput input) throws CoreException {
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
              String source = DecompileUtil.decompiler(storeInput, prefs.getString(JavaDecompilerPlugin.DECOMPILER_TYPE));
  
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
  
                      ReflectionUtils.invokeMethod(editor, "setTitleImage", //$NON-NLS-1$
                              new Class[] { Image.class },
                              new Object[] { JavaDecompilerPlugin.getImageDescriptor("icons/decompiler.png") //$NON-NLS-1$
                                      .createImage() });
  
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

    @Override
    protected void setPartName(String partName) {
        super.setPartName(getPartTitle(partName));
    }

    private String getPartTitle(String title) {
        if (decompilerType == null || title == null || title.endsWith("]")) {
            return title;
        }
        return title + " [" + decompilerType + "]";
    }

    @Override
    public void createPartControl(Composite parent) {
        super.createPartControl(parent);
        showSource();
    }

    protected JavaDecompilerBufferManager getBufferManager() {
        JavaDecompilerBufferManager manager;
        BufferManager defManager = BufferManager.getDefaultBufferManager();
        if (defManager instanceof JavaDecompilerBufferManager) {
            manager = (JavaDecompilerBufferManager) defManager;
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
        result[0] = "org.sf.feeling.decompiler.Main"; //$NON-NLS-1$
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
                ReflectionUtils.setFieldValue(this, "fTarget", //$NON-NLS-1$
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
                ReflectionUtils.setFieldValue(this, "fTarget", //$NON-NLS-1$
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
                ReflectionUtils.setFieldValue(this, "fTarget", //$NON-NLS-1$
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
                    ReflectionUtils.setFieldValue(this, "fTarget", //$NON-NLS-1$
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
                    ReflectionUtils.setFieldValue(this, "fTarget", //$NON-NLS-1$
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
        setAction(ITextEditorActionConstants.FIND_INCREMENTAL_REVERSE, incrementalFindAction);

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
        if (getEditorInput() instanceof IClassFileEditorInput in) {
            showSource(in);
        }
    }

    protected void showSource(IClassFileEditorInput classFileEditorInput) {
        if (sourceShown) {
            return;
        }
        try {
            StackLayout fStackLayout = (StackLayout) ReflectionUtils.getFieldValue(this, "fStackLayout"); //$NON-NLS-1$
            Composite fParent = (Composite) ReflectionUtils.getFieldValue(this, "fParent"); //$NON-NLS-1$
            Composite fViewerComposite = (Composite) ReflectionUtils.getFieldValue(this, "fViewerComposite"); //$NON-NLS-1$
            if (fStackLayout != null && fViewerComposite != null && fParent != null) {
                fStackLayout.topControl = fViewerComposite;
                fParent.layout();
            }
        } catch (Exception e) {
            Logger.debug(e);
        }
        sourceShown = true;
    }

}