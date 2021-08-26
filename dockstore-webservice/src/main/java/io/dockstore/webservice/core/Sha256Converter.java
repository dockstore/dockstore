package io.dockstore.webservice.core;

import java.util.Collections;
import java.util.List;
import javax.persistence.AttributeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sha256Converter implements AttributeConverter<List<Checksum>, String> {

    private static final Logger LOG = LoggerFactory.getLogger(Sha256Converter.class);

    @Override
    public String convertToDatabaseColumn(final List<Checksum> attribute) {
        LOG.error("sha256 column is read-only, should not be writing to the database");
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
