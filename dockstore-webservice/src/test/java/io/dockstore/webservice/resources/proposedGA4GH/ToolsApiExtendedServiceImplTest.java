package io.dockstore.webservice.resources.proposedGA4GH;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.io.Files;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.Strings;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class ToolsApiExtendedServiceImplTest {

    private final String placeholderStr = "PLACEHOLDER";

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
            String includeESQuery = Files.asCharSource(file, StandardCharsets.UTF_8).read();
            ToolsApiExtendedServiceImpl.checkSearchTermLimit(includeESQuery);
            fail("Should not pass search term length limit check");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getMessage().contains(searchRequestExceedsLimitMessage));
        }

        try {
            // Test a query that contains wildcards.
            // The UI sends two requests with wildcards. This tests one of those requests. The wildcard parsing is the same for the other request.
            File file = new File(ResourceHelpers.resourceFilePath("elasticSearchQueryWildcard.json"));
            String wildcardESQuery = Files.asCharSource(file, StandardCharsets.UTF_8).read();
            ToolsApiExtendedServiceImpl.checkSearchTermLimit(wildcardESQuery);
            fail("Should not pass search term length limit check");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getMessage().contains(searchRequestExceedsLimitMessage));
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
        File file = new File(ResourceHelpers.resourceFilePath("elasticSearchQueryIncludeWithPlaceholder.json"));
        String query = Files.asCharSource(file, StandardCharsets.UTF_8).read();
        
        // Test a query without special characters in the "include" key
        String query1 = Strings.CS.replace(query, placeholderStr, "This is a normal string");
        String result1 = ToolsApiExtendedServiceImpl.escapeCharactersInSearchTerm(query1);
        assertTrue(result1.contains("This is a normal string"));

        // Test a query without special characters ending with .*
        String query2 = Strings.CS.replace(query, placeholderStr, "This is a normal string.*");
        String result2 = ToolsApiExtendedServiceImpl.escapeCharactersInSearchTerm(query2);
        assertTrue(result2.contains("This is a normal string.*"));

        // Test a query with special characters in the "include" key
        String query3 = Strings.CS.replace(query, placeholderStr, "This.str{i>ng(has#special]char@acters.");
        String result3 = ToolsApiExtendedServiceImpl.escapeCharactersInSearchTerm(query3);
        assertTrue(result3.contains("This\\\\.str\\\\{i\\\\>ng\\\\(has\\\\#special\\\\]char\\\\@acters\\\\."));

        // Test a query with special characters ending with .*
        String query4 = Strings.CS.replace(query, placeholderStr, "This.str{i>ng(has#special]char@acters.*");
        String result4 = ToolsApiExtendedServiceImpl.escapeCharactersInSearchTerm(query4);
        assertTrue(result4.contains("This\\\\.str\\\\{i\\\\>ng\\\\(has\\\\#special\\\\]char\\\\@acters.*"));

        // Test an empty query
        String query5 = "{}";
        String result5 = ToolsApiExtendedServiceImpl.escapeCharactersInSearchTerm(query5);
        assertEquals(query5, result5);
    }

    /**
     * Tests that "include" key is returned from requests.
     * @throws IOException
     */
    @Test
    void testGetSearchQueryJsonIncludeKey() throws IOException {
        File file = new File(ResourceHelpers.resourceFilePath("elasticSearchQueryIncludeWithPlaceholder.json"));
        String query = Files.asCharSource(file, StandardCharsets.UTF_8).read();

        // Test a query containing an include key
        String testString = "This is a test string";
        JSONObject testJson = new JSONObject(Strings.CS.replace(query, placeholderStr, testString));
        assertEquals(testString, ToolsApiExtendedServiceImpl.getSearchQueryJsonIncludeKey(testJson));

        // Test an empty query (no include key)
        JSONObject emptyJson = new JSONObject("{}");
        assertEquals("", ToolsApiExtendedServiceImpl.getSearchQueryJsonIncludeKey(emptyJson));
    }
}
