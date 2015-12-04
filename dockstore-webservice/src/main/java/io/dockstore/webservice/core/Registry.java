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
    QUAY_IO("quay.io"), DOCKER_HUB("registry.hub.docker.com");

    /**
     * this name is what is actually used in commands like docker pull
     */
    @ApiModelProperty(value = "A friendly name which can be used when web browsing", required = true)
    private final String friendlyName;

    Registry(final String friendlyName) {
        this.friendlyName = friendlyName;
    }

    @Override
    public String toString() {
        return friendlyName;
    }
}
