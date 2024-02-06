package io.dockstore.webservice.core;

import jakarta.persistence.AttributeConverter;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sha256Converter implements AttributeConverter<List<Checksum>, String> {

    private static final Logger LOG = LoggerFactory.getLogger(Sha256Converter.class);

    @Override
    public String convertToDatabaseColumn(final List<Checksum> attribute) {
        // while technically, this should never be called since we should never be re-writing sha256
        // nonetheless, hibernate seems to stage/simulate deletions even on select queries leading to this being called
        LOG.debug("sha256 column is read-only, should not be writing to the database");
        return null;
    }

    @Override
    public List<Checksum> convertToEntityAttribute(final String digest) {
        if (digest == null) {
            return Collections.emptyList();
        }
        return List.of(new Checksum(SourceFile.SHA_TYPE, removeEncodingFromDigest(digest)));
    }

    /**
     * An example digest from PG is \x24ea9b890cc4fe30b061f3c585c8988fccb95157 -- remove the \x
     *
     * Not sure we should do this, but it is backwards compatible with how we've been doing digests.
     * @param sha
     * @return
     */
    private String removeEncodingFromDigest(final String sha) {
        if (sha.startsWith("\\x")) {
            return sha.substring(2);
        }
        return sha;
    }

}
