
package io.dockstore.webservice;

import static io.dockstore.client.cli.WorkflowIT.DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME;
import static io.dockstore.common.Hoverfly.ORCID_SIMULATION_SOURCE;
import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATH;
import static io.dockstore.webservice.helpers.GitHubAppHelper.LAMBDA_ERROR;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubBranchDeletion;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubInstallation;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubRelease;
import static io.openapi.api.impl.ToolClassesApiServiceImpl.COMMAND_LINE_TOOL;
import static io.openapi.api.impl.ToolClassesApiServiceImpl.NOTEBOOK;
import static io.openapi.api.impl.ToolClassesApiServiceImpl.WORKFLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.client.cli.OrganizationIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.RepositoryConstants.DockstoreTestUser2;
import io.dockstore.common.RepositoryConstants.DockstoreTesting;
import io.dockstore.common.SourceControl;
import io.dockstore.common.ValidationConstants;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.api.LambdaEventsApi;
import io.dockstore.openapi.client.api.MetadataApi;
import io.dockstore.openapi.client.api.OrganizationsApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Collection;
import io.dockstore.openapi.client.model.Entry;
import io.dockstore.openapi.client.model.LambdaEvent;
import io.dockstore.openapi.client.model.OrcidAuthorInformation;
import io.dockstore.openapi.client.model.Organization;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.StarRequest;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.Validation;
import io.dockstore.openapi.client.model.Version.DescriptionSourceEnum;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum;
import io.dockstore.openapi.client.model.Workflow.ModeEnum;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.core.EntryTypeMetadata;
import io.dockstore.webservice.helpers.GitHubAppHelper;
import io.dockstore.webservice.jdbi.AppToolDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dockstore.webservice.languages.WDLHandler;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
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

    private FileDAO fileDAO;
    private AppToolDAO appToolDAO;
    private NotebookDAO notebookDAO;
    private WorkflowVersionDAO workflowVersionDAO;
    private Session session;

    @BeforeEach
    @Override
    public void resetDBBetweenTests() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        this.fileDAO = new FileDAO(sessionFactory);
        this.appToolDAO = new AppToolDAO(sessionFactory);
        this.notebookDAO = new NotebookDAO(sessionFactory);
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);
        // used to allow us to use DAOs outside the web service
        session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Test
    void testAppToolCollections() {
        final ApiClient openApiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(openApiClient);
        handleGitHubRelease(client, DockstoreTesting.TAGGED_APPTOOL, "refs/tags/1.0", USER_2_USERNAME);
        Workflow appTool = client.getWorkflowByPath("github.com/" + DockstoreTesting.TAGGED_APPTOOL_TOOL_PATH, WorkflowSubClass.APPTOOL, "versions,validations");

        WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        final PublishRequest publishRequest = new PublishRequest();
        publishRequest.publish(true);
        client.publish1(appTool.getId(), publishRequest);

        // Setup admin. admin: true, curator: false
        final ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);
        // Create the organization
        Organization registeredOrganization = OrganizationIT.createOpenAPIOrg(organizationsApiAdmin);

        // Admin approve it
        organizationsApiAdmin.approveOrganization(registeredOrganization.getId());
        // Create a collection
        Collection stubCollection = OrganizationIT.openApiStubCollectionObject();
        stubCollection.setName("hcacollection");

        // Attach collection
        final Collection createdCollection = organizationsApiAdmin.createCollection(stubCollection, registeredOrganization.getId());
        // Add tool to collection
        organizationsApiAdmin.addEntryToCollection(registeredOrganization.getId(), createdCollection.getId(), appTool.getId(), null);

        Collection collection = organizationsApiAdmin.getCollectionById(registeredOrganization.getId(), createdCollection.getId());
        assertTrue((collection.getEntries().stream().anyMatch(entry -> Objects.equals(entry.getId(), appTool.getId()))));
    }

    @Test
    void testDifferentLanguagesWithSameWorkflowName() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);

        // Add a WDL version of a workflow should pass.
        handleGitHubRelease(workflowClient, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/heads/sameWorkflowName-WDL", USER_2_USERNAME);
        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.README));

        // Add a CWL version of a workflow with the same name should cause error.
        try {
            handleGitHubRelease(workflowClient, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/heads/sameWorkflowName-CWL", USER_2_USERNAME);
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
        handleGitHubRelease(workflowClient, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/heads/孤独のグルメ", USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(1, foobar.getWorkflowVersions().size());

        handleGitHubBranchDeletion(workflowClient, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, USER_2_USERNAME, "refs/heads/孤独のグルメ");

        foobar = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(0, foobar.getWorkflowVersions().size());
    }

    @Test
    void testNormalReadMeLocation() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowClient, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.4", USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        Workflow foobar2 = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");


        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.DESCRIPTOR && workflowClient.getWorkflowVersionDescription(foobar.getId(), v.getId()).contains("This is a description")));
        assertTrue(foobar2.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.DESCRIPTOR && workflowClient.getWorkflowVersionDescription(foobar2.getId(), v.getId()).contains("This is a description")));
    }

    @Test
    void testRootReadMeLocation() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowClient, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.3", USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        Workflow foobar2 = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");


        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.README && workflowClient.getWorkflowVersionDescription(foobar.getId(), v.getId()).contains("A repo that includes .dockstore.yml"
            + "\n")));
        assertTrue(foobar2.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.README && workflowClient.getWorkflowVersionDescription(foobar2.getId(), v.getId()).contains("A repo that includes .dockstore.yml"
            + "\n")));
    }

    @Test
    void testAlternateReadMeLocation() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowClient, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.5", USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        Workflow foobar2 = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");

        assertTrue(foobar.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.CUSTOM_README && workflowClient.getWorkflowVersionDescription(foobar.getId(), v.getId()).contains("an 'X' in it")));
        assertTrue(foobar2.getWorkflowVersions().stream().allMatch(v -> v.getDescriptionSource() == DescriptionSourceEnum.CUSTOM_README && workflowClient.getWorkflowVersionDescription(foobar2.getId(), v.getId()).contains("a '🙃' in it")));
    }

    @Test
    void testReadMePathOverridesDescriptor() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        handleGitHubRelease(workflowClient, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.7", USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        Workflow foobar2 = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");

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
        final Workflow foobarA = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        final Workflow foobar2A = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertTrue(foobarA.getWorkflowVersions().stream().allMatch(v -> v.getReadMePath() == null && workflowClient.getWorkflowVersionDescription(foobarA.getId(), v.getId()) == null));
        assertTrue(foobar2A.getWorkflowVersions().stream().allMatch(v -> v.getReadMePath() == null && workflowClient.getWorkflowVersionDescription(foobar2A.getId(), v.getId()) == null));

        // The GitHub release should update the readmepath and description to the correct values
        handleGitHubRelease(workflowClient, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.7", USER_2_USERNAME);
        final Workflow foobarB = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        final Workflow foobar2B = workflowClient.getWorkflowByPath("github.com/" + DockstoreTesting.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertTrue(foobarB.getWorkflowVersions().stream().allMatch(v -> "/README2.md".equals(v.getReadMePath()) && workflowClient.getWorkflowVersionDescription(foobarB.getId(), v.getId()).contains("an 'X' in it")));
        assertTrue(foobar2B.getWorkflowVersions().stream().allMatch(v -> "/docs/README.md".equals(v.getReadMePath()) && workflowClient.getWorkflowVersionDescription(foobar2B.getId(), v.getId()).contains("a '🙃' in it")));
    }

    @Test
    void testCheckingUserLambdaEventsAsAdmin() {
        final ApiClient userClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi userWorkflowsClient = new WorkflowsApi(userClient);
        UsersApi usersApi = new UsersApi(userClient);

        long userid = usersApi.getUser().getId();

        handleGitHubRelease(userWorkflowsClient, DockstoreTesting.TAGGED_APPTOOL, "refs/tags/1.0", USER_2_USERNAME);

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

        handleGitHubRelease(workflowClient, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.7", USER_2_USERNAME); // This creates 2 workflows
        assertEquals(2, getPublicVisibilityCount(), "Two workflows created should both have PUBLIC git visiblity");
        handleGitHubRelease(workflowClient, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.5", USER_2_USERNAME); // This updates the 2 workflows
        assertEquals(2, getPublicVisibilityCount(), "Updated workflows should still both have PUBLIC git visiblity");
        assertEquals(1, getNullVisibilityCount(), "Git visibility should still be null for manually registered workflows");

        // Simulate transitioning a private repo to a public repo
        testingPostgres.runUpdateStatement("update workflow set gitvisibility = 'PRIVATE' where gitvisibility is not null;");
        assertEquals(0, getPublicVisibilityCount());
        handleGitHubRelease(workflowClient, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.4", USER_2_USERNAME); // This updates the 2 workflows
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
        handleGitHubInstallation(workflowsApi, List.of(DockstoreTesting.WORKFLOW_DOCKSTORE_YML), USER_2_USERNAME);
        ++numberOfWebhookInvocations;
        List<LambdaEvent> orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.INSTALL, true); // There should be no entry name
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release 0.1 on GitHub - one new wdl workflow
        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, tag01, USER_2_USERNAME);
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag01, foobarWorkflowName, true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release 0.2 on GitHub - one existing wdl workflow, one new cwl workflow
        handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, tag02, USER_2_USERNAME);
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag02, foobarWorkflowName, true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag02, foobar2WorkflowName, true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Delete tag 0.2
        handleGitHubBranchDeletion(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, USER_2_USERNAME, tag02);
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        // Delete events should have the names of workflows that had a version deleted
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.DELETE, tag02, foobarWorkflowName, true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.DELETE, tag02, foobar2WorkflowName, true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release refs/heads/invalidDockstoreYml where the foobar workflow description in the .dockstore.yml is missing the 'subclass' property
        assertThrows(ApiException.class, () -> handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, branchInvalidDockstoreYml, USER_2_USERNAME));
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        // There should be two push events, one failed event for workflow 'foobar' and one successful event for workflow 'foobar2'
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, branchInvalidDockstoreYml, foobarWorkflowName, false);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, branchInvalidDockstoreYml, foobar2WorkflowName, true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release refs/heads/differentLanguagesWithSameWorkflowName where two workflows have the same workflow name
        assertThrows(ApiException.class, () -> handleGitHubRelease(workflowsApi, DockstoreTesting.WORKFLOW_DOCKSTORE_YML, branchDifferentLanguagesWithSameWorkflowName, USER_2_USERNAME));
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 10);
        // Should only have no entry name because the error is for the whole .dockstore.yml
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, branchDifferentLanguagesWithSameWorkflowName, null, false);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release using the repository "dockstore-testing/test-workflows-and-tools" which registers 1 unpublished tool and 1 published workflow
        final String tag10 = "refs/tags/1.0";
        handleGitHubRelease(workflowsApi, DockstoreTesting.TEST_WORKFLOWS_AND_TOOLS, tag10, USER_2_USERNAME);
        ++numberOfWebhookInvocations;
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTesting, "0", 15);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag10, "", true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUSH, tag10, "md5sum", true);
        assertEntryNameInNewestLambdaEvent(orgEvents, LambdaEvent.TypeEnum.PUBLISH, tag10, "", true);
        assertNumberOfUniqueDeliveryIds(orgEvents, numberOfWebhookInvocations);

        // Release refs/heads/invalidToolName. There is one successful workflow and one failed tool with an invalid name
        final String invalidToolNameBranch = "refs/heads/invalidToolName";
        assertThrows(ApiException.class, () -> handleGitHubRelease(workflowsApi, DockstoreTesting.TEST_WORKFLOWS_AND_TOOLS, invalidToolNameBranch, USER_2_USERNAME));
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
        handleGitHubInstallation(client, List.of(DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML), USER_2_USERNAME);

        // Release 0.1 on GitHub - one new wdl workflow
        handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.1", USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertTrue(workflowCount >= 2, "should see 2 workflows from the .dockstore.yml from the master branch");

        // Ensure that new workflow is created and is what is expected
        Workflow workflow = getFoobar1Workflow(client);
        assertEquals(Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType(), "Should be a WDL workflow");
        assertEquals(Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Should be type DOCKSTORE_YML");
        assertEquals(1, workflow.getWorkflowVersions().stream().filter(v -> v.getName().contains("0.1")).toList().size(), "Should have one version 0.1");
        assertEquals("A repo that includes .dockstore.yml", workflow.getTopicAutomatic());

        // Release 0.2 on GitHub - one existing wdl workflow, one new cwl workflow
        handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.2", USER_2_USERNAME);
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
        handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/heads/master", USER_2_USERNAME);
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
        handleGitHubBranchDeletion(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, USER_2_USERNAME, "refs/tags/0.2");
        workflow = getFoobar1Workflow(client);
        assertTrue(workflow.getWorkflowVersions().stream().noneMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")),
                "Should not have a 0.2 version.");
        workflow2 = getFoobar2Workflow(client);
        assertTrue(workflow2.getWorkflowVersions().stream().noneMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")),
                "Should not have a 0.2 version.");

        // Add version that doesn't exist
        long failedCount = usersApi.getUserGitHubEvents(0, 10).stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count();
        try {
            handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/heads/idonotexist", USER_2_USERNAME);
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
        handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/heads/emptytestparameter", USER_2_USERNAME);
        workflow2 = getFoobar2Workflow(client);
        assertTrue(workflow2.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "emptytestparameter")).findFirst().get().isValid(),
                "Should have emptytestparameter version that is valid");
        testValidationUpdate(client);
        testDefaultVersion(client);
    }

    private Workflow getFoobar1Workflow(WorkflowsApi client, String... includes) {
        if (includes.length == 0) {
            return client.getWorkflowByPath("github.com/" + DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        }
        return client.getWorkflowByPath("github.com/" + DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, String.join(",", includes));
    }

    private Workflow getFoobar2Workflow(WorkflowsApi client, String... includes) {
        if (includes.length == 0) {
            return client.getWorkflowByPath("github.com/" + DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, "versions");
        }
        return client.getWorkflowByPath("github.com/" + DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML + "/foobar2", WorkflowSubClass.BIOWORKFLOW, String.join(",", includes));
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

        handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/heads/master", USER_2_USERNAME);
        workflow2 = getFoobar2Workflow(client);
        masterVersion = workflow2.getWorkflowVersions().stream().filter((WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertTrue(masterVersion.get().isValid(), "Master version should be valid after GitHub App triggered again");
    }

    private void testDefaultVersion(WorkflowsApi client) {
        Workflow workflow2 = getFoobar2Workflow(client);
        assertNull(workflow2.getDefaultVersion());
        Workflow workflow = getFoobar1Workflow(client);
        assertNull(workflow.getDefaultVersion());
        handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.4", USER_2_USERNAME);
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
        String wdlWorkflowRepoPath = String.format("github.com/%s/%s", DockstoreTestUser2.TEST_AUTHORS, "foobar");

        // Workflows containing 1 descriptor author and multiple .dockstore.yml authors.
        // If the .dockstore.yml specifies an author, then only the .dockstore.yml's authors should be saved
        handleGitHubRelease(workflowsApi, DockstoreTestUser2.TEST_AUTHORS, "refs/heads/main", USER_2_USERNAME);
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
            handleGitHubRelease(workflowsApi, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/heads/invalidWorkflowName", USER_2_USERNAME);
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

        handleGitHubRelease(client, DockstoreTesting.TAGGED_APPTOOL, "refs/tags/1.0", USER_2_USERNAME);
        Workflow appTool = client.getWorkflowByPath("github.com/" + DockstoreTesting.TAGGED_APPTOOL_TOOL_PATH, WorkflowSubClass.APPTOOL, "versions,validations");

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

        handleGitHubRelease(workflowsApi, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.4", USER_2_USERNAME);
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
        handleGitHubRelease(workflowsApi, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.4", USER_2_USERNAME);
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

        handleGitHubRelease(workflowsApi, wdlWorkflowRepo, "refs/heads/master", USER_2_USERNAME);
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
            handleGitHubRelease(client, "dockstore-testing/recursive-wdl", "refs/heads/main", USER_2_USERNAME);
            fail("should have thrown");
        } catch (ApiException ex) {
            // Confirm that the release failed and was logged correctly.
            List<LambdaEvent> events = usersApi.getUserGitHubEvents(0, 10);
            assertEquals(1, events.size(), "There should be one event");
            assertEquals(0, events.stream().filter(LambdaEvent::isSuccess).count(), "There should be no successful events");
            assertTrue(events.get(0).getMessage().contains(WDLHandler.ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT), "Event message should indicate the problem");
        }
    }

    @Test
    void testAppToolRSSFeedAndSiteMap() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        final PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);

        // There should be no apptools
        assertEquals(0, appToolDAO.findAllPublishedPaths().size());
        assertEquals(0, appToolDAO.findAllPublishedPathsOrderByDbupdatedate().size());

        // create and publish apptool
        usersApi.syncUserWithGitHub();
        GitHubAppHelper.registerAppTool(webClient);
        handleGitHubRelease(workflowApi, DockstoreTesting.TAGGED_APPTOOL, "refs/tags/1.0", USER_2_USERNAME);
        Workflow appTool = workflowApi.getWorkflowByPath("github.com/" + DockstoreTesting.TAGGED_APPTOOL_TOOL_PATH, WorkflowSubClass.APPTOOL, "versions");
        workflowApi.publish1(appTool.getId(), publishRequest);

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
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        Workflow workflow = workflowApi
                .manualRegister(SourceControl.GITHUB.getFriendlyName(), DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "/Dockstore.wdl",
                        "foobar", DescriptorLanguage.WDL.getShortName(), "/test.json");
        workflowApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.cwl", "",
                DescriptorLanguage.CWL.getShortName(), "/test.json");

        // Refresh should work
        workflow = workflowApi.refresh1(workflow.getId(), false);
        assertEquals(Workflow.ModeEnum.FULL, workflow.getMode(), "Workflow should be FULL mode");
        assertTrue(workflow.getWorkflowVersions().stream().allMatch(WorkflowVersion::isLegacyVersion), "All versions should be legacy");

        // Webhook call should convert workflow to DOCKSTORE_YML
        handleGitHubRelease(workflowApi, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.1", USER_2_USERNAME);
        workflow = workflowApi.getWorkflowByPath("github.com/" + DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML + "/foobar", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Workflow should be DOCKSTORE_YML mode");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> !workflowVersion.isLegacyVersion()), "One version should be not legacy");

        // Refresh should now no longer work
        try {
            workflowApi.refresh1(workflow.getId(), false);
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
        assertTrue(workflows.stream().anyMatch(wf -> Objects.equals(wf.getMode(), Workflow.ModeEnum.DOCKSTORE_YML)), "There should still be a dockstore.yml workflow");
        assertTrue(workflows.stream().anyMatch(wf -> Objects.equals(wf.getMode(), Workflow.ModeEnum.STUB)), "There should be at least one stub workflow");

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
     * This tests deleting a GitHub App workflow's default version
     */
    @Test
    void testDeleteDefaultWorkflowVersion() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        final String filterNoneWorkflowPath = "github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filternone";

        // Add 1.0 tag and set as default version
        handleGitHubRelease(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, "refs/tags/1.0", USER_2_USERNAME);
        Workflow workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filternone", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size(), "should have 1 version");
        assertNull(workflow.getDefaultVersion(), "should have no default version until set");
        workflow = client.updateDefaultVersion1(workflow.getId(), workflow.getWorkflowVersions().get(0).getName());
        assertNotNull(workflow.getDefaultVersion(), "should have a default version after setting");

        // Add 2.0 tag
        handleGitHubRelease(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, "refs/tags/2.0", USER_2_USERNAME);
        workflow = client.getWorkflowByPath(filterNoneWorkflowPath, WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(2, workflow.getWorkflowVersions().size(), "should have 2 versions");

        // Delete 1.0 tag, should reassign 2.0 as the default version
        handleGitHubBranchDeletion(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, USER_2_USERNAME, "refs/tags/1.0");
        workflow = client.getWorkflowByPath(filterNoneWorkflowPath, WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size(), "should have 1 version after deletion");
        assertNotNull(workflow.getDefaultVersion(), "should have reassigned the default version during deletion");

        // Publish workflow
        PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);
        client.publish1(workflow.getId(), publishRequest);
        workflow = client.getWorkflowByPath(filterNoneWorkflowPath, WorkflowSubClass.BIOWORKFLOW, "versions");
        assertTrue(workflow.isIsPublished());

        // Delete 2.0 tag, unset default version
        handleGitHubBranchDeletion(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, USER_2_USERNAME, "refs/tags/2.0");
        workflow = client.getWorkflowByPath(filterNoneWorkflowPath, WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(0, workflow.getWorkflowVersions().size(), "should have 0 versions after deletion");
        assertNull(workflow.getDefaultVersion(), "should have no default version after final version is deleted");
        assertFalse(workflow.isIsPublished(), "should not be published if it has 0 versions");
    }

    /**
     * This tests calling refresh on a workflow with a Dockstore.yml
     */
    @Test
    void testManualRefreshWorkflowWithGitHubApp() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Release 0.1 on GitHub - one new wdl workflow
        handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.1", USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        Workflow workflow = getFoobar1Workflow(client);
        assertEquals("Apache License 2.0", workflow.getLicenseInformation().getLicenseName(), "Should be able to get license after GitHub App register");

        // Ensure that new workflow is created and is what is expected

        assertEquals(Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType(), "Should be a WDL workflow");
        assertEquals(Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Should be type DOCKSTORE_YML");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.1")), "Should have a 0.1 version.");
        boolean hasLegacyVersion = workflow.getWorkflowVersions().stream().anyMatch(WorkflowVersion::isLegacyVersion);
        assertFalse(hasLegacyVersion, "Workflow should not have any legacy refresh versions.");

        // Refresh
        try {
            client.refresh1(workflow.getId(), false);
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
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        try {
            handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.1", "thisisafakeuser");
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
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        handleGitHubRelease(workflowsApi, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/heads/missingPrimaryDescriptor", USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);
        // Ensure that new workflow is created and is what is expected
        Workflow workflow = getFoobar1Workflow(workflowsApi);
        assertEquals(Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType(), "Should be a WDL workflow");
        assertEquals(Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Should be type DOCKSTORE_YML");
        assertEquals(1, workflow.getWorkflowVersions().size(), "Should have one version");
        assertFalse(workflow.getWorkflowVersions().get(0).isValid(), "Should be invalid (wrong language, bad version)");

        workflowsApi.updateDescriptorType(workflow.getId(), DescriptorLanguage.CWL.toString());
        Workflow updatedWorkflowAfterModifyingDescriptorType = workflowsApi.getWorkflow(workflow.getId(), "");
        assertEquals(Workflow.DescriptorTypeEnum.CWL, updatedWorkflowAfterModifyingDescriptorType.getDescriptorType(),
            "The descriptor language should have been changed");
        assertEquals(0, updatedWorkflowAfterModifyingDescriptorType.getWorkflowVersions().size(), "The old versions should have been removed");

        workflowsApi.updateDescriptorType(workflow.getId(), DescriptorLanguage.WDL.toString());
        updatedWorkflowAfterModifyingDescriptorType = workflowsApi.getWorkflow(workflow.getId(), "versions");
        assertEquals(Workflow.DescriptorTypeEnum.WDL, updatedWorkflowAfterModifyingDescriptorType.getDescriptorType(),
            "The descriptor language should have been changed");
        assertEquals(0, updatedWorkflowAfterModifyingDescriptorType.getWorkflowVersions().size(), "The old versions should have been removed");

        // Release 0.1 on GitHub - one new wdl workflow
        handleGitHubRelease(workflowsApi, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.1", USER_2_USERNAME);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new workflow is created and is what is expected
        workflow = getFoobar1Workflow(workflowsApi);
        assertEquals(Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType(), "Should be a WDL workflow");
        assertEquals(Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Should be type DOCKSTORE_YML");
        assertEquals(1, workflow.getWorkflowVersions().size(), "Should have one version 0.1");
        assertTrue(workflow.getWorkflowVersions().get(0).isValid(), "Should be valid");
        try {
            workflowsApi
                    .updateDescriptorType(workflow.getId(), DescriptorLanguage.CWL.toString());
            fail("Should not be able to change the descriptor type of a workflow that has valid versions");
        } catch (ApiException e) {
            assertEquals("Cannot change descriptor type of a valid workflow", e.getMessage());
        }
        PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);
        workflowsApi.publish1(workflow.getId(), publishRequest);
        try {
            workflowsApi.updateDescriptorType(workflow.getId(), DescriptorLanguage.WDL.toString());
            fail("Should also not be able to change the descriptor type of a workflow that is published");
        } catch (ApiException e) {
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
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);

        // Release 0.1 on GitHub - one new wdl workflow
        handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/tags/0.1", USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new workflow is created and is what is expected
        Workflow workflow = getFoobar1Workflow(client);
        assertEquals(DescriptorTypeEnum.WDL, workflow.getDescriptorType(), "Should be a WDL workflow");
        assertEquals(ModeEnum.DOCKSTORE_YML, workflow.getMode(), "Should be type DOCKSTORE_YML");
        assertEquals(1, workflow.getWorkflowVersions().size(), "Should have one version 0.1");
        assertTrue(workflow.getWorkflowVersions().get(0).isValid(), "Should be valid");
        assertNull(getLatestLambdaEventMessage(0, usersApi), "Lambda event message should be empty");

        // Push missingPrimaryDescriptor on GitHub - one existing wdl workflow, missing primary descriptor
        handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/heads/missingPrimaryDescriptor", USER_2_USERNAME);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new version is in the correct state (invalid)
        workflow = getFoobar1Workflow(client, "validations");
        assertNotNull(workflow);
        assertEquals(2, workflow.getWorkflowVersions().size(), "Should have two versions");

        WorkflowVersion missingPrimaryDescriptorVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "missingPrimaryDescriptor")).findFirst().get();
        assertFalse(missingPrimaryDescriptorVersion.isValid(), "Version should be invalid");

        // Check existence of files and validations
        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(missingPrimaryDescriptorVersion.getId());
        assertTrue(sourceFiles.stream().anyMatch(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), DOCKSTORE_YML_PATH)), "Should have .dockstore.yml file");
        assertTrue(sourceFiles.stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/doesnotexist.wdl")).findFirst().isEmpty(),
            "Should not have doesnotexist.wdl file");
        assertFalse(missingPrimaryDescriptorVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_YML)).findFirst().get().isValid(),
            "Should have invalid .dockstore.yml");
        assertFalse(missingPrimaryDescriptorVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_WDL)).findFirst().get().isValid(),
            "Should have invalid doesnotexist.wdl");
        assertTrue(getLatestLambdaEventMessage(0, usersApi).contains("descriptor"), "Refers to missing primary descriptor");

        // Push missingTestParameterFile on GitHub - one existing wdl workflow, missing a test parameter file
        handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/heads/missingTestParameterFile", USER_2_USERNAME);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new version is in the correct state (invalid)
        workflow = getFoobar1Workflow(client, "validations");
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
        assertFalse(missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_YML)).findFirst().get().isValid(),
            "Should have invalid .dockstore.yml");
        assertTrue(missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_WDL)).findFirst().get().isValid(),
            "Should have valid Dockstore2.wdl");
        assertTrue(getLatestLambdaEventMessage(0, usersApi).contains("/idonotexist.json"), "Refers to missing test file");

        // Push unknownProperty on GitHub - one existing wdl workflow, incorrectly spelled testParameterFiles property
        handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/heads/unknownProperty", USER_2_USERNAME);
        workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount);

        // Ensure that new version is in the correct state (valid)
        workflow = getFoobar1Workflow(client, "validations");
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
        assertFalse(missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_YML)).findFirst().get().isValid(),
            "Should have invalid .dockstore.yml");
        assertTrue(missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_WDL)).findFirst().get().isValid(),
            "Should have valid Dockstore2.wdl");
        assertTrue(getLatestLambdaEventMessage(0, usersApi).contains("testParameterFilets"), "Refers to misspelled property");

        // There should be 4 successful lambda events
        List<LambdaEvent> events = usersApi.getUserGitHubEvents(0, 10);
        assertEquals(4, events.stream().filter(LambdaEvent::isSuccess).count(), "There should be 4 successful events");

        final int versionCountBeforeInvalidDockstoreYml = getFoobar1Workflow(client).getWorkflowVersions().size();
        // Push branch with invalid dockstore.yml
        try {
            handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/heads/invalidDockstoreYml", USER_2_USERNAME);
            fail("Should not reach this statement");
        } catch (ApiException ex) {
            List<LambdaEvent> failEvents = usersApi.getUserGitHubEvents(0, 10);
            assertEquals(1, failEvents.stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count(), "There should be 1 unsuccessful event");
            assertEquals(versionCountBeforeInvalidDockstoreYml, getFoobar1Workflow(client).getWorkflowVersions().size(), "Number of versions should be the same");
        }
    }

    private LambdaEvent getLatestLambdaEvent(Integer offset, UsersApi usersApi) {
        return usersApi.getUserGitHubEvents(offset, 1).get(0);
    }

    private String getLatestLambdaEventMessage(Integer offset, UsersApi usersApi) {
        return getLatestLambdaEvent(offset, usersApi).getMessage();
    }

    /**
     * Test that a .dockstore.yml workflow has the expected path for its test parameter file.
     */
    @Test
    void testTestParameterPaths() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        handleGitHubRelease(client, DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML, "refs/heads/master", USER_2_USERNAME);
        Workflow workflow = getFoobar1Workflow(client);
        WorkflowVersion version = workflow.getWorkflowVersions().get(0);
        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(version.getId());
        assertTrue(sourceFiles.stream().anyMatch(sourceFile -> sourceFile.getPath().equals("/dockstore.wdl.json")), "Test file should have the expected path");
    }

    /**
     * This tests the GitHub release with .dockstore.yml located in /.github/.dockstore.yml
     */
    @Test
    void testGithubDirDockstoreYml() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        handleGitHubRelease(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, "refs/tags/1.0", USER_2_USERNAME);
        Workflow workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filternone", WorkflowSubClass.BIOWORKFLOW, "");
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
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // master should be excluded by all of the workflows with filters
        handleGitHubRelease(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, "refs/heads/master", USER_2_USERNAME);
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filterbranch", WorkflowSubClass.BIOWORKFLOW, ""));
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filtertag", WorkflowSubClass.BIOWORKFLOW, ""));
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filtermulti", WorkflowSubClass.BIOWORKFLOW, ""));
        Workflow workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filternone", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size());
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filterregexerror", WorkflowSubClass.BIOWORKFLOW, ""));

        // tag 2.0 should be excluded by all of the workflows with filters
        handleGitHubRelease(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, "refs/tags/2.0", USER_2_USERNAME);
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filterbranch", WorkflowSubClass.BIOWORKFLOW, ""));
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filtertag", WorkflowSubClass.BIOWORKFLOW, ""));
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filtermulti", WorkflowSubClass.BIOWORKFLOW, ""));
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filternone", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(2, workflow.getWorkflowVersions().size());
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filterregexerror", WorkflowSubClass.BIOWORKFLOW, ""));

        // develop2 should be accepted by the heads/dev* filter in filtermulti
        handleGitHubRelease(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, "refs/heads/develop2", USER_2_USERNAME);
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filterbranch", WorkflowSubClass.BIOWORKFLOW, ""));
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filtertag", WorkflowSubClass.BIOWORKFLOW, ""));
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filtermulti", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filternone", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(3, workflow.getWorkflowVersions().size());
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filterregexerror", WorkflowSubClass.BIOWORKFLOW, ""));

        // tag 1.1 should be accepted by the 1.* filter in filtermulti
        handleGitHubRelease(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, "refs/tags/1.1", USER_2_USERNAME);
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filterbranch", WorkflowSubClass.BIOWORKFLOW, ""));
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filtertag", WorkflowSubClass.BIOWORKFLOW, ""));
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filtermulti", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(2, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filternone", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(4, workflow.getWorkflowVersions().size());
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filterregexerror", WorkflowSubClass.BIOWORKFLOW, ""));

        // tag 1.0 should be accepted by tags/1.0 in filtertag and 1.* in filtermulti
        handleGitHubRelease(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, "refs/tags/1.0", USER_2_USERNAME);
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filterbranch", WorkflowSubClass.BIOWORKFLOW, ""));
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filtertag", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filtermulti", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(3, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filternone", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(5, workflow.getWorkflowVersions().size());
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filterregexerror", WorkflowSubClass.BIOWORKFLOW, ""));

        // develop should be accepted by develop in filterbranch and heads/dev* in filtermulti
        handleGitHubRelease(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, "refs/heads/develop", USER_2_USERNAME);
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filterbranch", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filtertag", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(1, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filtermulti", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(4, workflow.getWorkflowVersions().size());
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filternone", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertEquals(6, workflow.getWorkflowVersions().size());
        assertThrows(ApiException.class, () -> client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filterregexerror", WorkflowSubClass.BIOWORKFLOW, ""));
    }

    /**
     * This tests publishing functionality in .dockstore.yml
     */
    @Test
    void testDockstoreYmlPublish() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        handleGitHubRelease(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, "refs/heads/publish", USER_2_USERNAME);
        Workflow workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filternone", WorkflowSubClass.BIOWORKFLOW, "");
        assertEquals(1, testingPostgres.getPublishEventCountForWorkflow(workflow.getId()));
        assertEquals(0, testingPostgres.getUnpublishEventCountForWorkflow(workflow.getId()));
        assertTrue(workflow.isIsPublished());
        handleGitHubRelease(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, "refs/heads/unpublish", USER_2_USERNAME);
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST + "/filternone", WorkflowSubClass.BIOWORKFLOW, "");
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
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        String wdlWorkflowRepoPath = String.format("github.com/%s/%s", DockstoreTestUser2.TEST_AUTHORS, "foobar");
        String cwlWorkflowRepoPath = String.format("github.com/%s/%s", DockstoreTestUser2.TEST_AUTHORS, "foobar2");
        String nextflowWorkflowRepoPath = String.format("github.com/%s/%s", DockstoreTestUser2.TEST_AUTHORS, "foobar3");
        String wdl2WorkflowRepoPath = String.format("github.com/%s/%s", DockstoreTestUser2.TEST_AUTHORS, "foobar4");
        Workflow workflow;
        WorkflowVersion version;

        // Workflows containing 1 descriptor author and multiple .dockstore.yml authors.
        // If the .dockstore.yml specifies an author, then only the .dockstore.yml's authors should be saved
        handleGitHubRelease(client, DockstoreTestUser2.TEST_AUTHORS, "refs/heads/main", USER_2_USERNAME);
        // WDL workflow
        workflow = client.getWorkflowByPath(wdlWorkflowRepoPath, WorkflowSubClass.BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(2, version.getAuthors().size());
        assertEquals(2, version.getOrcidAuthors().size());
        final String wdlDescriptorAuthorName = "Descriptor Author";
        assertTrue(version.getAuthors().stream().noneMatch(author -> author.getName().equals(wdlDescriptorAuthorName)), "Should not have any author from the descriptor");
        // CWL workflow
        workflow = client.getWorkflowByPath(cwlWorkflowRepoPath, WorkflowSubClass.BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(1, version.getAuthors().size());
        assertEquals(1, version.getOrcidAuthors().size());
        final String cwlDescriptorAuthorName = "Test User";
        assertTrue(version.getAuthors().stream().noneMatch(author -> author.getName().equals(cwlDescriptorAuthorName)), "Should not have any author from the descriptor");
        // Nextflow workflow
        workflow = client.getWorkflowByPath(nextflowWorkflowRepoPath, WorkflowSubClass.BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(1, version.getAuthors().size());
        assertEquals(1, version.getOrcidAuthors().size());
        final String nextflowDescriptorAuthorName = "Nextflow Test Author";
        assertTrue(version.getAuthors().stream().noneMatch(author -> author.getName().equals(nextflowDescriptorAuthorName)), "Should not have any author from the descriptor");
        // WDL workflow containing 1 descriptor author, 1 ORCID author, and 0 non-ORCID authors
        workflow = client.getWorkflowByPath(wdl2WorkflowRepoPath, WorkflowSubClass.BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(0, version.getAuthors().size());
        assertEquals(1, version.getOrcidAuthors().size());
        assertTrue(version.getAuthors().stream().noneMatch(author -> author.getName().equals(wdlDescriptorAuthorName)), "Should not have any author from the descriptor");

        // WDL workflow containing only .dockstore.yml authors
        handleGitHubRelease(client, DockstoreTestUser2.TEST_AUTHORS, "refs/heads/onlyDockstoreYmlAuthors", USER_2_USERNAME);
        workflow = client.getWorkflowByPath(wdlWorkflowRepoPath, WorkflowSubClass.BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("onlyDockstoreYmlAuthors")).findFirst().get();
        assertEquals(2, version.getAuthors().size());
        assertEquals(2, version.getOrcidAuthors().size());

        // WDL workflow containing only a descriptor author
        handleGitHubRelease(client, DockstoreTestUser2.TEST_AUTHORS, "refs/heads/onlyDescriptorAuthor", USER_2_USERNAME);
        workflow = client.getWorkflowByPath(wdlWorkflowRepoPath, WorkflowSubClass.BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("onlyDescriptorAuthor")).findFirst().get();
        assertEquals(1, version.getAuthors().size());
        assertEquals(wdlDescriptorAuthorName, version.getAuthor());
        assertEquals(0, version.getOrcidAuthors().size());

        // Release WDL workflow containing only a descriptor author again and test that it doesn't create a duplicate author
        handleGitHubRelease(client, DockstoreTestUser2.TEST_AUTHORS, "refs/heads/onlyDescriptorAuthor", USER_2_USERNAME);
        workflow = client.getWorkflowByPath(wdlWorkflowRepoPath, WorkflowSubClass.BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("onlyDescriptorAuthor")).findFirst().get();
        assertEquals(1, version.getAuthors().size());
        assertEquals(wdlDescriptorAuthorName, version.getAuthor());
        assertEquals(0, version.getOrcidAuthors().size());

        // WDL workflow containing multiple descriptor authors separated by a comma ("Author 1, Author 2") and no .dockstore.yml authors
        handleGitHubRelease(client, DockstoreTestUser2.TEST_AUTHORS, "refs/heads/multipleDescriptorAuthors", USER_2_USERNAME);
        workflow = client.getWorkflowByPath(wdlWorkflowRepoPath, WorkflowSubClass.BIOWORKFLOW, "versions,authors");
        version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("multipleDescriptorAuthors")).findFirst().get();
        assertEquals(2, version.getAuthors().size());
        version.getAuthors().forEach(author -> assertNotNull(author.getEmail()));
        assertEquals(0, version.getOrcidAuthors().size());
    }

    // .dockstore.yml in test repo needs to change to add a 'name' field to one of them. Should also include another branch that doesn't keep the name field
    @Test
    void testTools() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(webClient);
        WorkflowsApi client = new WorkflowsApi(webClient);

        handleGitHubRelease(client, DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, "refs/heads/main", USER_2_USERNAME);
        Workflow appTool = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS_TOOL_PATH, WorkflowSubClass.APPTOOL, "versions");
        Workflow workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, WorkflowSubClass.BIOWORKFLOW, "versions");

        assertNotNull(workflow);
        assertNotNull(appTool);

        assertEquals(1, appTool.getWorkflowVersions().size());
        assertEquals(1, workflow.getWorkflowVersions().size());

        Long userId = usersApi.getUser().getId();
        List<io.dockstore.openapi.client.model.Workflow> usersAppTools = usersApi.userAppTools(userId);
        assertEquals(1, usersAppTools.size());

        handleGitHubRelease(client, DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, "refs/heads/invalid-workflow", USER_2_USERNAME);
        appTool = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS_TOOL_PATH, WorkflowSubClass.APPTOOL, "versions,validations");
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, WorkflowSubClass.BIOWORKFLOW, "versions,validations");
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

        handleGitHubRelease(client, DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, "refs/heads/invalidTool", USER_2_USERNAME);
        appTool = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS_TOOL_PATH, WorkflowSubClass.APPTOOL, "versions,validations");
        workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, WorkflowSubClass.BIOWORKFLOW, "versions,validations");
        assertEquals(3, appTool.getWorkflowVersions().size());
        assertEquals(3, workflow.getWorkflowVersions().size());

        invalidVersion = appTool.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.isValid()).findFirst().get();
        Validation toolValidation = invalidVersion.getValidations().stream().filter(validation -> validation.getType().equals(Validation.TypeEnum.DOCKSTORE_CWL)).findFirst().get();
        assertFalse(toolValidation.isValid());
        assertTrue(toolValidation.getMessage().contains("Did you mean to register a workflow"));

        // publish endpoint updates elasticsearch index
        PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);
        WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        client.publish1(appTool.getId(), publishRequest);
        client.publish1(workflow.getId(), publishRequest);
        assertFalse(systemOut.getText().contains("Could not submit index to elastic search"));

        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(webClient);
        final List<io.dockstore.openapi.client.model.Tool> tools = ga4Ghv20Api.toolsGet(null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(2, tools.size());

        final io.dockstore.openapi.client.model.Tool tool = ga4Ghv20Api.toolsIdGet("github.com/" + DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS_TOOL_PATH);
        assertNotNull(tool);
        assertEquals("CommandLineTool", tool.getToolclass().getDescription());
        final Tool trsWorkflow = ga4Ghv20Api.toolsIdGet(EntryTypeMetadata.WORKFLOW.getTrsPrefix() + "/github.com/" + DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS);
        assertNotNull(trsWorkflow);
        assertEquals("Workflow", trsWorkflow.getToolclass().getDescription());

        publishRequest.setPublish(false);
        client.publish1(appTool.getId(), publishRequest);
        client.publish1(workflow.getId(), publishRequest);
        assertFalse(systemOut.getText().contains("Could not submit index to elastic search"));
    }

    @Test
    void testSnapshotAppTool() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        handleGitHubRelease(client, DockstoreTesting.TAGGED_APPTOOL, "refs/tags/1.0", USER_2_USERNAME);
        Workflow appTool = client.getWorkflowByPath("github.com/" + DockstoreTesting.TAGGED_APPTOOL_TOOL_PATH, WorkflowSubClass.APPTOOL, "versions,validations");

        PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);
        WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        client.publish1(appTool.getId(), publishRequest);

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
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        handleGitHubRelease(client, DockstoreTesting.TAGGED_APPTOOL, "refs/tags/1.0", USER_2_USERNAME);
        Workflow appTool = client.getWorkflowByPath("github.com/" + DockstoreTesting.TAGGED_APPTOOL_TOOL_PATH, WorkflowSubClass.APPTOOL, "versions,validations");

        PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);
        client.publish1(appTool.getId(), publishRequest);

        String newTopic = "this is a new topic";
        appTool.setTopicManual(newTopic);
        appTool = client.updateWorkflow(appTool.getId(), appTool);
        assertEquals(newTopic, appTool.getTopicManual());
    }

    @Test
    void testStarAppTool() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(webClient);
        WorkflowsApi client = new WorkflowsApi(webClient);

        handleGitHubRelease(client, DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, "refs/heads/main", USER_2_USERNAME);
        Workflow appTool = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS_TOOL_PATH, WorkflowSubClass.APPTOOL, "versions,validations");

        PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);
        WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        client.publish1(appTool.getId(), publishRequest);

        List<io.dockstore.openapi.client.model.Entry> pre = usersApi.getStarredTools();
        assertEquals(0, pre.stream().filter(e -> e.getId().equals(appTool.getId())).count());
        assertEquals(0, client.getStarredUsers1(appTool.getId()).size());

        client.starEntry1(appTool.getId(), new StarRequest().star(true));

        List<Entry> post = usersApi.getStarredTools();
        assertEquals(1, post.stream().filter(e -> e.getId().equals(appTool.getId())).count());
        assertEquals(pre.size() + 1, post.size());
        assertEquals(1, client.getStarredUsers1(appTool.getId()).size());
    }

    @Test
    void testTRSWithAppTools() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        handleGitHubRelease(client, DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, "refs/heads/main", USER_2_USERNAME);
        handleGitHubRelease(client, DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, "refs/heads/invalid-workflow", USER_2_USERNAME);
        handleGitHubRelease(client, DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, "refs/heads/invalidTool", USER_2_USERNAME);
        Workflow appTool = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS_TOOL_PATH, WorkflowSubClass.APPTOOL, "versions,validations");
        Workflow workflow = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, WorkflowSubClass.BIOWORKFLOW, "versions,validations");        // publish endpoint updates elasticsearch index
        PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);
        WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        client.publish1(appTool.getId(), publishRequest);
        client.publish1(workflow.getId(), publishRequest);


        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(webClient);
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
        tools = ga4Ghv20Api.toolsGet(null, null, NOTEBOOK, null, null, null, null, null, null, null, false, null, null);
        // no notebooks
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
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        try {
            handleGitHubRelease(client, DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, "refs/heads/duplicate-paths", USER_2_USERNAME);
            fail("Should not be able to create a workflow and apptool with the same path.");
        } catch (ApiException ex) {
            assertTrue(ex.getMessage().contains("have no name"));
        }

        // Check that the database trigger created an entry in fullworkflowpath table
        long pathCount = testingPostgres.runSelectStatement("select count(*) from fullworkflowpath", long.class);
        assertEquals(0, pathCount);
        handleGitHubRelease(client, DockstoreTestUser2.DOCKSTOREYML_GITHUB_FILTERS_TEST, "refs/tags/1.0", USER_2_USERNAME);
        pathCount = testingPostgres.runSelectStatement("select count(*) from fullworkflowpath", long.class);
        assertTrue(pathCount >= 3);

        try {
            testingPostgres.runUpdateStatement("INSERT INTO fullworkflowpath(id, organization, repository, sourcecontrol, workflowname) VALUES (1010, 'DockstoreTestUser2', 'dockstoreyml-github-filters-test', 'github.com', 'filternone')");
            fail("Database should prevent duplicate paths between tables");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("duplicate key value violates"));
        }
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

    private long countVersions() {
        return countTableRows("workflowversion");
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
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        handleGitHubRelease(client, DockstoreTesting.MULTI_ENTRY, "refs/heads/master", USER_2_USERNAME);
        assertEquals(2, countWorkflows());
        assertEquals(2, countTools());
    }

    @Test
    void testMultiEntryOneBroken() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // test one broken tool
        shouldThrowLambdaError(() -> handleGitHubRelease(client, DockstoreTesting.MULTI_ENTRY, "refs/heads/broken-tool", USER_2_USERNAME));
        assertEquals(2, countWorkflows());
        assertEquals(1, countTools());

        // test one broken workflow
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        shouldThrowLambdaError(() -> handleGitHubRelease(client, DockstoreTesting.MULTI_ENTRY, "refs/heads/broken-workflow", USER_2_USERNAME));
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
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // test tool-tool name collision
        shouldThrowLambdaError(() -> handleGitHubRelease(client, DockstoreTesting.MULTI_ENTRY, "refs/heads/same-name-tool-tool", USER_2_USERNAME));
        confirmNoEntries();

        // test workflow-workflow name collision
        shouldThrowLambdaError(() -> handleGitHubRelease(client, DockstoreTesting.MULTI_ENTRY, "refs/heads/same-name-workflow-workflow", USER_2_USERNAME));
        confirmNoEntries();

        // test tool-workflow name collision
        shouldThrowLambdaError(() -> handleGitHubRelease(client, DockstoreTesting.MULTI_ENTRY, "refs/heads/same-name-tool-workflow", USER_2_USERNAME));
        confirmNoEntries();

        // test no names
        shouldThrowLambdaError(() -> handleGitHubRelease(client, DockstoreTesting.MULTI_ENTRY, "refs/heads/no-names", USER_2_USERNAME));
        confirmNoEntries();

        // test service and unnamed workflows
        shouldThrowLambdaError(() -> handleGitHubRelease(client, DockstoreTesting.MULTI_ENTRY, "refs/heads/service-and-unnamed-workflow", USER_2_USERNAME));
        confirmNoEntries();

        // test same name notebook-workflow collision
        shouldThrowLambdaError(() -> handleGitHubRelease(client, DockstoreTesting.MULTI_ENTRY, "refs/heads/same-name-notebook-workflow", USER_2_USERNAME));
        confirmNoEntries();

        // test no name notebook-workflow collision
        shouldThrowLambdaError(() -> handleGitHubRelease(client, DockstoreTesting.MULTI_ENTRY, "refs/heads/no-name-notebook-workflow", USER_2_USERNAME));
        confirmNoEntries();
    }

    /**
     * Test that the push will fail if the .dockstore.yml contains a
     * relative primary descriptor path, and/or relative test descriptor paths.
     */
    @Test
    void testMultiEntryRelativePrimaryDescriptorPath() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(webClient);
        WorkflowsApi client = new WorkflowsApi(webClient);

        ApiException ex = shouldThrowLambdaError(() -> handleGitHubRelease(client, DockstoreTesting.MULTI_ENTRY, "refs/heads/relative-primary-descriptor-path", USER_2_USERNAME));
        assertTrue(ex.getMessage().toLowerCase().contains("could not be processed"));
        assertEquals(0, countWorkflows());
        assertEquals(0, countTools());
        List<LambdaEvent> failedLambdaEvents = usersApi.getUserGitHubEvents(0, 10).stream()
                .filter(event -> !event.isSuccess())
                .toList();
        assertEquals(4, failedLambdaEvents.size(), "There should be four failed events");
        failedLambdaEvents.forEach(event -> assertTrue(event.getMessage().toLowerCase().contains("absolute"), "Should contain the word 'absolute'"));
    }

    /**
     * This tests just the github release process for installs
     */
    @Test
    void testGitHubReleaseInstallationOnly() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Track install event
        handleGitHubInstallation(client, List.of(DockstoreTestUser2.WORKFLOW_DOCKSTORE_YML), USER_2_USERNAME);
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
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Track install event
        handleGitHubInstallation(client, List.of(DockstoreTesting.RODENT_OF_UNUSUAL_SIZE), USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertTrue(workflowCount >= 12, "should see a lot of workflows from the .dockstore.yml from the master branch from some branch or another");
    }

    /**
     * This tests just the github release process for installs but the .dockstore.yml is in a develop branch
     * and missing from the default branch
     */
    @Test
    void testGitHubReleaseFeatureBranchInstallationOnly() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Track install event
        handleGitHubInstallation(client, List.of(DockstoreTestUser2.DOCKSTORE_WORKFLOW_CNV), USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertEquals(1, workflowCount, "should see a workflow from the .dockstore.yml");
        long workflowVersionCount = testingPostgres.runSelectStatement("select count(*) from workflowversion where reference like 'develop'", long.class);
        assertEquals(1, workflowVersionCount, "should see a workflow from the .dockstore.yml from a specific branch");
    }

    /**
     * Tests that the "ref inspection" feature of the GitHub App release processing is working properly.
     * That is, ignore the release if the event's "after" commit SHA does not match the ref's current head commit SHA,
     * and process the release otherwise.
     */
    @Test
    void testRefInspectionRelease() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        String repo = "dockstore-testing/simple-notebook";
        assertEquals(0, countVersions());
        // Release from various tags
        // Non-existent tag, should be ignored
        handleGitHubRelease(client, repo, "refs/tags/bogus", USER_2_USERNAME, "adc83b19e793491b1c6ea0fd8b46cd9f32e592fc");
        assertEquals(0, countVersions());
        // Existing tag with incorrect "after" SHA, should be ignored
        handleGitHubRelease(client, repo, "refs/tags/simple-v1", USER_2_USERNAME, "adc83b19e793491b1c6ea0fd8b46cd9f32e592fc");
        assertEquals(0, countVersions());
        // Existing tag with correct "after" SHA, should succeed
        handleGitHubRelease(client, repo, "refs/tags/simple-v1", USER_2_USERNAME, "ebca52b72a5c9f9d33543648aacb10a6bc736677");
        assertEquals(1, countVersions());
        // Existing tag with no "after" SHA supplied, should succeed
        handleGitHubRelease(client, repo, "refs/tags/simple-published-v1", USER_2_USERNAME);
        assertEquals(2, countVersions());
    }

    /**
     * Tests that the "ref inspection" feature of the GitHub App delete processing is working properly.
     * That is, ignore the delete if the ref corresponding to the event currently exists, and process the delete otherwise.
     */
    @Test
    void testRefInspectionDelete() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        String existingRepo = "dockstore-testing/simple-notebook";
        String nonexistentRepo = "nonexistent-potatoey-repo/a-branch";
        String nonexistentRef = "refs/tags/foo";
        // Add versions corresponding to a nonexistent repo and ref
        addNotebookAndVersion("dockstore-testing", "simple-notebook", "foo");
        addNotebookAndVersion("nonexistent-potatoey-repo", "a-branch", "foo");
        long versionCount = countVersions();
        // Delete a version corresponding to a nonexistent repo, should succeed
        handleGitHubBranchDeletion(client, nonexistentRepo, USER_2_USERNAME, nonexistentRef, false);
        assertEquals(versionCount - 1, countVersions());
        // Delete a version corresponding to a nonexistent ref, should succeed
        handleGitHubBranchDeletion(client, existingRepo, USER_2_USERNAME, nonexistentRef, false);
        assertEquals(versionCount - 2, countVersions());
        // Attempt to delete a version corresponding to an existing tag, should be ignored
        handleGitHubBranchDeletion(client, existingRepo, USER_2_USERNAME, "refs/tags/simple-v1", false);
        assertEquals(versionCount - 2, countVersions());
        // Attempt to delete a version corresponding to an existing branch, should be ignored
        handleGitHubBranchDeletion(client, existingRepo, USER_2_USERNAME, "refs/heads/main", false);
        assertEquals(versionCount - 2, countVersions());
    }

    private void addNotebookAndVersion(String organization, String repo, String ref) {
        Transaction transaction = session.beginTransaction();

        io.dockstore.webservice.core.Notebook notebook = new io.dockstore.webservice.core.Notebook();
        notebook.setOrganization(organization);
        notebook.setRepository(repo);
        notebook.setWorkflowName("test_name");
        notebook.setSourceControl(SourceControl.GITHUB);
        notebook.setDescriptorType(DescriptorLanguage.JUPYTER);
        notebook.setDescriptorTypeSubclass(DescriptorLanguageSubclass.PYTHON);
        notebook.setMode(io.dockstore.webservice.core.WorkflowMode.DOCKSTORE_YML);
        notebookDAO.create(notebook);

        io.dockstore.webservice.core.WorkflowVersion version = new io.dockstore.webservice.core.WorkflowVersion();
        version.setName(ref);
        version.setReference(ref);
        version.setReferenceType(io.dockstore.webservice.core.Version.ReferenceType.TAG);
        version.setWorkflowPath(String.format("github.com/%s/%s", organization, ref));
        version.setParent(notebook);
        workflowVersionDAO.create(version);

        transaction.commit();
    }
}
