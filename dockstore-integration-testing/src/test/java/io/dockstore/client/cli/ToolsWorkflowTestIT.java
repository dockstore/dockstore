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

package io.dockstore.client.cli;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import com.google.common.io.Resources;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.IntegrationTest;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;


/**
 * @author jpatricia on 04/07/16.
 */
@Category({ConfidentialTest.class, IntegrationTest.class})
public class ToolsWorkflowTestIT extends BaseIT {

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT);
    }

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    private WorkflowsApi setupWebService() throws IOException, TimeoutException, ApiException {
        ApiClient webClient = WorkflowIT.getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);

        // Get workflows
        usersApi.refreshWorkflows(userId);

        return workflowApi;
    }

    private List<String> getJSON(String repo, String fileName, String descType, String branch)
            throws IOException, TimeoutException, ApiException {
        final String TEST_WORKFLOW_NAME = "test-workflow";
        WorkflowsApi workflowApi = setupWebService();
        Workflow githubWorkflow = workflowApi.manualRegister("github", repo, fileName, TEST_WORKFLOW_NAME, descType, "/test.json");

        // This checks if a workflow whose default name was manually registered as test-workflow remains as test-workflow and not null or empty string
        Assert.assertTrue(githubWorkflow.getWorkflowName().equals(TEST_WORKFLOW_NAME));

        // Publish github workflow
        Workflow refresh = workflowApi.refresh(githubWorkflow.getId());

        // This checks if a workflow whose default name is test-workflow remains as test-workflow and not null or empty string after refresh
        Assert.assertTrue(refresh.getWorkflowName().equals(TEST_WORKFLOW_NAME));

        Optional<WorkflowVersion> master = refresh.getWorkflowVersions().stream().filter(workflow -> workflow.getName().equals(branch))
                .findFirst();

        //getting the dag json string
        final String basePath = WorkflowIT.getWebClient().getBasePath();
        URL url = new URL(basePath + "/workflows/" + githubWorkflow.getId() + "/tools/" + master.get().getId());
        List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));

        return strings;
    }

    private int countToolInJSON(List<String> strings) {
        //count the number of nodes in the DAG json
        int countTool = 0;
        int last = 0;
        String tool = "id";
        if (strings.size() > 0) {
            while (last != -1) {
                last = strings.get(0).indexOf(tool, last);

                if (last != -1) {
                    countTool++;
                    last += tool.length();
                }
            }
        }

        return countTool;
    }

    @Test
    public void testWorkflowToolCWL() throws IOException, TimeoutException, ApiException {
        // Input: 1st-workflow.cwl
        // Repo: test_workflow_cwl
        // Branch: master
        // Test: normal cwl workflow DAG
        // Return: JSON string with two tools, only one tool has a docker requirement

        final List<String> strings = getJSON("DockstoreTestUser2/test_workflow_cwl", "/1st-workflow.cwl", "cwl", "master");
        int countNode = countToolInJSON(strings);

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have one tool with docker image, has " + countNode, countNode, 1);
        Assert.assertTrue("tool should not have untar since it has no docker image", !strings.get(0).contains("untar"));
        Assert.assertTrue("tool should have compile as id", strings.get(0).contains("compile"));
        Assert.assertTrue("compile docker and link should not be blank" + strings.get(0), strings.get(0).contains(
                "\"id\":\"compile\"," + "\"file\":\"arguments.cwl\"," + "\"docker\":\"java:7\","
                        + "\"link\":\"https://hub.docker.com/_/java\""));

    }

    @Test
    public void testWorkflowToolWDLSingleNode() throws IOException, TimeoutException, ApiException {
        // Input: hello.wdl
        // Repo: test_workflow_wdl
        // Branch: master
        // Test: normal wdl workflow DAG, single node
        // Return: JSON string with one tool with docker requirement

        final List<String> strings = getJSON("DockstoreTestUser2/test_workflow_wdl", "/hello.wdl", "wdl", "master");
        int countNode = countToolInJSON(strings);

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have two tools", countNode, 2);
        Assert.assertTrue("tool should have hello as id", strings.get(0).contains("hello"));
        Assert.assertTrue("hello docker and link should not be blank", strings.get(0).contains(
                "\"id\":\"hello\"," + "\"file\":\"/hello.wdl\"," + "\"docker\":\"ubuntu:latest\","
                        + "\"link\":\"https://hub.docker.com/_/ubuntu\""));

    }

    @Test
    public void testWorkflowToolWDLMultipleNodes() throws IOException, TimeoutException, ApiException {
        // Input: hello.wdl
        // Repo: hello-dockstore-workflow
        // Branch: master
        // Test: normal wdl workflow DAG, multiple nodes
        // Return: JSON string with three tools and all of them have no docker requirement

        final List<String> strings = getJSON("DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.wdl", "wdl", "testWDL");
        int countNode = countToolInJSON(strings);

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have no tools", countNode, 0);
        Assert.assertTrue("ps should not exist", !strings.get(0).contains("\"id\":\"ps\""));
        Assert.assertTrue("cgrep should not exist", !strings.get(0).contains("\"id\":\"cgrep\","));
        Assert.assertTrue("wc should not exist", !strings.get(0).contains("\"id\":\"wc\""));

    }

    @Test
    public void testWorkflowToolCWLMissingTool() throws IOException, TimeoutException, ApiException {
        // Input: Dockstore.cwl
        // Repo: hello-dockstore-workflow
        // Branch: testCWL
        // Test: Repo does not have required tool files called by workflow file
        // Return: JSON string blank array

        final List<String> strings = getJSON("DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.cwl", "cwl", "testCWL");

        //JSON will have node:[] and edges:[]
        Assert.assertEquals("JSON should not have any data tools", strings.size(), 1);
    }

    @Test
    @Ignore("This test will fail as long as we are not using validation on WDL workflows and are assuming that if the file exists it is valid")
    public void testWorkflowToolWDLMissingTask() throws IOException, TimeoutException, ApiException {
        // Input: hello.wdl
        // Repo: test_workflow_wdl
        // Branch: missing_docker
        // Test: task called by workflow not found in the file
        // Return: blank JSON

        final List<String> strings = getJSON("DockstoreTestUser2/test_workflow_wdl", "/hello.wdl", "wdl", "missing_docker");

        //JSON will have no content at all
        Assert.assertEquals("JSON should be blank", strings.size(), 0);
    }

    @Test
    public void testToolImportAndIncludeSyntax() throws IOException, TimeoutException, ApiException {
        // Input: Dockstore.cwl
        // Repo: dockstore-whalesay-imports
        // Branch: master
        // Test: "run: {import:.....}"
        // Return: JSON string contains the two tools, both have docker requirement

        final List<String> strings = getJSON("DockstoreTestUser2/dockstore-whalesay-imports", "/Dockstore.cwl", "cwl", "master");
        int countNode = countToolInJSON(strings);

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have one tool", countNode, 1);
        Assert.assertTrue("tool data should have compile as id", strings.get(0).contains("compile"));
        Assert.assertTrue("compile docker and link should use default docker req from workflow", strings.get(0).contains(
                "\"id\":\"compile\"," + "\"file\":\"arguments.cwl\"," + "\"docker\":\"java:7\","
                        + "\"link\":\"https://hub.docker.com/_/java\""));
    }

    @Test
    public void testToolCWL1Syntax() throws IOException, TimeoutException, ApiException {
        // Input: preprocess_vcf.cwl
        // Repo: OxoG-Dockstore-Tools
        // Branch: develop
        // Test: "[pass_filter -> [inputs: ...., outputs: ....]] instead of [id->pass_filter,inputs->....]"
        // Return: JSON string contains five tools, all have docker requirement, but no link to it since the link is invalid

        final List<String> strings = getJSON("DockstoreTestUser2/OxoG-Dockstore-Tools", "/preprocess_vcf.cwl", "cwl", "develop");
        int countNode = countToolInJSON(strings);

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have 5 tools", countNode, 5);
        Assert.assertTrue("tool data should have pass_filter as id", strings.get(0).contains("pass_filter"));
        Assert.assertTrue("tool data should have merge_vcfs as id", strings.get(0).contains("merge_vcfs"));
        Assert.assertTrue("pass_filter should have docker link", strings.get(0).contains(
                "\"id\":\"pass_filter\"," + "\"file\":\"pass-filter.cwl\"," + "\"docker\":\"pancancer/pcawg-oxog-tools\","
                        + "\"link\":\"https://hub.docker.com/r/pancancer/pcawg-oxog-tools\""));
        Assert.assertTrue("merge_vcfs should have docker link", strings.get(0).contains(
                "\"id\":\"merge_vcfs\"," + "\"file\":\"vcf_merge.cwl\"," + "\"docker\":\"pancancer/pcawg-oxog-tools\","
                        + "\"link\":\"https://hub.docker.com/r/pancancer/pcawg-oxog-tools\""));
    }

    @Test
    public void testToolCWL1SyntaxCorrectLink() throws IOException, TimeoutException, ApiException {
        // Input: preprocess_vcf.cwl
        // Repo: OxoG-Dockstore-Tools
        // Branch: correct_docker_link
        // Test: "pass_filter should have a link to quay repo"
        // Return: JSON string contains five tools, all have docker requirement, but no link to it since the link is invalid

        final List<String> strings = getJSON("DockstoreTestUser2/OxoG-Dockstore-Tools", "/preprocess_vcf.cwl", "cwl",
                "correct_docker_link");
        int countNode = countToolInJSON(strings);

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have 5 tools", countNode, 5);
        Assert.assertTrue("tool data should have pass_filter as id", strings.get(0).contains("pass_filter"));
        Assert.assertTrue("tool data should have merge_vcfs as id", strings.get(0).contains("merge_vcfs"));
        Assert.assertTrue("pass_filter should not have docker link", strings.get(0).contains(
                "\"id\":\"pass_filter\"," + "\"file\":\"pass-filter.cwl\"," + "\"docker\":\"quay.io/pancancer/pcawg-oxog-tools\","
                        + "\"link\":\"https://quay.io/repository/pancancer/pcawg-oxog-tools\""));
        Assert.assertTrue("merge_vcfs should not have docker link", strings.get(0).contains(
                "\"id\":\"merge_vcfs\"," + "\"file\":\"vcf_merge.cwl\"," + "\"docker\":\"pancancer/pcawg-oxog-tools\","
                        + "\"link\":\"https://hub.docker.com/r/pancancer/pcawg-oxog-tools\""));
    }
}
