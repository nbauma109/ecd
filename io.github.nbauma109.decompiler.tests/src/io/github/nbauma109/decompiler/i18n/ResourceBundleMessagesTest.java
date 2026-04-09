package io.github.nbauma109.decompiler.i18n;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ResourceBundleMessagesTest {

    private static final String BUNDLE_NAME = "io.github.nbauma109.decompiler.i18n.messages"; //$NON-NLS-1$

    @Test
    public void getStringTwoParamReturnsKnownValue() {
        assertEquals("D&ecompile@Ctrl+Alt+,",
                ResourceBundleMessages.getString(BUNDLE_NAME,
                        "JavaDecompilerActionBarContributor.Action.Decompile")); //$NON-NLS-1$
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
                ResourceBundleMessages.getString(BUNDLE_NAME,
                        "JavaDecompilerActionBarContributor.Action.Decompile", //$NON-NLS-1$
                        ResourceBundleMessagesTest.class));
    }

    @Test
    public void cachedBundleLookupReturnsSameValueOnRepeatedCalls() {
        String first = ResourceBundleMessages.getString(BUNDLE_NAME,
                "JavaDecompilerActionBarContributor.Action.Decompile"); //$NON-NLS-1$
        String second = ResourceBundleMessages.getString(BUNDLE_NAME,
                "JavaDecompilerActionBarContributor.Action.Decompile"); //$NON-NLS-1$
        assertEquals(first, second);
    }
}
