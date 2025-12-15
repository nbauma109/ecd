/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;

public class FileUtil {

    private FileUtil() {
    }

    public static void writeToFile(File file, String string) {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            String encoding = null;
            try {
                encoding = JavaDecompilerPlugin.getDefault().getExportEncoding();
            } catch (Exception e) {
            }
            if (encoding == null || encoding.trim().isEmpty()) {
                encoding = StandardCharsets.UTF_8.name();
            }
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), encoding))) {
                out.print(string);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeToBinarayFile(File file, InputStream source, boolean close) {
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(source);
            if (!file.exists()) {
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                file.createNewFile();
            }
            try (BufferedOutputStream fouts = new BufferedOutputStream(new FileOutputStream(file))) {

                byte[] b = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                int i = 0;
                while ((i = bis.read(b)) != -1) {
                    fouts.write(b, 0, i);
                }
                fouts.flush();
            }
            if (close) {
                bis.close();
            }
        } catch (IOException e) {
            Logger.getLogger(FileUtil.class.getName()).log(Level.WARNING, "Write binaray file failed.", //$NON-NLS-1$
                    e);
            if (close) {
                try {
                    bis.close();
                } catch (IOException f) {
                    Logger.getLogger(FileUtil.class.getName()).log(Level.WARNING, "Close input stream failed.", //$NON-NLS-1$
                            f);
                }
            }
        }
    }

    public static boolean copyFile(String src, String des) {
        try (FileInputStream fis = new FileInputStream(src)) {
            writeToBinarayFile(new File(des), fis, false);
        } catch (Exception e) {
            Logger.getLogger(FileUtil.class.getName()).log(Level.WARNING, "Copy file failed.", //$NON-NLS-1$
                    e);
        }
        return false;
    }

    /**
     * Recursively delete a directory.
     *
     * @param directory directory to delete
     * @throws IOException in case deletion is unsuccessful
     */
    private static void deleteDirectory(IProgressMonitor monitor, File directory, File base, int step)
            throws IOException {
        if (!directory.exists()) {
            return;
        }

        cleanDirectory(monitor, directory, base, step);
        if (!directory.delete()) {
            String message = "Unable to delete directory " + directory + "."; //$NON-NLS-1$ //$NON-NLS-2$
            throw new IOException(message);
        }
    }

    public static void deleteDirectory(IProgressMonitor monitor, File directory, int step) throws IOException {
        deleteDirectory(monitor, directory, directory, step);
    }

    private static void cleanDirectory(IProgressMonitor monitor, File directory, File base, int step)
            throws IOException {
        if (!directory.exists()) {
            String message = directory + " does not exist"; //$NON-NLS-1$
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            String message = directory + " is not a directory"; //$NON-NLS-1$
            throw new IllegalArgumentException(message);
        }

        IOException exception = null;

        boolean isPackage = false;

        File[] files = directory.listFiles();
        for (File file : files) {
            if (!isPackage && file.isFile()) {
                isPackage = true;
            }
            try {
                forceDelete(monitor, file, base, step);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }

        if (isPackage && (monitor != null)) {
            monitor.worked(step);
        }

        if (null != exception) {
            throw exception;
        }
    }

    private static void forceDelete(IProgressMonitor monitor, File file, File base, int step) throws IOException {
        if (file.isDirectory()) {
            deleteDirectory(monitor, file, base, step);
        } else {
            if (monitor != null) {
                String taskName = file.getAbsolutePath().substring(
                        base.getAbsolutePath().length() + Long.toString(System.currentTimeMillis()).length() + 2);
                monitor.subTask(taskName);
            }
            if (!file.exists()) {
                throw new FileNotFoundException("File does not exist: " + file); //$NON-NLS-1$
            }
            if (!file.delete()) {
                String message = "Unable to delete file: " + file; //$NON-NLS-1$
                throw new IOException(message);
            }
        }
    }

    public static void recursiveZip(IProgressMonitor monitor, ZipOutputStream zos, File file, final String path,
            FileFilter filter, int step) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles(filter);
            if (files != null) {
                for (File file2 : files) {
                    recursiveZip(monitor, zos, file2, (!path.isEmpty() ? (path + "/") : path) //$NON-NLS-1$
                            + file2.getName(), filter, step);
                }
            }
            if (monitor != null) {
                monitor.worked(step);
            }
        }
        if (file.isFile()) {
            if (monitor != null) {
                monitor.subTask(path);
            }
            byte[] bt = new byte[4096];
            ZipEntry ze = new ZipEntry(path);
            ze.setSize(file.length());
            zos.putNextEntry(ze);
            try (BufferedInputStream fis = new BufferedInputStream(new FileInputStream(file))) {
                int i = 0;
                while ((i = fis.read(bt)) != -1) {
                    zos.write(bt, 0, i);
                }
            }
        }
    }

    public static boolean isZipFile(String path) {
        if (path == null) {
            return false;
        }
        try {
            try (ZipFile zipFile = new ZipFile(path)) {
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }

    public static String getContent(File file) {
        return getContent(file, Charset.defaultCharset().name());
    }

    private static String getContent(File file, String encoding) {
        if (file == null || !file.exists()) {
            return null;
        }
        long fileLenLong = file.length();
        if (fileLenLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("File too large: " + file);
        }
        int fileLen = (int) fileLenLong;
        try (InputStream is = new BufferedInputStream(new FileInputStream(file));
                ByteArrayOutputStream out = new ByteArrayOutputStream(fileLen)) {
            byte[] tmp = new byte[4096];
            while (true) {
                int r = is.read(tmp);
                if (r == -1) {
                    break;
                }
                out.write(tmp, 0, r);
            }
            byte[] bytes = out.toByteArray();
            String content = new String(bytes, encoding);
            return content.trim();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void writeToFile(File file, String string, String encoding) {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), encoding))) {
                out.print(string);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deltree(File root) {
        if (root == null || !root.exists()) {
            return;
        }

        if (root.isFile()) {
            root.delete();
            return;
        }

        File[] children = root.listFiles();
        if (children != null) {
            for (File child : children) {
                deltree(child);
            }
        }

        root.delete();
    }
}
