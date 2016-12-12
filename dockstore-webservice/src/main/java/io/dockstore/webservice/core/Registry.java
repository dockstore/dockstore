/*
 *    Copyright 2016 OICR
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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * This enumerates the types of docker registry that we can associate an entry with.
 * 
 * @author dyuen
 */
@ApiModel(description = "This enumerates the types of docker registry that we can associate an entry with. ")
public enum Registry {
    // Add new registries here
    QUAY_IO("quay.io", "Quay.io", "https://quay.io/repository/"), DOCKER_HUB("registry.hub.docker.com", "Docker Hub", "https://hub.docker.com/"), GITLAB("registry.gitlab.com", "Gitlab", "empty");

    /**
     * this name is what is actually used in commands like docker pull
     */
    @ApiModelProperty(value = "A name used for uniquely identifying the registry", required = true)
    private final String dockerCommand;

    /**
     * this name is what is displayed to users to name the registry
     */
    @ApiModelProperty(value = "A friendly name which can be used when web browsing", required = true)
    private final String friendlyName;

    /**
     * this url is what is used for creating links to the registry website
     */
    @ApiModelProperty(value = "A url used for creating links to the registry site", required = true)
    private final String url;

    Registry(final String dockerCommand, final String friendlyName, final String url) {
        this.friendlyName = friendlyName;
        this.dockerCommand = dockerCommand;
        this.url = url;
    }

    @Override
    public String toString() {
        return dockerCommand;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public String getUrl() {
        return url;
    }
}
