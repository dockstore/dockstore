package io.dockstore.webservice.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.DescriptorLanguage;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LimitedSourceFileBuilderTest {

    private static final DescriptorLanguage.FileType TYPE = DescriptorLanguage.FileType.DOCKSTORE_WDL;
    private static final String SMALL_CONTENT = "This is some content.";
    private static final String BIG_CONTENT = ".".repeat(100_000_000);
    private static final String BINARY_CONTENT = "Content that contains a nul character \u0000";
    private static final String PATH = "abc.wdl";
    private static final String ABSOLUTE_PATH = "/abc.wdl";

    @Test
    void testSmallContent() {
        SourceFile file = SourceFile.limitedBuilder().type(TYPE).content(SMALL_CONTENT).path(PATH).absolutePath(ABSOLUTE_PATH).build();
        assertEquals(TYPE, file.getType());
        assertEquals(SMALL_CONTENT, file.getContent());
        assertEquals(PATH, file.getPath());
        assertEquals(ABSOLUTE_PATH, file.getAbsolutePath());
    }

    @Test
    void testBigContent() {
        SourceFile file = SourceFile.limitedBuilder().type(TYPE).content(BIG_CONTENT).path(PATH).absolutePath(ABSOLUTE_PATH).build();
        assertEquals(TYPE, file.getType());
        assertTrue(file.getContent().startsWith("Dockstore does not store files of this type over"));
        assertEquals(PATH, file.getPath());
        assertEquals(ABSOLUTE_PATH, file.getAbsolutePath());
    }

    @Test
    void testBinaryContent() {
        SourceFile file = SourceFile.limitedBuilder().type(TYPE).content(BINARY_CONTENT).path(PATH).absolutePath(ABSOLUTE_PATH).build();
        assertEquals(TYPE, file.getType());
        assertTrue(file.getContent().startsWith("Dockstore does not store binary files"));
        assertEquals(PATH, file.getPath());
        assertEquals(ABSOLUTE_PATH, file.getAbsolutePath());
    }

    @Test
    void testNullContent() {
        SourceFile file = SourceFile.limitedBuilder().type(TYPE).content(null).path(PATH).absolutePath(ABSOLUTE_PATH).build();
        assertEquals(TYPE, file.getType());
        assertEquals(null, file.getContent());
        assertEquals(PATH, file.getPath());
        assertEquals(ABSOLUTE_PATH, file.getAbsolutePath());
    }

    @Test
    void testPathsMethod() {
        SourceFile file = SourceFile.limitedBuilder().type(TYPE).content(SMALL_CONTENT).paths(ABSOLUTE_PATH).build();
        assertEquals(TYPE, file.getType());
        assertEquals(SMALL_CONTENT, file.getContent());
        assertEquals(ABSOLUTE_PATH, file.getPath());
        assertEquals(ABSOLUTE_PATH, file.getAbsolutePath());
    }
}
