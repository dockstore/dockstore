package io.dockstore.client.cli;

import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubRelease;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.RepositoryConstants.DockstoreTestUser2;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * A collection of tests for the version validation system
 *
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class OpenValidationIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }


    /**
     * this method will set up the webservice and return the workflows api
     *
     * @return WorkflowsApi
     * @throws ApiException
     */
    private WorkflowsApi setupWorkflowWebService() throws ApiException {
        ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        return new WorkflowsApi(client);
    }

    /**
     * Tests that we properly validate WDL workflows
     * Requires GitHub Repo DockstoreTestUser2/TestEntryValidation, master branch
     */
    @Test
    void testWdl11Workflow() {
        // Setup webservice and get workflows api
        WorkflowsApi workflowsApi = setupWorkflowWebService();

        // Register a workflow
        handleGitHubRelease(workflowsApi, DockstoreTestUser2.TEST_WDL11_WORKFLOW, "refs/heads/master", USER_2_USERNAME);
        Workflow workflow = workflowsApi.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_WDL11_WORKFLOW, WorkflowSubClass.BIOWORKFLOW, "versions,validations");
        assertNotNull(workflow);
        final PublishRequest publishRequest = new PublishRequest();
        publishRequest.publish(true);
        workflow = workflowsApi.publish1(workflow.getId(), publishRequest);
        // should test as invalid, but publishable
        assertTrue(workflow.isIsPublished());
    }
}
