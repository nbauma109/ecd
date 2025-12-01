package org.sf.feeling.decompiler.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jface.preference.IPreferenceStore;
import org.junit.Test;
import org.sf.feeling.decompiler.JavaDecompilerPlugin;
import org.sf.feeling.decompiler.fernflower.FernFlowerSourceMapper;

public class FernFlowerSourceMapperTest {

    @Test
    public void testDecompile() throws IOException, URISyntaxException {
        IPreferenceStore prefs = JavaDecompilerPlugin.getDefault().getPreferenceStore();
        prefs.setValue(JavaDecompilerPlugin.PREF_DISPLAY_LINE_NUMBERS, true);
        prefs.setValue(JavaDecompilerPlugin.ALIGN, true);
        JavaDecompilerPlugin.getDefault().setDebugMode(true);

        URL resource = getClass().getResource("/test.jar");

        File jarFile = File.createTempFile("ecd-test-", ".jar");
        try (InputStream in = resource.openStream()) {
            Files.copy(in, jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        FileUtils.forceMkdir(new File("target/test"));

        JarClassExtractor.extract(jarFile.getAbsolutePath(), "test", "Test.class", true, "target/test");

        FernFlowerSourceMapper fernFlowerSourceMapper = new FernFlowerSourceMapper();
        File file = new File("target/test/Test.class");
        String expected = toString(getClass().getResource("/Test.txt"));
        String output = fernFlowerSourceMapper.decompile("FernFlower", file);
        assertEquals(expected, output);
    }

    private String toString(URL resource) throws IOException, URISyntaxException {
        return IOUtils.toString(resource.toURI(), UTF_8);
    }
}
