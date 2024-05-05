/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.source.attach.utils;

import java.io.File;
import java.io.StringWriter;

import org.sf.feeling.decompiler.util.FileUtil;
import org.sf.feeling.decompiler.util.Logger;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

public class SourceBindingUtil {

	private static final File sourceBindingJsonFile = new File(SourceConstants.SourceAttacherDir, "source.json"); //$NON-NLS-1$

	private static synchronized JsonObject loadSourceBindingJson() {
		if (sourceBindingJsonFile.exists()) {
			try {
				return Json.parse(FileUtil.getContent(sourceBindingJsonFile)).asObject();
			} catch (Exception e) {
				Logger.error("Load source attach binding configuration failed.", e); //$NON-NLS-1$
			}
		}
		return null;
	}

	private static synchronized void saveSourceBindingJson(JsonObject json) {
		try {
			StringWriter sw = new StringWriter();
			json.writeTo(sw);
			String jsonString = sw.toString();
			FileUtil.writeToFile(sourceBindingJsonFile, jsonString);
		} catch (Exception e) {
			Logger.error("Save source attach binding configuration failed.", e); //$NON-NLS-1$
		}
	}

	public static synchronized String[] getSourceFileBySha(String sha) {
		JsonObject config = loadSourceBindingJson();
		if (config == null) {
			return null;
		}

		JsonArray records = config.get("records").asArray(); //$NON-NLS-1$
		for (int i = 0; i < records.size(); i++) {
			JsonObject item = records.get(i).asObject();
			JsonArray shaArray = item.get("sha").asArray(); //$NON-NLS-1$
			;
			for (int j = 0; j < shaArray.size(); j++) {
				String shaString = shaArray.get(j).asString();
				if (sha != null && sha.equalsIgnoreCase(shaString)) {
					String source = item.getString("source", null); //$NON-NLS-1$
					String temp = item.getString("temp", null); //$NON-NLS-1$
					return new String[] { source, temp };
				}
			}
		}
		return null;
	}

	public static synchronized String[] getSourceFileByDownloadUrl(String downloadUrl) {
		JsonObject config = loadSourceBindingJson();
		if (config == null) {
			return null;
		}

		JsonArray records = config.get("records").asArray(); //$NON-NLS-1$
		for (int i = 0; i < records.size(); i++) {
			JsonObject item = records.get(i).asObject();
			if (item.get("downloadUrl").isNull()) //$NON-NLS-1$
				continue;
			String downloadUrlValue = item.getString("downloadUrl", null); //$NON-NLS-1$
			if (downloadUrl != null && downloadUrl.equals(downloadUrlValue)) {
				String source = item.getString("source", null); //$NON-NLS-1$
				String temp = item.getString("temp", null); //$NON-NLS-1$
				return new String[] { source, temp };
			}
		}
		return null;
	}

	public static synchronized void saveSourceBindingRecord(File sourceFile, String sha, String downloadUrl,
			File tempSourceFile) {
		if (sourceFile == null || tempSourceFile == null || !sourceFile.exists() || !tempSourceFile.exists())
			return;

		JsonObject config = loadSourceBindingJson();
		if (config == null) {
			config = createSourceBindingConfig(sourceFile, sha, downloadUrl, tempSourceFile);
			saveSourceBindingJson(config);
			return;
		}

		JsonArray records = config.get("records").asArray(); //$NON-NLS-1$
		String sourcePath = sourceFile.getAbsolutePath();

		boolean exist = false;
		for (int i = 0; i < records.size(); i++) {
			JsonObject item = records.get(i).asObject();
			String source = item.getString("source", null); //$NON-NLS-1$
			if (sourcePath.equals(source)) {
				modifySourceBindingRecord(item, sha, downloadUrl, tempSourceFile);
				exist = true;
			}
		}

		if (!exist) {
			JsonObject sourceBindingRecord = createSourceBindingRecord(sourceFile, sha, downloadUrl, tempSourceFile);
			records.add(sourceBindingRecord);
		}

		saveSourceBindingJson(config);
	}

	private static synchronized void modifySourceBindingRecord(JsonObject item, String sha, String downloadUrl,
			File tempSourceFile) {
		JsonArray shaArray = item.get("sha").asArray(); //$NON-NLS-1$
		boolean exist = false;
		for (int i = 0; i < shaArray.size(); i++) {
			String shaString = shaArray.get(i).asString();
			if (sha.equalsIgnoreCase(shaString)) {
				exist = true;
				break;
			}
		}
		if (!exist) {
			shaArray.add(sha);
		}
		if (downloadUrl != null) {
			item.set("downloadUrl", downloadUrl); //$NON-NLS-1$
		}
		if (tempSourceFile != null && tempSourceFile.exists()) {
			item.set("temp", tempSourceFile.getAbsolutePath()); //$NON-NLS-1$
		}
	}

	private static synchronized JsonObject createSourceBindingConfig(File sourceFile, String sha, String downloadUrl,
			File tempSourceFile) {
		JsonObject config = new JsonObject();
		config.set("version", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
		JsonArray records = new JsonArray();
		config.set("records", records); //$NON-NLS-1$
		JsonObject sourceBindingRecord = createSourceBindingRecord(sourceFile, sha, downloadUrl, tempSourceFile);
		records.add(sourceBindingRecord);
		return config;
	}

	private static synchronized JsonObject createSourceBindingRecord(File sourceFile, String sha, String downloadUrl,
			File tempSourceFile) {
		JsonObject sourceBindingRecord = new JsonObject();
		sourceBindingRecord.set("source", sourceFile.getAbsolutePath()); //$NON-NLS-1$
		sourceBindingRecord.set("downloadUrl", downloadUrl); //$NON-NLS-1$
		JsonArray shaArray = new JsonArray();
		shaArray.add(sha);
		sourceBindingRecord.set("sha", shaArray); //$NON-NLS-1$
		sourceBindingRecord.set("temp", tempSourceFile.getAbsolutePath()); //$NON-NLS-1$
		return sourceBindingRecord;
	}

	public static void checkSourceBindingConfig() {
		JsonObject config = loadSourceBindingJson();
		if (config == null) {
			return;
		}

		JsonArray records = config.get("records").asArray(); //$NON-NLS-1$
		for (int i = records.size() - 1; i >= 0; i--) {
			JsonObject item = records.get(i).asObject();
			String source = item.getString("source", null); //$NON-NLS-1$
			if (source == null || !new File(source).exists()) {
				records.remove(i);
			}
		}
		saveSourceBindingJson(config);
	}
}
