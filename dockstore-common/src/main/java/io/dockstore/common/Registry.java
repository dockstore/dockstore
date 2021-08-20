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

package io.dockstore.common;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This enumerates the types of docker registry that we can associate an entry with.
 *
 * @author dyuen
 */
public enum Registry {
    // Add new registries here
    QUAY_IO("quay.io", "Quay.io", "https://quay.io/repository/", false, false),
    DOCKER_HUB("registry.hub.docker.com", "Docker Hub", "https://hub.docker.com/", false, false),
    GITLAB("registry.gitlab.com", "GitLab", "https://gitlab.com/", false, false),
    AMAZON_ECR("public.ecr.aws", "Amazon ECR", "https://gallery.ecr.aws/", false, true),
    SEVEN_BRIDGES(null, "Seven Bridges", null, true, true),
    GITHUB_CONTAINER_REGISTRY("ghcr.io", "GitHub Container Registry", "https://ghcr.io/", false, false);

    /**
     * This name is what is actually used in commands like docker pull.
     * For registries that have both a public facing site and private custom docker paths (ex: Amazon ECR), this name contains the public docker path
     */
    private final String dockerPath;

    /**
     * this name is what is displayed to users to name the registry
     */
    private final String friendlyName;

    /**
     * this url is what is used for creating links to the registry website.
     * A value of 'empty' means that there are no ways to directly link to the image
     */
    private final String url;

    /**
     * if set to true, then the registry has no public facing site and is only available through an authorized docker pull (the docker registry path is not set)
     * if set to false, then the registry has a public facing site which can be linked to
     */
    private final boolean privateOnly;

    /**
     * If set to true, then the registry has custom docker paths that are only available through an authorized docker pull.
     * - Note: even if this is set to true, the registry may still have a public facing site and a public docker registry path (ex: Amazon ECR).
     * If set to false, then the registry has a public facing site which can be linked to
     */
    private final boolean customDockerPath;

    Registry(final String dockerPath, final String friendlyName, final String url, final boolean privateOnly, final boolean customDockerPath) {
        this.friendlyName = friendlyName;
        this.dockerPath = dockerPath;
        this.url = url;
        this.privateOnly = privateOnly;
        this.customDockerPath = customDockerPath;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public String getUrl() {
        return url;
    }

    public String getDockerPath() {
        return dockerPath;
    }

    public boolean isPrivateOnly() {
        return privateOnly;
    }

    public boolean hasCustomDockerPath() {
        return customDockerPath;
    }

    /**
     * Expanded version for API list of registries
     */
    public static class RegistryBean {

        @JsonProperty("enum")
        public String value;
        public String dockerPath;
        public String friendlyName;
        public String url;
        public String privateOnly;
        public String customDockerPath;

        public RegistryBean(Registry registry) {
            this.value = registry.name();
            this.dockerPath = registry.getDockerPath();
            this.friendlyName = registry.getFriendlyName();
            this.url = registry.url;
            this.privateOnly = Boolean.toString(registry.isPrivateOnly());
            this.customDockerPath = Boolean.toString(registry.hasCustomDockerPath());
        }
    }
}
