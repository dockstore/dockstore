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
import static io.specto.hoverfly.junit.core.HoverflyConfig.localConfigs;

import com.google.api.client.util.Charsets;
import com.google.common.io.Files;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.resources.AbstractWorkflowResource;
import io.dropwizard.testing.ResourceHelpers;
import io.specto.hoverfly.junit.rule.HoverflyRule;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

public class CheckUrlHelperIT {

    public static String fakeCheckUrlLambdaBaseURL = "http://fakecheckurllambdabaseurl:3000";

    @ClassRule
    public static HoverflyRule hoverflyRule = HoverflyRule.inSimulationMode(CHECK_URL_SOURCE, localConfigs().destination(fakeCheckUrlLambdaBaseURL));

    @Test
    public void checkUrlsFromLambdaGood() throws IOException {
        Map<String, String> state = new HashMap<>();
        state.put("status", "good");
        hoverflyRule.setState(state);
        File file = new File(ResourceHelpers.resourceFilePath("testArrayHttpInputLocalOutput.json"));
        String s = Files.asCharSource(file, Charsets.UTF_8).read();
        WorkflowVersion workflowVersion = setupWorkflowVersion(s);
        Assert.assertNull("Double-check that it's not originally true/false",
            workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, fakeCheckUrlLambdaBaseURL + "/lambda");
        Assert.assertTrue(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    public void checkUrlsFromLambdaBad() throws IOException {
        Map<String, String> state = new HashMap<>();
        state.put("status", "bad");
        hoverflyRule.setState(state);
        File file = new File(ResourceHelpers.resourceFilePath("testArrayHttpInputLocalOutput.json"));
        String s = Files.asCharSource(file, Charsets.UTF_8).read();
        WorkflowVersion workflowVersion = setupWorkflowVersion(s);
        Assert.assertNull("Double-check that it's not originally true/false",
            workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, fakeCheckUrlLambdaBaseURL + "/lambda");
        Assert.assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    public void checkUrlsFromLambdaSomeBad() throws IOException {
        Map<String, String> state = new HashMap<>();
        state.put("status", "someGoodSomeBad");
        hoverflyRule.setState(state);
        File file = new File(ResourceHelpers.resourceFilePath("someGoodSomeBad.json"));
        String s = Files.asCharSource(file, Charsets.UTF_8).read();
        WorkflowVersion workflowVersion = setupWorkflowVersion(s);
        Assert.assertNull("Double-check that it's not originally true/false",
            workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, fakeCheckUrlLambdaBaseURL + "/lambda");
        Assert.assertFalse(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    @Test
    public void checkUrlsFromLambdaTerriblyWrong() throws IOException {
        Map<String, String> state = new HashMap<>();
        state.put("status", "terriblyWrong");
        hoverflyRule.setState(state);
        File file = new File(ResourceHelpers.resourceFilePath("someGoodSomeBad.json"));
        String s = Files.asCharSource(file, Charsets.UTF_8).read();
        WorkflowVersion workflowVersion = setupWorkflowVersion(s);
        Assert.assertNull("Double-check that it's not originally true/false",
            workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
        AbstractWorkflowResource.publicAccessibleUrls(workflowVersion, fakeCheckUrlLambdaBaseURL + "/lambda");
        Assert.assertNull(workflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());
    }

    private WorkflowVersion setupWorkflowVersion(String fileContents) {
        WorkflowVersion workflowVersion = new WorkflowVersion();
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(FileType.CWL_TEST_JSON);
        sourceFile.setContent(fileContents);
        sourceFile.setAbsolutePath("/asdf.json");
        workflowVersion.addSourceFile(sourceFile);
        return workflowVersion;
    }
}
