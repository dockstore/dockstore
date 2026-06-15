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
import java.sql.Timestamp;
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
        // workflow with one invalid version -> falls back to all versions
        workflow.addWorkflowVersion(createVersion("invalid", false, 13));
        assertEquals("invalid", EntryVersionHelper.determineRepresentativeVersion(workflow).get().getName());
        // add a valid version -> valid versions are preferred over all
        workflow.addWorkflowVersion(createVersion("valid", true, 11));
        assertEquals("valid", EntryVersionHelper.determineRepresentativeVersion(workflow).get().getName());
        // add "master" -> mainline versions take top priority
        workflow.addWorkflowVersion(createVersion("master", false, 1));
        assertEquals("master", EntryVersionHelper.determineRepresentativeVersion(workflow).get().getName());
        // add "main" with a higher id -> "main" wins via the id fallback (dbUpdateDate is null for both)
        workflow.addWorkflowVersion(createVersion("main", false, 2));
        assertEquals("main", EntryVersionHelper.determineRepresentativeVersion(workflow).get().getName());
        // add "develop" -> mainline still wins with "main"; "develop" pool is not reached
        workflow.addWorkflowVersion(createVersion("develop", false, 3));
        assertEquals("main", EntryVersionHelper.determineRepresentativeVersion(workflow).get().getName());
    }

    @Test
    void testRepresentativeVersionMostRecentlyUpdated() throws IllegalAccessException {
        Timestamp older = Timestamp.valueOf("2024-01-01 00:00:00");
        Timestamp younger = Timestamp.valueOf("2025-06-01 00:00:00");

        // Within the mainline pool: newer dbUpdateDate beats higher id
        BioWorkflow workflow = new BioWorkflow();
        workflow.addWorkflowVersion(createVersion("main", false, 10, older));
        workflow.addWorkflowVersion(createVersion("master", false, 5, younger));
        assertEquals("master", EntryVersionHelper.determineRepresentativeVersion(workflow).get().getName());

        // Within the mainline pool: non-null dbUpdateDate beats null dbUpdateDate, even with a lower id
        BioWorkflow workflow2 = new BioWorkflow();
        workflow2.addWorkflowVersion(createVersion("main", false, 10, null));
        workflow2.addWorkflowVersion(createVersion("master", false, 5, older));
        assertEquals("master", EntryVersionHelper.determineRepresentativeVersion(workflow2).get().getName());

        // dbUpdateDate is only a tiebreaker within a pool; a more-recently-updated version
        // in a lower-priority pool cannot beat an older version in a higher-priority pool
        BioWorkflow workflow3 = new BioWorkflow();
        workflow3.addWorkflowVersion(createVersion("master", false, 1, older));
        workflow3.addWorkflowVersion(createVersion("develop", false, 100, younger));
        assertEquals("master", EntryVersionHelper.determineRepresentativeVersion(workflow3).get().getName());

        // Within the valid pool: newer dbUpdateDate wins over higher id
        BioWorkflow workflow4 = new BioWorkflow();
        workflow4.addWorkflowVersion(createVersion("v1.0", true, 10, older));
        workflow4.addWorkflowVersion(createVersion("v2.0", true, 5, younger));
        assertEquals("v2.0", EntryVersionHelper.determineRepresentativeVersion(workflow4).get().getName());

        // Within the fallback (all) pool: newer dbUpdateDate wins over higher id
        BioWorkflow workflow5 = new BioWorkflow();
        workflow5.addWorkflowVersion(createVersion("feature-a", false, 10, older));
        workflow5.addWorkflowVersion(createVersion("feature-b", false, 5, younger));
        assertEquals("feature-b", EntryVersionHelper.determineRepresentativeVersion(workflow5).get().getName());
    }

    @Test
    void testRepresentativeVersionSelectionDevelop() throws IllegalAccessException {
        // When there are no mainline/valid-tag/default candidates, "develop" is chosen.
        BioWorkflow workflow = new BioWorkflow();
        workflow.addWorkflowVersion(createVersion("feature-x", false, 1));
        workflow.addWorkflowVersion(createVersion("develop", false, 2));
        assertEquals("develop", EntryVersionHelper.determineRepresentativeVersion(workflow).get().getName());
    }

    private WorkflowVersion createVersion(String name, boolean valid, long id) throws IllegalAccessException {
        return createVersion(name, valid, id, null);
    }

    private WorkflowVersion createVersion(String name, boolean valid, long id, Timestamp dbUpdateDate) throws IllegalAccessException {
        WorkflowVersion version = new WorkflowVersion();
        version.setName(name);
        version.setValid(valid);
        FieldUtils.writeField(version, "id", id, true);
        version.setDbUpdateDate(dbUpdateDate);
        return version;
    }
}
