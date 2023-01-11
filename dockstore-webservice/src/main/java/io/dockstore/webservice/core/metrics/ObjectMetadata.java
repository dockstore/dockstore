package io.dockstore.webservice.core.metrics;

public enum ObjectMetadata {
    TOOL_ID("tool_id"),
    VERSION_NAME("version_name"),
    PLATFORM("platform"),
    FILENAME("file_nane"),
    OWNER("owner");

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
