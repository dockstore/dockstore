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

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Registry;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
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

    /**
     * This tests the process of adding a checker workflow to a tool entry and that some calls to the tool will also trigger the calls to the checker
     * - Refresh tool should refresh checker
     * - Publish tool should publish checker
     * - Unpublish tool should unpublish checker
     * @throws ApiException
     */
    @Test
    public void testToolAddCheckerRefreshPublishUnpublish() throws ApiException {
        // Setup for test
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        ContainersApi containersApi = new ContainersApi(webClient);

        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        final PublishRequest unpublishRequest = SwaggerUtility.createPublishRequest(false);

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Manually register a tool
        DockstoreTool newTool = new DockstoreTool();
        newTool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        newTool.setName("md5sum");
        newTool.setGitUrl("git@github.com:DockstoreTestUser2/md5sum-checker.git");
        newTool.setDefaultDockerfilePath("/md5sum/Dockerfile");
        newTool.setDefaultCwlPath("/md5sum/md5sum-tool.cwl");
        newTool.setRegistryString(Registry.QUAY_IO.toString());
        newTool.setNamespace("dockstoretestuser2");
        newTool.setToolname("altname");
        newTool.setPrivateAccess(false);
        newTool.setDefaultCWLTestParameterFile("/testcwl.json");
        DockstoreTool githubTool = containersApi.registerManual(newTool);

        // Refresh the workflow
        containersApi.refresh(githubTool.getId());

        // Add checker workflow
        workflowApi.registerCheckerWorkflow("/checker_workflow_wrapping_tool.cwl", githubTool.getId(), "cwl", null);

        // Refresh workflow
        containersApi.refresh(githubTool.getId());

        // Checker workflow should refresh
        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("The checker workflow should be full, there are " + count, count == 1);

        // Checker workflow should have the same test path as entry
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflow where defaulttestparameterfilepath = '/testcwl.json'", new ScalarHandler<>());
        assertTrue("The checker workflow should have the correct default test path /testcwl.json, there are " + count2, count2 == 1);

        // Checker workflow should have the correct workflow path
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from workflow where sourcecontrol = 'github.com' and organization = 'DockstoreTestUser2' and repository = 'md5sum-checker' and workflowname like 'altname%' and giturl = 'git@github.com:DockstoreTestUser2/md5sum-checker.git'", new ScalarHandler<>());
        assertTrue("The checker workflow should have the correct path information, there are " + count3, count3 == 1);

        // Publish workflow
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from workflow where ispublished = true", new ScalarHandler<>());
        assertTrue("No workflows should be published, there are " + count4, count4 == 0);

        final long count5 = testingPostgres
            .runSelectStatement("select count(*) from tool where ispublished = true", new ScalarHandler<>());
        assertTrue("No tools should be published, there are " + count5, count5 == 0);

        containersApi.publish(githubTool.getId(), publishRequest);

        // Checker workflow should publish
        final long count6 = testingPostgres
            .runSelectStatement("select count(*) from workflow where ispublished = true", new ScalarHandler<>());
        assertTrue("The checker workflow should be published, there are " + count6, count6 == 1);

        final long count7 = testingPostgres
            .runSelectStatement("select count(*) from tool where ispublished = true", new ScalarHandler<>());
        assertTrue("the tool should be published, there are " + count7, count7 == 1);

        // Unpublish workflow
        containersApi.publish(githubTool.getId(), unpublishRequest);

        // Checker workflow should unpublish
        final long count8 = testingPostgres
            .runSelectStatement("select count(*) from workflow where ispublished = true", new ScalarHandler<>());
        assertTrue("Checker workflow should not be published, there are " + count8, count8 == 0);

        final long count9 = testingPostgres
            .runSelectStatement("select count(*) from tool where ispublished = true", new ScalarHandler<>());
        assertTrue("the tool should not be published, there are " + count9, count9 == 0);
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
        // Setup for test
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        final PublishRequest unpublishRequest = SwaggerUtility.createPublishRequest(false);

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Manually register a workflow
        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl", "altname", "cwl", "/testcwl.json");

        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("No workflows are in full mode, there are " + count, count == 0);

        // Refresh the workflow
        workflowApi.refresh(githubWorkflow.getId());

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("One workflow should be full, there are " + count2, count2 == 1);

        // Add checker workflow
        workflowApi.registerCheckerWorkflow("/checker_workflow_wrapping_workflow.cwl", githubWorkflow.getId(), "cwl", null);

        // Refresh workflow
        workflowApi.refresh(githubWorkflow.getId());

        // Checker workflow should refresh
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("Two workflows should be full (one being the checker), there are " + count3, count3 == 2);

        // Checker workflow should have the same test path as entry
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from workflow where defaulttestparameterfilepath = '/testcwl.json'", new ScalarHandler<>());
        assertTrue("There should be two workflows with default test parameter file path of /testcwl.json, there are " + count4, count4 == 2);

        // Checker workflow should have the correct workflow path
        final long count5 = testingPostgres
            .runSelectStatement("select count(*) from workflow where sourcecontrol = 'github.com' and organization = 'DockstoreTestUser2' and repository = 'md5sum-checker' and workflowname like 'altname%' and giturl = 'git@github.com:DockstoreTestUser2/md5sum-checker.git'", new ScalarHandler<>());
        assertTrue("There should be two workflows with similar paths, tehre are " + count5, count5 == 2);

        // Publish workflow
        final long count6 = testingPostgres
            .runSelectStatement("select count(*) from workflow where ispublished = true", new ScalarHandler<>());
        assertTrue("No workflows should be published, there are " + count6, count6 == 0);
        workflowApi.publish(githubWorkflow.getId(), publishRequest);

        // Checker workflow should publish
        final long count7 = testingPostgres
            .runSelectStatement("select count(*) from workflow where ispublished = true", new ScalarHandler<>());
        assertTrue("Two workflows should be published (one being the checker), there are " + count7, count7 == 2);

        // Unpublish workflow
        workflowApi.publish(githubWorkflow.getId(), unpublishRequest);

        // Checker workflow should unpublish
        final long count8 = testingPostgres
            .runSelectStatement("select count(*) from workflow where ispublished = true", new ScalarHandler<>());
        assertTrue("No workflows should be published, there are " + count8, count8 == 0);
    }

    /**
     * Should not be able to add a checker workflow to a stub workflow (Should fail)
     * @throws ApiException
     */
    @Test(expected = ApiException.class)
    public void testAddCheckerToStub() throws ApiException {
        // Setup for test
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Manually register a workflow
        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl", "altname", "cwl", "/testcwl.json");

        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("No workflows are in full mode, there are " + count, count == 0);

        // Add checker workflow
        workflowApi.registerCheckerWorkflow("checker_workflow_wrapping_workflow.cwl", githubWorkflow.getId(), "cwl", null);
    }

    /**
     * Tests that you cannot register a tool with an underscore
     * @throws ApiException
     */
    @Test(expected = ApiException.class)
    public void testRegisteringToolWithUnderscoreInName() throws ApiException {
        // Setup for test
        final ApiClient webClient = getWebClient();
        ContainersApi containersApi = new ContainersApi(webClient);
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Make tool
        DockstoreTool newTool = new DockstoreTool();
        newTool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        newTool.setName("md5sum");
        newTool.setGitUrl("git@github.com:DockstoreTestUser2/md5sum-checker.git");
        newTool.setDefaultDockerfilePath("/md5sum/Dockerfile");
        newTool.setDefaultCwlPath("/md5sum/md5sum-tool.cwl");
        newTool.setRegistryString(Registry.QUAY_IO.toString());
        newTool.setNamespace("dockstoretestuser2");
        newTool.setToolname("_altname");
        newTool.setPrivateAccess(false);
        newTool.setDefaultCWLTestParameterFile("/testcwl.json");

        // Register the tool
        DockstoreTool githubTool = containersApi.registerManual(newTool);
    }

    /**
     * Tests that you cannot register a workflow with an underscore
     * @throws ApiException
     */
    @Test(expected = ApiException.class)
    public void testRegisteringWorkflowWithUnderscoreInName() throws ApiException {
        // Setup for test
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // Manually register a workflow
        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl", "_altname", "cwl", "/testcwl.json");

    }

}
