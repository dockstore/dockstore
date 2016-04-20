/*
 *    Copyright 2016 OICR
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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.Constants;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;

import static io.dockstore.common.CommonTestUtilities.clearStateMakePrivate2;
import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;
import static org.junit.Assert.assertTrue;

/**
 * Extra confidential integration tests, focus on testing workflow interactions
 * 
 * @author dyuen
 */
public class WorkflowET {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstoreTest.yml"));
    private static final String DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW = "DockstoreTestUser2/hello-dockstore-workflow";
    private static final String DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW = "dockstore_testuser2/dockstore-workflow";

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Before
    public void clearDBandSetup() throws IOException, TimeoutException {
        clearStateMakePrivate2();
    }

    public static ApiClient getWebClient() throws IOException, TimeoutException {
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        File configFile = FileUtils.getFile("src", "test", "resources", "config2");
        HierarchicalINIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        ApiClient client = new ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        client.addDefaultHeader(
                "Authorization",
                "Bearer "
                        + (testingPostgres.runSelectStatement("select content from token where tokensource='dockstore';",
                                new ScalarHandler<>())));
        return client;
    }

    @Test
    public void testStubRefresh() throws IOException, TimeoutException, ApiException {
        // need to promote user to admin to refresh all stubs
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final List<Workflow> workflows = workflowApi.refreshAll();
        assertTrue("workflow size was " + workflows.size(), workflows.size() > 1);
        assertTrue("found non stub workflows "
                + workflows.stream().filter(workflow -> workflow.getMode() != Workflow.ModeEnum.STUB).count(),
                workflows.stream().allMatch(workflow -> workflow.getMode() == Workflow.ModeEnum.STUB));
    }

    @Test
    public void testTargettedRefresh() throws IOException, TimeoutException, ApiException {
        // need to promote user to admin to refresh all stubs
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.refreshAll();

        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());
        final Workflow workflowByPathBitbucket = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW);
        final Workflow refreshBitbucket = workflowApi.refresh(workflowByPathBitbucket.getId());

        assertTrue("github workflow is not in full mode", refreshGithub.getMode() == Workflow.ModeEnum.FULL);
        assertTrue("github workflow version count is wrong: " + refreshGithub.getWorkflowVersions().size(), refreshGithub.getWorkflowVersions().size() == 4);
        assertTrue(
                "should find two versions with files for github workflow, found : "
                        + refreshGithub.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty())
                                .count(),
                refreshGithub.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count() == 2);
        assertTrue("should find two valid versions for github workflow, found : "
                + refreshGithub.getWorkflowVersions().stream().filter(WorkflowVersion::getValid).count(), refreshGithub.getWorkflowVersions().stream()
                .filter(WorkflowVersion::getValid).count() == 2);

        assertTrue("bitbucket workflow is not in full mode", refreshBitbucket.getMode() == Workflow.ModeEnum.FULL);

        assertTrue("bitbucket workflow version count is wrong: " + refreshBitbucket.getWorkflowVersions().size(), refreshBitbucket.getWorkflowVersions().size() == 5);
        assertTrue(
                "should find 4 versions with files for bitbucket workflow, found : "
                        + refreshBitbucket.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty())
                        .count(),
                refreshBitbucket.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count() == 4);
        assertTrue("should find 4 valid versions for bitbucket workflow, found : "
                + refreshBitbucket.getWorkflowVersions().stream().filter(WorkflowVersion::getValid).count(), refreshBitbucket.getWorkflowVersions().stream()
                .filter(WorkflowVersion::getValid).count() == 4);
    }

    /**
     * This test checks that a user can successfully refresh their workflows (only stubs)
     * @throws IOException
     * @throws TimeoutException
     * @throws ApiException
         */
    @Test
    public void testRefreshAllForAUser() throws IOException, TimeoutException, ApiException {
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        long userId = 1;

        final ApiClient webClient = getWebClient();
        UsersApi usersApi = new UsersApi(webClient);
        final List<Workflow> workflow = usersApi.refreshWorkflows(userId);

        // Check that there are multiple workflows
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow", new ScalarHandler<>());
        assertTrue("Workflow entries should exist", count > 0);

        // Check that there are only stubs (no workflow version)
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion", new ScalarHandler<>());
        assertTrue("No entries in workflowversion", count2 == 0);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("No workflows are in full mode", count3 == 0);

    }

    /**
     * This test does not use admin rights, note that a number of operations go through the UserApi to get this to work
     * @throws IOException
     * @throws TimeoutException
     * @throws ApiException
     */
    @Test
    public void testPublishingAndListingOfPublished() throws IOException, TimeoutException, ApiException {
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // should start with nothing published
        assertTrue("should start with nothing published " , workflowApi.allPublishedWorkflows().isEmpty());
        // refresh just for the current user
        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();
        usersApi.refreshWorkflows(userId);
        assertTrue("should remain with nothing published " , workflowApi.allPublishedWorkflows().isEmpty());
       // assertTrue("should have a bunch of stub workflows: " +  usersApi..allWorkflows().size(), workflowApi.allWorkflows().size() == 4);

        final Workflow workflowByPath = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW);
        // refresh targeted
        workflowApi.refresh(workflowByPath.getId());

        // publish one
        final PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublish(true);
        workflowApi.publish(workflowByPath.getId(), publishRequest);
        assertTrue("should have one published, found  " + workflowApi.allPublishedWorkflows().size(), workflowApi.allPublishedWorkflows().size() == 1);
        final Workflow publishedWorkflow = workflowApi.getPublishedWorkflow(workflowByPath.getId());
        assertTrue("did not get published workflow", publishedWorkflow != null);
        final Workflow publishedWorkflowByPath = workflowApi.getPublishedWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW);
        assertTrue("did not get published workflow", publishedWorkflowByPath != null);
    }

    /**
     * Tests manual registration and publishing of a github and bitbucket workflow
     * @throws IOException
     * @throws TimeoutException
     * @throws ApiException
         */
    @Test
    public void testManualRegisterThenPublish() throws IOException, TimeoutException, ApiException {
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublish(true);

        // Set up postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Get workflows
        usersApi.refreshWorkflows(userId);

        // Manually register workflow github
        Workflow githubWorkflow = workflowApi.manualRegister("github", DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, "/Dockstore.wdl", "altname", "wdl");

        // Manually register workflow bitbucket
        Workflow bitbucketWorkflow = workflowApi.manualRegister("bitbucket", DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW, "/Dockstore.cwl", "altname", "cwl");

        // Assert some things
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("No workflows are in full mode", count == 0);
        final long count2= testingPostgres.runSelectStatement("select count(*) from workflow where workflowname = 'altname'", new ScalarHandler<>());
        assertTrue("There should be two workflows with name altname, there are " + count2, count2 == 2);

        // Publish github workflow
        workflowApi.refresh(githubWorkflow.getId());
        workflowApi.publish(githubWorkflow.getId(), publishRequest);

        // Publish bitbucket workflow
        workflowApi.refresh(bitbucketWorkflow.getId());
        workflowApi.publish(bitbucketWorkflow.getId(), publishRequest);

        // Assert some things
        assertTrue("should have two published, found  " + workflowApi.allPublishedWorkflows().size(), workflowApi.allPublishedWorkflows().size() == 2);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("Two workflows are in full mode", count3 == 2);
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid = 't'", new ScalarHandler<>());
        assertTrue("There should be 5 valid version tags, there are " + count4, count4 == 6);
    }

    /**
     * Tests that trying to register a duplicate workflow fails, and that registering a non-existant repository failes
     * @throws ApiException
     * @throws IOException
     * @throws TimeoutException
         */
    @Test
    public void testManualRegisterErrors() throws ApiException, IOException, TimeoutException {
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Get workflows
        usersApi.refreshWorkflows(userId);

        // Manually register workflow
        boolean success = true;
        try {
            workflowApi.manualRegister("github", DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, "/Dockstore.wdl", "", "wdl");
        } catch (ApiException c) {
            success = false;
        } finally {
            assertTrue("The workflow cannot be registered as it is a duplicate.", !success);
        }

        success = true;
        try {
            workflowApi.manualRegister("github", "dasn/iodnasiodnasio", "/Dockstore.wdl", "", "wdl");
        } catch (ApiException c) {
            success = false;
        } finally {
            assertTrue("The workflow cannot be registered as the repository doesn't exist.", !success);
        }
    }
}
