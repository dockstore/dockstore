package io.dockstore.webservice.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.DescriptorLanguage;
import org.junit.jupiter.api.Test;

class LimitedSourceFileBuilderTest {

    private static final DescriptorLanguage.FileType TYPE = DescriptorLanguage.FileType.DOCKSTORE_WDL;
    private static final String SMALL_CONTENT = "A small bit of content.";
    private static final String HUGE_CONTENT = ".".repeat(100_000_000);
    private static final String BINARY_CONTENT = "Content that contains a nul character \u0000";
    private static final String PATH = "abc.wdl";
    private static final String ABSOLUTE_PATH = "/abc.wdl";

    @Test
    void testSmallContent() {
        SourceFile file = SourceFile.limitedBuilder().type(TYPE).content(SMALL_CONTENT).path(PATH).absolutePath(ABSOLUTE_PATH).build();
        assertEquals(TYPE, file.getType());
        assertEquals(SMALL_CONTENT, file.getContent());
        assertEquals(SourceFile.FormEnum.COMPLETE, file.getForm());
        assertEquals(PATH, file.getPath());
        assertEquals(ABSOLUTE_PATH, file.getAbsolutePath());
    }

    @Test
    void testHugeContent() {
        SourceFile file = SourceFile.limitedBuilder().type(TYPE).content(HUGE_CONTENT).path(PATH).absolutePath(ABSOLUTE_PATH).build();
        assertEquals(TYPE, file.getType());
        assertTrue(file.getContent().startsWith("Dockstore does not store files of this type over"));
        assertEquals(SourceFile.FormEnum.ERROR, file.getForm());
        assertEquals(PATH, file.getPath());
        assertEquals(ABSOLUTE_PATH, file.getAbsolutePath());
    }

    @Test
    void testBinaryContent() {
        SourceFile file = SourceFile.limitedBuilder().type(TYPE).content(BINARY_CONTENT).path(PATH).absolutePath(ABSOLUTE_PATH).build();
        assertEquals(TYPE, file.getType());
        assertEquals("Dockstore does not store binary files", file.getContent());
        assertEquals(SourceFile.FormEnum.ERROR, file.getForm());
        assertEquals(PATH, file.getPath());
        assertEquals(ABSOLUTE_PATH, file.getAbsolutePath());
    }

    @Test
    void testNullContent() {
        SourceFile file = SourceFile.limitedBuilder().type(TYPE).content(null).path(PATH).absolutePath(ABSOLUTE_PATH).build();
        assertEquals(TYPE, file.getType());
        assertEquals("Dockstore could not retrieve this file", file.getContent());
        assertEquals(SourceFile.FormEnum.ERROR, file.getForm());
        assertEquals(PATH, file.getPath());
        assertEquals(ABSOLUTE_PATH, file.getAbsolutePath());
    }

    @Test
    void testPathsMethod() {
        SourceFile file = SourceFile.limitedBuilder().type(TYPE).content(SMALL_CONTENT).paths(ABSOLUTE_PATH).build();
        assertEquals(TYPE, file.getType());
        assertEquals(SMALL_CONTENT, file.getContent());
        assertEquals(SourceFile.FormEnum.COMPLETE, file.getForm());
        assertEquals(ABSOLUTE_PATH, file.getPath());
        assertEquals(ABSOLUTE_PATH, file.getAbsolutePath());
    }
}
