/*******************************************************************************
 * © 2026 Nicolas Baumann (@nbauma109)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileWriter;
import java.net.URL;
import java.nio.file.Files;
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
import io.github.nbauma109.decompiler.util.HashUtils;

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
    private static final String DUMMY_JAR = "dummy.jar"; //$NON-NLS-1$
    private static final String SOURCES_JAR_SUFFIX = "-sources.jar"; //$NON-NLS-1$
    private static final String POM_PROPERTIES_PATH = "META-INF/maven/org.example/demo/pom.properties"; //$NON-NLS-1$

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
                Collections.singletonMap(POM_PROPERTIES_PATH,
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
        entries.put(POM_PROPERTIES_PATH,
                "groupId=org.example\nartifactId=demo\nversion=1.2.3\n");
        entries.put("META-INF/maven/org.other/other/pom.properties",
                "groupId=org.other\nartifactId=other\nversion=9.0.0\n");
        File jar = createZip("merged.jar", entries);

        Optional<GAV> gav = new ExposedFinder().exposeFindGavFromFile(jar.getAbsolutePath());

        assertFalse(gav.isPresent());
    }

    @Test
    public void findGavFromFileReturnsEmptyForJarWithNoPomPropertiesEntries() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("com/example/Demo.class", "bytecode");
        entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n");
        File jar = createZip("no-pom.jar", entries);

        Optional<GAV> gav = new ExposedFinder().exposeFindGavFromFile(jar.getAbsolutePath());

        assertFalse(gav.isPresent());
    }

    @Test
    public void findGavFromFileReturnsEmptyWhenPomPropertiesLackRequiredFields() throws IOException {
        // pom.properties is missing 'groupId' – the GAV should not be added to the result set
        Map<String, String> entries = Collections.singletonMap(
                POM_PROPERTIES_PATH,
                "artifactId=demo\nversion=1.0\n"); // groupId intentionally absent
        File jar = createZip("missing-group.jar", entries);

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
        boolean found = finder.exposeTryCachedSources(DUMMY_JAR, Collections.emptyMap(), results);
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
        String uniqueUrl = "https://example.com/never-cached-" + System.nanoTime() + SOURCES_JAR_SUFFIX;
        Map<GAV, String> sourcesUrls = Collections.singletonMap(gav, uniqueUrl);
        boolean found = finder.exposeTryCachedSources(DUMMY_JAR, sourcesUrls, results);
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

        String testUrl = "https://test.example.com/cached-" + System.nanoTime() + SOURCES_JAR_SUFFIX;
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
        String testUrl = "https://test.example.com/missing-" + System.nanoTime() + SOURCES_JAR_SUFFIX;
        SourceBindingUtil.saveSourceBindingRecord(missingSourceFile, "deadbeef", testUrl, tempFileForReg);
        try {
            Files.delete(missingSourceFile.toPath()); // now the cached path no longer exists
        } catch (IOException e) {
            fail(e.getMessage());
        }

        GAV gav = new GAV();
        gav.setGroupId("test.example");
        gav.setArtifactId("missing");
        gav.setVersion("1.0");
        Map<GAV, String> sourcesUrls = Collections.singletonMap(gav, testUrl);

        ExposedFinder finder = new ExposedFinder();
        List<SourceFileResult> results = new ArrayList<>();
        boolean found = finder.exposeTryCachedSources(DUMMY_JAR, sourcesUrls, results);

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

    // -----------------------------------------------------------------------
    // persistCachedSourceInMavenRepo tests
    // -----------------------------------------------------------------------

    @Test
    public void persistCachedSourceInMavenRepoCopiesSourceToMavenRepo() throws IOException {
        File binJar = createZip("bin-persist-copy.jar",
                Collections.singletonMap("pkg/Demo.class", "bytecode")); //$NON-NLS-1$ //$NON-NLS-2$
        File srcJar = createZip("src-persist-copy.jar",
                Collections.singletonMap("pkg/Demo.java", "class Demo {}")); //$NON-NLS-1$ //$NON-NLS-2$

        GAV gav = new GAV();
        gav.setGroupId("io.test.copilot.persist");
        gav.setArtifactId("persist-copy-test");
        gav.setVersion("9.9.9");
        String sourceUrl = "https://test.example.com/persist-copy-test-9.9.9-sources.jar"; //$NON-NLS-1$

        ExposedFinder finder = new ExposedFinder();
        File result = finder.exposePersistCachedSourceInMavenRepo(gav, sourceUrl, srcJar, binJar.getAbsolutePath());

        assertNotNull(result);
        assertTrue(result.exists());
        FileUtils.deleteQuietly(result.getParentFile());
    }

    @Test
    public void persistCachedSourceInMavenRepoReturnsNullForNonSourceUrl() throws IOException {
        File binJar = createZip("bin-non-source.jar",
                Collections.singletonMap("pkg/Demo.class", "bytecode")); //$NON-NLS-1$ //$NON-NLS-2$
        File srcJar = createZip("src-non-source.jar",
                Collections.singletonMap("pkg/Demo.java", "class Demo {}")); //$NON-NLS-1$ //$NON-NLS-2$

        GAV gav = new GAV();
        gav.setGroupId("io.test.copilot.nonsrc");
        gav.setArtifactId("non-source-url-test");
        gav.setVersion("9.9.9");
        String nonSourceUrl = "https://test.example.com/non-source-url-test-9.9.9.jar"; // NOT -sources.jar //$NON-NLS-1$

        File result = new ExposedFinder().exposePersistCachedSourceInMavenRepo(gav, nonSourceUrl, srcJar,
                binJar.getAbsolutePath());

        assertNull(result);
    }

    @Test
    public void persistCachedSourceInMavenRepoReturnsMavenFileWhenValidAlreadyExists() throws IOException {
        File binJar = createZip("bin-valid-exists.jar",
                Collections.singletonMap("pkg/Demo.class", "bytecode")); //$NON-NLS-1$ //$NON-NLS-2$
        File srcJar = createZip("src-valid-exists.jar",
                Collections.singletonMap("pkg/Demo.java", "class Demo {}")); //$NON-NLS-1$ //$NON-NLS-2$

        GAV gav = new GAV();
        gav.setGroupId("io.test.copilot.validexists");
        gav.setArtifactId("valid-exists-test");
        gav.setVersion("9.9.9");
        String sourceUrl = "https://test.example.com/valid-exists-test-9.9.9-sources.jar"; //$NON-NLS-1$

        ExposedFinder finder = new ExposedFinder();
        // First call creates the maven repo file
        File result1 = finder.exposePersistCachedSourceInMavenRepo(gav, sourceUrl, srcJar, binJar.getAbsolutePath());
        assertNotNull(result1);

        // Second call: file already exists and is valid → returned directly
        File result2 = finder.exposePersistCachedSourceInMavenRepo(gav, sourceUrl, srcJar, binJar.getAbsolutePath());
        assertNotNull(result2);
        assertEquals(result1.getAbsolutePath(), result2.getAbsolutePath());

        FileUtils.deleteQuietly(result1.getParentFile());
    }

    @Test
    public void persistCachedSourceInMavenRepoReturnsNullWhenSourceDoesNotMatchBinary() throws IOException {
        File binJar = createZip("bin-mismatch-persist.jar",
                Collections.singletonMap("pkg/Foo.class", "bytecode")); //$NON-NLS-1$ //$NON-NLS-2$
        File srcJar = createZip("src-mismatch-persist.jar",
                Collections.singletonMap("pkg/Bar.java", "class Bar {}")); //$NON-NLS-1$ //$NON-NLS-2$

        GAV gav = new GAV();
        gav.setGroupId("io.test.copilot.mismatch");
        gav.setArtifactId("mismatch-persist-test");
        gav.setVersion("9.9.9");
        String sourceUrl = "https://test.example.com/mismatch-persist-test-9.9.9-sources.jar"; //$NON-NLS-1$

        File result = new ExposedFinder().exposePersistCachedSourceInMavenRepo(gav, sourceUrl, srcJar,
                binJar.getAbsolutePath());

        assertNull(result);
    }

    @Test
    public void tryCachedSourcesWithPersistInMavenRepoReturnsTrueAndCopiesSource() throws IOException {
        File binJar = createZip("bin-persist-cached.jar",
                Collections.singletonMap("pkg/Demo.class", "bytecode")); //$NON-NLS-1$ //$NON-NLS-2$
        File srcJar = createZip("src-persist-cached.jar",
                Collections.singletonMap("pkg/Demo.java", "class Demo {}")); //$NON-NLS-1$ //$NON-NLS-2$
        File tmpJar = createZip("tmp-persist-cached.jar",
                Collections.singletonMap("pkg/Demo.java", "class Demo {}")); //$NON-NLS-1$ //$NON-NLS-2$

        String testUrl = "https://test.persist.example.com/persist-cached-9.9.9-sources.jar"; //$NON-NLS-1$
        SourceBindingUtil.saveSourceBindingRecord(srcJar, HashUtils.sha1Hash(srcJar), testUrl, tmpJar);

        GAV gav = new GAV();
        gav.setGroupId("io.test.copilot.persistcached");
        gav.setArtifactId("persist-cached");
        gav.setVersion("9.9.9");
        Map<GAV, String> sourcesUrls = Collections.singletonMap(gav, testUrl);

        ExposedFinder finder = new ExposedFinder();
        List<SourceFileResult> results = new ArrayList<>();
        boolean found = finder.exposeTryCachedSourcesWithPersist(binJar.getAbsolutePath(), sourcesUrls, results);

        assertTrue(found);
        assertEquals(1, results.size());

        File mavenRepoFile = finder.exposeGetMavenRepoTargetForDownload(gav, testUrl);
        if (mavenRepoFile != null) {
            FileUtils.deleteQuietly(mavenRepoFile.getParentFile());
        }
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

        boolean exposeTryCachedSourcesWithPersist(String binFile, Map<GAV, String> sourcesUrls,
                List<SourceFileResult> results) {
            return tryCachedSources(binFile, sourcesUrls, results, true);
        }

        File exposePersistCachedSourceInMavenRepo(GAV gav, String downloadUrl, File sourceFile, String binFile) {
            return persistCachedSourceInMavenRepo(gav, downloadUrl, sourceFile, binFile);
        }

        File exposeGetMavenRepoTargetForDownload(GAV gav, String downloadUrl) {
            return getMavenRepoTargetForDownload(gav, downloadUrl);
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
