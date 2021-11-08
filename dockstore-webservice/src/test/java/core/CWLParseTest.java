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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.SortedSet;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dropwizard.testing.ResourceHelpers;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class CWLParseTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();


    @Test
    public void testOldMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example0.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Version entry = sInterface.parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        Assert.assertEquals("incorrect author", "Keiran Raine", entry.getAuthor());
        Assert.assertEquals("incorrect email", "keiranmraine@gmail.com", entry.getEmail());
    }

    @Test
    public void testNewMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example2.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Version entry = sInterface.parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        Assert.assertEquals("incorrect author", "Denis Yuen", entry.getAuthor());
        Assert.assertEquals("incorrect email", "dyuen@oicr.on.ca", entry.getEmail());
    }

    /**
     * This tests a CWL 1.1 doc field that has a string value
     * This test is probably redundant
     * @throws IOException  If file contents could not be read
     */
    @Test
    public void testcwlVersion11doc1() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_cwlVersion1_1_example1.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Version entry = sInterface.parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        Assert.assertEquals("incorrect author", "Peter Amstutz", entry.getAuthor());
        Assert.assertEquals("incorrect email", "peter.amstutz@curoverse.com", entry.getEmail());
        Assert.assertEquals("incorrect description", "Print the contents of a file to stdout using 'cat' running in a docker container.", entry.getDescription());
    }

    /**
     * This tests a CWL 1.1 doc field that has a single value in an array
     * @throws IOException If file contents could not be read
     */
    @Test
    public void testcwlVersion11doc2() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_cwlVersion1_1_example2.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Version entry = sInterface.parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        Assert.assertEquals("incorrect author", "Peter Amstutz", entry.getAuthor());
        Assert.assertEquals("incorrect email", "peter.amstutz@curoverse.com", entry.getEmail());
        Assert.assertEquals("incorrect description", "Print the contents of a file to stdout using 'cat' running in a docker container.", entry.getDescription());
    }

    /**
     * This tests a CWL 1.1 doc field that has multiple values in an array
     * @throws IOException If file contents could not be read
     */
    @Test
    public void testcwlVersion11doc3() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_cwlVersion1_1_example3.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Version entry = sInterface.parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        Assert.assertEquals("incorrect author", "Peter Amstutz", entry.getAuthor());
        Assert.assertEquals("incorrect email", "peter.amstutz@curoverse.com", entry.getEmail());
        Assert.assertEquals("incorrect description", "Print the contents of a file to stdout using 'cat' running in a docker container.\nNew line doc.", entry.getDescription());
    }

    /**
     * This tests a malicious CWL descriptor
     * @throws IOException If file contents could not be read
     */
    @Test
    public void testMaliciousCwl() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("malicious.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Version entry = sInterface.parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        // This checks the version is not created but not that it was never parsed
        Assert.assertEquals(entry.isValid(), false);
        SortedSet<Validation> validations = entry.getValidations();
        Assert.assertTrue(validations.first().getMessage().contains("CWL file is malformed or missing"));
    }

    @Test
    public void testCombinedMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example3.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Version entry = sInterface.parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        Assert.assertEquals("incorrect author", "Denis Yuen", entry.getAuthor());
        Assert.assertEquals("incorrect email", "dyuen@oicr.on.ca", entry.getEmail());
    }
}
