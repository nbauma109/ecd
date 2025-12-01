/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.editor;

import java.util.Enumeration;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.BufferManager;
import org.eclipse.jdt.internal.core.ClassFile;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.swt.widgets.Display;
import org.sf.feeling.decompiler.util.Logger;
import org.sf.feeling.decompiler.util.UIUtil;

/**
 * This class is a hack that replaces JDT <code>BufferManager</code> in order to
 * make <code>addBuffer()</code> and <code>removeBuffer()</code> accessible.
 */
public class JavaDecompilerBufferManager extends BufferManager {

	public static void closeDecompilerBuffers(boolean all) {
		BufferManager manager = BufferManager.getDefaultBufferManager();
		if (manager instanceof JavaDecompilerBufferManager) {
			Enumeration enumeration = manager.getOpenBuffers();
			while (enumeration.hasMoreElements()) {
				IBuffer buffer = (IBuffer) enumeration.nextElement();
				((JavaDecompilerBufferManager) manager).removeBuffer(buffer);
			}
		}
	}

	public JavaDecompilerBufferManager(BufferManager manager) {
		synchronized (BufferManager.class) {
			Enumeration enumeration = manager.getOpenBuffers();
			while (enumeration.hasMoreElements()) {
				IBuffer buffer = (IBuffer) enumeration.nextElement();
				addBuffer(buffer);
			}
			BufferManager.DEFAULT_BUFFER_MANAGER = this;
		}
	}

	@Override
	public void addBuffer(final IBuffer buffer) {
		if (buffer == null || buffer.getContents() == null) {
			if (buffer != null) {
				delayAddBuffer(buffer);
			}
			return;
		}
		super.addBuffer(buffer);
	}

	private void delayAddBuffer(final IBuffer buffer) {
		new Thread() {

			@Override
			public void run() {
				if (buffer.getContents() != null) {
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						Logger.debug(e);
					}
					addBuffer(buffer);
				}
			}
		}.start();
	}

	@Override
	public void removeBuffer(IBuffer buffer) {
		super.removeBuffer(buffer);
	}

	@Override
	public IBuffer getBuffer(final IOpenable owner) {
		IBuffer buffer = super.getBuffer(owner);
		final IBuffer[] buffers = new IBuffer[] { buffer };

		if (UIUtil.requestFromJavadocHover()) {

			if (buffers[0] == null || buffers[0].getContents() == null) {
				buffers[0] = null;

				Display.getDefault().asyncExec(new Runnable() {

					@Override
					public void run() {
						JavaDecompilerClassFileEditor editor = UIUtil.getActiveEditor();
						if (editor != null && editor.getEditorInput() instanceof IClassFileEditorInput) {
							IClassFile input = ((IClassFileEditorInput) editor.getEditorInput()).getClassFile();
							if (owner.equals(input)) {
								buffers[0] = editor.getClassBuffer();
								JavaDecompilerBufferManager.this.addBuffer(buffers[0]);
							}
						}
					}
				});
			}
		} else if (UIUtil.requestCreateBuffer()) {

			JavaDecompilerClassFileEditor editor = UIUtil.getActiveEditor();
			if (editor != null && editor.getEditorInput() instanceof IClassFileEditorInput) {
				IClassFile input = ((IClassFileEditorInput) editor.getEditorInput()).getClassFile();
				if (owner.equals(input)) {
					String content = editor.getDocumentProvider().getDocument(editor.getEditorInput()).get();
					if (buffers[0] != null) {
						if (!content.equals(buffers[0].getContents())) {
                            buffers[0].setContents(content);
                        }
					} else {
						ClassFile cf = (ClassFile) input;
						IBuffer classBuffer = BufferManager.createBuffer(cf);
						classBuffer.setContents(content);
						editor.getBufferManager().addBuffer(classBuffer);

						try {
							ClassFileSourceMap.updateSource(editor.getBufferManager(), cf, content.toCharArray());
						} catch (JavaModelException e) {
							Logger.debug(e);
						}

						buffers[0] = classBuffer;
						JavaDecompilerBufferManager.this.addBuffer(buffers[0]);
					}
				}
			}
		}

		return buffers[0];
	}
}
