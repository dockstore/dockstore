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

import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WDLParseTest {

    @Test
    public void testWDLMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example0.wdl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(SourceFile.FileType.DOCKSTORE_WDL);
        Entry entry = sInterface
            .parseWorkflowContent(new Tool(), filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>());
        assertTrue("incorrect author", entry.getAuthor().contains("Chip Stewart"));
        assertTrue("incorrect email", entry.getEmail().contains("stewart@broadinstitute.org"));
    }

    @Test
    public void testWDLMetadataExampleWithMerge() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example1.wdl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(SourceFile.FileType.DOCKSTORE_WDL);
        Entry entry = sInterface
            .parseWorkflowContent(new Tool(), filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>());
        assertTrue("incorrect author", entry.getAuthor().split(",").length >= 2);
        assertTrue("incorrect email", entry.getEmail().isEmpty());
    }

    @Test
    public void testWDLMetadataExampleWithWorkflowMeta() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example2.wdl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(SourceFile.FileType.DOCKSTORE_WDL);
        Entry entry = sInterface
            .parseWorkflowContent(new Tool(), filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>());
        assertTrue("incorrect author", entry.getAuthor().split(",").length >= 2);
        assertEquals("incorrect email", "This is a cool workflow", entry.getDescription());
    }
}
