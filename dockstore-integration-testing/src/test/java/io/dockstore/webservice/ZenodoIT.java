/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice;

import static io.dockstore.client.cli.BaseIT.ADMIN_USERNAME;
import static io.dockstore.client.cli.BaseIT.USER_2_USERNAME;
import static io.dockstore.client.cli.BaseIT.getAnonymousOpenAPIWebClient;
import static io.dockstore.common.CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH;
import static io.dockstore.common.CommonTestUtilities.getOpenAPIWebClient;
import static io.dockstore.common.CommonTestUtilities.getWorkflowVersion;
import static io.dockstore.common.Hoverfly.ZENODO_DOI_SEARCH;
import static io.dockstore.common.Hoverfly.ZENODO_SIMULATION_SOURCE;
import static io.dockstore.common.Hoverfly.ZENODO_SIMULATION_URL;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubRelease;
import static io.dockstore.webservice.helpers.ZenodoHelper.ACCESS_LINK_ALREADY_EXISTS;
import static io.dockstore.webservice.helpers.ZenodoHelper.ACCESS_LINK_DOESNT_EXIST;
import static io.dockstore.webservice.helpers.ZenodoHelper.FROZEN_VERSION_REQUIRED;
import static io.dockstore.webservice.helpers.ZenodoHelper.NO_DOCKSTORE_DOI;
import static io.dockstore.webservice.helpers.ZenodoHelper.NO_ZENODO_USER_TOKEN;
import static io.dockstore.webservice.helpers.ZenodoHelper.PUBLISHED_ENTRY_REQUIRED;
import static io.dockstore.webservice.helpers.ZenodoHelper.UNHIDDEN_VERSION_REQUIRED;
import static io.dockstore.webservice.resources.WorkflowResource.A_WORKFLOW_MUST_BE_UNPUBLISHED_TO_RESTUB;
import static io.dockstore.webservice.resources.WorkflowResource.A_WORKFLOW_MUST_HAVE_NO_SNAPSHOT_TO_RESTUB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.HoverflyTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.RepositoryConstants.DockstoreTesting;
import io.dockstore.common.SourceControl;
import io.dockstore.common.TestingPostgres;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.AccessLink;
import io.dockstore.openapi.client.model.AutoDoiRequest;
import io.dockstore.openapi.client.model.Doi;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.Workflow.DoiSelectionEnum;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.core.Doi.DoiInitiator;
import io.dockstore.webservice.core.TokenScope;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.openapi.model.DescriptorType;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyMode;
import io.specto.hoverfly.junit5.HoverflyExtension;
import io.specto.hoverfly.junit5.api.HoverflyConfig;
import io.specto.hoverfly.junit5.api.HoverflyCore;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Integration tests involving requesting DOIs and shared access links. Uses Hoverfly to mock responses from sandbox.zenodo.org.
 * Due to the limitations of mocking, these tests only do basic verifications, such as confirming that the there is a DOI URL for the workflow version.
 * It is difficult to mock things such as metadata (ex: aliases and authors) since they are workflow-specific, thus it is not tested.
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@ExtendWith(HoverflyExtension.class)
// Must set destination otherwise Hoverfly will intercept everything, including GitHub requests
@HoverflyCore(mode = HoverflyMode.SIMULATE, config = @HoverflyConfig(destination = ZENODO_SIMULATION_URL, commands = { "-disable-cache" })) // Disable cache so that it doesn't re-use responses which results in duplicate DOIs
@Tag(ConfidentialTest.NAME)
@Tag(HoverflyTest.NAME)
class ZenodoIT {

    // Set fake Dockstore Zenodo token so DOIs can automatically be created
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(DockstoreWebserviceApplication.class, CONFIDENTIAL_CONFIG_PATH,
            ConfigOverride.config("dockstoreZenodoAccessToken", "foobar"));

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    /**
     * The Concept DOI from the fixtures/zenodoListRecords.json and fixtures/zenodoVersions.json
     */
    private static final String CONCEPT_DOI = "10.5281/zenodo.11094520";
    /**
     * The version DOI from the fixtures/zenodoListRecords.json and fixtures/zenodoVersions.json
     */
    private static final String VERSION_DOI = "10.5281/zenodo.11095507";
    private static final String FAKE_VERSION_DOI = "10.5281/zenodo.11095506";

    private static TestingPostgres testingPostgres;

    @BeforeAll
    public static void dropAndRecreateDB() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
    }

    @BeforeEach
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.getEnvironment().healthChecks().shutdown();
        SUPPORT.after();
    }

    @Test
    void testGitHubAppAutomaticDoiCreation(Hoverfly hoverfly) {
        hoverfly.simulate(ZENODO_SIMULATION_SOURCE);
        final ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final WorkflowsApi adminWorkflowsApi = new WorkflowsApi(getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres));

        // Add a fake Zenodo token
        testingPostgres.runUpdateStatement(String.format("insert into token (id, dbcreatedate, dbupdatedate, content, refreshToken, tokensource, userid, username, scope) values "
                + "(9001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'fakeToken', 'fakeRefreshToken', 'zenodo.org', 1, '%s', '%s')", USER_2_USERNAME, TokenScope.AUTHENTICATE.name()));

        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.8", USER_2_USERNAME);
        Workflow foobar2 = workflowsApi.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");
        final long foobar2Id = foobar2.getId();
        final AutoDoiRequest autoDoiRequest = new AutoDoiRequest();
        autoDoiRequest.setAutoGenerateDois(true);
        adminWorkflowsApi.autoGenerateDois(autoDoiRequest, foobar2Id);
        WorkflowVersion foobar2TagVersion08 = getWorkflowVersion(foobar2, "0.8").orElse(null);
        assertNotNull(foobar2TagVersion08);
        final long foobar2VersionId = foobar2TagVersion08.getId();

        // No DOIs should've been automatically created because the workflow is unpublished
        assertTrue(foobar2TagVersion08.getDois().isEmpty());
        // Publish workflow
        workflowsApi.publish1(foobar2.getId(), new PublishRequest().publish(true));

        // Release the tag again. Should automatically create a DOI
        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.8", USER_2_USERNAME);
        foobar2TagVersion08 = workflowsApi.getWorkflowVersionById(foobar2.getId(), foobar2TagVersion08.getId(), "");
        assertFalse(foobar2TagVersion08.isFrozen(), "Version should not be snapshotted for automatic DOI creation");
        assertNotNull(foobar2TagVersion08.getDois().get(DoiInitiator.DOCKSTORE.name()).getName());
        foobar2 = workflowsApi.getWorkflow(foobar2Id, "");
        assertEquals(DoiSelectionEnum.DOCKSTORE, foobar2.getDoiSelection(), "DOI selection should update to DOCKSTORE since there were previously no DOIs");

        // Should be able to request a user-created DOI for the version even though it has a Dockstore-created DOI
        foobar2TagVersion08.setFrozen(true);
        workflowsApi.updateWorkflowVersion(foobar2Id, List.of(foobar2TagVersion08));
        workflowsApi.requestDOIForWorkflowVersion(foobar2Id, foobar2VersionId, "");
        foobar2 = workflowsApi.getWorkflow(foobar2Id, "versions");
        foobar2TagVersion08 = getWorkflowVersion(foobar2, "0.8").orElse(null);
        assertNotNull(foobar2TagVersion08.getDois().get(DoiInitiator.USER.name()).getName());
        assertEquals(DoiSelectionEnum.USER, foobar2.getDoiSelection(), "DOI selection should update to USER since it takes highest precedence");

        // Release a different tag. Should automatically create DOI
        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.9", USER_2_USERNAME);
        foobar2 = workflowsApi.getWorkflow(foobar2Id, "versions");
        WorkflowVersion foobar2TagVersion09 = getWorkflowVersion(foobar2, "0.9").orElse(null);
        assertNotNull(foobar2TagVersion09);
        assertNotNull(foobar2TagVersion09.getDois().get(DoiInitiator.DOCKSTORE.name()).getName());
        assertEquals(DoiSelectionEnum.USER, foobar2.getDoiSelection(), "DOI selection should be USER since it takes highest precedence");

        // Release a branch. Should not automatically create a DOI because it's not a tag
        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/heads/master", USER_2_USERNAME);
        foobar2 = workflowsApi.getWorkflow(foobar2Id, "versions");
        WorkflowVersion foobar2BranchVersion = getWorkflowVersion(foobar2, "master").orElse(null);
        assertNotNull(foobar2BranchVersion);
        assertFalse(foobar2BranchVersion.getDois().containsKey(DoiInitiator.DOCKSTORE.name()));

        // Test updating the DOI selection
        assertEquals(DoiSelectionEnum.USER, foobar2.getDoiSelection());
        foobar2.setDoiSelection(DoiSelectionEnum.DOCKSTORE);
        workflowsApi.updateWorkflow(foobar2Id, foobar2);
        foobar2 = workflowsApi.getWorkflow(foobar2Id, "versions");
        assertEquals(DoiSelectionEnum.DOCKSTORE, foobar2.getDoiSelection());
    }

    @Test
    void testNoDoiGeneratedIfNotEnabled(Hoverfly hoverfly) {
        hoverfly.simulate(ZENODO_SIMULATION_SOURCE);
        final ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        // Add a fake Zenodo token
        testingPostgres.runUpdateStatement(String.format("insert into token (id, dbcreatedate, dbupdatedate, content, refreshToken, tokensource, userid, username, scope) values "
                + "(9001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'fakeToken', 'fakeRefreshToken', 'zenodo.org', 1, '%s', '%s')", USER_2_USERNAME, TokenScope.AUTHENTICATE.name()));

        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.8", USER_2_USERNAME);
        Workflow foobar2 = workflowsApi.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");
        final long foobar2Id = foobar2.getId();
        WorkflowVersion foobar2TagVersion08 = getWorkflowVersion(foobar2, "0.8").orElse(null);
        assertNotNull(foobar2TagVersion08);
        final long foobar2VersionId = foobar2TagVersion08.getId();

        // No DOIs should've been automatically created because the workflow is unpublished
        assertTrue(foobar2TagVersion08.getDois().isEmpty());
        // Publish workflow
        workflowsApi.publish1(foobar2.getId(), new PublishRequest().publish(true));

        // Release the tag again. Should automatically create a DOI
        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.8", USER_2_USERNAME);
        foobar2TagVersion08 = workflowsApi.getWorkflowVersionById(foobar2.getId(), foobar2TagVersion08.getId(), "");
        assertEquals(Map.of(), foobar2TagVersion08.getDois(), "No DOI should have been created with autoGenerateDois set to false");
        foobar2 = workflowsApi.getWorkflow(foobar2Id, "");
        assertEquals(Map.of(), foobar2.getConceptDois(), "No DOI should have been created with autoGenerateDois set to false");
    }

    @Test
    void testRefreshAutomaticDoiCreation(Hoverfly hoverfly) {
        hoverfly.simulate(ZENODO_SIMULATION_SOURCE);
        ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final WorkflowsApi adminWorkflowsApi = new WorkflowsApi(getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres));

        // register workflow and refresh workflow
        Workflow workflow = workflowsApi
                .manualRegister(SourceControl.GITHUB.name(), DockstoreTesting.HELLO_WDL_WORKFLOW, "/Dockstore.wdl", "", DescriptorType.WDL.toString(), "/test.json");
        final AutoDoiRequest autoDoiRequest = new AutoDoiRequest();
        autoDoiRequest.setAutoGenerateDois(true);
        adminWorkflowsApi.autoGenerateDois(autoDoiRequest, workflow.getId());
        workflowsApi.refresh1(workflow.getId(), false);
        workflow = workflowsApi.getWorkflow(workflow.getId(), "versions");

        // Get a valid tag
        WorkflowVersion tagVersion = getWorkflowVersion(workflow, "1.1").orElse(null);
        assertTrue(tagVersion.getDois().isEmpty(), "Should not have any automatic DOIs because it's unpublished");

        // Publish the workflow and refresh
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));
        workflowsApi.refresh1(workflow.getId(), false);

        tagVersion = workflowsApi.getWorkflowVersionById(workflow.getId(), tagVersion.getId(), "");
        assertNotNull(tagVersion.getDois().get(DoiInitiator.DOCKSTORE.name()).getName(), "Should have automatic DOI because it's a valid published tag");
        workflow = workflowsApi.getWorkflow(workflow.getId(), "");
        assertEquals(DoiSelectionEnum.DOCKSTORE, workflow.getDoiSelection(), "DOI selection should update to DOCKSTORE since there were previously no DOIs");
    }

    @Test
    void testAutomaticDoiCreationOnPublishGitHubApp(Hoverfly hoverfly) {
        hoverfly.simulate(ZENODO_SIMULATION_SOURCE);
        ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final WorkflowsApi adminWorkflowsApi = new WorkflowsApi(getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres));

        // Create a GitHub App workflow
        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.8", USER_2_USERNAME);
        Workflow workflow = workflowsApi.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");
        final AutoDoiRequest autoDoiRequest = new AutoDoiRequest();
        autoDoiRequest.setAutoGenerateDois(true);
        adminWorkflowsApi.autoGenerateDois(autoDoiRequest, workflow.getId());
        WorkflowVersion tagVersion = getWorkflowVersion(workflow, "0.8").orElse(null);
        assertNotNull(tagVersion);
        // No DOIs should've been automatically created because the workflow is unpublished
        assertTrue(workflow.getConceptDois().isEmpty());
        assertTrue(tagVersion.getDois().isEmpty());

        // Publish workflow, should automatically create DOIs
        workflowsApi.publish1(workflow.getId(), new PublishRequest().publish(true));
        workflow = workflowsApi.getWorkflow(workflow.getId(), "versions");
        tagVersion = getWorkflowVersion(workflow, "0.8").orElse(null);
        assertNotNull(workflow.getConceptDois().get(DoiInitiator.DOCKSTORE.name()).getName());
        assertNotNull(tagVersion.getDois().get(DoiInitiator.DOCKSTORE.name()).getName());
        assertEquals(DoiSelectionEnum.DOCKSTORE, workflow.getDoiSelection(), "DOI selection should update to DOCKSTORE since there were previously no DOIs");
    }

    @Test
    void testAutomaticDoiCreationOnPublishManualRegister(Hoverfly hoverfly) {
        hoverfly.simulate(ZENODO_SIMULATION_SOURCE);
        ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final WorkflowsApi adminWorkflowsApi = new WorkflowsApi(getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres));

        // Manually register workflow and refresh workflow
        Workflow workflow = workflowsApi
                .manualRegister(SourceControl.GITHUB.name(), DockstoreTesting.HELLO_WDL_WORKFLOW, "/Dockstore.wdl", "", DescriptorType.WDL.toString(), "/test.json");
        final AutoDoiRequest autoDoiRequest = new AutoDoiRequest();
        autoDoiRequest.setAutoGenerateDois(true);
        adminWorkflowsApi.autoGenerateDois(autoDoiRequest, workflow.getId());
        workflowsApi.refresh1(workflow.getId(), false);
        workflow = workflowsApi.getWorkflow(workflow.getId(), "versions");
        assertTrue(workflow.getConceptDois().isEmpty(), "Should not have any automatic DOIs because it's unpublished");
        assertEquals(DoiSelectionEnum.USER, workflow.getDoiSelection(), "Default should be USER");

        // Get a valid tag
        WorkflowVersion tagVersion = getWorkflowVersion(workflow, "1.1").orElse(null);
        assertTrue(tagVersion.getDois().isEmpty(), "Should not have any automatic DOIs because it's unpublished");

        // Publish the workflow, which should automatically create DOIs
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));
        workflow = workflowsApi.getWorkflow(workflow.getId(), "versions");
        tagVersion = workflowsApi.getWorkflowVersionById(workflow.getId(), tagVersion.getId(), "");
        assertNotNull(workflow.getConceptDois().get(DoiInitiator.DOCKSTORE.name()).getName(), "Should have automatic concept DOI");
        assertNotNull(tagVersion.getDois().get(DoiInitiator.DOCKSTORE.name()).getName(), "Should have automatic DOI because it's a valid published tag");
        assertEquals(DoiSelectionEnum.DOCKSTORE, workflow.getDoiSelection(), "DOI selection should update to DOCKSTORE since there were previously no DOIs");

        // Change DOI selection to GITHUB. It should be ignored because there are no GitHub DOIs for the workflow
        workflow.setDoiSelection(DoiSelectionEnum.GITHUB);
        workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflow = workflowsApi.getWorkflow(workflow.getId(), "");
        assertEquals(DoiSelectionEnum.DOCKSTORE, workflow.getDoiSelection(), "The DOI selection should not change");
    }

    @Test
    void testGenerateDOIFrozenVersion(Hoverfly hoverfly) throws ApiException {
        hoverfly.simulate(ZENODO_SIMULATION_SOURCE);
        ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        // register workflow
        Workflow githubWorkflow = workflowsApi
                .manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/test_lastmodified", "/hello.wdl", "test-update-workflow", DescriptorType.WDL.toString(), "/test.json");

        Workflow workflowBeforeFreezing = workflowsApi.refresh1(githubWorkflow.getId(), false);
        WorkflowVersion master = getWorkflowVersion(workflowBeforeFreezing, "master").get();
        final long workflowId = workflowBeforeFreezing.getId();
        final long versionId = master.getId();

        // DOI should only be generated for published workflows.
        ApiException exception = assertThrows(ApiException.class, () -> workflowsApi.requestDOIForWorkflowVersion(workflowId, versionId, ""));
        assertTrue(exception.getMessage().contains(PUBLISHED_ENTRY_REQUIRED));

        // Publish workflow
        workflowsApi.publish1(workflowId, CommonTestUtilities.createOpenAPIPublishRequest(true));

        // DOI should only be generated for frozen versions of workflows.
        exception = assertThrows(ApiException.class, () -> workflowsApi.requestDOIForWorkflowVersion(workflowId, versionId, ""));
        assertTrue(exception.getMessage().contains(FROZEN_VERSION_REQUIRED));

        // freeze version 'master'
        master.setFrozen(true);
        workflowsApi.updateWorkflowVersion(workflowId, List.of(master));
        master = workflowsApi.getWorkflowVersionById(workflowId, versionId, "");
        assertTrue(master.isFrozen());

        // Should be able to refresh a workflow with a frozen version without throwing an error
        workflowsApi.refresh1(githubWorkflow.getId(), false);

        // should not be able to restub whether published or not since there is a snapshot/frozen
        exception = assertThrows(ApiException.class, () -> workflowsApi.restub(workflowId));
        assertTrue(exception.getMessage().contains(A_WORKFLOW_MUST_BE_UNPUBLISHED_TO_RESTUB));

        // should not be able to request a DOI for a hidden version
        testingPostgres.runUpdateStatement("update version_metadata set hidden = true");
        master = workflowsApi.getWorkflowVersionById(workflowId, versionId, "");
        assertTrue(master.isHidden());
        exception = assertThrows(ApiException.class, () -> workflowsApi.requestDOIForWorkflowVersion(workflowId, versionId, ""));
        assertTrue(exception.getMessage().contains(UNHIDDEN_VERSION_REQUIRED));
        testingPostgres.runUpdateStatement("update version_metadata set hidden = false");

        // Unpublish workflow
        workflowsApi.publish1(workflowId, CommonTestUtilities.createOpenAPIPublishRequest(false));

        // don't die horribly when stubbing something with snapshots, explain the error
        exception = assertThrows(ApiException.class, () -> workflowsApi.restub(workflowId));
        assertTrue(exception.getMessage().contains(A_WORKFLOW_MUST_HAVE_NO_SNAPSHOT_TO_RESTUB));

        // Publish workflow
        workflowsApi.publish1(workflowId, CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Should not be able to register DOI without Zenodo token
        exception = assertThrows(ApiException.class, () -> workflowsApi.requestDOIForWorkflowVersion(workflowId, versionId, ""));
        assertTrue(exception.getMessage().contains(NO_ZENODO_USER_TOKEN));

        // Add a fake Zenodo token
        testingPostgres.runUpdateStatement(String.format("insert into token (id, dbcreatedate, dbupdatedate, content, refreshToken, tokensource, userid, username, scope) values "
                + "(9001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'fakeToken', 'fakeRefreshToken', 'zenodo.org', 1, '%s', '%s')", USER_2_USERNAME, TokenScope.AUTHENTICATE.name()));

        workflowsApi.requestDOIForWorkflowVersion(workflowId, versionId, "");

        Workflow workflow = workflowsApi.getWorkflow(workflowId, "");
        assertNotNull(workflow.getConceptDois().get(DoiInitiator.USER.name()));
        master = workflowsApi.getWorkflowVersionById(workflowId, versionId, "");
        assertNotNull(master.getDois().get(DoiInitiator.USER.name()).getName());
        assertEquals(DoiSelectionEnum.USER, workflow.getDoiSelection());

        // unpublish workflow
        workflowsApi.publish1(workflowBeforeFreezing.getId(), CommonTestUtilities.createOpenAPIPublishRequest(false));

        // Should not be able to restub an unpublished workflow that has a frozen version or a DOI, depending upon the environment
        exception = assertThrows(ApiException.class, () -> workflowsApi.restub(workflowId));
    }

    @Test
    void testAccessLinkOperations(Hoverfly hoverfly) {
        hoverfly.simulate(ZENODO_SIMULATION_SOURCE);
        ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        ApiClient anonWebClient = getAnonymousOpenAPIWebClient();
        WorkflowsApi anonWorkflowsApi = new WorkflowsApi(anonWebClient);
        final WorkflowsApi adminWorkflowsApi = new WorkflowsApi(getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres));

        // Create a GitHub App workflow
        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.8", USER_2_USERNAME);
        Workflow workflow = workflowsApi.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");
        final AutoDoiRequest autoDoiRequest = new AutoDoiRequest();
        autoDoiRequest.setAutoGenerateDois(true);
        adminWorkflowsApi.autoGenerateDois(autoDoiRequest, workflow.getId());
        WorkflowVersion tagVersion = getWorkflowVersion(workflow, "0.8").orElse(null);
        assertNotNull(tagVersion);
        // No DOIs should've been automatically created because the workflow is unpublished
        assertTrue(workflow.getConceptDois().isEmpty());
        assertTrue(tagVersion.getDois().isEmpty());

        // Should fail to create an access because there are no Dockstore DOIs
        final long workflowId = workflow.getId();
        ApiException exception = assertThrows(ApiException.class, () -> workflowsApi.requestDOIEditLink(workflowId));
        assertTrue(exception.getMessage().contains(NO_DOCKSTORE_DOI));

        // Publish workflow, should automatically create DOIs
        workflowsApi.publish1(workflow.getId(), new PublishRequest().publish(true));
        workflow = workflowsApi.getWorkflow(workflowId, "versions");
        tagVersion = getWorkflowVersion(workflow, "0.8").orElse(null);
        assertNotNull(workflow.getConceptDois().get(DoiInitiator.DOCKSTORE.name()).getName());
        assertNotNull(tagVersion.getDois().get(DoiInitiator.DOCKSTORE.name()).getName());

        // Create an access link
        AccessLink expectedAccessLink = workflowsApi.requestDOIEditLink(workflowId);
        assertNotNull(expectedAccessLink.getId());
        assertNotNull(expectedAccessLink.getToken());

        // Get the existing access
        assertEquals(expectedAccessLink, workflowsApi.getDOIEditLink(workflowId));

        // Should not be able to request another link
        exception = assertThrows(ApiException.class, () -> workflowsApi.requestDOIEditLink(workflowId));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode());
        assertTrue(exception.getMessage().contains(ACCESS_LINK_ALREADY_EXISTS));

        // Anonymous users should not be able to perform operations on the link
        exception = assertThrows(ApiException.class, () -> anonWorkflowsApi.requestDOIEditLink(workflowId));
        assertEquals(HttpStatus.SC_UNAUTHORIZED, exception.getCode());
        exception = assertThrows(ApiException.class, () -> anonWorkflowsApi.getDOIEditLink(workflowId));
        assertEquals(HttpStatus.SC_UNAUTHORIZED, exception.getCode());
        exception = assertThrows(ApiException.class, () -> anonWorkflowsApi.deleteDOIEditLink(workflowId));
        assertEquals(HttpStatus.SC_UNAUTHORIZED, exception.getCode());

        // Delete the access link
        workflowsApi.deleteDOIEditLink(workflowId);
        exception = assertThrows(ApiException.class, () -> workflowsApi.getDOIEditLink(workflowId));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode());
        assertTrue(exception.getMessage().contains(ACCESS_LINK_DOESNT_EXIST));
    }

    @Test
    void testGitHubZenodoDoiDiscovery(Hoverfly hoverfly) {
        hoverfly.simulate(ZENODO_DOI_SEARCH);
        final ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.8", USER_2_USERNAME);

        // If we publish using the API, then we have to add the Hoverfly statements to autocreate the DOI; easier to just change the DB
        testingPostgres.runUpdateStatement("update workflow set ispublished = true, waseverpublic = true;");

        final List<Workflow> workflows = workflowsApi.updateDois(null, null);
        workflows.forEach(workflow -> {
            assertEquals(CONCEPT_DOI, workflow.getConceptDois().get(DoiInitiator.GITHUB.toString()).getName());
            assertEquals(DoiSelectionEnum.GITHUB, workflow.getDoiSelection());
            final List<WorkflowVersion> workflowVersions = workflowsApi.getWorkflowVersions(workflow.getId(), null, null, null, null);
            workflowVersions.stream().filter(w -> "0.8".equals(w.getName())).forEach(wv -> {
                final Doi doi = wv.getDois().get(DoiInitiator.GITHUB.toString());
                assertEquals(VERSION_DOI, doi.getName());
            });
        });
    }

    @Test
    void testUpdateDoisFilter(Hoverfly hoverfly) {
        hoverfly.simulate(ZENODO_DOI_SEARCH);
        final ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.8", USER_2_USERNAME);

        // If we publish using the API, then we have to add the Hoverfly statements to autocreate the DOI; easier to just change the DB
        testingPostgres.runUpdateStatement("update workflow set ispublished = true, waseverpublic = true;");

        assertEquals(List.of(), workflowsApi.updateDois("foo/bar", null), "No workflows are in foo/bar");
        final ApiException exception = assertThrows(ApiException.class, () -> workflowsApi.updateDois("NotAnOrgSlashRepoFormat", null));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode());
        assertEquals(2, workflowsApi.updateDois(DockstoreTesting.WORKFLOW_DOCKSTORE_YML, null).size(), "Should update 2 workflows");
    }

    @Test
    void testUpdateDoisLastReleaseDate(Hoverfly hoverfly) {
        hoverfly.simulate(ZENODO_DOI_SEARCH);
        final ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.8", USER_2_USERNAME);
        // If we publish using the API, then we have to add the Hoverfly statements to autocreate the DOI; easier to just change the DB
        final Duration duration = Duration.ofDays(2);
        final DateTimeFormatter psqlTimestampFormatter = DateTimeFormatter.ofPattern("YYYY-MM-dd hh:mm:ss");
        final String twoDaysAgo = LocalDateTime.now().minus(duration).format(psqlTimestampFormatter);
        // If we publish using the API, then we have to add the Hoverfly statements to autocreate the DOI; easier to just change the DB
        testingPostgres.runUpdateStatement("update workflow set ispublished = true, waseverpublic = true, latestreleasedate = '%s';".formatted(twoDaysAgo));
        List<Workflow> workflows = workflowsApi.updateDois(null, 1);
        assertEquals(0, workflows.size(), "Should update 0 workflows because lastreleasedate is more than a day ago");
        final String oneDayAgo = LocalDateTime.now().format(psqlTimestampFormatter);
        testingPostgres.runUpdateStatement("update workflow set ispublished = true, waseverpublic = true, latestreleasedate = '%s';".formatted(
                oneDayAgo));
        workflows = workflowsApi.updateDois(null, 1);
        assertEquals(2, workflows.size(), "Should update 2 workflows because lastreleasedate is within a day ago");
    }

    @Test
    void testExistingConceptDoiNotOverwritten(Hoverfly hoverfly) {
        hoverfly.simulate(ZENODO_DOI_SEARCH);
        final ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.8", USER_2_USERNAME);

        // If we publish using the API, then we have to add the Hoverfly statements to autocreate the DOI; easier to just change the DB
        testingPostgres.runUpdateStatement("update workflow set ispublished = true, waseverpublic = true;");

        List<Workflow> workflows = workflowsApi.updateDois(null, null);
        final String conceptDoiName = workflows.get(0).getConceptDois().get(DoiSelectionEnum.GITHUB.name()).getName();
        assertEquals(0, workflowsApi.updateDois(null, null).size(), "No workflows should be updated there are no new DOIs");

        // Hack to remove GITHUB initiator version DOIs; need to change name because of DB constraint that names must be unique
        testingPostgres.runUpdateStatement("update doi set name ='" + FAKE_VERSION_DOI + "', initiator = 'DOCKSTORE' where type = 'VERSION'");
        workflows = workflowsApi.updateDois(null, null);
        assertEquals(2, workflows.size(), "Concept DOI exists, but version DOIs are new");
        workflowsApi.getWorkflowVersions(workflows.get(0).getId(), null, null, null, null).forEach(wv -> assertEquals(VERSION_DOI, wv.getDois().get(DoiSelectionEnum.GITHUB.toString()).getName(),
                "Version DOI for GitHub initiator should be set"));

        testingPostgres.runUpdateStatement("update doi set name = '" + conceptDoiName.replace('7', '8') + "' where type = 'CONCEPT'");
        assertEquals(0, workflowsApi.updateDois(null, null).size(), "There is a new concept DOI, but the existing concept DOI takes precedence");

    }

}
