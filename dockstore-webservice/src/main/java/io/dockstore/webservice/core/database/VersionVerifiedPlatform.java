package io.dockstore.webservice.core.database;

/**
 * This record is a subset of fields from sourcefile and verificationinformation. States what platform (source) a version has been verified on and who performed the verification (metadata).
 *
 * @author natalieperez
 * @since 1.10.0
 */
public record VersionVerifiedPlatform(Long versionId, String source, String metadata, String platformVersion, String path, boolean verified) {

}
