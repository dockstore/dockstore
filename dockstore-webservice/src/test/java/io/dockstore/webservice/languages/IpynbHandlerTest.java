// TODO add copyright
package io.dockstore.webservice.languages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DockerImageReference;
import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.DescriptionSource;
import io.dockstore.webservice.core.FileFormat;
import io.dockstore.webservice.core.ParsedInformation;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.languages.LanguageHandlerInterface.DockerSpecifier;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
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

    private IpynbHandler handler;
    private WorkflowVersion version;
  
    @BeforeEach
    void init() {
        handler = new IpynbHandler();
        version = new WorkflowVersion();
    }

    private String read(String resourceName) {
        try {
            return FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("notebooks/ipynb/" + resourceName)));
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String deleteMatchingLines(String content, String match) {
        return String.join("\n", List.of(content.split("\n")).stream().filter(line -> !line.contains(match)).toList());
    }

    private <T, F> Set<F> map(Set<T> set, Function<T, F> mapper) {
        return set.stream().map(mapper).collect(Collectors.toSet());
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
    void testProcessImports() {
    }

    @Test
    void testProcessUserFiles() {
    }

    @Test
    void testGetContent() {
    }

    @Test
    void testValidateWorkflowSet() {
    }

    @Test
    void testValidateToolSet() {
    }

    @Test
    void testValidateTestParameterSet() {
    }
}
