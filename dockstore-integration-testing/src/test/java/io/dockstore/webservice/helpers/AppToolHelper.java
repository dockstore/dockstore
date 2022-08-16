package io.dockstore.webservice.helpers;

import io.dockstore.client.cli.BasicIT;
import io.swagger.client.ApiClient;
import io.swagger.client.api.WorkflowsApi;

public final class AppToolHelper {

    public static final String INSTALLATION_ID = "1179416";
    public static final String TOOL_AND_WORKFLOW_REPO = "DockstoreTestUser2/test-workflows-and-tools";

    private AppToolHelper() {
    }

    public static void registerAppTool(ApiClient webClient) {
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.handleGitHubRelease(TOOL_AND_WORKFLOW_REPO, BasicIT.USER_2_USERNAME, "refs/heads/main",
            INSTALLATION_ID);

    }
}
