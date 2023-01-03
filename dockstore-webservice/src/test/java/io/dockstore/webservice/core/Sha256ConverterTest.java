package io.dockstore.webservice.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class Sha256ConverterTest {

    @Test
    void convertToDatabaseColumn() {
        final Sha256Converter sha256Converter = new Sha256Converter();
        final String fakeDigest = "1234567890";
        final List<Checksum> checksums = sha256Converter.convertToEntityAttribute("\\x" + fakeDigest);
        assertNull(sha256Converter.convertToDatabaseColumn(checksums));
        assertNull(sha256Converter.convertToDatabaseColumn(Collections.emptyList()));
    }

    @Test
    void convertToEntityAttribute() {
        final Sha256Converter sha256Converter = new Sha256Converter();
        assertEquals(0, sha256Converter.convertToEntityAttribute(null).size());
        final String fakeDigest = "1234567890";
        final List<Checksum> checksums = sha256Converter.convertToEntityAttribute("\\x" + fakeDigest);
        assertEquals(1, checksums.size());
        final Checksum checksum = checksums.get(0);
        assertEquals(SourceFile.SHA_TYPE, checksum.getType());
        assertEquals(fakeDigest, checksum.getChecksum());

        // Shouldn't be any digests in DB without leading \x, but just in case
        final List<Checksum> checksums2 = sha256Converter.convertToEntityAttribute(fakeDigest);
        assertEquals(1, checksums2.size());
        final Checksum checksum2 = checksums.get(0);
        assertEquals(SourceFile.SHA_TYPE, checksum2.getType());
        assertEquals(fakeDigest, checksum2.getChecksum());
    }
}
