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

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.WorkflowIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ExtendedGa4GhApi;
import io.swagger.client.api.MetadataApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Workflow;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertTrue;

/**
 * @author dyuen
 */
@Category(ConfidentialTest.class)
public class SearchResourceIT extends BaseIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    public void waitForRefresh(Integer t) {
        // Elasticsearch needs time to refresh the index after update and deletion events.
        try {
            Thread.sleep(t);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSearchOperations() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);

        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        // update the search index
        extendedGa4GhApi.toolsIndexGet();
        waitForRefresh(5000);
        waitForRefresh(5000);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, null);
        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());

        workflowApi.publish(workflow.getId(), new PublishRequest() {
            public Boolean isPublish() { return false;}
        });

        waitForRefresh(1500);
        String exampleESQuery = "{\"size\":201,\"_source\":{\"excludes\":[\"*.content\",\"*.sourceFiles\",\"description\",\"users\",\"workflowVersions.dirtyBit\",\"workflowVersions.hidden\",\"workflowVersions.last_modified\",\"workflowVersions.name\",\"workflowVersions.valid\",\"workflowVersions.workflow_path\",\"workflowVersions.workingDirectory\",\"workflowVersions.reference\"]},\"query\":{\"match_all\":{}}}";
        workflowApi.publish(workflow.getId(), new PublishRequest() {
            public Boolean isPublish() { return true;}
        });

        waitForRefresh(1500);
        // after publication index should include workflow
        String s = extendedGa4GhApi.toolsIndexSearch(exampleESQuery);
        assertTrue(s.contains(WorkflowIT.DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW));
    }

    /**
     * This tests that the elastic search health check will fail if the Docker container is down, the
     * index is not made, or the index is made but there are no results.
     */
    @Test
    public void testElasticSearchHealthCheck() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        MetadataApi metadataApi = new MetadataApi(webClient);

        // Should fail with no index
        try {
            metadataApi.checkElasticSearch();
        } catch (ApiException ex) {
            assertTrue("Should fail", true);
        }

        // Update the search index
        extendedGa4GhApi.toolsIndexGet();
        waitForRefresh(5000);
        // Should still fail even with index
        try {
            metadataApi.checkElasticSearch();
        } catch (ApiException ex) {
            assertTrue("Should fail", true);
        }

        // Register and publish workflow
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, null);
        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());

        workflowApi.publish(workflow.getId(), new PublishRequest() {
            public Boolean isPublish() { return true;}
        });

        waitForRefresh(1500);

        // Should not fail since a workflow exists in index
        try {
            metadataApi.checkElasticSearch();
        } catch (ApiException ex) {
            assertTrue("Should not fail", false);
        }
    }
}
