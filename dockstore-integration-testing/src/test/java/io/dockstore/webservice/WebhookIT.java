
package io.dockstore.webservice;

import static io.dockstore.common.Hoverfly.ORCID_SIMULATION_SOURCE;
import static io.dockstore.webservice.helpers.GitHubAppHelper.INSTALLATION_ID;
import static io.dockstore.webservice.helpers.GitHubAppHelper.LAMBDA_ERROR;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubRelease;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.client.cli.OrganizationIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.common.ValidationConstants;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.LambdaEventsApi;
import io.dockstore.openapi.client.api.OrganizationsApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Collection;
import io.dockstore.openapi.client.model.LambdaEvent;
import io.dockstore.openapi.client.model.OrcidAuthorInformation;
import io.dockstore.openapi.client.model.Organization;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.openapi.client.model.WorkflowVersion.DescriptionSourceEnum;
import io.dockstore.webservice.helpers.GitHubAppHelper.DockstoreTestUser2Repos;
import io.dockstore.webservice.languages.WDLHandler;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.http.HttpStatus;
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
 * Like {@link SwaggerWebhookIT } but with only openapi classes to avoid having to give fully defined classes everywhere
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class WebhookIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private final String dockstoreTesting = "dockstore-testing";
    private final String taggedToolRepo = "dockstore-testing/tagged-apptool";
    private final String taggedToolRepoPath = "dockstore-testing/tagged-apptool/md5sum";
    private final String workflowDockstoreYmlRepo = "dockstore-testing/workflow-dockstore-yml";
    private final String testWorkflowsAndToolsRepo = "dockstore-testing/test-workflows-and-tools";


    @BeforeEach
    public void cleanDB() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }


    @Test
    @Disabled("https://ucsc-cgl.atlassian.net/browse/DOCK-1890")
    void testAppToolCollections() throws Exception {
        final ApiClient openApiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(openApiClient);
        handleGitHubRelease(client, INSTALLATION_ID, taggedToolRepo, "refs/tags/1.0", USER_2_USERNAME);
        Workflow appTool = client.getWorkflowByPath("github.com/" + taggedToolRepoPath, WorkflowSubClass.APPTOOL, "versions,validations");

        WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        final PublishRequest publishRequest = new PublishRequest();
        publishRequest.publish(true);
        client.publish1(appTool.getId(), publishRequest);

        // Setup admin. admin: true, curator: false
        final ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);
        // Create the organization
        Organization registeredOrganization = OrganizationIT.openApiStubOrgObject();

        // Admin approve it
        organizationsApiAdmin.approveOrganization(registeredOrganization.getId());
        // Create a collection
        Collection stubCollection = OrganizationIT.openApiStubCollectionObject();
        stubCollection.setName("hcacollection");

        // Attach collection
        final Collection createdCollection = organizationsApiAdmin.createCollection(stubCollection, registeredOrganization.getId());
        // Add tool to collection
        organizationsApiAdmin.addEntryToCollection(registeredOrganization.getId(), createdCollection.getId(), appTool.getId(), null);

        // uncomment this after DOCK-1890 and delete from WebhookIT
        // Collection collection = organizationsApiAdmin.getCollectionById(registeredOrganization.getId(), createdCollection.getId());
        // assertTrue((collection.getEntries().stream().anyMatch(entry -> Objects.equals(entry.getId(), appTool.getId()))));
    }

    @Test
    void testDifferentLanguagesWithSameWorkflowName() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);

        // Add a WDL version of a workflow should pass.
        handleGitHubRelease(workflowClient, INSTALLATION_ID, workflowDockstoreYmlRepo, "refs/heads/sameWorkflowName-WDL", USER_2_USERNAME);
        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.README));

        // Add a CWL version of a workflow with the same name should cause error.
        try {
            handleGitHubRelease(workflowClient, INSTALLATION_ID, workflowDockstoreYmlRepo, "refs/heads/sameWorkflowName-CWL", USER_2_USERNAME);
            fail("should have thrown");
        } catch (ApiException ex) {
            List<LambdaEvent> events = usersApi.getUserGitHubEvents(0, 10);
            LambdaEvent event = events.stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).findFirst().get();
            String message = event.getMessage().toLowerCase();
            assertTrue(message.contains("descriptor language"));
            assertTrue(message.contains("workflow"));
            assertTrue(message.contains("version"));
        }
    }

    @Test
    void testWackyBranchCreationAndDeletion() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        // needs to be a branch to match branch deletion below
        handleGitHubRelease(workflowClient, INSTALLATION_ID, workflowDockstoreYmlRepo, "refs/heads/孤独のグルメ", USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(1, foobar.getWorkflowVersions().size());

        workflowClient.handleGitHubBranchDeletion(workflowDockstoreYmlRepo, USER_2_USERNAME, "refs/heads/孤独のグルメ", String.valueOf(INSTALLATION_ID));

        foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(0, foobar.getWorkflowVersions().size());
    }

    @Test
    void testNormalReadMeLocation() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowClient, INSTALLATION_ID, workflowDockstoreYmlRepo, "refs/tags/0.4", USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        Workflow foobar2 = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");


        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.DESCRIPTOR && workflowClient.getWorkflowVersionDescription(foobar.getId(), v.getId()).contains("This is a description")));
        assertTrue(foobar2.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.DESCRIPTOR && workflowClient.getWorkflowVersionDescription(foobar2.getId(), v.getId()).contains("This is a description")));
    }

    @Test
    void testRootReadMeLocation() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowClient, INSTALLATION_ID, workflowDockstoreYmlRepo, "refs/tags/0.3", USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        Workflow foobar2 = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");


        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.README && workflowClient.getWorkflowVersionDescription(foobar.getId(), v.getId()).contains("A repo that includes .dockstore.yml"
            + "\n")));
        assertTrue(foobar2.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.README && workflowClient.getWorkflowVersionDescription(foobar2.getId(), v.getId()).contains("A repo that includes .dockstore.yml"
            + "\n")));
    }

    @Test
    void testAlternateReadMeLocation() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowClient, INSTALLATION_ID, workflowDockstoreYmlRepo, "refs/tags/0.5", USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        Workflow foobar2 = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");

        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.CUSTOM_README && workflowClient.getWorkflowVersionDescription(foobar.getId(), v.getId()).contains("an 'X' in it")));
        assertTrue(foobar2.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.CUSTOM_README && workflowClient.getWorkflowVersionDescription(foobar2.getId(), v.getId()).contains("a '🙃' in it")));
    }

    @Test
    void testReadMePathOverridesDescriptor() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowClient, INSTALLATION_ID, workflowDockstoreYmlRepo, "refs/tags/0.7", USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        Workflow foobar2 = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");

        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.CUSTOM_README && workflowClient.getWorkflowVersionDescription(foobar.getId(), v.getId()).contains("an 'X' in it")));
        assertTrue(foobar2.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.CUSTOM_README && workflowClient.getWorkflowVersionDescription(foobar2.getId(), v.getId()).contains("a '🙃' in it")));
        // check that the descriptors in question really did have potential descriptions
        final List<SourceFile> foobarSourcefiles = workflowClient.getWorkflowVersionsSourcefiles(foobar.getId(), foobar.getWorkflowVersions().get(0).getId(), null);
        final List<SourceFile> foobar2Sourcefiles = workflowClient.getWorkflowVersionsSourcefiles(foobar2.getId(), foobar2.getWorkflowVersions().get(0).getId(), null);
        assertTrue(foobarSourcefiles.stream().anyMatch(s -> s.getContent().contains("This is a description")));
        assertTrue(foobar2Sourcefiles.stream().anyMatch(s -> s.getContent().contains("This is a description")));

        // Change the README path and description in the DB to dummy values
        testingPostgres.runUpdateStatement("update workflowversion set readmepath = null");
        testingPostgres.runUpdateStatement("update version_metadata set description = null");
        final Workflow foobarA = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        final Workflow foobar2A = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertTrue(foobarA.getWorkflowVersions().stream().allMatch(v -> v.getReadMePath() == null && workflowClient.getWorkflowVersionDescription(foobarA.getId(), v.getId()) == null));
        assertTrue(foobar2A.getWorkflowVersions().stream().allMatch(v -> v.getReadMePath() == null && workflowClient.getWorkflowVersionDescription(foobar2A.getId(), v.getId()) == null));

        // The GitHub release should update the readmepath and description to the correct values
        handleGitHubRelease(workflowClient, INSTALLATION_ID, workflowDockstoreYmlRepo, "refs/tags/0.7", USER_2_USERNAME);
        final Workflow foobarB = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        final Workflow foobar2B = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertTrue(foobarB.getWorkflowVersions().stream().allMatch(v -> "/README2.md".equals(v.getReadMePath()) && workflowClient.getWorkflowVersionDescription(foobarB.getId(), v.getId()).contains("an 'X' in it")));
        assertTrue(foobar2B.getWorkflowVersions().stream().allMatch(v -> "/docs/README.md".equals(v.getReadMePath()) && workflowClient.getWorkflowVersionDescription(foobar2B.getId(), v.getId()).contains("a '🙃' in it")));
    }

    @Test
    void testCheckingUserLambdaEventsAsAdmin() {
        final ApiClient userClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi userWorkflowsClient = new WorkflowsApi(userClient);
        UsersApi usersApi = new UsersApi(userClient);

        long userid = usersApi.getUser().getId();

        handleGitHubRelease(userWorkflowsClient, INSTALLATION_ID, taggedToolRepo, "refs/tags/1.0", USER_2_USERNAME);

        // Setup admin
        final ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        LambdaEventsApi lambdaEventsApi = new LambdaEventsApi(webClientAdminUser);

        List<LambdaEvent> lambdaEvents = lambdaEventsApi.getUserLambdaEvents(userid, 0, 100);
        assertEquals(1, lambdaEvents.size());
        assertEquals("refs/tags/1.0", lambdaEvents.get(0).getReference());
    }
        
    @Test
    void testGitVisibility() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        final String manualWorkflowPath = "DockstoreTestUser/dockstore-whalesay-wdl";
        workflowClient.manualRegister(SourceControl.GITHUB.name(), manualWorkflowPath, "/dockstore.wdl", "", DescriptorLanguage.WDL.getShortName(), "");
        final Workflow manualWorkflow = workflowClient.getWorkflowByPath(SourceControl.GITHUB.toString() + "/" + manualWorkflowPath,
            WorkflowSubClass.BIOWORKFLOW, "");
        workflowClient.refresh1(manualWorkflow.getId(), false);
        assertEquals(1, getNullVisibilityCount(), "Git visibility is null for manually registered workflows");

        handleGitHubRelease(workflowClient, INSTALLATION_ID, workflowDockstoreYmlRepo, "refs/tags/0.7", USER_2_USERNAME); // This creates 2 workflows
        assertEquals(2, getPublicVisibilityCount(), "Two workflows created should both have PUBLIC git visiblity");
        handleGitHubRelease(workflowClient, INSTALLATION_ID, workflowDockstoreYmlRepo, "refs/tags/0.5", USER_2_USERNAME); // This updates the 2 workflows
        assertEquals(2, getPublicVisibilityCount(), "Updated workflows should still both have PUBLIC git visiblity");
        assertEquals(1, getNullVisibilityCount(), "Git visibility should still be null for manually registered workflows");

        // Simulate transitioning a private repo to a public repo
        testingPostgres.runUpdateStatement("update workflow set gitvisibility = 'PRIVATE' where gitvisibility is not null;");
        assertEquals(0, getPublicVisibilityCount());
        handleGitHubRelease(workflowClient, INSTALLATION_ID, workflowDockstoreYmlRepo, "refs/tags/0.4", USER_2_USERNAME); // This updates the 2 workflows
        assertEquals(2, getPublicVisibilityCount(), "Private visibility should have changed to public");
    }

    private Long getPublicVisibilityCount() {
        return testingPostgres.runSelectStatement(
            "select count(*) from workflow where gitvisibility = 'PUBLIC'", long.class);
    }

    private Long getNullVisibilityCount() {
        return testingPostgres.runSelectStatement(
            "select count(*) from workflow where gitvisibility is null", long.class);
    }

    @Test
    void testEntryNameAndGroupIdInLambdaEvents() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final LambdaEventsApi lambdaEventsApi = new LambdaEventsApi(webClient);
        // Constants for the "dockstore-testing/workflow-dockstore-yml" repo
        final String tag01 = "refs/tags/0.1";
        final String tag02 = "refs/tags/0.2";
        final String branchInvalidDockstoreYml = "refs/heads/invalidDockstoreYml";
        final String branchDifferentLanguagesWithSameWorkflowName = "refs/heads/differentLanguagesWithSameWorkflowName";
        final String foobarWorkflowName = "foobar";
        final String foobar2WorkflowName = "foobar2";
        long numberOfWebhookInvocations = 0;

        // Track install event
        workflowsApi.handleGitHubInstallation(String.valueOf(INSTALLATION_ID), workflowDockstoreYmlRepo, USER_2_USERNAME);
        ++numberOfWebhookInvocations;
        List<LambdaEvent> orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.INSTALL, true); // There should be no entry name
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release 0.1 on GitHub - one new wdl workflow
        handleGitHubRelease(workflowsApi, INSTALLATION_ID, workflowDockstoreYmlRepo, tag01, USER_2_USERNAME);
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag01, foobarWorkflowName, true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release 0.2 on GitHub - one existing wdl workflow, one new cwl workflow
        handleGitHubRelease(workflowsApi, INSTALLATION_ID, workflowDockstoreYmlRepo, tag02, USER_2_USERNAME);
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag02, foobarWorkflowName, true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag02, foobar2WorkflowName, true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Delete tag 0.2
        workflowsApi.handleGitHubBranchDeletion(workflowDockstoreYmlRepo, USER_2_USERNAME, tag02, String.valueOf(INSTALLATION_ID));
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        // Delete events should have the names of workflows that had a version deleted
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.DELETE, tag02, foobarWorkflowName, true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.DELETE, tag02, foobar2WorkflowName, true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release refs/heads/invalidDockstoreYml where the foobar workflow description in the .dockstore.yml is missing the 'subclass' property
        assertThrows(ApiException.class, () -> handleGitHubRelease(workflowsApi, INSTALLATION_ID, workflowDockstoreYmlRepo, branchInvalidDockstoreYml, USER_2_USERNAME));
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        // There should be two push events, one failed event for workflow 'foobar' and one successful event for workflow 'foobar2'
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, branchInvalidDockstoreYml, foobarWorkflowName, false);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, branchInvalidDockstoreYml, foobar2WorkflowName, true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release refs/heads/differentLanguagesWithSameWorkflowName where two workflows have the same workflow name
        assertThrows(ApiException.class, () -> handleGitHubRelease(workflowsApi, INSTALLATION_ID, workflowDockstoreYmlRepo, branchDifferentLanguagesWithSameWorkflowName, USER_2_USERNAME));
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        // Should only have no entry name because the error is for the whole .dockstore.yml
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, branchDifferentLanguagesWithSameWorkflowName, null, false);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release using the repository "dockstore-testing/test-workflows-and-tools" which registers 1 unpublished tool and 1 published workflow
        final String tag10 = "refs/tags/1.0";
        handleGitHubRelease(workflowsApi, INSTALLATION_ID, testWorkflowsAndToolsRepo, tag10, USER_2_USERNAME);
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 15);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag10, "", true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag10, "md5sum", true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUBLISH, tag10, "", true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release refs/heads/invalidToolName. There is one successful workflow and one failed tool with an invalid name
        final String invalidToolNameBranch = "refs/heads/invalidToolName";
        assertThrows(ApiException.class, () -> handleGitHubRelease(workflowsApi, INSTALLATION_ID, testWorkflowsAndToolsRepo, invalidToolNameBranch, USER_2_USERNAME));
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 15);
        // There should be two push events, one successful event for the workflow and one failed event for the tool
        final String workflowName = "";
        final String toolName = "md5sum/with/slashes";
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, invalidToolNameBranch, workflowName, true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, invalidToolNameBranch, toolName, false);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);
    }

    /**
     * Assert success and entry name for a lambda event type where gitReference and entryName isn't applicable. Example: INSTALL events
     * @param lambdaEvents
     * @param lambdaEventType
     * @param expectedIsSuccess
     */
    private void assertEntryNameInNewestLambdaEvent(List<LambdaEvent> lambdaEvents, LambdaEvent.TypeEnum lambdaEventType, boolean expectedIsSuccess) {
        // Filter lambda events to the ones that are applicable
        List<LambdaEvent> filteredLambdaEvents = lambdaEvents.stream().filter(event -> lambdaEventType == event.getType()).toList();
        assertFalse(filteredLambdaEvents.isEmpty(), String.format("Should have at least 1 %s lambda event", lambdaEventType));

        LambdaEvent newestLambdaEvent = filteredLambdaEvents.get(0); // Newest one is always the first one
        assertEquals(expectedIsSuccess, newestLambdaEvent.isSuccess());
        assertNull(newestLambdaEvent.getEntryName(), "Should not have an entry name");
    }

    /**
     * Assert success and entry name for the newest lambda event for a specified type, gitReference and entryName.
     * @param lambdaEvents
     * @param lambdaEventType
     * @param gitReference
     * @param entryName
     * @param expectedIsSuccess
     */
    private void assertEntryNameInNewestLambdaEvent(List<LambdaEvent> lambdaEvents, LambdaEvent.TypeEnum lambdaEventType, String gitReference, String entryName, boolean expectedIsSuccess) {
        // Filter lambda events to the ones that are applicable
        List<LambdaEvent> filteredLambdaEvents = lambdaEvents.stream()
                .filter(event -> lambdaEventType == event.getType() && gitReference.equals(event.getReference()) && Objects.equals(entryName, event.getEntryName()))
                .toList();

        assertFalse(filteredLambdaEvents.isEmpty(), String.format("Should have at least 1 %s lambda event with git reference %s and entry name '%s'", lambdaEventType, gitReference, entryName));
        LambdaEvent newestLambdaEvent = filteredLambdaEvents.get(0); // Newest one is always the first one
        assertEquals(expectedIsSuccess, newestLambdaEvent.isSuccess());
    }

    /**
     * Assert that there are the expected number of unique delivery IDs in the list of lambda events.
     * @param lambdaEvents
     * @param expectedNumberOfDeliveryIds
     */
    private void assertNumberOfUniqueDeliveryIds(List<LambdaEvent> lambdaEvents, long expectedNumberOfDeliveryIds) {
        long numberOfDeliveryIds = lambdaEvents.stream().map(LambdaEvent::getDeliveryId).distinct().count();
        assertEquals(expectedNumberOfDeliveryIds, numberOfDeliveryIds);
    }

    /**
     * This tests the GitHub release process
     */
    @Test
    void testGitHubReleaseNoWorkflowOnDockstore() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        LambdaEventsApi lambdaEventsApi = new LambdaEventsApi(webClient);

        // Track install event
        client.handleGitHubInstallation(String.valueOf(INSTALLATION_ID), DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML, USER_2_USERNAME);

        // Release 0.1 on GitHub - one new wdl workflow
        handleGitHubRelease(client, INSTALLATION_ID, DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.1", USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertTrue(workflowCount >= 2, "should see 2 workflows from the .dockstore.yml from the master branch");

        // Ensure that new workflow is created and is what is expected
        Workflow workflow = getFoobar1Workflow(client);
        assertEquals(Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType(), "Should be a WDL workflow");
        assertEquals(Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Should be type DOCKSTORE_YML");
        assertEquals(1, workflow.getWorkflowVersions().stream().filter(v -> v.getName().contains("0.1")).toList().size(), "Should have one version 0.1");
        assertEquals("A repo that includes .dockstore.yml", workflow.getTopicAutomatic());

        // Release 0.2 on GitHub - one existing wdl workflow, one new cwl workflow
        handleGitHubRelease(client, INSTALLATION_ID, DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.2", USER_2_USERNAME);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(2, workflowCount);

        // Ensure that existing workflow is updated
        workflow = getFoobar1Workflow(client);

        // Ensure that new workflow is created and is what is expected
        Workflow workflow2 = getFoobar2Workflow(client);
        assertEquals(Workflow.DescriptorTypeEnum.CWL, workflow2.getDescriptorType(), "Should be a CWL workflow");
        assertEquals(Workflow.ModeEnum.DOCKSTORE_YML, workflow2.getMode(), "Should be type DOCKSTORE_YML");
        assertEquals(1, workflow2.getWorkflowVersions().stream().filter(v -> v.getName().contains("0.2")).toList().size(), "Should have one version 0.2");


        // Unset the license information to simulate license change
        testingPostgres.runUpdateStatement("update workflow set licensename=null");
        // Unset topicAutomatic to simulate a topicAutomatic change
        testingPostgres.runUpdateStatement("update workflow set topicAutomatic=null");
        // Branch master on GitHub - updates two existing workflows
        handleGitHubRelease(client, INSTALLATION_ID, DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML, "refs/heads/master", USER_2_USERNAME);
        List<Workflow> workflows = new ArrayList<>();
        workflows.add(workflow);
        workflows.add(workflow2);
        assertEquals(2, workflows.size(), "Should only have two workflows");
        workflows.forEach(workflowIndividual -> {
            assertEquals("Apache License 2.0", workflowIndividual.getLicenseInformation().getLicenseName(), "Should be able to get license after manual GitHub App version update");
            assertEquals("A repo that includes .dockstore.yml", workflowIndividual.getTopicAutomatic(), "Should be able to get topic from GitHub after GitHub App version update");
        });

        workflow = getFoobar1Workflow(client);
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "master")),
                "Should have a master version.");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.1")),
                "Should have a 0.1 version.");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")),
                "Should have a 0.2 version.");

        workflow2 = getFoobar2Workflow(client);
        assertTrue(workflow2.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "master")),
                "Should have a master version.");
        assertTrue(workflow2.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")),
                "Should have a 0.2 version.");

        // Master version should have metadata set
        Optional<WorkflowVersion> masterVersion = workflow.getWorkflowVersions().stream().filter((WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertEquals("Test User", masterVersion.get().getAuthor(), "Should have author set");
        assertEquals("test@dockstore.org", masterVersion.get().getEmail(), "Should have email set");

        masterVersion = workflow2.getWorkflowVersions().stream().filter((WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertEquals("Test User", masterVersion.get().getAuthor(), "Should have author set");
        assertTrue(masterVersion.get().isValid(), "Should be valid");
        assertEquals("test@dockstore.org", masterVersion.get().getEmail(), "Should have email set");

        boolean hasLegacyVersion = workflow.getWorkflowVersions().stream().anyMatch(
                WorkflowVersion::isLegacyVersion);
        assertFalse(hasLegacyVersion, "Workflow should not have any legacy refresh versions.");

        // Delete tag 0.2
        client.handleGitHubBranchDeletion(DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML, USER_2_USERNAME, "refs/tags/0.2", String.valueOf(INSTALLATION_ID));
        workflow = getFoobar1Workflow(client);
        assertTrue(workflow.getWorkflowVersions().stream().noneMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")),
                "Should not have a 0.2 version.");
        workflow2 = getFoobar2Workflow(client);
        assertTrue(workflow2.getWorkflowVersions().stream().noneMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")),
                "Should not have a 0.2 version.");

        // Add version that doesn't exist
        long failedCount = usersApi.getUserGitHubEvents(0, 10).stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count();
        try {
            handleGitHubRelease(client, INSTALLATION_ID, DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML, "refs/heads/idonotexist", USER_2_USERNAME);
            fail("Should fail and not reach this point");
        } catch (ApiException ex) {
            assertEquals(failedCount + 1, usersApi.getUserGitHubEvents(0, 10).stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count(), "There should be one more unsuccessful event than before");
        }

        // There should be 13 successful lambda events
        List<LambdaEvent> events = usersApi.getUserGitHubEvents(0, 20);
        assertEquals(13, events.stream().filter(LambdaEvent::isSuccess).count(), "There should be 13 successful events");

        // Test pagination for user github events
        events = usersApi.getUserGitHubEvents(2, 2);
        assertEquals(2, events.size(), "There should be 2 events (id 13 and 14)");
        assertTrue(events.stream().anyMatch(lambdaEvent -> Objects.equals(13L, lambdaEvent.getId())), "Should have event with ID 13");
        assertTrue(events.stream().anyMatch(lambdaEvent -> Objects.equals(14L, lambdaEvent.getId())), "Should have event with ID 14");

        // Test the organization events endpoint
        List<LambdaEvent> orgEvents = lambdaEventsApi.getLambdaEventsByOrganization("DockstoreTestUser2", "0", 20);
        assertEquals(16, orgEvents.size(), "There should be 16 events");

        // Test pagination
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization("DockstoreTestUser2", "2", 2);
        assertEquals(2, orgEvents.size(), "There should be 2 events (id 13 and 14)");
        assertTrue(orgEvents.stream().anyMatch(lambdaEvent -> Objects.equals(13L, lambdaEvent.getId())), "Should have event with ID 13");
        assertTrue(orgEvents.stream().anyMatch(lambdaEvent -> Objects.equals(14L, lambdaEvent.getId())), "Should have event with ID 14");

        // Change organization to test filter
        testingPostgres.runUpdateStatement("UPDATE lambdaevent SET repository = 'workflow-dockstore-yml', organization = 'DockstoreTestUser3' WHERE id = '1'");

        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization("DockstoreTestUser2", "0", 20);
        assertEquals(15, orgEvents.size(), "There should now be 15 events");

        try {
            lambdaEventsApi.getLambdaEventsByOrganization("IAmMadeUp", "0", 10);
            fail("Should not reach this statement");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getCode(), "Should fail because user cannot access org.");
        }

        // Try adding version with empty test parameter file (should work)
        handleGitHubRelease(client, INSTALLATION_ID, DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML, "refs/heads/emptytestparameter", USER_2_USERNAME);
        workflow2 = getFoobar2Workflow(client);
        assertTrue(workflow2.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "emptytestparameter")).findFirst().get().isValid(),
                "Should have emptytestparameter version that is valid");
        testValidationUpdate(client);
        testDefaultVersion(client);
    }

    private Workflow getFoobar1Workflow(WorkflowsApi client) {
        return client.getWorkflowByPath("github.com/" + DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
    }

    private Workflow getFoobar2Workflow(WorkflowsApi client) {
        return client.getWorkflowByPath("github.com/" + DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");
    }

    /**
     * This tests that when a version was invalid, a new GitHub release will retrigger the validation
     * @param client    WorkflowsApi
     */
    private void testValidationUpdate(WorkflowsApi client) {
        testingPostgres.runUpdateStatement("update workflowversion set valid='f'");

        Workflow workflow2 = getFoobar2Workflow(client);
        Optional<WorkflowVersion> masterVersion = workflow2.getWorkflowVersions().stream().filter((WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertFalse(masterVersion.get().isValid(), "Master version should be invalid because it was manually changed");

        handleGitHubRelease(client, INSTALLATION_ID, DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML, "refs/heads/master", USER_2_USERNAME);
        workflow2 = getFoobar2Workflow(client);
        masterVersion = workflow2.getWorkflowVersions().stream().filter((WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertTrue(masterVersion.get().isValid(), "Master version should be valid after GitHub App triggered again");
    }

    private void testDefaultVersion(WorkflowsApi client) {
        Workflow workflow2 = getFoobar2Workflow(client);
        assertNull(workflow2.getDefaultVersion());
        Workflow workflow = getFoobar1Workflow(client);
        assertNull(workflow.getDefaultVersion());
        handleGitHubRelease(client, INSTALLATION_ID, DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.4", USER_2_USERNAME);
        workflow2 = getFoobar2Workflow(client);
        assertEquals("0.4", workflow2.getDefaultVersion(), "The new tag says the latest tag should be the default version");
        workflow = getFoobar1Workflow(client);
        assertNull(workflow.getDefaultVersion());
    }

    /**
     * This test relies on Hoverfly to simulate responses from the ORCID API.
     * In the simulation, the responses are crafted for an ORCID author with ID 0000-0002-6130-1021.
     * ORCID authors with other IDs are considered "not found" by the simulation.
     */
    @Test
    void testGetWorkflowVersionOrcidAuthors() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        ApiClient anonymousWebClient = getAnonymousOpenAPIWebClient();
        WorkflowsApi anonymousWorkflowsApi = new WorkflowsApi(anonymousWebClient);
        String wdlWorkflowRepoPath = String.format("github.com/%s/%s", DockstoreTestUser2Repos.TEST_AUTHORS, "foobar");

        // Workflows containing 1 descriptor author and multiple .dockstore.yml authors.
        // If the .dockstore.yml specifies an author, then only the .dockstore.yml's authors should be saved
        handleGitHubRelease(workflowsApi, INSTALLATION_ID, DockstoreTestUser2Repos.TEST_AUTHORS, "refs/heads/main", USER_2_USERNAME);
        // WDL workflow
        Workflow workflow = workflowsApi.getWorkflowByPath(wdlWorkflowRepoPath, WorkflowSubClass.BIOWORKFLOW, "versions,authors");
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(2, version.getAuthors().size());
        assertEquals(2, version.getOrcidAuthors().size());

        // Hoverfly is not used as a class rule here because for some reason it's trying to intercept GitHub in both spy and simulation mode
        try (Hoverfly hoverfly = new Hoverfly(HoverflyMode.SIMULATE)) {
            hoverfly.start();
            hoverfly.simulate(ORCID_SIMULATION_SOURCE);
            List<OrcidAuthorInformation> orcidAuthorInfo = workflowsApi.getWorkflowVersionOrcidAuthors(workflow.getId(), version.getId());
            assertEquals(1, orcidAuthorInfo.size()); // There's 1 OrcidAuthorInfo instead of 2 because only 1 ORCID ID from the version exists on ORCID

            // Publish workflow
            PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);
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
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);

        try {
            handleGitHubRelease(workflowsApi, INSTALLATION_ID, DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML, "refs/heads/invalidWorkflowName", USER_2_USERNAME);
        } catch (ApiException ex) {
            assertEquals(LAMBDA_ERROR, ex.getCode(), "Should not be able to add a workflow with an invalid name");
            List<LambdaEvent> failEvents = usersApi.getUserGitHubEvents(0, 10);
            assertEquals(1, failEvents.stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count(), "There should be 1 unsuccessful event");
            assertTrue(failEvents.get(0).getMessage().contains(ValidationConstants.ENTRY_NAME_REGEX_MESSAGE));
        }
    }

    @Test
    void testChangingAppToolTopicsOpenapi() {
        final ApiClient openApiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(openApiClient);

        handleGitHubRelease(client, INSTALLATION_ID, taggedToolRepo, "refs/tags/1.0", USER_2_USERNAME);
        Workflow appTool = client.getWorkflowByPath("github.com/" + taggedToolRepoPath, WorkflowSubClass.APPTOOL, "versions,validations");

        PublishRequest publishRequest = new PublishRequest();
        publishRequest.publish(true);

        client.publish1(appTool.getId(), publishRequest);

        String newTopic = "this is a new topic";
        appTool.setTopicManual(newTopic);
        appTool = client.updateWorkflow(appTool.getId(), appTool);
        assertEquals(newTopic, appTool.getTopicManual());
    }

    /**
     * Tests that the GitHub release syncs a workflow's metadata with the default version's metadata.
     * Tests two scenarios:
     * <li>The default version for a workflow is set using the latestTagAsDefault property from the dockstore.yml</li>
     * <li>The default version for a workflow is set manually using the API</li>
     */
    @Test
    void testSyncWorkflowMetadataWithDefaultVersion() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowsApi, INSTALLATION_ID, DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.4", USER_2_USERNAME);
        Workflow workflow = getFoobar1Workflow(workflowsApi); // dockstore.yml for foobar doesn't have latestTagAsDefault set
        Workflow workflow2 = getFoobar2Workflow(workflowsApi); // dockstore.yml for foobar2 has latestTagAsDefault set
        assertNull(workflow.getDefaultVersion());
        assertEquals("0.4", workflow2.getDefaultVersion(), "Should have latest tag set as default version");

        workflowsApi.updateDefaultVersion1(workflow.getId(), "0.4"); // Set default version for workflow that doesn't have one
        workflow = getFoobar1Workflow(workflowsApi);
        assertEquals("0.4", workflow.getDefaultVersion(), "Should have default version set");

        // Find WorkflowVersion for default version and make sure it has metadata set
        Optional<WorkflowVersion> defaultVersion = workflow.getWorkflowVersions().stream()
                .filter((WorkflowVersion version) -> Objects.equals(version.getName(), "0.4"))
                .findFirst();
        assertTrue(defaultVersion.isPresent());
        assertEquals("Test User", defaultVersion.get().getAuthor(), "Version should have author set");
        assertEquals("test@dockstore.org", defaultVersion.get().getEmail(), "Version should have email set");

        // Check that the workflow metadata is the same as the default version's metadata
        checkWorkflowMetadataWithDefaultVersionMetadata(workflow, defaultVersion.get());
        checkWorkflowMetadataWithDefaultVersionMetadata(workflow2, defaultVersion.get());

        // Clear workflow metadata to test the scenario where the default version metadata was updated and is now out of sync with the workflow's metadata
        testingPostgres.runUpdateStatement(String.format("UPDATE author SET name = 'foo' where versionid = '%s'", defaultVersion.get().getId()));
        testingPostgres.runUpdateStatement(String.format("UPDATE author SET email = 'foo' where versionid = '%s'", defaultVersion.get().getId()));
        testingPostgres.runUpdateStatement(String.format("UPDATE workflow SET description = NULL where id = '%s'", workflow.getId()));
        // GitHub release should sync metadata with default version
        handleGitHubRelease(workflowsApi, INSTALLATION_ID, DockstoreTestUser2Repos.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.4", USER_2_USERNAME);
        workflow = getFoobar1Workflow(workflowsApi);
        workflow2 = getFoobar2Workflow(workflowsApi);
        checkWorkflowMetadataWithDefaultVersionMetadata(workflow, defaultVersion.get());
        checkWorkflowMetadataWithDefaultVersionMetadata(workflow2, defaultVersion.get());
    }

    // Asserts that the workflow metadata is the same as the default version metadata
    private void checkWorkflowMetadataWithDefaultVersionMetadata(Workflow workflow, WorkflowVersion defaultVersion) {
        assertEquals(1, defaultVersion.getAuthors().size());
        assertEquals(defaultVersion.getAuthors().size(), workflow.getAuthors().size());
        assertEquals(defaultVersion.getAuthors().get(0), workflow.getAuthors().get(0), "Workflow author should equal default version author");
    }

    /**
     * Tests that the language version in WDL descriptor files is correct during a GitHub release
     */
    @Test
    void testDockstoreYmlWorkflowLanguageVersions() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        String wdlWorkflowRepo = "dockstore-testing/dockstore-whalesay2";

        handleGitHubRelease(workflowsApi, INSTALLATION_ID, wdlWorkflowRepo, "refs/heads/master", USER_2_USERNAME);
        Workflow workflow = workflowsApi.getWorkflowByPath("github.com/" + wdlWorkflowRepo, WorkflowSubClass.BIOWORKFLOW, "versions");
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst().get();
        List<SourceFile> sourceFiles = workflowsApi.getWorkflowVersionsSourcefiles(workflow.getId(), version.getId(), null);
        assertNotNull(sourceFiles);
        assertEquals(2, sourceFiles.size());
        sourceFiles.forEach(sourceFile -> {
            if ("/Dockstore.wdl".equals(sourceFile.getAbsolutePath())) {
                assertEquals(DescriptorLanguage.FileType.DOCKSTORE_WDL.name(), sourceFile.getType().getValue());
                assertEquals("1.0", sourceFile.getMetadata().getTypeVersion(), "Language version of WDL descriptor with 'version 1.0' should be 1.0");
            } else {
                assertEquals(DescriptorLanguage.FileType.DOCKSTORE_YML.name(), sourceFile.getType().getValue());
                assertNull(sourceFile.getMetadata().getTypeVersion(), ".dockstore.yml should not have a version");
            }
        });
        assertEquals(1, version.getVersionMetadata().getDescriptorTypeVersions().size(), "Should only have one language version");
        assertTrue(version.getVersionMetadata().getDescriptorTypeVersions().contains("1.0"));
    }

    /**
     * Tests that an attempt to register a WDL that contains recursive
     * remote references will result in failure.
     * <a href="https://ucsc-cgl.atlassian.net/browse/DOCK-2299">...</a>
     */
    @Test
    void testRegistrationOfRecursiveWDL() {
        final ApiClient openApiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(openApiClient);
        UsersApi usersApi = new UsersApi(openApiClient);

        // Attempt to process a repo containing a recursive WDL.  Internally, we use the same libraries that Cromwell does to process WDLs.
        // The WDL processing code should throw a StackOverflowError, which is remapped to a more explanatory CustomWebApplicationException, which will trigger a typical registration failure.
        try {
            handleGitHubRelease(client, INSTALLATION_ID, "dockstore-testing/recursive-wdl", "refs/heads/main", USER_2_USERNAME);
            fail("should have thrown");
        } catch (ApiException ex) {
            // Confirm that the release failed and was logged correctly.
            List<LambdaEvent> events = usersApi.getUserGitHubEvents(0, 10);
            assertEquals(1, events.size(), "There should be one event");
            assertEquals(0, events.stream().filter(LambdaEvent::isSuccess).count(), "There should be no successful events");
            assertTrue(events.get(0).getMessage().contains(WDLHandler.ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT), "Event message should indicate the problem");
        }
    }
}
