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
        return new CheckUrlInterface() {
            @Override
            public UrlStatus checkUrls(final Set<String> possibleUrls) {
                return urlStatus;
            }
        };
    }

    private CheckUrlInterface createCheckUrlInterface(Set<String> openUrls) {
        return new CheckUrlInterface() {
            @Override
            public UrlStatus checkUrls(final Set<String> possibleUrls) {
                if (possibleUrls.containsAll(openUrls)) {
                    return UrlStatus.ALL_OPEN;
                }
                return UrlStatus.NOT_ALL_OPEN;
            }
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
