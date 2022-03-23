
package org.sf.feeling.decompiler.editor;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.util.ClassFileBytesDisassembler;
import org.eclipse.jdt.internal.core.BinaryType;
import org.eclipse.jdt.internal.core.ClassFile;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;
import org.eclipse.jdt.internal.ui.javaeditor.breadcrumb.EditorBreadcrumb;
import org.eclipse.jdt.ui.text.IColorManager;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.JavaTextTools;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.IPostSelectionProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.internal.texteditor.LineNumberColumn;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.IUpdate;
import org.eclipse.ui.texteditor.rulers.IColumnSupport;
import org.eclipse.ui.texteditor.rulers.RulerColumnDescriptor;
import org.eclipse.ui.texteditor.rulers.RulerColumnRegistry;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.actions.ByteCodeAction;
import org.sf.feeling.decompiler.actions.DisassemblerAction;
import org.sf.feeling.decompiler.actions.SourceCodeAction;
import org.sf.feeling.decompiler.util.Logger;
import org.sf.feeling.decompiler.util.ReflectionUtils;
import org.sf.feeling.decompiler.util.UIUtil;

import com.drgarbage.asm.render.impl.OutlineElement;
import com.drgarbage.asm.render.impl.OutlineElementField;
import com.drgarbage.asm.render.impl.OutlineElementMethod;
import com.drgarbage.asm.render.impl.OutlineElementType;
import com.drgarbage.asm.render.intf.IClassFileDocument;
import com.drgarbage.asm.render.intf.IFieldSection;
import com.drgarbage.asm.render.intf.IMethodSection;
import com.drgarbage.asm.render.intf.IOutlineElementField;
import com.drgarbage.utils.ClassFileDocumentsUtils;

public class DisassemblerSourceViewer extends AbstractDecoratedTextEditor implements IPropertyChangeListener {

	private SelectionProvider fSelectionProvider = new JdtSelectionProvider();

	private JavaDecompilerClassFileEditor editor;

	private DisassemblerDocument disassemblerDocument;

	private Composite container;

	public StyledText getTextWidget() {
		return getSourceViewer().getTextWidget();
	}

	@Override
	public ISelectionProvider getSelectionProvider() {
		return fSelectionProvider;
	}

	public DisassemblerSourceViewer(JavaDecompilerClassFileEditor editor) {
		this.editor = editor;
	}

	private IPreferenceStore createCombinedPreferenceStore() {
		List<IPreferenceStore> stores = new ArrayList<>(3);

		stores.add(JavaDecompilerPlugin.getDefault().getPreferenceStore());
		stores.add(JavaPlugin.getDefault().getPreferenceStore());
		stores.add(EditorsUI.getPreferenceStore());

		return new ChainedPreferenceStore(stores.toArray(new IPreferenceStore[stores.size()]));
	}

	public Composite createControl(Composite parent) {
		setSite(editor.getSite());

		DisassemblerDocumentProvider provider = new DisassemblerDocumentProvider();
		setDocumentProvider(provider);
		setInput(editor.getEditorInput());

		container = new Composite(parent, SWT.NONE);
		container.setLayout(new FillLayout());
		IPreferenceStore store = createCombinedPreferenceStore();
		setPreferenceStore(store);

		int styles = SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION;

		IVerticalRuler fVerticalRuler = createVerticalRuler();
		ReflectionUtils.setFieldValue(this, "fVerticalRuler", fVerticalRuler); //$NON-NLS-1$

		SourceViewer fSourceViewer = new JavaSourceViewer(container, fVerticalRuler, null, false, styles, store);
		ReflectionUtils.setFieldValue(this, "fSourceViewer", fSourceViewer); //$NON-NLS-1$
		getSourceViewerDecorationSupport(fSourceViewer);

		createActions();

		ReflectionUtils.invokeMethod(this, "initializeSourceViewer", new Class[] { //$NON-NLS-1$
				IEditorInput.class }, new Object[] { getEditorInput() });

		if (fSourceViewerDecorationSupport != null)
			fSourceViewerDecorationSupport.install(getPreferenceStore());

		StyledText styledText = fSourceViewer.getTextWidget();
		styledText.addMouseListener(getCursorListener());
		styledText.addKeyListener(getCursorListener());

		ReflectionUtils.setFieldValue(this, "fEditorContextMenuId", "#TextEditorContext"); //$NON-NLS-1$ //$NON-NLS-2$
		String id = "#TextEditorContext"; //$NON-NLS-1$
		MenuManager manager = new MenuManager(id, id);
		manager.setRemoveAllWhenShown(true);
		manager.addMenuListener(getContextMenuListener());
		Menu fTextContextMenu = manager.createContextMenu(styledText);
		styledText.setMenu(fTextContextMenu);

		ReflectionUtils.setFieldValue(this, "fRulerContextMenuId", "#TextRulerContext"); //$NON-NLS-1$ //$NON-NLS-2$
		id = "#TextRulerContext"; //$NON-NLS-1$
		manager = new MenuManager(id, id);
		manager.setRemoveAllWhenShown(true);
		manager.addMenuListener(getContextMenuListener());

		Control rulerControl = fVerticalRuler.getControl();
		Menu fRulerContextMenu = manager.createContextMenu(rulerControl);
		rulerControl.setMenu(fRulerContextMenu);
		rulerControl.addMouseListener(getRulerMouseListener());

		createOverviewRulerContextMenu();

		JavaTextTools textTools = JavaPlugin.getDefault().getJavaTextTools();
		IColorManager colorManager = textTools.getColorManager();
		DisassemblerConfiguration classFileConfiguration = new DisassemblerConfiguration(colorManager, store, editor,
				IJavaPartitions.JAVA_PARTITIONING);
		fSourceViewer.configure(classFileConfiguration);
		setSourceViewerConfiguration(classFileConfiguration);
		getSourceViewerDecorationSupport(fSourceViewer).install(getPreferenceStore());
		initializeViewerColors(fSourceViewer);
		ReflectionUtils.invokeMethod(this, "initializeViewerFont", new Class[] { //$NON-NLS-1$
				ISourceViewer.class }, new Object[] { fSourceViewer });

		final EditorSelectionChangedListener fEditorSelectionChangedListener = new EditorSelectionChangedListener();
		fEditorSelectionChangedListener.install(getSelectionProvider());

		updateDocument(provider, fSourceViewer);

		IVerticalRuler ruler = getVerticalRuler();
		if (ruler instanceof CompositeRuler)
			updateContributedRulerColumns((CompositeRuler) ruler);

		IColumnSupport columnSupport = getAdapter(IColumnSupport.class);

		RulerColumnDescriptor lineNumberColumnDescriptor = RulerColumnRegistry.getDefault()
				.getColumnDescriptor(LineNumberColumn.ID);
		if (lineNumberColumnDescriptor != null)
			columnSupport.setColumnVisible(lineNumberColumnDescriptor, isLineNumberRulerVisible());

		IPropertyChangeListener fFontPropertyChangeListener = (IPropertyChangeListener) ReflectionUtils
				.getFieldValue(this, "fFontPropertyChangeListener"); //$NON-NLS-1$
		JFaceResources.getFontRegistry().addListener(fFontPropertyChangeListener);

		JavaDecompilerPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(this);

		parent.addDisposeListener(new DisposeListener() {

			@Override
			public void widgetDisposed(DisposeEvent e) {
				JavaDecompilerPlugin.getDefault().getPreferenceStore()
						.removePropertyChangeListener(DisassemblerSourceViewer.this);
				fEditorSelectionChangedListener.uninstall(getSelectionProvider());

				DisassemblerSourceViewer.this.dispose();
			}
		});

		return container;
	}

	private void updateDocument(DisassemblerDocumentProvider provider, ISourceViewer fSourceViewer) {
		IClassFile cf = (ClassFile) ((IClassFileEditorInput) editor.getEditorInput()).getClassFile();

		disassemblerDocument = new DisassemblerDocument(provider, editor);
		provider.setDocument(disassemblerDocument);

		JavaTextTools tools = JavaPlugin.getDefault().getJavaTextTools();
		tools.setupJavaDocumentPartitioner(disassemblerDocument, IJavaPartitions.JAVA_PARTITIONING);

		try {
			provider.setDocumentContent(disassemblerDocument, new ByteArrayInputStream(cf.getBytes()), null);
		} catch (CoreException e) {
			ClassFileBytesDisassembler disassembler = ToolFactory.createDefaultClassFileBytesDisassembler();
			try {
				String content = disassembler.disassemble(cf.getBytes(), "\n", ClassFileBytesDisassembler.DETAILED); //$NON-NLS-1$
				disassemblerDocument.set((content == null ? "" : content)); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (Exception ex) {
				Logger.debug(e);
			}
		}
		fSourceViewer.setDocument(disassemblerDocument);
	}

	class JdtSelectionProvider extends SelectionProvider {

		private List<ISelectionChangedListener> fSelectionListeners = new ArrayList<>();
		private List<ISelectionChangedListener> fPostSelectionListeners = new ArrayList<>();
		private ITextSelection fInvalidSelection;
		private ISelection fValidSelection;

		/*
		 * @see org.eclipse.jface.viewers.ISelectionProvider#
		 * addSelectionChangedListener(ISelectionChangedListener)
		 */
		@Override
		public void addSelectionChangedListener(ISelectionChangedListener listener) {
			super.addSelectionChangedListener(listener);
			if (getSourceViewer() != null)
				fSelectionListeners.add(listener);
		}

		/*
		 * @see org.eclipse.jface.viewers.ISelectionProvider#getSelection()
		 */
		@Override
		public ISelection getSelection() {
			if (fInvalidSelection != null)
				return fInvalidSelection;
			return super.getSelection();
		}

		/*
		 * @see org.eclipse.jface.viewers.ISelectionProvider#
		 * removeSelectionChangedListener(ISelectionChangedListener)
		 */
		@Override
		public void removeSelectionChangedListener(ISelectionChangedListener listener) {
			super.removeSelectionChangedListener(listener);
			if (getSourceViewer() != null)
				fSelectionListeners.remove(listener);
		}

		/*
		 * @see org.eclipse.jface.viewers.ISelectionProvider#setSelection(ISelection)
		 */
		@Override
		public void setSelection(ISelection selection) {
			if (selection instanceof ITextSelection) {
				if (fInvalidSelection != null) {
					fInvalidSelection = null;

					ITextSelection newSelection = (ITextSelection) selection;
					ITextSelection oldSelection = (ITextSelection) getSelection();

					if (newSelection.getOffset() == oldSelection.getOffset()
							&& newSelection.getLength() == oldSelection.getLength()) {
						markValid();
					} else {
						super.setSelection(selection);
					}
				} else {
					super.setSelection(selection);
				}
			} else if (selection instanceof IStructuredSelection
					&& ((IStructuredSelection) selection).getFirstElement() instanceof EditorBreadcrumb) {
				markInvalid();
			}
		}

		/*
		 * @see org.eclipse.jface.text.IPostSelectionProvider#
		 * addPostSelectionChangedListener(org.eclipse.jface.viewers.
		 * ISelectionChangedListener)
		 */
		@Override
		public void addPostSelectionChangedListener(ISelectionChangedListener listener) {
			super.addPostSelectionChangedListener(listener);
			if (getSourceViewer() != null && getSourceViewer().getSelectionProvider() instanceof IPostSelectionProvider)
				fPostSelectionListeners.add(listener);
		}

		/*
		 * @see org.eclipse.jface.text.IPostSelectionProvider#
		 * removePostSelectionChangedListener(org.eclipse.jface.viewers.
		 * ISelectionChangedListener)
		 */
		@Override
		public void removePostSelectionChangedListener(ISelectionChangedListener listener) {
			super.removePostSelectionChangedListener(listener);
			if (getSourceViewer() != null)
				fPostSelectionListeners.remove(listener);
		}

		/*
		 * @see org.eclipse.jface.text.IPostSelectionValidator#isValid()
		 */
		@Override
		public boolean isValid(ISelection postSelection) {
			return fInvalidSelection == null && super.isValid(postSelection);
		}

		/**
		 * Marks this selection provider as currently being invalid. An invalid
		 * selection is one which can not be selected in the source viewer.
		 */
		private void markInvalid() {
			fValidSelection = getSelection();
			fInvalidSelection = new TextSelection(0, 0);

			SelectionChangedEvent event = new SelectionChangedEvent(this, fInvalidSelection);

			for (ISelectionChangedListener listener : fSelectionListeners) {
				listener.selectionChanged(event);
			}

			for (ISelectionChangedListener listener : fPostSelectionListeners) {
				listener.selectionChanged(event);
			}
		}

		/**
		 * Marks this selection provider as being valid.
		 */
		private void markValid() {
			fInvalidSelection = null;

			SelectionChangedEvent event = new SelectionChangedEvent(this, fValidSelection);

			for (ISelectionChangedListener listener : fSelectionListeners) {
				listener.selectionChanged(event);
			}

			for (ISelectionChangedListener listener : fPostSelectionListeners) {
				listener.selectionChanged(event);
			}
		}
	}

	private class EditorSelectionChangedListener extends AbstractSelectionChangedListener {

		public void selectionChanged(SelectionChangedEvent event) {
			doHandleCursorPositionChanged();
		}

	}

	private void doHandleCursorPositionChanged() {
		IClassFileDocument disassemblerClassDocument = ((DisassemblerDocumentProvider) getDocumentProvider())
				.getClassFileDocument();
		if (disassemblerClassDocument != null) {
			/* set selection in the outline */
			final StyledText disassemblerText = getSourceViewer().getTextWidget();
			int selectedRange = disassemblerText.getSelectionRange().x;

			try {
				int line = disassemblerDocument.getLineOfOffset(selectedRange);
				ClassFile cf = (ClassFile) ((IClassFileEditorInput) getEditorInput()).getClassFile();

				if (disassemblerClassDocument.isLineInMethod(line - 2)) {
					IMethodSection method = disassemblerClassDocument.findMethodSection(line - 2);

					if (method != null) {
						IMethod m = ClassFileDocumentsUtils.findMethod(cf.getType(), method.getName(),
								method.getDescriptor());
						if (m != null) {
							editor.setSelection(m);
						}
					}
				} else if (disassemblerClassDocument.isLineInField(line - 2)) {
					IFieldSection field = disassemblerClassDocument.findFieldSection(line - 2);
					if (field != null) {
						IField f = cf.getType().getField(field.getName());
						if (f != null) {
							editor.setSelection(f);
						}
					}
				} else {
					editor.setSelection(cf.getType());
				}
			} catch (Exception e) {
				Logger.debug(e);
			}
		}

	}

	protected void editorContextMenuAboutToShow(IMenuManager menu) {
		super.editorContextMenuAboutToShow(menu);
		String text = (String) ReflectionUtils.invokeMethod(this, "getShowInMenuLabel"); //$NON-NLS-1$
		for (int i = 0; i < menu.getItems().length; i++) {
			if (menu.getItems()[i] instanceof MenuManager) {
				if (((MenuManager) menu.getItems()[i]).getMenuText().equals(text)) {
					menu.remove(menu.getItems()[i]);
				}
			}
		}

		menu.appendToGroup(ITextEditorActionConstants.GROUP_OPEN, getAction(SourceCodeAction.ID));
		menu.appendToGroup(ITextEditorActionConstants.GROUP_OPEN, getAction(ByteCodeAction.ID));
		menu.appendToGroup(ITextEditorActionConstants.GROUP_OPEN, getAction(DisassemblerAction.ID));

		menu.addMenuListener(new IMenuListener() {

			@Override
			public void menuAboutToShow(IMenuManager manager) {
				showMenu(manager);
			}
		});
	}

	private void showMenu(IMenuManager submenu) {
		for (Iterator<IContributionItem> iter = Arrays.asList(submenu.getItems()).iterator(); iter.hasNext();) {
			IContributionItem item = iter.next();
			if (item instanceof ActionContributionItem) {
				IAction action = ((ActionContributionItem) item).getAction();
				if (action instanceof IUpdate) {
					((IUpdate) action).update();
				}
			}
		}
	}

	@Override
	protected void createActions() {
		super.createActions();
		setAction(SourceCodeAction.ID, new SourceCodeAction());
		setAction(ByteCodeAction.ID, new ByteCodeAction());
		setAction(DisassemblerAction.ID, new DisassemblerAction());
	}

	@Override
	public String[] collectContextMenuPreferencePages() {
		return editor.collectContextMenuPreferencePages();
	}

	@Override
	public boolean isEditorInputModifiable() {
		return false;
	}

	public Control getControl() {
		return container;
	}

	public void setSelectionElement(ISourceReference selectedElement) {
		setSelectionElement(selectedElement, false);
	}

	public void setSelectionElement(ISourceReference selectedElement, boolean force) {
		final StyledText disassemblerText = getSourceViewer().getTextWidget();
		OutlineElement disassemblerRootElement = (OutlineElement) ((DisassemblerDocumentProvider) getDocumentProvider())
				.getClassFileOutlineElement();
		if (JavaDecompilerPlugin.getDefault().getSourceMode() == JavaDecompilerPlugin.DISASSEMBLER_MODE
				&& disassemblerText != null && !disassemblerText.isDisposed() && disassemblerRootElement != null) {
			if (!force) {
				if (UIUtil.requestFromDisassemblerSelection())
					return;

				if (!UIUtil.requestFromLinkToSelection())
					return;
			}

			if (selectedElement instanceof IMethod || selectedElement instanceof IField
					|| selectedElement instanceof BinaryType) {
				try {
					OutlineElement element = searchElement(disassemblerRootElement, selectedElement);
					if (element != null) {
						selectElement(disassemblerText, element);
					}
				} catch (Exception e) {
					Logger.debug(e);
				}
			}
		}
	}

	public void selectBytecodeLineAndReveal(StyledText disassemblerText, int bytecodeDocumentLine, String elementName,
			int elementType) {
		try {
			/* get line information */
			IRegion region = disassemblerDocument.getLineInformation(bytecodeDocumentLine + 2);
			int lineStartOffset = region.getOffset();
			int lineLenght = region.getLength();
			String lineString = disassemblerDocument.get(lineStartOffset, lineLenght);

			if (elementName == null) {
				disassemblerText.setSelection(lineStartOffset);
			}

			int elementIndex;
			int elementLength;
			switch (elementType) {
			case IJavaElement.CLASS_FILE:
				lineStartOffset = 0;
				String header = disassemblerDocument.get().substring(0, disassemblerDocument.get().indexOf("{")); //$NON-NLS-1$
				int startIndex = 0;
				if ((startIndex = header.indexOf("class")) != -1 //$NON-NLS-1$
						|| (startIndex = header.indexOf("enum")) != -1 //$NON-NLS-1$
						|| (startIndex = header.indexOf("interface")) != -1) //$NON-NLS-1$
				{
					elementIndex = startIndex + header.indexOf(elementName, startIndex) - startIndex;
				} else {
					elementIndex = header.indexOf(elementName);
				}
				break;
			case IJavaElement.FIELD:
				elementIndex = lineString.indexOf(" " + elementName + ";") + 1; //$NON-NLS-1$ //$NON-NLS-2$
				break;
			case IJavaElement.METHOD:
				elementIndex = lineString.indexOf(" " + elementName + "(") + 1; //$NON-NLS-1$ //$NON-NLS-2$
				break;
			default:
				elementIndex = 0;
				elementLength = 0;
			}

			/* name not found */
			if (elementIndex == 0) {
				elementLength = 0;
			} else {
				elementLength = elementName.length();
			}

			disassemblerText.setSelection(lineStartOffset + elementIndex,
					lineStartOffset + elementIndex + elementLength);
		} catch (BadLocationException e) {
			/* nothing to do */
		}
	}

	private void selectElement(StyledText disassemblerText, OutlineElement element) {
		int bytecodeDocumentLine = element.getBytecodeDocumentLine();
		if ((element instanceof IType)) {
			IType type = (IType) element;
			String name = type.getElementName();
			selectBytecodeLineAndReveal(disassemblerText, bytecodeDocumentLine, name, IJavaElement.CLASS_FILE);
		} else if ((element instanceof IOutlineElementField)) {
			IOutlineElementField field = (IOutlineElementField) element;
			String name = field.getFieldSection().getName();
			selectBytecodeLineAndReveal(disassemblerText, bytecodeDocumentLine, name, IJavaElement.FIELD);
		} else if ((element instanceof IMethod)) {
			IMethod method = (IMethod) element;
			String name = method.getElementName();
			selectBytecodeLineAndReveal(disassemblerText, bytecodeDocumentLine, name, IJavaElement.METHOD);
		} else {
			selectLineAndRevaluate(disassemblerText, bytecodeDocumentLine);
		}
	}

	public void selectLineAndRevaluate(StyledText disassemblerText, int bytecodeLine) {
		try {
			int lineStartOffset = disassemblerDocument.getLineOffset(bytecodeLine);
			disassemblerText.setSelection(lineStartOffset);

		} catch (BadLocationException e) {
			/* nothing to do */
		}
	}

	private OutlineElement searchElement(OutlineElement element, ISourceReference reference) throws CoreException {
		if (reference instanceof BinaryType) {
			if (element instanceof OutlineElementType) {
				OutlineElementType asmType = (OutlineElementType) element;
				BinaryType jdtType = (BinaryType) reference;
				String asmTypeName = asmType.getElementName();
				if (asmTypeName.indexOf('$') != -1) {
					asmTypeName = asmTypeName.substring(asmTypeName.lastIndexOf('$') + 1);
					if (asmTypeName.matches("\\d+")) //$NON-NLS-1$
					{
						asmTypeName = ""; //$NON-NLS-1$
					}
				}
				if (asmTypeName.equals(jdtType.getElementName())) {
					return element;
				}

			} else {
				IJavaElement[] children = element.getChildren();
				for (int i = 0; i < children.length; i++) {
					IJavaElement child = children[i];
					if (child instanceof OutlineElement) {
						OutlineElement result = searchElement((OutlineElement) child, reference);
						if (result != null) {
							return result;
						}
					}
				}
			}
		} else if (reference instanceof IField) {
			if (element instanceof OutlineElementField) {
				OutlineElementField asmField = (OutlineElementField) element;
				IField jdtField = (IField) reference;
				String asmFieldName = (String) ReflectionUtils.getFieldValue(asmField, "elementName"); //$NON-NLS-1$
				if (asmFieldName.indexOf('$') != -1) {
					asmFieldName = asmFieldName.substring(asmFieldName.lastIndexOf('$') + 1);
				}
				if (asmFieldName.equals(jdtField.getElementName())) {
					String jdtType = jdtField.getTypeSignature().replaceAll("<.*>", ""); //$NON-NLS-1$ //$NON-NLS-2$
					String asmType = asmField.getTypeSignature().replace('/', '.');
					if (jdtType.equals(asmType))
						return element;
				}
			} else {
				IJavaElement[] children = element.getChildren();
				for (int i = 0; i < children.length; i++) {
					IJavaElement child = children[i];
					if (child instanceof OutlineElement) {
						OutlineElement result = searchElement((OutlineElement) child, reference);
						if (result != null) {
							return result;
						}
					}
				}
			}
		} else if (reference instanceof IMethod) {
			if (element instanceof OutlineElementMethod) {
				OutlineElementMethod asmMethod = (OutlineElementMethod) element;
				IMethod jdtMethod = (IMethod) reference;

				String asmMethodName = asmMethod.getElementName();
				if (asmMethodName.indexOf('$') != -1) {
					asmMethodName = asmMethodName.substring(asmMethodName.lastIndexOf('$') + 1);
					if (asmMethodName.matches("\\d+")) //$NON-NLS-1$
					{
						asmMethodName = ""; //$NON-NLS-1$
					}
				}

				if (asmMethodName.equals(jdtMethod.getElementName())
						&& asmMethod.getSignature().equals(jdtMethod.getSignature())) {
					if (jdtMethod.getParent() instanceof BinaryType) {
						BinaryType jdtType = (BinaryType) jdtMethod.getParent();
						OutlineElementType asmType = (OutlineElementType) asmMethod.getDeclaringType();
						String asmTypeName = asmType.getElementName();
						if (asmTypeName.indexOf('$') != -1) {
							asmTypeName = asmTypeName.substring(asmTypeName.lastIndexOf('$') + 1);
							if (asmTypeName.matches("\\d+")) //$NON-NLS-1$
							{
								asmTypeName = ""; //$NON-NLS-1$
							}
						}
						if (asmTypeName.equals(jdtType.getElementName())) {
							return element;
						}
					} else
						return asmMethod;
				}
			} else {
				IJavaElement[] children = element.getChildren();
				for (int i = 0; i < children.length; i++) {
					IJavaElement child = children[i];
					if (child instanceof OutlineElement) {
						OutlineElement result = searchElement((OutlineElement) child, reference);
						if (result != null) {
							return result;
						}
					}
				}
			}
		}
		return null;
	}

	protected IConfigurationElement getConfigurationElement() {
		return (IConfigurationElement) ReflectionUtils.invokeMethod(editor, "getConfigurationElement"); //$NON-NLS-1$
	}

	private volatile boolean docChanged = false;
	private volatile boolean styleChanged = false;

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty().startsWith(JavaDecompilerPlugin.bytecodeMnemonicPreferencesPrefix)) {
			((DisassemblerConfiguration) getSourceViewerConfiguration()).adaptToPreferenceChange(event);
			if (styleChanged == false) {
				styleChanged = true;
				Display.getDefault().timerExec(10, new Runnable() {

					public void run() {
						getSourceViewer().invalidateTextPresentation();
						styleChanged = false;
					}
				});
			}
		} else if (event.getProperty().startsWith(JavaDecompilerPlugin.classFileAttributePreferencesPrefix)
				|| event.getProperty().equals(JavaDecompilerPlugin.BRANCH_TARGET_ADDRESS_RENDERING)) {
			if (docChanged == false) {
				docChanged = true;
				Display.getDefault().timerExec(10, new Runnable() {

					public void run() {
						updateDocument((DisassemblerDocumentProvider) getDocumentProvider(), getSourceViewer());
						if (editor.getSelectedElement() != null) {
							setSelectionElement(editor.getSelectedElement(), true);
						}
						docChanged = false;
					}
				});
			}
		}
	}

	@Override
	public boolean isDirty() {
		return false;
	}

	public boolean isEditable() {
		return false;
	}

	@Override
	public boolean isEditorInputReadOnly() {
		return true;
	}

	protected void handleCursorPositionChanged() {
		ReflectionUtils.invokeMethod(editor, "handleCursorPositionChanged"); //$NON-NLS-1$
	}
}
