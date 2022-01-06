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

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.WorkflowTest;
import io.swagger.client.ApiException;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
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
@Category({ ConfidentialTest.class, WorkflowTest.class })
public class ToolsWorkflowTestIT extends BaseIT {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT);
    }

    private List<String> getJSON(String repo, String fileName, String descType, String branch) throws ApiException {
        final String testWorkflowName = "test-workflow";
        WorkflowsApi workflowApi = new WorkflowsApi(getWebClient(USER_1_USERNAME, testingPostgres));
        Workflow githubWorkflow = workflowApi.manualRegister("github", repo, fileName, testWorkflowName, descType, "/test.json");

        // This checks if a workflow whose default name was manually registered as test-workflow remains as test-workflow and not null or empty string
        Assert.assertEquals(githubWorkflow.getWorkflowName(), testWorkflowName);

        // Publish github workflow
        Workflow refresh = workflowApi.refresh(githubWorkflow.getId(), false);

        // This checks if a workflow whose default name is test-workflow remains as test-workflow and not null or empty string after refresh
        Assert.assertEquals(refresh.getWorkflowName(), testWorkflowName);

        Optional<WorkflowVersion> master = refresh.getWorkflowVersions().stream().filter(workflow -> workflow.getName().equals(branch))
            .findFirst();

        //getting the dag json string
        return Lists.newArrayList(workflowApi.getTableToolContent(githubWorkflow.getId(), master.get().getId()));
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
    public void testWorkflowToolCWL() throws IOException, ApiException {
        // https://github.com/DockstoreTestUser2/test_workflow_cwl
        // Input: 1st-workflow.cwl
        // Repo: test_workflow_cwl
        // Branch: master
        // Test: normal cwl workflow DAG
        // Return: JSON string with three tools, in arguments.cwl, grep.cwl, and wc.cwl

        final List<String> strings = getJSON("DockstoreTestUser2/test_workflow_cwl", "/1st-workflow.cwl", "cwl", "master");
        int countNode = countToolInJSON(strings);

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have three tools with docker images, has " + countNode, 3, countNode);
        Assert.assertFalse("tool should not have untar since it has no docker image", strings.get(0).contains("untar"));
        Assert.assertTrue("tool should have compile as id", strings.get(0).contains("compile"));
        Assert.assertTrue("compile docker and link should not be blank" + strings.get(0), strings.get(0).contains(
            "\"id\":\"compile\"," + "\"file\":\"arguments.cwl\"," + "\"docker\":\"java:7\","
                + "\"link\":\"https://hub.docker.com/_/java\""));
        Assert.assertTrue("compile docker and link should not be blank" + strings.get(0), strings.get(0).contains(
                "\"id\":\"wrkflow\"," + "\"file\":\"grep-and-count.cwl\"," + "\"docker\":\"java:7\","
                        + "\"link\":\"https://hub.docker.com/_/java\""));

    }

    @Test
    public void testWorkflowToolWDLSingleNode() throws IOException, ApiException {
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
    public void testWorkflowToolWDLMultipleNodes() throws IOException, ApiException {
        // Input: hello.wdl
        // Repo: hello-dockstore-workflow
        // Branch: master
        // Test: normal wdl workflow DAG, multiple nodes
        // Return: JSON string with three tools and all of them have no docker requirement

        final List<String> strings = getJSON("DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.wdl", "wdl", "testWDL");
        int countNode = countToolInJSON(strings);

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have no tools", countNode, 0);
        Assert.assertFalse("ps should not exist", strings.get(0).contains("\"id\":\"ps\""));
        Assert.assertFalse("cgrep should not exist", strings.get(0).contains("\"id\":\"cgrep\","));
        Assert.assertFalse("wc should not exist", strings.get(0).contains("\"id\":\"wc\""));

    }

    @Test
    public void testWorkflowToolCWLMissingTool() throws IOException, ApiException {
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
    public void testWorkflowToolWDLMissingTask() throws ApiException {
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
    public void testToolImportAndIncludeSyntax() throws IOException, ApiException {
        // Input: Dockstore.cwl
        // Repo: dockstore-whalesay-imports
        // Branch: master
        // Test: "run: {$import:.....}"
        // Return: JSON string contains the two tools, both have docker requirement

        final List<String> strings = getJSON("DockstoreTestUser2/dockstore-whalesay-imports", "/Dockstore.cwl", "cwl", "update-to-valid-cwl");
        int countNode = countToolInJSON(strings);

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have one tool", 1, countNode);
        Assert.assertTrue("tool data should have compile as id", strings.get(0).contains("compile"));
        Assert.assertTrue("compile docker and link should use default docker req from workflow", strings.get(0).contains(
            "\"id\":\"compile\"," + "\"file\":\"arguments.cwl\"," + "\"docker\":\"java:7\","
                + "\"link\":\"https://hub.docker.com/_/java\""));
    }

    @Test
    public void testToolCWL1Syntax() throws IOException, ApiException {
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
    public void testToolCWL1SyntaxCorrectLink() throws IOException, ApiException {
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
