/*
 * Copyright 2021 OICR and UCSC
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
import io.dockstore.webservice.helpers.CheckUrlHelper;
import io.dockstore.webservice.helpers.CheckUrlInterface;
import io.dockstore.webservice.helpers.CheckUrlInterface.UrlStatus;
import io.dockstore.webservice.resources.AbstractWorkflowResource;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CheckUrlHelperIT {

    private static final String WDL_TEST_JSON = """
        {
          "test.files": [
            "https://goodurl.com",
            "https://anothergoodurl.com"
          ],
          "test.file": "notaurl.file"
        }        
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
    private static final CheckUrlHelper CHECK_URL_HELPER =
        new CheckUrlHelper("https://url.doesnot.matter");

    @Test
    void checkUrlsFromLambdaGood() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_FILES_INPUT, WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(UrlStatus.ALL_OPEN),
            DescriptorLanguage.WDL);
        assertTrue(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkUrlsFromLambdaBad() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_FILES_INPUT, WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(UrlStatus.NOT_ALL_OPEN),
            DescriptorLanguage.WDL);
        assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkUrlsFromLambdaSomeBad() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(
            WDL_WITH_FILES_INPUT, WDL_TEST_JSON.replace("https://goodurl.com", "https://badUrl.com"));
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(UrlStatus.NOT_ALL_OPEN),
            DescriptorLanguage.WDL);
        assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkUrlsFromLambdaTerriblyWrong() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_FILES_INPUT, WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, createCheckUrlInterface(UrlStatus.UNKNOWN),
            DescriptorLanguage.WDL);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkLocalFileIsAParameter() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_FILE_INPUT, WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        // Even though lambda doesn't exist, CHECK_URL_HELPER should catch an invalid URL
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, CHECK_URL_HELPER,
            DescriptorLanguage.WDL);
        assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkDescriptorHasNoFileInputs() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_NO_INPUTS, WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        // Even though lambda doesn't exist, code should fail before hand
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, CHECK_URL_HELPER,
            DescriptorLanguage.WDL);
        assertTrue(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    private CheckUrlInterface createCheckUrlInterface(final UrlStatus urlStatus) {
        return new CheckUrlInterface() {
            @Override
            public UrlStatus checkUrls(final Set<String> possibleUrls) {
                return urlStatus;
            }
        };
    }

    private WorkflowVersion setupWorkflowVersion(final String descriptorContent, String jsonContent) {
        WorkflowVersion workflowVersion = new WorkflowVersion();
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(FileType.WDL_TEST_JSON);
        sourceFile.setContent(jsonContent);
        sourceFile.setAbsolutePath("/asdf.json");
        sourceFile.setPath("/asdf.json");
        workflowVersion.addSourceFile(sourceFile);
        final String primaryDescriptorPath = "/Dockstore.wdl";
        workflowVersion.setWorkflowPath(primaryDescriptorPath);
        final SourceFile primaryDescriptor = new SourceFile();
        primaryDescriptor.setPath(primaryDescriptorPath);
        primaryDescriptor.setAbsolutePath(primaryDescriptorPath);
        primaryDescriptor.setType(FileType.DOCKSTORE_WDL);
        primaryDescriptor.setContent(descriptorContent);
        workflowVersion.addSourceFile(primaryDescriptor);
        return workflowVersion;
    }
}
