package io.dockstore.webservice.helpers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

import io.dockstore.webservice.CustomWebApplicationException;
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
        final ZipSourceFileHelper.SourceFiles sourceFiles = ZipSourceFileHelper.sourceFilesFromZip(smartSeqZipFile, SourceFile.FileType.DOCKSTORE_WDL);
        Assert.assertEquals("SmartSeq2SingleSample.wdl", sourceFiles.getPrimaryDescriptor().getPath());
        Assert.assertEquals(9, sourceFiles.getAllDescriptors().size());
        Assert.assertEquals("Expecting one .dockstore.yml", 1, sourceFiles.getAllDescriptors().stream().filter(sf -> sf.getType() == SourceFile.FileType.DOCKSTORE_YML).count());
    }

    @Test
    public void validateType() throws IOException {
        ZipFile smartSeqZipFile = new ZipFile(new File(SMART_SEQ_ZIP_PATH));
        try {
            ZipSourceFileHelper.sourceFilesFromZip(smartSeqZipFile, SourceFile.FileType.DOCKSTORE_CWL);
            Assert.fail("Expected failure because zip has WDL but workflow is CWL");
        } catch (CustomWebApplicationException ex) {
            // This is expected
        }
    }
}
