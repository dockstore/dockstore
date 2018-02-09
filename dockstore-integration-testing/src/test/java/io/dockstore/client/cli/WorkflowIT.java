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

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.IntegrationTest;
import io.dockstore.common.SourceControl;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Extra confidential integration tests, focus on testing workflow interactions
 *
 * @author dyuen
 */
@Category({ConfidentialTest.class, IntegrationTest.class})
public class WorkflowIT extends BaseIT {

    private static final String DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow";
    private static final String DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW = SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow";
    private static final String DOCKSTORE_TEST_USER2_IMPORTS_DOCKSTORE_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore-whalesay-imports";
    private static final String DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore_workflow_cnv";
    private static final String DOCKSTORE_TEST_USER2_NEXTFLOW_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/rnatoy";

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
    public void testStubRefresh() throws ApiException {
        // need to promote user to admin to refresh all stubs
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final List<Workflow> workflows = workflowApi.refreshAll();

        for (Workflow workflow: workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        assertTrue("workflow size was " + workflows.size(), workflows.size() > 1);
        assertTrue(
                "found non stub workflows " + workflows.stream().filter(workflow -> workflow.getMode() != Workflow.ModeEnum.STUB).count(),
                workflows.stream().allMatch(workflow -> workflow.getMode() == Workflow.ModeEnum.STUB));
    }

    @Test
    public void testTargettedRefresh() throws ApiException {
        // need to promote user to admin to refresh all stubs
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        final List<Workflow> workflows = workflowApi.refreshAll();

        for (Workflow workflow: workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());
        final Workflow workflowByPathBitbucket = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW);
        final Workflow refreshBitbucket = workflowApi.refresh(workflowByPathBitbucket.getId());

        assertTrue("github workflow is not in full mode", refreshGithub.getMode() == Workflow.ModeEnum.FULL);
        assertTrue("github workflow version count is wrong: " + refreshGithub.getWorkflowVersions().size(),
                refreshGithub.getWorkflowVersions().size() == 4);
        assertTrue("should find two versions with files for github workflow, found : " + refreshGithub.getWorkflowVersions().stream()
                        .filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count(),
                refreshGithub.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count()
                        == 2);
        assertTrue("should find two valid versions for github workflow, found : " + refreshGithub.getWorkflowVersions().stream()
                        .filter(WorkflowVersion::isValid).count(),
                refreshGithub.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).count() == 2);

        assertTrue("bitbucket workflow is not in full mode", refreshBitbucket.getMode() == Workflow.ModeEnum.FULL);

        assertTrue("bitbucket workflow version count is wrong: " + refreshBitbucket.getWorkflowVersions().size(),
                refreshBitbucket.getWorkflowVersions().size() == 5);
        assertTrue("should find 4 versions with files for bitbucket workflow, found : " + refreshBitbucket.getWorkflowVersions().stream()
                        .filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count(),
                refreshBitbucket.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty())
                        .count() == 4);
        assertTrue("should find 4 valid versions for bitbucket workflow, found : " + refreshBitbucket.getWorkflowVersions().stream()
                        .filter(WorkflowVersion::isValid).count(),
                refreshBitbucket.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).count() == 4);
    }

    @Test
    public void testNextFlowRefresh() throws ApiException {
        // need to promote user to admin to refresh all stubs
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        final List<Workflow> workflows = workflowApi.refreshAll();

        for (Workflow workflow: workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_NEXTFLOW_WORKFLOW);
        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(AbstractEntryClient.Type.NEXTFLOW.toString());
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_NEXTFLOW_WORKFLOW);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());

        assertTrue("github workflow is not in full mode", refreshGithub.getMode() == Workflow.ModeEnum.FULL);
        assertTrue("github workflow version count is wrong: " + refreshGithub.getWorkflowVersions().size(),
            refreshGithub.getWorkflowVersions().size() == 12);
        assertTrue("should find 12 versions with files for github workflow, found : " + refreshGithub.getWorkflowVersions().stream()
                .filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count(),
            refreshGithub.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count()
                == 12);
        assertTrue("should find 12 valid versions for github workflow, found : " + refreshGithub.getWorkflowVersions().stream()
                .filter(WorkflowVersion::isValid).count(),
            refreshGithub.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).count() == 12);

        // nextflow version should have
        assertTrue("should find 2 files for each version for now: " + refreshGithub.getWorkflowVersions().stream()
                .filter(workflowVersion -> workflowVersion.getSourceFiles().size() != 2).count(),
            refreshGithub.getWorkflowVersions().stream().noneMatch(workflowVersion -> workflowVersion.getSourceFiles().size() != 2));
    }

    /**
     * This test checks that a user can successfully refresh their workflows (only stubs)
     *
     * @throws ApiException
     */
    @Test
    public void testRefreshAllForAUser() throws ApiException {
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
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("No workflows are in full mode", count3 == 0);

        // check that a nextflow workflow made it
        long nfWorkflowCount = workflow.stream().filter(w -> w.getGitUrl().contains("mta-nf")).count();
        assertTrue("Nextflow workflow not found", nfWorkflowCount > 0);
        Workflow mtaNf = workflow.stream().filter(w -> w.getGitUrl().contains("mta-nf")).findFirst().get();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        mtaNf.setWorkflowPath("/nextflow.config");
        mtaNf.setDescriptorType(SourceFile.TypeEnum.NEXTFLOW.toString());
        workflowApi.updateWorkflow(mtaNf.getId(), mtaNf);
        workflowApi.refresh(mtaNf.getId());
        mtaNf = workflowApi.getWorkflow(mtaNf.getId());
        assertTrue("Nextflow workflow not found after update", mtaNf != null);
        assertTrue("nextflow workflow should have at least two versions", mtaNf.getWorkflowVersions().size() >= 2);
        int numOfSourceFiles = mtaNf.getWorkflowVersions().stream().mapToInt(version -> version.getSourceFiles().size()).sum();
        assertTrue("nextflow workflow should have at least two sourcefiles", numOfSourceFiles >= 2);
        long scriptCount = mtaNf.getWorkflowVersions().stream()
            .mapToLong(version -> version.getSourceFiles().stream().filter(file -> file.getType() == SourceFile.TypeEnum.NEXTFLOW).count()).sum();
        long configCount = mtaNf.getWorkflowVersions().stream()
            .mapToLong(version -> version.getSourceFiles().stream().filter(file -> file.getType() == SourceFile.TypeEnum.NEXTFLOW_CONFIG).count()).sum();
        assertTrue("nextflow workflow should have at least one config file and one script file", scriptCount >= 1 && configCount >= 1);

    }

    /**
     * This test does not use admin rights, note that a number of operations go through the UserApi to get this to work
     *
     * @throws ApiException
     */
    @Test
    public void testPublishingAndListingOfPublished() throws ApiException {
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // should start with nothing published
        assertTrue("should start with nothing published ", workflowApi.allPublishedWorkflows().isEmpty());
        // refresh just for the current user
        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();
        usersApi.refreshWorkflows(userId);
        assertTrue("should remain with nothing published ", workflowApi.allPublishedWorkflows().isEmpty());
        // assertTrue("should have a bunch of stub workflows: " +  usersApi..allWorkflows().size(), workflowApi.allWorkflows().size() == 4);

        final Workflow workflowByPath = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW);
        // refresh targeted
        workflowApi.refresh(workflowByPath.getId());

        // publish one
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish(workflowByPath.getId(), publishRequest);
        assertTrue("should have one published, found  " + workflowApi.allPublishedWorkflows().size(),
                workflowApi.allPublishedWorkflows().size() == 1);
        final Workflow publishedWorkflow = workflowApi.getPublishedWorkflow(workflowByPath.getId());
        assertTrue("did not get published workflow", publishedWorkflow != null);
        final Workflow publishedWorkflowByPath = workflowApi.getPublishedWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW);
        assertTrue("did not get published workflow", publishedWorkflowByPath != null);
    }

    /**
     * Tests manual registration and publishing of a github and bitbucket workflow
     *
     * @throws ApiException
     */
    @Test
    public void testManualRegisterThenPublish() throws ApiException {
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);

        // Set up postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Get workflows
        usersApi.refreshWorkflows(userId);

        // Manually register workflow github
        Workflow githubWorkflow = workflowApi
                .manualRegister("github", "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.wdl", "altname", "wdl", "/test.json");

        // Manually register workflow bitbucket
        Workflow bitbucketWorkflow = workflowApi
                .manualRegister("bitbucket", "dockstore_testuser2/dockstore-workflow", "/Dockstore.cwl", "altname", "cwl", "/test.json");

        // Assert some things
        final long count = testingPostgres
                .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("No workflows are in full mode", count == 0);
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from workflow where workflowname = 'altname'", new ScalarHandler<>());
        assertTrue("There should be two workflows with name altname, there are " + count2, count2 == 2);

        // Publish github workflow
        workflowApi.refresh(githubWorkflow.getId());
        workflowApi.publish(githubWorkflow.getId(), publishRequest);

        // Publish bitbucket workflow
        workflowApi.refresh(bitbucketWorkflow.getId());
        workflowApi.publish(bitbucketWorkflow.getId(), publishRequest);

        // Assert some things
        assertTrue("should have two published, found  " + workflowApi.allPublishedWorkflows().size(),
                workflowApi.allPublishedWorkflows().size() == 2);
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertTrue("Two workflows are in full mode", count3 == 2);
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where valid = 't'", new ScalarHandler<>());
        assertTrue("There should be 5 valid version tags, there are " + count4, count4 == 6);
    }

    /**
     * Tests that trying to register a duplicate workflow fails, and that registering a non-existant repository failes
     *
     * @throws ApiException
     */
    @Test
    public void testManualRegisterErrors() throws ApiException {
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Get workflows
        usersApi.refreshWorkflows(userId);

        // Manually register workflow
        boolean success = true;
        try {
            workflowApi.manualRegister("github", "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.wdl", "", "wdl", "/test.json");
        } catch (ApiException c) {
            success = false;
        } finally {
            assertTrue("The workflow cannot be registered as it is a duplicate.", !success);
        }

        success = true;
        try {
            workflowApi.manualRegister("github", "dasn/iodnasiodnasio", "/Dockstore.wdl", "", "wdl", "/test.json");
        } catch (ApiException c) {
            success = false;
        } finally {
            assertTrue("The workflow cannot be registered as the repository doesn't exist.", !success);
        }
    }

    @Test
    public void testSecondaryFileOperations() throws ApiException {
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore-whalesay-imports", "/Dockstore.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_IMPORTS_DOCKSTORE_WORKFLOW);

        // This checks if a workflow whose default name was manually registered as an empty string would become null
        assertNull(workflowByPathGithub.getWorkflowName());

        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());

        // This checks if a workflow whose default name is null would remain as null after refresh
        assertNull(workflow.getWorkflowName());

        // test out methods to access secondary files
        final List<SourceFile> masterImports = workflowApi.secondaryCwl(workflow.getId(), "master");
        assertTrue("should find 2 imports, found " + masterImports.size(), masterImports.size() == 2);
        final SourceFile master = workflowApi.cwl(workflow.getId(), "master");
        assertTrue("master content incorrect", master.getContent().contains("untar") && master.getContent().contains("compile"));

        // get secondary files by path
        SourceFile argumentsTool = workflowApi.secondaryCwlPath(workflow.getId(), "arguments.cwl", "master");
        assertTrue("argumentstool content incorrect", argumentsTool.getContent().contains("Example trivial wrapper for Java 7 compiler"));
    }

    @Test
    public void testRelativeSecondaryFileOperations() throws ApiException {
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW);

        // This checks if a workflow whose default name was manually registered as an empty string would become null
        assertNull(workflowByPathGithub.getWorkflowName());

        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());

        // This checks if a workflow whose default name is null would remain as null after refresh
        assertNull(workflow.getWorkflowName());

        // test out methods to access secondary files

        final List<SourceFile> masterImports = workflowApi.secondaryCwl(workflow.getId(), "master");
        assertTrue("should find 3 imports, found " + masterImports.size(), masterImports.size() == 3);
        final List<SourceFile> rootImports = workflowApi.secondaryCwl(workflow.getId(), "rootTest");
        assertTrue("should find 0 imports, found " + rootImports.size(), rootImports.size() == 0);

        // next, change a path for the root imports version
        List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        workflowVersions.stream().filter(v -> v.getName().equals("rootTest")).findFirst().get().setWorkflowPath("/cnv.cwl");
        workflowApi.updateWorkflowVersion(workflow.getId(), workflowVersions);
        workflowApi.refresh(workflowByPathGithub.getId());
        final List<SourceFile> newMasterImports = workflowApi.secondaryCwl(workflow.getId(), "master");
        assertTrue("should find 3 imports, found " + newMasterImports.size(), newMasterImports.size() == 3);
        final List<SourceFile> newRootImports = workflowApi.secondaryCwl(workflow.getId(), "rootTest");
        assertTrue("should find 3 imports, found " + newRootImports.size(), newRootImports.size() == 3);
    }
}
