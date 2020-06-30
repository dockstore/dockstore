package io.dockstore.webservice.core.database;

/**
 * This class is a subset of fields from sourcefile and verificationinformation. States what platform (source) a version has been verified on
 * and who performed the verification (metadata).
 * @author natalieperez
 * @since 1.10.0
 */
public class VersionVerifiedPlatform {

    final private Long versionId;
    final private String metadata;
    final private String source;

    public VersionVerifiedPlatform(final Long versionId, final String source, final String metadata) {
        this.versionId = versionId;
        this.source = source;
        this.metadata = metadata;
    }

    public long getVersionId() {
        return versionId;
    }

    public String getMetadata() {
        return metadata;
    }

    public String getSource() {
        return source;
    }
}
