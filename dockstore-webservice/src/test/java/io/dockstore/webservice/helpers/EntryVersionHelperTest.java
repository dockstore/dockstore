package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.WorkflowVersion;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;

class EntryVersionHelperTest {

    @Test
    void removeWorkingDirectory() {
        assertEquals("Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("/Dockstore.cwl", "Dockstore.cwl"));
        assertEquals("foo/Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("foo/Dockstore.cwl", "Dockstore.cwl"));
        assertEquals("foo/Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("./foo/Dockstore.cwl", "Dockstore.cwl"));
        assertEquals("foo/Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("/foo/Dockstore.cwl", "Dockstore.cwl"));
        // Edge case of filename also being part of the path
        assertEquals("Dockstore.cwl/Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("/Dockstore.cwl/Dockstore.cwl", "Dockstore.cwl"));

        assertEquals("Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("./Dockstore.cwl", "Dockstore.cwl"));
        assertEquals("Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("././Dockstore.cwl", "Dockstore.cwl"));
        assertEquals(".dockstore.yml", EntryVersionHelper.removeWorkingDirectory(".dockstore.yml", ".dockstore.yml"));
        assertEquals(".dockstore.yml", EntryVersionHelper.removeWorkingDirectory("/.dockstore.yml", ".dockstore.yml"));
        assertEquals(".dockstore.yml", EntryVersionHelper.removeWorkingDirectory("./.dockstore.yml", ".dockstore.yml"));
        assertEquals(".dockstore.yml", EntryVersionHelper.removeWorkingDirectory("././.dockstore.yml", ".dockstore.yml"));
    }

    /**
     * Tests that there's no exceptions when create zip file with null content or empty content
     * Does not test the possible IOException from zipOutputStream.putNextEntry and zipOutputStream.closeEntry
     * @throws IOException
     */
    @Test
    void testWriteStreamAsZip() throws IOException {
        EntryVersionHelper anonymousClass = () -> null;
        SourceFile sourceFile1 = new SourceFile();
        sourceFile1.setContent(null);
        sourceFile1.setPath("/nullSourcefile");
        sourceFile1.setAbsolutePath("/nullSourcefile");
        sourceFile1.setType(DescriptorLanguage.FileType.CWL_TEST_JSON);
        SourceFile sourceFile2 = new SourceFile();
        sourceFile2.setContent("");
        sourceFile2.setPath("/emptySourcefile");
        sourceFile2.setAbsolutePath("/emptySourcefile");
        sourceFile2.setType(DescriptorLanguage.FileType.CWL_TEST_JSON);
        SourceFile sourceFile3 = new SourceFile();
        sourceFile3.setContent("potato");
        sourceFile3.setPath("/actualSourcefile");
        sourceFile3.setAbsolutePath("/actualSourcefile");
        sourceFile3.setType(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        String zipAsString;
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            SourceFile sourceFile4 = new SourceFile();
            sourceFile4.setContent("potato in directory");
            sourceFile4.setPath("/directory/actualSourcefile");
            sourceFile4.setAbsolutePath("/directory/actualSourcefile");
            sourceFile4.setType(DescriptorLanguage.FileType.CWL_TEST_JSON);
            Set<SourceFile> sourceFiles = new HashSet<>();
            sourceFiles.add(sourceFile1);
            sourceFiles.add(sourceFile2);
            sourceFiles.add(sourceFile3);
            sourceFiles.add(sourceFile4);
            anonymousClass.writeStreamAsZip(sourceFiles, byteArrayOutputStream, Paths.get(""));
            // Very weird way of checking that the zip contains the correct sourcefiles
            zipAsString = byteArrayOutputStream.toString();
        }
        assertTrue(zipAsString.contains("actualSourcefile"));
        assertTrue(zipAsString.contains("emptySourcefile"));
        assertTrue(zipAsString.contains("directory/actualSourcefile"));
        assertFalse(zipAsString.contains("/nullSourcefile"));
    }

    @Test
    void testZipFileName() {
        String path = "github.com/dockstore/hello_world";
        String versionName = "master";
        assertEquals("github.com-dockstore-hello_world-master.zip", EntryVersionHelper.generateZipFileName(path, versionName));
    }

    @Test
    void testRepresentativeVersionSelection() throws IllegalAccessException {
        // workflow with no versions
        BioWorkflow workflow = new BioWorkflow();
        assertEquals(Optional.empty(), EntryVersionHelper.determineRepresentativeVersion(workflow));
        // workflow with one invalid version
        workflow.addWorkflowVersion(createVersion("invalid", false, 13));
        assertEquals("invalid", EntryVersionHelper.determineRepresentativeVersion(workflow).get().getName());
        // workflow with one valid and one invalid version
        workflow.addWorkflowVersion(createVersion("valid", true, 11));
        assertEquals("valid", EntryVersionHelper.determineRepresentativeVersion(workflow).get().getName());
        // workflow with "master" version and some other versions
        workflow.addWorkflowVersion(createVersion("master", false, 1));
        assertEquals("master", EntryVersionHelper.determineRepresentativeVersion(workflow).get().getName());
        // workflow with "master", "main", and "develop" versions, should select one of those three with the highest id
        workflow.addWorkflowVersion(createVersion("main", false, 2));
        workflow.addWorkflowVersion(createVersion("develop", false, 3));
        assertEquals("develop", EntryVersionHelper.determineRepresentativeVersion(workflow).get().getName());
    }

    private WorkflowVersion createVersion(String name, boolean valid, long id) throws IllegalAccessException {
        WorkflowVersion version = new WorkflowVersion();
        version.setName(name);
        version.setValid(valid);
        FieldUtils.writeField(version, "id", id, true);
        return version;
    }
}
