package io.dockstore.webservice.resources.proposedGA4GH;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.api.client.util.Charsets;
import com.google.common.io.Files;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ToolsApiExtendedServiceImplTest {

    /**
     * Tests that ES requests with long search terms fail.
     * Only requests with the "include" key or wildcards are checked for the long search term.
     * @throws IOException
     */
    @Test
    void testCheckSearchTermLimit() throws IOException {
        String searchRequestExceedsLimitMessage = "Search request exceeds limit";

        try {
            // Test a query that contains the "include" key
            File file = new File(ResourceHelpers.resourceFilePath("elasticSearchQueryInclude.json"));
            String includeESQuery = Files.asCharSource(file, Charsets.UTF_8).read();
            ToolsApiExtendedServiceImpl.checkSearchTermLimit(includeESQuery);
            fail("Should not pass search term length limit check");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getErrorMessage().contains(searchRequestExceedsLimitMessage));
        }

        try {
            // Test a query that contains wildcards.
            // The UI sends two requests with wildcards. This tests one of those requests. The wildcard parsing is the same for the other request.
            File file = new File(ResourceHelpers.resourceFilePath("elasticSearchQueryWildcard.json"));
            String wildcardESQuery = Files.asCharSource(file, Charsets.UTF_8).read();
            ToolsApiExtendedServiceImpl.checkSearchTermLimit(wildcardESQuery);
            fail("Should not pass search term length limit check");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getErrorMessage().contains(searchRequestExceedsLimitMessage));
        }

        try {
            String emptyJson = "{}";
            ToolsApiExtendedServiceImpl.checkSearchTermLimit(emptyJson);
        } catch (CustomWebApplicationException ex) {
            fail("Queries that can't be parsed for an \"include\" key or wildcards should pass");
        }
    }

    /**
     * Tests that ES requests with special characters in search terms pass.
     * Only requests with the "include" key are checked for special characters.
     * @throws IOException
     */
    @Test
    void testEscapeCharactersInSearchTerm() throws IOException {
        // Test a query without special characters in the "include" key
        File file1 = new File(ResourceHelpers.resourceFilePath("elasticSearchQueryInclude.json"));
        String query1 = Files.asCharSource(file1, Charsets.UTF_8).read();
        String result1 = ToolsApiExtendedServiceImpl.escapeCharactersInSearchTerm(query1);
        assertTrue(result1.contains("This is a long string This is a long string This is a long string This is a long string This is a long string This is a long string This is a long string This is a long string This is a long string This is a long string This is a long string This is a long string.*"));

        // Test a query without special characters in the "include" key
        File file2 = new File(ResourceHelpers.resourceFilePath("elasticSearchQueryIncludeSpecialCharacters.json"));
        String query2 = Files.asCharSource(file2, Charsets.UTF_8).read();
        String result2 = ToolsApiExtendedServiceImpl.escapeCharactersInSearchTerm(query2);
        assertTrue(result2.contains("This.str\\{i\\>ng\\(has\\#special\\]char\\@acters.*"));

        // Test an empty query
        try {
            String emptyJson = "{}";
            String result = ToolsApiExtendedServiceImpl.escapeCharactersInSearchTerm(emptyJson);
        } catch (CustomWebApplicationException ex) {
            fail("Queries that can't be parsed for an \"include\" key should pass");
        }
    }
}
