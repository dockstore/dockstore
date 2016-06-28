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
 * Created by jpatricia on 24/06/16.
 */
public class DAGWorkflowTestIT {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstoreTest.yml"));

    @Before
    public void clearDBandSetup() throws IOException, TimeoutException {
        clearStateMakePrivate2();
    }

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();


    @Test
    public void testWorkflowDAGCWL() throws IOException, TimeoutException, ApiException {
        //Test with '1st-workflow.cwl'
        //will return a json with 2 nodes and an edge connecting it

        final ApiClient webClient = WorkflowET.getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublish(true);

        // Get workflows
        usersApi.refreshWorkflows(userId);

        // Manually register workflow github
        Workflow githubWorkflow = workflowApi.manualRegister("github", "DockstoreTestUser2/test_workflow_cwl", "/1st-workflow.cwl", "test-workflow", "cwl");

        // Publish github workflow
        Workflow refresh = workflowApi.refresh(githubWorkflow.getId());


        Optional<WorkflowVersion> master = refresh.getWorkflowVersions().stream().filter(workflow -> workflow.getName().equals("master")).findFirst();


        final String basePath = webClient.getBasePath();
        URL url = new URL(basePath + "/workflows/" +githubWorkflow.getId()+"/dag/" + master.get().getId() );
        final List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));

        //count the number of nodes in the DAG json
        int countNode = 0;
        int last = 0;
        String node = "tool";
        while(last !=-1){
            last = strings.get(0).indexOf(node,last);

            if(last !=-1){
                countNode++;
                last += node.length();
            }
        }

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have two nodes", countNode, 2);
        Assert.assertTrue("node data should have untar as tool", strings.get(0).contains("untar"));
        Assert.assertTrue("node data should have compile as tool", strings.get(0).contains("compile"));
        Assert.assertTrue("edge should connect untar and compile", strings.get(0).contains("\"source\":\"0\",\"target\":\"1\""));

    }

    @Test
    public void testWorkflowDAGWDL() throws IOException, TimeoutException, ApiException {
        //Test with 'hello.wdl'
        //will return a json with a node and no edge

        final ApiClient webClient = WorkflowET.getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublish(true);

        // Get workflows
        usersApi.refreshWorkflows(userId);

        // Manually register workflow github
        Workflow githubWorkflow = workflowApi.manualRegister("github", "DockstoreTestUser2/test_workflow_wdl", "/hello.wdl", "test-workflow", "wdl");

        // Publish github workflow
        Workflow refresh = workflowApi.refresh(githubWorkflow.getId());


        Optional<WorkflowVersion> master = refresh.getWorkflowVersions().stream().filter(workflow -> workflow.getName().equals("master")).findFirst();


        final String basePath = webClient.getBasePath();
        URL url = new URL(basePath + "/workflows/" +githubWorkflow.getId()+"/dag/" + master.get().getId() );
        final List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));

        //count the number of nodes in the DAG json
        int countNode = 0;
        int last = 0;
        String node = "tool";
        while(last !=-1){
            last = strings.get(0).indexOf(node,last);

            if(last !=-1){
                countNode++;
                last += node.length();
            }
        }

        Assert.assertTrue("JSON should not be blank", strings.size() > 0);
        Assert.assertEquals("JSON should have one node", countNode,1);
        Assert.assertTrue("node data should have hello as task", strings.get(0).contains("hello"));
        Assert.assertTrue("should have no edge", strings.get(0).contains("\"edges\":[]"));
    }

    @Test
    public void testWorkflowDAGCWLMissingTool() throws IOException, TimeoutException, ApiException {
        //Test 'Dockstore.cwl'
        //will return empty nodes and edges because it's missing required tools in the repo

        final ApiClient webClient = WorkflowET.getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublish(true);

        // Get workflows
        usersApi.refreshWorkflows(userId);

        // Manually register workflow github
        Workflow githubWorkflow = workflowApi.manualRegister("github", "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.cwl", "testCWL", "cwl");

        // Publish github workflow
        Workflow refresh = workflowApi.refresh(githubWorkflow.getId());


        Optional<WorkflowVersion> master = refresh.getWorkflowVersions().stream().filter(workflow -> workflow.getName().equals("testCWL")).findFirst();


        final String basePath = webClient.getBasePath();
        URL url = new URL(basePath + "/workflows/" +githubWorkflow.getId()+"/dag/" + master.get().getId() );
        final List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));

        //JSON will have node:[] and edges:[]
        Assert.assertEquals("JSON should not have any data for nodes and edges", strings.size(),1);
    }

    @Test
    public void testWorkflowDAGWDLMissingTask() throws IOException, TimeoutException, ApiException {
        //Test 'hello.wdl'
        //will return blank JSON because the task is missing in the file

        final ApiClient webClient = WorkflowET.getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublish(true);

        // Get workflows
        usersApi.refreshWorkflows(userId);

        // Manually register workflow github
        Workflow githubWorkflow = workflowApi.manualRegister("github", "DockstoreTestUser2/test_workflow_wdl", "/hello.wdl", "test-workflow", "wdl");

        // Publish github workflow
        Workflow refresh = workflowApi.refresh(githubWorkflow.getId());


        Optional<WorkflowVersion> master = refresh.getWorkflowVersions().stream().filter(workflow -> workflow.getName().equals("missing_docker")).findFirst();


        final String basePath = webClient.getBasePath();
        URL url = new URL(basePath + "/workflows/" +githubWorkflow.getId()+"/dag/" + master.get().getId() );
        final List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));

        //JSON will have no content at all
        Assert.assertEquals("JSON should be blank", strings.size(),0);
    }
}
