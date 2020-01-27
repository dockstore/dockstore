/*
 *    Copyright 2018 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MemoryIntensiveTest;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dockstore.webservice.languages.WDLHandler;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static io.dockstore.webservice.languages.WDLHandler.ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(MemoryIntensiveTest.class)
public class WDLParseTest {
    @Test
    public void testWDLMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example0.wdl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL);
        Version entry = sInterface
            .parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        assertTrue("incorrect author", entry.getAuthor().contains("Chip Stewart"));
        assertTrue("incorrect email", entry.getEmail().contains("stewart@broadinstitute.org"));
    }

    @Test
    public void testWDLMetadataExampleWithMerge() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example1.wdl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL);
        Version entry = sInterface
            .parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        assertTrue("incorrect author", entry.getAuthor().split(",").length >= 2);
        assertNull("incorrect email", entry.getEmail());
    }

    @Test
    public void testWDLMetadataExampleWithWorkflowMeta() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example2.wdl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL);
        Version entry = sInterface
            .parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        assertTrue("incorrect author", entry.getAuthor().split(",").length >= 2);
        assertEquals("incorrect email", "This is a cool workflow", entry.getDescription());
    }

    @Test
    public void testRecursiveImportsMetadata() {
        try {
            WDLHandler.checkForRecursiveLocalImports(getSourceFile1().getContent(), getSourceFiles(), new HashSet<>(), "/");
            Assert.fail("Should have detected recursive local import");
        } catch (ParseException e) {
            Assert.assertEquals("Recursive local import detected: /first-import.wdl", e.getMessage());
        }
    }

    @Test
    public void parseRecursiveWorkflowContent() {
        WDLHandler wdlHandler = new WDLHandler();
        WorkflowVersion version = new WorkflowVersion();
        wdlHandler.parseWorkflowContent(getSourceFile1().getAbsolutePath(), getSourceFile1().getContent(), getSourceFiles(), version);
        SortedSet<Validation> validations = version.getValidations();
        Assert.assertTrue(validations.first().getMessage().contains("Recursive local import detected: /first-import.wdl"));
    }

    private static SourceFile getSourceFile1() {
        SourceFile sourceFile1 = new SourceFile();
        sourceFile1.setAbsolutePath("/Dockstore.wdl");
        sourceFile1.setPath("Dockstore.wdl");
        sourceFile1.setContent("import \"first-import.wdl\" as first\n" + "task hello {\n" + "  String name\n" + "\n" + "  command {\n"
                + "    echo 'Hello ${name}!'\n" + "  }\n" + "  output {\n" + "    File response = stdout()\n" + "  }\n" + "}\n" + "\n"
                + "workflow test {\n" + "  call hello\n" + "  call first.hello\n" + "}");
        return sourceFile1;
    }

    private static SourceFile getSourceFile2() {
        SourceFile sourceFile2 = new SourceFile();
        sourceFile2.setAbsolutePath("/first-import.wdl");
        sourceFile2.setPath("first-import.wdl");
        sourceFile2.setContent("import \"Dockstore.wdl\" as second\n" + "task hello {\n" + "  String name\n" + "\n" + "  command {\n"
                + "    echo 'Hello ${name}!'\n" + "  }\n" + "  output {\n" + "    File response = stdout()\n" + "  }\n" + "}");
        return sourceFile2;
    }

    private static Set<SourceFile> getSourceFiles() {
        Set<SourceFile> sourceFiles = new HashSet<>();
        sourceFiles.add(getSourceFile1());
        sourceFiles.add(getSourceFile2());
        return sourceFiles;
    }


    /**
     * Tests that Dockstore can handle a workflow with recursive imports
     */
    @Test
    public void testRecursiveImport() {
        String type = "workflow";
        File recursiveWDL = new File(ResourceHelpers.resourceFilePath("recursive.wdl"));
        String primaryDescriptorFilePath = recursiveWDL.getAbsolutePath();
        SourceFile sourceFile = new SourceFile();
        try {
            sourceFile.setContent(FileUtils.readFileToString(recursiveWDL, StandardCharsets.UTF_8));
            sourceFile.setAbsolutePath(recursiveWDL.getAbsolutePath());
            sourceFile.setPath(recursiveWDL.getAbsolutePath());
            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);
            Set<SourceFile> sourceFileSet = new HashSet<>();
            sourceFileSet.add(sourceFile);
            WDLHandler wdlHandler = new WDLHandler();
            wdlHandler.validateEntrySet(sourceFileSet, primaryDescriptorFilePath, type);
            Assert.fail();
        } catch (IOException e) {
            Assert.fail();
        } catch (CustomWebApplicationException e) {
            Assert.assertEquals(ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT, e.getErrorMessage());
        }
    }

    /**
     * Tests that Dockstore can handle a workflow with something that kinda looks recursive but isn't
     */
    @Test
    public void testNotReallyRecursiveImport() {
        String type = "workflow";
        File recursiveWDL = new File(ResourceHelpers.resourceFilePath("not-really-recursive/not-really-recursive.wdl"));
        String primaryDescriptorFilePath = recursiveWDL.getAbsolutePath();
        SourceFile sourceFile = new SourceFile();
        try {
            sourceFile.setContent(FileUtils.readFileToString(recursiveWDL, StandardCharsets.UTF_8));
            sourceFile.setAbsolutePath(recursiveWDL.getAbsolutePath());
            sourceFile.setPath(recursiveWDL.getAbsolutePath());
            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);
            Set<SourceFile> sourceFileSet = new HashSet<>();
            sourceFileSet.add(sourceFile);
            WDLHandler wdlHandler = new WDLHandler();
            wdlHandler.validateEntrySet(sourceFileSet, primaryDescriptorFilePath, type);
        } catch (IOException | CustomWebApplicationException e) {
            Assert.fail();
        }
    }

    /**
     * Tests that Dockstore can handle a WDL 1.0 workflow using HTTP and map import
     * Error parsing will throw an exception, but with no error should just pass
     *
     * Also tests metadata in WDL 1.0 files
     */
    @Test
    public void testDraft3Code() {
        String type = "workflow";
        File primaryWDL = new File(ResourceHelpers.resourceFilePath("importTesting.wdl"));
        File importedWDL = new File(ResourceHelpers.resourceFilePath("md5sum.wdl"));
        String primaryDescriptorFilePath = primaryWDL.getAbsolutePath();
        SourceFile sourceFile = new SourceFile();
        SourceFile importedFile = new SourceFile();
        try {
            sourceFile.setContent(FileUtils.readFileToString(primaryWDL, StandardCharsets.UTF_8));
            sourceFile.setAbsolutePath(primaryWDL.getAbsolutePath());
            sourceFile.setPath(primaryWDL.getAbsolutePath());
            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);

            importedFile.setContent(FileUtils.readFileToString(importedWDL, StandardCharsets.UTF_8));
            importedFile.setAbsolutePath(importedWDL.getAbsolutePath());
            importedFile.setPath("./md5sum.wdl");
            importedFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);

            Set<SourceFile> sourceFileSet = new HashSet<>();
            sourceFileSet.add(sourceFile);
            sourceFileSet.add(importedFile);

            WDLHandler wdlHandler = new WDLHandler();
            wdlHandler.validateEntrySet(sourceFileSet, primaryDescriptorFilePath, type);

            LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL);
            Version entry = sInterface
                    .parseWorkflowContent(primaryWDL.getAbsolutePath(), FileUtils.readFileToString(primaryWDL, StandardCharsets.UTF_8), sourceFileSet, new WorkflowVersion());
            assertEquals("incorrect author", 1, entry.getAuthor().split(",").length);
            assertEquals("incorrect email", "foobar@foo.com", entry.getEmail());
            assertTrue("incorrect description", entry.getDescription().length() > 0);
        } catch (Exception e) {
            Assert.fail("Should properly parse file and imports.");
        }
    }
}
