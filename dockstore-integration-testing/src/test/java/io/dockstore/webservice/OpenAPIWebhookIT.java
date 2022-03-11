package io.dockstore.webservice;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BasicIT;
import io.dockstore.client.cli.OrganizationIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.openapi.client.model.Organization;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

/**
 * Like WebhookIT but with only openapi classes to avoid having to give fully defined classes everywhere
 */
@Category(ConfidentialTest.class)
public class OpenAPIWebhookIT extends BaseIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private final String installationId = "1179416";
    private final String taggedToolRepo = "dockstore-testing/tagged-apptool";
    private final String taggedToolRepoPath = "dockstore-testing/tagged-apptool/md5sum";


    @Test
    @Ignore("https://ucsc-cgl.atlassian.net/browse/DOCK-1890")
    public void testAppToolCollections() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final io.dockstore.openapi.client.ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi client = new io.dockstore.openapi.client.api.WorkflowsApi(openApiClient);

        client.handleGitHubRelease(taggedToolRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        io.dockstore.openapi.client.model.Workflow appTool = client.getWorkflowByPath("github.com/" + taggedToolRepoPath, WorkflowSubClass.APPTOOL, "versions,validations");

        io.dockstore.openapi.client.model.WorkflowVersion validVersion = appTool.getWorkflowVersions().stream().filter(io.dockstore.openapi.client.model.WorkflowVersion::isValid).findFirst().get();
        testingPostgres.runUpdateStatement("update apptool set actualdefaultversion = " + validVersion.getId() + " where id = " + appTool.getId());
        final io.dockstore.openapi.client.model.PublishRequest publishRequest = new io.dockstore.openapi.client.model.PublishRequest();
        publishRequest.publish(true);
        client.publish1(appTool.getId(), publishRequest);

        // Setup admin. admin: true, curator: false
        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi organizationsApiAdmin = new io.dockstore.openapi.client.api.OrganizationsApi(webClientAdminUser);
        // Create the organization
        Organization registeredOrganization = OrganizationIT.openApiStubOrgObject();

        // Admin approve it
        organizationsApiAdmin.approveOrganization(registeredOrganization.getId());
        // Create a collection
        io.dockstore.openapi.client.model.Collection stubCollection = OrganizationIT.openApiStubCollectionObject();
        stubCollection.setName("hcacollection");

        // Attach collection
        final io.dockstore.openapi.client.model.Collection createdCollection = organizationsApiAdmin.createCollection(stubCollection, registeredOrganization.getId());
        // Add tool to collection
        organizationsApiAdmin.addEntryToCollection(registeredOrganization.getId(), createdCollection.getId(), appTool.getId(), null);

        // uncomment this after DOCK-1890 and delete from WebhookIT
        // Collection collection = organizationsApiAdmin.getCollectionById(registeredOrganization.getId(), createdCollection.getId());
        // assertTrue((collection.getEntries().stream().anyMatch(entry -> Objects.equals(entry.getId(), appTool.getId()))));
    }
}
