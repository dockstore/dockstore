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

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.DescriptionSource;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

public class CWLParseTest {


    @Test
    public void testOldMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example0.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Tool tool = new Tool();
        Tag tag = new Tag();
        addVersionAsDefault(tool, tag);
        Entry entry = sInterface.parseWorkflowContent(tool, filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(),
                tag);
        checkAuthorEmailDescription(entry, tag, "Keiran Raine", "keiranmraine@gmail.com", "The Sanger's Cancer Genome Project core somatic calling workflow from \n"
                + "the ICGC PanCancer Analysis of Whole Genomes (PCAWG) project.\n"
                + "For more information see the PCAWG project [page](https://dcc.icgc.org/pcawg) and our GitHub\n"
                + "[page](https://github.com/ICGC-TCGA-PanCancer) for our code including the source for\n"
                + "[this workflow](https://github.com/ICGC-TCGA-PanCancer/CGP-Somatic-Docker).");
    }

    @Test
    public void testNewMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example2.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Tool tool = new Tool();
        Tag tag = new Tag();
        addVersionAsDefault(tool, tag);
        Entry entry = sInterface.parseWorkflowContent(tool, filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(),
                tag);
        checkAuthorEmailDescription(entry, tag, "Denis Yuen", "dyuen@oicr.on.ca", "Note that this is an example and the metadata is not necessarily consistent.");
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
        Tag tag = new Tag();
        Tool tool = new Tool();
        addVersionAsDefault(tool, tag);
        Entry entry = sInterface.parseWorkflowContent(tool, filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(),
                tag);
        checkAuthorEmailDescription(entry, tag, "Peter Amstutz", "peter.amstutz@curoverse.com", "Print the contents of a file to stdout using 'cat' running in a docker container.");
    }

    /**
     * This tests a CWL 1.1 doc field that has a single value in an array
     * @throws IOException If file contents could not be read
     */
    @Test
    public void testcwlVersion11doc2() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_cwlVersion1_1_example2.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Tag tag = new Tag();
        Tool tool = new Tool();
        addVersionAsDefault(tool, tag);
        Entry entry = sInterface.parseWorkflowContent(tool, filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(),
                tag);
        checkAuthorEmailDescription(entry, tag, "Peter Amstutz", "peter.amstutz@curoverse.com", "Print the contents of a file to stdout using 'cat' running in a docker container.");
    }

    /**
     * This tests a CWL 1.1 doc field that has multiple values in an array
     * @throws IOException If file contents could not be read
     */
    @Test
    public void testcwlVersion11doc3() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_cwlVersion1_1_example3.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Tool tool = new Tool();
        Tag tag = new Tag();
        addVersionAsDefault(tool, tag);
        Entry entry = sInterface.parseWorkflowContent(tool, filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(),
                tag);
        checkAuthorEmailDescription(entry, tag, "Peter Amstutz", "peter.amstutz@curoverse.com", "Print the contents of a file to stdout using 'cat' running in a docker container.\nNew line doc.");
    }

    @Test
    public void testCombinedMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example3.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        Tag tag = new Tag();
        Tool tool = new Tool();
        addVersionAsDefault(tool, tag);
        Entry entry = sInterface.parseWorkflowContent(tool, filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), tag);
        checkAuthorEmailDescription(entry, tag, "Denis Yuen", "dyuen@oicr.on.ca", "The Sanger's Cancer Genome Project core somatic calling workflow from \n"
                + "the ICGC PanCancer Analysis of Whole Genomes (PCAWG) project.\n"
                + "For more information see the PCAWG project [page](https://dcc.icgc.org/pcawg) and our GitHub\n"
                + "[page](https://github.com/ICGC-TCGA-PanCancer) for our code including the source for\n"
                + "[this workflow](https://github.com/ICGC-TCGA-PanCancer/CGP-Somatic-Docker).");
    }

    private static void addVersionAsDefault(Entry entry, Version version) {
        String randomVersionName = "potato";
        version.setName(randomVersionName);
        entry.addWorkflowVersion(version);
        entry.setDefaultVersion(randomVersionName);
    }

    private static void checkAuthorEmailDescription(Entry entry, Version version, String author, String email, String description) {
        Assert.assertEquals("incorrect author", author, entry.getAuthor());
        Assert.assertEquals("incorrect author", author, version.getAuthor());
        Assert.assertEquals("incorrect email", email, entry.getEmail());
        Assert.assertEquals("incorrect email", email, version.getEmail());
        Assert.assertEquals("incorrect description", description, entry.getDescription());
        Assert.assertEquals("incorrect description", description, version.getDescription());
        if (description == null) {
            Assert.assertEquals("incorrect description source", null, version.getDescriptionSource());
        } else {
            Assert.assertEquals("incorrect description source", DescriptionSource.DESCRIPTOR, version.getDescriptionSource());
        }

    }


}
