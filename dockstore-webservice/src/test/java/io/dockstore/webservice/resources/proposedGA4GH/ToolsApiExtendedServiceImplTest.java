package io.dockstore.webservice.resources.proposedGA4GH;

import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.SEARCH_QUERY_INVALID_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.api.client.util.Charsets;
import com.google.common.io.Files;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import org.apache.http.HttpStatus;
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

        // Test that queries with invalid JSON return a 400
        CustomWebApplicationException exception = assertThrows(CustomWebApplicationException.class, () -> ToolsApiExtendedServiceImpl.checkSearchTermLimit("foobar"));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getResponse().getStatus());
        assertEquals(SEARCH_QUERY_INVALID_JSON, exception.getErrorMessage());
        exception = assertThrows(CustomWebApplicationException.class, () -> ToolsApiExtendedServiceImpl.checkSearchTermLimit("{"));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getResponse().getStatus());
        assertEquals(SEARCH_QUERY_INVALID_JSON, exception.getErrorMessage());
        exception = assertThrows(CustomWebApplicationException.class, () -> ToolsApiExtendedServiceImpl.checkSearchTermLimit("{\"aggs\":}"));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getResponse().getStatus());
        assertEquals(SEARCH_QUERY_INVALID_JSON, exception.getErrorMessage());
    }
}
