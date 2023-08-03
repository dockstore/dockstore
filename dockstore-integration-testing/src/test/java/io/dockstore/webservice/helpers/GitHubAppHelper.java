package io.dockstore.webservice.helpers;

import static io.dockstore.client.cli.BaseIT.USER_2_USERNAME;
import static io.dockstore.common.FixtureUtility.fixture;

import io.swagger.client.ApiClient;
import io.swagger.client.api.WorkflowsApi;
import java.util.UUID;
import org.json.JSONObject;

/**
 * A helper for GitHub App tests
 */
public final class GitHubAppHelper {

    public static final Integer INSTALLATION_ID = 1179416;
    public static final String TOOL_AND_WORKFLOW_REPO = "DockstoreTestUser2/test-workflows-and-tools";

    private GitHubAppHelper() {
    }

    public static void registerAppTool(ApiClient webClient) {
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.handleGitHubRelease(TOOL_AND_WORKFLOW_REPO, USER_2_USERNAME, "refs/heads/main",
            String.valueOf(INSTALLATION_ID));

    }

    /**
     * Performs a GitHub release, using a generated X-GitHub-Delivery header
     * @param workflowsApi
     * @param installationId GitHub installation ID
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitRef Full git reference for a GitHub branch/tag. Ex. refs/heads/master or refs/tags/v1.0
     * @param gitHubUsername Username of user on GitHub who triggered action
     */
    public static void handleGitHubRelease(io.dockstore.openapi.client.api.WorkflowsApi workflowsApi, Integer installationId, String repository, String gitRef, String gitHubUsername) {
        workflowsApi.handleGitHubRelease(getGitHubWebhookPushPayload(installationId, repository, gitRef, gitHubUsername), generateXGitHubDelivery());
    }

    /**
     * Returns a string payload of a GitHub webhook push event with the arguments substituted into the template payload.
     * @param installationId GitHub installation ID
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitRef Full git reference for a GitHub branch/tag. Ex. refs/heads/master or refs/tags/v1.0
     * @param gitHubUsername Username of user on GitHub who triggered action
     * @return
     */
    public static String getGitHubWebhookPushPayload(Integer installationId, String repository, String gitRef, String gitHubUsername) {
        JSONObject pushPayloadJson = new JSONObject(fixture("github-push-event-payload.json"));
        pushPayloadJson.put("ref", gitRef);
        pushPayloadJson.getJSONObject("repository").put("full_name", repository);
        pushPayloadJson.getJSONObject("installation").put("id", installationId);
        pushPayloadJson.getJSONObject("sender").put("login", gitHubUsername);
        return pushPayloadJson.toString();
    }

    /**
     * Generates a random GUID to use as the X-GitHub-Delivery header.
     * @return
     */
    public static String generateXGitHubDelivery() {
        return UUID.randomUUID().toString();
    }
}
