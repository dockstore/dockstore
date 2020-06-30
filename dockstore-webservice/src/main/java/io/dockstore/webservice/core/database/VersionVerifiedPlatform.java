package io.dockstore.webservice.core.database;

public class VersionVerifiedPlatform {

    private long versionId;
    private String metadata;
    private String source;

    public VersionVerifiedPlatform(long versionId, String source, String metadata) {
        this.versionId = versionId;
        this.source = source;
        this.metadata = metadata;
    }

    public long getVersionId() {
        return versionId;
    }

    public void setVersionId(final long versionId) {
        this.versionId = versionId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(final String metadata) {
        this.metadata = metadata;
    }

    public String getSource() {
        return source;
    }

    public void setSource(final String source) {
        this.source = source;
    }
}
