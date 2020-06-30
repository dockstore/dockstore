package io.dockstore.webservice.core.database;

/**
 * This class is a subset of fields from sourcefile and verificationinformation. States what platform (source) a version has been verified on
 * and who performed the verification (metadata).
 * @author natalieperez
 * @since 1.10.0
 */
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
