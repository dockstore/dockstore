package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class ZipSourceFileHelperTest {

    private static final String SMART_SEQ_ZIP_PATH = ResourceHelpers.resourceFilePath("smartseq.zip");
    /**
     * Contains workflow in folder
     */
    private static final String WHALESAY_ZIP_PATH = ResourceHelpers.resourceFilePath("whalesayinsubdir.zip");

    @Test
    void validateZip() throws IOException {
        ZipFile smartSeqZipFile = new ZipFile(new File(SMART_SEQ_ZIP_PATH));
        try {
            ZipSourceFileHelper.validateZip(smartSeqZipFile, 1, 1);
            fail("Expected validate to throw error");
        } catch (Exception ex) {
            // This is expected
        }
        try {
            ZipSourceFileHelper.validateZip(smartSeqZipFile, 1, 100);
            fail("Expected validate to throw error");
        } catch (Exception ex) {
            // This is expected
        }
        ZipSourceFileHelper.validateZip(smartSeqZipFile, 100, 100_000);
    }

    @Test
    void sourceFilesFromZip() throws IOException {
        try (ZipFile smartSeqZipFile = new ZipFile(new File(SMART_SEQ_ZIP_PATH))) {
            final ZipSourceFileHelper.SourceFiles sourceFiles = ZipSourceFileHelper.sourceFilesFromZip(smartSeqZipFile, DescriptorLanguage.FileType.DOCKSTORE_WDL);
            assertEquals("SmartSeq2SingleSample.wdl", sourceFiles.getPrimaryDescriptor().getPath());
            assertEquals("/SmartSeq2SingleSample.wdl", sourceFiles.getPrimaryDescriptor().getAbsolutePath());
            assertEquals(9, sourceFiles.getAllDescriptors().size());
            assertEquals(1, sourceFiles.getAllDescriptors().stream().filter(sf -> sf.getType() == FileType.DOCKSTORE_YML).count(), "Expecting one .dockstore.yml");
        }
    }

    @Test
    void sourceFilesFromZipWithFolder() throws IOException {
        try (ZipFile whalesayZipFile = new ZipFile(new File(WHALESAY_ZIP_PATH))) {
            final ZipSourceFileHelper.SourceFiles sourceFiles = ZipSourceFileHelper.sourceFilesFromZip(whalesayZipFile, DescriptorLanguage.FileType.DOCKSTORE_WDL);
            assertEquals("subdir/Dockstore.wdl", sourceFiles.getPrimaryDescriptor().getPath());
            assertEquals("/subdir/Dockstore.wdl", sourceFiles.getPrimaryDescriptor().getAbsolutePath());
            assertEquals(2, sourceFiles.getAllDescriptors().size()); // One yml and one WDL
        }
    }

    @Test
    void validateType() throws IOException {
        ZipFile smartSeqZipFile = new ZipFile(new File(SMART_SEQ_ZIP_PATH));
        try {
            ZipSourceFileHelper.sourceFilesFromZip(smartSeqZipFile, DescriptorLanguage.FileType.DOCKSTORE_CWL);
            fail("Expected failure because zip has WDL but workflow is CWL");
        } catch (CustomWebApplicationException ex) {
            // This is expected
        }
    }
}
