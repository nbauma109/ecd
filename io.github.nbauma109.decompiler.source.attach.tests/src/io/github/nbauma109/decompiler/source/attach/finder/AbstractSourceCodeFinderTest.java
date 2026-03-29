package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.nbauma109.decompiler.source.attach.utils.SourceBindingUtil;

import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.AttributeSet;

import org.apache.commons.io.FileUtils;
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
        FileUtils.deleteQuietly(testRoot);
    }

    @Test
    public void findGavFromFileReturnsParsedCoordinatesForSinglePomProperties() throws IOException {
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
    public void findGavFromFileReturnsEmptyForMergedJarWithMultiplePomProperties() throws IOException {
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
    public void getStringReadsPlainTextFromFileUrl() throws IOException {
        File file = new File(testRoot, "plain.txt");
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("plain text");
        }

        String text = new ExposedFinder().exposeGetString(file.toURI().toURL());

        assertEquals("plain text", text);
    }

    @Test
    public void getStringReadsGzippedContentFromFileUrl() throws IOException {
        File file = new File(testRoot, "compressed.bin");
        try (GZIPOutputStream out = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.write("compressed text".getBytes(StandardCharsets.UTF_8));
        }

        String text = new ExposedFinder().exposeGetString(file.toURI().toURL());

        assertEquals("compressed text", text);
    }

    @Test
    public void getStringReturnsEmptyStringWhenUrlCannotBeRead() throws IOException {
        URL missing = new File(testRoot, "missing.txt").toURI().toURL();

        assertEquals("", new ExposedFinder().exposeGetString(missing));
    }

    @Test
    public void getTextReturnsSelectedHtmlText() throws BadLocationException {
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
                // The test only needs a single iterator position, so advancing is intentionally a no-op.
            }
        };

        String text = new ExposedFinder().exposeGetText(doc, iterator);

        assertEquals(HELLO_FINDER, text);
    }

    @Test
    public void tryCachedSourcesReturnsFalseForEmptyMap() {
        ExposedFinder finder = new ExposedFinder();
        List<SourceFileResult> results = new ArrayList<>();
        boolean found = finder.exposeTryCachedSources("dummy.jar", Collections.emptyMap(), results);
        assertFalse(found);
        assertTrue(results.isEmpty());
    }

    @Test
    public void tryCachedSourcesReturnsFalseForUncachedUrl() {
        ExposedFinder finder = new ExposedFinder();
        List<SourceFileResult> results = new ArrayList<>();
        GAV gav = new GAV();
        gav.setGroupId("org.example");
        gav.setArtifactId("lib");
        gav.setVersion("1.0");
        // Use a unique URL that is guaranteed not to be in any binding file
        String uniqueUrl = "https://example.com/never-cached-" + System.nanoTime() + "-sources.jar";
        Map<GAV, String> sourcesUrls = Collections.singletonMap(gav, uniqueUrl);
        boolean found = finder.exposeTryCachedSources("dummy.jar", sourcesUrls, results);
        assertFalse(found);
        assertTrue(results.isEmpty());
    }

    @Test
    public void tryCachedSourcesReturnsTrueWhenCachedSourceExists() throws IOException {
        // Arrange: create a real source file and a matching temp file, then register them
        File sourceFile = new File(testRoot, "cached-source.jar");
        createMinimalZip(sourceFile);
        File tempFile = new File(testRoot, "cached-temp.jar");
        createMinimalZip(tempFile);

        String testUrl = "https://test.example.com/cached-" + System.nanoTime() + "-sources.jar";
        SourceBindingUtil.saveSourceBindingRecord(sourceFile, "cafebabe", testUrl, tempFile);

        GAV gav = new GAV();
        gav.setGroupId("test.example");
        gav.setArtifactId("cached");
        gav.setVersion("1.0");
        Map<GAV, String> sourcesUrls = Collections.singletonMap(gav, testUrl);

        ExposedFinder finder = new ExposedFinder();
        List<SourceFileResult> results = new ArrayList<>();
        boolean found = finder.exposeTryCachedSources(sourceFile.getAbsolutePath(), sourcesUrls, results);

        assertTrue(found);
        assertEquals(1, results.size());
    }

    @Test
    public void tryCachedSourcesReturnsFalseWhenCachedSourceFileIsMissing() throws IOException {
        // Arrange: register a binding but do NOT create the source file
        File missingSourceFile = new File(testRoot, "missing-source.jar");
        File tempFileForReg = new File(testRoot, "temp-for-reg.jar");
        createMinimalZip(tempFileForReg);
        // We need a "source" file to pass saveSourceBindingRecord's exists() check — create then delete
        createMinimalZip(missingSourceFile);
        String testUrl = "https://test.example.com/missing-" + System.nanoTime() + "-sources.jar";
        SourceBindingUtil.saveSourceBindingRecord(missingSourceFile, "deadbeef", testUrl, tempFileForReg);
        assertTrue("Expected source file to be deleted for test", missingSourceFile.delete()); // now the cached path no longer exists

        GAV gav = new GAV();
        gav.setGroupId("test.example");
        gav.setArtifactId("missing");
        gav.setVersion("1.0");
        Map<GAV, String> sourcesUrls = Collections.singletonMap(gav, testUrl);

        ExposedFinder finder = new ExposedFinder();
        List<SourceFileResult> results = new ArrayList<>();
        boolean found = finder.exposeTryCachedSources("dummy.jar", sourcesUrls, results);

        assertFalse(found);
        assertTrue(results.isEmpty());
    }

    private void createMinimalZip(File dest) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(dest)))) {
            ZipEntry entry = new ZipEntry("placeholder.txt");
            zos.putNextEntry(entry);
            zos.write("placeholder".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private File createZip(String name, Map<String, String> entries) throws IOException {
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
    private static final class ExposedFinder extends AbstractSourceCodeFinder {
        Optional<GAV> exposeFindGavFromFile(String binFile) throws IOException {
            return findGAVFromFile(binFile);
        }

        String exposeGetString(URL url) {
            return getString(url);
        }

        String exposeGetText(HTMLDocument doc, HTMLDocument.Iterator iterator) throws BadLocationException {
            return getText(doc, iterator);
        }

        boolean exposeTryCachedSources(String binFile, Map<GAV, String> sourcesUrls, List<SourceFileResult> results) {
            return tryCachedSources(binFile, sourcesUrls, results);
        }

        @Override
        public void find(String binFile, String sha1, List<SourceFileResult> resultList) {
            // These tests exercise inherited helper methods directly, not the finder contract.
        }

        @Override
        public void cancel() {
            // This test double never starts asynchronous work, so there is nothing to cancel.
        }
    }
}
