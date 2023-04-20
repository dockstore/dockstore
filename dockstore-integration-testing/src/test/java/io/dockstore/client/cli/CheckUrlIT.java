/*
 * Copyright 2023 OICR and UCSC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.CheckUrlInterface;
import io.dockstore.webservice.helpers.CheckUrlInterface.UrlStatus;
import io.dockstore.webservice.helpers.LambdaUrlChecker;
import io.dockstore.webservice.resources.AbstractWorkflowResource;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CheckUrlIT {

    private static final String WDL_TEST_JSON = """
        {
          "test.files": [
            "https://open.dockstore.org",
            "https://anotheropen.dockstore.org"
          ],
          "test.file": "notaurl.file"
        }        
        """;

    private static final String CWL_TEST_JSON = """
        {
          "bam_cram_file": {
                "class": "File",
                "path": "https://open.dockstore.org",
                "secondaryFiles": [
                  {
                    "class": "File",
                    "path": "https://anotheropen.dockstore.org"
                  }
                ]
              }
        }
        """;

    private static final String CWL_TEST_JSON_WITH_LOCATION = """
        {
          "bam_cram_file": {
                "class": "File",
                "location": "https://open.dockstore.org",
                "secondaryFiles": [
                  {
                    "class": "File",
                    "path": "https://anotheropen.dockstore.org"
                  }
                ]
              }
        }
        """;

    private static final String TEST_JSON_WITH_FOOBAR = """
        <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" id="Layer_1" x="0" y="0" enable-background="new 0 0 575.7 458.6" version="1.1" viewBox="0 0 575.7 458.6" xml:space="preserve"><polygon fill="#7785AB" points="256.5 338.4 129.6 301.3 129.6 176.6 256.5 139.8"/><polygon fill="#7785AB" points="433.6 288.6 265.4 338.4 265.4 139.8 433.6 189.2"/><path fill="#FFF" d="M227.5,278.2c0,13.3-11.5,21.7-24.5,19c-11.9-2.4-20.8-13.5-20.8-24.8c0-11.4,9-20.1,20.9-19.5 C216.1,253.5,227.5,264.9,227.5,278.2z"/><path fill="#FFF" d="M198.6,191.4c0,9-7.1,17-15.4,17.9c-7.8,0.8-13.7-5-13.7-13.1c0-8.1,6-15.9,13.8-17.6 C191.6,176.7,198.7,182.4,198.6,191.4z"/><path fill="#FFF" d="M156,256.1c0,5.6-3.8,9.7-8.3,9.1c-4.3-0.5-7.7-5.1-7.7-10.3c0-5.2,3.4-9.3,7.7-9.2 C152.2,245.9,156,250.5,156,256.1z"/><polygon fill="none" stroke="#FFF" stroke-miterlimit="10" stroke-width="5.604" points="183.4 195.3 208.7 279.4 147.5 256.2"/><line x1="302.9" x2="302.9" y1="176.6" y2="297.1" fill="none" stroke="#FFF" stroke-linecap="round" stroke-miterlimit="10" stroke-width="4.452" opacity=".43"/><line x1="328.9" x2="328.9" y1="181.8" y2="291.8" fill="none" stroke="#FFF" stroke-linecap="round" stroke-miterlimit="10" stroke-width="4.452" opacity=".43"/><line x1="350.8" x2="350.8" y1="186.2" y2="287.5" fill="none" stroke="#FFF" stroke-linecap="round" stroke-miterlimit="10" stroke-width="4.452" opacity=".43"/><line x1="369.5" x2="369.5" y1="190.3" y2="284" fill="none" stroke="#FFF" stroke-linecap="round" stroke-miterlimit="10" stroke-width="4.452" opacity=".43"/><line x1="385.6" x2="385.6" y1="193.6" y2="280.8" fill="none" stroke="#FFF" stroke-linecap="round" stroke-miterlimit="10" stroke-width="4.452" opacity=".43"/><line x1="399.6" x2="399.6" y1="196.5" y2="278.1" fill="none" stroke="#FFF" stroke-linecap="round" stroke-miterlimit="10" stroke-width="4.452" opacity=".43"/><line x1="411.9" x2="411.9" y1="199.1" y2="275.7" fill="none" stroke="#FFF" stroke-linecap="round" stroke-miterlimit="10" stroke-width="4.452" opacity=".43"/><line x1="422.7" x2="422.7" y1="201.3" y2="273.6" fill="none" stroke="#FFF" stroke-linecap="round" stroke-miterlimit="10" stroke-width="4.452" opacity=".43"/><polygon fill="#7785AB" points="303.6 166.4 265.4 157.2 265.4 133.5 303.6 145.4"/><polygon fill="#7785AB" points="433.6 198.7 422 196 422 181.9 433.6 185.6"/><polygon fill="#7785AB" points="256.5 156 233.6 161.6 233.6 140.4 256.5 133.3"/><polygon fill="#7785AB" points="139.8 184.9 129.6 187.4 129.6 172.2 139.8 169"/><polygon fill="#7785AB" points="256.4 344.5 231.9 336.9 231.9 315 256.4 320.9"/><polygon fill="#7785AB" points="139.2 308.3 129.6 305.3 129.6 290.4 139.2 292.7"/><polygon fill="#7785AB" points="303.7 332.4 265.5 344.4 265.5 321.5 303.7 312.1"/><polygon fill="#7785AB" points="433.5 292 422 295.6 422 283.9 433.5 281"/><path fill="#7785AB" d="M182.7,162c0,2.5-1.9,5.1-4.2,5.7c-2.3,0.6-4.1-0.9-4.1-3.3s1.8-5,4.1-5.7C180.8,158,182.7,159.5,182.7,162z"/><path fill="#7785AB" d="M182.7,316.5c0,2.5-1.9,4-4.2,3.3c-2.3-0.7-4.1-3.2-4.1-5.7c0-2.4,1.8-3.9,4.1-3.3 C180.8,311.4,182.7,313.9,182.7,316.5z"/></svg>
        """;

    private static final String CWL_WITH_FILE_INPUT = """
        cwlVersion: v1.0
        class: Workflow
                
        inputs:
          bam_cram_file: File
                
        outputs:
          output_file:
            type: File
            outputSource: hello-world/output
                
        steps:
          hello-world:
            run: dockstore-tool-helloworld.cwl
            in:
              input_file: input_file
            out: [output]
        """;

    private static final String CWL_WITH_NO_FILE_INPUT = """
        cwlVersion: v1.0
        class: Workflow
                
        inputs:
          input_file: string
                
        outputs:
          output_file:
            type: File
            outputSource: hello-world/output
                
        steps:
          hello-world:
            run: dockstore-tool-helloworld.cwl
            in:
              input_file: input_file
            out: [output]
        """;

    private static final String WDL_WITH_FILES_INPUT = """
            version 1.0
            workflow test {
              input {
                Array[File] files
              }
            }
            """;

    private static final String WDL_WITH_FILE_INPUT = """
            version 1.0
            workflow test {
              input {
                File file
              }
            }
        """;

    private static final String WDL_WITH_NO_INPUTS = """
        version 1.0
        workflow test {
        }
        """;

    /**
     * Instance of LambdaUrlChecker that doesn't have a lambda to invoke; used for testing
     * conditions where the checker should correctly fail before even invoking the lambda.
     */
    private static final LambdaUrlChecker CHECK_URL_HELPER =
        new LambdaUrlChecker("https://url.doesnot.matter");

    /**
     * Psuedo open url 1 -- never hit in the tests
     */
    private static final String OPEN_URL_1 = "https://open.dockstore.org";
    /**
     * Psuedo open url 2 -- never hit in the tests
     */
    private static final String OPEN_URL_2 = "https://anotheropen.dockstore.org";
    private static final Set<String> OPEN_URLS = Set.of(OPEN_URL_1, OPEN_URL_2);
    /**
     * Pseudo non-open url -- never hit in the tests.
     */
    private static final String NON_OPEN_URL = "https://notopen.dockstore.org";

    @Test
    void checkUrlsFromLambdaGood() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_FILES_INPUT,
            FileType.DOCKSTORE_WDL, WDL_TEST_JSON,
            FileType.WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(UrlStatus.ALL_OPEN),
            DescriptorLanguage.WDL);
        assertTrue(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkUrlsFromLambdaBad() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_FILES_INPUT,
            FileType.DOCKSTORE_WDL, WDL_TEST_JSON,
            FileType.WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(UrlStatus.NOT_ALL_OPEN),
            DescriptorLanguage.WDL);
        assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkUrlsFromLambdaSomeBad() {
        // These urls are never hit in the tests
        WorkflowVersion workflowVersion = setupWorkflowVersion(
            WDL_WITH_FILES_INPUT, FileType.DOCKSTORE_WDL,
            WDL_TEST_JSON.replace(OPEN_URL_1, NON_OPEN_URL),
            FileType.WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(OPEN_URLS),
            DescriptorLanguage.WDL);
        assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkUrlsFromFoobar() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(
            WDL_WITH_FILES_INPUT, FileType.DOCKSTORE_WDL,
            TEST_JSON_WITH_FOOBAR,
            FileType.WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(OPEN_URLS),
            DescriptorLanguage.WDL);
        // just don't die
        assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkUrlsFromLambdaTerriblyWrong() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_FILES_INPUT,
            FileType.DOCKSTORE_WDL, WDL_TEST_JSON,
            FileType.WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(UrlStatus.UNKNOWN),
            DescriptorLanguage.WDL);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkLocalFileIsAParameter() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_FILE_INPUT,
            FileType.DOCKSTORE_WDL, WDL_TEST_JSON,
            FileType.WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        // Even though lambda doesn't exist, CHECK_URL_HELPER should catch an invalid URL
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, CHECK_URL_HELPER,
            DescriptorLanguage.WDL);
        assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkWdlDescriptorHasNoFileInputs() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_NO_INPUTS,
            FileType.DOCKSTORE_WDL, WDL_TEST_JSON,
            FileType.WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        // Even though lambda doesn't exist, code should fail before hand
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, CHECK_URL_HELPER,
            DescriptorLanguage.WDL);
        assertTrue(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkCwlDescriptorHasNoFileInputs() {
        final WorkflowVersion workflowVersion =
            setupWorkflowVersion(CWL_WITH_NO_FILE_INPUT, FileType.DOCKSTORE_CWL, CWL_TEST_JSON,
                FileType.CWL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        // Even though lambda doesn't exist, code should fail before hand
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, CHECK_URL_HELPER,
            DescriptorLanguage.CWL);
        assertTrue(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkCwlDescriptorWithFileInput() {
        final WorkflowVersion workflowVersion =
            setupWorkflowVersion(CWL_WITH_FILE_INPUT, FileType.DOCKSTORE_CWL, CWL_TEST_JSON,
                FileType.CWL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(UrlStatus.ALL_OPEN),
            DescriptorLanguage.CWL);
        assertTrue(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkCwlDescriptorWithLocationInput() {
        final WorkflowVersion workflowVersion =
            setupWorkflowVersion(CWL_WITH_FILE_INPUT, FileType.DOCKSTORE_CWL, CWL_TEST_JSON_WITH_LOCATION,
                FileType.CWL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(UrlStatus.ALL_OPEN),
            DescriptorLanguage.CWL);
        // allow location as a synonym for PATH
        assertTrue(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkCwlDescriptorWithFoobar() {
        final WorkflowVersion workflowVersion =
            setupWorkflowVersion(CWL_WITH_FILE_INPUT, FileType.DOCKSTORE_CWL, TEST_JSON_WITH_FOOBAR,
                FileType.CWL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(UrlStatus.ALL_OPEN),
            DescriptorLanguage.CWL);
        // just don't die
        assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkCwlDescriptorWithFileInputButNotInTestParam() {
        final String cwlTestJson = CWL_TEST_JSON.replace("bam_cram_file", "another_name");
        final WorkflowVersion workflowVersion =
            setupWorkflowVersion(CWL_WITH_FILE_INPUT, FileType.DOCKSTORE_CWL, cwlTestJson,
                FileType.CWL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(UrlStatus.ALL_OPEN),
            DescriptorLanguage.CWL);
        assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "there is no corresponding test parameter for the file input");
    }

    private CheckUrlInterface createCheckUrlInterface(final UrlStatus urlStatus) {
        return possibleUrls -> urlStatus;
    }

    private CheckUrlInterface createCheckUrlInterface(Set<String> openUrls) {
        return possibleUrls -> {
            if (possibleUrls.containsAll(openUrls)) {
                return UrlStatus.ALL_OPEN;
            }
            return UrlStatus.NOT_ALL_OPEN;
        };
    }

    private WorkflowVersion setupWorkflowVersion(final String descriptorContent,
        final FileType descriptorFileType, String jsonContent, final FileType testFileType) {
        WorkflowVersion workflowVersion = new WorkflowVersion();
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(testFileType);
        sourceFile.setContent(jsonContent);
        sourceFile.setAbsolutePath("/asdf.json");
        sourceFile.setPath("/asdf.json");
        workflowVersion.addSourceFile(sourceFile);
        String extension = descriptorFileType == FileType.DOCKSTORE_WDL ? "wdl" : "cwl";
        final String primaryDescriptorPath = "/Dockstore." + extension;
        workflowVersion.setWorkflowPath(primaryDescriptorPath);
        final SourceFile primaryDescriptor = new SourceFile();
        primaryDescriptor.setPath(primaryDescriptorPath);
        primaryDescriptor.setAbsolutePath(primaryDescriptorPath);
        primaryDescriptor.setType(descriptorFileType);
        primaryDescriptor.setContent(descriptorContent);
        workflowVersion.addSourceFile(primaryDescriptor);
        return workflowVersion;
    }
}
