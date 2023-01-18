package io.dockstore.webservice.core.metrics;

public enum ObjectMetadata {
    OWNER("owner"),
    DESCRIPTION("description");

    private final String metadataKey;

    ObjectMetadata(String metadata) {
        this.metadataKey = metadata;
    }

    public String getMetadataKey() {
        return metadataKey;
    }

    @Override
    public String toString() {
        return metadataKey;
    }
}
