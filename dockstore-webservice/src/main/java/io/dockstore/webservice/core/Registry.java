package io.dockstore.webservice.core;

/**
 * @author dyuen
 */
public enum Registry {
    QUAY_IO("quay.io"), DOCKER_HUB("registry.hub.docker.com");

    /**
     * this name is what is actually used in commands like docker pull
     */
    private final String friendlyName;

    Registry(final String friendlyName) {
        this.friendlyName = friendlyName;
    }

    @Override
    public String toString() {
        return friendlyName;
    }
}
