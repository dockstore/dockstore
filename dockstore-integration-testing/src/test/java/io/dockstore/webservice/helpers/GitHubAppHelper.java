package io.dockstore.webservice.helpers;

import static io.dockstore.client.cli.BaseIT.USER_2_USERNAME;

import io.dockstore.common.RepositoryConstants.DockstoreTestUser2;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Installation;
import io.dockstore.openapi.client.model.InstallationRepositoriesPayload;
import io.dockstore.openapi.client.model.PushPayload;
import io.dockstore.openapi.client.model.Sender;
import io.dockstore.openapi.client.model.WebhookRepository;
import java.util.List;
import java.util.UUID;

/**
 * A helper for GitHub App tests
 */
public final class GitHubAppHelper {

    public static final Long INSTALLATION_ID = 1179416L;
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
     * @param after commit SHA of head commit on reference, or null to force the release
     */
    public static void handleGitHubRelease(WorkflowsApi workflowsApi, String repository, String gitRef, String gitHubUsername, String after) {
        PushPayload pushPayload = new PushPayload().ref(gitRef);
        pushPayload.setRepository(new WebhookRepository().fullName(repository));
        pushPayload.setSender(new Sender().login(gitHubUsername));
        pushPayload.setInstallation(new Installation().id(INSTALLATION_ID));
        pushPayload.setAfter(after);
        workflowsApi.handleGitHubRelease(pushPayload, generateXGitHubDelivery());
    }

    /**
     * Performs a GitHub release, forcing the release to be processed
     */
    public static void handleGitHubRelease(WorkflowsApi workflowsApi, String repository, String gitRef, String gitHubUsername) {
        handleGitHubRelease(workflowsApi, repository, gitRef, gitHubUsername, null);
    }

    public static void handleGitHubInstallation(WorkflowsApi workflowsApi, List<String> repositories, String gitHubUsername) {
        InstallationRepositoriesPayload payload = new InstallationRepositoriesPayload()
                .repositoriesAdded(repositories.stream().map(repo -> new WebhookRepository().fullName(repo)).toList());
        payload.setInstallation(new Installation().id(INSTALLATION_ID));
        payload.setSender(new Sender().login(gitHubUsername));
        workflowsApi.handleGitHubInstallation(payload, generateXGitHubDelivery());
    }

    /**
     * Delete the workflow versions corresponding to a specified GitHub ref
     * @param workflowsApi
     * @param repository repository id (ex: 'dockstore-testing/simple-notebook')
     * @param gitHubUsername user login
     * @param gitRef full reference (ex: 'refs/tags/simple-v1')
     * @param force force the delete to be processed, even if the "ref inspection" scheme would otherwise ignore it
     */
    public static void handleGitHubBranchDeletion(WorkflowsApi workflowsApi, String repository, String gitHubUsername, String gitRef, boolean force) {
        workflowsApi.handleGitHubBranchDeletion(repository, gitHubUsername, gitRef, generateXGitHubDelivery(), force ? null : INSTALLATION_ID);
    }

    /**
     * Delete the workflow versions corresponding to a specified GitHub ref, forcing the delete to be processed
     */
    public static void handleGitHubBranchDeletion(WorkflowsApi workflowsApi, String repository, String gitHubUsername, String gitRef) {
        handleGitHubBranchDeletion(workflowsApi, repository, gitHubUsername, gitRef, true);
    }

    /**
     * Generates a random GUID to use as the X-GitHub-Delivery header.
     * @return
     */
    public static String generateXGitHubDelivery() {
        return UUID.randomUUID().toString();
    }
}
