/*
 *    Copyright 2019 OICR
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
package io.dockstore.webservice;

import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATH;
import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_LIMIT;
import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.client.cli.BasicIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.ServiceDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dropwizard.client.JerseyClientBuilder;
import io.swagger.api.impl.ToolsImplCommon;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.StarRequest;
import io.swagger.client.model.Tool;
import java.util.List;
import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.glassfish.jersey.client.ClientProperties;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
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
 * @author dyuen
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class ServiceIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    private WorkflowDAO workflowDAO;
    private ServiceDAO serviceDAO;
    private Session session;
    private UserDAO userDAO;
    private FileDAO fileDAO;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.serviceDAO = new ServiceDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.fileDAO = new FileDAO(sessionFactory);


        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use tokenDAO outside of the web service
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Test
    void checkWorkflowAndServiceHierarchy() {
        CreateContent createContent = new CreateContent().invoke(false);
        long workflowID = createContent.getWorkflowID();
        long serviceID = createContent.getServiceID();
        long serviceID2 = createContent.getServiceID2();

        // might not be right if our test database is larger than PAGINATION_LIMIT
        final List<Workflow> allPublished = workflowDAO.findAllPublished(0, Integer.valueOf(PAGINATION_LIMIT), null, null, null);
        assertTrue(allPublished.stream().anyMatch(workflow -> workflow.getId() == workflowID && workflow instanceof BioWorkflow));
        assertTrue(allPublished.stream().anyMatch(workflow -> workflow.getId() == serviceID && workflow instanceof Service));
        assertTrue(allPublished.stream().anyMatch(workflow -> workflow.getId() == serviceID2 && workflow instanceof Service));

        final Service byId = serviceDAO.findById(serviceID);
        final Service byId1 = serviceDAO.findById(workflowID);

        assertTrue(byId != null && byId1 == null);
        session.close();
    }

    @Test
    @Disabled("https://github.com/dockstore/dockstore/pull/4720")
    void testTRSOutputOfService() {
        new CreateContent().invoke();
        final ApiClient webClient = getWebClient(true, false);
        Ga4GhApi client = new Ga4GhApi(webClient);
        final List<Tool> tools = client.toolsGet(null, null, null, null, null, null, null, null, null, null, null);
        assertTrue(tools.stream().filter(tool -> tool.getToolclass().getName().equalsIgnoreCase("workflow")).count() >= 1);
        // TODO: change boolean once services are exposed
        boolean servicesExposedInTRS = false;
        if (servicesExposedInTRS) {
            assertTrue(tools.stream().filter(tool -> tool.getToolclass().getName().equalsIgnoreCase("service")).count() >= 2);
        }
    }

    @Test
    void testProprietaryAPI() {
        final CreateContent invoke = new CreateContent().invoke();
        final ApiClient webClient = getWebClient(true, false);
        WorkflowsApi client = new WorkflowsApi(webClient);
        final List<io.swagger.client.model.Workflow> services = client.allPublishedWorkflows(null, null, null, null, null, true, null);
        final List<io.swagger.client.model.Workflow> workflows = client.allPublishedWorkflows(null, null, null, null, null, false, null);
        assertTrue(workflows.size() >= 2 && workflows.stream()
            .noneMatch(workflow -> workflow.getDescriptorType().getValue().equalsIgnoreCase(DescriptorLanguage.SERVICE.toString())));
        Client jerseyClient = new JerseyClientBuilder(SUPPORT.getEnvironment()).build("test client");
        testXTotalCount(jerseyClient, String.format("http://localhost:%d/workflows/published", SUPPORT.getLocalPort()));
        testXTotalCount(jerseyClient, String.format("http://localhost:%d/workflows/published?services=true", SUPPORT.getLocalPort()));
        assertTrue(services.size() >= 1 && services.stream()
            .allMatch(workflow -> workflow.getDescriptorType().getValue().equalsIgnoreCase(DescriptorLanguage.SERVICE.toString())));

        // try some standard things we would like services to be able to do
        client.starEntry(invoke.getServiceID(), new StarRequest().star(true));
        client.updateLabels(invoke.getServiceID(), "foo,batman,chicken", "");

        // did it happen?
        final io.swagger.client.model.Workflow workflow = client.getWorkflow(invoke.getServiceID(), "");
        assertFalse(workflow.getStarredUsers().isEmpty());
        assertTrue(workflow.getLabels().stream().anyMatch(label -> "batman".equals(label.getValue())));
    }

    /**
     * Test X-total-count.  It so happens there's two services and two bioworkflows
     *
     * @param jerseyClient Jersey Client to test endpoint
     * @param path         Path of endpoint
     */
    private void testXTotalCount(Client jerseyClient, String path) {
        Response response = jerseyClient.target(path).request().property(ClientProperties.READ_TIMEOUT, 0).get();
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        MultivaluedMap<String, Object> headers = response.getHeaders();
        Object xTotalCount = headers.getFirst("X-total-count");
        assertEquals("2", xTotalCount);
    }

    @Test
    void testGeneralDefaultPathMechanism() {
        final CreateContent invoke = new CreateContent().invoke();
        final ApiClient webClient = getWebClient(true, false);
        WorkflowsApi client = new WorkflowsApi(webClient);
        // did it happen?
        final io.swagger.client.model.Workflow workflow = client.getWorkflow(invoke.getServiceID(), "");
    }

    /**
     * This tests endpoints that will be triggered by GitHub App webhooks.
     * A service is created and a version is added for a release 1.0
     */
    @Test
    void testGitHubAppEndpoints() throws Exception {

        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient("DockstoreTestUser2", testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        String serviceRepo = "DockstoreTestUser2/test-service";
        String installationId = "1179416";

        // Add version
        client.handleGitHubRelease(serviceRepo, "DockstoreTestUser2", "refs/tags/1.0", installationId);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from service", long.class);
        assertEquals(1, workflowCount);
        io.swagger.client.model.Workflow service = client.getWorkflowByPath("github.com/" + serviceRepo, SERVICE, "versions");

        assertNotNull(service);
        assertEquals(1, service.getWorkflowVersions().size(), "Should have a new version");
        List<SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(service.getWorkflowVersions().get(0).getId());
        assertEquals(3, sourceFiles.size(), "Should have 3 source files");

        long users = testingPostgres.runSelectStatement("select count(*) from user_entry where entryid = '" + service.getId() + "'", long.class);
        assertEquals(1, users, "Should have 1 user");

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from service where sourcecontrol = 'github.com' and organization = 'DockstoreTestUser2' and repository = 'test-service'",
            long.class);
        assertEquals(1, count, "there should be one matching service");

        // Test user endpoints
        UsersApi usersApi = new UsersApi(webClient);
        final long userId = testingPostgres.runSelectStatement("select userid from user_entry where entryid = '" + service.getId() + "'", long.class);
        List<io.swagger.client.model.Workflow> services = usersApi.userServices(userId);
        List<io.swagger.client.model.Workflow> workflows = usersApi.userWorkflows(userId);
        assertEquals(1, services.size(), "There should be one service");
        assertEquals(0, workflows.size(), "There should be no workflows");

        // Should not be able to refresh service
        try {
            client.refresh(services.get(0).getId(), false);
            fail("Should not be able refresh a service");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode(), "Should fail since you cannot refresh services.");
        }
    }

    /**
     * Ensures that you cannot create a service if the given user is not on Dockstore
     */
    @Test
    void createServiceNoUser() throws Exception {

        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient("admin@admin.com", testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        String serviceRepo = "DockstoreTestUser2/test-service";
        String installationId = "1179416";

        // Add service
        try {
            client.handleGitHubRelease(serviceRepo, "iamnotarealuser", "refs/tags/1.0", installationId);
            fail("Should not reach this statement.");
        } catch (ApiException ex) {
            assertEquals(LAMBDA_FAILURE, ex.getCode(), "Should have error code 418");
        }

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from service where sourcecontrol = 'github.com' and organization = 'DockstoreTestUser2' and repository = 'test-service'",
            long.class);
        assertEquals(0, count, "there should be no matching service");
    }

    /**
     * Ensures that a service and workflow can have the same path
     *
     * @throws Exception
     */
    @Test
    void testServiceWithSamePathAsWorkflow() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        final String github = SourceControl.GITHUB.toString();
        String serviceRepo = "DockstoreTestUser2/test-service";
        String installationId = "1179416";

        // Add service
        client.handleGitHubRelease(serviceRepo, BasicIT.USER_2_USERNAME, "refs/tags/1.0", installationId);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from service", long.class);
        assertEquals(1, workflowCount);

        // Add workflow with same path as service
        final io.swagger.client.model.Workflow workflow = client
            .manualRegister("github", serviceRepo, "/Dockstore.cwl", "", "cwl", "/test.json");
        assertNotNull(workflow);

        // forcibly publish both for testing
        testingPostgres.runUpdateStatement("update workflow set ispublished = 't'");
        testingPostgres.runUpdateStatement("update service set ispublished = 't'");

        // test retrieval
        final io.swagger.client.model.Workflow returnedWorkflow = client.getPublishedWorkflowByPath(github + "/" + serviceRepo, BIOWORKFLOW, "",  null);
        final io.swagger.client.model.Workflow returnedService = client.getPublishedWorkflowByPath(github + "/" + serviceRepo, SERVICE, "",  null);
        assertNotSame(returnedWorkflow.getId(), returnedService.getId());

        // test GA4GH retrieval
        Ga4GhApi ga4GhApi = new Ga4GhApi(webClient);
        final Tool tool1 = ga4GhApi.toolsIdGet(ToolsImplCommon.WORKFLOW_PREFIX + "/" + github + "/" + serviceRepo);
        final Tool tool2 = ga4GhApi.toolsIdGet(ToolsImplCommon.SERVICE_PREFIX + "/" + github + "/" + serviceRepo);
        assertNotSame(tool1.getId(), tool2.getId());
    }

    /**
     * This tests that you can't add a version that doesn't exist
     */
    @Test
    void updateServiceIncorrectTag() throws Exception {

        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient("admin@admin.com", testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        String serviceRepo = "DockstoreTestUser2/test-service";
        String installationId = "1179416";

        // Add version that doesn't exist
        try {
            client.handleGitHubRelease(serviceRepo, "admin@admin.com", "refs/tags/1.0-fake", installationId);
            fail("Should not reach this statement.");
        } catch (ApiException ex) {
            assertEquals(LAMBDA_FAILURE, ex.getCode(), "Should have error code 418");
        }
    }

    /**
     * This tests that you can't add a version with an invalid dockstore.yml or no dockstore.yml
     */
    @Test
    void updateServiceNoOrInvalidYml() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient("admin@admin.com", testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        String serviceRepo = "DockstoreTestUser2/test-service";
        String installationId = "1179416";

        // Add version that has no dockstore.yml
        try {
            client.handleGitHubRelease(serviceRepo, "admin@admin.com", "refs/tags/no-yml", installationId);
            fail("Should not reach this statement.");
        } catch (ApiException ex) {
            assertEquals(LAMBDA_FAILURE, ex.getCode(), "Should have error code 418");
        }

        // Add version that has invalid dockstore.yml
        try {
            client.handleGitHubRelease(serviceRepo, "admin@admin.com", "refs/tags/invalid-yml", installationId);
            fail("Should not reach this statement.");
        } catch (ApiException ex) {
            assertEquals(LAMBDA_FAILURE, ex.getCode(), "Should have error code 418");
        }
    }

    /**
     * Tests that refresh will only grab the releases
     */
    @Test
    void updateServiceSync() throws Exception {
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient("DockstoreTestUser2", testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        String serviceRepo = "DockstoreTestUser2/test-service";
        String installationId = "1179416";

        // Add service
        client.handleGitHubRelease(serviceRepo, "DockstoreTestUser2", "refs/tags/1.0", installationId);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from service", long.class);
        assertEquals(1, workflowCount);

        io.swagger.client.model.Workflow service = client.getWorkflowByPath("github.com/" + serviceRepo, SERVICE, "");
        // io.swagger.client.model.Workflow service = services.get(0);
        try {
            client.refresh(service.getId(), false);
            fail("Should fail on refresh and not reach this point");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode(), "Should not be able to refresh a dockstore.yml service.");
        }
    }

    /**
     * This tests that you cannot create a service from an in invalid GitHub repository
     */
    @Test
    void createServiceNoGitHubRepo() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getWebClient("admin@admin.com", testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        String serviceRepo = "DockstoreTestUser2/test-service-foo-bar-not-real";
        String installationId = "1179416";

        // Add service
        try {
            client.handleGitHubRelease(serviceRepo, "admin@admin.com", "refs/tags/1.0", installationId);
            fail("Should not reach this statement.");
        } catch (ApiException ex) {
            assertEquals(LAMBDA_FAILURE, ex.getCode(), "Should have error code 418");
        }
    }

    private class CreateContent {
        private long workflowID;
        private long serviceID;
        private long serviceID2;

        long getWorkflowID() {
            return workflowID;
        }

        long getServiceID() {
            return serviceID;
        }

        long getServiceID2() {
            return serviceID2;
        }

        CreateContent invoke() {
            return invoke(true);
        }

        CreateContent invoke(boolean cleanup) {
            final Transaction transaction = session.beginTransaction();

            Workflow testWorkflow = new BioWorkflow();
            testWorkflow.setDescription("foo workflow");
            testWorkflow.setIsPublished(true);
            testWorkflow.setSourceControl(SourceControl.GITHUB);
            testWorkflow.setDescriptorType(DescriptorLanguage.CWL);
            testWorkflow.setOrganization("shield");
            testWorkflow.setRepository("shield_repo");

            Service testService = new Service();
            testService.setDescription("test service");
            testService.setIsPublished(true);
            testService.setSourceControl(SourceControl.GITHUB);
            testService.setDescriptorType(DescriptorLanguage.SERVICE);
            testService.setMode(WorkflowMode.DOCKSTORE_YML);
            testService.setOrganization("hydra");
            testService.setRepository("hydra_repo");
            testService.setDefaultWorkflowPath(DOCKSTORE_YML_PATH);

            Service test2Service = new Service();
            test2Service.setDescription("test service");
            test2Service.setIsPublished(true);
            test2Service.setSourceControl(SourceControl.GITHUB);
            test2Service.setMode(WorkflowMode.DOCKSTORE_YML);
            test2Service.setDescriptorType(DescriptorLanguage.SERVICE);
            test2Service.setOrganization("hydra");
            test2Service.setRepository("hydra_repo2");
            test2Service.setDefaultWorkflowPath(DOCKSTORE_YML_PATH);

            final Map<DescriptorLanguage.FileType, String> defaultPaths = test2Service.getDefaultPaths();
            for (DescriptorLanguage.FileType val : DescriptorLanguage.FileType.values()) {
                defaultPaths.put(val, "path for " + val);
            }
            test2Service.setDefaultPaths(defaultPaths);

            // add all users to all things for now
            for (User user : userDAO.findAll()) {
                testWorkflow.addUser(user);
                testService.addUser(user);
                test2Service.addUser(user);
            }

            workflowID = workflowDAO.create(testWorkflow);
            serviceID = serviceDAO.create(testService);
            serviceID2 = serviceDAO.create(test2Service);

            assertTrue(workflowID != 0 && serviceID != 0);

            session.flush();
            transaction.commit();
            if (cleanup) {
                session.close();
            }
            return this;
        }
    }
}
