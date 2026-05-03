/*******************************************************************************
 * (C) 2026 Claude (@Claude)
 * (C) 2026 Copilot (@Copilot)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.i18n;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ResourceBundleMessagesTest {

    private static final String BUNDLE_NAME = "io.github.nbauma109.decompiler.i18n.messages"; //$NON-NLS-1$
    private static final String DECOMPILE_ACTION_KEY = "JavaDecompilerActionBarContributor.Action.Decompile"; //$NON-NLS-1$

    @Test
    public void getStringTwoParamReturnsKnownValue() {
        assertEquals("D&ecompile@Ctrl+Alt+,",
                ResourceBundleMessages.getString(BUNDLE_NAME, DECOMPILE_ACTION_KEY));
    }

    @Test
    public void getStringTwoParamWrapsMissingKeyWithExclamationMarks() {
        assertEquals("!no.such.key!", ResourceBundleMessages.getString(BUNDLE_NAME, "no.such.key")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void getFormattedStringThreeParamFormatsArguments() {
        String result = ResourceBundleMessages.getFormattedString(BUNDLE_NAME,
                "ExportSourceAction.Status.Error.DecompileFailed", //$NON-NLS-1$
                new Object[] { "Foo.class" }); //$NON-NLS-1$
        assertEquals("Decompile class Foo.class failed.", result);
    }

    @Test
    public void getStringFourParamWithScopeReturnsKnownValue() {
        assertEquals("D&ecompile@Ctrl+Alt+,",
                ResourceBundleMessages.getString(BUNDLE_NAME, DECOMPILE_ACTION_KEY,
                        ResourceBundleMessagesTest.class));
    }

    @Test
    public void cachedBundleLookupReturnsSameValueOnRepeatedCalls() {
        String first = ResourceBundleMessages.getString(BUNDLE_NAME, DECOMPILE_ACTION_KEY);
        String second = ResourceBundleMessages.getString(BUNDLE_NAME, DECOMPILE_ACTION_KEY);
        assertEquals(first, second);
    }
}
