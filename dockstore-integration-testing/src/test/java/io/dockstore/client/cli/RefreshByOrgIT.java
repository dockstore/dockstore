package io.dockstore.client.cli;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.swagger.client.ApiException;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static io.dockstore.common.CommonTestUtilities.clearStateMakePrivate2;
import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author gluu
 * @since 25/09/17
 */
public class RefreshByOrgIT {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstoreTest.yml"));
    private static Client client;
    private static String token;
    private static String usersURLPrefix;
    private static ObjectMapper objectMapper;
    private static Long id;

    @BeforeClass
    public static void clearDBandSetup() throws IOException, TimeoutException, ApiException {
        clearStateMakePrivate2();
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        id = testingPostgres.runSelectStatement("select id from enduser where username='DockstoreTestUser2';", new ScalarHandler<>());
        Environment environment = RULE.getEnvironment();
        token = testingPostgres.runSelectStatement("select content from token where tokensource='dockstore';", new ScalarHandler<>());
        client = new JerseyClientBuilder(environment).build("test client").property(ClientProperties.READ_TIMEOUT, 60000);
        objectMapper = environment.getObjectMapper();
    }

    @Before
    public void clearDB() throws IOException, TimeoutException, ApiException {
        clearStateMakePrivate2();
    }

    private void checkInitialDB() throws IOException {
        // The DB initially has no workflows
        assertThat(getWorkflows().size()).isGreaterThanOrEqualTo(0);
        // The DB initially has 4 tools, 1 from dockstore2 and 3 from dockstoretestuser2
        assertThat(getTools().size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    public void testRefreshToolsByOrg() throws IOException {
        usersURLPrefix = "http://localhost:%d/users/" + id;
        checkInitialDB();
        testRefreshToolsByOrg1();
        assertThat(getTools().size()).isGreaterThanOrEqualTo(4);
        testRefreshToolsByOrg2();
        assertThat(getTools().size()).isGreaterThanOrEqualTo(5);
        testRefreshToolsByOrg3();
        assertThat(getTools().size()).isGreaterThanOrEqualTo(5);
    }

    /**
     * This tests if refreshing dockstore2 retains the same number of repos
     */
    private void testRefreshToolsByOrg1() throws IOException {
        String url = usersURLPrefix + "/containers/dockstore2/refresh";
        List<Tool> tools = clientHelperTool(url);
        assertThat(tools.size()).isGreaterThanOrEqualTo(4);
    }

    /**
     * This tests if refreshing dockstorestestuser2 adds 1 additional repo
     */
    private void testRefreshToolsByOrg2() throws IOException {
        String url = usersURLPrefix + "/containers/dockstoretestuser2/refresh";
        List<Tool> tools = clientHelperTool(url);
        assertThat(tools.size()).isGreaterThanOrEqualTo(5);

    }

    /**
     * This tests if refreshing a non-existent repo does not alter the current 5 repos
     */
    private void testRefreshToolsByOrg3() throws IOException {
        String url = usersURLPrefix + "/containers/mmmrrrggglll/refresh";
        List<Tool> tools = clientHelperTool(url);
        assertThat(tools.size()).isGreaterThanOrEqualTo(5);
    }

    private List<Tool> clientHelperTool(String url) throws IOException {
        Response response = client.target(String.format(url, RULE.getLocalPort())).request()
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
        assertThat(getWorkflows().size()).isGreaterThanOrEqualTo(14);
        testRefreshWorkflowsByOrg2();
        assertThat(getWorkflows().size()).isGreaterThanOrEqualTo(14);
        testRefreshWorkflowsByOrg3();
        assertThat(getWorkflows().size()).isGreaterThanOrEqualTo(14);
        testRefreshWorkflowsByOrg4();
        assertThat(getWorkflows().size()).isGreaterThanOrEqualTo(15);
        testRefreshWorkflowsByOrg5();
        assertThat(getWorkflows().size()).isGreaterThanOrEqualTo(16);
    }

    /**
     * This tests if the webservice's refresh adds the 4 DockstoreTestUser2 repositories
     */
    private void testRefreshWorkflowsByOrg1() throws IOException {
        String url = usersURLPrefix + "/workflows/DockstoreTestUser2/refresh";
        List<Workflow> workflows = clientHelperWorkflow(url);
        assert workflows != null;
        assertThat(workflows.size()).isGreaterThanOrEqualTo(14);

    }

    /**
     * This tests if the webservice's refresh retains the previous 14 repos when adding an organization that exist
     * but user has no access to.
     */
    private void testRefreshWorkflowsByOrg2() throws IOException {
        String url = usersURLPrefix + "/workflows/dockstore/refresh";
        List<Workflow> workflows = clientHelperWorkflow(url);
        assertThat(workflows.size()).isGreaterThanOrEqualTo(14);
    }

    private List<Workflow> getWorkflows() throws IOException {
        String url = usersURLPrefix + "/workflows";
        return clientHelperWorkflow(url);
    }

    private List<Tool> getTools() throws IOException {
        String url = usersURLPrefix + "/containers";
        return clientHelperTool(url);
    }

    /**
     * This tests if the webservice's refresh adds retains the previous 14 repositories while trying to add
     * repositories from an organization that does not exist.
     */
    private void testRefreshWorkflowsByOrg3() throws IOException {
        String url = usersURLPrefix + "/workflows/mmmrrrggglll/refresh";
        List<Workflow> workflows = clientHelperWorkflow(url);
        assertThat(workflows.size()).isGreaterThanOrEqualTo(14);
    }

    /**
     * This tests if the webservice's refresh can add the dockstore_testuser2 bitbucket organization
     */
    private void testRefreshWorkflowsByOrg4() throws IOException {
        String url = usersURLPrefix + "/workflows/dockstore_testuser2/refresh";
        List<Workflow> workflows = clientHelperWorkflow(url);
        assertThat(workflows.size()).isGreaterThanOrEqualTo(15);
    }

    /**
     * This tests if the webservice's refresh can add the dockstore.test.user2 GitLab organization
     */
    private void testRefreshWorkflowsByOrg5() throws IOException {
        String url = usersURLPrefix + "/workflows/dockstore.test.user2/refresh";
        List<Workflow> workflows = clientHelperWorkflow(url);
        assertThat(workflows.size()).isGreaterThanOrEqualTo(16);
    }

    private List<Workflow> clientHelperWorkflow(String url) throws IOException {
        Response response = client.target(String.format(url, RULE.getLocalPort())).request()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).get();
        String entity = response.readEntity(String.class);
        return objectMapper.readValue(entity, new TypeReference<List<Workflow>>() {
        });

    }
}
