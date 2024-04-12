package io.dockstore.webservice.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Encapsulates information about automatic updates to the entry.
 */
@Schema(description = "Information about automatic updates to the entry")
public class SyncStatus {
    @Schema(description = "False if the GitHub App is conclusively not installed, true otherwise.")
    private boolean gitHubAppInstalled;

    public SyncStatus(boolean gitHubAppInstalled) {
        this.gitHubAppInstalled = gitHubAppInstalled;
    }

    @JsonProperty
    public boolean isGitHubAppInstalled() {
        return gitHubAppInstalled;
    }

    public void setGitHubAppInstalled(boolean gitHubAppInstalled) {
        this.gitHubAppInstalled = gitHubAppInstalled;
    }
}
