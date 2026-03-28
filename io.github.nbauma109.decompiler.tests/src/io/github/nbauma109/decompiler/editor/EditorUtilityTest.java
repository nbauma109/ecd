package io.github.nbauma109.decompiler.editor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.nbauma109.decompiler.Startup;
import io.github.nbauma109.decompiler.testutil.DecompilerTestSupport;

public class EditorUtilityTest {

    private File testRoot;

    @Before
    public void setUp() {
        File targetDir = new File("target");
        assertTrue(targetDir.exists() || targetDir.mkdirs());

        testRoot = new File(targetDir, "editor-utility-tests" + File.separator + System.nanoTime());
        assertTrue(testRoot.mkdirs());
    }

    @After
    public void tearDown() {
        FileUtils.deleteQuietly(testRoot);
    }

    @Test
    public void decompilerTypeContainsDefaultDecompilerInSortedSet() {
        assertFalse(DecompilerType.getDecompilerTypes().isEmpty());
        assertTrue(DecompilerType.getDecompilerTypes().contains(DecompilerType.getDefault()));
    }

    @Test
    public void decompilerClassEditorInputUsesCustomTooltipWhenProvided() throws IOException {
        File file = new File(testRoot, "Demo.class");
        Files.writeString(file.toPath(), "bytes", StandardCharsets.UTF_8);
        IFileStore store = EFS.getLocalFileSystem().fromLocalFile(file);
        DecompilerClassEditorInput input = new DecompilerClassEditorInput(store);

        assertTrue(input.getToolTipText().contains("Demo.class"));

        input.setToolTipText("custom tooltip");

        assertEquals("custom tooltip", input.getToolTipText());
    }

    @Test
    public void realignStatusExposesExpectedEnumValues() {
        assertArrayEquals(new RealignStatus[] {
                RealignStatus.TURNED_OFF,
                RealignStatus.NATIVELY_REALIGNED,
                RealignStatus.PARSED_AND_REALIGNED,
                RealignStatus.PARSE_ERROR
        }, RealignStatus.values());
        assertEquals(RealignStatus.PARSE_ERROR, RealignStatus.valueOf("PARSE_ERROR"));
    }

    @Test
    public void startupEarlyStartupIsNoOp() {
        new Startup().earlyStartup();
    }
}
