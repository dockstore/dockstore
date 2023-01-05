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

import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.dockstore.common.DescriptorLanguage.WDL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Registry;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.DockstoreTool.ModeEnum;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import io.swagger.model.DescriptorType;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * Tests CRUD style operations for tools and workflows hosted directly on Dockstore
 *
 * @author dyuen, agduncan
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@org.junit.jupiter.api.Tag(ConfidentialTest.NAME)
public class CRUDClientIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    private FileDAO fileDAO;

    @BeforeEach
    public void setup() {
        CommonTestUtilities.addAdditionalToolsWithPrivate2(SUPPORT, false, testingPostgres);

        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.fileDAO = new FileDAO(sessionFactory);

        // used to allow us to use fileDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Test
    void testToolCreation() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        HostedApi api = new HostedApi(webClient);
        DockstoreTool hostedTool = api
            .createHostedTool("awesomeTool", Registry.QUAY_IO.getDockerPath().toLowerCase(), CWL.getShortName(), "coolNamespace", null);
        assertNotNull(hostedTool, "tool was not created properly");
        assertEquals("git@dockstore.org:quay.io/coolNamespace/awesomeTool.git", hostedTool.getGitUrl(), "Should have git URL set");
        // createHostedTool() endpoint is safe to have user profiles because that profile is your own
        assertEquals(1, hostedTool.getUsers().size(), "One user should belong to this tool, yourself");
        hostedTool.getUsers().forEach(user -> assertNotNull(user.getUserProfiles(), "createHostedTool() endpoint should have user profiles"));

        hostedTool.getUsers().forEach(user -> user.setUserProfiles(null));

        assertTrue(hostedTool.getId() != 0, "tool was not created with a valid id");
        // can get it back with regular api
        ContainersApi oldApi = new ContainersApi(webClient);
        DockstoreTool container = oldApi.getContainer(hostedTool.getId(), null);
        // clear lazy fields for now till merge
        hostedTool.setAliases(null);
        container.setAliases(null);
        hostedTool.setUserIdToOrcidPutCode(null); // Setting to null to compare with the getContainer endpoint since that one doesn't return orcid put codes
        assertEquals(container, hostedTool);
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testToolEditing() throws IOException {
        HostedApi api = new HostedApi(getWebClient(ADMIN_USERNAME, testingPostgres));
        DockstoreTool hostedTool = api
            .createHostedTool("awesomeTool", Registry.QUAY_IO.getDockerPath().toLowerCase(), CWL.getShortName(), "coolNamespace", null);
        SourceFile descriptorFile = new SourceFile();
        descriptorFile
            .setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("tar-param.cwl")), StandardCharsets.UTF_8));
        descriptorFile.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        descriptorFile.setPath("/Dockstore.cwl");
        descriptorFile.setAbsolutePath("/Dockstore.cwl");
        SourceFile dockerfile = new SourceFile();
        dockerfile.setContent("FROM ubuntu:latest");
        dockerfile.setType(SourceFile.TypeEnum.DOCKERFILE);
        dockerfile.setPath("/Dockerfile");
        dockerfile.setAbsolutePath("/Dockerfile");
        DockstoreTool dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, dockerfile));
        Optional<Tag> first = dockstoreTool.getWorkflowVersions().stream()
            .max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(first.get().getId());
        assertEquals(2, sourceFiles.size(), "correct number of source files");
        assertTrue(dockstoreTool.getLastModifiedDate() != null && dockstoreTool.getLastModified() != 0, "a tool lacks a date");

        SourceFile file2 = new SourceFile();
        file2.setContent("{\"message\": \"Hello world!\"}");
        file2.setType(SourceFile.TypeEnum.CWL_TEST_JSON);
        file2.setPath("/test.json");
        file2.setAbsolutePath("/test.json");
        // add one file and include the old one implicitly
        dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(file2));
        first = dockstoreTool.getWorkflowVersions().stream().max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        assertEquals(3, fileDAO.findSourceFilesByVersion(first.get().getId()).size(), "correct number of source files");
        String revisionWithTestFile = first.get().getName();

        // delete a file
        file2.setContent(null);

        dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, file2, dockerfile));
        first = dockstoreTool.getWorkflowVersions().stream().max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        assertEquals(2, fileDAO.findSourceFilesByVersion(first.get().getId()).size(), "correct number of source files");

        // Default version automatically updated to the latest version (3).
        dockstoreTool = api.deleteHostedToolVersion(hostedTool.getId(), "3");
        assertEquals("2", dockstoreTool.getDefaultVersion(), "Default version should have updated to the next newest one");
        assertEquals(2, dockstoreTool.getWorkflowVersions().size(), "should only be two revisions");

        //check that all revisions have editing users
        long count = dockstoreTool.getWorkflowVersions().stream().filter(tag -> tag.getVersionEditor() != null).count();
        assertEquals(count, dockstoreTool.getWorkflowVersions().size(), "all versions do not seem to have editors");

        // ensure that we cannot retrieve files until publication, important for hosted workflows which don't exist publically
        ContainersApi otherUserApi = new ContainersApi(getWebClient(USER_1_USERNAME, testingPostgres));
        boolean thrownException = false;
        try {
            otherUserApi.getTestParameterFiles(dockstoreTool.getId(), DescriptorType.CWL.toString(), revisionWithTestFile);
        } catch (ApiException e) {
            thrownException = true;
        }
        assertTrue(thrownException);

        ContainersApi ownerApi = new ContainersApi(getWebClient(ADMIN_USERNAME, testingPostgres));
        assertNotNull(ownerApi.getTestParameterFiles(dockstoreTool.getId(), DescriptorType.CWL.toString(), revisionWithTestFile), "The owner can still get their own entry");

        // Publish tool
        ContainersApi containersApi = new ContainersApi(getWebClient(ADMIN_USERNAME, testingPostgres));
        PublishRequest pub = CommonTestUtilities.createPublishRequest(true);
        containersApi.publish(dockstoreTool.getId(), pub);

        // files should be visible afterwards
        List<SourceFile> files = otherUserApi
            .getTestParameterFiles(dockstoreTool.getId(), DescriptorType.CWL.toString(), revisionWithTestFile);
        assertTrue(!files.isEmpty() && !files.get(0).getContent().isEmpty());
    }

    @Test
    void testWorkflowCreation() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        HostedApi api = new HostedApi(webClient);
        Workflow hostedTool = api.createHostedWorkflow("awesomeWorkflow", null, CWL.getShortName(), null, null);
        assertNotNull(hostedTool, "workflow was not created properly");
        // createHostedWorkflow() endpoint is safe to have user profiles because that profile is your own
        assertEquals(1, hostedTool.getUsers().size());
        hostedTool.getUsers().forEach(user -> {
            assertNotNull(user.getUserProfiles(), "createHostedWorkflow() endpoint should have user profiles");
            // Setting it to null afterwards to compare with the getWorkflow endpoint since that one doesn't return user profiles
            user.setUserProfiles(null);
        });
        assertTrue(hostedTool.getId() != 0, "workflow was not created with a valid if");
        // can get it back with regular api
        WorkflowsApi oldApi = new WorkflowsApi(webClient);
        Workflow container = oldApi.getWorkflow(hostedTool.getId(), null);
        // clear lazy fields for now till merge
        hostedTool.setAliases(null);
        container.setAliases(null);
        hostedTool.setUserIdToOrcidPutCode(null); // Setting it to null to compare with the getWorkflow endpoint since that one doesn't return orcid put codes
        assertEquals(1, container.getUsers().size());
        container.getUsers().forEach(user -> assertNull(user.getUserProfiles(), "getWorkflow() endpoint should not have user profiles"));
        assertEquals(container, hostedTool);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void testWorkflowEditing() throws IOException {
        HostedApi api = new HostedApi(getWebClient(ADMIN_USERNAME, testingPostgres));
        WorkflowsApi workflowsApi = new WorkflowsApi(getWebClient(ADMIN_USERNAME, testingPostgres));
        io.dockstore.openapi.client.api.WorkflowsApi openApiWorkflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres));
        Workflow hostedWorkflow = api.createHostedWorkflow("awesomeTool", null, CWL.getShortName(), null, null);
        SourceFile file = new SourceFile();
        file.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl")), StandardCharsets.UTF_8));
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        file.setAbsolutePath("/Dockstore.cwl");
        Workflow dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));
        Optional<io.dockstore.openapi.client.model.WorkflowVersion> first = openApiWorkflowsApi.getWorkflowVersions(dockstoreWorkflow.getId()).stream()
                .max(Comparator.comparingInt((io.dockstore.openapi.client.model.WorkflowVersion t) -> Integer.parseInt(t.getName())));
        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(first.get().getId());
        assertEquals(1, sourceFiles.size(), "correct number of source files");
        assertTrue(first.get().getLastModified() != null && first.get().getLastModified() != 0, "a workflow lacks a date");

        SourceFile file2 = new SourceFile();
        file2.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("arguments.cwl")), StandardCharsets.UTF_8));
        file2.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file2.setPath("/arguments.cwl");
        file2.setAbsolutePath("/arguments.cwl");
        // add one file and include the old one implicitly
        dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file2));
        first = openApiWorkflowsApi.getWorkflowVersions(dockstoreWorkflow.getId()).stream()
            .max(Comparator.comparingInt((io.dockstore.openapi.client.model.WorkflowVersion t) -> Integer.parseInt(t.getName())));
        sourceFiles = fileDAO.findSourceFilesByVersion(first.get().getId());
        assertEquals(2, sourceFiles.size(), "correct number of source files");

        SourceFile file3 = new SourceFile();
        file3.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("tar-param.cwl")), StandardCharsets.UTF_8));
        file3.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file3.setPath("/tar-param.cwl");
        file3.setAbsolutePath("/tar-param.cwl");
        // add one file and include the old one implicitly
        dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file3));
        first = openApiWorkflowsApi.getWorkflowVersions(dockstoreWorkflow.getId()).stream()
            .max(Comparator.comparingInt((io.dockstore.openapi.client.model.WorkflowVersion t) -> Integer.parseInt(t.getName())));
        sourceFiles = fileDAO.findSourceFilesByVersion(first.get().getId());
        assertEquals(3, sourceFiles.size(), "correct number of source files");
        assertEquals("3", first.get().getName(), "Name of the version that was just created should be 3");
        // Delete the workflow version and recreate it
        api.deleteHostedWorkflowVersion(hostedWorkflow.getId(), "3");
        dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file3));
        first = openApiWorkflowsApi.getWorkflowVersions(dockstoreWorkflow.getId()).stream()
            .max(Comparator.comparingInt((io.dockstore.openapi.client.model.WorkflowVersion t) -> Integer.parseInt(t.getName())));
        assertEquals("4", first.get().getName(), "Version name should've skipped 3 because it was previously deleted");

        // delete a file
        file2.setContent(null);

        dockstoreWorkflow = api.editHostedWorkflow(dockstoreWorkflow.getId(), Lists.newArrayList(file, file2));
        first = openApiWorkflowsApi.getWorkflowVersions(dockstoreWorkflow.getId()).stream()
            .max(Comparator.comparingInt((io.dockstore.openapi.client.model.WorkflowVersion t) -> Integer.parseInt(t.getName())));
        sourceFiles = fileDAO.findSourceFilesByVersion(first.get().getId());
        assertEquals(2, sourceFiles.size(), "correct number of source files");

        dockstoreWorkflow = api.deleteHostedWorkflowVersion(hostedWorkflow.getId(), "1");
        assertEquals(3, dockstoreWorkflow.getWorkflowVersions().size(), "should only be three revisions");

        //check that all revisions have editing users
        long count = dockstoreWorkflow.getWorkflowVersions().stream().filter(tag -> tag.getVersionEditor() != null).count();
        assertEquals(count, dockstoreWorkflow.getWorkflowVersions().size(), "all versions do not seem to have editors");

        // ensure that we cannot retrieve files until publication, important for hosted workflows which don't exist publically
        WorkflowsApi otherUserApi = new WorkflowsApi(getWebClient(USER_1_USERNAME, testingPostgres));
        boolean thrownException = false;
        try {
            otherUserApi.primaryDescriptor(dockstoreWorkflow.getId(), first.get().getName(), DescriptorLanguage.CWL.toString());
        } catch (ApiException e) {
            thrownException = true;
        }
        assertTrue(thrownException);

        assertNotNull(workflowsApi.primaryDescriptor(dockstoreWorkflow.getId(), first.get().getName(), CWL.toString()).getId(), "The owner can still get their own entry");

        // Publish workflow
        PublishRequest pub = CommonTestUtilities.createPublishRequest(true);
        workflowsApi.publish(dockstoreWorkflow.getId(), pub);

        // files should be visible afterwards
        file = otherUserApi.primaryDescriptor(dockstoreWorkflow.getId(), first.get().getName(), DescriptorLanguage.CWL.toString());
        assertFalse(file.getContent().isEmpty());

        // Check that absolute file gets set if not explicity set
        SourceFile file4 = new SourceFile();
        file4.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("tar-param.cwl")), StandardCharsets.UTF_8));
        file4.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        String absPathTest = "/no-absolute-path.cwl";
        file4.setPath(absPathTest);
        file4.setAbsolutePath(null); // Redundant, but clarifies intent of test
        dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file4));
        Optional<WorkflowVersion> firstVersion = dockstoreWorkflow.getWorkflowVersions().stream()
            .max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        Optional<io.dockstore.webservice.core.SourceFile> msf = fileDAO.findSourceFilesByVersion(firstVersion.get().getId()).stream().filter(sf -> absPathTest.equals(sf.getPath())).findFirst();
        String absolutePath = msf.get().getAbsolutePath();
        assertEquals(absPathTest, absolutePath);
    }

    @Test
    void testDeletingFrozenVersion() throws IOException {
        HostedApi api = new HostedApi(getWebClient(ADMIN_USERNAME, testingPostgres));
        WorkflowsApi workflowsApi = new WorkflowsApi(getWebClient(ADMIN_USERNAME, testingPostgres));
        Workflow hostedWorkflow = api.createHostedWorkflow("awesomeTool", null, CWL.getShortName(), null, null);
        SourceFile file = new SourceFile();
        file.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl")), StandardCharsets.UTF_8));
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        file.setAbsolutePath("/Dockstore.cwl");
        hostedWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));

        WorkflowVersion frozenVersion = workflowsApi.getWorkflowVersions(hostedWorkflow.getId()).get(0);
        frozenVersion.setFrozen(true);
        workflowsApi.updateWorkflowVersion(hostedWorkflow.getId(), Collections.singletonList(frozenVersion));

        try {
            api.deleteHostedWorkflowVersion(hostedWorkflow.getId(), frozenVersion.getName());
            fail("Should not be able to delete a frozen version");
        } catch (ApiException ex) {
            assertEquals("Cannot delete a snapshotted version.", ex.getMessage());
        }
    }

    @Test
    void testWorkflowEditingWithAuthorMetadataCWL() throws IOException {
        HostedApi api = new HostedApi(getWebClient(ADMIN_USERNAME, testingPostgres));
        Workflow hostedWorkflow = api
            .createHostedWorkflow("awesomeTool", null, DescriptorLanguage.CWL.toString().toLowerCase(), null, null);
        SourceFile file = new SourceFile();
        file.setContent(FileUtils
            .readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/Dockstore.cwl")), StandardCharsets.UTF_8));
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        file.setAbsolutePath("/Dockstore.cwl");
        Workflow dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));
        // Workflow only has one author (who also has an email)
        assertTrue(!dockstoreWorkflow.getAuthor().isEmpty() && !dockstoreWorkflow.getEmail().isEmpty());
    }

    @Test
    void testWorkflowEditingWithAuthorMetadataWDL() throws IOException {
        HostedApi api = new HostedApi(getWebClient(ADMIN_USERNAME, testingPostgres));
        Workflow hostedWorkflow = api
            .createHostedWorkflow("awesomeTool", null, DescriptorLanguage.WDL.toString().toLowerCase(), null, null);
        SourceFile file = new SourceFile();
        file.setContent(
            FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("metadata_example2.wdl")), StandardCharsets.UTF_8));
        file.setType(SourceFile.TypeEnum.DOCKSTORE_WDL);
        file.setPath("/Dockstore.wdl");
        file.setAbsolutePath("/Dockstore.wdl");
        Workflow dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));
        // Workflow has multiple authors, but only one author has an email. The author returned may be one of the authors without an email.
        assertFalse(dockstoreWorkflow.getAuthor().isEmpty());
    }

    @Test
    void testValidHostedFileNames() throws IOException {
        HostedApi api = new HostedApi(getWebClient(ADMIN_USERNAME, testingPostgres));
        Workflow hostedWorkflow = api
                .createHostedWorkflow("awesomeTool", null, DescriptorLanguage.WDL.toString(), null, null);
        SourceFile file = new SourceFile();
        file.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("metadata_example2.wdl")), StandardCharsets.UTF_8));
        file.setType(SourceFile.TypeEnum.DOCKSTORE_WDL);
        file.setPath("/Dockstore.wdl");
        SourceFile file2 = new SourceFile();
        file2.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("metadata_example2.wdl")), StandardCharsets.UTF_8));
        file2.setPath("/");
        List sourceFiles = new ArrayList();
        sourceFiles.add(file);
        sourceFiles.add(file2);

        String msg = "Files must have a name";
        ApiException exception = assertThrows(ApiException.class, () -> api.editHostedWorkflow(hostedWorkflow.getId(), sourceFiles));
        assertTrue(exception.getMessage().contains(msg));

        sourceFiles.remove(file2);
        file2.setPath("folder/");
        sourceFiles.add(file2);
        exception = assertThrows(ApiException.class, () -> api.editHostedWorkflow(hostedWorkflow.getId(), sourceFiles));
        assertTrue(exception.getMessage().contains(msg));

        sourceFiles.remove(file2);
        file2.setPath("/name.wdl");
        api.editHostedWorkflow(hostedWorkflow.getId(), sourceFiles);
    }

    /**
     * Ensures that only valid descriptor types can be used to create a hosted tool
     */
    @Test
    void testToolCreationInvalidDescriptorType() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        HostedApi api = new HostedApi(webClient);
        api.createHostedTool("awesomeToolCwl", Registry.QUAY_IO.getDockerPath().toLowerCase(), CWL.getShortName(), "coolNamespace", null);
        api.createHostedTool("awesomeToolCwl", Registry.QUAY_IO.getDockerPath().toLowerCase(), CWL.getShortName(), "coolNamespace",
            "anotherName");
        api.createHostedTool("awesomeToolWdl", Registry.QUAY_IO.getDockerPath().toLowerCase(), WDL.getShortName(), "coolNamespace", null);
        api.createHostedTool("awesomeToolWdl", Registry.QUAY_IO.getDockerPath().toLowerCase(), WDL.getShortName(), "coolNamespace",
            "anotherName");

        // Invalid descriptor type does not matter for tools
        try {
            api.createHostedTool("awesomeToolCwll", Registry.QUAY_IO.getDockerPath().toLowerCase(), "cwll", "coolNamespace", null);
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode());
        }
        api.createHostedTool("awesomeToolCwll", Registry.QUAY_IO.getDockerPath().toLowerCase(), null, "coolNamespace", null);
    }

    /**
     * Ensures that hosted tools cannot be refreshed (this tests individual refresh)
     */
    @Test
    void testRefreshingHostedTool() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool hostedTool = hostedApi
            .createHostedTool("awesomeTool", Registry.QUAY_IO.getDockerPath().toLowerCase(), CWL.getShortName(), "coolNamespace", null);
        ApiException apiException = assertThrows(ApiException.class, () -> {
            DockstoreTool refreshedTool = containersApi.refresh(hostedTool.getId());
            assertTrue(refreshedTool.getUsers().size() > 0, "There should be at least one user of the workflow");
            refreshedTool.getUsers()
                .forEach(entryUser -> assertNotEquals(null, entryUser.getUserProfiles(), "refresh() endpoint should have user profiles"));
        });
    }

    /**
     * Ensures that hosted tools cannot be updated
     */
    @Test
    void testUpdatingHostedTool() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool hostedTool = hostedApi
            .createHostedTool("awesomeTool", Registry.QUAY_IO.getDockerPath().toLowerCase(), CWL.getShortName(), "coolNamespace", null);
        DockstoreTool newTool = new DockstoreTool();
        // need to modify something that does not make sense now but isn't ignored
        newTool.setMode(ModeEnum.MANUAL_IMAGE_PATH);
        assertThrows(ApiException.class,  () -> containersApi.updateContainer(hostedTool.getId(), newTool));
    }

    /**
     * Ensures that hosted tools can have their default path updated,
     * that deletion of the default version tag will fail gracefully,
     * and that a hosted tool can be deleted.
     */
    @Test
    void testUpdatingDefaultVersionHostedTool() throws IOException {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        ContainersApi containersApi = new ContainersApi(webClient);
        ContainertagsApi containertagsApi = new ContainertagsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);

        // Add a tool with a version
        DockstoreTool hostedTool = hostedApi
            .createHostedTool("awesomeTool", Registry.QUAY_IO.getDockerPath().toLowerCase(), CWL.getShortName(), "coolNamespace", null);
        SourceFile descriptorFile = new SourceFile();
        descriptorFile
            .setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("tar-param.cwl")), StandardCharsets.UTF_8));
        descriptorFile.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        descriptorFile.setPath("/Dockstore.cwl");
        descriptorFile.setAbsolutePath("/Dockstore.cwl");
        SourceFile dockerfile = new SourceFile();
        dockerfile.setContent("FROM ubuntu:latest");
        dockerfile.setType(SourceFile.TypeEnum.DOCKERFILE);
        dockerfile.setPath("/Dockerfile");
        dockerfile.setAbsolutePath("/Dockerfile");
        DockstoreTool dockstoreTool = hostedApi.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, dockerfile));
        Optional<Tag> first = dockstoreTool.getWorkflowVersions().stream()
            .max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        assertTrue(first.isPresent());
        assertEquals(2, fileDAO.findSourceFilesByVersion(first.get().getId()).size(), "correct number of source files");

        // Update the default version of the tool
        Tag defaultTag = first.get();
        containersApi.updateToolDefaultVersion(hostedTool.getId(), defaultTag.getName());

        // test deletion of default version tag, should fail gracefully
        // fix for #4406 (DOCK-1880)
        try {
            containertagsApi.deleteTags(hostedTool.getId(), defaultTag.getId());
            fail("Should not be able to delete a default version tag");
        } catch (ApiException ex) {
            // This is the expected behavior
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }

        // test deletion of hosted tool for #3171
        containersApi.deleteContainer(hostedTool.getId());

    }

    /**
     * Ensures that hosted workflows can have their default path updated
     */
    @Test
    void testUpdatingDefaultVersionHostedWorkflow() throws IOException {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        io.dockstore.openapi.client.api.WorkflowsApi openApiWorkflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres));
        HostedApi hostedApi = new HostedApi(webClient);

        // Add a workflow with a version
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", null, CWL.getShortName(), null, null);
        SourceFile file = new SourceFile();
        file.setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl")), StandardCharsets.UTF_8));
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        file.setAbsolutePath("/Dockstore.cwl");
        Workflow dockstoreWorkflow = hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));
        Optional<io.dockstore.openapi.client.model.WorkflowVersion> first = openApiWorkflowsApi.getWorkflowVersions(hostedWorkflow.getId()).stream()
            .max(Comparator.comparingInt((io.dockstore.openapi.client.model.WorkflowVersion t) -> Integer.parseInt(t.getName())));
        assertTrue(first.isPresent());
        long numSourcefiles = testingPostgres.runSelectStatement("SELECT COUNT(*) FROM sourcefile, workflow, workflowversion, version_sourcefile WHERE workflow.id = " + hostedWorkflow.getId() + " AND workflowversion.parentid = workflow.id AND version_sourcefile.versionid = workflowversion.id AND sourcefile.id = version_sourcefile.sourcefileid", long.class);
        assertEquals(1, numSourcefiles, "correct number of source files");
        // Update the default version of the workflow
        workflowsApi.updateWorkflowDefaultVersion(hostedWorkflow.getId(), first.get().getName());
    }

    /**
     * Ensures that hosted tools cannot have new test parameter files added
     */
    @Test
    void testAddingTestParameterFilesHostedTool() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool hostedTool = hostedApi
            .createHostedTool("awesomeTool", Registry.QUAY_IO.getDockerPath().toLowerCase(), CWL.getShortName(), "coolNamespace", null);
        assertThrows(ApiException.class,  () -> containersApi
            .addTestParameterFiles(hostedTool.getId(), new ArrayList<>(), DescriptorLanguage.CWL.toString().toLowerCase(), "", "1"));
    }

    /**
     * Ensures that hosted tools cannot have their test parameter files deleted
     */
    @Test
    void testDeletingTestParameterFilesHostedTool() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool hostedTool = hostedApi
            .createHostedTool("awesomeTool", Registry.QUAY_IO.getDockerPath().toLowerCase(), CWL.getShortName(), "coolNamespace", null);
        assertThrows(ApiException.class,  () ->  containersApi.deleteTestParameterFiles(hostedTool.getId(), new ArrayList<>(), DescriptorLanguage.CWL.toString(), "1"));
    }

    /**
     * Ensures that only valid descriptor types can be used to create a hosted workflow
     */
    @Test
    void testWorkflowCreationInvalidDescriptorType() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        HostedApi api = new HostedApi(webClient);
        api.createHostedWorkflow("awesomeToolCwl", null, DescriptorLanguage.CWL.toString().toLowerCase(), null, null);
        api.createHostedWorkflow("awesomeToolWdl", null, DescriptorLanguage.WDL.toString().toLowerCase(), null, null);
        assertThrows(ApiException.class,  () ->  api.createHostedWorkflow("awesomeToolCwll", null, "cwll", null, null));
    }

    /**
     * Ensures that hosted workflows cannot be refreshed (this tests individual refresh)
     */
    @Test
    void testRefreshingHostedWorkflow() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi
            .createHostedWorkflow("awesomeTool", null, DescriptorLanguage.CWL.toString().toLowerCase(), null, null);
        assertThrows(ApiException.class,  () -> workflowApi.refresh(hostedWorkflow.getId(), false));
    }

    /**
     * Ensures that hosted workflows cannot be restubed
     */
    @Test
    void testRestubHostedWorkflow() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi
            .createHostedWorkflow("awesomeTool", null, DescriptorLanguage.CWL.toString().toLowerCase(), null, null);
        assertThrows(ApiException.class,  () -> workflowApi.restub(hostedWorkflow.getId()));
    }

    /**
     * Ensures that hosted workflows cannot be updated
     */
    @Test
    void testUpdatingHostedWorkflow() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi
            .createHostedWorkflow("awesomeTool", null, DescriptorLanguage.CWL.toString().toLowerCase(), null, null);
        Workflow newWorkflow = new Workflow();
        assertThrows(ApiException.class,  () -> workflowApi.updateWorkflow(hostedWorkflow.getId(), newWorkflow));
    }

    /**
     * Ensures that hosted workflows cannot have their paths updated
     */
    @Test
    void testUpdatingWorkflowPathHostedWorkflow() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi
            .createHostedWorkflow("awesomeTool", null, DescriptorLanguage.CWL.toString().toLowerCase(), null, null);
        Workflow newWorkflow = new Workflow();
        assertThrows(ApiException.class,  () -> workflowApi.updateWorkflowPath(hostedWorkflow.getId(), newWorkflow));
    }

    /**
     * Ensures that hosted workflows cannot have new test parameter files added
     */
    @Test
    void testAddingTestParameterFilesHostedWorkflow() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi
            .createHostedWorkflow("awesomeTool", null, DescriptorLanguage.CWL.toString().toLowerCase(), null, null);
        assertThrows(ApiException.class,  () ->  workflowApi.addTestParameterFiles(hostedWorkflow.getId(), new ArrayList<>(), "", "1"));
    }

    /**
     * Ensures that hosted workflows cannot have their test parameter files deleted
     */
    @Test
    void testDeletingTestParameterFilesHostedWorkflow() {
        ApiClient webClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi
            .createHostedWorkflow("awesomeTool", null, DescriptorLanguage.CWL.toString().toLowerCase(), null, null);
        assertThrows(ApiException.class,  () ->   workflowApi.deleteTestParameterFiles(hostedWorkflow.getId(), new ArrayList<>(), "1"));
    }

    /**
     * Tests that the tool name is validated when registering a hosted tool.
     */
    @Test
    void testHostedToolNameValidation() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.HostedApi hostedApi = new io.dockstore.openapi.client.api.HostedApi(webClient);

        try {
            hostedApi.createHostedTool(Registry.QUAY_IO.getDockerPath().toLowerCase(), "awesomeTool", CWL.getShortName(), "coolNamespace", "<foo!>/<$bar>");
            fail("Should not be able to register a hosted tool with a tool name containing special characters that are not underscores or hyphens.");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertTrue(ex.getMessage().contains("Invalid tool name"));
        }
    }
}
