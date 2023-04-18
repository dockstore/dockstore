/*
 *    Copyright 2017 OICR
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.SortedSet;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class CWLParseTest {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();

    @SystemStub
    public final SystemErr systemErr = new SystemErr();


    @Test
    void testOldMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example0.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Version entry = sInterface.parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        assertEquals("Keiran Raine", entry.getAuthor(), "incorrect author");
        assertEquals("keiranmraine@gmail.com", entry.getEmail(), "incorrect email");
    }

    @Test
    void testNewMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example2.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Version entry = sInterface.parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        assertEquals("Denis Yuen", entry.getAuthor(), "incorrect author");
        assertEquals("dyuen@oicr.on.ca", entry.getEmail(), "incorrect email");
    }

    /**
     * This tests a CWL 1.1 doc field that has a string value
     * This test is probably redundant
     * @throws IOException  If file contents could not be read
     */
    @Test
    void testcwlVersion11doc1() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_cwlVersion1_1_example1.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        String content = FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8);
        Version entry = sInterface.parseWorkflowContent(filePath, content, new HashSet<>(), new Tag());
        assertEquals("Peter Amstutz", entry.getAuthor(), "incorrect author");
        assertEquals("peter.amstutz@curoverse.com", entry.getEmail(), "incorrect email");
        assertEquals("Print the contents of a file to stdout using 'cat' running in a docker container.", entry.getDescription(), "incorrect description");

        // Test that a version that no longer has a descriptor description has its version description and description source set to null
        content = content.replace("doc: \"Print the contents of a file to stdout using 'cat' running in a docker container.\"", ""); // Remove the description from the descriptor
        entry = sInterface.parseWorkflowContent(filePath, content, new HashSet<>(), entry);
        assertNull(entry.getDescription());
        assertNull(entry.getDescriptionSource());
    }

    /**
     * This tests a CWL 1.1 doc field that has a single value in an array
     * @throws IOException If file contents could not be read
     */
    @Test
    void testcwlVersion11doc2() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_cwlVersion1_1_example2.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        String content = FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8);
        Version entry = sInterface.parseWorkflowContent(filePath, content, new HashSet<>(), new Tag());
        assertEquals("Peter Amstutz", entry.getAuthor(), "incorrect author");
        assertEquals("peter.amstutz@curoverse.com", entry.getEmail(), "incorrect email");
        assertEquals("Print the contents of a file to stdout using 'cat' running in a docker container.", entry.getDescription(), "incorrect description");

        // Test that a version that no longer has a descriptor description has its version description and description source set to null
        content = content.replace("doc: [\"Print the contents of a file to stdout using 'cat' running in a docker container.\"]", ""); // Remove the description from the descriptor
        entry = sInterface.parseWorkflowContent(filePath, content, new HashSet<>(), entry);
        assertNull(entry.getDescription());
        assertNull(entry.getDescriptionSource());
    }

    /**
     * This tests a CWL 1.1 doc field that has multiple values in an array
     * @throws IOException If file contents could not be read
     */
    @Test
    void testcwlVersion11doc3() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_cwlVersion1_1_example3.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        String content = FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8);
        final String expectedDescription = "Print the contents of a file to stdout using 'cat' running in a docker container.\nNew line doc.";
        Version entry = sInterface.parseWorkflowContent(filePath, content, new HashSet<>(), new Tag());
        assertEquals("Peter Amstutz", entry.getAuthor(), "incorrect author");
        assertEquals("peter.amstutz@curoverse.com", entry.getEmail(), "incorrect email");
        assertEquals(expectedDescription, entry.getDescription(), "incorrect description");

        // Test that a version that no longer has a descriptor description has its version description and description source set to null
        content = content.replace("doc: [\"Print the contents of a file to stdout using 'cat' running in a docker container.\", \"New line doc.\"]", ""); // Remove the description from the descriptor
        entry = sInterface.parseWorkflowContent(filePath, content, new HashSet<>(), entry);
        assertNull(entry.getDescription());
        assertNull(entry.getDescriptionSource());
    }

    /**
     * This tests a malicious CWL descriptor
     * @throws IOException If file contents could not be read
     */
    @Test
    void testMaliciousCwl() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("malicious.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Version entry = sInterface.parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        // This checks the version is not created but not that it was never parsed
        assertFalse(entry.isValid());
        SortedSet<Validation> validations = entry.getValidations();
        assertTrue(validations.first().getMessage().contains("CWL file is malformed or missing"));
    }

    @Test
    void testCombinedMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example3.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Version entry = sInterface.parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        assertEquals("Denis Yuen", entry.getAuthor(), "incorrect author");
        assertEquals("dyuen@oicr.on.ca", entry.getEmail(), "incorrect email");
    }
}
