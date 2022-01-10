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
        Preprocessor pre = new Preprocessor(files);
        return pre.preprocess(parse(content), rootPath, 0);
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
        Assert.assertEquals(parse("a: z\nb: y"), preprocess("a: z\n$mixin: b", set(file("/b", "a: x\nb: y"))));
    }

    @Test
    public void testRun() {
        final String runContent = "something: torun";
        Assert.assertEquals(parse("run:\n  " + runContent), preprocess("run: b", set(file("/b", runContent))));
    }

    @Test
    public void testMissingFile() {
        Preprocessor pre = new Preprocessor(set());
        Assert.assertEquals(Collections.emptyMap(), preprocess("$import: b", set()));
        Assert.assertEquals("", preprocess("$include: b", set()));
        Assert.assertEquals(parse("a: x"), preprocess("a: x\n$mixin: b", set()));
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
        char[] chars = new char[includeSize];
        Arrays.fill(chars, 'x');
        preprocess(builder.toString(), set(file("/b", new String(chars))));
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
