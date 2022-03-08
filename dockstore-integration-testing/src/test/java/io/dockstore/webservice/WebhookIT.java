/*
 *
 *    Copyright 2020 OICR
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
import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATH;
import static io.openapi.api.impl.ToolClassesApiServiceImpl.COMMAND_LINE_TOOL;
import static io.openapi.api.impl.ToolClassesApiServiceImpl.WORKFLOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BasicIT;
import io.dockstore.client.cli.OrganizationIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.api.LambdaEventsApi;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.jdbi.FileDAO;
import io.swagger.api.impl.ToolsImplCommon;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.OrganizationsApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Collection;
import io.swagger.client.model.LambdaEvent;
import io.swagger.client.model.Organization;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Validation;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

/**
 * @author agduncan
 */
@Category(ConfidentialTest.class)
public class WebhookIT extends BaseIT {
    private static final int LAMBDA_ERROR = 418;

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private final String workflowRepo = "DockstoreTestUser2/workflow-dockstore-yml";
    private final String githubFiltersRepo = "DockstoreTestUser2/dockstoreyml-github-filters-test";
    private final String installationId = "1179416";
    private final String toolAndWorkflowRepo = "DockstoreTestUser2/test-workflows-and-tools";
    private final String toolAndWorkflowRepoToolPath = "DockstoreTestUser2/test-workflows-and-tools/md5sum";
    private final String taggedToolRepo = "dockstore-testing/tagged-apptool";
    private final String taggedToolRepoPath = "dockstore-testing/tagged-apptool/md5sum";
    private final String authorsRepo = "DockstoreTestUser2/test-authors";
    private FileDAO fileDAO;

    @Before
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        this.fileDAO = new FileDAO(sessionFactory);

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use tokenDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Test
    public void testWorkflowMigration() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
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
        assertEquals("Workflow should be FULL mode", Workflow.ModeEnum.FULL, workflow.getMode());
        assertTrue("All versions should be legacy", workflow.getWorkflowVersions().stream().allMatch(WorkflowVersion::isLegacyVersion));

        // Webhook call should convert workflow to DOCKSTORE_YML
        workflowApi.handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/tags/0.1", installationId);
        workflow = workflowApi.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", BIOWORKFLOW, "versions");
        assertEquals("Workflow should be DOCKSTORE_YML mode", Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertTrue("One version should be not legacy", workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> !workflowVersion.isLegacyVersion()));

        // Refresh should now no longer work
        try {
            workflowApi.refresh(workflow.getId(), false);
            fail("Should fail on refresh and not reach this point");
        } catch (ApiException ex) {
            assertEquals("Should not be able to refresh a dockstore.yml workflow.", HttpStatus.SC_BAD_REQUEST, ex.getCode());
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
        assertTrue("There should still be a dockstore.yml workflow", workflows.stream().anyMatch(wf -> Objects.equals(wf.getMode(), Workflow.ModeEnum.DOCKSTORE_YML)));
        assertTrue("There should be at least one stub workflow", workflows.stream().anyMatch(wf -> Objects.equals(wf.getMode(), Workflow.ModeEnum.STUB)));

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
     * This tests the GitHub release process
     */
    @Test
    public void testGitHubReleaseNoWorkflowOnDockstore() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi client = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);
        LambdaEventsApi lambdaEventsApi = new LambdaEventsApi(webClient);

        // Track install event
        client.handleGitHubInstallation(installationId, workflowRepo, BasicIT.USER_2_USERNAME);

        // Release 0.1 on GitHub - one new wdl workflow
        client.handleGitHubRelease("refs/tags/0.1", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new workflow is created and is what is expected
        io.dockstore.openapi.client.model.Workflow workflow = getFoobar1Workflow(client);
        assertEquals("Should be a WDL workflow", io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", io.dockstore.openapi.client.model.Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertEquals("Should have one version 0.1", 1, workflow.getWorkflowVersions().size());
        assertEquals("A repo that includes .dockstore.yml", workflow.getTopicAutomatic());

        // Release 0.2 on GitHub - one existing wdl workflow, one new cwl workflow
        client.handleGitHubRelease("refs/tags/0.2", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(2, workflowCount);

        // Ensure that existing workflow is updated
        workflow = getFoobar1Workflow(client);

        // Ensure that new workflow is created and is what is expected
        io.dockstore.openapi.client.model.Workflow workflow2 = getFoobar2Workflow(client);
        assertEquals("Should be a CWL workflow", io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum.CWL, workflow2.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", io.dockstore.openapi.client.model.Workflow.ModeEnum.DOCKSTORE_YML, workflow2.getMode());
        assertEquals("Should have one version 0.2", 1, workflow2.getWorkflowVersions().size());


        // Unset the license information to simulate license change
        testingPostgres.runUpdateStatement("update workflow set licensename=null");
        // Unset topicAutomatic to simulate a topicAutomatic change
        testingPostgres.runUpdateStatement("update workflow set topicAutomatic=null");
        // Branch master on GitHub - updates two existing workflows
        client.handleGitHubRelease("refs/heads/master", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        List<io.dockstore.openapi.client.model.Workflow> workflows = new ArrayList<>();
        workflows.add(workflow);
        workflows.add(workflow2);
        assertEquals("Should only have two workflows", 2, workflows.size());
        workflows.forEach(workflowIndividual -> {
            assertEquals("Should be able to get license after manual GitHub App version update", "Apache License 2.0", workflowIndividual.getLicenseInformation().getLicenseName());
            assertEquals("Should be able to get topic from GitHub after GitHub App version update", "A repo that includes .dockstore.yml", workflowIndividual.getTopicAutomatic());
        });

        workflow = getFoobar1Workflow(client);
        assertTrue("Should have a master version.", workflow.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")));
        assertTrue("Should have a 0.1 version.", workflow.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.1")));
        assertTrue("Should have a 0.2 version.", workflow.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")));

        workflow2 = getFoobar2Workflow(client);
        assertTrue("Should have a master version.", workflow2.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")));
        assertTrue("Should have a 0.2 version.", workflow2.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")));



        // Master version should have metadata set
        Optional<io.dockstore.openapi.client.model.WorkflowVersion> masterVersion = workflow.getWorkflowVersions().stream().filter((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertEquals("Should have author set", "Test User", masterVersion.get().getAuthor());
        assertEquals("Should have email set", "test@dockstore.org", masterVersion.get().getEmail());
        assertEquals("Should have email set", "This is a description", masterVersion.get().getDescription());

        masterVersion = workflow2.getWorkflowVersions().stream().filter((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertEquals("Should have author set", "Test User", masterVersion.get().getAuthor());
        assertTrue("Should be valid", masterVersion.get().isValid());
        assertEquals("Should have email set", "test@dockstore.org", masterVersion.get().getEmail());
        assertEquals("Should have email set", "This is a description", masterVersion.get().getDescription());

        boolean hasLegacyVersion = workflow.getWorkflowVersions().stream().anyMatch(
                io.dockstore.openapi.client.model.WorkflowVersion::isLegacyVersion);
        assertFalse("Workflow should not have any legacy refresh versions.", hasLegacyVersion);

        // Delete tag 0.2
        client.handleGitHubBranchDeletion(workflowRepo, BasicIT.USER_2_USERNAME, "refs/tags/0.2", installationId);
        workflow = getFoobar1Workflow(client);
        assertTrue("Should not have a 0.2 version.", workflow.getWorkflowVersions().stream().noneMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")));
        workflow2 = getFoobar2Workflow(client);
        assertTrue("Should not have a 0.2 version.", workflow2.getWorkflowVersions().stream().noneMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")));

        // Add version that doesn't exist
        try {
            client.handleGitHubRelease("refs/heads/idonotexist", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
            fail("Should fail and not reach this point");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            List<io.dockstore.openapi.client.model.LambdaEvent> failureEvents = usersApi.getUserGitHubEvents("0", 10);
            assertEquals("There should be 1 unsuccessful event", 1,
                    failureEvents.stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count());
        }

        // There should be 5 successful lambda events
        List<io.dockstore.openapi.client.model.LambdaEvent> events = usersApi.getUserGitHubEvents("0", 10);
        assertEquals("There should be 5 successful events", 5,
                events.stream().filter(io.dockstore.openapi.client.model.LambdaEvent::isSuccess).count());

        // Test pagination for user github events
        events = usersApi.getUserGitHubEvents("2", 2);
        assertEquals("There should be 2 events (id 3 and 4)", 2, events.size());
        assertTrue("Should have event with ID 3", events.stream().anyMatch(lambdaEvent -> Objects.equals(3L, lambdaEvent.getId())));
        assertTrue("Should have event with ID 4", events.stream().anyMatch(lambdaEvent -> Objects.equals(4L, lambdaEvent.getId())));

        // Test the organization events endpoint
        List<io.dockstore.openapi.client.model.LambdaEvent> orgEvents = lambdaEventsApi.getLambdaEventsByOrganization("DockstoreTestUser2", "0", 10);
        assertEquals("There should be 6 events", 6, orgEvents.size());

        // Test pagination
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization("DockstoreTestUser2", "2", 2);
        assertEquals("There should be 2 events (id 3 and 4)", 2, orgEvents.size());
        assertTrue("Should have event with ID 3", orgEvents.stream().anyMatch(lambdaEvent -> Objects.equals(3L, lambdaEvent.getId())));
        assertTrue("Should have event with ID 4", orgEvents.stream().anyMatch(lambdaEvent -> Objects.equals(4L, lambdaEvent.getId())));

        // Change organization to test filter
        testingPostgres.runUpdateStatement("UPDATE lambdaevent SET repository = 'workflow-dockstore-yml', organization = 'DockstoreTestUser3' WHERE id = '1'");

        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization("DockstoreTestUser2", "0", 10);
        assertEquals("There should now be 5 events", 5, orgEvents.size());

        try {
            lambdaEventsApi.getLambdaEventsByOrganization("IAmMadeUp", "0", 10);
            fail("Should not reach this statement");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals("Should fail because user cannot access org.", HttpStatus.SC_UNAUTHORIZED, ex.getCode());
        }

        // Try adding version with empty test parameter file (should work)
        client.handleGitHubRelease("refs/heads/emptytestparameter", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        workflow2 = getFoobar2Workflow(client);
        assertTrue("Should have emptytestparameter version that is valid", workflow2.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "emptytestparameter")).findFirst().get().isValid());
        testValidationUpdate(client);
        testDefaultVersion(client);
    }

    private void testDefaultVersion(io.dockstore.openapi.client.api.WorkflowsApi client) {
        io.dockstore.openapi.client.model.Workflow workflow2 = getFoobar2Workflow(client);
        assertNull(workflow2.getDefaultVersion());
        io.dockstore.openapi.client.model.Workflow workflow = getFoobar1Workflow(client);
        assertEquals(null, workflow.getDefaultVersion());
        client.handleGitHubRelease("refs/tags/0.4", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        workflow2 = getFoobar2Workflow(client);
        assertEquals("The new tag says the latest tag should be the default version", "0.4", workflow2.getDefaultVersion());
        workflow = getFoobar1Workflow(client);
        assertEquals(null, workflow.getDefaultVersion());

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
        assertFalse("Master version should be invalid because it was manually changed", masterVersion.get().isValid());

        client.handleGitHubRelease("refs/heads/master", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        workflow2 = getFoobar2Workflow(client);
        masterVersion = workflow2.getWorkflowVersions().stream().filter((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertTrue("Master version should be valid after GitHub App triggered again", masterVersion.get().isValid());
    }

    /**
     * This tests deleting a GitHub App workflow's default version
     */
    @Test
    public void testDeleteDefaultWorkflowVersion() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Add 1.0 tag and set as default version
        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        Workflow workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals("should have 1 version", 1, workflow.getWorkflowVersions().size());
        assertNull("should have no default version until set", workflow.getDefaultVersion());
        workflow = client.updateWorkflowDefaultVersion(workflow.getId(), workflow.getWorkflowVersions().get(0).getName());
        assertNotNull("should have a default version after setting", workflow.getDefaultVersion());

        // Add 2.0 tag
        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/2.0", installationId);
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals("should have 2 versions", 2, workflow.getWorkflowVersions().size());

        // Delete 1.0 tag, should reassign 2.0 as the default version
        client.handleGitHubBranchDeletion(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals("should have 1 version after deletion", 1, workflow.getWorkflowVersions().size());
        assertNotNull("should have reassigned the default version during deletion", workflow.getDefaultVersion());

        // Delete 2.0 tag, unset default version
        client.handleGitHubBranchDeletion(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/2.0", installationId);
        workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "versions");
        assertEquals("should have 0 versions after deletion", 0, workflow.getWorkflowVersions().size());
        assertNull("should have no default version after final version is deleted", workflow.getDefaultVersion());
    }

    /**
     * This tests calling refresh on a workflow with a Dockstore.yml
     */
    @Test
    public void testManualRefreshWorkflowWithGitHubApp() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Release 0.1 on GitHub - one new wdl workflow
        client.handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/tags/0.1", installationId);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        Workflow workflow = getFoobar1Workflow(client);
        assertEquals("Should be able to get license after GitHub App register", "Apache License 2.0", workflow.getLicenseInformation().getLicenseName());

        // Ensure that new workflow is created and is what is expected

        assertEquals("Should be a WDL workflow", Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertTrue("Should have a 0.1 version.", workflow.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.1")));
        boolean hasLegacyVersion = workflow.getWorkflowVersions().stream().anyMatch(WorkflowVersion::isLegacyVersion);
        assertFalse("Workflow should not have any legacy refresh versions.", hasLegacyVersion);

        // Refresh
        try {
            client.refresh(workflow.getId(), false);
            fail("Should fail on refresh and not reach this point");
        } catch (ApiException ex) {
            assertEquals("Should not be able to refresh a dockstore.yml workflow.", HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }
    }

    /**
     * This tests the GitHub release process does not work for users that do not exist on Dockstore
     */
    @Test
    public void testGitHubReleaseNoWorkflowOnDockstoreNoUser() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        try {
            client.handleGitHubRelease(workflowRepo, "thisisafakeuser", "refs/tags/0.1", installationId);
            Assert.fail("Should not reach this statement.");
        } catch (ApiException ex) {
            assertEquals("Should not be able to add a workflow when user does not exist on Dockstore.", LAMBDA_ERROR, ex.getCode());
        }
    }

    /**
     * Tests:
     * An unpublished workflow with invalid versions can have its descriptor type changed
     * The workflow can then have new valid versions registered
     * The valid workflow cannot have its descriptor type changed anymore (because it's valid)
     * The published workflow cannot have its descriptor type changed anymore (because it's published)
     * @throws Exception    DB problem
     */
    @Test
    public void testDescriptorChange() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.ApiClient openAPIWebClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(openAPIWebClient);
        WorkflowsApi client = new WorkflowsApi(webClient);
        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/missingPrimaryDescriptor", installationId);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);
        // Ensure that new workflow is created and is what is expected
        Workflow workflow = getFoobar1Workflow(client);
        assertEquals("Should be a WDL workflow", Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertEquals("Should have one version", 1, workflow.getWorkflowVersions().size());
        assertFalse("Should be invalid (wrong language, bad version)", workflow.getWorkflowVersions().get(0).isValid());

        workflowsApi.updateDescriptorType(workflow.getId(), DescriptorLanguage.CWL.toString());
        io.dockstore.openapi.client.model.Workflow updatedWorkflowAfterModifyingDescriptorType = workflowsApi.getWorkflow(workflow.getId(), "");
        assertEquals("The descriptor language should have been changed", io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum.CWL, updatedWorkflowAfterModifyingDescriptorType.getDescriptorType());
        assertEquals("The old versions should have been removed", 0, updatedWorkflowAfterModifyingDescriptorType.getWorkflowVersions().size());

        workflowsApi.updateDescriptorType(workflow.getId(), DescriptorLanguage.WDL.toString());
        updatedWorkflowAfterModifyingDescriptorType = workflowsApi.getWorkflow(workflow.getId(), "versions");
        assertEquals("The descriptor language should have been changed", io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum.WDL, updatedWorkflowAfterModifyingDescriptorType.getDescriptorType());
        assertEquals("The old versions should have been removed", 0, updatedWorkflowAfterModifyingDescriptorType.getWorkflowVersions().size());

        // Release 0.1 on GitHub - one new wdl workflow
        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/tags/0.1", installationId);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new workflow is created and is what is expected
        workflow = getFoobar1Workflow(client);
        assertEquals("Should be a WDL workflow", Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertEquals("Should have one version 0.1", 1, workflow.getWorkflowVersions().size());
        assertTrue("Should be valid", workflow.getWorkflowVersions().get(0).isValid());
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
    public void testInvalidDockstoreYmlFiles() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);

        // Release 0.1 on GitHub - one new wdl workflow
        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/tags/0.1", installationId);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new workflow is created and is what is expected
        Workflow workflow = getFoobar1Workflow(client);
        assertEquals("Should be a WDL workflow", Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertEquals("Should have one version 0.1", 1, workflow.getWorkflowVersions().size());
        assertTrue("Should be valid", workflow.getWorkflowVersions().get(0).isValid());

        // Push missingPrimaryDescriptor on GitHub - one existing wdl workflow, missing primary descriptor
        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/missingPrimaryDescriptor", installationId);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new version is in the correct state (invalid)
        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", BIOWORKFLOW, "validations");
        assertNotNull(workflow);
        assertEquals("Should have two versions", 2, workflow.getWorkflowVersions().size());

        WorkflowVersion missingPrimaryDescriptorVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "missingPrimaryDescriptor")).findFirst().get();
        assertFalse("Version should be invalid", missingPrimaryDescriptorVersion.isValid());

        // Check existence of files and validations
        List<SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(missingPrimaryDescriptorVersion.getId());
        assertTrue("Should have .dockstore.yml file", sourceFiles.stream().anyMatch(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), DOCKSTORE_YML_PATH)));
        assertTrue("Should not have doesnotexist.wdl file", sourceFiles.stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/doesnotexist.wdl")).findFirst().isEmpty());
        assertFalse("Should have invalid .dockstore.yml", missingPrimaryDescriptorVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_YML)).findFirst().get().isValid());
        assertFalse("Should have invalid doesnotexist.wdl", missingPrimaryDescriptorVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_WDL)).findFirst().get().isValid());

        // Push missingTestParameterFile on GitHub - one existing wdl workflow, missing a test parameter file
        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/missingTestParameterFile", installationId);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new version is in the correct state (invalid)
        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", BIOWORKFLOW, "validations");
        assertNotNull(workflow);
        assertEquals("Should have three versions", 3, workflow.getWorkflowVersions().size());

        WorkflowVersion missingTestParameterFileVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "missingTestParameterFile")).findFirst().get();
        assertTrue("Version should be valid (missing test parameter doesn't make the version invalid)", missingTestParameterFileVersion.isValid());

        // Check existence of files and validations
        sourceFiles = fileDAO.findSourceFilesByVersion(missingTestParameterFileVersion.getId());
        assertTrue("Should have .dockstore.yml file", sourceFiles.stream().anyMatch(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), DOCKSTORE_YML_PATH)));
        assertTrue("Should not have /test/doesnotexist.txt file", sourceFiles.stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/test/doesnotexist.txt")).findFirst().isEmpty());
        assertTrue("Should have Dockstore2.wdl file", sourceFiles.stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/Dockstore2.wdl")).findFirst().isPresent());
        assertFalse("Should have invalid .dockstore.yml", missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_YML)).findFirst().get().isValid());
        assertTrue("Should have valid Dockstore2.wdl", missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_WDL)).findFirst().get().isValid());

        // Push unknownProperty on GitHub - one existing wdl workflow, incorrectly spelled testParameterFiles property
        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/unknownProperty", installationId);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new version is in the correct state (invalid)
        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", BIOWORKFLOW, "validations");
        assertNotNull(workflow);
        assertEquals("Should have four versions", 4, workflow.getWorkflowVersions().size());

        WorkflowVersion unknownPropertyVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "unknownProperty")).findFirst().get();
        assertTrue("Version should be valid (unknown property doesn't make the version invalid)", unknownPropertyVersion.isValid());

        // Check existence of files and validations
        sourceFiles = fileDAO.findSourceFilesByVersion(unknownPropertyVersion.getId());
        assertTrue("Should have .dockstore.yml file", sourceFiles.stream().anyMatch(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), DOCKSTORE_YML_PATH)));
        assertTrue("Should not have /dockstore.wdl.json file", sourceFiles.stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/dockstore.wdl.json")).findFirst().isEmpty());
        assertTrue("Should have Dockstore2.wdl file", sourceFiles.stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/Dockstore2.wdl")).findFirst().isPresent());
        assertFalse("Should have invalid .dockstore.yml", missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_YML)).findFirst().get().isValid());
        assertTrue("Should have valid Dockstore2.wdl", missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_WDL)).findFirst().get().isValid());

        // There should be 4 successful lambda events
        List<LambdaEvent> events = usersApi.getUserGitHubEvents("0", 10);
        assertEquals("There should be 4 successful events", 4, events.stream().filter(LambdaEvent::isSuccess).count());

        final int versionCountBeforeInvalidDockstoreYml = getFoobar1Workflow(client).getWorkflowVersions().size();
        // Push branch with invalid dockstore.yml
        try {
            client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/invalidDockstoreYml", installationId);
            fail("Should not reach this statement");
        } catch (ApiException ex) {
            List<LambdaEvent> failEvents = usersApi.getUserGitHubEvents("0", 10);
            assertEquals("There should be 1 unsuccessful event", 1,
                    failEvents.stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count());
            assertEquals("Number of versions should be the same", versionCountBeforeInvalidDockstoreYml, getFoobar1Workflow(client).getWorkflowVersions().size());
        }
    }

    /**
     * Test that a .dockstore.yml workflow has the expected path for its test parameter file.
     */
    @Test
    public void testTestParameterPaths() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/master", installationId);
        Workflow workflow = getFoobar1Workflow(client);
        WorkflowVersion version = workflow.getWorkflowVersions().get(0);
        List<SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(version.getId());
        assertTrue("Test file should have the expected path", sourceFiles.stream().filter(sourceFile -> sourceFile.getPath().equals("/dockstore.wdl.json")).findFirst().isPresent());
    }

    /**
     * This tests the GitHub release with .dockstore.yml located in /.github/.dockstore.yml
     */
    @Test
    public void testGithubDirDockstoreYml() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        client.handleGitHubRelease(githubFiltersRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        Workflow workflow = client.getWorkflowByPath("github.com/" + githubFiltersRepo + "/filternone", BIOWORKFLOW, "");
        assertNotNull(workflow);
    }

    /**
     * This tests filters functionality in .dockstore.yml
     * https://github.com/DockstoreTestUser2/dockstoreyml-github-filters-test
     * Workflow filters are configured as follows:
     * * filterbranch filters for "develop"
     * * filtertag filters for "1.0"
     * * filtermulti filters for "dev*" and "1.*"
     * * filternone has no filters (accepts all tags & branches)
     * * filterregexerror has a filter with an invalid regex string (matches nothing)
     */
    @Test
    public void testDockstoreYmlFilters() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
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
     * @throws Exception
     */
    @Test
    public void testDockstoreYmlPublish() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
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
     * @throws Exception
     */
    @Test
    public void testDockstoreYmlAuthors() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        String wdlWorkflowRepoPath = String.format("github.com/%s/%s", authorsRepo, "foobar");
        String cwlWorkflowRepoPath = String.format("github.com/%s/%s", authorsRepo, "foobar2");
        String nextflowWorkflowRepoPath = String.format("github.com/%s/%s", authorsRepo, "foobar3");
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
        assertTrue("Should not have any author from the descriptor", version.getAuthors().stream().noneMatch(author -> author.getName().equals(wdlDescriptorAuthorName)));
        // CWL workflow
        workflow = client.getWorkflowByPath(cwlWorkflowRepoPath, BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(1, version.getAuthors().size());
        assertEquals(1, version.getOrcidAuthors().size());
        final String cwlDescriptorAuthorName = "Test User";
        assertTrue("Should not have any author from the descriptor", version.getAuthors().stream().noneMatch(author -> author.getName().equals(cwlDescriptorAuthorName)));
        // Nextflow workflow
        workflow = client.getWorkflowByPath(nextflowWorkflowRepoPath, BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(1, version.getAuthors().size());
        assertEquals(1, version.getOrcidAuthors().size());
        final String nextflowDescriptorAuthorName = "Nextflow Test Author";
        assertTrue("Should not have any author from the descriptor", version.getAuthors().stream().noneMatch(author -> author.getName().equals(nextflowDescriptorAuthorName)));

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
        version.getAuthors().stream().forEach(author -> assertNotNull(author.getEmail()));
        assertEquals(0, version.getOrcidAuthors().size());
    }

    /**
     * Tests that the GitHub release process doesn't work for workflows with invalid names
     * @throws Exception
     */
    @Test
    public void testDockstoreYmlInvalidWorkflowName() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);

        try {
            workflowsApi.handleGitHubRelease("refs/heads/invalidWorkflowName", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals("Should not be able to add a workflow with an invalid name", LAMBDA_ERROR, ex.getCode());
            List<io.dockstore.openapi.client.model.LambdaEvent> failEvents = usersApi.getUserGitHubEvents("0", 10);
            assertEquals("There should be 1 unsuccessful event", 1,
                    failEvents.stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count());
            assertTrue(failEvents.get(0).getMessage().contains("Invalid workflow name"));
        }
    }

    // .dockstore.yml in test repo needs to change to add a 'name' field to one of them. Should also include another branch that doesn't keep the name field
    @Test
    public void testTools() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
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
        invalidVersion.getValidations();
        Validation workflowValidation = invalidVersion.getValidations().stream().filter(validation -> validation.getType().equals(Validation.TypeEnum.DOCKSTORE_CWL)).findFirst().get();
        assertFalse(workflowValidation.isValid());
        assertTrue(workflowValidation.getMessage().contains("Did you mean to register a tool"));
        appTool.getWorkflowVersions().stream().forEach(workflowVersion -> {
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
        WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(workflowVersion -> workflowVersion.isValid()).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        client.publish(appTool.getId(), publishRequest);
        client.publish(workflow.getId(), publishRequest);
        Assert.assertFalse(systemOutRule.getLog().contains("Could not submit index to elastic search"));

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
        Assert.assertFalse(systemOutRule.getLog().contains("Could not submit index to elastic search"));
    }

    @Test
    public void testSnapshotAppTool() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(openApiClient);
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
    public void testTRSWithAppTools() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
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
    public void testDuplicatePathsAcrossTables() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        try {
            client.handleGitHubRelease(toolAndWorkflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/duplicate-paths", installationId);
            fail("Should not be able to create a workflow and apptool with the same path.");
        } catch (ApiException ex) {
            assertTrue(ex.getMessage().contains("with the same path already exists."));
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

        // Should be able to have service with duplicate name
        client.handleGitHubRelease(toolAndWorkflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/addService", installationId);
    }

    @Test
    public void testAppToolCollections() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(openApiClient);
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
}
