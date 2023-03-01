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

import static io.dockstore.common.Hoverfly.CHECK_URL_SOURCE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.resources.AbstractWorkflowResource;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyMode;
import io.specto.hoverfly.junit5.HoverflyExtension;
import io.specto.hoverfly.junit5.api.HoverflyConfig;
import io.specto.hoverfly.junit5.api.HoverflyCore;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(HoverflyExtension.class)
@HoverflyCore(mode = HoverflyMode.SIMULATE, config = @HoverflyConfig(destination = CheckUrlHelperIT.FAKE_CHECK_URL_LAMBDA_BASE_URL))
class CheckUrlHelperIT {

    public static final String FAKE_CHECK_URL_LAMBDA_BASE_URL = "http://fakecheckurllambdabaseurl:3000";

    public static final String WDL_TEST_JSON = """
        {
          "test.files": [
            "https://goodurl.com",
            "https://anothergoodurl.com"
          ],
          "test.file": "notaurl.file"
        }        
        """;

    public static final String WDL_WITH_FILES_INPUT = """
            version 1.0
            workflow test {
              input {
                Array[File] files
              }
            }
            """;

    public static final String WDL_WITH_FILE_INPUT = """
            version 1.0
            workflow test {
              input {
                File file
              }
            }
        """;

    public static final String WDL_WITH_NO_INPUTS = """
        version 1.0
        workflow test {
        }
        """;

    @Test
    void checkUrlsFromLambdaGood(Hoverfly hoverfly) throws IOException {
        hoverfly.simulate(CHECK_URL_SOURCE);
        Map<String, String> state = new HashMap<>();
        state.put("status", "good");
        hoverfly.setState(state);
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_FILES_INPUT, WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, FAKE_CHECK_URL_LAMBDA_BASE_URL + "/lambda",
            DescriptorLanguage.WDL);
        assertTrue(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkUrlsFromLambdaBad(Hoverfly hoverfly) throws IOException {
        hoverfly.simulate(CHECK_URL_SOURCE);
        Map<String, String> state = new HashMap<>();
        state.put("status", "bad");
        hoverfly.setState(state);
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_FILES_INPUT, WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, FAKE_CHECK_URL_LAMBDA_BASE_URL + "/lambda",
            DescriptorLanguage.WDL);
        assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkUrlsFromLambdaSomeBad(Hoverfly hoverfly) throws IOException {
        hoverfly.simulate(CHECK_URL_SOURCE);
        Map<String, String> state = new HashMap<>();
        state.put("status", "someGoodSomeBad");
        hoverfly.setState(state);
        WorkflowVersion workflowVersion = setupWorkflowVersion(
            WDL_WITH_FILES_INPUT, WDL_TEST_JSON.replace("https://goodurl.com", "https://badUrl.com"));
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, FAKE_CHECK_URL_LAMBDA_BASE_URL + "/lambda",
            DescriptorLanguage.WDL);
        assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkUrlsFromLambdaTerriblyWrong(Hoverfly hoverfly) throws IOException {
        hoverfly.simulate(CHECK_URL_SOURCE);
        Map<String, String> state = new HashMap<>();
        state.put("status", "terriblyWrong");
        hoverfly.setState(state);
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_FILES_INPUT, WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, FAKE_CHECK_URL_LAMBDA_BASE_URL + "/lambda",
            DescriptorLanguage.WDL);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkLocalFileIsAParameter() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_FILE_INPUT, WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, FAKE_CHECK_URL_LAMBDA_BASE_URL + "/lambda",
            DescriptorLanguage.WDL);
        assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    void checkDescriptorHasNoFileInputs() {
        WorkflowVersion workflowVersion = setupWorkflowVersion(WDL_WITH_NO_INPUTS, WDL_TEST_JSON);
        assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile(), "Double-check that it's not originally true/false");
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, FAKE_CHECK_URL_LAMBDA_BASE_URL + "/lambda",
            DescriptorLanguage.WDL);
        assertTrue(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
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
