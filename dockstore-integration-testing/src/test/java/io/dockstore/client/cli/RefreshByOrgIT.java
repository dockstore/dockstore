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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.TestingPostgres;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Tool;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.User;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static io.dockstore.common.CommonTestUtilities.WAIT_TIME;
import static io.dockstore.common.CommonTestUtilities.getWebClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * @author gluu
 * @since 25/09/17
 */
@Category(ConfidentialTest.class)
public class RefreshByOrgIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
    private static final List<String> NEW_DOCKSTORE_TEST_USER_2_TOOLS = Collections.singletonList("dockstore-tool-imports");
    private static final List<String> NEW_DOCKSTORE_TEST_USER_2_WORKFLOWS_ARRAY = Arrays
        .asList("dockerhubandgithub", "dockstore_empty_repo", "dockstore-whalesay-imports", "parameter_test_workflow",
            "quayandgithubalternate", "OxoG-Dockstore-Tools", "test_workflow_cwl", "hello-dockstore-workflow", "quayandgithub",
            "dockstore_workflow_cnv", "test_workflow_wdl", "quayandgithubwdl", "test_lastmodified", "dockstore-tool-imports");
    private static final List<String> NEW_DOCKSTORE_DOT_TEST_DOT_USER_2_WORKFLOWS = Collections.singletonList("dockstore-workflow-example");
    private static final List<String> NEW_DOCKSTORE_TEST_USER_2_WORKFLOWS = Collections.singletonList("dockstore-workflow");
    private static Client client;
    private static String token;
    private static String usersURLPrefix;
    private static ObjectMapper objectMapper;
    private static Long id;
    private static List<Tool> previousTools;
    private static List<BioWorkflow> previousWorkflows;
    private static TestingPostgres testingPostgres;

    @AfterClass
    public static void afterClass() {
        SUPPORT.after();
    }

    @BeforeClass
    public static void clearDBandSetup() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, true);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
        id = testingPostgres.runSelectStatement("select id from enduser where username='DockstoreTestUser2';", long.class);
        Environment environment = SUPPORT.getEnvironment();
        token = testingPostgres.runSelectStatement("select content from token where tokensource='dockstore';", String.class);
        client = new JerseyClientBuilder(environment).build("test client").property(ClientProperties.READ_TIMEOUT, WAIT_TIME);
        objectMapper = environment.getObjectMapper();
    }

    private void checkInitialDB() throws IOException {
        // The DB initially has no workflows
        List<BioWorkflow> currentWorkflows = getWorkflows();
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
    public void testToolDeletion() throws IOException {
        // TODO: the tests in this class are a bit odd compared to more modern tests
        usersURLPrefix = "http://localhost:%d/users/" + id;
        checkInitialDB();
        // insert a non-existent tool to be deleted during refresh
        testingPostgres.runUpdateStatement(
            "insert into tool (id, giturl, mode, name, namespace, registry, ispublished, dbupdatedate, dbcreatedate) select 100, giturl, mode, 'newtool', namespace, registry, ispublished, dbupdatedate, dbcreatedate from tool where id = 2;");
        testingPostgres.runUpdateStatement("insert into user_entry (userid, entryid) values (1, 100)");
        Long count = testingPostgres.runSelectStatement("select count(*) from tool where id = 100;", long.class);
        assertEquals(1, (long)count);
        testRefreshToolsByOrg2();
        count = testingPostgres.runSelectStatement("select count(*) from tool where id = 100;", long.class);
        // tool should have been deleted
        assertEquals(0, (long)count);
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
        assertThat(currentTools.size() - previousTools.size()).isGreaterThanOrEqualTo(NEW_DOCKSTORE_TEST_USER_2_TOOLS.size());
        previousTools = currentTools;
        testRefreshToolsByOrg3();
        currentTools = getTools();
        assertThat(currentTools.size() - previousTools.size()).isEqualTo(0);
    }

    /**
     * This tests if the user can quickly register a single tool with Quay.io org and repo specified
     */
    @Test
    public void testRefreshToolByOrgAndRepo() {
        final String exceptionMessage = "Could not get repository from Quay.io";
        final String knownValidOrganization = "dockstoretestuser2";
        final String knownValidRepository = "md5sum";
        ApiClient apiClient = getWebClient(true, "DockstoreTestUser2", testingPostgres);
        UsersApi usersApi = new UsersApi(apiClient);
        User user = usersApi.getUser();
        Long userId = user.getId();
        List<DockstoreTool> dockstoreToolsBeforeRefresh = usersApi.userContainers(userId);
        boolean hasTool = hasTool(dockstoreToolsBeforeRefresh, knownValidOrganization, knownValidRepository);
        Assert.assertFalse(hasTool);
        List<DockstoreTool> dockstoreToolsAfterRefresh = usersApi.refreshToolsByOrganization(userId, knownValidOrganization, knownValidRepository);
        Assert.assertEquals("Should have 1 more tool than before", dockstoreToolsBeforeRefresh.size() + 1, dockstoreToolsAfterRefresh.size());
        hasTool = hasTool(dockstoreToolsAfterRefresh, knownValidOrganization, knownValidRepository);
        Assert.assertTrue("Should've have added a single tool", hasTool);
        try {
            usersApi.refreshToolsByOrganization(userId, "fakeOrganization", "fakeRepository");
            Assert.fail("Should have an exception when repo and org does not exist");
        } catch (ApiException e) {
            Assert.assertEquals(exceptionMessage, e.getMessage());
        }
        try {
            usersApi.refreshToolsByOrganization(userId, knownValidOrganization, "fakeRepository");
            Assert.fail("Should have an exception when repo does not exist");
        } catch (ApiException e) {
            Assert.assertEquals(exceptionMessage, e.getMessage());
        }
        try {
            usersApi.refreshToolsByOrganization(userId, "fakeOrganization", "md5sum");
            Assert.fail("Should have an exception when org does not exist");
        } catch (ApiException e) {
            Assert.assertEquals(exceptionMessage, e.getMessage());
        }
    }

    private boolean hasTool(List<DockstoreTool> dockstoreTools, String organization, String repository) {
        return dockstoreTools.stream().anyMatch(
            dockstoreTool -> dockstoreTool.getName().equals(repository) && dockstoreTool.getNamespace().equals(organization)
                && dockstoreTool.getRegistry().equals(DockstoreTool.RegistryEnum.QUAY_IO));
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
        assertThat(tools.size() - previousTools.size()).isGreaterThanOrEqualTo(NEW_DOCKSTORE_TEST_USER_2_TOOLS.size());
        // Remove all the tools that have the same name as the previous ones
        tools.removeIf(tool -> previousToolsHaveName(tool.getName()));
        // Ensure that one of the new tools added is "dockstore-tool-imports"
        NEW_DOCKSTORE_TEST_USER_2_TOOLS
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
        List<BioWorkflow> currentWorkflows = getWorkflows();
        assertThat(currentWorkflows.size() - previousWorkflows.size()).isGreaterThanOrEqualTo(NEW_DOCKSTORE_TEST_USER_2_WORKFLOWS_ARRAY.size());
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
        assertThat(currentWorkflows.size() - previousWorkflows.size()).isGreaterThanOrEqualTo(NEW_DOCKSTORE_TEST_USER_2_WORKFLOWS.size());
        previousWorkflows = currentWorkflows;

        testRefreshWorkflowsByOrg5();
        currentWorkflows = getWorkflows();
        assertThat(currentWorkflows.size() - previousWorkflows.size()).isGreaterThanOrEqualTo(NEW_DOCKSTORE_DOT_TEST_DOT_USER_2_WORKFLOWS.size());
    }

    /**
     * This tests if the webservice's refresh adds the 14 DockstoreTestUser2 repositories
     */
    private void testRefreshWorkflowsByOrg1() throws IOException {
        String url = usersURLPrefix + "/workflows/DockstoreTestUser2/refresh";
        List<BioWorkflow> workflows = clientHelperWorkflow(url);
        // Remove all the tools that have the same name as the previous ones
        workflows.removeIf(workflow -> previousWorkflowsHaveName(workflow.getRepository()));
        // Ensure that there are at least 14 new tools
        assertThat(workflows.size()).isGreaterThanOrEqualTo(NEW_DOCKSTORE_TEST_USER_2_WORKFLOWS_ARRAY.size());
        NEW_DOCKSTORE_TEST_USER_2_WORKFLOWS_ARRAY.forEach(
            newTool -> assertThat(workflows.parallelStream().anyMatch(workflow -> workflow.getRepository().equals(newTool))).isTrue());
    }

    /**
     * This tests if the webservice's refresh retains the previous 14 repos when adding an organization that exist
     * but user has no access to.
     */
    private void testRefreshWorkflowsByOrg2() throws IOException {
        String url = usersURLPrefix + "/workflows/dockstore/refresh";
        List<BioWorkflow> workflows = clientHelperWorkflow(url);
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
        List<BioWorkflow> workflows = clientHelperWorkflow(url);
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
        List<BioWorkflow> workflows = clientHelperWorkflow(url);
        // Remove all the tools that have the same name as the previous ones
        workflows.removeIf(workflow -> previousWorkflowsHaveName(workflow.getRepository()));
        // Ensure that there are at least 14 new tools
        assertThat(workflows.size()).isGreaterThanOrEqualTo(NEW_DOCKSTORE_TEST_USER_2_WORKFLOWS.size());
        NEW_DOCKSTORE_TEST_USER_2_WORKFLOWS.forEach(workflowRepository -> assertThat(
            workflows.parallelStream().anyMatch(workflow -> workflow.getRepository().equals(workflowRepository))).isTrue());
    }

    /**
     * This tests if the webservice's refresh can add the dockstore.test.user2 GitLab organization
     */
    private void testRefreshWorkflowsByOrg5() throws IOException {
        String url = usersURLPrefix + "/workflows/dockstore.test.user2/refresh";
        List<BioWorkflow> workflows = clientHelperWorkflow(url);
        // Remove all the tools that have the same name as the previous ones
        workflows.removeIf(workflow -> previousWorkflowsHaveName(workflow.getRepository()));
        // Ensure that there are at least 14 new tools
        assertThat(workflows.size()).isGreaterThanOrEqualTo(NEW_DOCKSTORE_DOT_TEST_DOT_USER_2_WORKFLOWS.size());
        NEW_DOCKSTORE_DOT_TEST_DOT_USER_2_WORKFLOWS.forEach(workflowRepository -> assertThat(
            workflows.parallelStream().anyMatch(workflow -> workflow.getRepository().equals(workflowRepository))).isTrue());
    }

    private List<BioWorkflow> clientHelperWorkflow(String url) throws IOException {
        Response response = client.target(String.format(url, SUPPORT.getLocalPort())).request()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).get();
        String entity = response.readEntity(String.class);
        return objectMapper.readValue(entity, new TypeReference<>() {
        });
    }

    private List<BioWorkflow> getWorkflows() throws IOException {
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
