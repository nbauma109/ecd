/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.finder;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.text.EditorKit;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.sf.feeling.decompiler.source.attach.utils.SourceAttachUtil;
import org.sf.feeling.decompiler.source.attach.utils.SourceBindingUtil;
import org.sf.feeling.decompiler.source.attach.utils.SourceConstants;
import org.sf.feeling.decompiler.source.attach.utils.UrlDownloader;
import org.sf.feeling.decompiler.util.Logger;

/**
 * Expects a file name like
 * <code>org.eclipse.ant.core_3.5.100.v20180512-1141.jar</code>
 */
public class EclipsePluginSourceByUrlPatternFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

	// https://www.mmnt.ru/int/get?st={0}
	// https://www.searchftps.com/indexer/search.aspx?__LASTFOCUS=&__EVENTTARGET=ctl00%24MainContent%24SearchButton&__EVENTARGUMENT=&ctl00%24MainContent%24SearchKeywordTextBox={0}&ctl00%24MainContent%24SearchTypeDropDownList=And&ctl00%24MainContent%24SearchOrderDropDownList=DateDesc&ctl00%24MainContent%24SearchFilterDropDownList=NoFilter
	private final String urlPattern;

	public EclipsePluginSourceByUrlPatternFinder(String urlPattern) {
		this.urlPattern = urlPattern;
	}

	@Override
	public String toString() {
		return this.getClass() + "; urlPattern=" + urlPattern; //$NON-NLS-1$
	}

	@Override
	public void cancel() {
	}

	@Override
	public void find(String binFile, List<SourceFileResult> results) {
		File bin = new File(binFile);
		Object fileResult = null;
		try {
			String fileName = bin.getName();
			int position = fileName.lastIndexOf('_');
			if (position != -1) {
				String baseName = fileName.substring(0, position);
				String version = fileName.substring(position + 1);
				String sourceFileName = baseName + ".source_" + version; //$NON-NLS-1$
				fileResult = findFile(sourceFileName, bin);
			}
		} catch (Throwable e) {
			Logger.debug(e);
		}
		if (fileResult == null) {
			return;
		}

		if (fileResult instanceof SourceFileResult) {
			results.add((SourceFileResult) fileResult);
		} else if (fileResult instanceof String[]) {
			String[] result = (String[]) fileResult;
			if (result[0] != null) {
				String name = result[0].substring(result[0].lastIndexOf('/') + 1);
				SourceFileResult object = new SourceFileResult(this, binFile, result[1], name, 50);
				Logger.debug(toString() + " FOUND: " + object, null); //$NON-NLS-1$
				results.add(object);
			}
		} else {
			throw new RuntimeException("Unexpected result: " + fileResult);
		}

	}

	private Object findFile(String fileName, File bin) throws Exception {
		String file = null;
		String url = null;

		List<String> links = searchFileLinksByName(fileName);
		String link;
		for (Iterator<String> it = links.iterator(); it.hasNext();) {
			link = it.next();
			boolean keep = false;
			if (link.endsWith("/" + fileName)) //$NON-NLS-1$
			{
				keep = true;
			}
			if (!keep) {
				it.remove();
			}
		}

		for (String url1 : links) {
			String[] sourceFiles = SourceBindingUtil.getSourceFileByDownloadUrl(url1);
			if (sourceFiles != null && sourceFiles[0] != null && new File(sourceFiles[0]).exists()) {
				File sourceFile = new File(sourceFiles[0]);
				File tempFile = new File(sourceFiles[1]);
				SourceFileResult result = new SourceFileResult(this, bin.getAbsolutePath(), sourceFile, tempFile, 100);
				return result;
			}
		}

		for (String url1 : links) {
			String tmpFile = new UrlDownloader().download(url1);

			if (tmpFile != null && new File(tmpFile).exists()
					&& SourceAttachUtil.isSourceCodeFor(tmpFile, bin.getAbsolutePath())) {
				setDownloadUrl(url1);
				file = tmpFile;
				url = url1;
				break;
			}
		}
		return new String[] { url, file };
	}

	private List<String> searchFileLinksByName(String fileName) throws Exception {
		List<String> result = new ArrayList<>();
		URL baseUrl = new URL(this.urlPattern.replace("{0}", URLEncoder.encode(fileName, "UTF-8"))); //$NON-NLS-1$ //$NON-NLS-2$
		String html = getHtmlFromUrl(baseUrl, fileName);

		List<String> links = new ArrayList<>();

		EditorKit kit = new HTMLEditorKit();
		HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
		doc.putProperty("IgnoreCharsetDirective", true); //$NON-NLS-1$
		Reader reader = new StringReader(html);
		kit.read(reader, doc, 0);

		HTMLDocument.Iterator it = doc.getIterator(HTML.Tag.A);
		while (it.isValid()) {
			addLink(baseUrl, fileName, doc, it, links);
			it.next();
		}

		result.addAll(links);
		return result;
	}

	protected String getHtmlFromUrl(URL baseUrl, String fileName) throws Exception {
		return getString(baseUrl);
	}

	protected void addLink(URL baseUrl, String fileName, HTMLDocument doc, HTMLDocument.Iterator aElement,
			List<String> links) throws Exception {
		SimpleAttributeSet s = (SimpleAttributeSet) aElement.getAttributes();

		String href = (String) s.getAttribute(HTML.Attribute.HREF);
		if ((href != null) && (!href.startsWith("javascript:")) //$NON-NLS-1$
				&& (!href.startsWith("news:")) //$NON-NLS-1$
				&& (href.indexOf('#') == -1)) {
			String absHref = new URL(new URL(baseUrl.toString()), href).toString();
			links.add(absHref);
		}
	}

	public static void main(String[] args) {
		EclipsePluginSourceByUrlPatternFinder finder;
		List<SourceFileResult> results = new ArrayList<>();
		finder = new EclipsePluginSourceByUrlPatternFinder("https://www.mmnt.ru/int/get?st={0}"); //$NON-NLS-1$
		finder.find(new File(SourceConstants.USER_M2_REPO_DIR, "ant/ant/1.6.5/ant-1.6.5.jar"), results); //$NON-NLS-1$
		System.out.println(results);
	}
}