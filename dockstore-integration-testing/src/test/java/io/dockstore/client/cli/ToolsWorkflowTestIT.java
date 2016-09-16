/*
 *    Copyright 2016 OICR
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

import com.google.common.io.Resources;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemOutRule;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static io.dockstore.common.CommonTestUtilities.clearStateMakePrivate2;

/**
 * Created by jpatricia on 04/07/16.
 */
public class ToolsWorkflowTestIT {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstoreTest.yml"));

    @Before
    public void clearDBandSetup() throws IOException, TimeoutException, ApiException {
        clearStateMakePrivate2();
    }

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    private WorkflowsApi setupWebService() throws IOException, TimeoutException, ApiException{
        ApiClient webClient = WorkflowET.getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublish(true);

        // Get workflows
        usersApi.refreshWorkflows(userId);

        return workflowApi;
    }

    private List<String> getJSON(String repo, String fileName, String descType, String branch) throws IOException, TimeoutException, ApiException{
        WorkflowsApi workflowApi = setupWebService();
        Workflow githubWorkflow = workflowApi.manualRegister("github", repo, fileName, "test-workflow", descType);

        // Publish github workflow
        Workflow refresh = workflowApi.refresh(githubWorkflow.getId());
        Optional<WorkflowVersion> master = refresh.getWorkflowVersions().stream().filter(workflow -> workflow.getName().equals(branch)).findFirst();

        //getting the dag json string
        final String basePath = WorkflowET.getWebClient().getBasePath();
        URL url = new URL(basePath + "/workflows/" +githubWorkflow.getId()+"/tools/" + master.get().getId() );
        List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));

        return strings;
    }

    private int countToolInJSON(List<String> strings){
        //count the number of nodes in the DAG json
        int countTool = 0;
        int last = 0;
        String tool = "id";
        while(last !=-1){
            last = strings.get(0).indexOf(tool,last);

            if(last !=-1){
                countTool++;
                last += tool.length();
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
        Assert.assertEquals("JSON should have two tools", countNode, 2);
        Assert.assertTrue("tool should have untar as id", strings.get(0).contains("untar"));
        Assert.assertTrue("tool should have compile as id", strings.get(0).contains("compile"));
        Assert.assertTrue("untar docker and link should be blank", strings.get(0).contains("\"id\":\"untar\","+
                "\"file\":\"tar-param.cwl\","+
                "\"docker\":\"Not Specified\"," +
                "\"link\":\"Not Specified\""));
        Assert.assertTrue("compile docker and link should not be blank", strings.get(0).contains("\"id\":\"compile\"," +
                "\"file\":\"arguments.cwl\","+
                "\"docker\":\"java:7\"," +
                "\"link\":\"https://hub.docker.com/_/java\""));

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
        Assert.assertEquals("JSON should have one tool", countNode,1);
        Assert.assertTrue("tool should have hello as id", strings.get(0).contains("hello"));
        Assert.assertTrue("hello docker and link should not be blank", strings.get(0).contains("\"id\":\"hello\","+
                "\"docker\":\"ubuntu:latest\","+
                "\"link\":\"https://hub.docker.com/_/ubuntu\""));

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
        Assert.assertEquals("JSON should have three tools", countNode,3);
        Assert.assertTrue("tool should have ps as id", strings.get(0).contains("ps"));
        Assert.assertTrue("tool should have cgrep as id", strings.get(0).contains("cgrep"));
        Assert.assertTrue("tool should have have wc as id", strings.get(0).contains("wc"));
        Assert.assertTrue("ps docker and link should be blank", strings.get(0).contains("\"id\":\"ps\","+
                "\"docker\":\"Not Specified\","+
                "\"link\":\"Not Specified\""));
        Assert.assertTrue("cgrep docker and link should be blank", strings.get(0).contains("\"id\":\"cgrep\","+
                "\"docker\":\"Not Specified\","+
                "\"link\":\"Not Specified\""));
        Assert.assertTrue("wc docker and link should be blank", strings.get(0).contains("\"id\":\"wc\","+
                "\"docker\":\"Not Specified\","+
                "\"link\":\"Not Specified\""));

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
        Assert.assertEquals("JSON should not have any data tools", strings.size(),1);
    }

    @Test
    public void testWorkflowToolWDLMissingTask() throws IOException, TimeoutException, ApiException {
        // Input: hello.wdl
        // Repo: test_workflow_wdl
        // Branch: missing_docker
        // Test: task called by workflow not found in the file
        // Return: blank JSON

        final List<String> strings = getJSON("DockstoreTestUser2/test_workflow_wdl", "/hello.wdl", "wdl", "missing_docker");

        //JSON will have no content at all
        Assert.assertEquals("JSON should be blank", strings.size(),0);
    }

    @Test
    public void testToolImportSyntax() throws IOException, TimeoutException, ApiException {
        // Input: Dockstore.cwl
        // Repo: dockstore-whalesay-imports
        // Branch: master
        // Test: "run: {import:.....}"
        // Return: JSON string contains the two tools, both have docker requirement

        final List<String> strings = getJSON("DockstoreTestUser2/dockstore-whalesay-imports", "/Dockstore.cwl", "cwl", "master");
        int countNode = countToolInJSON(strings);

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have two tools", countNode, 2);
        Assert.assertTrue("tool data should have rev as id", strings.get(0).contains("rev"));
        Assert.assertTrue("tool data should have sorted as id", strings.get(0).contains("sorted"));
        Assert.assertTrue("untar docker and link should use default docker req from workflow", strings.get(0).contains("\"id\":\"rev\","+
                "\"file\":\"revtool.cwl\","+
                "\"docker\":\"debian:8\"," +
                "\"link\":\"https://hub.docker.com/_/debian\""));
        Assert.assertTrue("compile docker and link should use default docker req from workflow", strings.get(0).contains("\"id\":\"sorted\"," +
                "\"file\":\"sorttool.cwl\","+
                "\"docker\":\"debian:8\"," +
                "\"link\":\"https://hub.docker.com/_/debian\""));

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
        Assert.assertTrue("pass_filter should not have docker link", strings.get(0).contains("\"id\":\"pass_filter\","+
                "\"file\":\"pass-filter.cwl\","+
                "\"docker\":\"pancancer/pcawg-oxog-tools\"," +
                "\"link\":\"https://hub.docker.com/r/pancancer/pcawg-oxog-tools\""));
        Assert.assertTrue("merge_vcfs should not have docker link", strings.get(0).contains("\"id\":\"merge_vcfs\"," +
                "\"file\":\"vcf_merge.cwl\","+
                "\"docker\":\"pancancer/pcawg-oxog-tools\"," +
                "\"link\":\"https://hub.docker.com/r/pancancer/pcawg-oxog-tools\""));
    }

    @Test
    public void testToolCWL1SyntaxCorrectLink() throws IOException, TimeoutException, ApiException {
        // Input: preprocess_vcf.cwl
        // Repo: OxoG-Dockstore-Tools
        // Branch: correct_docker_link
        // Test: "pass_filter should have a link to quay repo"
        // Return: JSON string contains five tools, all have docker requirement, but no link to it since the link is invalid

        final List<String> strings = getJSON("DockstoreTestUser2/OxoG-Dockstore-Tools", "/preprocess_vcf.cwl", "cwl", "correct_docker_link");
        int countNode = countToolInJSON(strings);

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have 5 tools", countNode, 5);
        Assert.assertTrue("tool data should have pass_filter as id", strings.get(0).contains("pass_filter"));
        Assert.assertTrue("tool data should have merge_vcfs as id", strings.get(0).contains("merge_vcfs"));
        Assert.assertTrue("pass_filter should not have docker link", strings.get(0).contains("\"id\":\"pass_filter\","+
                "\"file\":\"pass-filter.cwl\","+
                "\"docker\":\"quay.io/pancancer/pcawg-oxog-tools\"," +
                "\"link\":\"https://quay.io/repository/pancancer/pcawg-oxog-tools\""));
        Assert.assertTrue("merge_vcfs should not have docker link", strings.get(0).contains("\"id\":\"merge_vcfs\"," +
                "\"file\":\"vcf_merge.cwl\","+
                "\"docker\":\"pancancer/pcawg-oxog-tools\"," +
                "\"link\":\"https://hub.docker.com/r/pancancer/pcawg-oxog-tools\""));
    }
}