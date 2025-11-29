/*******************************************************************************
 * Copyright (c) 2017 Chen Chao and other ECD project contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.sf.feeling.decompiler.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.sf.feeling.decompiler.JavaDecompilerPlugin;

public class ReflectionUtils {

	private ReflectionUtils() {
	}

	public static Method getDeclaredMethod(Object object, String methodName, Class[] parameterTypes) {
		if (object == null || methodName == null)
			return null;

		for (Class clazz = object.getClass(); clazz != Object.class; clazz = clazz.getSuperclass()) {
			try {
				return clazz.getDeclaredMethod(methodName, parameterTypes);
			} catch (Exception e) {

			}
		}

		return null;
	}

	public static Object invokeMethod(Object object, String methodName, Class[] parameterTypes, Object[] parameters) {
		if (object == null || methodName == null)
			return null;

		Method method = getDeclaredMethod(object, methodName, parameterTypes);
		return invokeMethod(method, object, parameters);
	}

	public static Object invokeMethod(Method method, Object object, Object[] parameters) {
		try {
			if (null != method) {
				method.setAccessible(true);
				return method.invoke(object, parameters);
			}
		} catch (Exception e) {
			JavaDecompilerPlugin.logError(e, ""); //$NON-NLS-1$
		}

		return null;
	}

	public static Object invokeMethod(Object object, String methodName) {
		if (object == null || methodName == null)
			return null;

		Method method = getDeclaredMethod(object, methodName, new Class[0]);
		try {
			if (null != method) {
				method.setAccessible(true);
				return method.invoke(object, new Object[0]);
			}
		} catch (Exception e) {
			JavaDecompilerPlugin.logError(e, ""); //$NON-NLS-1$
		}

		return null;
	}

	private static Field getDeclaredField(Object object, String fieldName) {
		if (object == null || fieldName == null)
			return null;

		Class clazz = object.getClass();

		for (; clazz != Object.class; clazz = clazz.getSuperclass()) {
			try {
				return clazz.getDeclaredField(fieldName);
			} catch (Exception e) {

			}
		}

		return null;
	}

	public static void setFieldValue(Object object, String fieldName, Object value) {
		if (object == null || fieldName == null)
			return;

		Field field = getDeclaredField(object, fieldName);

		try {
			if (field != null) {
				field.setAccessible(true);
				field.set(object, value);
			}
		} catch (Exception e) {
			JavaDecompilerPlugin.logError(e, ""); //$NON-NLS-1$
		}

	}

	public static Object getFieldValue(Object object, String fieldName) {
		if (object == null || fieldName == null)
			return null;

		Field field = getDeclaredField(object, fieldName);

		try {
			if (field != null) {
				field.setAccessible(true);
				return field.get(object);
			}

		} catch (Exception e) {
			JavaDecompilerPlugin.logError(e, ""); //$NON-NLS-1$
		}

		return null;
	}

	public static Object invokeMethod(Object object, String methodName, Class clazz, Object value) {
		if (object == null || methodName == null)
			return null;

		Method method = getDeclaredMethod(object, methodName, new Class[] { clazz });
		try {
			if (null != method) {
				method.setAccessible(true);
				return method.invoke(object, new Object[] { value });
			}
		} catch (Exception e) {
			JavaDecompilerPlugin.logError(e, ""); //$NON-NLS-1$
		}

		return null;
	}
}