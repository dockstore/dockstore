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
import io.dockstore.common.SourceControl;
import io.swagger.annotations.ApiModel;

/**
 * Enumerates the sources for access tokens for the dockstore
 *
 * @author dyuen
 */
@ApiModel(description = "Enumerates the sources for access tokens for the dockstore")
public enum TokenType {
    @JsonProperty("quay.io")
    QUAY_IO("quay.io", null),
    @JsonProperty("github.com")
    GITHUB_COM("github.com", SourceControl.GITHUB),
    @JsonProperty("dockstore")
    DOCKSTORE("dockstore", SourceControl.DOCKSTORE),
    @JsonProperty("bitbucket.org")
    BITBUCKET_ORG("bitbucket.org", SourceControl.BITBUCKET),
    @JsonProperty("gitlab.com")
    GITLAB_COM("gitlab.com", SourceControl.GITLAB),
    @JsonProperty("zenodo.org")
    ZENODO_ORG("zenodo.org", null),
    @JsonProperty("google.com")
    GOOGLE_COM("google.com", null);
    private final String friendlyName;
    private final SourceControl sourceControl;

    TokenType(String friendlyName, SourceControl sourceControl) {
        this.friendlyName = friendlyName;
        this.sourceControl = sourceControl;
    }

    @Override
    public String toString() {
        return friendlyName;
    }

    public boolean isSourceControlToken() {
        return sourceControl != null;
    }

    public SourceControl getSourceControl() {
        return sourceControl;
    }
}
