package io.dockstore.webservice.helpers;

import io.dockstore.webservice.Constants;
import io.dockstore.webservice.core.Checksum;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class FileFormatHelperTest {

    @Test
    public void testCalcDigests() {
        Assert.assertEquals(0, FileFormatHelper.calcDigests(null).size());
        final List<Checksum> checksums = FileFormatHelper.calcDigests("hello world");
        Assert.assertEquals(2, checksums.size());
        final Checksum sha1 = checksums.stream().filter(checksum -> Constants.SHA1_TYPE_FOR_SOURCEFILES.equals(checksum.getType())).findFirst().get();
        Assert.assertNotNull(sha1.getChecksum());
        final Checksum sha256 = checksums.stream().filter(checksum -> Constants.SHA256_TYPE_FOR_SOURCEFILES.equals(checksum.getType())).findFirst().get();
        Assert.assertNotNull(sha256.getChecksum());
        Assert.assertNotEquals(sha1.getChecksum(), sha256.getChecksum());
    }
}
