package io.dockstore.webservice.helpers;

import static io.dockstore.client.cli.BaseIT.USER_2_USERNAME;
import static io.dockstore.common.FixtureUtility.fixture;

import io.dockstore.common.RepositoryConstants.DockstoreTestUser2;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.WorkflowsApi;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A helper for GitHub App tests
 */
public final class GitHubAppHelper {

    public static final Integer INSTALLATION_ID = 1179416;
    public static final int LAMBDA_ERROR = 418;

    private GitHubAppHelper() {
    }

    public static void registerAppTool(ApiClient webClient) {
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        handleGitHubRelease(workflowApi, DockstoreTestUser2.TEST_WORKFLOW_AND_TOOLS, "refs/heads/main", USER_2_USERNAME);
    }

    /**
     * Performs a GitHub release, using a generated X-GitHub-Delivery header
     * @param workflowsApi
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitRef Full git reference for a GitHub branch/tag. Ex. refs/heads/master or refs/tags/v1.0
     * @param gitHubUsername Username of user on GitHub who triggered action
     */
    public static void handleGitHubRelease(WorkflowsApi workflowsApi, String repository, String gitRef, String gitHubUsername) {
        workflowsApi.handleGitHubRelease(getGitHubWebhookPushPayload(INSTALLATION_ID, repository, gitRef, gitHubUsername), generateXGitHubDelivery());
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

    public static void handleGitHubInstallation(WorkflowsApi workflowsApi, List<String> repositories, String gitHubUsername) {
        workflowsApi.handleGitHubInstallation(getGitHubWebhookInstallationPayload(INSTALLATION_ID, repositories, gitHubUsername), generateXGitHubDelivery());
    }

    /**
     * Returns a string payload of a GitHub webhook installation event with the arguments substituted into the template payload.
     * @param installationId GitHub installation ID
     * @param repositories A list of repositories (ex. dockstore/dockstore-ui2) that installed the GitHub App
     * @param gitHubUsername Username of user on GitHub who triggered action
     * @return
     */
    public static String getGitHubWebhookInstallationPayload(Integer installationId, List<String> repositories, String gitHubUsername) {
        JSONObject installationPayloadJson = new JSONObject(fixture("github-install-event-payload.json"));
        installationPayloadJson.getJSONObject("installation").put("id", installationId);
        installationPayloadJson.getJSONObject("sender").put("login", gitHubUsername);

        JSONArray repositoriesAddedArray = installationPayloadJson.getJSONArray("repositories_added");
        JSONObject repositoryAddedStub = repositoriesAddedArray.getJSONObject(0);
        repositoriesAddedArray.clear();
        for (String repository: repositories) {
            JSONObject repositoryAddedObject = new JSONObject(repositoryAddedStub, JSONObject.getNames(repositoryAddedStub));
            repositoryAddedObject.put("full_name", repository);
            repositoriesAddedArray.put(repositoryAddedObject);
        }

        return installationPayloadJson.toString();
    }

    public static void handleGitHubBranchDeletion(WorkflowsApi workflowsApi, String repository, String gitHubUsername, String gitRef) {
        workflowsApi.handleGitHubBranchDeletion(repository, gitHubUsername, gitRef, generateXGitHubDelivery());
    }

    /**
     * Generates a random GUID to use as the X-GitHub-Delivery header.
     * @return
     */
    public static String generateXGitHubDelivery() {
        return UUID.randomUUID().toString();
    }
}
