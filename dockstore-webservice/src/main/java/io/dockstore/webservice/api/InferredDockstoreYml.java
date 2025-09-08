package io.dockstore.webservice.api;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(description = "Response for an inferred .dockstore.yml")
public class InferredDockstoreYml {

    @Schema(description = "The GitHub branch reference used for the inference", requiredMode = RequiredMode.REQUIRED)
    private String gitReference;

    @Schema(description = "The inferred .dockstore.yml", requiredMode = RequiredMode.REQUIRED)
    private String dockstoreYml;

    public InferredDockstoreYml(String gitReference, String dockstoreYml) {
        this.gitReference = gitReference;
        this.dockstoreYml = dockstoreYml;
    }

    public String getGitReference() {
        return gitReference;
    }

    public void setGitReference(String gitReference) {
        this.gitReference = gitReference;
    }

    public String getDockstoreYml() {
        return dockstoreYml;
    }

    public void setDockstoreYml(String dockstoreYml) {
        this.dockstoreYml = dockstoreYml;
    }
}
