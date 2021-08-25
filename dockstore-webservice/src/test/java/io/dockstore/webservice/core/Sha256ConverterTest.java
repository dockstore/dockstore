package io.dockstore.webservice.core;

import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class Sha256ConverterTest {

    @Test
    public void convertToDatabaseColumn() {
        final Sha256Converter sha256Converter = new Sha256Converter();
        final String fakeDigest = "1234567890";
        final List<Checksum> checksums = sha256Converter.convertToEntityAttribute("\\x" + fakeDigest);
        Assert.assertNull(sha256Converter.convertToDatabaseColumn(checksums));
        Assert.assertNull(sha256Converter.convertToDatabaseColumn(Collections.emptyList()));
    }

    @Test
    public void convertToEntityAttribute() {
        final Sha256Converter sha256Converter = new Sha256Converter();
        Assert.assertEquals(0, sha256Converter.convertToEntityAttribute(null).size());
        final String fakeDigest = "1234567890";
        final List<Checksum> checksums = sha256Converter.convertToEntityAttribute("\\x" + fakeDigest);
        Assert.assertEquals(1, checksums.size());
        final Checksum checksum = checksums.get(0);
        Assert.assertEquals(SourceFile.SHA_TYPE, checksum.getType());
        Assert.assertEquals(fakeDigest, checksum.getChecksum());

        // Shouldn't be any digests in DB without leading \x, but just in case
        final List<Checksum> checksums2 = sha256Converter.convertToEntityAttribute(fakeDigest);
        Assert.assertEquals(1, checksums2.size());
        final Checksum checksum2 = checksums.get(0);
        Assert.assertEquals(SourceFile.SHA_TYPE, checksum2.getType());
        Assert.assertEquals(fakeDigest, checksum2.getChecksum());
    }
}
