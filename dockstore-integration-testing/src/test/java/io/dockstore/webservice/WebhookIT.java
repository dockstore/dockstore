/*
 *
 *    Copyright 2022 OICR, UCSC
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
 *
 */

package io.dockstore.webservice;

import static io.dockstore.client.cli.WorkflowIT.DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME;
import static io.dockstore.common.Hoverfly.ORCID_SIMULATION_SOURCE;
import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATH;
import static io.openapi.api.impl.ToolClassesApiServiceImpl.COMMAND_LINE_TOOL;
import static io.openapi.api.impl.ToolClassesApiServiceImpl.WORKFLOW;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.client.cli.BasicIT;
import io.dockstore.client.cli.OrganizationIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.common.ValidationConstants;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.api.LambdaEventsApi;
import io.dockstore.openapi.client.model.OrcidAuthorInformation;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.helpers.AppToolHelper;
import io.dockstore.webservice.jdbi.AppToolDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.languages.WDLHandler;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyMode;
import io.swagger.api.impl.ToolsImplCommon;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.MetadataApi;
import io.swagger.client.api.OrganizationsApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Collection;
import io.swagger.client.model.LambdaEvent;
import io.swagger.client.model.Organization;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Validation;
import io.swagger.client.model.Validation.TypeEnum;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.Workflow.DescriptorTypeEnum;
import io.swagger.client.model.Workflow.ModeEnum;
import io.swagger.client.model.WorkflowVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * @author agduncan
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class WebhookIT extends BaseIT {
    private static final int LAMBDA_ERROR = 418;
    private static final String DOCKSTORE_WHALESAY_WDL = "dockstore-whalesay-wdl";

    /**
     * You'd think there'd be an enum for this, but there's not
     */
    private static final String WORKFLOWS_ENTRY_SEARCH_TYPE = "WORKFLOWS";

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private final String workflowRepo = "DockstoreTestUser2/workflow-dockstore-yml";
    private final String githubFiltersRepo = "DockstoreTestUser2/dockstoreyml-github-filters-test";
    private final String installationId = "1179416";
    private final String toolAndWorkflowRepo = "DockstoreTestUser2/test-workflows-and-tools";
    private final String toolAndWorkflowRepoToolPath = "DockstoreTestUser2/test-workflows-and-tools/md5sum";
    private final String taggedToolRepo = "dockstore-testing/tagged-apptool";
    private final String taggedToolRepoPath = "dockstore-testing/tagged-apptool/md5sum";
    private final String authorsRepo = "DockstoreTestUser2/test-authors";
    private final String multiEntryRepo = "dockstore-testing/multi-entry";
    private final String unusualBranchWorkflowDockstoreYmlRepo = "DockstoreTestUser2/dockstore_workflow_cnv";
    private final String largeWorkflowDockstoreYmlRepo = "dockstore-testing/rodent-of-unusual-size";
    private final String whalesay2Repo = "DockstoreTestUser/dockstore-whalesay-2";
    private FileDAO fileDAO;
    private AppToolDAO appToolDAO;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        this.fileDAO = new FileDAO(sessionFactory);
        this.appToolDAO = new AppToolDAO(sessionFactory);

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use tokenDAO outside the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }
    @Test
    void testAppToolRSSFeedAndSiteMap() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        final PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);

        // There should be no apptools
        assertEquals(0, appToolDAO.findAllPublishedPaths().size());
        assertEquals(0, appToolDAO.findAllPublishedPathsOrderByDbupdatedate().size());

        // create and publish apptool
        usersApi.syncUserWithGitHub();
        AppToolHelper.registerAppTool(webClient);
        workflowApi.handleGitHubRelease(taggedToolRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        Workflow appTool = workflowApi.getWorkflowByPath("github.com/" + taggedToolRepoPath, APPTOOL, "versions");
        workflowApi.publish(appTool.getId(), publishRequest);

        // There should be 1 apptool.
        assertEquals(1, appToolDAO.findAllPublishedPaths().size());
        assertEquals(1, appToolDAO.findAllPublishedPathsOrderByDbupdatedate().size());

        final MetadataApi metadataApi = new MetadataApi(webClient);
        String rssFeed = metadataApi.rssFeed();
        assertTrue(rssFeed.contains("http://localhost/containers/github.com/dockstore-testing/tagged-apptool/md5sum"), "RSS feed should contain 1 apptool");

        String sitemap = metadataApi.sitemap();
        assertTrue(sitemap.contains("http://localhost/containers/github.com/dockstore-testing/tagged-apptool/md5sum"), "Sitemap with testing data should have 1 apptool");
    }

    @Test
    void testWorkflowMigration() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), workflowRepo, "/Dockstore.wdl",
                "foobar", "wdl", "/test.json");
        workflowApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.cwl", "",
                DescriptorLanguage.CWL.getShortName(), "/test.json");

        // Refresh should work
        workflow = workflowApi.refresh(workflow.getId(), false);
        assertEquals(ModeEnum.FULL, workflow.getMode(), "Workflow should be FULL mode");
        assertTrue(workflow.getWorkflowVersions().stream().allMatch(WorkflowVersion::isLegacyVersion), "All versions should be legacy");

        // Webhook call should convert workflow to DOCKSTORE_YML
        workflowApi.handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/tags/0.1", installationId);
        workflow = workflowApi.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", BIOWORKFLOW, "versions");
        assertEquals(ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Workflow should be DOCKSTORE_YML mode");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> !workflowVersion.isLegacyVersion()), "One version should be not legacy");

        // Refresh should now no longer work
        try {
            workflowApi.refresh(workflow.getId(), false);
            fail("Should fail on refresh and not reach this point");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode(), "Should not be able to refresh a dockstore.yml workflow.");
        }

        // Should be able to refresh a legacy version
        workflow = workflowApi.refreshVersion(workflow.getId(), "0.2", false);

        // Should not be able to refresh a GitHub App version
        try {
            workflowApi.refreshVersion(workflow.getId(), "0.1", false);
            fail("Should not be able to refresh");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }

        // Refresh a version that doesn't already exist
        try {
            workflowApi.refreshVersion(workflow.getId(), "dne", false);
            fail("Should not be able to refresh");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }

        List<Workflow> workflows = usersApi.addUserToDockstoreWorkflows(usersApi.getUser().getId(), "");
        assertTrue(workflows.stream().anyMatch(wf -> Objects.equals(wf.getMode(), ModeEnum.DOCKSTORE_YML)), "There should still be a dockstore.yml workflow");
        assertTrue(workflows.stream().anyMatch(wf -> Objects.equals(wf.getMode(), ModeEnum.STUB)), "There should be at least one stub workflow");

        // Test that refreshing a frozen version doesn't update the version
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET commitid = NULL where name = '0.2'");

        // Refresh before frozen should populate the commit id
        workflow = workflowApi.refreshVersion(workflow.getId(), "0.2", false);
        WorkflowVersion workflowVersion = workflow.getWorkflowVersions().stream().filter(wv -> Objects.equals(wv.getName(), "0.2")).findFirst().get();
        assertNotNull(workflowVersion.getCommitID());

        // Refresh after freezing should not update
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET commitid = NULL where name = '0.2'");

        // Freeze legacy version
        workflowVersion.setFrozen(true);
        List<WorkflowVersion> workflowVersions = workflowApi
                 .updateWorkflowVersion(workflow.getId(), Lists.newArrayList(workflowVersion));
        workflowVersion = workflowVersions.stream().filter(v -> v.getName().equals("0.2")).findFirst().get();
        assertTrue(workflowVersion.isFrozen());

        // Ensure refresh does not touch frozen legacy version
        workflow = workflowApi.refreshVersion(workflow.getId(), "0.2", false);
        assertNotNull(workflow);
        workflowVersion = workflow.getWorkflowVersions().stream().filter(wv -> Objects.equals(wv.getName(), "0.2")).findFirst().get();
        assertNull(workflowVersion.getCommitID());
    }

    /**
     * Tests discovering workflows. As background <code>BasicIT.USER_2_USERNAME</code> belongs to 3
     * GitHub organizations:
     * <ul>
     *     <li>dockstoretesting</li>
     *     <li>dockstore-testing</li>
     *     <li>DockstoreTestUser2</li>
     * </ul>
     *
     * and has rights to one repo not in any of those orgs:
     * <ul>
     *     <li>DockstoreTestUser/dockstore-whalesay-2</li>
     * </ul>
     */
    @Test
    void testAddUserToDockstoreWorkflows() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);

        registerWorkflowsForDiscoverTests(webClient);

        // Disassociate all entries from all users
        testingPostgres.runUpdateStatement("DELETE from user_entry");
        assertEquals(0, usersApi.getUserEntries(10, null, null).size(), "User should have 0 entries");

        // Discover again
        usersApi.addUserToDockstoreWorkflows(usersApi.getUser().getId(), "");

        //
        assertEquals(3, usersApi.getUserEntries(10, null, null).size(), "User should have 3 entries, 2 from DockstoreTestUser2 org and one from DockstoreTestUser/dockstore-whalesay-wdl");
    }

    /**
     * Tests that a user's workflow mapped to a repository that the user does not have GitHub permissions
     * to, gets removed.
     */
    @Test
    void testUpdateUserWorkflows() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);
        registerWorkflowsForDiscoverTests(webClient);

        // Create a workflow for a repo that USER_2_USERNAME does not have permissions to
        final String sql = String.format(
            "SELECT id FROM workflow WHERE organization = '%s' AND repository = '%s'", BasicIT.USER_1_USERNAME, DOCKSTORE_WHALESAY_WDL);
        final Long entryId = testingPostgres.runSelectStatement(sql, Long.class);
        final Long userId = usersApi.getUser().getId();

        // Make the user an owner of the workflow that the user should not have permissions to.
        final String userEntrySql =
            String.format("INSERT INTO user_entry(userid, entryid) VALUES (%s, %s)", userId,
                entryId);
        testingPostgres.runUpdateStatement(userEntrySql);
        assertEquals(4, usersApi.getUserEntries(10, null, WORKFLOWS_ENTRY_SEARCH_TYPE).size(), "User should have 4 workflows");

        final io.dockstore.openapi.client.api.UsersApi adminUsersApi =
            new io.dockstore.openapi.client.api.UsersApi(
                getOpenAPIWebClient(BaseIT.ADMIN_USERNAME, testingPostgres));

        // This should drop the most recently added workflow; user doesn't have corresponding GitHub permissions
        adminUsersApi.checkWorkflowOwnership();
        assertEquals(3, usersApi.getUserEntries(10, null, WORKFLOWS_ENTRY_SEARCH_TYPE).size(), "User should now have 3 workflows");

    }

    private void registerWorkflowsForDiscoverTests(final io.dockstore.openapi.client.ApiClient webClient) {
        final io.dockstore.openapi.client.api.WorkflowsApi workflowsApi =
            new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        // Register 2 workflows in DockstoreTestUser2 org (user belongs to org)
        final String githubFriendlyName = SourceControl.GITHUB.getFriendlyName();
        workflowsApi
            .manualRegister(githubFriendlyName, workflowRepo, "/Dockstore.wdl",
                "foobar", "wdl", "/test.json");
        workflowsApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.cwl", "",
            DescriptorLanguage.CWL.getShortName(), "/test.json");

        // Register 1 workflow for DockstoreTestUser/dockstore-whalesay-2 (user has access to that repo only)
        workflowsApi
            .manualRegister(githubFriendlyName, whalesay2Repo, "/Dockstore.wdl",
                "foobar", "wdl", "/test.json");

        // Register DockstoreTestUser/dockstore-whalesay-wdl workflow (user does not have access to that repo nor org)
        testingPostgres.addUnpublishedWorkflow(SourceControl.GITHUB, BasicIT.USER_1_USERNAME, DOCKSTORE_WHALESAY_WDL, DescriptorLanguage.WDL);

        final io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);
        assertEquals(3, usersApi.getUserEntries(10, null, WORKFLOWS_ENTRY_SEARCH_TYPE).size(),
            "User should have 3 workflows, 2 from DockstoreTestUser2 org and one from DockstoreTestUser/dockstore-whalesay-wdl");

    }

    /**
     * This tests just the github release process for installs
     */
    @Test
    void testGitHubReleaseInstallationOnly() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi client = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);

        // Track install event
        client.handleGitHubInstallation(installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertTrue(workflowCount >= 2, "should see 2 workflows from the .dockstore.yml from the master branch");
    }

    /**
     * This tests just the github release process for installs but with a very large repo.
     * This repo has had the .dockstore.yml added a while back (10 months)
     */
    @Test
    @Disabled("this repo is huge and this test takes forever to run (but not because of the .dockstore.yml detection code), can use this for manual testing but probably do not want this on CI")
    void testGitHubReleaseLargeInstallationOnly() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi client = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);

        // Track install event
        client.handleGitHubInstallation(installationId, largeWorkflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertTrue(workflowCount >= 12, "should see a lot of workflows from the .dockstore.yml from the master branch from some branch or another");
    }

    /**
     * This tests just the github release process for installs but the .dockstore.yml is in a develop branch
     * and missing from the default branch
     */
    @Test
    void testGitHubReleaseFeatureBranchInstallationOnly() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi client = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);

        // Track install event
        client.handleGitHubInstallation(installationId, unusualBranchWorkflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount, "should see a workflow from the .dockstore.yml");
        long workflowVersionCount = testingPostgres.runSelectStatement("select count(*) from workflowversion where reference like 'develop'", long.class);
        assertEquals(1, workflowVersionCount, "should see a workflow from the .dockstore.yml from a specific branch");
    }


    /**
     * This tests the GitHub release process
     */
    @Test
    void testGitHubReleaseNoWorkflowOnDockstore() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi client = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);
        LambdaEventsApi lambdaEventsApi = new LambdaEventsApi(webClient);

        // Track install event
        client.handleGitHubInstallation(installationId, workflowRepo, BasicIT.USER_2_USERNAME);

        // Release 0.1 on GitHub - one new wdl workflow
        client.handleGitHubRelease("refs/tags/0.1", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertTrue(workflowCount >= 2, "should see 2 workflows from the .dockstore.yml from the master branch");

        // Ensure that new workflow is created and is what is expected
        io.dockstore.openapi.client.model.Workflow workflow = getFoobar1Workflow(client);
        assertEquals(io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType(), "Should be a WDL workflow");
        assertEquals(io.dockstore.openapi.client.model.Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Should be type DOCKSTORE_YML");
        assertEquals(1, workflow.getWorkflowVersions().stream().filter(v -> v.getName().contains("0.1")).toList().size(), "Should have one version 0.1");
        assertEquals("A repo that includes .dockstore.yml", workflow.getTopicAutomatic());

        // Release 0.2 on GitHub - one existing wdl workflow, one new cwl workflow
        client.handleGitHubRelease("refs/tags/0.2", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(2, workflowCount);

        // Ensure that existing workflow is updated
        workflow = getFoobar1Workflow(client);

        // Ensure that new workflow is created and is what is expected
        io.dockstore.openapi.client.model.Workflow workflow2 = getFoobar2Workflow(client);
        assertEquals(io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum.CWL, workflow2.getDescriptorType(), "Should be a CWL workflow");
        assertEquals(io.dockstore.openapi.client.model.Workflow.ModeEnum.DOCKSTORE_YML, workflow2.getMode(), "Should be type DOCKSTORE_YML");
        assertEquals(1, workflow2.getWorkflowVersions().stream().filter(v -> v.getName().contains("0.2")).toList().size(), "Should have one version 0.2");


        // Unset the license information to simulate license change
        testingPostgres.runUpdateStatement("update workflow set licensename=null");
        // Unset topicAutomatic to simulate a topicAutomatic change
        testingPostgres.runUpdateStatement("update workflow set topicAutomatic=null");
        // Branch master on GitHub - updates two existing workflows
        client.handleGitHubRelease("refs/heads/master", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        List<io.dockstore.openapi.client.model.Workflow> workflows = new ArrayList<>();
        workflows.add(workflow);
        workflows.add(workflow2);
        assertEquals(2, workflows.size(), "Should only have two workflows");
        workflows.forEach(workflowIndividual -> {
            assertEquals("Apache License 2.0", workflowIndividual.getLicenseInformation().getLicenseName(), "Should be able to get license after manual GitHub App version update");
            assertEquals("A repo that includes .dockstore.yml", workflowIndividual.getTopicAutomatic(), "Should be able to get topic from GitHub after GitHub App version update");
        });

        workflow = getFoobar1Workflow(client);
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")),
            "Should have a master version.");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.1")),
            "Should have a 0.1 version.");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")),
            "Should have a 0.2 version.");

        workflow2 = getFoobar2Workflow(client);
        assertTrue(workflow2.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")),
            "Should have a master version.");
        assertTrue(workflow2.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")),
            "Should have a 0.2 version.");



        // Master version should have metadata set
        Optional<io.dockstore.openapi.client.model.WorkflowVersion> masterVersion = workflow.getWorkflowVersions().stream().filter((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertEquals("Test User", masterVersion.get().getAuthor(), "Should have author set");
        assertEquals("test@dockstore.org", masterVersion.get().getEmail(), "Should have email set");
        assertEquals("This is a description", masterVersion.get().getDescription(), "Should have email set");

        masterVersion = workflow2.getWorkflowVersions().stream().filter((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertEquals("Test User", masterVersion.get().getAuthor(), "Should have author set");
        assertTrue(masterVersion.get().isValid(), "Should be valid");
        assertEquals("test@dockstore.org", masterVersion.get().getEmail(), "Should have email set");
        assertEquals("This is a description", masterVersion.get().getDescription(), "Should have email set");

        boolean hasLegacyVersion = workflow.getWorkflowVersions().stream().anyMatch(
                io.dockstore.openapi.client.model.WorkflowVersion::isLegacyVersion);
        assertFalse(hasLegacyVersion, "Workflow should not have any legacy refresh versions.");

        // Delete tag 0.2
        client.handleGitHubBranchDeletion(workflowRepo, BasicIT.USER_2_USERNAME, "refs/tags/0.2", installationId);
        workflow = getFoobar1Workflow(client);
        assertTrue(workflow.getWorkflowVersions().stream().noneMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")),
            "Should not have a 0.2 version.");
        workflow2 = getFoobar2Workflow(client);
        assertTrue(workflow2.getWorkflowVersions().stream().noneMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")),
            "Should not have a 0.2 version.");

        // Add version that doesn't exist
        try {
            client.handleGitHubRelease("refs/heads/idonotexist", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
            fail("Should fail and not reach this point");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            List<io.dockstore.openapi.client.model.LambdaEvent> failureEvents = usersApi.getUserGitHubEvents("0", 10);
            assertEquals(1, failureEvents.stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count(), "There should be 1 unsuccessful event");
        }

        // There should be 5 successful lambda events
        List<io.dockstore.openapi.client.model.LambdaEvent> events = usersApi.getUserGitHubEvents("0", 10);
        assertEquals(6, events.stream().filter(io.dockstore.openapi.client.model.LambdaEvent::isSuccess).count(), "There should be 5 successful events");

        // Test pagination for user github events
        events = usersApi.getUserGitHubEvents("2", 2);
        assertEquals(2, events.size(), "There should be 2 events (id 3 and 4)");
        assertTrue(events.stream().anyMatch(lambdaEvent -> Objects.equals(4L, lambdaEvent.getId())), "Should have event with ID 4");
        assertTrue(events.stream().anyMatch(lambdaEvent -> Objects.equals(5L, lambdaEvent.getId())), "Should have event with ID 5");

        // Test the organization events endpoint
        List<io.dockstore.openapi.client.model.LambdaEvent> orgEvents = lambdaEventsApi.getLambdaEventsByOrganization("DockstoreTestUser2", "0", 10);
        assertEquals(7, orgEvents.size(), "There should be 7 events");

        // Test pagination
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization("DockstoreTestUser2", "2", 2);
        assertEquals(2, orgEvents.size(), "There should be 2 events (id 3 and 4)");
        assertTrue(orgEvents.stream().anyMatch(lambdaEvent -> Objects.equals(4L, lambdaEvent.getId())), "Should have event with ID 4");
        assertTrue(orgEvents.stream().anyMatch(lambdaEvent -> Objects.equals(5L, lambdaEvent.getId())), "Should have event with ID 5");

        // Change organization to test filter
        testingPostgres.runUpdateStatement("UPDATE lambdaevent SET repository = 'workflow-dockstore-yml', organization = 'DockstoreTestUser3' WHERE id = '1'");

        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization("DockstoreTestUser2", "0", 10);
        assertEquals(6, orgEvents.size(), "There should now be 6 events");

        try {
            lambdaEventsApi.getLambdaEventsByOrganization("IAmMadeUp", "0", 10);
            fail("Should not reach this statement");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getCode(), "Should fail because user cannot access org.");
        }

        // Try adding version with empty test parameter file (should work)
        client.handleGitHubRelease("refs/heads/emptytestparameter", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        workflow2 = getFoobar2Workflow(client);
        assertTrue(workflow2.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "emptytestparameter")).findFirst().get().isValid(),
            "Should have emptytestparameter version that is valid");
        testValidationUpdate(client);
        testDefaultVersion(client);
    }

    @Test
    void testLambdaEvents() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);
        final LambdaEventsApi lambdaEventsApi = new LambdaEventsApi(webClient);
        final List<String> userOrganizations = usersApi.getUserOrganizations("github.com");
        assertTrue(userOrganizations.contains("dockstoretesting")); // Org user is member of
        assertTrue(userOrganizations.contains("DockstoreTestUser2")); // The GitHub account
        final String dockstoreTestUser = "DockstoreTestUser";
        assertTrue(userOrganizations.contains(dockstoreTestUser)); // User has access to only one repo in the org, DockstoreTestUser/dockstore-whalesay-2

        assertEquals(0, lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTestUser, "0", 10).size(), "No events at all works");

        testingPostgres.runUpdateStatement("INSERT INTO lambdaevent(message, repository, organization) values ('whatevs', 'repo-no-access', 'DockstoreTestUser')");
        assertEquals(0, lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTestUser, "0", 10).size(), "Can't see event for repo with no access");

        testingPostgres.runUpdateStatement("INSERT INTO lambdaevent(message, repository, organization) values ('whatevs', 'dockstore-whalesay-2', 'DockstoreTestUser')");
        final List<io.dockstore.openapi.client.model.LambdaEvent> events =
            lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTestUser, "0", 10);
        assertEquals(1, events.size(), "Can see event for repo with access, not one without");
    }

    private void testDefaultVersion(io.dockstore.openapi.client.api.WorkflowsApi client) {
        io.dockstore.openapi.client.model.Workflow workflow2 = getFoobar2Workflow(client);
        assertNull(workflow2.getDefaultVersion());
        io.dockstore.openapi.client.model.Workflow workflow = getFoobar1Workflow(client);
        assertNull(workflow.getDefaultVersion());
        client.handleGitHubRelease("refs/tags/0.4", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        workflow2 = getFoobar2Workflow(client);
        assertEquals("0.4", workflow2.getDefaultVersion(), "The new tag says the latest tag should be the default version");
        workflow = getFoobar1Workflow(client);
        assertNull(workflow.getDefaultVersion());

    }

    private io.dockstore.openapi.client.model.Workflow getFoobar1Workflow(io.dockstore.openapi.client.api.WorkflowsApi client) {
        return client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
    }

    private Workflow getFoobar1Workflow(WorkflowsApi client) {
        return client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", BIOWORKFLOW, "versions");
    }

    private io.dockstore.openapi.client.model.Workflow getFoobar2Workflow(io.dockstore.openapi.client.api.WorkflowsApi client) {
        return client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");
    }

    /**
     * This tests that when a version was invalid, a new GitHub release will retrigger the validation
     * @param client    WorkflowsApi
     */
    private void testValidationUpdate(io.dockstore.openapi.client.api.WorkflowsApi client) {
        testingPostgres.runUpdateStatement("update workflowversion set valid='f'");

        io.dockstore.openapi.client.model.Workflow workflow2 = getFoobar2Workflow(client);
        Optional<io.dockstore.openapi.client.model.WorkflowVersion> masterVersion = workflow2.getWorkflowVersions().stream().filter((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertFalse(masterVersion.get().isValid(), "Master version should be invalid because it was manually changed");

        client.handleGitHubRelease("refs/heads/master", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        workflow2 = getFoobar2Workflow(client);
        masterVersion = workflow2.getWorkflowVersions().stream().filter((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertTrue(masterVersion.get().isValid(), "Master version should be valid after GitHub App triggered again");
    }

    /**
     * This tests deleting a GitHub App workflow's default version
     */
    @Test
    void testDeleteDefaultWorkflowVersion() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Add 1.0 tag and set as default version
        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        Workflow workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size(), "should have 1 version");
        assertNull(workflow.getDefaultVersion(), "should have no default version until set");
        workflow = client.updateWorkflowDefaultVersion(workflow.getId(), workflow.getWorkflowVersions().get(0).getName());
        assertNotNull(workflow.getDefaultVersion(), "should have a default version after setting");

        // Add 2.0 tag
        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/2.0", installationId);
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals(2, workflow.getWorkflowVersions().size(), "should have 2 versions");

        // Delete 1.0 tag, should reassign 2.0 as the default version
        client.handleGitHubBranchDeletion(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size(), "should have 1 version after deletion");
        assertNotNull(workflow.getDefaultVersion(), "should have reassigned the default version during deletion");

        // Publish workflow
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        client.publish(workflow.getId(), publishRequest);
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertTrue(workflow.isIsPublished());

        // Delete 2.0 tag, unset default version
        client.handleGitHubBranchDeletion(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/2.0", installationId);
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals(0, workflow.getWorkflowVersions().size(), "should have 0 versions after deletion");
        assertNull(workflow.getDefaultVersion(), "should have no default version after final version is deleted");
        assertFalse(workflow.isIsPublished(), "should not be published if it has 0 versions");
    }

    /**
     * This tests calling refresh on a workflow with a Dockstore.yml
     */
    @Test
    void testManualRefreshWorkflowWithGitHubApp() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Release 0.1 on GitHub - one new wdl workflow
        client.handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/tags/0.1", installationId);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        Workflow workflow = getFoobar1Workflow(client);
        assertEquals("Apache License 2.0", workflow.getLicenseInformation().getLicenseName(), "Should be able to get license after GitHub App register");

        // Ensure that new workflow is created and is what is expected

        assertEquals(DescriptorTypeEnum.WDL, workflow.getDescriptorType(), "Should be a WDL workflow");
        assertEquals(ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Should be type DOCKSTORE_YML");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.1")), "Should have a 0.1 version.");
        boolean hasLegacyVersion = workflow.getWorkflowVersions().stream().anyMatch(WorkflowVersion::isLegacyVersion);
        assertFalse(hasLegacyVersion, "Workflow should not have any legacy refresh versions.");

        // Refresh
        try {
            client.refresh(workflow.getId(), false);
            fail("Should fail on refresh and not reach this point");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode(), "Should not be able to refresh a dockstore.yml workflow.");
        }
    }

    /**
     * This tests the GitHub release process does not work for users that do not exist on Dockstore
     */
    @Test
    void testGitHubReleaseNoWorkflowOnDockstoreNoUser() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        try {
            client.handleGitHubRelease(workflowRepo, "thisisafakeuser", "refs/tags/0.1", installationId);
            fail("Should not reach this statement.");
        } catch (ApiException ex) {
            assertEquals(LAMBDA_ERROR, ex.getCode(), "Should not be able to add a workflow when user does not exist on Dockstore.");
        }
    }

    /**
     * Tests:
     * An unpublished workflow with invalid versions can have its descriptor type changed
     * The workflow can then have new valid versions registered
     * The valid workflow cannot have its descriptor type changed anymore (because it's valid)
     * The published workflow cannot have its descriptor type changed anymore (because it's published)
     */
    @Test
    void testDescriptorChange() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.ApiClient openAPIWebClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(openAPIWebClient);
        WorkflowsApi client = new WorkflowsApi(webClient);
        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/missingPrimaryDescriptor", installationId);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);
        // Ensure that new workflow is created and is what is expected
        Workflow workflow = getFoobar1Workflow(client);
        assertEquals(DescriptorTypeEnum.WDL, workflow.getDescriptorType(), "Should be a WDL workflow");
        assertEquals(ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Should be type DOCKSTORE_YML");
        assertEquals(1, workflow.getWorkflowVersions().size(), "Should have one version");
        assertFalse(workflow.getWorkflowVersions().get(0).isValid(), "Should be invalid (wrong language, bad version)");

        workflowsApi.updateDescriptorType(workflow.getId(), DescriptorLanguage.CWL.toString());
        io.dockstore.openapi.client.model.Workflow updatedWorkflowAfterModifyingDescriptorType = workflowsApi.getWorkflow(workflow.getId(), "");
        assertEquals(io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum.CWL, updatedWorkflowAfterModifyingDescriptorType.getDescriptorType(),
            "The descriptor language should have been changed");
        assertEquals(0, updatedWorkflowAfterModifyingDescriptorType.getWorkflowVersions().size(), "The old versions should have been removed");

        workflowsApi.updateDescriptorType(workflow.getId(), DescriptorLanguage.WDL.toString());
        updatedWorkflowAfterModifyingDescriptorType = workflowsApi.getWorkflow(workflow.getId(), "versions");
        assertEquals(io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum.WDL, updatedWorkflowAfterModifyingDescriptorType.getDescriptorType(),
            "The descriptor language should have been changed");
        assertEquals(0, updatedWorkflowAfterModifyingDescriptorType.getWorkflowVersions().size(), "The old versions should have been removed");

        // Release 0.1 on GitHub - one new wdl workflow
        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/tags/0.1", installationId);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new workflow is created and is what is expected
        workflow = getFoobar1Workflow(client);
        assertEquals(DescriptorTypeEnum.WDL, workflow.getDescriptorType(), "Should be a WDL workflow");
        assertEquals(ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Should be type DOCKSTORE_YML");
        assertEquals(1, workflow.getWorkflowVersions().size(), "Should have one version 0.1");
        assertTrue(workflow.getWorkflowVersions().get(0).isValid(), "Should be valid");
        try {
            workflowsApi
                    .updateDescriptorType(workflow.getId(), DescriptorLanguage.CWL.toString());
            fail("Should not be able to change the descriptor type of a workflow that has valid versions");
        } catch (io.dockstore.openapi.client.ApiException e) {
            assertEquals("Cannot change descriptor type of a valid workflow", e.getMessage());
        }
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        client.publish(workflow.getId(), publishRequest);
        try {
            workflowsApi.updateDescriptorType(workflow.getId(), DescriptorLanguage.WDL.toString());
            fail("Should also not be able to change the descriptor type of a workflow that is published");
        } catch (io.dockstore.openapi.client.ApiException e) {
            assertEquals("Cannot change descriptor type of a published workflow", e.getMessage());
        }

    }

    /**
     * This tests the GitHub release process when the dockstore.yml is
     * * Missing the primary descriptor
     * * Missing a test parameter file
     * * Has an unknown property
     */
    @Test
    void testInvalidDockstoreYmlFiles() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);

        // Release 0.1 on GitHub - one new wdl workflow
        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/tags/0.1", installationId);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new workflow is created and is what is expected
        Workflow workflow = getFoobar1Workflow(client);
        assertEquals(DescriptorTypeEnum.WDL, workflow.getDescriptorType(), "Should be a WDL workflow");
        assertEquals(ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Should be type DOCKSTORE_YML");
        assertEquals(1, workflow.getWorkflowVersions().size(), "Should have one version 0.1");
        assertTrue(workflow.getWorkflowVersions().get(0).isValid(), "Should be valid");
        assertNull(getLatestLambdaEventMessage("0", usersApi), "Lambda event message should be empty");

        // Push missingPrimaryDescriptor on GitHub - one existing wdl workflow, missing primary descriptor
        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/missingPrimaryDescriptor", installationId);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new version is in the correct state (invalid)
        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", BIOWORKFLOW, "validations");
        assertNotNull(workflow);
        assertEquals(2, workflow.getWorkflowVersions().size(), "Should have two versions");

        WorkflowVersion missingPrimaryDescriptorVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "missingPrimaryDescriptor")).findFirst().get();
        assertFalse(missingPrimaryDescriptorVersion.isValid(), "Version should be invalid");

        // Check existence of files and validations
        List<SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(missingPrimaryDescriptorVersion.getId());
        assertTrue(sourceFiles.stream().anyMatch(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), DOCKSTORE_YML_PATH)), "Should have .dockstore.yml file");
        assertTrue(sourceFiles.stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/doesnotexist.wdl")).findFirst().isEmpty(),
            "Should not have doesnotexist.wdl file");
        assertFalse(missingPrimaryDescriptorVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), TypeEnum.DOCKSTORE_YML)).findFirst().get().isValid(),
            "Should have invalid .dockstore.yml");
        assertFalse(missingPrimaryDescriptorVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), TypeEnum.DOCKSTORE_WDL)).findFirst().get().isValid(),
            "Should have invalid doesnotexist.wdl");
        assertTrue(getLatestLambdaEventMessage("0", usersApi).contains("descriptor"), "Refers to missing primary descriptor");

        // Push missingTestParameterFile on GitHub - one existing wdl workflow, missing a test parameter file
        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/missingTestParameterFile", installationId);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new version is in the correct state (invalid)
        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", BIOWORKFLOW, "validations");
        assertNotNull(workflow);
        assertEquals(3, workflow.getWorkflowVersions().size(), "Should have three versions");

        WorkflowVersion missingTestParameterFileVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "missingTestParameterFile")).findFirst().get();
        assertTrue(missingTestParameterFileVersion.isValid(), "Version should be valid (missing test parameter doesn't make the version invalid)");

        // Check existence of files and validations
        sourceFiles = fileDAO.findSourceFilesByVersion(missingTestParameterFileVersion.getId());
        assertTrue(sourceFiles.stream().anyMatch(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), DOCKSTORE_YML_PATH)), "Should have .dockstore.yml file");
        assertTrue(sourceFiles.stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/test/doesnotexist.txt")).findFirst().isEmpty(),
            "Should not have /test/doesnotexist.txt file");
        assertTrue(sourceFiles.stream().anyMatch(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/Dockstore2.wdl")), "Should have Dockstore2.wdl file");
        assertFalse(missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), TypeEnum.DOCKSTORE_YML)).findFirst().get().isValid(),
            "Should have invalid .dockstore.yml");
        assertTrue(missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), TypeEnum.DOCKSTORE_WDL)).findFirst().get().isValid(),
            "Should have valid Dockstore2.wdl");
        assertTrue(getLatestLambdaEventMessage("0", usersApi).contains("/idonotexist.json"), "Refers to missing test file");

        // Push unknownProperty on GitHub - one existing wdl workflow, incorrectly spelled testParameterFiles property
        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/unknownProperty", installationId);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new version is in the correct state (valid)
        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", BIOWORKFLOW, "validations");
        assertNotNull(workflow);
        assertEquals(4, workflow.getWorkflowVersions().size(), "Should have four versions");

        WorkflowVersion unknownPropertyVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "unknownProperty")).findFirst().get();
        assertTrue(unknownPropertyVersion.isValid(), "Version should be valid (unknown property doesn't make the version invalid)");

        // Check existence of files and validations
        sourceFiles = fileDAO.findSourceFilesByVersion(unknownPropertyVersion.getId());
        assertTrue(sourceFiles.stream().anyMatch(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), DOCKSTORE_YML_PATH)), "Should have .dockstore.yml file");
        assertTrue(sourceFiles.stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/dockstore.wdl.json")).findFirst().isEmpty(),
            "Should not have /dockstore.wdl.json file");
        assertTrue(sourceFiles.stream().anyMatch(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/Dockstore2.wdl")), "Should have Dockstore2.wdl file");
        assertFalse(missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), TypeEnum.DOCKSTORE_YML)).findFirst().get().isValid(),
            "Should have invalid .dockstore.yml");
        assertTrue(missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), TypeEnum.DOCKSTORE_WDL)).findFirst().get().isValid(),
            "Should have valid Dockstore2.wdl");
        assertTrue(getLatestLambdaEventMessage("0", usersApi).contains("testParameterFilets"), "Refers to misspelled property");

        // There should be 4 successful lambda events
        List<LambdaEvent> events = usersApi.getUserGitHubEvents("0", 10);
        assertEquals(4, events.stream().filter(LambdaEvent::isSuccess).count(), "There should be 4 successful events");

        final int versionCountBeforeInvalidDockstoreYml = getFoobar1Workflow(client).getWorkflowVersions().size();
        // Push branch with invalid dockstore.yml
        try {
            client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/invalidDockstoreYml", installationId);
            fail("Should not reach this statement");
        } catch (ApiException ex) {
            List<LambdaEvent> failEvents = usersApi.getUserGitHubEvents("0", 10);
            assertEquals(1, failEvents.stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count(), "There should be 1 unsuccessful event");
            assertEquals(versionCountBeforeInvalidDockstoreYml, getFoobar1Workflow(client).getWorkflowVersions().size(), "Number of versions should be the same");
        }
    }

    private LambdaEvent getLatestLambdaEvent(String user, UsersApi usersApi) {
        return usersApi.getUserGitHubEvents(user, 1).get(0);
    }

    private String getLatestLambdaEventMessage(String user, UsersApi usersApi) {
        return getLatestLambdaEvent(user, usersApi).getMessage();
    }

    /**
     * Test that a .dockstore.yml workflow has the expected path for its test parameter file.
     */
    @Test
    void testTestParameterPaths() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/master", installationId);
        Workflow workflow = getFoobar1Workflow(client);
        WorkflowVersion version = workflow.getWorkflowVersions().get(0);
        List<SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(version.getId());
        assertTrue(sourceFiles.stream().anyMatch(sourceFile -> sourceFile.getPath().equals("/dockstore.wdl.json")), "Test file should have the expected path");
    }

    /**
     * This tests the GitHub release with .dockstore.yml located in /.github/.dockstore.yml
     */
    @Test
    void testGithubDirDockstoreYml() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        Workflow workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "");
        assertNotNull(workflow);
    }

    /**
     * This tests filters functionality in .dockstore.yml
     * <a href="https://github.com/DockstoreTestUser2/dockstoreyml-github-filters-test">...</a>
     * Workflow filters are configured as follows:
     * * filterbranch filters for "develop"
     * * filtertag filters for "1.0"
     * * filtermulti filters for "dev*" and "1.*"
     * * filternone has no filters (accepts all tags & branches)
     * * filterregexerror has a filter with an invalid regex string (matches nothing)
     */
    @Test
    void testDockstoreYmlFilters() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // master should be excluded by all of the workflows with filters
        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/heads/master", installationId);
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filterbranch", BIOWORKFLOW, ""));
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filtertag", BIOWORKFLOW, ""));
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filtermulti", BIOWORKFLOW, ""));
        Workflow workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size());
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filterregexerror", BIOWORKFLOW, ""));

        // tag 2.0 should be excluded by all of the workflows with filters
        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/2.0", installationId);
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filterbranch", BIOWORKFLOW, ""));
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filtertag", BIOWORKFLOW, ""));
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filtermulti", BIOWORKFLOW, ""));
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals(2, workflow.getWorkflowVersions().size());
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filterregexerror", BIOWORKFLOW, ""));

        // develop2 should be accepted by the heads/dev* filter in filtermulti
        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/heads/develop2", installationId);
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filterbranch", BIOWORKFLOW, ""));
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filtertag", BIOWORKFLOW, ""));
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filtermulti", BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals(3, workflow.getWorkflowVersions().size());
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filterregexerror", BIOWORKFLOW, ""));

        // tag 1.1 should be accepted by the 1.* filter in filtermulti
        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.1", installationId);
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filterbranch", BIOWORKFLOW, ""));
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filtertag", BIOWORKFLOW, ""));
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filtermulti", BIOWORKFLOW, "versions");
        assertEquals(2, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals(4, workflow.getWorkflowVersions().size());
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filterregexerror", BIOWORKFLOW, ""));

        // tag 1.0 should be accepted by tags/1.0 in filtertag and 1.* in filtermulti
        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filterbranch", BIOWORKFLOW, ""));
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filtertag", BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filtermulti", BIOWORKFLOW, "versions");
        assertEquals(3, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals(5, workflow.getWorkflowVersions().size());
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filterregexerror", BIOWORKFLOW, ""));

        // develop should be accepted by develop in filterbranch and heads/dev* in filtermulti
        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/heads/develop", installationId);
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filterbranch", BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filtertag", BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filtermulti", BIOWORKFLOW, "versions");
        assertEquals(4, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals(6, workflow.getWorkflowVersions().size());
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filterregexerror", BIOWORKFLOW, ""));
    }

    /**
     * This tests publishing functionality in .dockstore.yml
     */
    @Test
    void testDockstoreYmlPublish() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/heads/publish", installationId);
        Workflow workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "");
        assertEquals(1, testingPostgres.getPublishEventCountForWorkflow(workflow.getId()));
        assertEquals(0, testingPostgres.getUnpublishEventCountForWorkflow(workflow.getId()));
        assertTrue(workflow.isIsPublished());
        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/heads/unpublish", installationId);
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "");
        assertFalse(workflow.isIsPublished());
        assertEquals(1, testingPostgres.getPublishEventCountForWorkflow(workflow.getId()));
        assertEquals(1, testingPostgres.getUnpublishEventCountForWorkflow(workflow.getId()));
    }

    /**
     * This tests multiple authors functionality in .dockstore.yml and descriptor file.
     * If there are authors in .dockstore.yml, then only .dockstore.yml authors are saved, even if the descriptor has an author.
     * If there are no authors in .dockstore.yml, then authors from the descriptor are saved.
     */
    @Test
    void testDockstoreYmlAuthors() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        String wdlWorkflowRepoPath = String.format("github.com/%s/%s", authorsRepo, "foobar");
        String cwlWorkflowRepoPath = String.format("github.com/%s/%s", authorsRepo, "foobar2");
        String nextflowWorkflowRepoPath = String.format("github.com/%s/%s", authorsRepo, "foobar3");
        String wdl2WorkflowRepoPath = String.format("github.com/%s/%s", authorsRepo, "foobar4");
        Workflow workflow;
        WorkflowVersion version;

        // Workflows containing 1 descriptor author and multiple .dockstore.yml authors.
        // If the .dockstore.yml specifies an author, then only the .dockstore.yml's authors should be saved
        client.handleGitHubRelease(authorsRepo, BasicIT.USER_2_USERNAME, "refs/heads/main", installationId);
        // WDL workflow
        workflow = client.getWorkflowByPath(wdlWorkflowRepoPath, BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(2, version.getAuthors().size());
        assertEquals(2, version.getOrcidAuthors().size());
        final String wdlDescriptorAuthorName = "Descriptor Author";
        assertTrue(version.getAuthors().stream().noneMatch(author -> author.getName().equals(wdlDescriptorAuthorName)), "Should not have any author from the descriptor");
        // CWL workflow
        workflow = client.getWorkflowByPath(cwlWorkflowRepoPath, BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(1, version.getAuthors().size());
        assertEquals(1, version.getOrcidAuthors().size());
        final String cwlDescriptorAuthorName = "Test User";
        assertTrue(version.getAuthors().stream().noneMatch(author -> author.getName().equals(cwlDescriptorAuthorName)), "Should not have any author from the descriptor");
        // Nextflow workflow
        workflow = client.getWorkflowByPath(nextflowWorkflowRepoPath, BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(1, version.getAuthors().size());
        assertEquals(1, version.getOrcidAuthors().size());
        final String nextflowDescriptorAuthorName = "Nextflow Test Author";
        assertTrue(version.getAuthors().stream().noneMatch(author -> author.getName().equals(nextflowDescriptorAuthorName)), "Should not have any author from the descriptor");
        // WDL workflow containing 1 descriptor author, 1 ORCID author, and 0 non-ORCID authors
        workflow = client.getWorkflowByPath(wdl2WorkflowRepoPath, BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(0, version.getAuthors().size());
        assertEquals(1, version.getOrcidAuthors().size());
        assertTrue(version.getAuthors().stream().noneMatch(author -> author.getName().equals(wdlDescriptorAuthorName)), "Should not have any author from the descriptor");

        // WDL workflow containing only .dockstore.yml authors
        client.handleGitHubRelease(authorsRepo, BasicIT.USER_2_USERNAME, "refs/heads/onlyDockstoreYmlAuthors", installationId);
        workflow = client.getWorkflowByPath(wdlWorkflowRepoPath, BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("onlyDockstoreYmlAuthors")).findFirst().get();
        assertEquals(2, version.getAuthors().size());
        assertEquals(2, version.getOrcidAuthors().size());

        // WDL workflow containing only a descriptor author
        client.handleGitHubRelease(authorsRepo, BasicIT.USER_2_USERNAME, "refs/heads/onlyDescriptorAuthor", installationId);
        workflow = client.getWorkflowByPath(wdlWorkflowRepoPath, BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("onlyDescriptorAuthor")).findFirst().get();
        assertEquals(1, version.getAuthors().size());
        assertEquals(wdlDescriptorAuthorName, version.getAuthor());
        assertEquals(0, version.getOrcidAuthors().size());

        // Release WDL workflow containing only a descriptor author again and test that it doesn't create a duplicate author
        client.handleGitHubRelease(authorsRepo, BasicIT.USER_2_USERNAME, "refs/heads/onlyDescriptorAuthor", installationId);
        workflow = client.getWorkflowByPath(wdlWorkflowRepoPath, BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("onlyDescriptorAuthor")).findFirst().get();
        assertEquals(1, version.getAuthors().size());
        assertEquals(wdlDescriptorAuthorName, version.getAuthor());
        assertEquals(0, version.getOrcidAuthors().size());

        // WDL workflow containing multiple descriptor authors separated by a comma ("Author 1, Author 2") and no .dockstore.yml authors
        client.handleGitHubRelease(authorsRepo, BasicIT.USER_2_USERNAME, "refs/heads/multipleDescriptorAuthors", installationId);
        workflow = client.getWorkflowByPath(wdlWorkflowRepoPath, BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("multipleDescriptorAuthors")).findFirst().get();
        assertEquals(2, version.getAuthors().size());
        version.getAuthors().forEach(author -> assertNotNull(author.getEmail()));
        assertEquals(0, version.getOrcidAuthors().size());
    }

    /**
     * This test relies on Hoverfly to simulate responses from the ORCID API.
     * In the simulation, the responses are crafted for an ORCID author with ID 0000-0002-6130-1021.
     * ORCID authors with other IDs are considered "not found" by the simulation.
     */
    @Test
    void testGetWorkflowVersionOrcidAuthors() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        io.dockstore.openapi.client.ApiClient anonymousWebClient = getAnonymousOpenAPIWebClient();
        io.dockstore.openapi.client.api.WorkflowsApi anonymousWorkflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(anonymousWebClient);
        String wdlWorkflowRepoPath = String.format("github.com/%s/%s", authorsRepo, "foobar");

        // Workflows containing 1 descriptor author and multiple .dockstore.yml authors.
        // If the .dockstore.yml specifies an author, then only the .dockstore.yml's authors should be saved
        workflowsApi.handleGitHubRelease("refs/heads/main", installationId, authorsRepo, BasicIT.USER_2_USERNAME);
        // WDL workflow
        io.dockstore.openapi.client.model.Workflow workflow = workflowsApi.getWorkflowByPath(wdlWorkflowRepoPath, WorkflowSubClass.BIOWORKFLOW, "versions,authors");
        io.dockstore.openapi.client.model.WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(2, version.getAuthors().size());
        assertEquals(2, version.getOrcidAuthors().size());

        // Hoverfly is not used as a class rule here because for some reason it's trying to intercept GitHub in both spy and simulation mode
        try (Hoverfly hoverfly = new Hoverfly(HoverflyMode.SIMULATE)) {
            hoverfly.start();
            hoverfly.simulate(ORCID_SIMULATION_SOURCE);
            List<OrcidAuthorInformation> orcidAuthorInfo = workflowsApi.getWorkflowVersionOrcidAuthors(workflow.getId(), version.getId());
            assertEquals(1, orcidAuthorInfo.size()); // There's 1 OrcidAuthorInfo instead of 2 because only 1 ORCID ID from the version exists on ORCID

            // Publish workflow
            io.dockstore.openapi.client.model.PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);
            workflowsApi.publish1(workflow.getId(), publishRequest);

            // Check that an unauthenticated user can get the workflow version ORCID authors of a published workflow
            anonymousWorkflowsApi.getWorkflowVersionOrcidAuthors(workflow.getId(), version.getId());
            assertEquals(1, orcidAuthorInfo.size());
        }
    }

    /**
     * Tests that the GitHub release process doesn't work for workflows with invalid names
     */
    @Test
    void testDockstoreYmlInvalidWorkflowName() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);

        try {
            workflowsApi.handleGitHubRelease("refs/heads/invalidWorkflowName", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(LAMBDA_ERROR, ex.getCode(), "Should not be able to add a workflow with an invalid name");
            List<io.dockstore.openapi.client.model.LambdaEvent> failEvents = usersApi.getUserGitHubEvents("0", 10);
            assertEquals(1, failEvents.stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count(), "There should be 1 unsuccessful event");
            assertTrue(failEvents.get(0).getMessage().contains(ValidationConstants.ENTRY_NAME_REGEX_MESSAGE));
        }
    }

    // .dockstore.yml in test repo needs to change to add a 'name' field to one of them. Should also include another branch that doesn't keep the name field
    @Test
    void testTools() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(openApiClient);
        WorkflowsApi client = new WorkflowsApi(webClient);

        client.handleGitHubRelease(toolAndWorkflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/main", installationId);
        Workflow appTool = client.getWorkflowByPath("github.com/" + toolAndWorkflowRepoToolPath, APPTOOL, "versions");
        Workflow workflow = client.getWorkflowByPath("github.com/" + toolAndWorkflowRepo, BIOWORKFLOW, "versions");

        assertNotNull(workflow);
        assertNotNull(appTool);

        assertEquals(1, appTool.getWorkflowVersions().size());
        assertEquals(1, workflow.getWorkflowVersions().size());

        Long userId = usersApi.getUser().getId();
        List<io.dockstore.openapi.client.model.Workflow> usersAppTools = usersApi.userAppTools(userId);
        assertEquals(1, usersAppTools.size());

        client.handleGitHubRelease(toolAndWorkflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/invalid-workflow", installationId);
        appTool = client.getWorkflowByPath("github.com/" + toolAndWorkflowRepoToolPath, APPTOOL, "versions,validations");
        workflow = client.getWorkflowByPath("github.com/" + toolAndWorkflowRepo, BIOWORKFLOW, "versions,validations");
        assertEquals(2, appTool.getWorkflowVersions().size());
        assertEquals(2, workflow.getWorkflowVersions().size());

        WorkflowVersion invalidVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.isValid()).findFirst().get();
        assertFalse(invalidVersion.getValidations().isEmpty());
        Validation workflowValidation = invalidVersion.getValidations().stream().filter(validation -> validation.getType().equals(Validation.TypeEnum.DOCKSTORE_CWL)).findFirst().get();
        assertFalse(workflowValidation.isValid());
        assertTrue(workflowValidation.getMessage().contains("Did you mean to register a tool"));
        appTool.getWorkflowVersions().forEach(workflowVersion -> {
            if (!workflowVersion.isValid()) {
                fail("Tool should be valid for both versions");
            }
        });

        client.handleGitHubRelease(toolAndWorkflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/invalidTool", installationId);
        appTool = client.getWorkflowByPath("github.com/" + toolAndWorkflowRepoToolPath, APPTOOL, "versions,validations");
        workflow = client.getWorkflowByPath("github.com/" + toolAndWorkflowRepo, BIOWORKFLOW, "versions,validations");
        assertEquals(3, appTool.getWorkflowVersions().size());
        assertEquals(3, workflow.getWorkflowVersions().size());

        invalidVersion = appTool.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.isValid()).findFirst().get();
        Validation toolValidation = invalidVersion.getValidations().stream().filter(validation -> validation.getType().equals(Validation.TypeEnum.DOCKSTORE_CWL)).findFirst().get();
        assertFalse(toolValidation.isValid());
        assertTrue(toolValidation.getMessage().contains("Did you mean to register a workflow"));

        // publish endpoint updates elasticsearch index
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        client.publish(appTool.getId(), publishRequest);
        client.publish(workflow.getId(), publishRequest);
        assertFalse(systemOut.getText().contains("Could not submit index to elastic search"));

        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openApiClient);
        final List<io.dockstore.openapi.client.model.Tool> tools = ga4Ghv20Api.toolsGet(null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(2, tools.size());

        final io.dockstore.openapi.client.model.Tool tool = ga4Ghv20Api.toolsIdGet("github.com/DockstoreTestUser2/test-workflows-and-tools/md5sum");
        assertNotNull(tool);
        assertEquals("CommandLineTool", tool.getToolclass().getDescription());

        final Tool trsWorkflow = ga4Ghv20Api.toolsIdGet(ToolsImplCommon.WORKFLOW_PREFIX + "/github.com/DockstoreTestUser2/test-workflows-and-tools");
        assertNotNull(trsWorkflow);
        assertEquals("Workflow", trsWorkflow.getToolclass().getDescription());

        publishRequest.setPublish(false);
        client.publish(appTool.getId(), publishRequest);
        client.publish(workflow.getId(), publishRequest);
        assertFalse(systemOut.getText().contains("Could not submit index to elastic search"));
    }

    @Test
    void testSnapshotAppTool() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        client.handleGitHubRelease(taggedToolRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        Workflow appTool = client.getWorkflowByPath("github.com/" + taggedToolRepoPath, APPTOOL, "versions,validations");

        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        client.publish(appTool.getId(), publishRequest);

        // snapshot the version
        validVersion.setFrozen(true);
        client.updateWorkflowVersion(appTool.getId(), Lists.newArrayList(validVersion));

        // check if version is frozen
        appTool = client.getWorkflow(appTool.getId(), null);
        validVersion = appTool.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        assertTrue(validVersion.isFrozen());

        // check if image has been created
        long imageCount = testingPostgres.runSelectStatement("select count(*) from entry_version_image where versionid = " + validVersion.getId(), long.class);
        assertEquals(1, imageCount);
    }

    @Test
    void testChangingAppToolTopics() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        client.handleGitHubRelease(taggedToolRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        Workflow appTool = client.getWorkflowByPath("github.com/" + taggedToolRepoPath, APPTOOL, "versions,validations");

        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        client.publish(appTool.getId(), publishRequest);

        String newTopic = "this is a new topic";
        appTool.setTopicManual(newTopic);
        appTool = client.updateWorkflow(appTool.getId(), appTool);
        assertEquals(newTopic, appTool.getTopicManual());
    }

    @Test
    void testChangingAppToolTopicsOpenapi() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi client = new io.dockstore.openapi.client.api.WorkflowsApi(openApiClient);

        client.handleGitHubRelease("refs/tags/1.0", installationId, taggedToolRepo, BasicIT.USER_2_USERNAME);
        io.dockstore.openapi.client.model.Workflow appTool = client.getWorkflowByPath("github.com/" + taggedToolRepoPath, WorkflowSubClass.APPTOOL, "versions,validations");

        io.dockstore.openapi.client.model.PublishRequest publishRequest = new io.dockstore.openapi.client.model.PublishRequest();
        publishRequest.publish(true);

        client.publish1(appTool.getId(), publishRequest);

        String newTopic = "this is a new topic";
        appTool.setTopicManual(newTopic);
        appTool = client.updateWorkflow(appTool.getId(), appTool);
        assertEquals(newTopic, appTool.getTopicManual());
    }

    @Test
    void testStarAppTool() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(openApiClient);
        WorkflowsApi client = new WorkflowsApi(webClient);

        client.handleGitHubRelease(toolAndWorkflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/main", installationId);
        Workflow appTool = client.getWorkflowByPath("github.com/" + toolAndWorkflowRepoToolPath, APPTOOL, "versions,validations");

        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        client.publish(appTool.getId(), publishRequest);

        List<io.dockstore.openapi.client.model.Entry> pre = usersApi.getStarredTools();
        assertEquals(0, pre.stream().filter(e -> e.getId().equals(appTool.getId())).count());
        assertEquals(0, client.getStarredUsers(appTool.getId()).size());

        client.starEntry(appTool.getId(), new io.swagger.client.model.StarRequest().star(true));

        List<io.dockstore.openapi.client.model.Entry> post = usersApi.getStarredTools();
        assertEquals(1, post.stream().filter(e -> e.getId().equals(appTool.getId())).count());
        assertEquals(pre.size() + 1, post.size());
        assertEquals(1, client.getStarredUsers(appTool.getId()).size());
    }

    @Test
    void testTRSWithAppTools() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(openApiClient);
        WorkflowsApi client = new WorkflowsApi(webClient);

        client.handleGitHubRelease(toolAndWorkflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/main", installationId);
        client.handleGitHubRelease(toolAndWorkflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/invalid-workflow", installationId);
        client.handleGitHubRelease(toolAndWorkflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/invalidTool", installationId);
        Workflow appTool = client.getWorkflowByPath("github.com/" + toolAndWorkflowRepoToolPath, APPTOOL, "versions,validations");
        Workflow workflow = client.getWorkflowByPath("github.com/" + toolAndWorkflowRepo, BIOWORKFLOW, "versions,validations");        // publish endpoint updates elasticsearch index
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        client.publish(appTool.getId(), publishRequest);
        client.publish(workflow.getId(), publishRequest);


        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openApiClient);
        List<io.dockstore.openapi.client.model.Tool> tools = ga4Ghv20Api.toolsGet(null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(2, tools.size());

        // testing filters of various kinds

        tools = ga4Ghv20Api.toolsGet(null, null, null, null, null, null, null, null, null, null, true, null, null);
        // neither the apptool or the regular workflow are checkers
        assertEquals(0, tools.size());
        tools = ga4Ghv20Api.toolsGet(null, null, null, null, null, null, null, null, null, null, false, null, null);
        // neither the apptool or the regular workflow are checkers
        assertEquals(2, tools.size());
        tools = ga4Ghv20Api.toolsGet(null, null, WORKFLOW, null, null, null, null, null, null, null, false, null, null);
        // the apptool is a commandline tool and not a workflow
        assertEquals(1, tools.size());
        tools = ga4Ghv20Api.toolsGet(null, null, COMMAND_LINE_TOOL, null, null, null, null, null, null, null, false, null, null);
        // the apptool is a commandline tool and not a workflow
        assertEquals(1, tools.size());
        tools = ga4Ghv20Api.toolsGet(null, null, SERVICE, null, null, null, null, null, null, null, false, null, null);
        // neither are services
        assertEquals(0, tools.size());
        tools = ga4Ghv20Api.toolsGet(null, null, null, DescriptorLanguage.SERVICE.getShortName(), null, null, null, null, null, null, false, null, null);
        // neither are services this way either
        assertEquals(0, tools.size());

        // testing paging

        tools = ga4Ghv20Api.toolsGet(null, null, null, DescriptorLanguage.CWL.getShortName(), null, null, null, null, null, null, false, String.valueOf(-1), 1);
        // should just go to first page
        assertEquals(1, tools.size());
        assertEquals(WORKFLOW, tools.get(0).getToolclass().getDescription());
        tools = ga4Ghv20Api.toolsGet(null, null, null, DescriptorLanguage.CWL.getShortName(), null, null, null, null, null, null, false, String.valueOf(0), 1);
        // first page
        assertEquals(1, tools.size());
        assertEquals(WORKFLOW, tools.get(0).getToolclass().getDescription());
        tools = ga4Ghv20Api.toolsGet(null, null, null, DescriptorLanguage.CWL.getShortName(), null, null, null, null, null, null, false, String.valueOf(1), 1);
        // second page
        assertEquals(1, tools.size());
        assertEquals(COMMAND_LINE_TOOL, tools.get(0).getToolclass().getDescription());
        tools = ga4Ghv20Api.toolsGet(null, null, null, DescriptorLanguage.CWL.getShortName(), null, null, null, null, null, null, false, String.valueOf(1000), 1);
        //TODO should just go to second page, but for now I guess you just scroll off into nothingness
        assertEquals(0, tools.size());

    }


    @Test
    void testDuplicatePathsAcrossTables() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        try {
            client.handleGitHubRelease(toolAndWorkflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/duplicate-paths", installationId);
            fail("Should not be able to create a workflow and apptool with the same path.");
        } catch (ApiException ex) {
            assertTrue(ex.getMessage().contains("have no name"));
        }

        // Check that the database trigger created an entry in fullworkflowpath table
        long pathCount = testingPostgres.runSelectStatement("select count(*) from fullworkflowpath", long.class);
        assertEquals(0, pathCount);
        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        pathCount = testingPostgres.runSelectStatement("select count(*) from fullworkflowpath", long.class);
        assertTrue(pathCount >= 3);

        try {
            testingPostgres.runUpdateStatement("INSERT INTO fullworkflowpath(id, organization, repository, sourcecontrol, workflowname) VALUES (1010, 'DockstoreTestUser2', 'dockstoreyml-github-filters-test', 'github.com', 'filternone')");
            fail("Database should prevent duplicate paths between tables");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("duplicate key value violates"));
        }
    }

    @Test
    void testAppToolCollections() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        client.handleGitHubRelease(taggedToolRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        Workflow appTool = client.getWorkflowByPath("github.com/" + taggedToolRepoPath, APPTOOL, "versions,validations");

        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        client.publish(appTool.getId(), publishRequest);

        // Setup admin. admin: true, curator: false
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);
        // Create the organization
        Organization registeredOrganization = OrganizationIT.createOrg(organizationsApiAdmin);
        // Admin approve it
        organizationsApiAdmin.approveOrganization(registeredOrganization.getId());
        // Create a collection
        Collection stubCollection = OrganizationIT.stubCollectionObject();
        stubCollection.setName("hcacollection");

        // Attach collection
        final Collection createdCollection = organizationsApiAdmin.createCollection(registeredOrganization.getId(), stubCollection);
        // Add tool to collection
        organizationsApiAdmin.addEntryToCollection(registeredOrganization.getId(), createdCollection.getId(), appTool.getId(), null);
        Collection collection = organizationsApiAdmin.getCollectionById(registeredOrganization.getId(), createdCollection.getId());
        assertTrue((collection.getEntries().stream().anyMatch(entry -> Objects.equals(entry.getId(), appTool.getId()))));
    }
    
    private long countTools() {
        return countTableRows("apptool");
    }

    private long countWorkflows() {
        return countTableRows("workflow");
    }

    private long countNotebooks() {
        return countTableRows("notebook");
    }

    private long countServices() {
        return countTableRows("service");
    }

    private long countTableRows(String tableName) {
        return testingPostgres.runSelectStatement("select count(*) from " + tableName, long.class);
    }

    private ApiException shouldThrowLambdaError(Runnable runnable) {
        try {
            runnable.run();
            fail("should have thrown");
            return null;
        } catch (ApiException ex) {
            assertEquals(LAMBDA_ERROR, ex.getCode());
            return ex;
        }
    }

    // the "multi-entry" repo has four .dockstore.yml entries
    @Test
    void testMultiEntryAllGood() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        client.handleGitHubRelease(multiEntryRepo, BasicIT.USER_2_USERNAME, "refs/heads/master", installationId);
        assertEquals(2, countWorkflows());
        assertEquals(2, countTools());
    }

    @Test
    void testMultiEntryOneBroken() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // test one broken tool
        shouldThrowLambdaError(() -> client.handleGitHubRelease(multiEntryRepo, BasicIT.USER_2_USERNAME, "refs/heads/broken-tool", installationId));
        assertEquals(2, countWorkflows());
        assertEquals(1, countTools());

        // test one broken workflow
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        shouldThrowLambdaError(() -> client.handleGitHubRelease(multiEntryRepo, BasicIT.USER_2_USERNAME, "refs/heads/broken-workflow", installationId));
        assertEquals(1, countWorkflows());
        assertEquals(2, countTools());
    }

    private void confirmNoEntries() {
        assertEquals(0, countWorkflows());
        assertEquals(0, countTools());
        assertEquals(0, countNotebooks());
        assertEquals(0, countServices());
    }

    @Test
    void testMultiEntrySameName() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // test tool-tool name collision
        shouldThrowLambdaError(() -> client.handleGitHubRelease(multiEntryRepo, BasicIT.USER_2_USERNAME, "refs/heads/same-name-tool-tool", installationId));
        confirmNoEntries();

        // test workflow-workflow name collision
        shouldThrowLambdaError(() -> client.handleGitHubRelease(multiEntryRepo, BasicIT.USER_2_USERNAME, "refs/heads/same-name-workflow-workflow", installationId));
        confirmNoEntries();

        // test tool-workflow name collision
        shouldThrowLambdaError(() -> client.handleGitHubRelease(multiEntryRepo, BasicIT.USER_2_USERNAME, "refs/heads/same-name-tool-workflow", installationId));
        confirmNoEntries();

        // test no names
        shouldThrowLambdaError(() -> client.handleGitHubRelease(multiEntryRepo, BasicIT.USER_2_USERNAME, "refs/heads/no-names", installationId));
        confirmNoEntries();

        // test service and unnamed workflows
        shouldThrowLambdaError(() -> client.handleGitHubRelease(multiEntryRepo, BasicIT.USER_2_USERNAME, "refs/heads/service-and-unnamed-workflow", installationId));
        confirmNoEntries();

        // test same name notebook-workflow collision
        shouldThrowLambdaError(() -> client.handleGitHubRelease(multiEntryRepo, BasicIT.USER_2_USERNAME, "refs/heads/same-name-notebook-workflow", installationId));
        confirmNoEntries();

        // test no name notebook-workflow collision
        shouldThrowLambdaError(() -> client.handleGitHubRelease(multiEntryRepo, BasicIT.USER_2_USERNAME, "refs/heads/no-name-notebook-workflow", installationId));
        confirmNoEntries();
    }

    /**
     * Test that the push will fail if the .dockstore.yml contains a
     * relative primary descriptor path, and the primary descriptor
     * contains a relative secondary descriptor path.
     */
    @Test
    void testMultiEntryRelativePrimaryDescriptorPath() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(webClient);
        WorkflowsApi client = new WorkflowsApi(webClient);

        ApiException ex = shouldThrowLambdaError(() -> client.handleGitHubRelease(multiEntryRepo, BasicIT.USER_2_USERNAME, "refs/heads/relative-primary-descriptor-path", installationId));
        assertTrue(ex.getMessage().toLowerCase().contains("could not be processed"));
        assertEquals(0, countWorkflows());
        assertEquals(2, countTools());
        LambdaEvent lambdaEvent = getLatestLambdaEvent("0", usersApi);
        assertFalse(lambdaEvent.isSuccess(), "The event should be unsuccessful");
        assertTrue(lambdaEvent.getMessage().toLowerCase().contains("absolute"), "Should contain the word 'absolute'");
    }

    /**
     * Tests that the GitHub release syncs a workflow's metadata with the default version's metadata.
     * Tests two scenarios:
     * <li>The default version for a workflow is set using the latestTagAsDefault property from the dockstore.yml</li>
     * <li>The default version for a workflow is set manually using the API</li>
     */
    @Test
    void testSyncWorkflowMetadataWithDefaultVersion() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);

        workflowsApi.handleGitHubRelease("refs/tags/0.4", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        io.dockstore.openapi.client.model.Workflow workflow = getFoobar1Workflow(workflowsApi); // dockstore.yml for foobar doesn't have latestTagAsDefault set
        io.dockstore.openapi.client.model.Workflow workflow2 = getFoobar2Workflow(workflowsApi); // dockstore.yml for foobar2 has latestTagAsDefault set
        assertNull(workflow.getDefaultVersion());
        assertEquals("0.4", workflow2.getDefaultVersion(), "Should have latest tag set as default version");

        workflowsApi.updateDefaultVersion1(workflow.getId(), "0.4"); // Set default version for workflow that doesn't have one
        workflow = getFoobar1Workflow(workflowsApi);
        assertEquals("0.4", workflow.getDefaultVersion(), "Should have default version set");

        // Find WorkflowVersion for default version and make sure it has metadata set
        Optional<io.dockstore.openapi.client.model.WorkflowVersion> defaultVersion = workflow.getWorkflowVersions().stream()
                .filter((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.4"))
                .findFirst();
        assertTrue(defaultVersion.isPresent());
        assertEquals("Test User", defaultVersion.get().getAuthor(), "Version should have author set");
        assertEquals("test@dockstore.org", defaultVersion.get().getEmail(), "Version should have email set");
        assertEquals("This is a description", defaultVersion.get().getDescription(), "Version should have email set");

        // Check that the workflow metadata is the same as the default version's metadata
        checkWorkflowMetadataWithDefaultVersionMetadata(workflow, defaultVersion.get());
        checkWorkflowMetadataWithDefaultVersionMetadata(workflow2, defaultVersion.get());

        // Clear workflow metadata to test the scenario where the default version metadata was updated and is now out of sync with the workflow's metadata
        testingPostgres.runUpdateStatement(String.format("UPDATE workflow SET author = NULL where id = '%s'", workflow.getId()));
        testingPostgres.runUpdateStatement(String.format("UPDATE workflow SET email = NULL where id = '%s'", workflow.getId()));
        testingPostgres.runUpdateStatement(String.format("UPDATE workflow SET description = NULL where id = '%s'", workflow.getId()));
        // GitHub release should sync metadata with default version
        workflowsApi.handleGitHubRelease("refs/tags/0.4", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        workflow = getFoobar1Workflow(workflowsApi);
        workflow2 = getFoobar2Workflow(workflowsApi);
        checkWorkflowMetadataWithDefaultVersionMetadata(workflow, defaultVersion.get());
        checkWorkflowMetadataWithDefaultVersionMetadata(workflow2, defaultVersion.get());
    }

    /**
     * Tests that the language version in WDL descriptor files is correct during a GitHub release
     */
    @Test
    void testDockstoreYmlWorkflowLanguageVersions() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);
        String wdlWorkflowRepo = "dockstore-testing/dockstore-whalesay2";

        workflowsApi.handleGitHubRelease("refs/heads/master", installationId, wdlWorkflowRepo, BasicIT.USER_2_USERNAME);
        io.dockstore.openapi.client.model.Workflow workflow = workflowsApi.getWorkflowByPath("github.com/" + wdlWorkflowRepo, WorkflowSubClass.BIOWORKFLOW, "versions");
        io.dockstore.openapi.client.model.WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst().get();
        List<io.dockstore.openapi.client.model.SourceFile> sourceFiles = workflowsApi.getWorkflowVersionsSourcefiles(workflow.getId(), version.getId(), null);
        assertNotNull(sourceFiles);
        assertEquals(2, sourceFiles.size());
        sourceFiles.forEach(sourceFile -> {
            if ("/Dockstore.wdl".equals(sourceFile.getAbsolutePath())) {
                assertEquals(FileType.DOCKSTORE_WDL.name(), sourceFile.getType().getValue());
                assertEquals("1.0", sourceFile.getMetadata().getTypeVersion(), "Language version of WDL descriptor with 'version 1.0' should be 1.0");
            } else {
                assertEquals(FileType.DOCKSTORE_YML.name(), sourceFile.getType().getValue());
                assertNull(sourceFile.getMetadata().getTypeVersion(), ".dockstore.yml should not have a version");
            }
        });
        assertEquals(1, version.getVersionMetadata().getDescriptorTypeVersions().size(), "Should only have one language version");
        assertTrue(version.getVersionMetadata().getDescriptorTypeVersions().contains("1.0"));
    }

    // Asserts that the workflow metadata is the same as the default version metadata
    private void checkWorkflowMetadataWithDefaultVersionMetadata(io.dockstore.openapi.client.model.Workflow workflow, io.dockstore.openapi.client.model.WorkflowVersion defaultVersion) {
        assertEquals(defaultVersion.getAuthor(), workflow.getAuthor(), "Workflow author should equal default version author");
        assertEquals(defaultVersion.getEmail(), workflow.getEmail(), "Workflow email should equal default version email");
        assertEquals(defaultVersion.getDescription(), workflow.getDescription(), "Workflow description should equal default version description");
    }

    /**
     * Tests that an attempt to register a WDL that contains recursive
     * remote references will result in failure.
     * <a href="https://ucsc-cgl.atlassian.net/browse/DOCK-2299">...</a>
     */
    @Test
    void testRegistrationOfRecursiveWDL() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi client = new io.dockstore.openapi.client.api.WorkflowsApi(openApiClient);
        io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(openApiClient);

        // Attempt to process a repo containing a recursive WDL.  Internally, we use the same libraries that Cromwell does to process WDLs.
        // The WDL processing code should throw a StackOverflowError, which is remapped to a more explanatory CustomWebApplicationException, which will trigger a typical registration failure.
        try {
            client.handleGitHubRelease("refs/heads/main", installationId, "dockstore-testing/recursive-wdl", BasicIT.USER_2_USERNAME);
            fail("should have thrown");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            // Confirm that the release failed and was logged correctly.
            List<io.dockstore.openapi.client.model.LambdaEvent> events = usersApi.getUserGitHubEvents("0", 10);
            assertEquals(1, events.size(), "There should be one event");
            assertEquals(0, events.stream().filter(io.dockstore.openapi.client.model.LambdaEvent::isSuccess).count(), "There should be no successful events");
            assertTrue(events.get(0).getMessage().contains(WDLHandler.ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT), "Event message should indicate the problem");
        }
    }
}
