package io.swagger.model;

import io.dockstore.webservice.core.Checksum;
import io.dockstore.webservice.core.ChecksumConverter;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class ChecksumConverterTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();


    @Test
    public void checksumConverter() {
        final ChecksumConverter cs = new ChecksumConverter();
        final String[] stringTypes = {"sha256", "sha512", "md5"};
        final String[] stringChecksums = {"fakeSHA256Checksum", "fakeSHA512Checksum", "fakeMD5Checksum"};
        final String validStringChecksums = "sha256:fakeSHA256Checksum,sha512:fakeSHA512Checksum,md5:fakeMD5Checksum";

        // Can hand empty list
        List<Checksum> listChecksums = new ArrayList<>();
        Assert.assertNull(cs.convertToDatabaseColumn(listChecksums));

        // Can handle multiple checksums
        for (int i = 0; i < stringTypes.length; i++) {
            listChecksums.add(new Checksum(stringTypes[i], stringChecksums[i]));
        }
        Assert.assertEquals(validStringChecksums, cs.convertToDatabaseColumn(listChecksums));

        // Can handle spaces
        List<Checksum> listChecksumsSpaces = new ArrayList<>();
        for (int i = 0; i < stringTypes.length; i++) {
            listChecksumsSpaces.add(new Checksum(stringTypes[i] + " ", "   " + stringChecksums[i]));
        }
        Assert.assertEquals(validStringChecksums, cs.convertToDatabaseColumn(listChecksumsSpaces));

        String stringChecksumSpaces = stringTypes[0] + "  :   " + stringChecksums[0] + "    ";
        Assert.assertEquals(stringTypes[0] + ":" + stringChecksums[0], cs.convertToEntityAttribute(stringChecksumSpaces).get(0).getType() + ":" + cs.convertToEntityAttribute(stringChecksumSpaces).get(0).getChecksum());

        // Can handle invalid formatting
        String invalidStringChecksum = stringTypes[0] + stringChecksums[0];
        String incompleteStringChecksum = stringTypes[0];
        Assert.assertNull(cs.convertToEntityAttribute(invalidStringChecksum));
        Assert.assertNull(cs.convertToEntityAttribute(incompleteStringChecksum));

        Checksum incompleteChecksum = new Checksum();
        incompleteChecksum.setType(stringTypes[0]);
        listChecksums.add(incompleteChecksum);
        Assert.assertNull(cs.convertToDatabaseColumn(listChecksums));

    }
}
