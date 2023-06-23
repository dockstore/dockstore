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

package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.Entry;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.Workflow.ModeEnum;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Confidential tests for checker workflows
 *
 * @author agduncan
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@org.junit.jupiter.api.Tag(ConfidentialTest.NAME)
@org.junit.jupiter.api.Tag(WorkflowTest.NAME)
class CheckerWorkflowIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();


    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    /**
     * This tests the process of adding a checker workflow to a CWL tool entry and that some calls to the tool will also trigger the calls to the checker
     * - Refresh tool should refresh checker
     * - Publish tool should publish checker
     * - Unpublish tool should unpublish checker
     *
     * @throws ApiException
     */
    @Test
    void testCWLToolAddCheckerRefreshPublishUnpublish() throws ApiException {
        // Setup for test
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        ContainersApi containersApi = new ContainersApi(webClient);

        final PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        final PublishRequest unpublishRequest = CommonTestUtilities.createPublishRequest(false);

        // Manually register a tool
        DockstoreTool newTool = new DockstoreTool();
        newTool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        newTool.setName("my-md5sum");
        newTool.setGitUrl("git@github.com:DockstoreTestUser2/md5sum-checker.git");
        newTool.setDefaultDockerfilePath("/md5sum/Dockerfile");
        newTool.setDefaultCwlPath("/md5sum/md5sum-tool.cwl");
        newTool.setRegistryString(Registry.QUAY_IO.getDockerPath());
        newTool.setNamespace("dockstoretestuser2");
        newTool.setToolname("altname");
        newTool.setPrivateAccess(false);
        newTool.setDefaultCWLTestParameterFile("/testcwl.json");
        DockstoreTool githubTool = containersApi.registerManual(newTool);

        // Refresh the workflow
        DockstoreTool refresh = containersApi.refresh(githubTool.getId());

        // Check if the output file format is added to the file_formats property
        assertTrue(refresh.getWorkflowVersions().stream().anyMatch(tag -> tag.getOutputFileFormats().stream()
            .anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/data_3671"))));
        assertTrue(refresh.getOutputFileFormats().stream()
                .anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/data_3671")));
        assertTrue(refresh.getWorkflowVersions().stream().anyMatch(
            tag -> tag.getInputFileFormats().stream().anyMatch(fileFormat -> fileFormat.getValue().equals("file://fakeFileFormat"))));
        assertTrue(refresh.getInputFileFormats().stream().anyMatch(fileFormat -> fileFormat.getValue().equals("file://fakeFileFormat")));


        // Add checker workflow
        workflowApi.registerCheckerWorkflow(githubTool.getId(), "cwl", "/checker-workflow-wrapping-tool.cwl", null);

        // Refresh workflow
        DockstoreTool refreshedEntry = containersApi.refresh(githubTool.getId());

        // Refreshing the entry also calls the update user metadata function which populates the user profile
        assertTrue(refreshedEntry.getUsers().size() > 0, "There should be at least one user of the workflow");
        refreshedEntry.getUsers().forEach(entryUser -> {
            assertNotEquals(null, entryUser.getUserProfiles(), "refresh() endpoint should have user profiles");
        });

        // Checker workflow should refresh
        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals(1, count, "The checker workflow should be full, there are " + count);

        // Checker workflow should have the same test path as entry
        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from workflow w, entry_defaultpaths ed where ed.path = '/testcwl.json' and w.id = ed.entry_id", long.class);
        assertEquals(1, count2, "The checker workflow should have the correct default test path /testcwl.json, there are " + count2);

        // Checker workflow should have the correct workflow path
        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where sourcecontrol = 'github.com' and organization = 'DockstoreTestUser2' and repository = 'md5sum-checker' and workflowname like 'altname%' and giturl = 'git@github.com:DockstoreTestUser2/md5sum-checker.git'",
            long.class);
        assertEquals(1, count3, "The checker workflow should have the correct path information, there are " + count3);

        // Publish workflow
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished = true", long.class);
        assertEquals(0, count4, "No workflows should be published, there are " + count4);

        final long count5 = testingPostgres.runSelectStatement("select count(*) from tool where ispublished = true", long.class);
        assertEquals(0, count5, "No tools should be published, there are " + count5);

        containersApi.publish(githubTool.getId(), publishRequest);

        // Checker workflow should publish
        final long count6 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished = true", long.class);
        assertEquals(1, count6, "The checker workflow should be published, there are " + count6);

        final long count7 = testingPostgres.runSelectStatement("select count(*) from tool where ispublished = true", long.class);
        assertEquals(1, count7, "the tool should be published, there are " + count7);

        // Unpublish workflow
        containersApi.publish(githubTool.getId(), unpublishRequest);

        // Checker workflow should unpublish
        final long count8 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished = true", long.class);
        assertEquals(0, count8, "Checker workflow should not be published, there are " + count8);

        final long count9 = testingPostgres.runSelectStatement("select count(*) from tool where ispublished = true", long.class);
        assertEquals(0, count9, "the tool should not be published, there are " + count9);

        try {
            workflowApi.publish1(refreshedEntry.getCheckerId(), publishRequest);
            fail("Should not be able to directly publish the checker");
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode());
        }

        try {
            workflowApi.restub(refreshedEntry.getCheckerId());
            fail("Should not be able to restub the checker");
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode());
        }
    }

    /**
     * This series of tests for
     * should be able to refresh all or the organization when a checker stub is present without a failure (constraints issue from #1405)
     */

    @Test
    void testCheckerWorkflowAndRefreshIssueByAll() {
        testCheckerWorkflowAndRefresh(true, true);
    }

    @Test
    void testCheckerWorkflowAndRefreshIssueByOrganization() {
        testCheckerWorkflowAndRefresh(true, false);
    }

    @Test
    void testCheckerWorkflowAndRefreshIssueByAllToolVersion() {
        testCheckerWorkflowAndRefresh(false, true);
    }

    @Test
    void testCheckerWorkflowAndRefreshIssueByOrganizationToolVersion() {
        testCheckerWorkflowAndRefresh(false, false);
    }

    private void testCheckerWorkflowAndRefresh(boolean workflow, boolean all) {
        // Setup for test
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        ContainersApi containersApi = new ContainersApi(webClient);

        long baseEntryId;
        if (workflow) {

            // Manually register a workflow
            Workflow githubWorkflow = workflowApi
                .manualRegister("github", "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl", "", "cwl", "/testcwl.json");
            assertEquals("Apache License 2.0", githubWorkflow.getLicenseInformation().getLicenseName(), "Should be able to get license after manual register");
            // Clear license name to mimic old workflow that does not have a license associated with it
            testingPostgres.runUpdateStatement("update workflow set licensename=null");
            Workflow refreshedWorkflow = workflowApi.refresh1(githubWorkflow.getId(), false);
            assertEquals("Apache License 2.0", refreshedWorkflow.getLicenseInformation().getLicenseName(), "Should be able to get license after refresh");
            // Refresh the workflow
            baseEntryId = refreshedWorkflow.getId();
        } else {
            // Manually register a tool
            DockstoreTool newTool = new DockstoreTool();
            newTool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
            newTool.setName("my-md5sum");
            newTool.setGitUrl("git@github.com:DockstoreTestUser2/md5sum-checker.git");
            newTool.setDefaultDockerfilePath("/md5sum/Dockerfile");
            newTool.setDefaultCwlPath("/md5sum/md5sum-tool.cwl");
            newTool.setRegistryString(Registry.QUAY_IO.getDockerPath());
            newTool.setNamespace("dockstoretestuser2");
            newTool.setToolname("altname");
            newTool.setPrivateAccess(false);
            newTool.setDefaultCWLTestParameterFile("/testcwl.json");
            DockstoreTool githubTool = containersApi.registerManual(newTool);

            // Refresh the workflow
            DockstoreTool refresh = containersApi.refresh(githubTool.getId());
            baseEntryId = refresh.getId();
        }

        // Add checker workflow
        final Entry checkerWorkflowBase = workflowApi
            .registerCheckerWorkflow(baseEntryId, "cwl", "/checker-workflow-wrapping-workflow.cwl", null);
        final Workflow stubCheckerWorkflow = workflowApi.getWorkflow(checkerWorkflowBase.getCheckerId(), null);
        assertSame(ModeEnum.STUB, stubCheckerWorkflow.getMode());

        // should be able to refresh all or the organization when a checker stub is present without a failure (constraints issue from #1405)
        List<Workflow> workflows = new ArrayList<>();
        workflows.add(workflowApi
                .manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/dockstore-whalesay-wdl", "/dockstore.wdl", "",
                        DescriptorLanguage.WDL.getShortName(), ""));
        workflows.add(workflowApi
                .manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/dockstore-whalesay-2", "/dockstore.wdl", "",
                        DescriptorLanguage.WDL.getShortName(), ""));
        workflows.add(workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/ampa-nf", "/nextflow.config", "",
                DescriptorLanguage.NEXTFLOW.getShortName(), ""));
        workflows.add(workflowApi
                .manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json"));
        if (all) {
            for (Workflow workflowItem : workflows) {
                workflowApi.refresh1(workflowItem.getId(), false);
            }
        } else {
            for (Workflow workflowItem : workflows) {
                if (workflowItem.getOrganization().equalsIgnoreCase(stubCheckerWorkflow.getOrganization())) {
                    workflowApi.refresh1(workflowItem.getId(), false);
                }
            }
        }
    }

    /**
     * This tests the process of adding a checker workflow to a CWL workflow entry and that some calls to the workflow will also trigger the calls to the checker
     * - Refresh workflow should refresh checker
     * - Publish workflow should publish checker
     * - Unpublish workflow should unpublish checker
     *
     * @throws ApiException
     */
    @Test
    void testCWLWorkflowAddCheckerRefreshPublishUnpublish() throws ApiException {
        // Setup for test
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        final PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        final PublishRequest unpublishRequest = CommonTestUtilities.createPublishRequest(false);

        // Manually register a workflow
        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl", "altname", "cwl",
                "/testcwl.json");

        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals(0, count, "No workflows are in full mode, there are " + count);

        // Refresh the workflow
        workflowApi.refresh1(githubWorkflow.getId(), false);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals(1, count2, "One workflow should be full, there are " + count2);

        // Add checker workflow
        workflowApi.registerCheckerWorkflow(githubWorkflow.getId(), "cwl", "/checker-workflow-wrapping-workflow.cwl", null);

        // Refresh workflow
        Workflow refreshedEntry = workflowApi.refresh1(githubWorkflow.getId(), false);

        // Should be able to download zip for first version
        Workflow checkerWorkflow = workflowApi.getWorkflow(refreshedEntry.getCheckerId(), null);
        workflowApi.getWorkflowZip(checkerWorkflow.getId(), checkerWorkflow.getWorkflowVersions().get(0).getId());

        // Refreshing the entry also calls the update user metadata function which populates the user profile
        refreshedEntry.getUsers().forEach(entryUser -> {
            assertNotEquals(null, entryUser.getUserProfiles(), "refresh() endpoint should have user profiles");
        });

        // Checker workflow should refresh
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals(2, count3, "Two workflows should be full (one being the checker), there are " + count3);

        // Checker workflow should have the same test path as entry
        final long count4 = testingPostgres.runSelectStatement(
            "select count(*) from workflow w, entry_defaultpaths ed where ed.path = '/testcwl.json' and w.id = ed.entry_id", long.class);
        assertEquals(2, count4, "There should be two workflows with default test parameter file path of /testcwl.json, there are " + count4);

        // Checker workflow should have the correct workflow path
        final long count5 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where sourcecontrol = 'github.com' and organization = 'DockstoreTestUser2' and repository = 'md5sum-checker' and workflowname = 'altname_cwl_checker' and giturl = 'git@github.com:DockstoreTestUser2/md5sum-checker.git'",
            long.class);
        assertEquals(1, count5, "The workflow should have the correct path, there are " + count5);

        // Publish workflow
        final long count6 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished = true", long.class);
        assertEquals(0, count6, "No workflows should be published, there are " + count6);
        workflowApi.publish1(githubWorkflow.getId(), publishRequest);

        // Checker workflow should publish
        final long count7 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished = true", long.class);
        assertEquals(2, count7, "Two workflows should be published (one being the checker), there are " + count7);

        // Should still be able to download zip for first version
        workflowApi.getWorkflowZip(checkerWorkflow.getId(), checkerWorkflow.getWorkflowVersions().get(0).getId());

        // Unpublish workflow
        workflowApi.publish1(githubWorkflow.getId(), unpublishRequest);

        // Checker workflow should unpublish
        final long count8 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished = true", long.class);
        assertEquals(0, count8, "No workflows should be published, there are " + count8);

        // Should not be able to directly publish the checker
        try {
            workflowApi.publish1(refreshedEntry.getCheckerId(), publishRequest);
            fail("Should not reach this statement.");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }
    }

    /**
     * This tests the process of adding a checker workflow to a WDL workflow entry and that some calls to the workflow will also trigger the calls to the checker
     * - Refresh workflow should refresh checker
     * - Publish workflow should publish checker
     * - Unpublish workflow should unpublish checker
     *
     * @throws ApiException
     */
    @Test
    void testWDLWorkflowAddCheckerRefreshPublishUnpublish() throws ApiException {
        // Setup for test
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        final PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        final PublishRequest unpublishRequest = CommonTestUtilities.createPublishRequest(false);

        // Manually register a workflow
        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.wdl", "altname", "wdl",
                "/md5sum-wdl.json");

        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals(0, count, "No workflows are in full mode, there are " + count);

        // Refresh the workflow
        workflowApi.refresh1(githubWorkflow.getId(), false);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals(1, count2, "One workflow should be full, there are " + count2);

        // Add checker workflow
        workflowApi.registerCheckerWorkflow(githubWorkflow.getId(), "wdl", "/checker-workflow-wrapping-workflow.wdl", null);

        // Refresh workflow
        workflowApi.refresh1(githubWorkflow.getId(), false);

        // Checker workflow should refresh
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals(2, count3, "Two workflows should be full (one being the checker), there are " + count3);

        // Checker workflow should have the same test path as entry
        final long count4 = testingPostgres.runSelectStatement(
            "select count(*) from workflow w, entry_defaultpaths ed where ed.path = '/md5sum-wdl.json' and w.id = ed.entry_id", long.class);
        assertEquals(2, count4, "There should be two workflows with default test parameter file path of /md5sum-wdl.json, there are " + count4);

        // Checker workflow should have the correct workflow path
        final long count5 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where sourcecontrol = 'github.com' and organization = 'DockstoreTestUser2' and repository = 'md5sum-checker' and workflowname = 'altname_wdl_checker' and giturl = 'git@github.com:DockstoreTestUser2/md5sum-checker.git'",
            long.class);
        assertEquals(1, count5, "The workflow should have the correct path, there are " + count5);

        // Publish workflow
        final long count6 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished = true", long.class);
        assertEquals(0, count6, "No workflows should be published, there are " + count6);
        workflowApi.publish1(githubWorkflow.getId(), publishRequest);

        // Checker workflow should publish
        final long count7 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished = true", long.class);
        assertEquals(2, count7, "Two workflows should be published (one being the checker), there are " + count7);

        // Unpublish workflow
        workflowApi.publish1(githubWorkflow.getId(), unpublishRequest);

        // Checker workflow should unpublish
        final long count8 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished = true", long.class);
        assertEquals(0, count8, "No workflows should be published, there are " + count8);
    }

    /**
     * Should not be able to add a checker workflow to a stub workflow (Should fail)
     *
     * @throws ApiException
     */
    @Test
    void testAddCheckerToStub() throws ApiException {
        // Setup for test
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // Manually register a workflow
        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl", "altname", "cwl",
                "/testcwl.json");

        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals(0, count, "No workflows are in full mode, there are " + count);

        // Add checker workflow
        assertThrows(ApiException.class, () ->  workflowApi.registerCheckerWorkflow(githubWorkflow.getId(), "cwl", "checker-workflow-wrapping-workflow.cwl", null));
    }

    /**
     * Tests that you cannot register a tool with an underscore
     *
     * @throws ApiException
     */
    @Test
    void testRegisteringToolWithUnderscoreInName() throws ApiException {
        // Setup for test
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi containersApi = new ContainersApi(webClient);

        // Make tool
        DockstoreTool newTool = new DockstoreTool();
        newTool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        newTool.setName("my-md5sum");
        newTool.setGitUrl("git@github.com:DockstoreTestUser2/md5sum-checker.git");
        newTool.setDefaultDockerfilePath("/md5sum/Dockerfile");
        newTool.setDefaultCwlPath("/md5sum/md5sum-tool.cwl");
        newTool.setRegistryString(Registry.QUAY_IO.getDockerPath());
        newTool.setNamespace("dockstoretestuser2");
        newTool.setToolname("_altname");
        newTool.setPrivateAccess(false);
        newTool.setDefaultCWLTestParameterFile("/testcwl.json");
        assertThrows(ApiException.class, () ->  containersApi.registerManual(newTool));
    }

    /**
     * Tests that you cannot register a workflow with an underscore
     *
     * @throws ApiException
     */
    @Test
    void testRegisteringWorkflowWithUnderscoreInName() throws ApiException {
        // Setup for test
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // Manually register a workflow
        assertThrows(ApiException.class, () ->  workflowApi.manualRegister("github", "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl", "_altname", "cwl",
            "/testcwl.json"));
    }

}
