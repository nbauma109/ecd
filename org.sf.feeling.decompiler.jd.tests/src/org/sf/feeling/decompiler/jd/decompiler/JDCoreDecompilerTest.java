package org.sf.feeling.decompiler.jd.decompiler;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.eclipse.jface.preference.IPreferenceStore;
import org.junit.Test;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;

public class JDCoreDecompilerTest {

    @Test
    public void testDecompileFromArchive() throws IOException, URISyntaxException {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        prefs.setValue(JavaDecompilerPlugin.PREF_DISPLAY_LINE_NUMBERS, true);
        prefs.setValue(JavaDecompilerPlugin.ALIGN, true);
        JavaDecompilerPlugin.getDefault().setDebugMode(true);

        JDCoreSourceMapper sourceMapper = new JDCoreSourceMapper();
        JDCoreDecompiler decompiler = new JDCoreDecompiler(sourceMapper);
        decompiler.decompileFromArchive("resources/test.jar", "test", "Test.class");
        String output = decompiler.getSource();
        String expected = toString(getClass().getResource("/Test.txt"));
        assertEqualsIgnoreEOL(expected, output);
    }

    private void assertEqualsIgnoreEOL(String expected, String actual) {
        assertEquals(expected.replaceAll("\s*\r?\n", "\n"), actual.replaceAll("\s*\r?\n", "\n"));
    }

    private String toString(URL resource) throws IOException, URISyntaxException {
        return IOUtils.toString(resource.toURI(), UTF_8);
    }
}
