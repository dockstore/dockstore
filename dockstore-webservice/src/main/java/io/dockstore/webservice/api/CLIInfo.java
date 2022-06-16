package io.dockstore.webservice.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel("CLIInfo")
public class CLIInfo {

    private String cliLatestDockstoreScriptDownloadUrl;

    private String cliLatestVersion;

    private String cliLatestUnstableVersion;

    @JsonProperty
    public String getCliLatestDockstoreScriptDownloadUrl() {
        return cliLatestDockstoreScriptDownloadUrl;
    }

    @JsonProperty
    public String getCliLatestVersion() {
        return cliLatestVersion;
    }

    @JsonProperty
    public String getCliLatestUnstableVersion() {
        return cliLatestUnstableVersion;
    }

    public void setCliLatestDockstoreScriptDownloadUrl(String cliLatestDockstoreScriptDownloadUrl) {
        this.cliLatestDockstoreScriptDownloadUrl = cliLatestDockstoreScriptDownloadUrl;
    }

    public void setCliLatestVersion(String cliLatestVersion) {
        this.cliLatestVersion = cliLatestVersion;
    }

    public void setCliLatestUnstableVersion(String cliLatestUnstableVersion) {
        this.cliLatestUnstableVersion = cliLatestUnstableVersion;
    }
}
