package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.AttributeSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AbstractSourceCodeFinderTest {

    private static final String HELLO_FINDER = "Hello finder";

    private File testRoot;

    @Before
    public void setUp() {
        File targetDir = new File("target");
        assertTrue(targetDir.exists() || targetDir.mkdirs());

        testRoot = new File(targetDir, "abstract-source-finder-tests" + File.separator + System.nanoTime());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void tearDown() {
        deleteRecursively(testRoot);
    }

    @Test
    public void findGavFromFile_returnsParsedCoordinatesForSinglePomProperties() throws Exception {
        File jar = createZip("single.jar",
                Collections.singletonMap("META-INF/maven/org.example/demo/pom.properties",
                        "groupId=org.example\nartifactId=demo\nversion=1.2.3\n"));

        Optional<GAV> gav = new ExposedFinder().exposeFindGavFromFile(jar.getAbsolutePath());

        assertTrue(gav.isPresent());
        assertEquals("org.example", gav.get().getGroupId());
        assertEquals("demo", gav.get().getArtifactId());
        assertEquals("1.2.3", gav.get().getVersion());
    }

    @Test
    public void findGavFromFile_returnsEmptyForMergedJarWithMultiplePomProperties() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("META-INF/maven/org.example/demo/pom.properties",
                "groupId=org.example\nartifactId=demo\nversion=1.2.3\n");
        entries.put("META-INF/maven/org.other/other/pom.properties",
                "groupId=org.other\nartifactId=other\nversion=9.0.0\n");
        File jar = createZip("merged.jar", entries);

        Optional<GAV> gav = new ExposedFinder().exposeFindGavFromFile(jar.getAbsolutePath());

        assertFalse(gav.isPresent());
    }

    @Test
    public void getString_readsPlainTextFromFileUrl() throws Exception {
        File file = new File(testRoot, "plain.txt");
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("plain text");
        }

        String text = new ExposedFinder().exposeGetString(file.toURI().toURL());

        assertEquals("plain text", text);
    }

    @Test
    public void getString_readsGzippedContentFromFileUrl() throws Exception {
        File file = new File(testRoot, "compressed.bin");
        try (GZIPOutputStream out = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.write("compressed text".getBytes(StandardCharsets.UTF_8));
        }

        String text = new ExposedFinder().exposeGetString(file.toURI().toURL());

        assertEquals("compressed text", text);
    }

    @Test
    public void getString_returnsEmptyStringWhenUrlCannotBeRead() throws Exception {
        URL missing = new File(testRoot, "missing.txt").toURI().toURL();

        assertEquals("", new ExposedFinder().exposeGetString(missing));
    }

    @Test
    public void getText_returnsSelectedHtmlText() throws Exception {
        HTMLDocument doc = new HTMLDocument();
        doc.insertString(0, HELLO_FINDER, null);
        HTMLDocument.Iterator iterator = new HTMLDocument.Iterator() {
            @Override
            public AttributeSet getAttributes() {
                return null;
            }

            @Override
            public int getStartOffset() {
                return 0;
            }

            @Override
            public int getEndOffset() {
                return HELLO_FINDER.length();
            }

            @Override
            public HTML.Tag getTag() {
                return HTML.Tag.CONTENT;
            }

            @Override
            public boolean isValid() {
                return true;
            }

            @Override
            public void next() {
            }
        };

        String text = new ExposedFinder().exposeGetText(doc, iterator);

        assertEquals(HELLO_FINDER, text);
    }

    private File createZip(String name, Map<String, String> entries) throws Exception {
        File zip = new File(testRoot, name);
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setSize(bytes.length);
                zos.putNextEntry(zipEntry);
                zos.write(bytes);
                zos.closeEntry();
            }
        }
        return zip;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static final class ExposedFinder extends AbstractSourceCodeFinder {
        Optional<GAV> exposeFindGavFromFile(String binFile) throws Exception {
            return findGAVFromFile(binFile);
        }

        String exposeGetString(URL url) throws Exception {
            return getString(url);
        }

        String exposeGetText(HTMLDocument doc, HTMLDocument.Iterator iterator) throws Exception {
            return getText(doc, iterator);
        }

        @Override
        public void find(String binFile, String sha1, java.util.List<SourceFileResult> resultList) {
        }

        @Override
        public void cancel() {
        }
    }
}
