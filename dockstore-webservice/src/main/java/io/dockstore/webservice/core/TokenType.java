/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

/**
 * Enumerates the sources for access tokens for the dockstore
 *
 * @author dyuen
 */
@ApiModel(description = "Enumerates the sources for access tokens for the dockstore")
public enum TokenType {
    @JsonProperty("quay.io") QUAY_IO("quay.io", false),
    @JsonProperty("github.com") GITHUB_COM("github.com", true),
    @JsonProperty("dockstore") DOCKSTORE("dockstore", false),
    @JsonProperty("bitbucket.org") BITBUCKET_ORG("bitbucket.org", true),
    @JsonProperty("gitlab.com") GITLAB_COM("gitlab.com", true),
    @JsonProperty("zenodo.org") ZENODO_ORG("zenodo.org", true),
    @JsonProperty("google.com") GOOGLE_COM("google.com", false);
    private final String friendlyName;
    private final boolean sourceControlToken;

    TokenType(String friendlyName, boolean sourceControlToken) {
        this.friendlyName = friendlyName;
        this.sourceControlToken = sourceControlToken;
    }

    @Override
    public String toString() {
        return friendlyName;
    }

    public boolean isSourceControlToken() {
        return sourceControlToken;
    }
}
