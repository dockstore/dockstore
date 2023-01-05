/*
 *    Copyright 2022 OICR and UCSC
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.BitBucketTest;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.resources.WorkflowResource;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.BioWorkflow;
import io.swagger.client.model.Repository;
import io.swagger.client.model.Workflow;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(BitBucketTest.NAME)
public class BitBucketUserResourceIT extends BaseIT {


    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres, true);
    }


    /**
     * Tests that the endpoints for the wizard registration work.
     * @throws ApiException
     */
    @Test
    public void testWizardEndpoints() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        List<String> registries = userApi.getUserRegistries();
        assertTrue(registries.size() > 0);
        assertTrue(registries.contains(SourceControl.GITHUB.toString()));
        assertTrue(registries.contains(SourceControl.GITLAB.toString()));
        assertTrue(registries.contains(SourceControl.BITBUCKET.toString()));

        // Test GitHub
        List<String> orgs = userApi.getUserOrganizations(SourceControl.GITHUB.name());
        assertTrue(orgs.size() > 0);
        assertTrue(orgs.contains("dockstoretesting"));
        assertTrue(orgs.contains("DockstoreTestUser"));
        assertTrue(orgs.contains("DockstoreTestUser2"));

        List<Repository> repositories = userApi.getUserOrganizationRepositories(SourceControl.GITHUB.name(), "dockstoretesting");
        assertTrue(repositories.size() > 0);
        assertTrue(repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstoretesting/basic-tool") && !repo.isPresent()));
        assertTrue(repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstoretesting/basic-workflow") && !repo.isPresent()));

        // Register a workflow
        BioWorkflow ghWorkflow = workflowsApi.addWorkflow(SourceControl.GITHUB.name(), "dockstoretesting", "basic-workflow");
        assertNotNull(ghWorkflow, "GitHub workflow should be added");
        assertEquals("github.com/dockstoretesting/basic-workflow", ghWorkflow.getFullWorkflowPath());

        // dockstoretesting/basic-workflow should be present now
        repositories = userApi.getUserOrganizationRepositories(SourceControl.GITHUB.name(), "dockstoretesting");
        assertTrue(repositories.size() > 0);
        assertTrue(repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstoretesting/basic-tool") && !repo.isPresent()));
        assertTrue(repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstoretesting/basic-workflow") && repo.isPresent()));

        // Try deleting a workflow
        workflowsApi.deleteWorkflow(SourceControl.GITHUB.name(), "dockstoretesting", "basic-workflow");
        Workflow deletedWorkflow = null;
        try {
            deletedWorkflow = workflowsApi.getWorkflow(ghWorkflow.getId(), null);
            assertFalse(false, "Should not reach here as entry should not exist");
        } catch (ApiException ex) {
            assertNull(deletedWorkflow, "Workflow should be null");
        }

        // Try making a repo undeletable
        ghWorkflow = workflowsApi.addWorkflow(SourceControl.GITHUB.name(), "dockstoretesting", "basic-workflow");
        workflowsApi.refresh(ghWorkflow.getId(), false);
        repositories = userApi.getUserOrganizationRepositories(SourceControl.GITHUB.name(), "dockstoretesting");
        assertTrue(repositories.size() > 0);
        assertTrue(repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstoretesting/basic-workflow") && repo.isPresent() && !repo.isCanDelete()));

        // Test Gitlab
        orgs = userApi.getUserOrganizations(SourceControl.GITLAB.name());
        assertTrue(orgs.size() > 0);
        assertTrue(orgs.contains("dockstore.test.user2"));

        repositories = userApi.getUserOrganizationRepositories(SourceControl.GITLAB.name(), "dockstore.test.user2");
        assertTrue(repositories.size() > 0);
        assertTrue(repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstore.test.user2/dockstore-workflow-md5sum-unified") && !repo.isPresent()));
        assertTrue(repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstore.test.user2/dockstore-workflow-example") && !repo.isPresent()));

        // Register a workflow
        BioWorkflow glWorkflow = workflowsApi.addWorkflow(SourceControl.GITLAB.name(), "dockstore.test.user2", "dockstore-workflow-example");
        assertEquals("gitlab.com/dockstore.test.user2/dockstore-workflow-example", glWorkflow.getFullWorkflowPath());

        // dockstore.test.user2/dockstore-workflow-example should be present now
        repositories = userApi.getUserOrganizationRepositories(SourceControl.GITLAB.name(), "dockstore.test.user2");
        assertTrue(repositories.size() > 0);
        assertTrue(repositories.stream().anyMatch(repo -> Objects.equals(repo.getPath(), "dockstore.test.user2/dockstore-workflow-example") && repo.isPresent()));

        // Try registering the workflow again (duplicate) should fail
        try {
            workflowsApi.addWorkflow(SourceControl.GITLAB.name(), "dockstore.test.user2", "dockstore-workflow-example");
            assertFalse(false, "Should not reach this, should fail");
        } catch (ApiException ex) {
            assertTrue(ex.getMessage().contains("already exists"), "Should have error message that workflow already exists.");
        }

        // Try registering a hosted workflow
        try {
            BioWorkflow dsWorkflow = workflowsApi.addWorkflow(SourceControl.DOCKSTORE.name(), "foo", "bar");
            assertFalse(false, "Should not reach this, should fail");
        } catch (ApiException ex) {
            assertTrue(ex.getMessage().contains(WorkflowResource.SC_REGISTRY_ACCESS_MESSAGE), "Should have error message that hosted workflows cannot be added this way.");
        }

    }


}
