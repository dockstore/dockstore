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

    @Test
    public void testSearchOperations() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);

        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        // update the search index
        extendedGa4GhApi.toolsIndexGet();

        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, null);
        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());

        workflowApi.publish(workflow.getId(), new PublishRequest() {
            public Boolean isPublish() { return false;}
        });
        String exampleESQuery = "{\"size\":201,\"_source\":{\"excludes\":[\"*.content\",\"*.sourceFiles\",\"description\",\"users\",\"workflowVersions.dirtyBit\",\"workflowVersions.hidden\",\"workflowVersions.last_modified\",\"workflowVersions.name\",\"workflowVersions.valid\",\"workflowVersions.workflow_path\",\"workflowVersions.workingDirectory\",\"workflowVersions.reference\"]},\"query\":{\"match_all\":{}}}";
        workflowApi.publish(workflow.getId(), new PublishRequest() {
            public Boolean isPublish() { return true;}
        });
        // after publication index should include workflow
        String s = extendedGa4GhApi.toolsIndexSearch(exampleESQuery);
        assertTrue(s.contains(WorkflowIT.DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW));
    }
}
