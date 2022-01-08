package io.dockstore.webservice.languages;

import static io.dockstore.webservice.languages.CWLHandler.Preprocessor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.SourceFile;
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

    @Test
    public void testImport() {
        final String imported = "test: value";
        Preprocessor pre = new Preprocessor(set(file("/b", imported)));
        Object result = pre.preprocess(parse("$import: b"), "/a", 0);
        Assert.assertEquals(parse(imported), result);
    }

    @Test
    public void testInclude() {
        final String included = "abcde";
        Preprocessor pre = new Preprocessor(set(file("/b", included)));
        Object result = pre.preprocess(parse("$import: b"), "/a", 0);
        Assert.assertEquals(included, result);
    }

    @Test
    public void testMixin() {
        Preprocessor pre = new Preprocessor(set(file("/b", "a: x\nb: y")));
        Object result = pre.preprocess(parse("a: z\n$mixin: b"), "/a", 0);
        Assert.assertEquals(parse("a: z\nb: y"), result);
    }

    @Test
    public void testRun() {
        final String runContent = "something: torun";
        Preprocessor pre = new Preprocessor(set(file("/b", runContent)));
        Object result = pre.preprocess(parse("run: b"), "/a", 0);
        Assert.assertEquals(parse("run:\n  " + runContent), result);
    }

    @Test
    public void testMissingFile() {
        Preprocessor pre = new Preprocessor(set());
        Assert.assertEquals(Collections.emptyMap(), pre.preprocess(parse("$import: b"), "/a", 0));
        Assert.assertEquals("", pre.preprocess(parse("$include: b"), "/a", 0));
        Assert.assertEquals(parse("a: x"), pre.preprocess(parse("a: x\n$mixin: b"), "/a", 0));
    }

    @Test
    public void testMultilevelImports() {
        final String imported = "levels: two";
        Preprocessor pre = new Preprocessor(set(file("/b", "$import: c"), file("/c", imported)));
        Object result = pre.preprocess(parse("$import: b"), "/a", 0);
        Assert.assertEquals(parse(imported), result);
    }

    @Test
    public void testHttpUrlImport() {
        Assert.assertEquals(Collections.emptyMap(), new Preprocessor(set()).preprocess(parse("$import: http://www.foo.com/bar"), "/a", 0));
        Assert.assertEquals(Collections.emptyMap(), new Preprocessor(set()).preprocess(parse("$import: https://www.foo.com/bar"), "/a", 0));
    }

    @Test
    public void testFileUrlImport() {
        final String imported = "some: thing";
        Preprocessor pre = new Preprocessor(set(file("/b", imported)));
        Object result = pre.preprocess(parse("$import: file://b"), "/a", 0);
        Assert.assertEquals(parse(imported), result);
    }

    @Test
    public void testMaxDepth() {
    }

    @Test
    public void testMaxCharCount() {
    }

    @Test
    public void testMaxFileCount() {
    }
}
