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
import java.util.HashSet;
import java.util.Set;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dockstore.webservice.languages.WDLHandler;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WDLParseTest {

    @Test
    public void testWDLMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example0.wdl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL);
        Entry entry = sInterface
            .parseWorkflowContent(new Tool(), filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>());
        assertTrue("incorrect author", entry.getAuthor().contains("Chip Stewart"));
        assertTrue("incorrect email", entry.getEmail().contains("stewart@broadinstitute.org"));
    }

    @Test
    public void testWDLMetadataExampleWithMerge() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example1.wdl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL);
        Entry entry = sInterface
            .parseWorkflowContent(new Tool(), filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>());
        assertTrue("incorrect author", entry.getAuthor().split(",").length >= 2);
        assertTrue("incorrect email", entry.getEmail() == null);
    }

    @Test
    public void testWDLMetadataExampleWithWorkflowMeta() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example2.wdl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL);
        Entry entry = sInterface
            .parseWorkflowContent(new Tool(), filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>());
        assertTrue("incorrect author", entry.getAuthor().split(",").length >= 2);
        assertEquals("incorrect email", "This is a cool workflow", entry.getDescription());
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
            Assert.assertEquals("Error parsing workflow. You may have a recursive import.", e.getErrorMessage());
        }
    }

    /**
     * Tests that Dockstore can handle a WDL 1.0 workflow using HTTP and map import
     * Error parsing will throw an exception, but with no error should just pass
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
        } catch (Exception e) {
            Assert.fail("Should properly parse file and imports.");
        }
    }
}
