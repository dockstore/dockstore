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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.LanguageType;
import io.dockstore.common.Registry;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import io.swagger.model.DescriptorType;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests CRUD style operations for tools and workflows hosted directly on Dockstore
 *
 * @author dyuen,agduncan
 */
@Category(ConfidentialTest.class)
public class CRUDClientIT extends BaseIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testToolCreation(){
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        HostedApi api = new HostedApi(webClient);
        DockstoreTool hostedTool = api.createHostedTool("awesomeTool", Registry.QUAY_IO.toString().toLowerCase(), DescriptorLanguage.CWL_STRING, "coolNamespace", null);
        assertNotNull("tool was not created properly", hostedTool);
        // createHostedTool() endpoint is safe to have user profiles because that profile is your own
        assertEquals("One user should belong to this tool, yourself",1, hostedTool.getUsers().size());
        hostedTool.getUsers().forEach(user -> {
            assertNotNull("createHostedTool() endpoint should have user profiles", user.getUserProfiles());
            // Setting it to null afterwards to compare with the getContainer endpoint since that one doesn't return user profiles
            user.setUserProfiles(null);
        });

        assertTrue("tool was not created with a valid id", hostedTool.getId() != 0);
        // can get it back with regular api
        ContainersApi oldApi = new ContainersApi(webClient);
        DockstoreTool container = oldApi.getContainer(hostedTool.getId(), null);
        // clear lazy fields for now till merge
        hostedTool.setAliases(null);
        container.setAliases(null);
        assertEquals(container, hostedTool);
        assertEquals(1, container.getUsers().size());
        container.getUsers().forEach(user -> assertNull("getContainer() endpoint should not have user profiles", user.getUserProfiles()));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testToolEditing() throws IOException {
        HostedApi api = new HostedApi(getWebClient(ADMIN_USERNAME));
        DockstoreTool hostedTool = api.createHostedTool("awesomeTool", Registry.QUAY_IO.toString().toLowerCase(), DescriptorLanguage.CWL_STRING, "coolNamespace", null);
        SourceFile descriptorFile = new SourceFile();
        descriptorFile.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("tar-param.cwl")), StandardCharsets.UTF_8));
        descriptorFile.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        descriptorFile.setPath("/Dockstore.cwl");
        descriptorFile.setAbsolutePath("/Dockstore.cwl");
        SourceFile dockerfile = new SourceFile();
        dockerfile.setContent("FROM ubuntu:latest");
        dockerfile.setType(SourceFile.TypeEnum.DOCKERFILE);
        dockerfile.setPath("/Dockerfile");
        dockerfile.setAbsolutePath("/Dockerfile");
        DockstoreTool dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, dockerfile));
        Optional<Tag> first = dockstoreTool.getTags().stream().max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        assertEquals("correct number of source files", 2, first.get().getSourceFiles().size());
        assertTrue("a tool lacks a date", dockstoreTool.getLastModifiedDate() != null && dockstoreTool.getLastModified() != 0);

        SourceFile file2 = new SourceFile();
        file2.setContent("{\"message\": \"Hello world!\"}");
        file2.setType(SourceFile.TypeEnum.CWL_TEST_JSON);
        file2.setPath("/test.json");
        file2.setAbsolutePath("/test.json");
        // add one file and include the old one implicitly
        dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(file2));
        first = dockstoreTool.getTags().stream().max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        assertEquals("correct number of source files", 3, first.get().getSourceFiles().size());
        String revisionWithTestFile = first.get().getName();

        // delete a file
        file2.setContent(null);

        dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, file2, dockerfile));
        first = dockstoreTool.getTags().stream().max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        assertEquals("correct number of source files", 2, first.get().getSourceFiles().size());

        dockstoreTool = api.deleteHostedToolVersion(hostedTool.getId(), "1");
        assertEquals("should only be two revisions", 2, dockstoreTool.getTags().size());

        //check that all revisions have editing users
        long count = dockstoreTool.getTags().stream().filter(tag -> tag.getVersionEditor() != null).count();
        assertEquals("all versions do not seem to have editors", count, dockstoreTool.getTags().size());

        // ensure that we cannot retrieve files until publication, important for hosted workflows which don't exist publically
        ContainersApi otherUserApi = new ContainersApi(getWebClient(USER_1_USERNAME));
        boolean thrownException = false;
        try {
            otherUserApi.getTestParameterFiles(dockstoreTool.getId(), DescriptorType.CWL.toString(), revisionWithTestFile);
        } catch (ApiException e) {
            thrownException = true;
        }
        assertTrue(thrownException);

        // Publish tool
        ContainersApi containersApi = new ContainersApi(getWebClient(ADMIN_USERNAME));
        PublishRequest pub = SwaggerUtility.createPublishRequest(true);
        containersApi.publish(dockstoreTool.getId(), pub);

        // files should be visible afterwards
        List<SourceFile> files = otherUserApi.getTestParameterFiles(dockstoreTool.getId(), DescriptorType.CWL.toString(), revisionWithTestFile);
        assertTrue(!files.isEmpty() &&!files.get(0).getContent().isEmpty());
    }

    @Test
    public void testWorkflowCreation(){
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        HostedApi api = new HostedApi(webClient);
        Workflow hostedTool = api.createHostedWorkflow("awesomeWorkflow", null, DescriptorLanguage.CWL_STRING, null, null);
        assertNotNull("workflow was not created properly", hostedTool);
        // createHostedWorkflow() endpoint is safe to have user profiles because that profile is your own
        assertEquals(1, hostedTool.getUsers().size());
        hostedTool.getUsers().forEach(user -> {
            assertNotNull("createHostedWorkflow() endpoint should have user profiles", user.getUserProfiles());
            // Setting it to null afterwards to compare with the getWorkflow endpoint since that one doesn't return user profiles
            user.setUserProfiles(null);
        });
        assertTrue("workflow was not created with a valid if", hostedTool.getId() != 0);
        // can get it back with regular api
        WorkflowsApi oldApi = new WorkflowsApi(webClient);
        Workflow container = oldApi.getWorkflow(hostedTool.getId(), null);
        // clear lazy fields for now till merge
        hostedTool.setAliases(null);
        container.setAliases(null);
        assertEquals(1, container.getUsers().size());
        container.getUsers().forEach(user -> assertNull("getWorkflow() endpoint should not have user profiles", user.getUserProfiles()));
        assertEquals(container, hostedTool);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    public void testWorkflowEditing() throws IOException {
        HostedApi api = new HostedApi(getWebClient(ADMIN_USERNAME));
        Workflow hostedWorkflow = api.createHostedWorkflow("awesomeTool", null, DescriptorLanguage.CWL_STRING, null, null);
        SourceFile file = new SourceFile();
        file.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl")), StandardCharsets.UTF_8));
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        file.setAbsolutePath("/Dockstore.cwl");
        Workflow dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));
        Optional<WorkflowVersion> first = dockstoreWorkflow.getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        assertEquals("correct number of source files", 1, first.get().getSourceFiles().size());
        assertTrue("a workflow lacks a date", first.get().getLastModified() != null && first.get().getLastModified().getTime() != 0);

        SourceFile file2 = new SourceFile();
        file2.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("arguments.cwl")), StandardCharsets.UTF_8));
        file2.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file2.setPath("/arguments.cwl");
        file2.setAbsolutePath("/arguments.cwl");
        // add one file and include the old one implicitly
        dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file2));
        first = dockstoreWorkflow .getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        assertEquals("correct number of source files", 2, first.get().getSourceFiles().size());

        SourceFile file3 = new SourceFile();
        file3.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("tar-param.cwl")), StandardCharsets.UTF_8));
        file3.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file3.setPath("/tar-param.cwl");
        file3.setAbsolutePath("/tar-param.cwl");
        // add one file and include the old one implicitly
        dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file3));
        first = dockstoreWorkflow .getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        assertEquals("correct number of source files", 3, first.get().getSourceFiles().size());

        // delete a file
        file2.setContent(null);

        dockstoreWorkflow = api.editHostedWorkflow(dockstoreWorkflow.getId(), Lists.newArrayList(file,file2));
        first = dockstoreWorkflow.getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        assertEquals("correct number of source files", 2, first.get().getSourceFiles().size());

        dockstoreWorkflow = api.deleteHostedWorkflowVersion(hostedWorkflow.getId(), "1");
        assertEquals("should only be three revisions", 3, dockstoreWorkflow.getWorkflowVersions().size());

        //check that all revisions have editing users
        long count = dockstoreWorkflow.getWorkflowVersions().stream().filter(tag -> tag.getVersionEditor() != null).count();
        assertEquals("all versions do not seem to have editors", count, dockstoreWorkflow.getWorkflowVersions().size());

        // ensure that we cannot retrieve files until publication, important for hosted workflows which don't exist publically
        WorkflowsApi otherUserApi = new WorkflowsApi(getWebClient(USER_1_USERNAME));
        boolean thrownException = false;
        try {
            otherUserApi.cwl(dockstoreWorkflow.getId(), first.get().getName());
        } catch (ApiException e) {
            thrownException = true;
        }
        assertTrue(thrownException);

        // Publish workflow
        WorkflowsApi workflowsApi = new WorkflowsApi(getWebClient(ADMIN_USERNAME));
        PublishRequest pub = SwaggerUtility.createPublishRequest(true);
        workflowsApi.publish(dockstoreWorkflow.getId(), pub);

        // files should be visible afterwards
        file = otherUserApi.cwl(dockstoreWorkflow.getId(), first.get().getName());
        assertTrue(!file.getContent().isEmpty());

        // Check that absolute file gets set if not explicity set
        SourceFile file4 = new SourceFile();
        file4.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("tar-param.cwl")), StandardCharsets.UTF_8));
        file4.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        String ABS_PATH_TEST = "/no-absolute-path.cwl";
        file4.setPath(ABS_PATH_TEST);
        file4.setAbsolutePath(null); // Redundant, but clarifies intent of test
        dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file4));
        first = dockstoreWorkflow .getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        Optional<SourceFile> msf = first.get().getSourceFiles().stream().filter(sf -> ABS_PATH_TEST.equals(sf.getPath())).findFirst();
        String absolutePath = msf.get().getAbsolutePath();
        assertEquals(ABS_PATH_TEST, absolutePath);
    }

    @Test
    public void testWorkflowEditingWithAuthorMetadataCWL() throws IOException {
        HostedApi api = new HostedApi(getWebClient(ADMIN_USERNAME));
        Workflow hostedWorkflow = api.createHostedWorkflow("awesomeTool", null, LanguageType.CWL.toString(), null, null);
        SourceFile file = new SourceFile();
        file.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/Dockstore.cwl")), StandardCharsets.UTF_8));
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        file.setAbsolutePath("/Dockstore.cwl");
        Workflow dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));
        assertTrue(!dockstoreWorkflow.getAuthor().isEmpty() && !dockstoreWorkflow.getEmail().isEmpty());
    }
    @Test
    public void testWorkflowEditingWithAuthorMetadataWDL() throws IOException {
        HostedApi api = new HostedApi(getWebClient(ADMIN_USERNAME));
        Workflow hostedWorkflow = api.createHostedWorkflow("awesomeTool", null, LanguageType.WDL.toString(), null, null);
        SourceFile file = new SourceFile();
        file.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("metadata_example2.wdl")), StandardCharsets.UTF_8));
        file.setType(SourceFile.TypeEnum.DOCKSTORE_WDL);
        file.setPath("/Dockstore.wdl");
        file.setAbsolutePath("/Dockstore.wdl");
        Workflow dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));
        assertTrue(!dockstoreWorkflow.getAuthor().isEmpty() && !dockstoreWorkflow.getEmail().isEmpty());
    }

    /**
     * Ensures that only valid descriptor types can be used to create a hosted tool
     */
    @Test
    public void testToolCreationInvalidDescriptorType(){
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        HostedApi api = new HostedApi(webClient);
        api.createHostedTool("awesomeToolCwl", Registry.QUAY_IO.toString().toLowerCase(), DescriptorLanguage.CWL_STRING, "coolNamespace", null);
        api.createHostedTool("awesomeToolCwl", Registry.QUAY_IO.toString().toLowerCase(), DescriptorLanguage.CWL_STRING, "coolNamespace", "anotherName");
        api.createHostedTool("awesomeToolWdl", Registry.QUAY_IO.toString().toLowerCase(), DescriptorLanguage.WDL_STRING, "coolNamespace", null);
        api.createHostedTool("awesomeToolWdl", Registry.QUAY_IO.toString().toLowerCase(), DescriptorLanguage.WDL_STRING, "coolNamespace", "anotherName");

        // Invalid descriptor type does not matter for tools
        api.createHostedTool("awesomeToolCwll", Registry.QUAY_IO.toString().toLowerCase(), "cwll", "coolNamespace", null);
    }

    /**
     * Ensures that hosted tools cannot be refreshed (this tests individual refresh)
     */
    @Test
    public void testRefreshingHostedTool() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool hostedTool = hostedApi.createHostedTool("awesomeTool", Registry.QUAY_IO.toString().toLowerCase(), DescriptorLanguage.CWL_STRING, "coolNamespace", null);
        thrown.expect(ApiException.class);
        DockstoreTool refreshedTool = containersApi.refresh(hostedTool.getId());
        assertTrue("There should be at least one user of the workflow", refreshedTool.getUsers().size() > 0);
        refreshedTool.getUsers().forEach(entryUser -> assertNotEquals("refresh() endpoint should have user profiles", null, entryUser.getUserProfiles()));
    }

    /**
     * Ensures that hosted tools cannot be updated
     */
    @Test
    public void testUpdatingHostedTool() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool hostedTool = hostedApi.createHostedTool("awesomeTool", Registry.QUAY_IO.toString().toLowerCase(), DescriptorLanguage.CWL_STRING, "coolNamespace", null);
        DockstoreTool newTool = new DockstoreTool();
        thrown.expect(ApiException.class);
        containersApi.updateContainer(hostedTool.getId(), newTool);
    }

    /**
     * Ensures that hosted tools can have their default path updated
     */
    @Test
    public void testUpdatingDefaultVersionHostedTool() throws IOException {
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);

        // Add a tool with a version
        DockstoreTool hostedTool = hostedApi.createHostedTool("awesomeTool", Registry.QUAY_IO.toString().toLowerCase(), DescriptorLanguage.CWL_STRING, "coolNamespace", null);
        SourceFile descriptorFile = new SourceFile();
        descriptorFile.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("tar-param.cwl")), StandardCharsets.UTF_8));
        descriptorFile.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        descriptorFile.setPath("/Dockstore.cwl");
        descriptorFile.setAbsolutePath("/Dockstore.cwl");
        SourceFile dockerfile = new SourceFile();
        dockerfile.setContent("FROM ubuntu:latest");
        dockerfile.setType(SourceFile.TypeEnum.DOCKERFILE);
        dockerfile.setPath("/Dockerfile");
        dockerfile.setAbsolutePath("/Dockerfile");
        DockstoreTool dockstoreTool = hostedApi.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, dockerfile));
        Optional<Tag> first = dockstoreTool.getTags().stream().max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        assertTrue(first.isPresent());
        assertEquals("correct number of source files", 2, first.get().getSourceFiles().size());
        // Update the default version of the tool
        containersApi.updateToolDefaultVersion(hostedTool.getId(), first.get().getName());
    }

    /**
     * Ensures that hosted workflows can have their default path updated
     */
    @Test
    public void testUpdatingDefaultVersionHostedWorkflow() throws IOException {
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);

        // Add a workflow with a version
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", null, DescriptorLanguage.CWL_STRING, null, null);
        SourceFile file = new SourceFile();
        file.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl")), StandardCharsets.UTF_8));
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        file.setAbsolutePath("/Dockstore.cwl");
        Workflow dockstoreWorkflow = hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));
        Optional<WorkflowVersion> first = dockstoreWorkflow.getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        assertTrue(first.isPresent());
        assertEquals("correct number of source files", 1, first.get().getSourceFiles().size());
        // Update the default version of the workflow
        workflowsApi.updateWorkflowDefaultVersion(hostedWorkflow.getId(), first.get().getName());
    }

    /**
     * Ensures that hosted tools cannot have new test parameter files added
     */
    @Test
    public void testAddingTestParameterFilesHostedTool() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool hostedTool = hostedApi.createHostedTool("awesomeTool", Registry.QUAY_IO.toString().toLowerCase(), DescriptorLanguage.CWL_STRING, "coolNamespace", null);
        thrown.expect(ApiException.class);
        containersApi.addTestParameterFiles(hostedTool.getId(), new ArrayList<>(), LanguageType.CWL.toString(), "", "1");
    }

    /**
     * Ensures that hosted tools cannot have their test parameter files deleted
     */
    @Test
    public void testDeletingTestParameterFilesHostedTool() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool hostedTool = hostedApi.createHostedTool("awesomeTool", Registry.QUAY_IO.toString().toLowerCase(), DescriptorLanguage.CWL_STRING, "coolNamespace", null);
        thrown.expect(ApiException.class);
        containersApi.deleteTestParameterFiles(hostedTool.getId(), new ArrayList<>(), LanguageType.CWL.toString(), "1");
    }

    /**
     * Ensures that only valid descriptor types can be used to create a hosted workflow
     */
    @Test
    public void testWorkflowCreationInvalidDescriptorType(){
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        HostedApi api = new HostedApi(webClient);
        api.createHostedWorkflow("awesomeToolCwl", null, LanguageType.CWL.toString(), null, null);
        api.createHostedWorkflow("awesomeToolWdl", null, LanguageType.WDL.toString(), null, null);
        thrown.expect(ApiException.class);
        api.createHostedWorkflow("awesomeToolCwll", null, "cwll", null, null);
    }

    /**
     * Ensures that hosted workflows cannot be refreshed (this tests individual refresh)
     */
    @Test
    public void testRefreshingHostedWorkflow() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", null, LanguageType.CWL.toString(), null, null);
        thrown.expect(ApiException.class);
        workflowApi.refresh(hostedWorkflow.getId());
    }


    /**
     * Ensures that hosted workflows cannot be restubed
     */
    @Test
    public void testRestubHostedWorkflow() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", null, LanguageType.CWL.toString(), null, null);
        thrown.expect(ApiException.class);
        workflowApi.restub(hostedWorkflow.getId());
    }

    /**
     * Ensures that hosted workflows cannot be updated
     */
    @Test
    public void testUpdatingHostedWorkflow() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", null, LanguageType.CWL.toString(), null, null);
        Workflow newWorkflow = new Workflow();
        thrown.expect(ApiException.class);
        workflowApi.updateWorkflow(hostedWorkflow.getId(), newWorkflow);
    }

    /**
     * Ensures that hosted workflows cannot have their paths updated
     */
    @Test
    public void testUpdatingWorkflowPathHostedWorkflow() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", null, LanguageType.CWL.toString(), null, null);
        Workflow newWorkflow = new Workflow();
        thrown.expect(ApiException.class);
        workflowApi.updateWorkflowPath(hostedWorkflow.getId(), newWorkflow);
    }

    /**
     * Ensures that hosted workflows cannot have new test parameter files added
     */
    @Test
    public void testAddingTestParameterFilesHostedWorkflow() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", null, LanguageType.CWL.toString(), null, null);
        thrown.expect(ApiException.class);
        workflowApi.addTestParameterFiles(hostedWorkflow.getId(), new ArrayList<>(), "", "1");
    }

    /**
     * Ensures that hosted workflows cannot have their test parameter files deleted
     */
    @Test
    public void testDeletingTestParameterFilesHostedWorkflow() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", null, LanguageType.CWL.toString(), null, null);
        thrown.expect(ApiException.class);
        workflowApi.deleteTestParameterFiles(hostedWorkflow.getId(), new ArrayList<>(), "1");
    }
}
