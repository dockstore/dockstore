package io.swagger.model;

import java.util.ArrayList;
import java.util.List;

import io.dockstore.webservice.core.Checksum;
import io.dockstore.webservice.core.ChecksumConverter;
import org.junit.Assert;
import org.junit.Test;

public class ChecksumConverterTest {

    @Test
    public void checksumConverter() {
        final ChecksumConverter cs = new ChecksumConverter();
        final String[] stringTypes = {"sha256", "sha512", "md5"};
        final String[] stringChecksums = {"fakeSHA256Checksum", "fakeSHA512Checksum", "fakeMD5Checksum"};
        final String validStringChecksums = "sha256:fakeSHA256Checksum,sha512:fakeSHA512Checksum,md5:fakeMD5Checksum";

        // Can hand empty list
        List<Checksum> listChecksums = new ArrayList<>();
        Assert.assertEquals(null, cs.convertToDatabaseColumn(listChecksums));

        // Can handle multiple checksums
        for(int i = 0; i < stringTypes.length; i++) {
            listChecksums.add(new Checksum(stringTypes[i], stringChecksums[i]));
        }
        Assert.assertEquals(validStringChecksums, cs.convertToDatabaseColumn(listChecksums));

        // Can handle spaces
        List<Checksum> listChecksumsSpaces = new ArrayList<>();
        for(int i = 0; i < stringTypes.length; i++) {
            listChecksumsSpaces.add(new Checksum(stringTypes[i] + " ", "   " + stringChecksums[i]));
        }
        Assert.assertEquals(validStringChecksums, cs.convertToDatabaseColumn(listChecksumsSpaces));

        String stringChecksumSpaces = stringTypes[0] + "  :   " + stringChecksums[0] + "    ";
        Assert.assertEquals(stringTypes[0] + ":" + stringChecksums[0], cs.convertToEntityAttribute(stringChecksumSpaces).get(0).getType() + ":" + cs.convertToEntityAttribute(stringChecksumSpaces).get(0).getChecksum());

        // Can handle invalid formatting
        String invalidStringChecksum = stringTypes[0] + stringChecksums[0];
        String incompleteStringChecksum = stringTypes[0];
        Assert.assertEquals(null, cs.convertToEntityAttribute(invalidStringChecksum));
        Assert.assertEquals(null, cs.convertToEntityAttribute(incompleteStringChecksum));

        Checksum incompleteChecksum = new Checksum();
        incompleteChecksum.setType(stringTypes[0]);
        listChecksums.add(incompleteChecksum);
        Assert.assertEquals(null, cs.convertToDatabaseColumn(listChecksums));

    }
}
