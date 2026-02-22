package io.github.nbauma109.decompiler.source.attach.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import io.github.nbauma109.decompiler.util.HashUtils;

public abstract class AbstractSourceCodeFinderTests {

    protected void testFindCommonsIo(String serviceUrl) throws IOException {
        String gavUrl = "https://repo1.maven.org/maven2/commons-io/commons-io/2.16.0/";
        String fileName = "commons-io-2.16.0";
        testFind(serviceUrl, gavUrl, fileName);
    }

    protected void testFindSlf4jNop(String serviceUrl) throws IOException {
        String gavUrl = "https://repo1.maven.org/maven2/org/slf4j/slf4j-nop/2.1.0-alpha1/";
        String fileName = "slf4j-nop-2.1.0-alpha1";
        testFind(serviceUrl, gavUrl, fileName);
    }

    protected void testFindAsmUtil(String serviceUrl) throws IOException {
        String gavUrl = "https://repo1.maven.org/maven2/org/ow2/asm/asm-util/9.7/";
        String fileName = "asm-util-9.7";
        testFind(serviceUrl, gavUrl, fileName);
    }

    protected void testFindJunit(String serviceUrl) throws IOException {
        String gavUrl = "https://maven.alfresco.com/nexus/content/groups/public/junit/junit/4.11-20120805-1225/";
        String fileName = "junit-4.11-20120805-1225";
        testFind(serviceUrl, gavUrl, fileName);
    }

    protected void testFindOsgiServiceEventWithLeadingZeroSha1(String serviceUrl) throws IOException {
        String gavUrl = "https://repo1.maven.org/maven2/org/osgi/org.osgi.service.event/1.4.1/";
        String fileName = "org.osgi.service.event-1.4.1";
        String expectedBinSha1 = "0f6d98f06834a911d04be512b751de3563d6b909";
        testFind(serviceUrl, gavUrl, fileName, expectedBinSha1);
    }

    protected void testFind(String serviceUrl, String gavUrl, String fileName)
            throws IOException {
        testFind(serviceUrl, gavUrl, fileName, null);
    }

    protected void testFind(String serviceUrl, String gavUrl, String fileName, String expectedBinSha1)
            throws IOException {
        AbstractSourceCodeFinder directLinkSourceCodeFinder = newSourceCodeFinder(serviceUrl);
        List<SourceFileResult> results = new ArrayList<>();
        File downloadDir = new File("target");
        File jarFile = new File(downloadDir, fileName + ".jar");
        URL url = new URL(gavUrl + fileName + ".jar");
        File srcFile = new File(downloadDir, fileName + "-sources.jar");
        URL srcUrl = new URL(gavUrl + fileName + "-sources.jar");
        FileUtils.copyURLToFile(url, jarFile);
        FileUtils.copyURLToFile(srcUrl, srcFile);
        if (jarFile.exists()) {
            String sha1 = HashUtils.sha1Hash(jarFile);
            if (expectedBinSha1 != null) {
                assertEquals(expectedBinSha1, sha1);
                assertTrue("Expected SHA-1 to start with 0 but was " + sha1, sha1.startsWith("0"));
            }
            directLinkSourceCodeFinder.find(jarFile.getAbsolutePath(), sha1, results);
            assertNotNull(results);
            assertEquals(1, results.size());
            String expectedSha1 = HashUtils.sha1Hash(srcFile);
            String actualSha1 = HashUtils.sha1Hash(new File(results.get(0).getSource()));
            assertEquals(expectedSha1, actualSha1);
        }
    }

    protected abstract AbstractSourceCodeFinder newSourceCodeFinder(String serviceUrl);
}
