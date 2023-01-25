// TODO add copyright
package io.dockstore.webservice.languages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * Tests public methods in IpynbHandler
 */
@ExtendWith(SystemStubsExtension.class)
class IpynbHandlerTest {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    private IpynbHandler handler;
    private Notebook notebook;
    private WorkflowVersion version;
  
    private String read(String resourceName) {
        try {
            return FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("notebooks/ipynb/" + resourceName)));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String deleteMatchingLines(String content, String match) {
        return String.join("\n", List.of(content.split("\n")).stream().filter(line -> !line.contains(match)).toList());
    }

    private <T, F> Set<F> map(Collection<T> values, Function<T, F> mapper) {
        return values.stream().map(mapper).collect(Collectors.toSet());
    }

    private SourceFile mockSourceFile(String path, String content, DescriptorLanguage.FileType fileType) {
        SourceFile file = new SourceFile();
        file.setPath(path);
        file.setAbsolutePath(path);
        file.setContent(content);
        file.setType(fileType);
        return file;
    }

    private SourceCodeRepoInterface mockRepo(Set<String> paths) {

        SourceCodeRepoInterface repo = Mockito.mock(SourceCodeRepoInterface.class);
        when(repo.listFiles(any(), any(), any())).thenAnswer(
            invocation -> {
                String dir = invocation.getArgument(1, String.class);
                String normalizedDir = dir.endsWith("/") ? dir : dir + "/";
                return paths.stream().filter(path -> path.startsWith(normalizedDir)).map(path -> path.substring(normalizedDir.length()).split("/")[0]).toList();
            }
        );
        when(repo.readFile(any(), any(), any(), any())).thenAnswer(
            invocation -> {
                DescriptorLanguage.FileType type = invocation.getArgument(2, DescriptorLanguage.FileType.class);
                String path = invocation.getArgument(3, String.class);
                return Optional.ofNullable(paths.contains(path) ? mockSourceFile(path, "content of " + path, type) : null);
            }
        );
        when(repo.readPath(any(), any(), any(), any(), any())).thenCallRealMethod();
        when(repo.readPaths(any(), any(), any(), any(), any())).thenCallRealMethod();
        return repo;
    }

    @BeforeEach
    void init() {
        handler = new IpynbHandler();
        notebook = new Notebook();
        version = new WorkflowVersion();
    }

    @Test
    void testParseWorkflowContentNoAuthor() {
        handler.parseWorkflowContent("authors.ipynb", read("authors.ipynb").replace("authors", "foo"), Set.of(), version);
        assertTrue(version.getAuthors().isEmpty());
    }

    @Test
    void testParseWorkflowContentBlankAuthor() {
        handler.parseWorkflowContent("authors.ipynb", deleteMatchingLines(read("authors.ipynb"), "Author"), Set.of(), version);
        assertTrue(version.getAuthors().isEmpty());
    }

    @Test
    void testParseWorkflowContentOneAuthor() {
        handler.parseWorkflowContent("authors.ipynb", deleteMatchingLines(read("authors.ipynb"), "Author Two"), Set.of(), version);
        assertEquals(Set.of("Author One"), map(version.getAuthors(), Author::getName));
    }

    @Test
    void testParseWorkflowContentTwoAuthors() {
        handler.parseWorkflowContent("authors.ipynb", read("authors.ipynb"), Set.of(), version);
        assertEquals(Set.of("Author One", "Author Two"), map(version.getAuthors(), Author::getName));
    }

    @Test
    void testProcessImportsNoRees() {
        Map<String, SourceFile> rees = handler.processImports("", "", version, mockRepo(Set.of("/some.ipynb")), "/");
        assertTrue(rees.keySet().isEmpty());
    }

    @Test
    void testProcessImportsRootRequirementsTxt() {
        Map<String, SourceFile> rees = handler.processImports("", "", version, mockRepo(Set.of("/some.ipynb", "/requirements.txt", "/blah/requirements.txt")), "/");
        assertEquals(Set.of("/requirements.txt"), rees.keySet());
        assertEquals(Set.of(DescriptorLanguage.FileType.DOCKSTORE_NOTEBOOK_REES), map(rees.values(), SourceFile::getType));
    }

    @Test
    void testProcessUserFiles() {
        Map<String, SourceFile> files = handler.processUserFiles("", List.of("/data", "/existing_file.txt", "/missing_file.txt"), version, mockRepo(Set.of("/some.ipynb", "/data/a.txt", "/data/b.txt", "/data/sub/c.txt", "/existing_file.txt")), Set.of("/data/b.txt"));
        assertEquals(Set.of("/data/a.txt", "/data/sub/c.txt", "/existing_file.txt"), files.keySet());
        assertEquals(Set.of(DescriptorLanguage.FileType.DOCKSTORE_NOTEBOOK_OTHER), map(files.values(), SourceFile::getType));
    }

    @Test
    void testGetContent() {
        assertEquals(Optional.empty(), handler.getContent("authors.ipynb", read("authors.ipynb"), Set.of(), LanguageHandlerInterface.Type.TOOLS, null));
        assertEquals(Optional.empty(), handler.getContent("authors.ipynb", read("authors.ipynb"), Set.of(), LanguageHandlerInterface.Type.DAG, null));
    }

    @Test
    void testValidateWorkflowSet() {
        final String path = "/authors.ipynb";
        final String content = read("authors.ipynb");
        notebook.setDescriptorType(DescriptorLanguage.IPYNB);
        notebook.setDescriptorTypeSubclass(DescriptorLanguageSubclass.PYTHON);

        // Well-formed.
        SourceFile file = mockSourceFile(path, content, DescriptorLanguage.FileType.DOCKSTORE_IPYNB);
        assertTrue(handler.validateWorkflowSet(Set.of(file), path, notebook).isValid());

        // Invalid JSON.
        file = mockSourceFile(path, content.replaceFirst("\\{", "["), DescriptorLanguage.FileType.DOCKSTORE_IPYNB);
        assertFalse(handler.validateWorkflowSet(Set.of(file), path, notebook).isValid());

        // Valid JSON but no "cells" field.
        file = mockSourceFile(path, content.replaceFirst("\"cells\"", "\"foo\""), DescriptorLanguage.FileType.DOCKSTORE_IPYNB);
        assertFalse(handler.validateWorkflowSet(Set.of(file), path, notebook).isValid());

        // Different programming language.
        file = mockSourceFile(path, content.replace("\"python\"", "\"julia\""), DescriptorLanguage.FileType.DOCKSTORE_IPYNB);
        assertFalse(handler.validateWorkflowSet(Set.of(file), path, notebook).isValid());
    }

    @Test
    void testValidateToolSet() {
        assertThrows(UnsupportedOperationException.class, () -> handler.validateToolSet(Set.of(), "/"));
    }

    @Test
    void testValidateTestParameterSet() {
        assertThrows(UnsupportedOperationException.class, () -> handler.validateTestParameterSet(Set.of(), "/"));
    }
}
