/*
 * Copyright 2023 OICR, UCSC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    /*
    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());
    */

    private static final String PATH = "/hello.ipynb";
    private static final String CONTENT = read("hello.ipynb");

    private IpynbHandler handler;
    private Notebook notebook;
    private WorkflowVersion version;
  
    private static String read(String resourceName) {
        try {
            return FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("notebooks/ipynb/" + resourceName)));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String deleteMatchingLines(String content, String match) {
        return String.join("\n", List.of(content.split("\n")).stream().filter(line -> !line.contains(match)).toList());
    }

    private <T, F> Set<F> mapToSet(Collection<T> values, Function<T, F> mapper) {
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
    void reset() {
        handler = new IpynbHandler();
        notebook = new Notebook();
        version = new WorkflowVersion();
    }

    @Test
    void testParseWorkflowContentExtractAuthors() {
        // No authors.
        handler.parseWorkflowContent(PATH, CONTENT.replace("authors", "foo"), Set.of(), version);
        assertTrue(version.getAuthors().isEmpty());

        // Empty "authors" list.
        reset();
        handler.parseWorkflowContent(PATH, deleteMatchingLines(CONTENT, "Author"), Set.of(), version);
        assertTrue(version.getAuthors().isEmpty());

        // One author.
        reset();
        handler.parseWorkflowContent(PATH, deleteMatchingLines(CONTENT, "Author Two"), Set.of(), version);
        assertEquals(Set.of("Author One"), mapToSet(version.getAuthors(), Author::getName));

        // Two authors.
        reset();
        handler.parseWorkflowContent(PATH, CONTENT, Set.of(), version);
        assertEquals(Set.of("Author One", "Author Two"), mapToSet(version.getAuthors(), Author::getName));
    }

    @Test
    void testParseWorkflowContentExtractVersion() {
        SourceFile file = mockSourceFile(PATH, CONTENT, DescriptorLanguage.FileType.DOCKSTORE_IPYNB);
        handler.parseWorkflowContent(PATH, CONTENT, Set.of(file), version);
        assertEquals("4.0", file.getTypeVersion());
        assertEquals(List.of("4.0"), version.getDescriptorTypeVersions());
    }

    @Test
    void testProcessImportsNoRees() {
        Map<String, SourceFile> rees = handler.processImports("", "", version, mockRepo(Set.of(PATH)), "/");
        assertTrue(rees.keySet().isEmpty());
    }

    @Test
    void testProcessImportsRootRequirementsTxt() {
        Map<String, SourceFile> rees = handler.processImports("", "", version, mockRepo(Set.of(PATH, "/requirements.txt", "/blah/requirements.txt")), "/");
        assertEquals(Set.of("/requirements.txt"), rees.keySet());
        assertEquals(Set.of(DescriptorLanguage.FileType.DOCKSTORE_NOTEBOOK_REES), mapToSet(rees.values(), SourceFile::getType));
    }

    @Test
    void testProcessUserFiles() {
        Map<String, SourceFile> files = handler.processUserFiles("", List.of("/data", "/existing_file.txt", "/missing_file.txt"), version, mockRepo(Set.of(PATH, "/data/a.txt", "/data/b.txt", "/data/sub/c.txt", "/existing_file.txt")), Set.of("/data/b.txt"));
        assertEquals(Set.of("/data/a.txt", "/data/sub/c.txt", "/existing_file.txt"), files.keySet());
        assertEquals(Set.of(DescriptorLanguage.FileType.DOCKSTORE_NOTEBOOK_OTHER), mapToSet(files.values(), SourceFile::getType));
    }

    @Test
    void testGetContent() {
        assertEquals(Optional.empty(), handler.getContent(PATH, CONTENT, Set.of(), LanguageHandlerInterface.Type.TOOLS, null));
        assertEquals(Optional.empty(), handler.getContent(PATH, CONTENT, Set.of(), LanguageHandlerInterface.Type.DAG, null));
    }

    @Test
    void testValidateWorkflowSet() {
        notebook.setDescriptorType(DescriptorLanguage.IPYNB);
        notebook.setDescriptorTypeSubclass(DescriptorLanguageSubclass.PYTHON);

        // Well-formed.
        SourceFile file = mockSourceFile(PATH, CONTENT, DescriptorLanguage.FileType.DOCKSTORE_IPYNB);
        assertTrue(handler.validateWorkflowSet(Set.of(file), PATH, notebook).isValid());

        // Invalid JSON syntax.
        file = mockSourceFile(PATH, CONTENT.replaceFirst("\\{", "["), DescriptorLanguage.FileType.DOCKSTORE_IPYNB);
        assertFalse(handler.validateWorkflowSet(Set.of(file), PATH, notebook).isValid());

        // Valid JSON but no "metadata" field.
        file = mockSourceFile(PATH, CONTENT.replaceFirst("\"metadata\"", "\"foo\""), DescriptorLanguage.FileType.DOCKSTORE_IPYNB);
        assertFalse(handler.validateWorkflowSet(Set.of(file), PATH, notebook).isValid());

        // Valid JSON but no "cells" field.
        file = mockSourceFile(PATH, CONTENT.replaceFirst("\"cells\"", "\"foo\""), DescriptorLanguage.FileType.DOCKSTORE_IPYNB);
        assertFalse(handler.validateWorkflowSet(Set.of(file), PATH, notebook).isValid());

        // Valid JSON but no "nbformat" field.
        file = mockSourceFile(PATH, CONTENT.replaceFirst("\"nbformat\"", "\"foo\""), DescriptorLanguage.FileType.DOCKSTORE_IPYNB);
        assertFalse(handler.validateWorkflowSet(Set.of(file), PATH, notebook).isValid());

        // Valid JSON but non-integer "nbformat" field.
        file = mockSourceFile(PATH, CONTENT.replaceFirst("4,", "\"foo\","), DescriptorLanguage.FileType.DOCKSTORE_IPYNB);
        assertFalse(handler.validateWorkflowSet(Set.of(file), PATH, notebook).isValid());

        // Different programming language.
        file = mockSourceFile(PATH, CONTENT.replace("\"python\"", "\"julia\""), DescriptorLanguage.FileType.DOCKSTORE_IPYNB);
        assertFalse(handler.validateWorkflowSet(Set.of(file), PATH, notebook).isValid());
    }

    @Test
    void testValidateToolSet() {
        assertThrows(UnsupportedOperationException.class, () -> handler.validateToolSet(Set.of(), "/"));
    }

    @Test
    void testValidateTestParameterSet() {
        assertThrows(UnsupportedOperationException.class, () -> handler.validateTestParameterSet(Set.of()));
    }
}
