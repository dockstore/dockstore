/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
