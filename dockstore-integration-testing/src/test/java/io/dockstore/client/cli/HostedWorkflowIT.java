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

package io.dockstore.client.cli;

import static io.dockstore.common.DescriptorLanguage.CWL;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Registry;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.HostedApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.Entry;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.openapi.model.DescriptorType;
import org.hibernate.Session;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.Assertions;
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
@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
class HostedWorkflowIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());


    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();


        // used to allow us to use workflowDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);

    }
    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }


    @Test
    void testDeleteWithoutDefaultVersion() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        final WorkflowsApi workflowsApi = new WorkflowsApi(getOpenAPIWebClient(USER_2_USERNAME, testingPostgres));

        // Test same for hosted workflows
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow(null, "awesomeTool", CWL.getShortName(), null, null);
        SourceFile file = new SourceFile();
        file.setContent("cwlVersion: v1.0\n" + "class: Workflow");
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        file.setAbsolutePath("/Dockstore.cwl");
        hostedWorkflow = hostedApi.editHostedWorkflow(Lists.newArrayList(file), hostedWorkflow.getId());
        file.setContent("cwlVersion: v1.1\n" + "class: Workflow");
        hostedWorkflow = hostedApi.editHostedWorkflow(Lists.newArrayList(file), hostedWorkflow.getId());
        WorkflowVersion hostedVersion = workflowsApi.getWorkflowVersions(hostedWorkflow.getId()).get(0);

        // delete default version via DB
        testingPostgres.runUpdateStatement("update workflow set actualDefaultVersion = null");
        final Entry entry = hostedApi.deleteHostedWorkflowVersion(hostedWorkflow.getId(), hostedVersion.getName());
        // we're really checking that deleting a workflow version did not result in an exception
        assertNotNull(entry);
    }

    @Test
    void testGetEntryByPath() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        Entry foundEntry;

        // Find a hosted workflow
        Workflow workflow = hostedApi.createHostedWorkflow(null, "name", DescriptorType.CWL.toString(), null, null);
        try {
            foundEntry = workflowsApi.getEntryByPath("dockstore.org/DockstoreTestUser2/name");
            Assertions.assertEquals(workflow.getId(), foundEntry.getId());
        } catch (ApiException e) {
            Assertions.fail("Should be able to find the workflow entry with path " + workflow.getFullWorkflowPath());
        }

        // Try to find a workflow that doesn't exist
        try {
            workflowsApi.getEntryByPath("workflow/does/not/exist");
            Assertions.fail("Should not be able to find a workflow that doesn't exist.");
        } catch (ApiException e) {
            Assertions.assertEquals("Entry not found", e.getMessage());
        }

        // Find a hosted tool -> simple case where the repo-name has no slashes: 'foo', no tool name
        DockstoreTool tool = hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo",
            DescriptorType.CWL.toString(), "abcd1234", null);
        try {
            foundEntry = workflowsApi.getEntryByPath("public.ecr.aws/abcd1234/foo");
            Assertions.assertEquals(tool.getId(), foundEntry.getId());
        } catch (ApiException e) {
            Assertions.fail("Should be able to find the tool entry with path " + tool.getToolPath());
        }

        // Find a hosted tool -> repo name: 'foo/bar', no tool name
        tool = hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo/bar", DescriptorType.CWL.toString(), "abcd1234", null);
        try {
            foundEntry = workflowsApi.getEntryByPath("public.ecr.aws/abcd1234/foo/bar");
            Assertions.assertEquals(tool.getId(), foundEntry.getId());
        } catch (ApiException e) {
            Assertions.fail("Should be able to find the tool entry with path " + tool.getToolPath());
        }

        // Find a hosted tool -> repo-name: 'foo/bar', tool name: 'tool-name'
        tool = hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo/bar", DescriptorType.CWL.toString(), "abcd1234", "tool-name");
        try {
            foundEntry = workflowsApi.getEntryByPath("public.ecr.aws/abcd1234/foo/bar/tool-name");
            Assertions.assertEquals(tool.getId(), foundEntry.getId());
        } catch (ApiException e) {
            Assertions.fail("Should be able to find the tool entry with path " + tool.getToolPath());
        }
    }

    @Test
    void testAmazonECRHostedToolCreation() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        ContainersApi containersApi = new ContainersApi(webClient);

        // Create a hosted Amazon ECR tool using a private repository
        DockstoreTool tool = hostedApi.createHostedTool("test.dkr.ecr.us-east-1.amazonaws.com", "foo", DescriptorType.CWL.toString(), "namespace", "bar");
        Assertions.assertNotNull(containersApi.getContainer(tool.getId(), ""));

        // Create a hosted Amazon ECR tool using a public repository
        tool = hostedApi.createHostedTool("public.ecr.aws", "foo", DescriptorType.CWL.toString(), "namespace", "bar");
        Assertions.assertNotNull(containersApi.getContainer(tool.getId(), ""));
    }

    @Test
    void testDuplicateAmazonECRHostedToolCreation() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        String alreadyExistsMessage = "already exists";

        // Simple case: the two tools have the same names and entry names
        hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo", DescriptorType.CWL.toString(), "abcd1234", null);
        assertThrows(ApiException.class, () ->  hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo", DescriptorType.CWL.toString(), "abcd1234", null), alreadyExistsMessage);

        // The two tools have different names and entry names, but the tool paths are the same
        // Scenario 1:
        // Tool 1 has name: 'foo/bar' and no entry name
        // Tool 2 has name: 'foo' and entry name: 'bar'
        hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo/bar", DescriptorType.CWL.toString(), "abcd1234", null);
        assertThrows(ApiException.class, () ->   hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo", DescriptorType.CWL.toString(), "abcd1234", "bar"), alreadyExistsMessage);

        // Scenario 2:
        // Tool 1 has name: 'foo' and entry name: 'bar'
        // Tool 2 has name: 'foo/bar' and no entry name
        hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo", DescriptorType.CWL.toString(), "wxyz6789", "bar");
        assertThrows(ApiException.class, () ->   hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo/bar", DescriptorType.CWL.toString(), "wxyz6789", null), alreadyExistsMessage);
    }
}
