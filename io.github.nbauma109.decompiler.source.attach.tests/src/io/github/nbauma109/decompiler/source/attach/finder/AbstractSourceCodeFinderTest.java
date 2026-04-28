package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.AttributeSet;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.net.proxy.IProxyChangeListener;
import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.runtime.CoreException;
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
    @Test
    public void getStringReturnsEmptyForUnreachableHttpUrlNullProxyService() throws Exception {
        // http://localhost:0 is unreachable but forces creation of HttpURLConnection,
        // exercising openConnectionWithProxy → setProxyAuthenticator (null-proxyData early-return path).
        ExposedFinder finder = new ExposedFinder();
        finder.setProxyService(null);

        String result = finder.exposeGetString(new URL("http://localhost:0/nonexistent.txt")); //$NON-NLS-1$

        assertEquals("", result);
    }

    @Test
    public void getStringWithProxyDataAndCredentialsSetsPerConnectionAuthenticator() throws Exception {
        // Inject a proxy service that returns proxy data with credentials.
        // http://localhost:0 is unreachable but forces HttpURLConnection creation so that
        // setProxyAuthenticator is reached and calls conn.setAuthenticator().
        StubProxyData proxyData = new StubProxyData(IProxyData.HTTP_PROXY_TYPE,
                "proxy.test.local", 3128, "proxyUser", "proxyPass");
        ExposedFinder finder = new ExposedFinder();
        finder.setProxyService(new StubProxyService(new IProxyData[] { proxyData }));

        String result = finder.exposeGetString(new URL("http://localhost:0/nonexistent.txt")); //$NON-NLS-1$

        assertEquals("", result);
    }

    @Test
    public void getStringWithProxyDataAndNullPasswordDoesNotSetAuthenticator() throws Exception {
        // Proxy data present but password is null → the inner if-branch is false, setAuthenticator is NOT called.
        StubProxyData proxyData = new StubProxyData(IProxyData.HTTP_PROXY_TYPE,
                "proxy.test.local", 3128, "proxyUser", null);
        ExposedFinder finder = new ExposedFinder();
        finder.setProxyService(new StubProxyService(new IProxyData[] { proxyData }));

        String result = finder.exposeGetString(new URL("http://localhost:0/nonexistent.txt")); //$NON-NLS-1$

        assertEquals("", result);
    }

    // -----------------------------------------------------------------------
    // Stub helpers shared with proxy-service tests
    // -----------------------------------------------------------------------

    private static class StubProxyData implements IProxyData {
        private final String type;
        private String host;
        private int port;
        private String userId;
        private String password;

        StubProxyData(String type, String host, int port, String userId, String password) {
            this.type = type;
            this.host = host;
            this.port = port;
            this.userId = userId;
            this.password = password;
        }

        @Override
        public String getType() { return type; }

        @Override
        public String getHost() { return host; }

        @Override
        public int getPort() { return port; }

        @Override
        public String getUserId() { return userId; }

        @Override
        public String getPassword() { return password; }

        @Override
        public boolean isRequiresAuthentication() { return userId != null && !userId.isEmpty(); }

        @Override
        public void setHost(String h) { this.host = h; }

        @Override
        public void setPort(int p) { this.port = p; }

        @Override
        public void setUserid(String u) { this.userId = u; }

        @Override
        public void setPassword(String pw) { this.password = pw; }

        @Override
        public void disable() { this.host = null; this.port = -1; }
    }

    private static class StubProxyService implements IProxyService {
        private final IProxyData[] data;

        StubProxyService(IProxyData[] data) { this.data = data; }

        @Override
        public IProxyData[] select(java.net.URI uri) { return data; }

        @Override
        public IProxyData[] getProxyData() { return data; }

        @Override
        public IProxyData getProxyData(String type) { return null; }

        @Override
        public IProxyData[] getProxyDataForHost(String host) { return data; }

        @Override
        public IProxyData getProxyDataForHost(String host, String type) { return null; }

        @Override
        public boolean isProxiesEnabled() { return true; }

        @Override
        public boolean hasSystemProxies() { return false; }

        @Override
        public boolean isSystemProxiesEnabled() { return false; }

        @Override
        public void setProxiesEnabled(boolean e) { /* stub */ }

        @Override
        public void setSystemProxiesEnabled(boolean e) { /* stub */ }

        @Override
        public void setProxyData(IProxyData[] d) throws CoreException { /* stub */ }

        @Override
        public String[] getNonProxiedHosts() { return new String[0]; }

        @Override
        public void setNonProxiedHosts(String[] h) throws CoreException { /* stub */ }

        @Override
        public void addProxyChangeListener(IProxyChangeListener l) { /* stub */ }

        @Override
        public void removeProxyChangeListener(IProxyChangeListener l) { /* stub */ }
    }

    private static final class ExposedFinder extends AbstractSourceCodeFinder {
        private IProxyService injectedProxyService;

        void setProxyService(IProxyService proxyService) {
            this.injectedProxyService = proxyService;
        }

        @Override
        protected IProxyService resolveProxyService() {
            return injectedProxyService;
        }

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
