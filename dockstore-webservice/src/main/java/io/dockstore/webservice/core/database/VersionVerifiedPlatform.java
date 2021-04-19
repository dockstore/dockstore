package io.dockstore.webservice.core.database;

/**
 * This class is a subset of fields from sourcefile and verificationinformation. States what platform (source) a version has been verified on
 * and who performed the verification (metadata).
 * @author natalieperez
 * @since 1.10.0
 */
public class VersionVerifiedPlatform {

    private final Long versionId;
    private final String metadata;
    private final String source;
    private final String platformVersion;
    private final String path;
    private final boolean verified;

    public VersionVerifiedPlatform(final Long versionId, final String source, final String metadata, final String platformVersion, final String path, final boolean verified) {
        this.versionId = versionId;
        this.source = source;
        this.metadata = metadata;
        this.platformVersion = platformVersion;
        this.path = path;
        this.verified = verified;
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

    public String getPlatformVersion() {
        return platformVersion;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getPath() {
        return path;
    }

}
