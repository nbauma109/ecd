package io.github.nbauma109.decompiler.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MessagesTest {

    @Test
    public void getString_returnsConfiguredValueForKnownKey() {
        assertEquals("D&ecompile@Ctrl+Alt+,",
                Messages.getString("JavaDecompilerActionBarContributor.Action.Decompile"));
    }

    @Test
    public void getString_wrapsMissingKeysWithExclamationMarks() {
        assertEquals("!missing.key!", Messages.getString("missing.key"));
    }

    @Test
    public void getFormattedString_formatsArgumentsIntoMessageTemplate() {
        String text = Messages.getFormattedString("ExportSourceAction.Status.Error.DecompileFailed",
                new Object[] { "Demo.class" });

        assertTrue(text.contains("Demo.class"));
        assertEquals("Decompile class Demo.class failed.", text);
    }
}
