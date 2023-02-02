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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import io.swagger.model.DescriptorType;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
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
    public static final String DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME = "DockstoreTestUser2/hello-dockstore-workflow";

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
    void testHostedEditAndDelete() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);

        Workflow workflow = manualRegisterAndPublish(workflowApi, DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "", "cwl", SourceControl.GITHUB,
            "/Dockstore.cwl", false);

        // using hosted apis to delete normal workflow should fail
        HostedApi hostedApi = new HostedApi(webClient);
        try {
            hostedApi.deleteHostedWorkflowVersion(workflow.getId(), "v1.0");
            Assertions.fail("Should throw API exception");
        } catch (ApiException e) {
            Assertions.assertTrue(e.getMessage().contains("cannot modify non-hosted entries this way"));
        }

        // using hosted apis to edit normal workflow should fail
        try {
            hostedApi.editHostedWorkflow(workflow.getId(), new ArrayList<>());
            Assertions.fail("Should throw API exception");
        } catch (ApiException e) {
            Assertions.assertTrue(e.getMessage().contains("cannot modify non-hosted entries this way"));
        }
    }

    @Test
    void testHiddenAndDefaultVersions() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow workflow = workflowsApi.manualRegister("github", DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.wdl", "", DescriptorLanguage.WDL.toString(), "/test.json");

        workflow = workflowsApi.refresh(workflow.getId(), false);
        List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        WorkflowVersion version = workflowVersions.stream().filter(w -> w.getReference().equals("testBoth")).findFirst().get();
        version.setHidden(true);
        workflowsApi.updateWorkflowVersion(workflow.getId(), Collections.singletonList(version));

        try {
            workflow = workflowsApi.updateWorkflowDefaultVersion(workflow.getId(), version.getName());
            Assertions.fail("Shouldn't be able to set the default version to one that is hidden.");
        } catch (ApiException ex) {
            Assertions.assertEquals("You can not set the default version to a hidden version.", ex.getMessage());
        }

        // Set the default version to a non-hidden version
        version.setHidden(false);
        workflowsApi.updateWorkflowVersion(workflow.getId(), Collections.singletonList(version));
        workflow = workflowsApi.updateWorkflowDefaultVersion(workflow.getId(), version.getName());

        // Should not be able to hide a default version
        version.setHidden(true);
        try {
            workflowsApi.updateWorkflowVersion(workflow.getId(), Collections.singletonList(version));
            Assertions.fail("Should not be able to hide a default version");
        } catch (ApiException ex) {
            Assertions.assertEquals("You cannot hide the default version.", ex.getMessage());
        }

        // Test same for hosted workflows
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", null, CWL.getShortName(), null, null);
        SourceFile file = new SourceFile();
        file.setContent("cwlVersion: v1.0\n" + "class: Workflow");
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        file.setAbsolutePath("/Dockstore.cwl");
        hostedWorkflow = hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));

        WorkflowVersion hostedVersion = workflowsApi.getWorkflowVersions(hostedWorkflow.getId()).get(0);
        hostedVersion.setHidden(true);
        try {
            workflowsApi.updateWorkflowVersion(hostedWorkflow.getId(), Collections.singletonList(hostedVersion));
            Assertions.fail("Shouldn't be able to hide the default version.");
        } catch (ApiException ex) {
            Assertions.assertEquals("You cannot hide the default version.", ex.getMessage());
        }

        file.setContent("""
            cwlVersion: v1.0

            class: Workflow""");
        hostedWorkflow = hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));
        hostedVersion = workflowsApi.getWorkflowVersions(hostedWorkflow.getId()).stream().filter(v -> v.getName().equals("1")).findFirst().get();
        hostedVersion.setHidden(true);
        workflowsApi.updateWorkflowVersion(hostedWorkflow.getId(), Collections.singletonList(hostedVersion));

        try {
            workflowsApi.updateWorkflowDefaultVersion(hostedWorkflow.getId(), hostedVersion.getName());
            Assertions.fail("Shouldn't be able to set the default version to one that is hidden.");
        } catch (ApiException ex) {
            Assertions.assertEquals("You can not set the default version to a hidden version.", ex.getMessage());
        }
    }

    @Test
    void testCreationOfIncorrectHostedWorkflowTypeGarbage() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        assertThrows(ApiException.class, () -> hostedApi.createHostedWorkflow("name", null, "garbage type", null, null));

    }

    @Test
    void testGetEntryByPath() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.HostedApi hostedApi = new io.dockstore.openapi.client.api.HostedApi(webClient);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        io.dockstore.openapi.client.model.Entry foundEntry;

        // Find a hosted workflow
        io.dockstore.openapi.client.model.Workflow workflow = hostedApi.createHostedWorkflow(null, "name", io.openapi.model.DescriptorType.CWL.toString(), null, null);
        try {
            foundEntry = workflowsApi.getEntryByPath("dockstore.org/DockstoreTestUser2/name");
            Assertions.assertEquals(workflow.getId(), foundEntry.getId());
        } catch (io.dockstore.openapi.client.ApiException e) {
            Assertions.fail("Should be able to find the workflow entry with path " + workflow.getFullWorkflowPath());
        }

        // Try to find a workflow that doesn't exist
        try {
            workflowsApi.getEntryByPath("workflow/does/not/exist");
            Assertions.fail("Should not be able to find a workflow that doesn't exist.");
        } catch (io.dockstore.openapi.client.ApiException e) {
            Assertions.assertEquals("Entry not found", e.getMessage());
        }

        // Find a hosted tool -> simple case where the repo-name has no slashes: 'foo', no tool name
        io.dockstore.openapi.client.model.DockstoreTool tool = hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo",
            io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", null);
        try {
            foundEntry = workflowsApi.getEntryByPath("public.ecr.aws/abcd1234/foo");
            Assertions.assertEquals(tool.getId(), foundEntry.getId());
        } catch (io.dockstore.openapi.client.ApiException e) {
            Assertions.fail("Should be able to find the tool entry with path " + tool.getToolPath());
        }

        // Find a hosted tool -> repo name: 'foo/bar', no tool name
        tool = hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo/bar", io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", null);
        try {
            foundEntry = workflowsApi.getEntryByPath("public.ecr.aws/abcd1234/foo/bar");
            Assertions.assertEquals(tool.getId(), foundEntry.getId());
        } catch (io.dockstore.openapi.client.ApiException e) {
            Assertions.fail("Should be able to find the tool entry with path " + tool.getToolPath());
        }

        // Find a hosted tool -> repo-name: 'foo/bar', tool name: 'tool-name'
        tool = hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo/bar", io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", "tool-name");
        try {
            foundEntry = workflowsApi.getEntryByPath("public.ecr.aws/abcd1234/foo/bar/tool-name");
            Assertions.assertEquals(tool.getId(), foundEntry.getId());
        } catch (io.dockstore.openapi.client.ApiException e) {
            Assertions.fail("Should be able to find the tool entry with path " + tool.getToolPath());
        }
    }
    @Test
    void testDuplicateHostedWorkflowCreation() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        hostedApi.createHostedWorkflow("name", null, DescriptorType.CWL.toString(), null, null);
        assertThrows(ApiException.class, () -> hostedApi.createHostedWorkflow("name", null, DescriptorType.CWL.toString(), null, null), "already exists");
    }

    @Test
    void testDuplicateHostedToolCreation() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        hostedApi.createHostedTool("name", Registry.DOCKER_HUB.getDockerPath(), DescriptorType.CWL.toString(), "namespace", null);
        assertThrows(ApiException.class, () -> hostedApi.createHostedTool("name", Registry.DOCKER_HUB.getDockerPath(), DescriptorType.CWL.toString(), "namespace", null), "already exists");
    }

    @Test
    void testAmazonECRHostedToolCreation() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.HostedApi hostedApi = new io.dockstore.openapi.client.api.HostedApi(webClient);
        io.dockstore.openapi.client.api.ContainersApi containersApi = new io.dockstore.openapi.client.api.ContainersApi(webClient);

        // Create a hosted Amazon ECR tool using a private repository
        io.dockstore.openapi.client.model.DockstoreTool tool = hostedApi.createHostedTool("test.dkr.ecr.us-east-1.amazonaws.com", "foo", io.openapi.model.DescriptorType.CWL.toString(), "namespace", "bar");
        Assertions.assertNotNull(containersApi.getContainer(tool.getId(), ""));

        // Create a hosted Amazon ECR tool using a public repository
        tool = hostedApi.createHostedTool("public.ecr.aws", "foo", io.openapi.model.DescriptorType.CWL.toString(), "namespace", "bar");
        Assertions.assertNotNull(containersApi.getContainer(tool.getId(), ""));
    }

    @Test
    void testDuplicateAmazonECRHostedToolCreation() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.HostedApi hostedApi = new io.dockstore.openapi.client.api.HostedApi(webClient);
        String alreadyExistsMessage = "already exists";

        // Simple case: the two tools have the same names and entry names
        hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo", io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", null);
        assertThrows(io.dockstore.openapi.client.ApiException.class, () ->  hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo", io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", null), alreadyExistsMessage);

        // The two tools have different names and entry names, but the tool paths are the same
        // Scenario 1:
        // Tool 1 has name: 'foo/bar' and no entry name
        // Tool 2 has name: 'foo' and entry name: 'bar'
        hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo/bar", io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", null);
        assertThrows(io.dockstore.openapi.client.ApiException.class, () ->   hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo", io.openapi.model.DescriptorType.CWL.toString(), "abcd1234", "bar"), alreadyExistsMessage);

        // Scenario 2:
        // Tool 1 has name: 'foo' and entry name: 'bar'
        // Tool 2 has name: 'foo/bar' and no entry name
        hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo", io.openapi.model.DescriptorType.CWL.toString(), "wxyz6789", "bar");
        assertThrows(io.dockstore.openapi.client.ApiException.class, () ->   hostedApi.createHostedTool(Registry.AMAZON_ECR.getDockerPath(), "foo/bar", io.openapi.model.DescriptorType.CWL.toString(), "wxyz6789", null), alreadyExistsMessage);
    }

    @Test
    void testHostedWorkflowMetadata() throws IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("name", null, DescriptorType.CWL.toString(), null, null);
        Assertions.assertNotNull(hostedWorkflow.getLastModifiedDate());
        Assertions.assertNotNull(hostedWorkflow.getLastUpdated());

        // make a couple garbage edits
        SourceFile source = new SourceFile();
        source.setPath("/Dockstore.cwl");
        source.setAbsolutePath("/Dockstore.cwl");
        source.setContent("cwlVersion: v1.0\nclass: Workflow");
        source.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        SourceFile source1 = new SourceFile();
        source1.setPath("sorttool.cwl");
        source1.setContent("foo");
        source1.setAbsolutePath("/sorttool.cwl");
        source1.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        SourceFile source2 = new SourceFile();
        source2.setPath("revtool.cwl");
        source2.setContent("foo");
        source2.setAbsolutePath("/revtool.cwl");
        source2.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(source, source1, source2));

        source.setContent("cwlVersion: v1.0\nclass: Workflow");
        source1.setContent("food");
        source2.setContent("food");
        final Workflow updatedHostedWorkflow = hostedApi
            .editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(source, source1, source2));
        Assertions.assertNotNull(updatedHostedWorkflow.getLastModifiedDate());
        Assertions.assertNotNull(updatedHostedWorkflow.getLastUpdated());

        // note that this workflow contains metadata defined on the inputs to the workflow in the old (pre-map) CWL way that is still valid v1.0 CWL
        source.setContent(FileUtils
            .readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/Dockstore.cwl")), StandardCharsets.UTF_8));
        source1.setContent(
            FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/sorttool.cwl")), StandardCharsets.UTF_8));
        source2.setContent(
            FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/revtool.cwl")), StandardCharsets.UTF_8));
        Workflow workflow = hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(source, source1, source2));
        Assertions.assertFalse(workflow.getInputFileFormats().isEmpty());
        Assertions.assertFalse(workflow.getOutputFileFormats().isEmpty());
    }
}
