package io.dockstore.webservice.languages;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.languages.CWLHandler.Preprocessor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
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

    public void testNoSubsitutions() {
        final String arrayOfMaps = "-\n  a: b\n-\n  d: e";
        Assert.assertEquals(parse(arrayOfMaps), preprocess(arrayOfMaps, set()));
    }

    @Test
    public void testImport() {
        final String imported = "test: value";
        Assert.assertEquals(parse(imported), preprocess("$import: b", set(file("/b", imported))));
    }

    @Test
    public void testInclude() {
        final String included = "abcde";
        Assert.assertEquals(included, preprocess("$import: b", set(file("/b", included))));
    }

    @Test
    public void testMixin() {
        Assert.assertEquals(parse(V1_0 + WORKFLOW + metadataHint("a") + "a: z\nb: y"), preprocess(V1_0 + WORKFLOW + "a: z\n$mixin: b", set(file("/b", "a: x\nb: y"))));
        Assert.assertEquals(parse(V1_1 + WORKFLOW + metadataHint("a") + "$mixin: v"), preprocess(V1_1 + WORKFLOW + "$mixin: v", set()));
    }

    @Test
    public void testRun() {
        final String runContent = "something: torun";
        Assert.assertEquals(parse("run:\n  " + runContent), preprocess("run: b", set(file("/b", runContent))));
        Assert.assertEquals(parse("run:\n  " + runContent), preprocess("run:\n  $import: b", set(file("/b", runContent))));
    }

    @Test
    public void testMissingFile() {
        Assert.assertEquals(Collections.emptyMap(), preprocess("$import: b", set()));
        Assert.assertEquals("", preprocess("$include: b", set()));
        Assert.assertEquals(parse(V1_0 + WORKFLOW + metadataHint("a") + "a: x"), preprocess(V1_0 + WORKFLOW + "a: x\n$mixin: b", set()));
    }

    @Test
    public void testMultilevelImports() {
        final String imported = "levels: two";
        Assert.assertEquals(parse(imported), preprocess("$import: b", set(file("/b", "$import: c"), file("/c", imported))));
    }

    @Test
    public void testRelativeImport() {
        final String imported = "some: content";
        Assert.assertEquals(parse(imported), preprocess("$import: subsub/b", set(file("/sub/subsub/b", imported)), "/sub/a"));
    }

    @Test
    public void testAbsoluteImport() {
        final String imported = "some: content";
        Assert.assertEquals(parse(imported), preprocess("$import: /b", set(file("/b", imported)), "/sub/a"));
    }

    @Test
    public void testHttpUrlImport() {
        Assert.assertEquals(Collections.emptyMap(), preprocess("$import: http://www.foo.com/bar", set()));
        Assert.assertEquals(Collections.emptyMap(), preprocess("$import: https://www.foo.com/bar", set()));
    }

    @Test
    public void testFileUrlImport() {
        final String imported = "some: thing";
        Assert.assertEquals(parse(imported), preprocess("$import: file://b", set(file("/b", imported))));
    }

    @Test
    public void testRunOfNonexistentFile() {
        final String runImport = "run:\n  $import: filename";
        final String runReduced = "run: filename";
        Assert.assertEquals(parse(runReduced), preprocess(runImport, set()));
        Assert.assertEquals(parse(runReduced), preprocess(runReduced, set()));
    }

    @Test(expected = CustomWebApplicationException.class)
    public void testMaxDepth() {
        // preprocess a file that recursively imports itself
        preprocess("$import: a", set(file("/a", "$import: a")));
    }

    private void preprocessManyIncludes(int includeCount, int includeSize) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < includeCount; i++) {
            builder.append("a" + i + ":\n  $include: b\n");
        }
        preprocess(builder.toString(), set(file("/b", "x".repeat(includeSize))));
    }

    @Test(expected = CustomWebApplicationException.class)
    public void testMaxCharCount() {
        // preprocess a moderate number of very large includes, which add up to >1GB
        preprocessManyIncludes(100, 16 * 1024 * 1024);
    }

    @Test(expected = CustomWebApplicationException.class)
    public void testMaxFileCount() {
        // preprocess a very large number of zero-length includes
        preprocessManyIncludes(100000, 0);
    }
}
