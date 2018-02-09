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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.IntegrationTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.DropwizardTestSupport;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static io.dockstore.common.CommonTestUtilities.WAIT_TIME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author gluu
 * @since 25/09/17
 */
@Category({ConfidentialTest.class, IntegrationTest.class})
public class RefreshByOrgIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIG_PATH);

    @AfterClass
    public static void afterClass(){
        SUPPORT.after();
    }

    private static final List<String> newDockstoreTestUser2Tools = Arrays.asList("dockstore-tool-imports");
    private static final List<String> newDockstoreTestUser2Workflows = Arrays
            .asList("dockerhubandgithub", "dockstore_empty_repo", "dockstore-whalesay-imports", "parameter_test_workflow",
                    "quayandgithubalternate", "OxoG-Dockstore-Tools", "test_workflow_cwl", "hello-dockstore-workflow", "quayandgithub",
                    "dockstore_workflow_cnv", "test_workflow_wdl", "quayandgithubwdl", "test_lastmodified", "dockstore-tool-imports");
    private static final List<String> newDockstoreDotTestDotUser2Workflows = Arrays.asList("dockstore-workflow-example");
    private static final List<String> newDockstore_TestUser2Workflows = Arrays.asList("dockstore-workflow");
    private static Client client;
    private static String token;
    private static String usersURLPrefix;
    private static ObjectMapper objectMapper;
    private static Long id;
    private static List<Tool> previousTools;
    private static List<Workflow> previousWorkflows;

    @BeforeClass
    public static void clearDBandSetup() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, true);
        SUPPORT.before();
        final CommonTestUtilities.TestingPostgres testingPostgres = CommonTestUtilities.getTestingPostgres();
        id = testingPostgres.runSelectStatement("select id from enduser where username='DockstoreTestUser2';", new ScalarHandler<>());
        Environment environment = SUPPORT.getEnvironment();
        token = testingPostgres.runSelectStatement("select content from token where tokensource='dockstore';", new ScalarHandler<>());
        client = new JerseyClientBuilder(environment).build("test client").property(ClientProperties.READ_TIMEOUT, WAIT_TIME);
        objectMapper = environment.getObjectMapper();
    }

    private void checkInitialDB() throws IOException {
        // The DB initially has no workflows
        List<Workflow> currentWorkflows = getWorkflows();
        // Leaving this in here so we don't forget to change this when there is a workflow in the test DB
        assertThat(currentWorkflows.size()).isGreaterThanOrEqualTo(0);

        // The DB initially has 4 tools, 1 from dockstore2 and 3 from dockstoretestuser2
        List<Tool> currentTools = getTools();
        List<Tool> dockstore2Tools = currentTools.parallelStream().filter(tool -> tool.getNamespace().equals("dockstore2"))
                .collect(Collectors.toList());
        List<Tool> dockstoretestuser2Tools = currentTools.parallelStream().filter(tool -> tool.getNamespace().equals("dockstoretestuser2"))
                .collect(Collectors.toList());

        assertThat(dockstore2Tools.size()).isGreaterThanOrEqualTo(1);
        assertThat(dockstoretestuser2Tools.size()).isGreaterThanOrEqualTo(3);
        assertThat(currentTools.size()).isGreaterThanOrEqualTo(4);
        previousTools = currentTools;
        previousWorkflows = currentWorkflows;
    }

    @Test
    public void testRefreshToolsByOrg() throws IOException {
        usersURLPrefix = "http://localhost:%d/users/" + id;
        checkInitialDB();
        testRefreshToolsByOrg1();
        List<Tool> currentTools = getTools();
        assertThat(currentTools.size() - previousTools.size()).isEqualTo(0);
        previousTools = currentTools;
        testRefreshToolsByOrg2();
        currentTools = getTools();
        assertThat(currentTools.size() - previousTools.size()).isGreaterThanOrEqualTo(newDockstoreTestUser2Tools.size());
        previousTools = currentTools;
        testRefreshToolsByOrg3();
        currentTools = getTools();
        assertThat(currentTools.size() - previousTools.size()).isEqualTo(0);
    }

    /**
     * This tests if refreshing dockstore2 retains the same number of repos
     */
    private void testRefreshToolsByOrg1() throws IOException {
        String url = usersURLPrefix + "/containers/dockstore2/refresh";
        List<Tool> tools = clientHelperTool(url);
        assertThat(tools.size() - previousTools.size()).isEqualTo(0);
        // Remove all the tools that have the same name as the previous ones
        tools.removeIf(tool -> previousToolsHaveName(tool.getName()));
        // Ensure that there are no new tools
        assertThat(tools.size()).isEqualTo(0);
    }

    /**
     * This tests if refreshing dockstorestestuser2 adds at least 1 additional repo (dockstore-tool-imports)
     */
    private void testRefreshToolsByOrg2() throws IOException {
        String url = usersURLPrefix + "/containers/dockstoretestuser2/refresh";
        List<Tool> tools = clientHelperTool(url);
        assertThat(tools.size() - previousTools.size()).isGreaterThanOrEqualTo(newDockstoreTestUser2Tools.size());
        // Remove all the tools that have the same name as the previous ones
        tools.removeIf(tool -> previousToolsHaveName(tool.getName()));
        // Ensure that one of the new tools added is "dockstore-tool-imports"
        newDockstoreTestUser2Tools
                .forEach(newTool -> assertThat(tools.parallelStream().anyMatch(tool -> tool.getName().equals(newTool))).isTrue());

    }

    /**
     * This tests if refreshing a non-existent repo does not alter the current 5 repos
     */
    private void testRefreshToolsByOrg3() throws IOException {
        String url = usersURLPrefix + "/containers/mmmrrrggglll/refresh";
        List<Tool> tools = clientHelperTool(url);
        assertThat(tools.size() - previousTools.size()).isEqualTo(0);
        // Remove all the tools that have the same name as the previous ones
        tools.removeIf(tool -> previousToolsHaveName(tool.getName()));
        // Ensure that there are no new tools
        assertThat(tools.size()).isEqualTo(0);
    }

    private List<Tool> clientHelperTool(String url) throws IOException {
        Response response = client.target(String.format(url, SUPPORT.getLocalPort())).request()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).get();
        String entity = response.readEntity(String.class);
        return objectMapper.readValue(entity, new TypeReference<List<Tool>>() {
        });
    }

    @Test
    public void testRefreshWorkflowsByOrg() throws IOException {
        usersURLPrefix = "http://localhost:%d/users/" + id;
        checkInitialDB();

        testRefreshWorkflowsByOrg1();
        List<Workflow> currentWorkflows = getWorkflows();
        assertThat(currentWorkflows.size() - previousWorkflows.size()).isGreaterThanOrEqualTo(newDockstoreTestUser2Workflows.size());
        previousWorkflows = currentWorkflows;

        testRefreshWorkflowsByOrg2();
        currentWorkflows = getWorkflows();
        assertThat(currentWorkflows.size() - previousWorkflows.size()).isEqualTo(0);
        previousWorkflows = currentWorkflows;

        testRefreshWorkflowsByOrg3();
        currentWorkflows = getWorkflows();
        assertThat(currentWorkflows.size() - previousWorkflows.size()).isEqualTo(0);
        previousWorkflows = currentWorkflows;

        testRefreshWorkflowsByOrg4();
        currentWorkflows = getWorkflows();
        assertThat(currentWorkflows.size() - previousWorkflows.size()).isGreaterThanOrEqualTo(newDockstore_TestUser2Workflows.size());
        previousWorkflows = currentWorkflows;

        testRefreshWorkflowsByOrg5();
        currentWorkflows = getWorkflows();
        assertThat(currentWorkflows.size() - previousWorkflows.size()).isGreaterThanOrEqualTo(newDockstoreDotTestDotUser2Workflows.size());
    }

    /**
     * This tests if the webservice's refresh adds the 14 DockstoreTestUser2 repositories
     */
    private void testRefreshWorkflowsByOrg1() throws IOException {
        String url = usersURLPrefix + "/workflows/DockstoreTestUser2/refresh";
        List<Workflow> workflows = clientHelperWorkflow(url);
        // Remove all the tools that have the same name as the previous ones
        workflows.removeIf(workflow -> previousWorkflowsHaveName(workflow.getRepository()));
        // Ensure that there are at least 14 new tools
        assertThat(workflows.size()).isGreaterThanOrEqualTo(newDockstoreTestUser2Workflows.size());
        newDockstoreTestUser2Workflows.forEach(
                newTool -> assertThat(workflows.parallelStream().anyMatch(workflow -> workflow.getRepository().equals(newTool))).isTrue());
    }

    /**
     * This tests if the webservice's refresh retains the previous 14 repos when adding an organization that exist
     * but user has no access to.
     */
    private void testRefreshWorkflowsByOrg2() throws IOException {
        String url = usersURLPrefix + "/workflows/dockstore/refresh";
        List<Workflow> workflows = clientHelperWorkflow(url);
        // Remove all the tools that have the same name as the previous ones
        workflows.removeIf(workflow -> previousWorkflowsHaveName(workflow.getRepository()));
        // Ensure that there are at least 14 new tools
        assertThat(workflows.size()).isEqualTo(0);
    }

    /**
     * This tests if the webservice's refresh adds retains the previous 14 repositories while trying to add
     * repositories from an organization that does not exist.
     */
    private void testRefreshWorkflowsByOrg3() throws IOException {
        String url = usersURLPrefix + "/workflows/mmmrrrggglll/refresh";
        List<Workflow> workflows = clientHelperWorkflow(url);
        // Remove all the tools that have the same name as the previous ones
        workflows.removeIf(workflow -> previousWorkflowsHaveName(workflow.getRepository()));
        // Ensure that there are at least 14 new tools
        assertThat(workflows.size()).isEqualTo(0);
    }

    /**
     * This tests if the webservice's refresh can add the dockstore_testuser2 bitbucket organization
     */
    private void testRefreshWorkflowsByOrg4() throws IOException {
        String url = usersURLPrefix + "/workflows/dockstore_testuser2/refresh";
        List<Workflow> workflows = clientHelperWorkflow(url);
        // Remove all the tools that have the same name as the previous ones
        workflows.removeIf(workflow -> previousWorkflowsHaveName(workflow.getRepository()));
        // Ensure that there are at least 14 new tools
        assertThat(workflows.size()).isGreaterThanOrEqualTo(newDockstore_TestUser2Workflows.size());
        newDockstore_TestUser2Workflows.forEach(workflowRepository -> assertThat(
                workflows.parallelStream().anyMatch(workflow -> workflow.getRepository().equals(workflowRepository))).isTrue());
    }

    /**
     * This tests if the webservice's refresh can add the dockstore.test.user2 GitLab organization
     */
    private void testRefreshWorkflowsByOrg5() throws IOException {
        String url = usersURLPrefix + "/workflows/dockstore.test.user2/refresh";
        List<Workflow> workflows = clientHelperWorkflow(url);
        // Remove all the tools that have the same name as the previous ones
        workflows.removeIf(workflow -> previousWorkflowsHaveName(workflow.getRepository()));
        // Ensure that there are at least 14 new tools
        assertThat(workflows.size()).isGreaterThanOrEqualTo(newDockstoreDotTestDotUser2Workflows.size());
        newDockstoreDotTestDotUser2Workflows.forEach(workflowRepository -> assertThat(
                workflows.parallelStream().anyMatch(workflow -> workflow.getRepository().equals(workflowRepository))).isTrue());
    }

    private List<Workflow> clientHelperWorkflow(String url) throws IOException {
        Response response = client.target(String.format(url, SUPPORT.getLocalPort())).request()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).get();
        String entity = response.readEntity(String.class);
        return objectMapper.readValue(entity, new TypeReference<List<Workflow>>() {
        });
    }

    private List<Workflow> getWorkflows() throws IOException {
        String url = usersURLPrefix + "/workflows";
        return clientHelperWorkflow(url);
    }

    private List<Tool> getTools() throws IOException {
        String url = usersURLPrefix + "/containers";
        return clientHelperTool(url);
    }

    private boolean previousToolsHaveName(String name) {
        return previousTools.parallelStream().anyMatch(previousTool -> previousTool.getName().equals(name));
    }

    private boolean previousWorkflowsHaveName(String name) {
        return previousWorkflows.parallelStream().anyMatch(previousTool -> previousTool.getRepository().equals(name));
    }
}
