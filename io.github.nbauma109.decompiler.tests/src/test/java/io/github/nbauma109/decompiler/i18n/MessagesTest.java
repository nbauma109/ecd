/*******************************************************************************
 * © 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MessagesTest {

    @Test
    public void getStringReturnsConfiguredValueForKnownKey() {
        assertEquals("D&ecompile@Ctrl+Alt+,",
                Messages.getString("JavaDecompilerActionBarContributor.Action.Decompile"));
    }

    @Test
    public void getStringWrapsMissingKeysWithExclamationMarks() {
        assertEquals("!missing.key!", Messages.getString("missing.key"));
    }

    @Test
    public void getFormattedStringFormatsArgumentsIntoMessageTemplate() {
        String text = Messages.getFormattedString("ExportSourceAction.Status.Error.DecompileFailed",
                new Object[] { "Demo.class" });

        assertTrue(text.contains("Demo.class"));
        assertEquals("Decompile class Demo.class failed.", text);
    }
}
