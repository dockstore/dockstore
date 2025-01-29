/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice;

import static io.dockstore.common.RepositoryConstants.DockstoreTestUser2;
import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubRelease;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.RepositoryConstants.DockstoreTesting;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.webservice.core.EntryTypeMetadata;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.jdbi.FileDAO;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.model.Tool;
import java.util.List;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(BaseIT.TestStatus.class)
@Tag(ConfidentialTest.NAME)
public class OpenAPIServiceIT extends BaseIT {
    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private Session session;
    private FileDAO fileDAO;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        this.fileDAO = new FileDAO(sessionFactory);

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use tokenDAO outside of the web service
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @BeforeEach
    @Override
    public void resetDBBetweenTests() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    /**
     * This tests endpoints that will be triggered by GitHub App webhooks.
     * A service is created and a version is added for a release 1.0
     */
    @Test
    void testGitHubAppEndpoints() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Add version
        handleGitHubRelease(client, DockstoreTestUser2.TEST_SERVICE, "refs/tags/1.0", USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from service", long.class);
        assertEquals(1, workflowCount);
        Workflow service = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_SERVICE, WorkflowSubClass.SERVICE, "versions");

        final long validFileCount = testingPostgres.runSelectStatement("select count(*) from validation where valid", long.class);
        assertEquals(2, validFileCount, "Both the service's files should be valid");

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
        List<Workflow> services = usersApi.userServices(userId);
        List<Workflow> workflows = usersApi.userWorkflows(userId);
        assertEquals(1, services.size(), "There should be one service");
        assertEquals(0, workflows.size(), "There should be no workflows");

        // Should not be able to refresh service
        ApiException ex = assertThrows(ApiException.class, () -> client.refresh1(services.get(0).getId(), false));
        assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode(), "Should fail since you cannot refresh services.");

        // A service with descriptortypesubclass set to "n/a" can be updated
        testingPostgres.runUpdateStatement("update service set descriptortypesubclass = 'n/a'");
        testingPostgres.runUpdateStatement("delete from workflowversion wv where wv.parentid in (select id from service)");
        Workflow naService = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_SERVICE, WorkflowSubClass.SERVICE, "versions");
        assertEquals(0, naService.getWorkflowVersions().size(), "WorkflowVersions size should be reset to 0 via SQL");
        handleGitHubRelease(client, DockstoreTestUser2.TEST_SERVICE, "refs/tags/1.0", USER_2_USERNAME);
        naService = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_SERVICE, WorkflowSubClass.SERVICE, "versions");
        assertEquals(1, naService.getWorkflowVersions().size(), "WorkflowVersions size should be 1 after GitHub releease");
    }

    @Test
    void testReleaseAndPublish() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        // Test that a valid 1.2 service can be registered and published. #5636
        handleGitHubRelease(client, DockstoreTesting.TEST_SERVICE, "refs/tags/oneTwoSchema", USER_2_USERNAME);
        Workflow service = client.getWorkflowByPath("github.com/" + DockstoreTesting.TEST_SERVICE, WorkflowSubClass.SERVICE, null);
        assertTrue(service.isIsPublished(), "Service should have been published");
    }

    /**
     * Ensures that you can create a service if the given user is not on Dockstore
     */
    @Test
    void createServiceNoUser() {
        final ApiClient webClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Add service
        handleGitHubRelease(client, DockstoreTestUser2.TEST_SERVICE, "refs/tags/1.0", "iamnotarealuser");

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from service where sourcecontrol = 'github.com' and organization = 'DockstoreTestUser2' and repository = 'test-service'",
            long.class);
        assertEquals(1, count, "there should be one matching service");
    }

    /**
     * Ensures that a service and workflow can have the same path
     *
     */
    @Test
    void testServiceWithSamePathAsWorkflow() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Add service
        handleGitHubRelease(client, DockstoreTestUser2.TEST_SERVICE, "refs/tags/1.0", USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from service", long.class);
        assertEquals(1, workflowCount);

        // Add workflow with same path as service
        final Workflow workflow = client
            .manualRegister("github", DockstoreTestUser2.TEST_SERVICE, "/Dockstore.cwl", "", "cwl", "/test.json");
        assertNotNull(workflow);

        // forcibly publish both for testing
        testingPostgres.runUpdateStatement("update workflow set ispublished = 't', waseverpublic = 't'");
        testingPostgres.runUpdateStatement("update service set ispublished = 't', waseverpublic = 't'");

        // test retrieval
        final Workflow returnedWorkflow = client.getPublishedWorkflowByPath(SourceControl.GITHUB + "/" + DockstoreTestUser2.TEST_SERVICE, WorkflowSubClass.BIOWORKFLOW, "",  null);
        final Workflow returnedService = client.getPublishedWorkflowByPath(SourceControl.GITHUB + "/" + DockstoreTestUser2.TEST_SERVICE, WorkflowSubClass.SERVICE, "",  null);
        assertNotSame(returnedWorkflow.getId(), returnedService.getId());

        // test GA4GH retrieval
        Ga4GhApi ga4GhApi = new Ga4GhApi(getWebClient(USER_2_USERNAME, testingPostgres));
        final Tool tool1 = ga4GhApi.toolsIdGet(EntryTypeMetadata.WORKFLOW.getTrsPrefix() + "/" + SourceControl.GITHUB + "/" + DockstoreTestUser2.TEST_SERVICE);
        final Tool tool2 = ga4GhApi.toolsIdGet(EntryTypeMetadata.SERVICE.getTrsPrefix() + "/" + SourceControl.GITHUB + "/" + DockstoreTestUser2.TEST_SERVICE);
        assertNotSame(tool1.getId(), tool2.getId());
    }

    /**
     * This tests that you can't add a version that doesn't exist
     */
    @Test
    void updateServiceIncorrectTag() {
        final ApiClient webClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Add version that doesn't exist
        ApiException ex = assertThrows(ApiException.class, () -> handleGitHubRelease(client, DockstoreTestUser2.TEST_SERVICE, "refs/tags/1.0-fake", ADMIN_USERNAME));
        assertEquals(LAMBDA_FAILURE, ex.getCode(), "Should have error code 418");
    }

    /**
     * This tests that you can't add a version with an invalid dockstore.yml or no dockstore.yml
     */
    @Test
    void updateServiceNoOrInvalidYml() {
        final ApiClient webClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Add version that has no dockstore.yml
        ApiException ex = assertThrows(ApiException.class, () -> handleGitHubRelease(client, DockstoreTestUser2.TEST_SERVICE, "refs/tags/no-yml", ADMIN_USERNAME));
        assertEquals(LAMBDA_FAILURE, ex.getCode(), "Should have error code 418");

        // Add version that has invalid dockstore.yml
        ex = assertThrows(ApiException.class, () -> handleGitHubRelease(client, DockstoreTestUser2.TEST_SERVICE, "refs/tags/invalid-yml", ADMIN_USERNAME));
        assertEquals(LAMBDA_FAILURE, ex.getCode(), "Should have error code 418");
    }

    /**
     * Tests that refresh will only grab the releases
     */
    @Test
    void updateServiceSync() {
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Add service
        handleGitHubRelease(client, DockstoreTestUser2.TEST_SERVICE, "refs/tags/1.0", USER_2_USERNAME);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from service", long.class);
        assertEquals(1, workflowCount);

        Workflow service = client.getWorkflowByPath("github.com/" + DockstoreTestUser2.TEST_SERVICE, WorkflowSubClass.SERVICE, "");
        ApiException ex = assertThrows(ApiException.class, () -> client.refresh1(service.getId(), false));
        assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode(), "Should not be able to refresh a dockstore.yml service.");
    }

    /**
    * This tests that you cannot create a service from an in invalid GitHub repository
    */
    @Test
    void createServiceNoGitHubRepo() {
        final ApiClient webClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        String serviceRepo = "DockstoreTestUser2/test-service-foo-bar-not-real";
        ApiException ex = assertThrows(ApiException.class, () -> handleGitHubRelease(client, serviceRepo, "refs/tags/1.0", ADMIN_USERNAME));
        assertEquals(LAMBDA_FAILURE, ex.getCode(), "Should have error code 418");
    }
}
