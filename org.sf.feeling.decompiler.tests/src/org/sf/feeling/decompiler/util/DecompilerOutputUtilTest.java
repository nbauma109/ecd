package org.sf.feeling.decompiler.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.sf.feeling.decompiler.util.TextAssert.assertEquivalent;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class DecompilerOutputUtilTest {

    @Test
    public void testToStringPackageImportFernflower() throws IOException, URISyntaxException {
        test("PackageImportFernflower");
    }

    @Test
    public void testToStringPackageNoImportFernflower() throws IOException, URISyntaxException {
        test("PackageNoImportFernflower");
    }
    
    @Test
    public void testToStringImportNoPackageFernflower() throws IOException, URISyntaxException {
        test("ImportNoPackageFernflower");
    }
    
    @Test
    public void testToStringNoPackageNoImportFernflower() throws IOException, URISyntaxException {
        test("NoPackageNoImportFernflower");
    }

    @Test
    public void testToStringPackageImportProcyon() throws IOException, URISyntaxException {
        test("PackageImportProcyon");
    }
    
    @Test
    public void testToStringPackageNoImportProcyon() throws IOException, URISyntaxException {
        test("PackageNoImportProcyon");
    }
    
    @Test
    public void testToStringImportNoPackageProcyon() throws IOException, URISyntaxException {
        test("ImportNoPackageProcyon");
    }
    
    @Test
    public void testToStringNoPackageNoImportProcyon() throws IOException, URISyntaxException {
        test("NoPackageNoImportProcyon");
    }

    @Test
    public void testTryCatchFernflower() throws IOException, URISyntaxException {
        test("TryCatchFernflower");
    }

    private void test(String testName) throws IOException, URISyntaxException {
        String input = toString(getClass().getResource("/input/" + testName + ".txt"));
        String expected = toString(getClass().getResource("/output/" + testName + ".txt"));
        DecompilerOutputUtil decompilerOutputUtil = new DecompilerOutputUtil(input);
        decompilerOutputUtil.realign();
        String output = decompilerOutputUtil.toString();
        assertEquivalent(expected, output);
    }
    
    private String toString(URL resource) throws IOException, URISyntaxException {
        return IOUtils.toString(resource.toURI(), UTF_8);
    }
}
