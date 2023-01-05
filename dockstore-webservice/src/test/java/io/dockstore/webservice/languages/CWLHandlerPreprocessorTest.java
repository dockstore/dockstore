package io.dockstore.webservice.languages;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.languages.CWLHandler.Preprocessor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Tests CWLHandler.Preprocessor
 */
public class CWLHandlerPreprocessorTest {

    private static final String V1_0 = "cwlVersion: v1.0\n";
    private static final String V1_1 = "cwlVersion: v1.1\n";
    private static final String WORKFLOW = "class: Workflow\nid: foo\n";

    private SourceFile file(String absolutePath, String content) {
        SourceFile sourceFile = mock(SourceFile.class);
        when(sourceFile.getAbsolutePath()).thenReturn(absolutePath);
        when(sourceFile.getContent()).thenReturn(content);
        return sourceFile;
    }

    private Set<SourceFile> set(SourceFile... sourceFiles) {
        return new HashSet<>(Arrays.asList(sourceFiles));
    }

    private Object parse(String yaml) {
        new Yaml(new SafeConstructor()).load(yaml);
        return new Yaml().load(yaml);
    }

    private Object preprocess(String content, Set<SourceFile> files) {
        return preprocess(content, files, "/a");
    }

    private Object preprocess(String content, Set<SourceFile> files, String rootPath) {
        return new Preprocessor(files).preprocess(parse(content), rootPath);
    }

    private String metadataHint(String path) {
        return String.format("hints: [{ class: '%s', path: '%s' }]\n", CWLHandler.METADATA_HINT_CLASS, path);
    }

    @Test
    void testNoSubsitutions() {
        final String arrayOfMaps = "-\n  a: b\n-\n  d: e";
        Assertions.assertEquals(parse(arrayOfMaps), preprocess(arrayOfMaps, set()));
    }

    @Test
    void testImport() {
        final String imported = "test: value";
        Assertions.assertEquals(parse(imported), preprocess("$import: b", set(file("/b", imported))));
    }

    @Test
    void testInclude() {
        final String included = "abcde";
        Assertions.assertEquals(included, preprocess("$import: b", set(file("/b", included))));
    }

    @Test
    void testMixin() {
        Assertions.assertEquals(parse(V1_0 + WORKFLOW + metadataHint("a") + "a: z\nb: y"), preprocess(V1_0 + WORKFLOW + "a: z\n$mixin: b", set(file("/b", "a: x\nb: y"))));
        Assertions.assertEquals(parse(V1_1 + WORKFLOW + metadataHint("a") + "$mixin: v"), preprocess(V1_1 + WORKFLOW + "$mixin: v", set()));
    }

    @Test
    void testRun() {
        final String runContent = "something: torun";
        Assertions.assertEquals(parse("run:\n  " + runContent), preprocess("run: b", set(file("/b", runContent))));
        Assertions.assertEquals(parse("run:\n  " + runContent), preprocess("run:\n  $import: b", set(file("/b", runContent))));
    }

    @Test
    void testMissingFile() {
        Assertions.assertEquals(Collections.emptyMap(), preprocess("$import: b", set()));
        Assertions.assertEquals("", preprocess("$include: b", set()));
        Assertions.assertEquals(parse(V1_0 + WORKFLOW + metadataHint("a") + "a: x"), preprocess(V1_0 + WORKFLOW + "a: x\n$mixin: b", set()));
    }

    @Test
    void testMultilevelImports() {
        final String imported = "levels: two";
        Assertions.assertEquals(parse(imported), preprocess("$import: b", set(file("/b", "$import: c"), file("/c", imported))));
    }

    @Test
    void testRelativeImport() {
        final String imported = "some: content";
        Assertions.assertEquals(parse(imported), preprocess("$import: subsub/b", set(file("/sub/subsub/b", imported)), "/sub/a"));
    }

    @Test
    void testAbsoluteImport() {
        final String imported = "some: content";
        Assertions.assertEquals(parse(imported), preprocess("$import: /b", set(file("/b", imported)), "/sub/a"));
    }

    @Test
    void testHttpUrlImport() {
        Assertions.assertEquals(Collections.emptyMap(), preprocess("$import: http://www.foo.com/bar", set()));
        Assertions.assertEquals(Collections.emptyMap(), preprocess("$import: https://www.foo.com/bar", set()));
    }

    @Test
    void testFileUrlImport() {
        final String imported = "some: thing";
        Assertions.assertEquals(parse(imported), preprocess("$import: file://b", set(file("/b", imported))));
    }

    @Test
    void testRunOfNonexistentFile() {
        final String runImport = "run:\n  $import: filename";
        final String runReduced = "run: filename";
        Assertions.assertEquals(parse(runReduced), preprocess(runImport, set()));
        Assertions.assertEquals(parse(runReduced), preprocess(runReduced, set()));
    }

    @Test
    void testMaxDepth() {
        // preprocess a file that recursively imports itself
        assertThrows(CustomWebApplicationException.class, () -> preprocess("$import: a", set(file("/a", "$import: a"))));
    }

    private void preprocessManyIncludes(int includeCount, int includeSize) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < includeCount; i++) {
            builder.append("a").append(i).append(":\n  $include: b\n");
        }
        preprocess(builder.toString(), set(file("/b", "x".repeat(includeSize))));
    }

    @Test
    void testMaxCharCount() {
        // preprocess a moderate number of very large includes, which add up to >1GB
        assertThrows(CustomWebApplicationException.class, () ->  preprocessManyIncludes(100, 16 * 1024 * 1024));
    }

    @Test
    void testMaxFileCount() {
        // preprocess a very large number of zero-length includes
        assertThrows(CustomWebApplicationException.class, () ->   preprocessManyIncludes(100000, 0));
    }
}
