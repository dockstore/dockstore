/*
 *    Copyright 2017 OICR
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

package io.dockstore.client.cli;

import java.util.List;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.SourceControl;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Workflow;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;
import static org.junit.Assert.assertTrue;

/**
 * Confidential tests for checker workflows
 *
 * @author agduncan
 */
@Category(ConfidentialTest.class)
public class CheckerWorkflowIT extends BaseIT {

    private static final String DOCKSTORE_TEST_USER2_MD5SUM = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum";

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    @Test
    public void testToolAddCheckerRefreshPublishUnpublish() throws ApiException {
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Manually register a tool

        // Refresh the tool

        // Add checker workflow

        // Refresh tool

        // Checker workflow should refresh

        // Publish tool

        // Checker workflow should publish

        // Unpublish tool

        // Checker workflow should unpublish
    }

    /**
     * This tests the process of adding a checker workflow to a workflow entry and that some calls to the workflow will also trigger the calls to the checker
     * - Refresh workflow should refresh checker
     * - Publish workflow should publish checker
     * - Unpublish workflow should unpublish checker
     * @throws ApiException
     */
    @Test
    public void testWorkflowAddCheckerRefreshPublishUnpublish() throws ApiException {
        // Manually register a workflow
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        final PublishRequest unpublishRequest = SwaggerUtility.createPublishRequest(false);

        // Set up postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        usersApi.refreshWorkflows(userId);

        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl", "altname", "cwl", "/test.json");

        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("No workflows are in full mode", count == 0);

        // Refresh the workflow
        workflowApi.refresh(githubWorkflow.getId());

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("One workflow should be full", count2 == 1);

        // Add checker workflow
        workflowApi.registerCheckerWorkflow("checker_workflow_wrapping_workflow.cwl", githubWorkflow.getId(), "cwl", null);

        // Refresh workflow
        workflowApi.refresh(githubWorkflow.getId());

        // Checker workflow should refresh
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("Two workflows should be full (one being the checker)", count3 == 2);

        // Publish workflow
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from workflow where ispublished = true", new ScalarHandler<>());
        assertTrue("No workflows should be published", count4 == 0);
        workflowApi.publish(githubWorkflow.getId(), publishRequest);

        // Checker workflow should publish
        final long count5 = testingPostgres
            .runSelectStatement("select count(*) from workflow where ispublished = true", new ScalarHandler<>());
        assertTrue("Two workflows should be published (one being the checker)", count5 == 2);

        // Unpublish workflow
        workflowApi.publish(githubWorkflow.getId(), unpublishRequest);

        // Checker workflow should unpublish
        final long count6 = testingPostgres
            .runSelectStatement("select count(*) from workflow where ispublished = true", new ScalarHandler<>());
        assertTrue("No workflows should be published", count6 == 0);
    }

}
