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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.BaseIT.TestStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * @author jpatricia on 04/07/16.
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
public class ToolsWorkflowTestIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
    }

    private List<String> getJSON(String repo, String fileName, String descType, String branch) throws ApiException {
        final String testWorkflowName = "test-workflow";
        WorkflowsApi workflowApi = new WorkflowsApi(getWebClient(USER_1_USERNAME, testingPostgres));
        Workflow githubWorkflow = workflowApi.manualRegister("github", repo, fileName, testWorkflowName, descType, "/test.json");

        // This checks if a workflow whose default name was manually registered as test-workflow remains as test-workflow and not null or empty string
        assertEquals(testWorkflowName, githubWorkflow.getWorkflowName());

        // Publish github workflow
        Workflow refresh = workflowApi.refresh(githubWorkflow.getId(), false);

        // This checks if a workflow whose default name is test-workflow remains as test-workflow and not null or empty string after refresh
        assertEquals(testWorkflowName, refresh.getWorkflowName());

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

        assertTrue(strings.size() > 0, "JSON should not be blank");
        assertEquals(3, countNode, "JSON should have tools with docker images, has " + countNode);
        assertFalse(strings.get(0).contains("untar"), "tool should not have untar since it has no docker image");
        assertTrue(strings.get(0).contains("compile"), "tool should have compile as id");
        assertTrue(strings.get(0).contains(
            "\"id\":\"compile\"," + "\"file\":\"arguments.cwl\"," + "\"docker\":\"java:7\",\"link\":\"https://hub.docker.com/_/java\""),
            "compile docker and link should not be blank" + strings.get(0));
        assertTrue(strings.get(0).contains(
            "\"id\":\"wrkflow.wc\"," + "\"file\":\"wc.cwl\"," + "\"docker\":\"java:7\",\"link\":\"https://hub.docker.com/_/java\""),
            "workflow.wc docker and link should not be blank" + strings.get(0));
        assertTrue(strings.get(0).contains(
            "\"id\":\"wrkflow.grep\"," + "\"file\":\"grep.cwl\"," + "\"docker\":\"java:7\",\"link\":\"https://hub.docker.com/_/java\""),
            "workflow.grep docker and link should not be blank" + strings.get(0));
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

        assertTrue(strings.size() > 0, "JSON should not be blank");
        assertEquals(2, countNode, "JSON should have two tools");
        assertTrue(strings.get(0).contains("hello"), "tool should have hello as id");
        assertTrue(strings.get(0).contains(
            "\"id\":\"hello\"," + "\"file\":\"/hello.wdl\"," + "\"docker\":\"ubuntu:latest\","
                + "\"link\":\"https://hub.docker.com/_/ubuntu\""), "hello docker and link should not be blank");

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

        assertTrue(strings.size() > 0, "JSON should not be blank");
        assertEquals(0, countNode, "JSON should have no tools");
        assertFalse(strings.get(0).contains("\"id\":\"ps\""), "ps should not exist");
        assertFalse(strings.get(0).contains("\"id\":\"cgrep\","), "cgrep should not exist");
        assertFalse(strings.get(0).contains("\"id\":\"wc\""), "wc should not exist");

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
        assertEquals(1, strings.size(), "JSON should not have any data tools");
    }

    @Test
    @Disabled("This test will fail as long as we are not using validation on WDL workflows and are assuming that if the file exists it is valid")
    public void testWorkflowToolWDLMissingTask() throws ApiException {
        // Input: hello.wdl
        // Repo: test_workflow_wdl
        // Branch: missing_docker
        // Test: task called by workflow not found in the file
        // Return: blank JSON

        final List<String> strings = getJSON("DockstoreTestUser2/test_workflow_wdl", "/hello.wdl", "wdl", "missing_docker");

        //JSON will have no content at all
        assertEquals(0, strings.size(), "JSON should be blank");
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

        assertTrue(strings.size() > 0, "JSON should not be blank");
        assertEquals(1, countNode, "JSON should have one tool");
        assertTrue(strings.get(0).contains("compile"), "tool data should have compile as id");
        assertTrue(strings.get(0).contains(
            "\"id\":\"compile\"," + "\"file\":\"arguments.cwl\"," + "\"docker\":\"java:7\","
                + "\"link\":\"https://hub.docker.com/_/java\""), "compile docker and link should use default docker req from workflow");
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

        assertTrue(strings.size() > 0, "JSON should not be blank");
        assertEquals(5, countNode, "JSON should have 5 tools");
        assertTrue(strings.get(0).contains("pass_filter"), "tool data should have pass_filter as id");
        assertTrue(strings.get(0).contains("merge_vcfs"), "tool data should have merge_vcfs as id");
        assertTrue(strings.get(0).contains(
            "\"id\":\"pass_filter\"," + "\"file\":\"pass-filter.cwl\"," + "\"docker\":\"pancancer/pcawg-oxog-tools\","
                + "\"link\":\"https://hub.docker.com/r/pancancer/pcawg-oxog-tools\""), "pass_filter should have docker link");
        assertTrue(strings.get(0).contains(
            "\"id\":\"merge_vcfs\"," + "\"file\":\"vcf_merge.cwl\"," + "\"docker\":\"pancancer/pcawg-oxog-tools\","
                + "\"link\":\"https://hub.docker.com/r/pancancer/pcawg-oxog-tools\""), "merge_vcfs should have docker link");
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

        assertTrue(strings.size() > 0, "JSON should not be blank");
        assertEquals(5, countNode, "JSON should have 5 tools");
        assertTrue(strings.get(0).contains("pass_filter"), "tool data should have pass_filter as id");
        assertTrue(strings.get(0).contains("merge_vcfs"), "tool data should have merge_vcfs as id");
        assertTrue(strings.get(0).contains(
            "\"id\":\"pass_filter\"," + "\"file\":\"pass-filter.cwl\"," + "\"docker\":\"quay.io/pancancer/pcawg-oxog-tools\","
                + "\"link\":\"https://quay.io/repository/pancancer/pcawg-oxog-tools\""), "pass_filter should not have docker link");
        assertTrue(strings.get(0).contains(
            "\"id\":\"merge_vcfs\"," + "\"file\":\"vcf_merge.cwl\"," + "\"docker\":\"pancancer/pcawg-oxog-tools\","
                + "\"link\":\"https://hub.docker.com/r/pancancer/pcawg-oxog-tools\""), "merge_vcfs should not have docker link");
    }
}
