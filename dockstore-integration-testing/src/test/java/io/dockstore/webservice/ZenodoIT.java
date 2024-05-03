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

import static io.dockstore.client.cli.BaseIT.USER_2_USERNAME;
import static io.dockstore.common.CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH;
import static io.dockstore.common.CommonTestUtilities.getOpenAPIWebClient;
import static io.dockstore.common.Hoverfly.ZENODO_SIMULATION_SOURCE;
import static io.dockstore.common.Hoverfly.ZENODO_SIMULATION_URL;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubRelease;
import static io.dockstore.webservice.helpers.ZenodoHelper.FROZEN_VERSION_REQUIRED;
import static io.dockstore.webservice.helpers.ZenodoHelper.NO_ZENODO_USER_TOKEN;
import static io.dockstore.webservice.helpers.ZenodoHelper.PUBLISHED_ENTRY_REQUIRED;
import static io.dockstore.webservice.helpers.ZenodoHelper.VERSION_ALREADY_HAS_DOI;
import static io.dockstore.webservice.resources.WorkflowResource.A_WORKFLOW_MUST_BE_UNPUBLISHED_TO_RESTUB;
import static io.dockstore.webservice.resources.WorkflowResource.A_WORKFLOW_MUST_HAVE_NO_DOI_TO_RESTUB;
import static io.dockstore.webservice.resources.WorkflowResource.A_WORKFLOW_MUST_HAVE_NO_SNAPSHOT_TO_RESTUB;
import static io.dockstore.webservice.resources.WorkflowResource.MODIFY_AUTO_DOI_SETTING_IN_DOCKSTORE_YML;
import static io.specto.hoverfly.junit.core.HoverflyConfig.localConfigs;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.RepositoryConstants.DockstoreTesting;
import io.dockstore.common.TestingPostgres;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.core.TokenScope;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyMode;
import java.util.List;
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
@Tag(ConfidentialTest.NAME)
class ZenodoIT {

    // Set fake Dockstore Zenodo token so DOIs can automatically be created
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(DockstoreWebserviceApplication.class, CONFIDENTIAL_CONFIG_PATH,
            ConfigOverride.config("dockstoreZenodoAccessToken", "foobar"));

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

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
    void testGitHubAppAutomaticDoiCreation() {
        try (Hoverfly hoverfly = new Hoverfly(localConfigs().destination(ZENODO_SIMULATION_URL), HoverflyMode.SIMULATE)) {
            hoverfly.start();
            hoverfly.simulate(ZENODO_SIMULATION_SOURCE);
            final ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
            WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

            // Add a fake Zenodo token
            testingPostgres.runUpdateStatement(String.format(
                    "insert into token (id, dbcreatedate, dbupdatedate, content, refreshToken, tokensource, userid, username, scope) values "
                            + "(9001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'fakeToken', 'fakeRefreshToken', 'zenodo.org', 1, '%s', '%s')",
                    USER_2_USERNAME, TokenScope.AUTHENTICATE.name()));

            handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.9", USER_2_USERNAME);
            Workflow foobar2 = workflowsApi.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar2",
                    WorkflowSubClass.BIOWORKFLOW, "versions");
            final long foobar2Id = foobar2.getId();
            WorkflowVersion foobar2TagVersion09 = foobar2.getWorkflowVersions().stream().filter(version -> "0.9".equals(version.getName()))
                    .findFirst().orElse(null);
            assertNotNull(foobar2TagVersion09);
            final long foobar2VersionId = foobar2TagVersion09.getId();

            // No DOIs should've been automatically created because the workflow is unpublished
            assertTrue(foobar2.isEnableAutomaticDoiCreation());
            assertNull(foobar2TagVersion09.getDoiURL());
            // Publish workflow
            workflowsApi.publish1(foobar2.getId(), new PublishRequest().publish(true));

            // Release the tag again. Should automatically create a DOI
            handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.9", USER_2_USERNAME);
            foobar2TagVersion09 = workflowsApi.getWorkflowVersionById(foobar2.getId(), foobar2TagVersion09.getId(), "");
            assertTrue(foobar2TagVersion09.isFrozen(), "Version should've been automatically snapshotted");
            assertNotNull(foobar2TagVersion09.getDoiURL());
            assertTrue(foobar2TagVersion09.isDockstoreOwnedDoi());

            // Should not be able to request a DOI for the version because it already has one
            ApiException exception = assertThrows(ApiException.class,
                    () -> workflowsApi.requestDOIForWorkflowVersion(foobar2Id, foobar2VersionId, ""));
            assertTrue(exception.getMessage().contains(VERSION_ALREADY_HAS_DOI));

            // Release a different tag. Should automatically create DOI
            handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.8", USER_2_USERNAME);
            foobar2 = workflowsApi.getWorkflow(foobar2Id, "versions");
            WorkflowVersion foobar2TagVersion08 = foobar2.getWorkflowVersions().stream().filter(version -> "0.8".equals(version.getName()))
                    .findFirst().orElse(null);
            assertNotNull(foobar2TagVersion08);
            assertNotNull(foobar2TagVersion08.getDoiURL());
            assertTrue(foobar2TagVersion08.isDockstoreOwnedDoi());

            // Release a branch. Should not automatically create a DOI because it's not a tag
            handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/heads/master", USER_2_USERNAME);
            foobar2 = workflowsApi.getWorkflow(foobar2Id, "versions");
            WorkflowVersion foobar2BranchVersion = foobar2.getWorkflowVersions().stream()
                    .filter(version -> "master".equals(version.getName())).findFirst().orElse(null);
            assertNotNull(foobar2BranchVersion);
            assertNull(foobar2BranchVersion.getDoiURL());

            // Should throw an exception because GitHub App workflows can only modify the automatic DOI creation setting through the .dockstore.yml
            exception = assertThrows(ApiException.class, () -> workflowsApi.updateAutomaticDoiCreationSetting(foobar2Id, false));
            assertTrue(exception.getMessage().contains(MODIFY_AUTO_DOI_SETTING_IN_DOCKSTORE_YML));

            // Release a tag that has 'enableAutomaticDoiCreation: false' in the .dockstore.yml. Should not automatically create DOI because it's disabled for the workflow
            handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.10", USER_2_USERNAME);
            foobar2 = workflowsApi.getWorkflow(foobar2Id, "versions");
            assertFalse(foobar2.isEnableAutomaticDoiCreation());
            WorkflowVersion foobar2TagVersion07 = foobar2.getWorkflowVersions().stream().filter(version -> "0.10".equals(version.getName()))
                    .findFirst().orElse(null);
            assertNotNull(foobar2TagVersion07);
            assertNull(foobar2TagVersion07.getDoiURL());
        }
    }

    @Test
    void testGenerateDOIFrozenVersion() throws ApiException {
        try (Hoverfly hoverfly = new Hoverfly(localConfigs().destination(ZENODO_SIMULATION_URL), HoverflyMode.SIMULATE)) {
            hoverfly.start();
            hoverfly.simulate(ZENODO_SIMULATION_SOURCE);
            ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
            WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

            // register workflow
            Workflow githubWorkflow = workflowsApi.manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/hello.wdl",
                    "test-update-workflow", "wdl", "/test.json");

            Workflow workflowBeforeFreezing = workflowsApi.refresh1(githubWorkflow.getId(), false);
            WorkflowVersion master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master"))
                    .findFirst().get();
            final long workflowId = workflowBeforeFreezing.getId();
            final long versionId = master.getId();

            // DOI should only be generated for published workflows.
            ApiException exception = assertThrows(ApiException.class,
                    () -> workflowsApi.requestDOIForWorkflowVersion(workflowId, versionId, ""));
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

            // Should not be able to register DOI without Zenodo token
            exception = assertThrows(ApiException.class, () -> workflowsApi.requestDOIForWorkflowVersion(workflowId, versionId, ""));
            assertTrue(exception.getMessage().contains(NO_ZENODO_USER_TOKEN));

            // Add a fake Zenodo token
            testingPostgres.runUpdateStatement(String.format(
                    "insert into token (id, dbcreatedate, dbupdatedate, content, refreshToken, tokensource, userid, username, scope) values "
                            + "(9001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'fakeToken', 'fakeRefreshToken', 'zenodo.org', 1, '%s', '%s')",
                    USER_2_USERNAME, TokenScope.AUTHENTICATE.name()));

            workflowsApi.requestDOIForWorkflowVersion(workflowId, versionId, "");

            Workflow workflow = workflowsApi.getWorkflow(workflowId, "");
            assertNotNull(workflow.getConceptDoi());
            master = workflowsApi.getWorkflowVersionById(workflowId, versionId, "");
            assertNotNull(master.getDoiURL());

            // unpublish workflow
            workflowsApi.publish1(workflowBeforeFreezing.getId(), CommonTestUtilities.createOpenAPIPublishRequest(false));

            // should not be able to restub workflow with DOI even if it is unpublished
            exception = assertThrows(ApiException.class, () -> workflowsApi.restub(workflowId));
            assertTrue(exception.getMessage().contains(A_WORKFLOW_MUST_HAVE_NO_DOI_TO_RESTUB));

            // don't die horribly when stubbing something with snapshots, explain the error
            testingPostgres.runUpdateStatement("update workflow set conceptdoi = null");
            exception = assertThrows(ApiException.class, () -> workflowsApi.restub(workflowId));
            assertTrue(exception.getMessage().contains(A_WORKFLOW_MUST_HAVE_NO_SNAPSHOT_TO_RESTUB));
        }
    }
}
