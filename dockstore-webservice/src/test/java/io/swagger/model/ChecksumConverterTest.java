package io.swagger.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.dockstore.webservice.core.Checksum;
import io.dockstore.webservice.core.ChecksumConverter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

@ExtendWith(SystemStubsExtension.class)
public class ChecksumConverterTest {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());


    @Test
    public void checksumConverter() {
        final ChecksumConverter cs = new ChecksumConverter();
        final String[] stringTypes = {"sha256", "sha512", "md5"};
        final String[] stringChecksums = {"fakeSHA256Checksum", "fakeSHA512Checksum", "fakeMD5Checksum"};
        final String validStringChecksums = "sha256:fakeSHA256Checksum,sha512:fakeSHA512Checksum,md5:fakeMD5Checksum";

        // Can hand empty list
        List<Checksum> listChecksums = new ArrayList<>();
        assertNull(cs.convertToDatabaseColumn(listChecksums));

        // Can handle multiple checksums
        for (int i = 0; i < stringTypes.length; i++) {
            listChecksums.add(new Checksum(stringTypes[i], stringChecksums[i]));
        }
        assertEquals(validStringChecksums, cs.convertToDatabaseColumn(listChecksums));

        // Can handle spaces
        List<Checksum> listChecksumsSpaces = new ArrayList<>();
        for (int i = 0; i < stringTypes.length; i++) {
            listChecksumsSpaces.add(new Checksum(stringTypes[i] + " ", "   " + stringChecksums[i]));
        }
        assertEquals(validStringChecksums, cs.convertToDatabaseColumn(listChecksumsSpaces));

        String stringChecksumSpaces = stringTypes[0] + "  :   " + stringChecksums[0] + "    ";
        assertEquals(stringTypes[0] + ":" + stringChecksums[0],
            cs.convertToEntityAttribute(stringChecksumSpaces).get(0).getType() + ":" + cs.convertToEntityAttribute(stringChecksumSpaces).get(0).getChecksum());

        // Can handle invalid formatting
        String invalidStringChecksum = stringTypes[0] + stringChecksums[0];
        String incompleteStringChecksum = stringTypes[0];
        assertNull(cs.convertToEntityAttribute(invalidStringChecksum));
        assertNull(cs.convertToEntityAttribute(incompleteStringChecksum));

        Checksum incompleteChecksum = new Checksum();
        incompleteChecksum.setType(stringTypes[0]);
        listChecksums.add(incompleteChecksum);
        assertNull(cs.convertToDatabaseColumn(listChecksums));

    }
}
