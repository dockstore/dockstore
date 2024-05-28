package io.dockstore.webservice.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.DescriptorLanguage;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LimitedSourceFileBuilderTest {

    @Test
    void testSmall() {
        DescriptorLanguage.FileType type = DescriptorLanguage.FileType.DOCKSTORE_WDL;
        String content = "This is some content.";
        String path = "abc.wdl";
        String absolutePath = "/abc.wdl";

        SourceFile file = SourceFile.limitedBuilder().type(type).content(content).path(path).absolutePath(absolutePath).build();

        assertEquals(type, file.getType());
        assertEquals(content, file.getContent());
        assertEquals(path, file.getPath());
        assertEquals(absolutePath, file.getAbsolutePath());
    }

    @Test
    void testBig() {
        DescriptorLanguage.FileType type = DescriptorLanguage.FileType.DOCKSTORE_WDL;
        String content = "13 characters".repeat(1000000);
        String path = "abc.wdl";
        String absolutePath = "/abc.wdl";
        
        SourceFile file = SourceFile.limitedBuilder().type(type).content(content).path(path).absolutePath(absolutePath).build();

        assertEquals(type, file.getType());
        assertTrue(file.getContent().startsWith("Dockstore does not store files of this type over"));
        assertEquals(path, file.getPath());
        assertEquals(absolutePath, file.getAbsolutePath());
    }

    @Test
    void testBinary() {
        DescriptorLanguage.FileType type = DescriptorLanguage.FileType.DOCKSTORE_WDL;
        String content = "content with a nul character \u0000";
        String path = "abc.wdl";
        String absolutePath = "/abc.wdl";
        
        SourceFile file = SourceFile.limitedBuilder().type(type).content(content).path(path).absolutePath(absolutePath).build();

        assertEquals(type, file.getType());
        assertTrue(file.getContent().startsWith("Dockstore does not store binary files"));
        assertEquals(path, file.getPath());
        assertEquals(absolutePath, file.getAbsolutePath());
    }

    @Test
    void testPaths() {
        DescriptorLanguage.FileType type = DescriptorLanguage.FileType.DOCKSTORE_WDL;
        String content = "some content";
        String path = "/abc.wdl";

        SourceFile file = SourceFile.limitedBuilder().type(type).content(content).paths(path).build();

        assertEquals(type, file.getType());
        assertEquals(content, file.getContent());
        assertEquals(path, file.getPath());
        assertEquals(path, file.getAbsolutePath());
    }
 
    @Test
    void testNullContent() {
        DescriptorLanguage.FileType type = DescriptorLanguage.FileType.DOCKSTORE_WDL;
        String path = "abc.wdl";
        String absolutePath = "/abc.wdl";
        SourceFile file = SourceFile.limitedBuilder().type(type).content(null).path(path).absolutePath(absolutePath).build();
        assertEquals(type, file.getType());
        assertEquals(null, file.getContent());
        assertEquals(path, file.getPath());
        assertEquals(absolutePath, file.getAbsolutePath());
    }
}
