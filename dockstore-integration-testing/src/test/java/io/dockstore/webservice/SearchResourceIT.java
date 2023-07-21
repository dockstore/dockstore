/*
 *    Copyright 2018 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice;

import static io.dockstore.common.CommonTestUtilities.restartElasticsearch;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.SEARCH_QUERY_INVALID_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.client.cli.WorkflowIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.EntriesApi;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.MetadataApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.webservice.helpers.AppToolHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * @author dyuen
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
class SearchResourceIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private final String exampleESQuery = "{\"size\":201,\"_source\":{\"excludes\":[\"*.content\",\"*.sourceFiles\",\"description\",\"users\",\"workflowVersions.dirtyBit\",\"workflowVersions.hidden\",\"workflowVersions.last_modified\",\"workflowVersions.name\",\"workflowVersions.valid\",\"workflowVersions.workflow_path\",\"workflowVersions.workingDirectory\",\"workflowVersions.reference\"]},\"query\":{\"match_all\":{}}}";

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        restartElasticsearch();
    }

    /**
     * Continuously checks the elasticsearch index to see if it has the correct amount of entries
     * Increasing amount of sleep time, up to 15 seconds or so
     * @param hit   The amount of hits expected
     * @param extendedGa4GhApi  The api to get the elasticsearch results
     * @param counter   The amount of tries attempted
     * @param esQuery   The ES query to run
     */
    private void waitForIndexRefresh(int hit, ExtendedGa4GhApi extendedGa4GhApi, int counter, String esQuery) {
        try {
            String s = extendedGa4GhApi.toolsIndexSearch(esQuery);
            // There's actually two "total", one for shards and one for hits.
            // Need to only look at the hits one
            if (!s.contains("hits\":{\"total\":{\"value\":" + hit + ",")) {
                if (counter > 5) {
                    fail(s + " does not have the correct amount of hits");
                } else {
                    long sleepTime = 1000L * counter;
                    Thread.sleep(sleepTime);
                    waitForIndexRefresh(hit, extendedGa4GhApi, counter + 1, esQuery);
                }
            }
        } catch (Exception e) {
            fail("There were troubles sleeping: " + e.getMessage());
        }
    }

    private void waitForIndexRefresh(int hit, ExtendedGa4GhApi extendedGa4GhApi) {
        waitForIndexRefresh(hit, extendedGa4GhApi, 0, exampleESQuery);
    }

    @Test
    void testSearchOperations() throws ApiException {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        EntriesApi entriesApi = new EntriesApi(webClient);
        // update the search index
        extendedGa4GhApi.updateTheWorkflowsAndToolsIndices();
        waitForIndexRefresh(0, extendedGa4GhApi);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi
            .getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, WorkflowSubClass.BIOWORKFLOW, null);
        // do targeted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflow = workflowApi.refresh1(workflowByPathGithub.getId(), false);
        entriesApi.addAliases1(workflow.getId(), "potatoAlias");
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(false));
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));
        waitForIndexRefresh(1, extendedGa4GhApi);
        // after publication index should include workflow
        String s = extendedGa4GhApi.toolsIndexSearch(exampleESQuery);
        assertTrue(s.contains("\"aliases\":{\"potatoAlias\":{}}"), s + " should've contained potatoAlias");
        assertFalse(s.contains("\"aliases\":null"));
        assertTrue(s.contains(WorkflowIT.DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW));
        // ensure source file returns
        String newQuery = StringUtils.replace(exampleESQuery, "*.sourceFiles", "");
        String t = extendedGa4GhApi.toolsIndexSearch(newQuery);
        assertTrue(t.contains("sourceFiles") && t.contains("\"checksum\":\"cb5d0323091b22e0a1d6f52a4930ee256b15835c968462c03cf7be2cc842a4ad\""), t + " should've contained sourcefiles");
    }

    @Test
    void testNotebook() {
        // register a simple published notebook
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.handleGitHubRelease("refs/tags/simple-published-v1", AppToolHelper.INSTALLATION_ID, "dockstore-testing/simple-notebook", USER_2_USERNAME);

        // wait until the notebook is indexed
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        waitForIndexRefresh(1, extendedGa4GhApi, 0, StringUtils.replace(exampleESQuery, "\"match_all\":{}", "\"match\":{\"_index\":\"notebooks\"}"));

        // confirm the correct format and language
        String s = extendedGa4GhApi.toolsIndexSearch(exampleESQuery);
        assertTrue(s.contains("\"type\":\"Notebook\""));
        assertTrue(s.contains("\"repository\":\"simple-notebook\""));
        assertTrue(s.contains("\"descriptorType\":\"jupyter\""));
        assertTrue(s.contains("\"descriptorTypeSubclass\":\"Python\""));

        // confirm the presence of the notebook source file
        String fileQuery = StringUtils.replace(exampleESQuery, "*.sourceFiles", "");
        String t = extendedGa4GhApi.toolsIndexSearch(fileQuery);
        assertTrue(t.contains("/notebook.ipynb"));
    }

    /**
     * This tests that the elastic search health check will fail if the Docker container is down, the
     * index is not made, or the index is made but there are no results.
     */
    @Test
    void testElasticSearchHealthCheck() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        MetadataApi metadataApi = new MetadataApi(webClient);

        // Should fail with no index
        try {
            metadataApi.checkElasticSearch();
        } catch (ApiException ex) {
            assertTrue(true, "Should fail");
        }

        // Update the search index
        extendedGa4GhApi.updateTheWorkflowsAndToolsIndices();
        waitForIndexRefresh(0, extendedGa4GhApi);
        try {
            metadataApi.checkElasticSearch();
            fail("Should fail even with index because there's no hits");
        } catch (ApiException ex) {
            assertTrue(ex.getMessage().contains("Internal Server Error"));
        }

        // Register and publish workflow
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi
            .getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, WorkflowSubClass.BIOWORKFLOW, null);
        // do targeted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflow = workflowApi.refresh1(workflowByPathGithub.getId(), false);

        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        waitForIndexRefresh(1, extendedGa4GhApi);

        // Should not fail since a workflow exists in index
        try {
            metadataApi.checkElasticSearch();
        } catch (ApiException ex) {
            fail("Should not fail");
        }
    }

    /**
     * Tests that a search request with an invalid JSON request body returns a 415 error.
     */
    @Test
    void testSearchJsonRequestBody() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.ExtendedGa4GhApi extendedGa4GhApi = new io.dockstore.openapi.client.api.ExtendedGa4GhApi(webClient);
        // Test that queries with invalid JSON return a 415 code
        io.dockstore.openapi.client.ApiException exception = assertThrows(io.dockstore.openapi.client.ApiException.class, () -> extendedGa4GhApi.toolsIndexSearch("foobar"));
        assertEquals(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, exception.getCode());
        assertEquals(SEARCH_QUERY_INVALID_JSON, exception.getMessage());
        exception = assertThrows(io.dockstore.openapi.client.ApiException.class, () -> extendedGa4GhApi.toolsIndexSearch("{"));
        assertEquals(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, exception.getCode());
        assertEquals(SEARCH_QUERY_INVALID_JSON, exception.getMessage());
        exception = assertThrows(io.dockstore.openapi.client.ApiException.class, () -> extendedGa4GhApi.toolsIndexSearch("{\"aggs\":}"));
        assertEquals(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, exception.getCode());
        assertEquals(SEARCH_QUERY_INVALID_JSON, exception.getMessage());
    }
}
