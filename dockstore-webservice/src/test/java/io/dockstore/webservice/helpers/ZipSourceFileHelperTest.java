package io.dockstore.webservice.helpers;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;

import io.dockstore.webservice.core.SourceFile;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.fail;

public class ZipSourceFileHelperTest {

    private final static String SMART_SEQ_ZIP_PATH = ResourceHelpers.resourceFilePath("smartseq.zip");

    @Test
    public void validateZip() throws IOException {
        ZipFile smartSeqZipFile = new ZipFile(new File(SMART_SEQ_ZIP_PATH));
        try {
            ZipSourceFileHelper.validateZip(smartSeqZipFile, 1, 1);
            fail("Exepcted validate to throw error");
        } catch(Exception ex) {
            // This is expected
        }
        try {
            ZipSourceFileHelper.validateZip(smartSeqZipFile, 1, 100);
            fail("Exepcted validate to throw error");
        } catch(Exception ex) {
            // This is expected
        }
        ZipSourceFileHelper.validateZip(smartSeqZipFile, 100, 100_000);
    }

    @Test
    public void sourceFilesFromZip() throws IOException {
        ZipFile smartSeqZipFile = new ZipFile(new File(SMART_SEQ_ZIP_PATH));
        final List<SourceFile> sourceFiles = ZipSourceFileHelper.sourceFilesFromZip(smartSeqZipFile);
        Assert.assertEquals(9, sourceFiles.size());
        Assert.assertEquals(2, sourceFiles.stream().filter(sf -> sf.getType() == SourceFile.FileType.CWL_TEST_JSON).count());
    }
}
