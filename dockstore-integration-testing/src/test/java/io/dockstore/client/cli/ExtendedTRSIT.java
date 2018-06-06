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

package io.dockstore.client.cli;

import java.util.Map;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.SourceControl;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ExtendedGa4GhApi;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;
import io.swagger.client.model.Workflow;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

/**
 * Extra confidential integration tests, focuses on proposed GA4GH extensions
 * {@link BaseIT}
 * @author dyuen
 */
@Category(ConfidentialTest.class)
public class ExtendedTRSIT extends BaseIT {

    private static final String DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore_workflow_cnv";

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown= ExpectedException.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }


    @Test
    public void testVerificationOnSourceFileLevel() throws ApiException {
        final ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        String defaultTestParameterFilePath = "/test.json";
        String id = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv";
        String awesomePlatform = "awesome platform";
        String crummyPlatform = "crummy platform";


        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", defaultTestParameterFilePath);
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW);

        // refresh and publish the workflow
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());
        workflowApi.publish(workflow.getId(), new PublishRequest(){
            public Boolean isPublish() { return true;}
        });

        // check on URLs for workflows via ga4gh calls
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        // try to add verification metadata
        Map<String, Object> stringObjectMap = extendedGa4GhApi
            .toolsIdVersionsVersionIdTypeTestsPost("CWL", id, "master",
                defaultTestParameterFilePath, awesomePlatform, "metadata", true);
        Assert.assertEquals(1, stringObjectMap.size());
        stringObjectMap = extendedGa4GhApi
            .toolsIdVersionsVersionIdTypeTestsPost("CWL", id, "master",
                defaultTestParameterFilePath, crummyPlatform, "metadata", true);
        Assert.assertEquals(2, stringObjectMap.size());

        // assert some things about map structure
        Assert.assertTrue("verification information seems off", stringObjectMap.containsKey(awesomePlatform) && stringObjectMap.containsKey(
            crummyPlatform) && stringObjectMap.get(
            awesomePlatform) instanceof java.util.Map && stringObjectMap.get(crummyPlatform) instanceof java.util.Map && ((Map)stringObjectMap.get(
            awesomePlatform)).size() == 2 && ((Map)stringObjectMap.get(awesomePlatform)).get("metadata").equals("metadata")
        );

        // verification on a sourcefile level should flow up to to version and entry level
        Ga4GhApi api = new Ga4GhApi(webClient);
        Tool tool = api.toolsIdGet(id);
        Assert.assertTrue("verification states do not seem to flow up", tool.isVerified() && tool.getVersions().stream().allMatch(ToolVersion::isVerified));

        // try to remove verification metadata
        extendedGa4GhApi
            .toolsIdVersionsVersionIdTypeTestsPost("CWL", id, "master",
                defaultTestParameterFilePath, awesomePlatform, "metadata", null);
        stringObjectMap = extendedGa4GhApi
            .toolsIdVersionsVersionIdTypeTestsPost("CWL", id, "master",
                defaultTestParameterFilePath, crummyPlatform, "metadata", null);
        Assert.assertEquals(0, stringObjectMap.size());
    }
}
