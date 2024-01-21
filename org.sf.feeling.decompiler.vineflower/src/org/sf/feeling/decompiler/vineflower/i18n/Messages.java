
package org.sf.feeling.decompiler.vineflower.i18n;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class Messages {

	private static final String BUNDLE_NAME = "org.sf.feeling.decompiler.vineflower.i18n.messages"; //$NON-NLS-1$

	private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

	private Messages() {
	}

	public static String getString(String key) {

		try {
			String result = RESOURCE_BUNDLE.getString(key);
			result = new String(result.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
			return result;
		} catch (MissingResourceException e) {
			return '!' + key + '!';
		}
	}

	/**
	 * Gets formatted translation for current local
	 * 
	 * @param key the key
	 * @return translated value string
	 */
	public static String getFormattedString(String key, Object[] arguments) {
		return MessageFormat.format(getString(key), arguments);
	}
}
