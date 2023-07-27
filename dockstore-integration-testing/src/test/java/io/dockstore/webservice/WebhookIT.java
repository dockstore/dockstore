
package io.dockstore.webservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.client.cli.BasicIT;
import io.dockstore.client.cli.OrganizationIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.LambdaEventsApi;
import io.dockstore.openapi.client.api.OrganizationsApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Collection;
import io.dockstore.openapi.client.model.LambdaEvent;
import io.dockstore.openapi.client.model.Organization;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.openapi.client.model.WorkflowVersion.DescriptionSourceEnum;
import java.util.List;
import java.util.Objects;
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

    private final String installationId = "1179416";
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
        final ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(openApiClient);

        client.handleGitHubRelease(taggedToolRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
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
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);

        // Add a WDL version of a workflow should pass.
        workflowClient.handleGitHubRelease("refs/heads/sameWorkflowName-WDL", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);
        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.README));

        // Add a CWL version of a workflow with the same name should cause error.
        try {
            workflowClient.handleGitHubRelease("refs/heads/sameWorkflowName-CWL", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);
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
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        // needs to be a branch to match branch deletion below
        workflowClient.handleGitHubRelease("refs/heads/孤独のグルメ", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(1, foobar.getWorkflowVersions().size());

        workflowClient.handleGitHubBranchDeletion(workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME, "refs/heads/孤独のグルメ", installationId);

        foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(0, foobar.getWorkflowVersions().size());
    }

    @Test
    void testNormalReadMeLocation() {
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        workflowClient.handleGitHubRelease("refs/tags/0.4", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        Workflow foobar2 = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");


        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.DESCRIPTOR && workflowClient.getWorkflowVersionDescription(foobar.getId(), v.getId()).contains("This is a description")));
        assertTrue(foobar2.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.DESCRIPTOR && workflowClient.getWorkflowVersionDescription(foobar2.getId(), v.getId()).contains("This is a description")));
    }

    @Test
    void testRootReadMeLocation() {
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        workflowClient.handleGitHubRelease("refs/tags/0.3", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        Workflow foobar2 = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");


        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.README && workflowClient.getWorkflowVersionDescription(foobar.getId(), v.getId()).contains("A repo that includes .dockstore.yml"
            + "\n")));
        assertTrue(foobar2.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.README && workflowClient.getWorkflowVersionDescription(foobar2.getId(), v.getId()).contains("A repo that includes .dockstore.yml"
            + "\n")));
    }

    @Test
    void testAlternateReadMeLocation() {
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        workflowClient.handleGitHubRelease("refs/tags/0.5", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        Workflow foobar2 = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");


        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.CUSTOM_README && workflowClient.getWorkflowVersionDescription(foobar.getId(), v.getId()).contains("an 'X' in it")));
        assertTrue(foobar2.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.CUSTOM_README && workflowClient.getWorkflowVersionDescription(foobar2.getId(), v.getId()).contains("a '🙃' in it")));
    }

    @Test
    void testReadMePathOverridesDescriptor() {
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        workflowClient.handleGitHubRelease("refs/tags/0.7", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);

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
        workflowClient.handleGitHubRelease("refs/tags/0.7", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);
        final Workflow foobarB = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        final Workflow foobar2B = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertTrue(foobarB.getWorkflowVersions().stream().allMatch(v -> "/README2.md".equals(v.getReadMePath()) && workflowClient.getWorkflowVersionDescription(foobarB.getId(), v.getId()).contains("an 'X' in it")));
        assertTrue(foobar2B.getWorkflowVersions().stream().allMatch(v -> "/docs/README.md".equals(v.getReadMePath()) && workflowClient.getWorkflowVersionDescription(foobar2B.getId(), v.getId()).contains("a '🙃' in it")));
    }

    @Test
    void testCheckingUserLambdaEventsAsAdmin() {
        final ApiClient userClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi userWorkflowsClient = new WorkflowsApi(userClient);
        UsersApi usersApi = new UsersApi(userClient);

        long userid = usersApi.getUser().getId();

        userWorkflowsClient.handleGitHubRelease("refs/tags/1.0", installationId, taggedToolRepo, BasicIT.USER_2_USERNAME);

        // Setup admin
        final ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        LambdaEventsApi lambdaEventsApi = new LambdaEventsApi(webClientAdminUser);

        List<LambdaEvent> lambdaEvents = lambdaEventsApi.getUserLambdaEvents(userid, 0, 100);
        assertEquals(1, lambdaEvents.size());
        assertEquals("refs/tags/1.0", lambdaEvents.get(0).getReference());
    }
        
    @Test
    void testGitVisibility() {
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        final String manualWorkflowPath = "DockstoreTestUser/dockstore-whalesay-wdl";
        workflowClient.manualRegister(SourceControl.GITHUB.name(), manualWorkflowPath, "/dockstore.wdl", "", DescriptorLanguage.WDL.getShortName(), "");
        final Workflow manualWorkflow = workflowClient.getWorkflowByPath(SourceControl.GITHUB.toString() + "/" + manualWorkflowPath,
            WorkflowSubClass.BIOWORKFLOW, "");
        workflowClient.refresh1(manualWorkflow.getId(), false);
        assertEquals(1, getNullVisibilityCount(), "Git visibility is null for manually registered workflows");

        workflowClient.handleGitHubRelease("refs/tags/0.7", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME); // This creates 2 workflows
        assertEquals(2, getPublicVisibilityCount(), "Two workflows created should both have PUBLIC git visiblity");
        workflowClient.handleGitHubRelease("refs/tags/0.5", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME); // This updates the 2 workflows
        assertEquals(2, getPublicVisibilityCount(), "Updated workflows should still both have PUBLIC git visiblity");
        assertEquals(1, getNullVisibilityCount(), "Git visibility should still be null for manually registered workflows");

        // Simulate transitioning a private repo to a public repo
        testingPostgres.runUpdateStatement("update workflow set gitvisibility = 'PRIVATE' where gitvisibility is not null;");
        assertEquals(0, getPublicVisibilityCount());
        workflowClient.handleGitHubRelease("refs/tags/0.4", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME); // This updates the 2 workflows
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
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
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
        workflowsApi.handleGitHubInstallation(installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);
        ++numberOfWebhookInvocations;
        List<LambdaEvent> orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.INSTALL, true); // There should be no entry name
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release 0.1 on GitHub - one new wdl workflow
        workflowsApi.handleGitHubRelease(tag01, installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag01, foobarWorkflowName, true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release 0.2 on GitHub - one existing wdl workflow, one new cwl workflow
        workflowsApi.handleGitHubRelease(tag02, installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag02, foobarWorkflowName, true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag02, foobar2WorkflowName, true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Delete tag 0.2
        workflowsApi.handleGitHubBranchDeletion(workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME, tag02, installationId);
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        // Delete events should have the names of workflows that had a version deleted
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.DELETE, tag02, foobarWorkflowName, true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.DELETE, tag02, foobar2WorkflowName, true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release refs/heads/invalidDockstoreYml where the foobar workflow description in the .dockstore.yml is missing the 'subclass' property
        assertThrows(ApiException.class, () -> workflowsApi.handleGitHubRelease(branchInvalidDockstoreYml, installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME));
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        // There should be two push events, one failed event for workflow 'foobar' and one successful event for workflow 'foobar2'
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, branchInvalidDockstoreYml, foobarWorkflowName, false);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, branchInvalidDockstoreYml, foobar2WorkflowName, true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release refs/heads/differentLanguagesWithSameWorkflowName where two workflows have the same workflow name
        assertThrows(ApiException.class, () -> workflowsApi.handleGitHubRelease(branchDifferentLanguagesWithSameWorkflowName, installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME));
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        // Should only have no entry name because the error is for the whole .dockstore.yml
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, branchDifferentLanguagesWithSameWorkflowName, null, false);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release using the repository "dockstore-testing/test-workflows-and-tools" which registers 1 unpublished tool and 1 published workflow
        final String tag10 = "refs/tags/1.0";
        workflowsApi.handleGitHubRelease(tag10, installationId, testWorkflowsAndToolsRepo, BasicIT.USER_2_USERNAME);
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 15);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag10, "", true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag10, "md5sum", true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUBLISH, tag10, "", true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release refs/heads/invalidToolName. There is one successful workflow and one failed tool with an invalid name
        final String invalidToolNameBranch = "refs/heads/invalidToolName";
        assertThrows(ApiException.class, () -> workflowsApi.handleGitHubRelease(invalidToolNameBranch, installationId, testWorkflowsAndToolsRepo, BasicIT.USER_2_USERNAME));
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
}
