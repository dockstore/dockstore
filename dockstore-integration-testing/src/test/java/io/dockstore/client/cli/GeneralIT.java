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
import static io.dockstore.webservice.core.SourceFile.SHA_TYPE;
import static io.dockstore.webservice.core.Version.CANNOT_FREEZE_VERSIONS_WITH_NO_FILES;
import static io.dockstore.webservice.helpers.EntryVersionHelper.CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY;
import static io.dockstore.webservice.resources.DockerRepoResource.UNABLE_TO_VERIFY_THAT_YOUR_TOOL_POINTS_AT_A_VALID_SOURCE_CONTROL_REPO;
import static io.openapi.api.impl.ToolsApiServiceImpl.DESCRIPTOR_FILE_SHA256_TYPE_FOR_TRS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.common.ToolTest;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.DockstoreTool.ModeEnum;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.VersionVerifiedPlatform;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.LicenseInformation;
import io.dockstore.webservice.helpers.GitHubHelper;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.languages.WDLHandler;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.EntriesApi;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.DockstoreTool.TopicSelectionEnum;
import io.swagger.client.model.Entry;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.SourceFile.TypeEnum;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Tag.DoiStatusEnum;
import io.swagger.client.model.Workflow;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.AbuseLimitHandler;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.RateLimitHandler;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Extra confidential integration tests, don't rely on the type of repository used (Github, Dockerhub, Quay.io, Bitbucket)
 *
 * @author aduncan
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@org.junit.jupiter.api.Tag(ConfidentialTest.NAME)
@org.junit.jupiter.api.Tag(ToolTest.NAME)
class GeneralIT extends GeneralWorkflowBaseIT {
    public static final String DOCKSTORE_TOOL_IMPORTS = "dockstore-tool-imports";

    private static final String DOCKERHUB_TOOL_PATH = "registry.hub.docker.com/testPath/testUpdatePath/test5";

    private static final String QUAY_TOOL_PATH = "quay.io/dockstoretestuser2/dockstore-tool-imports/test5";

    private static final String DUMMY_DOI = "10.foo/bar";

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private FileDAO fileDAO;
    private Session session;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.fileDAO = new FileDAO(sessionFactory);
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.addAdditionalToolsWithPrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    void testGitHubLicense() throws IOException {
        String githubToken = testingPostgres
                .runSelectStatement("select content from token where username='DockstoreTestUser2' and tokensource='github.com'",
                        String.class);
        GitHub gitHub = new GitHubBuilder().withOAuthToken(githubToken).withRateLimitHandler(RateLimitHandler.FAIL).withAbuseLimitHandler(
                AbuseLimitHandler.FAIL).build();
        LicenseInformation licenseInformation = GitHubHelper.getLicenseInformation(gitHub, "dockstore-testing/md5sum-checker");
        assertEquals("Apache License 2.0", licenseInformation.getLicenseName());

        licenseInformation = GitHubHelper.getLicenseInformation(gitHub, "dockstore-testing/galaxy-workflows");
        assertEquals("MIT License", licenseInformation.getLicenseName());

        licenseInformation = GitHubHelper.getLicenseInformation(gitHub, "dockstoretestuser2/cwl-gene-prioritization");
        assertEquals("Other", licenseInformation.getLicenseName());

        licenseInformation = GitHubHelper.getLicenseInformation(gitHub, "dockstore-testing/silly-example");
        assertNull(licenseInformation.getLicenseName());
    }

    /**
     * this method will set up the webservice and return the container api
     *
     * @return ContainersApi
     * @throws ApiException
     */
    private ContainersApi setupWebService() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        return new ContainersApi(client);
    }

    /**
     * this method will set up the database and select data needed
     *
     * @return cwl/wdl/dockerfile path of the tool's tag in the database
     * @throws ApiException
     */
    private String getPathfromDB(String type) {
        // Set up DB

        // Select data from DB
        final Long toolID = testingPostgres.runSelectStatement("select id from tool where name = 'testUpdatePath'", long.class);
        final Long tagID = testingPostgres.runSelectStatement("select id from tag where parentid = " + toolID, long.class);

        return testingPostgres.runSelectStatement("select " + type + " from tag where id = " + tagID, String.class);
    }

    /**
     * Checks that all automatic containers have been found by dockstore and are not registered/published
     */
    @Test
    void testListAvailableContainers() {

        final long count = testingPostgres.runSelectStatement("select count(*) from tool where ispublished='f'", long.class);
        assertEquals(6, count, "unpublished entries should match");
    }

    /**
     * Checks that you can't add/remove labels unless they all are of proper format
     */
    @Test
    void testLabelIncorrectInput() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        try {
            toolApi.updateLabels(tool.getId(), "docker-hub,quay.io", "");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Invalid label format"));
        }
    }

    /**
     * Tests adding/editing/deleting container related labels (for search)
     */
    @Test
    void testAddEditRemoveLabel() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);

        // Test adding/removing labels for different containers
        toolApi.updateLabels(tool.getId(), "quay,github", "");
        toolApi.updateLabels(tool.getId(), "github,dockerhub", "");
        toolApi.updateLabels(tool.getId(), "alternate,github,dockerhub", "");
        toolApi.updateLabels(tool.getId(), "alternate,dockerhub", "");

        final long count = testingPostgres.runSelectStatement("select count(*) from entry_label where entryid = '2'", long.class);
        assertEquals(2, count, "there should be 2 labels for the given container, there are " + count);

        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from label where value = 'quay' or value = 'github' or value = 'dockerhub' or value = 'alternate'",
            long.class);
        assertEquals(4, count2, "there should be 4 labels in the database (No Duplicates), there are " + count2);

    }

    /**
     * Tests altering the cwl and dockerfile paths to invalid locations (quick registered)
     */
    @Test
    void testVersionTagWDLCWLAndDockerfilePathsAlteration() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        Optional<Tag> tag = tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master"))
            .findFirst();
        if (tag.isEmpty()) {
            fail("Tag master should exist");
        }
        List<Tag> tags = new ArrayList<>();
        Tag updatedTag = tag.get();
        updatedTag.setCwlPath("/testDir/Dockstore.cwl");
        updatedTag.setWdlPath("/testDir/Dockstore.wdl");
        updatedTag.setDockerfilePath("/testDir/Dockerfile");
        tags.add(updatedTag);
        toolTagsApi.updateTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tag, tool where tool.registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithub' and tool.toolname IS NULL and tool.id=tag.parentid and valid = 'f'",
            long.class);
        assertEquals(1, count, "there should now be an invalid tag, found " + count);

        tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master")).findFirst();
        tags = new ArrayList<>();
        updatedTag = tag.get();
        updatedTag.setCwlPath("/Dockstore.cwl");
        updatedTag.setWdlPath("/Dockstore.wdl");
        updatedTag.setDockerfilePath("/Dockerfile");
        tags.add(updatedTag);
        toolTagsApi.updateTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());

        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tag, tool where tool.registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithub' and tool.toolname IS NULL and tool.id=tag.parentid and valid = 'f'",
            long.class);
        assertEquals(0, count2, "the invalid tag should now be valid, found " + count2);
    }

    /**
     * Test trying to remove a tag for auto build
     */
    @Test
    void testVersionTagRemoveAutoContainer() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        Optional<Tag> tag = tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master"))
            .findFirst();
        if (tag.isEmpty()) {
            fail("Tag master should exist");
        }
        try {
            toolTagsApi.deleteTags(tool.getId(), tag.get().getId());
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Only manually added images can delete version tags"));
        }
    }

    /**
     * Test trying to add a tag for auto build
     */
    @Test
    void testVersionTagAddAutoContainer() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);

        try {
            // Add tag
            tool = addTag(tool, toolTagsApi, toolApi);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Only manually added images can add version tags"));
        }
    }

    private DockstoreTool createManualTool() {
        DockstoreTool tool = new DockstoreTool();
        tool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        tool.setName("quayandgithub");
        tool.setNamespace("dockstoretestuser2");
        tool.setRegistryString(Registry.QUAY_IO.getDockerPath());
        tool.setDefaultDockerfilePath("/Dockerfile");
        tool.setDefaultCwlPath("/Dockstore.cwl");
        tool.setDefaultWdlPath("/Dockstore.wdl");
        tool.setDefaultCWLTestParameterFile("/test.cwl.json");
        tool.setDefaultWDLTestParameterFile("/test.wdl.json");
        tool.setIsPublished(false);
        tool.setGitUrl("git@github.com:dockstoretestuser2/quayandgithubalternate.git");
        tool.setToolname("alternate");
        tool.setPrivateAccess(false);
        return tool;
    }


    DockstoreTool createManualGitLabTool() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = new DockstoreTool();
        tool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        tool.setName("dockstore-tool-bamstats");
        tool.setNamespace("NatalieEO");
        tool.setRegistryString(Registry.GITLAB.getDockerPath());
        tool.setGitUrl("git@gitlab.com:NatalieEO/dockstore-tool-bamstats.git");
        tool.setPrivateAccess(false);
        return tool;
    }


    /**
     * Tests adding tags to a manually registered container
     */
    @Test
    void testAddVersionTagManualContainer() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = createManualTool();
        tool.setDefaultDockerfilePath("/testDir/Dockerfile");
        tool.setDefaultCwlPath("/testDir/Dockstore.cwl");
        tool = toolApi.registerManual(tool);
        toolApi.refresh(tool.getId());

        // Add tag
        addTag(tool, toolTagsApi, toolApi);

        final long count = testingPostgres.runSelectStatement(
            " select count(*) from  tag, tool where tag.parentid = tool.id and giturl ='git@github.com:dockstoretestuser2/quayandgithubalternate.git' and toolname = 'alternate'",
            long.class);
        assertEquals(3, count, "there should be 3 tags, 2  that are autogenerated (master and latest) and the newly added masterTest tag, found " + count);
    }

    @Test
    void testSourceFileChecksums() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);

        DockstoreTool tool = createManualTool();
        tool.setDefaultDockerfilePath("/testDir/Dockerfile");
        tool.setDefaultCwlPath("/testDir/Dockstore.cwl");
        tool = toolApi.registerManual(tool);
        tool = toolApi.refresh(tool.getId());

        List<Tag> tags = tool.getWorkflowVersions();
        verifySourcefileChecksums(tags);

        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        toolApi.publish(tool.getId(), publishRequest);
        // Dockerfile
        List<FileWrapper> fileWrappers = ga4Ghv20Api.toolsIdVersionsVersionIdContainerfileGet("quay.io/dockstoretestuser2/quayandgithub/alternate", "master");
        verifyTRSSourceFileConversion(fileWrappers);

        FileWrapper fileWrapper = ga4Ghv20Api.toolsIdVersionsVersionIdTypeDescriptorGet("quay.io/dockstoretestuser2/quayandgithub/alternate", "CWL", "master");
        fileWrappers.clear();
        fileWrappers.add(fileWrapper);
        verifyTRSSourceFileConversion(fileWrappers);
    }

    private void verifySourcefileChecksums(final List<Tag> tags) {
        assertTrue(tags.size() > 0);
        tags.stream().forEach(tag -> {
            List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(tag.getId());
            assertTrue(sourceFiles.size() > 0);
            sourceFiles.stream().forEach(sourceFile -> {
                assertTrue(sourceFile.getChecksums().size() > 0);
                sourceFile.getChecksums().stream().forEach(checksum -> {
                    assertFalse(checksum.getChecksum().isEmpty());
                    assertEquals(SHA_TYPE, checksum.getType());
                });
            });
        });
    }

    /**
     * Tests that the language version in WDL tools are set correctly.
     */
    @Test
    void testWDLToolLanguageVersion() {
        io.dockstore.openapi.client.ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.ContainersApi containersApi = new io.dockstore.openapi.client.api.ContainersApi(client);

        io.dockstore.openapi.client.model.DockstoreTool tool = containersApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithubwdl", null);
        tool = containersApi.refresh(tool.getId());
        assertEquals(1, tool.getDescriptorType().size());
        assertEquals("WDL", tool.getDescriptorType().get(0));

        // All the versions in this tool don't specify the WDL version using the 'version' field
        tool.getWorkflowVersions().forEach(tag -> {
            List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(tag.getId());
            assertEquals(2, sourceFiles.size());

            sourceFiles.forEach(sourceFile -> {
                if ("/Dockstore.wdl".equals(sourceFile.getAbsolutePath())) {
                    assertEquals(FileType.DOCKSTORE_WDL, sourceFile.getType());
                    assertEquals(WDLHandler.DEFAULT_WDL_VERSION, sourceFile.getMetadata().getTypeVersion(), "Language version of WDL descriptor with no 'version' field should be default version");
                } else {
                    assertEquals(FileType.DOCKERFILE, sourceFile.getType());
                    assertNull(sourceFile.getMetadata().getTypeVersion(), "Docker files should not have a version");
                }
            });
            assertEquals(1, tag.getVersionMetadata().getDescriptorTypeVersions().size(), "Should only have one language version");
            assertTrue(tag.getVersionMetadata().getDescriptorTypeVersions().contains(WDLHandler.DEFAULT_WDL_VERSION));
        });
    }

    @Test
    void testGettingVerifiedVersions() {
        io.dockstore.openapi.client.ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsOpenApi = new io.dockstore.openapi.client.api.WorkflowsApi(client);
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        io.dockstore.openapi.client.api.EntriesApi entriesApi = new io.dockstore.openapi.client.api.EntriesApi(client);

        Workflow workflow = workflowApi
                .manualRegister("github", "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.wdl", "altname", DescriptorLanguage.WDL.getShortName(), "/test.json");

        workflow = workflowApi.refresh(workflow.getId(), false);
        long workflowVersionId = workflow.getWorkflowVersions().stream().filter(w -> w.getReference().equals("testBoth")).findFirst().get().getId();
        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(workflowVersionId);
        List<VersionVerifiedPlatform> versionsVerified = entriesApi.getVerifiedPlatforms(workflow.getId());
        assertEquals(0, versionsVerified.size());

        testingPostgres.runUpdateStatement("INSERT INTO sourcefile_verified(id, verified, source, metadata, platformversion) VALUES (" + sourceFiles.get(0).getId() + ", true, 'Potato CLI', 'Idaho', '1.0')");
        versionsVerified = entriesApi.getVerifiedPlatforms(workflow.getId());
        assertEquals(1, versionsVerified.size());

        ContainersApi toolApi = new ContainersApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        sourceFiles = fileDAO.findSourceFilesByVersion(tool.getWorkflowVersions().get(0).getId());
        versionsVerified = entriesApi.getVerifiedPlatforms(tool.getId());
        assertEquals(0, versionsVerified.size());

        testingPostgres.runUpdateStatement("INSERT INTO sourcefile_verified(id, verified, source, metadata) VALUES (" + sourceFiles.get(0).getId() + ", true, 'Potato CLI', 'Idaho')");
        versionsVerified = entriesApi.getVerifiedPlatforms(tool.getId());
        assertEquals(1, versionsVerified.size());

        // check that verified platforms can't be viewed by another user if entry isn't published
        io.dockstore.openapi.client.ApiClient user1Client = getOpenAPIWebClient(USER_1_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.EntriesApi user1EntriesApi = new io.dockstore.openapi.client.api.EntriesApi(user1Client);
        try {
            versionsVerified = user1EntriesApi.getVerifiedPlatforms(workflow.getId());
            fail("Should not be able to verified platforms if not published and doesn't belong to user.");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode());
            assertEquals("Forbidden: you do not have the credentials required to access this entry.", ex.getMessage());
        }

        // verified platforms can be viewed by others once published
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        workflowApi.publish(workflow.getId(), publishRequest);
        versionsVerified = user1EntriesApi.getVerifiedPlatforms(workflow.getId());
        assertEquals(1, versionsVerified.size());
    }

    @Test
    void testEditingHostedWorkflowTopics() {
        final io.dockstore.openapi.client.ApiClient openApiWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.HostedApi hostedApi = new io.dockstore.openapi.client.api.HostedApi(openApiWebClient);
        final io.dockstore.openapi.client.model.Workflow hostedWorkflow = hostedApi.createHostedWorkflow(null, "foo", DescriptorLanguage.WDL.toString(), null, null);
        hostedWorkflow.setTopicManual("new foo");
        assertSame(io.dockstore.openapi.client.model.Workflow.TopicSelectionEnum.MANUAL, hostedWorkflow.getTopicSelection());
        hostedWorkflow.setTopicSelection(io.dockstore.openapi.client.model.Workflow.TopicSelectionEnum.AUTOMATIC);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(openApiWebClient);
        final io.dockstore.openapi.client.model.Workflow workflow = workflowsApi.updateWorkflow(hostedWorkflow.getId(), hostedWorkflow);
        // topic should change
        assertEquals("new foo", workflow.getTopic());
        // but should ignore selection change
        assertEquals(io.dockstore.openapi.client.model.Workflow.TopicSelectionEnum.MANUAL, workflow.getTopicSelection());
    }

    @Test
    void testEditingHostedToolTopics() {
        final io.dockstore.openapi.client.ApiClient openApiWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.HostedApi hostedApi = new io.dockstore.openapi.client.api.HostedApi(openApiWebClient);
        final io.dockstore.openapi.client.model.DockstoreTool hostedTool = hostedApi.createHostedTool(Registry.QUAY_IO.getDockerPath().toLowerCase(), "foo", DescriptorLanguage.WDL.toString(), null, null);
        hostedTool.setTopicManual("new foo");
        assertSame(io.dockstore.openapi.client.model.DockstoreTool.TopicSelectionEnum.MANUAL, hostedTool.getTopicSelection());
        hostedTool.setTopicSelection(io.dockstore.openapi.client.model.DockstoreTool.TopicSelectionEnum.AUTOMATIC);
        io.dockstore.openapi.client.api.ContainersApi containersApi = new io.dockstore.openapi.client.api.ContainersApi(openApiWebClient);
        final io.dockstore.openapi.client.model.DockstoreTool dockstoreTool = containersApi.updateContainer(hostedTool.getId(), hostedTool);
        // topic should change
        assertEquals("new foo", dockstoreTool.getTopic());
        // but should ignore selection change
        assertEquals(io.dockstore.openapi.client.model.DockstoreTool.TopicSelectionEnum.MANUAL, dockstoreTool.getTopicSelection());
    }

    @Test
    void testGettingVersionsFileTypes() {
        io.dockstore.openapi.client.ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient openApiWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final HostedApi hostedApi = new HostedApi(webClient);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        io.dockstore.openapi.client.api.WorkflowsApi openApiWorkflowApi = new io.dockstore.openapi.client.api.WorkflowsApi(openApiWebClient);
        io.dockstore.openapi.client.api.EntriesApi entriesApi = new io.dockstore.openapi.client.api.EntriesApi(client);

        Workflow workflow = hostedApi.createHostedWorkflow("wdlHosted", null, DescriptorLanguage.WDL.toString(), null, null);
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(SourceFile.TypeEnum.DOCKSTORE_WDL);
        sourceFile.setContent("workflow potato {\n}");
        sourceFile.setPath("/Dockstore.wdl");
        sourceFile.setAbsolutePath("/Dockstore.wdl");

        workflow = hostedApi.editHostedWorkflow(workflow.getId(), Lists.newArrayList(sourceFile));
        io.dockstore.openapi.client.model.WorkflowVersion workflowVersion = openApiWorkflowApi.getWorkflowVersions(workflow.getId()).stream().filter(wv -> wv.getName().equals("1")).findFirst().get();
        List<String> fileTypes = entriesApi.getVersionsFileTypes(workflow.getId(), workflowVersion.getId());
        assertEquals(1, fileTypes.size());
        assertEquals(TypeEnum.DOCKSTORE_WDL.toString(), fileTypes.get(0));

        SourceFile testFile = new SourceFile();
        testFile.setType(SourceFile.TypeEnum.WDL_TEST_JSON);
        testFile.setContent("{}");
        testFile.setPath("/test.wdl.json");
        testFile.setAbsolutePath("/test.wdl.json");

        workflow = hostedApi.editHostedWorkflow(workflow.getId(), Lists.newArrayList(sourceFile, testFile));
        workflowVersion = openApiWorkflowApi.getWorkflowVersions(workflow.getId()).stream().filter(wv -> wv.getName().equals("2")).findFirst().get();
        fileTypes = entriesApi.getVersionsFileTypes(workflow.getId(), workflowVersion.getId());
        assertEquals(2, fileTypes.size());
        assertNotSame(fileTypes.get(0), fileTypes.get(1));

        DockstoreTool tool = hostedApi.createHostedTool("hostedTool", Registry.QUAY_IO.getDockerPath().toLowerCase(), DescriptorLanguage.CWL.toString(), "namespace", null);
        SourceFile dockerfile = new SourceFile();
        dockerfile.setContent("FROM ubuntu:latest");
        dockerfile.setPath("/Dockerfile");
        dockerfile.setAbsolutePath("/Dockerfile");
        dockerfile.setType(SourceFile.TypeEnum.DOCKERFILE);
        SourceFile cwl = new SourceFile();
        cwl.setContent("class: CommandLineTool\ncwlVersion: v1.0");
        cwl.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        cwl.setPath("/Dockstore.cwl");
        cwl.setAbsolutePath("/Dockstore.cwl");
        SourceFile testcwl = new SourceFile();
        testcwl.setType(SourceFile.TypeEnum.CWL_TEST_JSON);
        testcwl.setContent("{}");
        testcwl.setPath("/test.cwl.json");
        testcwl.setAbsolutePath("/test.cwl.json");
        tool = hostedApi.editHostedTool(tool.getId(), Lists.newArrayList(sourceFile, testFile, cwl, testcwl, dockerfile));

        fileTypes = entriesApi.getVersionsFileTypes(tool.getId(), tool.getWorkflowVersions().get(0).getId());
        assertEquals(5, fileTypes.size());
        // ensure no duplicates
        SortedSet<String> set = new TreeSet<>(fileTypes);
        assertEquals(set.size(), fileTypes.size());

        // check that file types can't be viewed by another user if entry isn't published
        io.dockstore.openapi.client.ApiClient user1Client = getOpenAPIWebClient(USER_1_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.EntriesApi user1entriesApi = new io.dockstore.openapi.client.api.EntriesApi(user1Client);
        try {
            fileTypes = user1entriesApi.getVersionsFileTypes(workflow.getId(), workflowVersion.getId());
            fail("Should not be able to grab a versions file types if not published and doesn't belong to user.");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode());
            assertEquals("Forbidden: you do not have the credentials required to access this entry.", ex.getMessage());
        }

        // file types can be viewed by others once published
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        workflowApi.publish(workflow.getId(), publishRequest);
        fileTypes = user1entriesApi.getVersionsFileTypes(workflow.getId(), workflowVersion.getId());
        assertEquals(2, fileTypes.size());
        assertNotSame(fileTypes.get(0), fileTypes.get(1));
    }

    // Tests 1.10.0 migration where id=adddescriptortypecolumn
    @Test
    void testMigrationForDescriptorType() {
        io.dockstore.openapi.client.ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);

        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        assertEquals(1, tool.getDescriptorType().size());
        assertEquals("CWL", tool.getDescriptorType().get(0));

        tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithubwdl", null);
        assertEquals(1, tool.getDescriptorType().size());
        assertEquals("WDL", tool.getDescriptorType().get(0));

        tool = toolApi.getContainerByToolPath("quay.io/dockstore2/testrepo2", null);
        assertEquals(2, tool.getDescriptorType().size());
        assertNotSame(tool.getDescriptorType().get(0), tool.getDescriptorType().get(1));
    }

    @Test
    void testRefreshingGetsDescriptorType() {
        io.dockstore.openapi.client.ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.ContainersApi openToolApi = new io.dockstore.openapi.client.api.ContainersApi(client);
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);

        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        tool = toolApi.refresh(tool.getId());
        assertEquals(1, tool.getDescriptorType().size());
        assertEquals("CWL", tool.getDescriptorType().get(0));

        tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithubwdl", null);
        tool = toolApi.refresh(tool.getId());
        assertEquals(1, tool.getDescriptorType().size());
        assertEquals("WDL", tool.getDescriptorType().get(0));

        tool = toolApi.getContainerByToolPath("quay.io/dockstore2/testrepo2", null);
        tool = toolApi.refresh(tool.getId());
        assertEquals(2, tool.getDescriptorType().size());
        assertNotSame(tool.getDescriptorType().get(0), tool.getDescriptorType().get(1));
    }

    private void verifyTRSSourceFileConversion(final List<FileWrapper> fileWrappers) {
        assertTrue(fileWrappers.size() > 0);
        fileWrappers.stream().forEach(fileWrapper -> {
            assertTrue(fileWrapper.getChecksum().size() > 0);
            fileWrapper.getChecksum().stream().forEach(checksum -> {
                assertFalse(checksum.getChecksum().isEmpty());
                assertEquals(DESCRIPTOR_FILE_SHA256_TYPE_FOR_TRS, checksum.getType());
            });
        });
    }

    @Test
    void testHiddenAndDefaultTags() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);

        List<Tag> tags = tool.getWorkflowVersions();
        Tag tag = tags.get(0);
        tag.setHidden(true);
        toolTagsApi.updateTags(tool.getId(), Collections.singletonList(tag));

        try {
            tool = toolApi.updateToolDefaultVersion(tool.getId(), tag.getName());
            fail("Shouldn't be able to set the default version to one that is hidden.");
        } catch (ApiException ex) {
            assertEquals("You can not set the default version to a hidden version.", ex.getMessage());
        }

        // Set the default version to a non-hidden version
        tag.setHidden(false);
        toolTagsApi.updateTags(tool.getId(), Collections.singletonList(tag));
        tool = toolApi.updateToolDefaultVersion(tool.getId(), tag.getName());

        // Should not be able to hide a default version
        tag.setHidden(true);
        try {
            toolTagsApi.updateTags(tool.getId(), Collections.singletonList(tag));
            fail("Should not be able to hide a default version");
        } catch (ApiException ex) {
            assertEquals("You cannot hide the default version.", ex.getMessage());
        }

        // Test the same for hosted tools
        DockstoreTool hostedTool = hostedApi.createHostedTool("hostedTool", Registry.QUAY_IO.getDockerPath().toLowerCase(), DescriptorLanguage.CWL.toString(), "namespace", null);
        SourceFile dockerfile = new SourceFile();
        dockerfile.setContent("FROM ubuntu:latest");
        dockerfile.setPath("/Dockerfile");
        dockerfile.setAbsolutePath("/Dockerfile");
        dockerfile.setType(SourceFile.TypeEnum.DOCKERFILE);
        SourceFile cwl = new SourceFile();
        cwl.setContent("class: CommandLineTool\ncwlVersion: v1.0");
        cwl.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        cwl.setPath("/Dockstore.cwl");
        cwl.setAbsolutePath("/Dockstore.cwl");
        hostedTool = hostedApi.editHostedTool(hostedTool.getId(), Lists.newArrayList(cwl, dockerfile));

        Tag hostedTag = hostedTool.getWorkflowVersions().get(0);
        hostedTag.setHidden(true);
        try {
            toolTagsApi.updateTags(hostedTool.getId(), Collections.singletonList(hostedTag));
            fail("Shouldn't be able to hide the default version.");
        } catch (ApiException ex) {
            assertEquals("You cannot hide the default version.", ex.getMessage());
        }

        cwl.setContent("class: CommandLineTool\n\ncwlVersion: v1.0");
        hostedTool = hostedApi.editHostedTool(hostedTool.getId(), Lists.newArrayList(cwl, dockerfile));
        hostedTag = hostedTool.getWorkflowVersions().stream().filter(v -> v.getName().equals("1")).findFirst().get();
        hostedTag.setHidden(true);
        toolTagsApi.updateTags(hostedTool.getId(), Collections.singletonList(hostedTag));

        try {
            toolApi.updateToolDefaultVersion(hostedTool.getId(), hostedTag.getName());
            fail("Shouldn't be able to set the default version to one that is hidden.");
        } catch (ApiException ex) {
            assertEquals("You can not set the default version to a hidden version.", ex.getMessage());
        }
    }


    /**
     * Tests hiding and unhiding different versions of a container (quick registered)
     */
    @Test
    void testVersionTagHide() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        Optional<Tag> tag = tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master"))
            .findFirst();
        if (tag.isEmpty()) {
            fail("Tag master should exist");
        }
        List<Tag> tags = new ArrayList<>();
        Tag updatedTag = tag.get();
        updatedTag.setHidden(true);
        tags.add(updatedTag);
        toolTagsApi.updateTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tag t, version_metadata vm where t.id = " + updatedTag.getId() + " and vm.hidden = 't' and t.id = vm.id", long.class);
        assertEquals(1, count, "there should be 1 hidden tag");

        tag = tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master")).findFirst();
        if (tag.isEmpty()) {
            fail("Tag master should exist");
        }
        tags = new ArrayList<>();
        updatedTag = tag.get();
        updatedTag.setHidden(false);
        tags.add(updatedTag);
        toolTagsApi.updateTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tag t, version_metadata vm where t.id = " + updatedTag.getId() + " and vm.hidden = 't' and t.id = vm.id", long.class);
        assertEquals(0, count2, "there should be 0 hidden tag");
    }

    /**
     * Test update tag with only WDL to invalid then valid
     */
    @Test
    void testVersionTagWDL() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithubwdl", null);
        Optional<Tag> tag = tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master"))
            .findFirst();
        if (tag.isEmpty()) {
            fail("Tag master should exist");
        }
        List<Tag> tags = new ArrayList<>();
        Tag updatedTag = tag.get();
        updatedTag.setWdlPath("/randomDir/Dockstore.wdl");
        tags.add(updatedTag);
        toolTagsApi.updateTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());

        // should now be invalid

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tag, tool where tool.registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithubwdl' and tool.toolname IS NULL and tool.id=tag.parentid and valid = 'f'",
            long.class);

        assertEquals(1, count, "there should now be 1 invalid tag, found " + count);
        tag = tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master")).findFirst();
        if (tag.isEmpty()) {
            fail("Tag master should exist");
        }
        tags = new ArrayList<>();
        updatedTag = tag.get();
        updatedTag.setWdlPath("/Dockstore.wdl");
        tags.add(updatedTag);
        toolTagsApi.updateTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());

        // should now be valid
        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tag, tool where tool.registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithubwdl' and tool.toolname IS NULL and tool.id=tag.parentid and valid = 'f'",
            long.class);
        assertEquals(0, count2, "the tag should now be valid");

    }

    private DockstoreTool addTag(DockstoreTool tool, ContainertagsApi toolTagsApi, ContainersApi toolApi) {
        List<Tag> tags = new ArrayList<>();
        Tag tag = new Tag();
        tag.setName("masterTest");
        tag.setImageId("4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8");
        tag.setReference("master");
        tags.add(tag);
        toolTagsApi.addTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());
        return tool;
    }

    private DockstoreTool addGitLabTag(DockstoreTool tool, ContainertagsApi toolTagsApi, ContainersApi toolApi) {
        List<Tag> tags = new ArrayList<>();
        Tag tag = new Tag();
        tag.setName("latest");
        tag.setReference("master");
        tags.add(tag);
        toolTagsApi.addTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());
        return tool;
    }

    @Test
    void testToolDelete() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);

        DockstoreTool tool = createManualTool();
        tool.setDefaultDockerfilePath("/testDir/Dockerfile");
        tool.setDefaultCwlPath("/testDir/Dockstore.cwl");
        tool.setDefaultWdlPath("/testDir/Dockstore.wdl");
        tool.setGitUrl("git@github.com:dockstoretestuser2/quayandgithubalternate.git");
        tool = toolApi.registerManual(tool);
        tool = toolApi.refresh(tool.getId());

        toolApi.deleteContainer(tool.getId());
    }

    /**
     * Will test deleting a tag from a manually registered container
     */
    @Test
    void testVersionTagDelete() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        // Register tool
        DockstoreTool tool = createManualTool();
        tool.setDefaultDockerfilePath("/testDir/Dockerfile");
        tool.setDefaultCwlPath("/testDir/Dockstore.cwl");
        tool.setDefaultWdlPath("/testDir/Dockstore.wdl");
        tool.setGitUrl("git@github.com:dockstoretestuser2/quayandgithubalternate.git");
        tool = toolApi.registerManual(tool);
        tool = toolApi.refresh(tool.getId());

        // Add tag
        tool = addTag(tool, toolTagsApi, toolApi);

        // Delete version
        Optional<Tag> optionalTag = tool.getWorkflowVersions().stream()
            .filter(existingTag -> Objects.equals(existingTag.getName(), "masterTest")).findFirst();
        if (optionalTag.isEmpty()) {
            fail("Tag masterTest should exist");
        }
        toolTagsApi.deleteTags(tool.getId(), optionalTag.get().getId());

        final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", long.class);
        assertEquals(0, count, "there should be no tags with the name masterTest");
    }

    /**
     * Check that cannot retrieve an incorrect individual container
     */
    @Test
    void testGetIncorrectContainer() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        try {
            DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/unknowncontainer", null);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Entry not found"));
        }
    }

    /**
     * Check that a user can't retrieve another users container
     */
    @Test
    void testGetOtherUsersContainer() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        try {
            DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/test_org/test1", null);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Entry not found"));
        }
    }

    /**
     * Tests that a user can only add Quay containers that they own directly or through an organization
     */
    @Test
    void testUserPrivilege() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);

        DockstoreTool tool = createManualTool();
        tool.setToolname("testTool");
        tool.setDefaultDockerfilePath("/testDir/Dockerfile");
        tool.setDefaultCwlPath("/testDir/Dockstore.cwl");
        tool = toolApi.registerManual(tool);
        tool = toolApi.refresh(tool.getId());

        // Repo user has access to
        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
            + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithub' and toolname = 'testTool'", long.class);
        assertEquals(1, count, "the container should exist");

        // Repo user is part of org
        DockstoreTool tool2 = createManualTool();
        tool2.setToolname("testOrg");
        tool2.setName("testrepo2");
        tool2.setNamespace("dockstore2");
        tool2.setGitUrl("git@github.com:dockstoretestuser2/quayandgithub.git");
        tool2 = toolApi.registerManual(tool2);
        tool2 = toolApi.refresh(tool2.getId());

        final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
            + "' and namespace = 'dockstore2' and name = 'testrepo2' and toolname = 'testOrg'", long.class);
        assertEquals(1, count2, "the container should exist");

        // Repo user doesn't own
        // TODO: The actual error is that the tool has no tags, not that the user does not have access
        DockstoreTool tool3 = createManualTool();
        tool3.setToolname("testTool");
        tool3.setName("testrepo");
        tool3.setNamespace("dockstoretestuser");
        tool3.setGitUrl("git@github.com:dockstoretestuser/quayandgithub.git");
        try {
            tool3 = toolApi.registerManual(tool3);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("no tags"));
        }
    }

    /**
     * Test to update the default path of CWL and it should change the tag's CWL path in the database
     *
     * @throws ApiException
     */
    @Test
    void testUpdateToolPathCWL() throws ApiException {
        //setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();

        DockstoreTool toolTest = toolsApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);
        toolsApi.refresh(toolTest.getId());

        //change the default cwl path and refresh
        toolTest.setDefaultCwlPath("/test1.cwl");
        toolsApi.updateTagContainerPath(toolTest.getId(), toolTest);
        toolsApi.refresh(toolTest.getId());

        //check if the tag's dockerfile path have the same cwl path or not in the database
        final String path = getPathfromDB("cwlpath");
        assertEquals("/test1.cwl", path, "the cwl path should be changed to /test1.cwl");
    }

    /**
     * should be able to refresh a tool where image ids are changing (constraints issue from #1405)
     *
     * @throws ApiException should not see error from the webservice
     */
    @Test
    void testImageIDUpdateDuringRefresh() throws ApiException {
        ContainersApi containersApi = setupWebService();
        DockstoreTool toolTest = containersApi.getContainerByToolPath(QUAY_TOOL_PATH, null);

        assertTrue(toolTest.getWorkflowVersions().size() >= 1, "should see one (or more) tags: " + toolTest.getWorkflowVersions().size());

        UsersApi usersApi = new UsersApi(containersApi.getApiClient());
        final Long userid = usersApi.getUser().getId();
        usersApi.refreshToolsByOrganization(userid, "dockstoretestuser2", DOCKSTORE_TOOL_IMPORTS);

        testingPostgres.runUpdateStatement("update tag set imageid = 'silly old value'");
        int size = containersApi.getContainer(toolTest.getId(), null).getWorkflowVersions().size();
        long size2 = containersApi.getContainer(toolTest.getId(), null).getWorkflowVersions().stream()
            .filter(tag -> tag.getImageId().equals("silly old value")).count();
        assertTrue(size == size2 && size >= 1);
        // individual refresh should update image ids
        containersApi.refresh(toolTest.getId());
        DockstoreTool container = containersApi.getContainer(toolTest.getId(), null);
        size = container.getWorkflowVersions().size();
        size2 = container.getWorkflowVersions().stream().filter(tag -> tag.getImageId() != null && tag.getImageId().equals("silly old value")).count();
        assertTrue(size2 == 0 && size >= 1);

        // so should overall refresh
        testingPostgres.runUpdateStatement("update tag set imageid = 'silly old value'");
        usersApi.refreshToolsByOrganization(userid, "dockstoretestuser2", DOCKSTORE_TOOL_IMPORTS);
        container = containersApi.getContainer(toolTest.getId(), null);
        size = container.getWorkflowVersions().size();
        size2 = container.getWorkflowVersions().stream().filter(tag -> tag.getImageId() != null && tag.getImageId().equals("silly old value")).count();
        assertTrue(size2 == 0 && size >= 1);

        // so should organizational refresh
        testingPostgres.runUpdateStatement("update tag set imageid = 'silly old value'");
        usersApi.refreshToolsByOrganization(userid, container.getNamespace(), DOCKSTORE_TOOL_IMPORTS);
        container = containersApi.getContainer(toolTest.getId(), null);
        size = container.getWorkflowVersions().size();
        size2 = container.getWorkflowVersions().stream().filter(tag -> tag.getImageId() != null && tag.getImageId().equals("silly old value")).count();
        assertTrue(size2 == 0 && size >= 1);
    }

    /**
     * Tests that image and checksum information can be grabbed from Quay and update db correctly.
     */
    @Test
    void testGrabbingImagesFromQuay() {
        ContainersApi containersApi = setupWebService();
        DockstoreTool tool = containersApi.getContainerByToolPath(QUAY_TOOL_PATH, null);

        assertEquals(0, containersApi.getContainer(tool.getId(), null).getWorkflowVersions().get(0).getImages().size());

        UsersApi usersApi = new UsersApi(containersApi.getApiClient());
        final Long userid = usersApi.getUser().getId();
        usersApi.refreshToolsByOrganization(userid, "dockstoretestuser2", DOCKSTORE_TOOL_IMPORTS);

        // Check that the image information has been grabbed on refresh.
        List<Tag> tags = containersApi.getContainer(tool.getId(), null).getWorkflowVersions();
        for (Tag tag : tags) {
            assertNotNull(tag.getImages().get(0).getChecksums().get(0).getType());
            assertNotNull(tag.getImages().get(0).getChecksums().get(0).getChecksum());
        }

        // Check for case where user deletes tag and creates new one of same name.
        // Check that the new imageid and checksums are grabbed from Quay on refresh. Also check the old images have been deleted.
        String imageID = tags.get(0).getImages().get(0).getImageID();
        String imageID2 = tags.get(1).getImages().get(0).getImageID();

        final long count = testingPostgres.runSelectStatement("select count(*) from image", long.class);
        testingPostgres.runUpdateStatement("update image set image_id = 'dummyid'");
        assertEquals("dummyid", containersApi.getContainer(tool.getId(), null).getWorkflowVersions().get(0).getImages().get(0).getImageID());
        usersApi.refreshToolsByOrganization(userid, "dockstoretestuser2", DOCKSTORE_TOOL_IMPORTS);
        final long count2 = testingPostgres.runSelectStatement("select count(*) from image", long.class);
        assertEquals(imageID, containersApi.getContainer(tool.getId(), null).getWorkflowVersions().get(0).getImages().get(0).getImageID());
        assertEquals(imageID2, containersApi.getContainer(tool.getId(), null).getWorkflowVersions().get(1).getImages().get(0).getImageID());
        assertEquals(count, count2);
    }


    @Test
    void testAnnotatedGitHubTag() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);

        DockstoreTool tool = new DockstoreTool();
        tool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        tool.setName("simphen");
        tool.setNamespace("uwgac");
        tool.setRegistryString(Registry.DOCKER_HUB.getDockerPath());
        tool.setDefaultDockerfilePath("/Dockerfile");
        tool.setDefaultCwlPath("/tools/allele_freq.cwl");
        tool.setDefaultWdlPath("/Dockstore.wdl");
        tool.setDefaultCWLTestParameterFile("/test.cwl.json");
        tool.setDefaultWDLTestParameterFile("/test.wdl.json");
        tool.setIsPublished(false);
        // This actually exists: https://bitbucket.org/DockstoreTestUser/dockstore-whalesay-2/src/master/
        tool.setGitUrl("git@github.com:dockstore-testing/md5sum-checker.git");
        tool.setToolname("testing");
        tool.setPrivateAccess(false);

        tool = toolApi.registerManual(tool);

        List<Tag> tags = new ArrayList<>();
        Tag tag = new Tag();
        tag.setName("0.2.2");
        tag.setReference("annotated-tag");
        tags.add(tag);
        toolTagsApi.addTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());
        tag = tool.getWorkflowVersions().get(0);

        // Test that the right commit is grabbed for an annotated tag
        // https://github.com/dockstore-testing/md5sum-checker/releases/tag/annotated-tag
        // https://github.com/dockstore-testing/md5sum-checker/tree/f7927a52c0583a0bb96ec23f0509683ea7f6cd38
        assertEquals("f7927a52c0583a0bb96ec23f0509683ea7f6cd38", tag.getCommitID());
    }

    @Test
    void ga4ghImageType() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        tool = toolApi.refresh(tool.getId());
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        toolApi.publish(tool.getId(), publishRequest);
        Tool ga4ghatool = ga4Ghv20Api.toolsIdGet("quay.io/dockstoretestuser2/quayandgithub");

        final Response.ResponseBuilder responseBuilder = Response.ok(ga4ghatool);
        Response response = responseBuilder.build();
        response.getEntity();

        ObjectMapper om = new ObjectMapper();
        boolean failed = false;
        try {
            JSONObject json = new JSONObject(om.writeValueAsString(response.getEntity()));
            assertTrue(json.toString().contains("Docker"));
            assertFalse(json.toString().contains("DOCKER"));
        } catch (JsonProcessingException ex) {
            failed = true;
        }
        assertFalse(failed, "Parsing should not have failed");
    }

    @Test
    void testGrabChecksumFromGitLab() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = createManualGitLabTool();

        tool = toolApi.registerManual(tool);

        tool = addGitLabTag(tool, toolTagsApi, toolApi);
        List<Tag> tags = toolApi.getContainer(tool.getId(), null).getWorkflowVersions();
        verifyChecksumsAreSaved(tags);

        // Check for case where user deletes tag and creates new one of same name.
        // Check that the new imageid and checksums are grabbed on refresh. Also check the old images have been deleted.
        refreshAfterDeletedTag(toolApi, tool, tags);

        // mimic getting an registry being slow/now responding and verify we do not delete the image information we already have by going to an invalid url.
        testingPostgres.runUpdateStatement("update tool set name = 'thisnamedoesnotexist' where giturl = 'git@gitlab.com:NatalieEO/dockstore-tool-bamstats.git'");
        toolApi.refresh(tool.getId());
        List<Tag> updatedTags = toolApi.getContainer(tool.getId(), null).getWorkflowVersions();
        verifyChecksumsAreSaved(updatedTags);
    }


    /**
     * Tests that if a tool has a tag with mismatching tag name and tag reference, and it is set as the default tag
     * then the author metadata is properly grabbed.
     */
    @Test
    void testParseMetadataFromToolWithTagNameAndReferenceMismatch() {
        // Setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();

        // Create tool with mismatching tag name and tag reference
        DockstoreTool tool = toolsApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);
        tool.setDefaultVersion("1.0");
        DockstoreTool toolTest = toolsApi.updateContainer(tool.getId(), tool);
        toolsApi.refresh(toolTest.getId());

        DockstoreTool refreshedTool = toolsApi.getContainer(toolTest.getId(), null);
        assertNotNull(refreshedTool.getAuthor(), "Author should be set, even if tag name and tag reference are mismatched.");
    }

    /**
     * Test to update the default path of WDL and it should change the tag's WDL path in the database
     *
     * @throws ApiException
     */
    @Test
    void testUpdateToolPathWDL() throws ApiException {
        //setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();

        //register tool
        DockstoreTool toolTest = toolsApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);
        toolsApi.refresh(toolTest.getId());

        //change the default wdl path and refresh
        toolTest.setDefaultWdlPath("/test1.wdl");
        toolsApi.updateTagContainerPath(toolTest.getId(), toolTest);
        toolsApi.refresh(toolTest.getId());

        //check if the tag's wdl path have the same wdl path or not in the database
        final String path = getPathfromDB("wdlpath");
        assertEquals("/test1.wdl", path, "the cwl path should be changed to /test1.wdl");
    }

    @Test
    void testToolFreezingWithNoFiles() {
        //setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();
        ContainertagsApi tagsApi = new ContainertagsApi(toolsApi.getApiClient());

        // change default paths
        DockstoreTool c = toolsApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);
        c.setDefaultCwlPath("foo.cwl");
        c.setDefaultWdlPath("foo.wdl");
        c.setDefaultDockerfilePath("foo");
        c = toolsApi.updateContainer(c.getId(), c);
        c.getWorkflowVersions().forEach(tag -> {
            tag.setCwlPath("foo.cwl");
            tag.setWdlPath("foo.wdl");
            tag.setDockerfilePath("foo");
        });
        c = toolsApi.updateTagContainerPath(c.getId(), c);
        DockstoreTool refresh = toolsApi.refresh(c.getId());
        assertFalse(refresh.getWorkflowVersions().isEmpty());
        Tag master = refresh.getWorkflowVersions().stream().filter(t -> t.getName().equals("1.0")).findFirst().get();
        master.setFrozen(true);
        master.setImageId("awesomeid");
        try {
            tagsApi.updateTags(refresh.getId(), Lists.newArrayList(master));
        } catch (ApiException e) {
            // should exception
            assertTrue(e.getMessage().contains(CANNOT_FREEZE_VERSIONS_WITH_NO_FILES), "missing error message");
            return;
        }
        fail("should be unreachable");
    }

    @Test
    void testToolFreezing() throws ApiException {
        //setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();
        ContainertagsApi tagsApi = new ContainertagsApi(toolsApi.getApiClient());

        DockstoreTool c = toolsApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);
        DockstoreTool refresh = toolsApi.refresh(c.getId());

        assertFalse(refresh.getWorkflowVersions().isEmpty());
        Tag master = refresh.getWorkflowVersions().stream().filter(t -> t.getName().equals("1.0")).findFirst().get();
        master.setFrozen(true);
        master.setImageId("awesomeid");
        List<Tag> tags = tagsApi.updateTags(refresh.getId(), Lists.newArrayList(master));
        master = tags.stream().filter(t -> t.getName().equals("1.0")).findFirst().get();
        assertTrue(master.isFrozen() && master.getImageId().equals("awesomeid"));
        master.setImageId("weakid");
        tags = tagsApi.updateTags(refresh.getId(), Lists.newArrayList(master));
        master = tags.stream().filter(t -> t.getName().equals("1.0")).findFirst().get();
        assertTrue(master.isFrozen() && master.getImageId().equals("awesomeid"));
        master.setFrozen(false);
        tags = tagsApi.updateTags(refresh.getId(), Lists.newArrayList(master));
        master = tags.stream().filter(t -> t.getName().equals("1.0")).findFirst().get();
        assertTrue(master.isFrozen() && master.getImageId().equals("awesomeid"));

        // but should be able to change doi stuff
        master.setFrozen(true);
        master.setDoiStatus(Tag.DoiStatusEnum.REQUESTED);
        master.setDoiURL(DUMMY_DOI);
        tags = tagsApi.updateTags(refresh.getId(), Lists.newArrayList(master));
        master = tags.stream().filter(t -> t.getName().equals("1.0")).findFirst().get();
        assertEquals(DUMMY_DOI, master.getDoiURL());
        assertEquals(DoiStatusEnum.REQUESTED, master.getDoiStatus());

        // try modifying sourcefiles
        // cannot modify sourcefiles for a frozen version
        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(master.getId());
        assertFalse(sourceFiles.isEmpty());
        sourceFiles.forEach(s -> {
            assertTrue(s.isFrozen());
            testingPostgres.runUpdateStatement("update sourcefile set content = 'foo' where id = " + s.getId());
            final String content = testingPostgres
                .runSelectStatement("select content from sourcefile where id = " + s.getId(), String.class);
            assertNotEquals("foo", content);
        });

        // try deleting a row join table
        sourceFiles.forEach(s -> {
            final int affected = testingPostgres
                .runUpdateStatement("delete from version_sourcefile vs where vs.sourcefileid = " + s.getId());
            assertEquals(0, affected);
        });

        // try updating a row in the join table
        sourceFiles.forEach(s -> {
            final int affected = testingPostgres
                .runUpdateStatement("update version_sourcefile set sourcefileid=123456 where sourcefileid = " + s.getId());
            assertEquals(0, affected);
        });

        final Long versionId = master.getId();
        // try creating a row in the join table
        sourceFiles.forEach(s -> {
            try {
                testingPostgres.runUpdateStatement(
                    "insert into version_sourcefile (versionid, sourcefileid) values (" + versionId + ", " + 1234567890 + ")");
                fail("Insert should have failed to do row-level security");
            } catch (Exception ex) {
                assertTrue(ex.getMessage().contains("new row violates row-level"));
            }
        });

        // cannot add or delete test files for frozen versions
        try {
            toolsApi.deleteTestParameterFiles(refresh.getId(), Lists.newArrayList("foo"), "cwl", "1.0");
            fail("could delete test parameter file");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains(CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY));
        }
        try {
            toolsApi.addTestParameterFiles(refresh.getId(), Lists.newArrayList("foo"), "cwl", "", "1.0");
            fail("could add test parameter file");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains(CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY));
        }

    }

    /**
     * Test to update the default path of Dockerfile and it should change the tag's dockerfile path in the database
     *
     * @throws ApiException
     */
    @Test
    void testUpdateToolPathDockerfile() throws ApiException {
        //setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();

        DockstoreTool toolTest = toolsApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);
        toolsApi.refresh(toolTest.getId());

        //change the default dockerfile and refresh
        toolTest.setDefaultDockerfilePath("/test1/Dockerfile");
        toolsApi.updateTagContainerPath(toolTest.getId(), toolTest);
        toolsApi.refresh(toolTest.getId());

        //check if the tag's dockerfile path have the same dockerfile path or not in the database
        final String path = getPathfromDB("dockerfilepath");
        assertEquals("/test1/Dockerfile", path, "the cwl path should be changed to /test1/Dockerfile");
    }

    /**
     * Test to update the tool's forum and it should change the in the database
     *
     * @throws ApiException
     */
    @Test
    void testUpdateToolForumUrlAndTopic() throws ApiException {
        final String forumUrl = "hello.com";
        //setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();

        DockstoreTool toolTest = toolsApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);
        toolsApi.refresh(toolTest.getId());

        assertEquals(TopicSelectionEnum.AUTOMATIC, toolTest.getTopicSelection(), "Should default to automatic");

        //change the forumurl
        toolTest.setForumUrl(forumUrl);
        final String newTopic = "newTopic";
        toolTest.setTopicManual(newTopic);
        toolTest.setTopicSelection(TopicSelectionEnum.MANUAL);
        DockstoreTool dockstoreTool = toolsApi.updateContainer(toolTest.getId(), toolTest);

        //check the tool's forumurl is updated in the database
        final String updatedForumUrl = testingPostgres.runSelectStatement("select forumurl from tool where id = " + toolTest.getId(), String.class);
        assertEquals(forumUrl, updatedForumUrl, "the forumurl should be hello.com");

        // check the tool's topicManual and topicSelection
        assertEquals(newTopic, dockstoreTool.getTopicManual());
        assertEquals(TopicSelectionEnum.MANUAL, toolTest.getTopicSelection());
    }

    @Test
    void testTopicAfterRegisterAndRefresh() throws ApiException {
        ContainersApi toolsApi = setupWebService();

        DockstoreTool tool = toolsApi.registerManual(createManualTool());

        // confirm the tool topic settings
        final String topicAutomatic = tool.getTopicAutomatic();
        assertEquals("Test repo for dockstore", topicAutomatic);
        assertNull(tool.getTopicManual());
        assertEquals(TopicSelectionEnum.AUTOMATIC, tool.getTopicSelection());

        // set the automatic topic to a garbage string, change the manual topic, and select it
        final String topicManual = "a user-specified manual topic!";
        final String garbage = "fooooo";
        assertEquals(1,
            testingPostgres.runUpdateStatement(String.format("update tool set topicAutomatic = '%s', topicManual = '%s', topicSelection = '%s' where id = %d", garbage, topicManual, "MANUAL", tool.getId())));

        // confirm the new topic settings
        tool = toolsApi.getContainer(tool.getId(), null);
        assertEquals(garbage, tool.getTopicAutomatic());
        assertEquals(topicManual, tool.getTopicManual());
        assertEquals(TopicSelectionEnum.MANUAL, tool.getTopicSelection());

        // refresh the Tool
        DockstoreTool refreshedTool = toolsApi.refresh(tool.getId());

        // make sure the automatic topic was refreshed, and that the manual topic and selection are the same
        assertEquals(tool.getId(), refreshedTool.getId());
        assertEquals(topicAutomatic, refreshedTool.getTopicAutomatic());
        assertEquals(topicManual, refreshedTool.getTopicManual());
        assertEquals(TopicSelectionEnum.MANUAL, refreshedTool.getTopicSelection());
    }

    /**
     * Creates a basic Manual Tool with Quay
     *
     * @param gitUrl
     * @return
     */
    private DockstoreTool getQuayContainer(String gitUrl) {
        DockstoreTool tool = new DockstoreTool();
        tool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        tool.setName("my-md5sum");
        tool.setGitUrl(gitUrl);
        tool.setDefaultDockerfilePath("/md5sum/Dockerfile");
        tool.setDefaultCwlPath("/md5sum/md5sum-tool.cwl");
        tool.setRegistryString(Registry.QUAY_IO.getDockerPath());
        tool.setNamespace("dockstoretestuser2");
        tool.setToolname("altname");
        tool.setPrivateAccess(false);
        tool.setDefaultCWLTestParameterFile("/testcwl.json");
        return tool;
    }

    private io.dockstore.openapi.client.model.DockstoreTool getOpenApiQuayContainer(String gitUrl) {
        io.dockstore.openapi.client.model.DockstoreTool tool = new io.dockstore.openapi.client.model.DockstoreTool();
        tool.setMode(ModeEnum.MANUAL_IMAGE_PATH);
        tool.setName("my-md5sum");
        tool.setGitUrl(gitUrl);
        tool.setDefaultDockerfilePath("/md5sum/Dockerfile");
        tool.setDefaultCwlPath("/md5sum/md5sum-tool.cwl");
        tool.setRegistryString(Registry.QUAY_IO.getDockerPath());
        tool.setNamespace("dockstoretestuser2");
        tool.setToolname("altname");
        tool.setPrivateAccess(false);
        tool.setDefaultCWLTestParameterFile("/testcwl.json");
        return tool;
    }

    /**
     * Tests that manually adding a tool that should become auto is properly converted
     */
    @Test
    void testManualToAuto() {
        String gitUrl = "git@github.com:DockstoreTestUser2/md5sum-checker.git";
        ContainersApi toolsApi = setupWebService();
        DockstoreTool tool = getQuayContainer(gitUrl);
        DockstoreTool toolTest = toolsApi.registerManual(tool);
        assertEquals("Apache License 2.0", toolTest.getLicenseInformation().getLicenseName(), "Should be able to get license after manual register");

        // Clear license name to mimic old entry that does not have a license associated with it
        testingPostgres.runUpdateStatement("update tool set licensename=null");
        DockstoreTool refresh = toolsApi.refresh(toolTest.getId());
        assertEquals("Apache License 2.0", refresh.getLicenseInformation().getLicenseName(), "Should be able to get license after refresh");

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode = '" + DockstoreTool.ModeEnum.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS + "' and giturl = '"
                + gitUrl + "' and name = 'my-md5sum' and namespace = 'dockstoretestuser2' and toolname = 'altname'", long.class);
        assertEquals(1, count, "The tool should be auto, there are " + count);
    }

    /**
     * Tests that manually adding a tool that shouldn't become auto stays manual
     * The tool should specify a git URL that does not match any in any Quay builds
     */
    @Test
    void testManualToolStayManual() {
        String gitUrl = "git@github.com:DockstoreTestUser2/dockstore-whalesay-imports.git";
        ContainersApi toolsApi = setupWebService();
        DockstoreTool tool = getQuayContainer(gitUrl);
        DockstoreTool toolTest = toolsApi.registerManual(tool);
        toolsApi.refresh(toolTest.getId());

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode = '" + DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH + "' and giturl = '" + gitUrl
                + "' and name = 'my-md5sum' and namespace = 'dockstoretestuser2' and toolname = 'altname'", long.class);
        assertEquals(1, count, "The tool should be manual, there are " + count);
    }

    @Test
    void testCannotRegisterGarbageSourceControlFromDockerHub() {
        String gitUrl = "git@github.com:DockstoreTestUser2/bewareoftheleopard.git";
        ContainersApi toolsApi = setupWebService();
        DockstoreTool tool = getQuayContainer(gitUrl);

        try {
            toolsApi.registerManual(tool);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains(UNABLE_TO_VERIFY_THAT_YOUR_TOOL_POINTS_AT_A_VALID_SOURCE_CONTROL_REPO));
            return;
        }
        fail("should fail to register");
    }

    /**
     * Tests that the tool name is validated when manually registering a tool
     */
    @Test
    void testManualToolNameValidation() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi containersApi = new ContainersApi(webClient);
        DockstoreTool tool = createManualTool();

        try {
            tool.setToolname("!@#$%^&<foo>/<bar>");
            containersApi.registerManual(tool);
            fail("Should not be able to register a tool with a tool name containing special characters that are not underscores and hyphens.");
        } catch (ApiException ex) {
            assertTrue(ex.getMessage().contains("Invalid tool name"));
        }
    }

    /**
     * Tests that you can properly check if a user with some username exists
     */
    @Test
    void testCheckUser() {
        // Authorized user should pass
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        boolean userOneExists = userApi.checkUserExists("DockstoreTestUser2");
        assertTrue(userOneExists, "User DockstoreTestUser2 should exist");
        boolean userTwoExists = userApi.checkUserExists(BaseIT.OTHER_USERNAME);
        assertTrue(userTwoExists, "User OtherUser should exist");
        boolean fakeUserExists = userApi.checkUserExists("NotARealUser");
        assertFalse(fakeUserExists);

        // Unauthorized user should fail
        ApiClient unauthClient = CommonTestUtilities.getWebClient(false, "", testingPostgres);
        UsersApi unauthUserApi = new UsersApi(unauthClient);
        boolean failed = false;
        try {
            unauthUserApi.checkUserExists("DockstoreTestUser2");
        } catch (ApiException ex) {
            failed = true;
        }
        assertTrue(failed, "Should throw an exception when not authorized.");
    }

    /**
     * This tests that you can retrieve tools by alias (using optional auth)
     */
    @Test
    void testToolAlias() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi containersApi = new ContainersApi(webClient);
        EntriesApi entryApi = new EntriesApi(webClient);

        final ApiClient anonWebClient = CommonTestUtilities.getWebClient(false, null, testingPostgres);
        ContainersApi anonContainersApi = new ContainersApi(anonWebClient);

        final ApiClient otherUserWebClient = CommonTestUtilities.getWebClient(true, OTHER_USERNAME, testingPostgres);
        ContainersApi otherUserContainersApi = new ContainersApi(otherUserWebClient);

        // Add tool
        DockstoreTool tool = containersApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);
        DockstoreTool refresh = containersApi.refresh(tool.getId());

        // Add alias
        Entry entry = entryApi.addAliases(refresh.getId(), "foobar");
        assertTrue(entry.getAliases().containsKey("foobar"), "Should have alias foobar");

        // check that dates are present
        final Timestamp dbDate = testingPostgres.runSelectStatement("select dbcreatedate from entry_alias where id = " + entry.getId(), Timestamp.class);
        assertNotNull(dbDate);
        // check that date looks sane
        final Calendar instance = GregorianCalendar.getInstance();
        instance.set(2020, Calendar.MARCH, 13);
        assertTrue(dbDate.after(instance.getTime()));

        // Get unpublished tool by alias as owner
        DockstoreTool aliasTool = containersApi.getToolByAlias("foobar");
        assertNotNull(aliasTool, "Should retrieve the tool by alias");

        // Cannot get tool by alias as other user
        try {
            otherUserContainersApi.getToolByAlias("foobar");
            fail("Should not be able to retrieve tool.");
        } catch (ApiException ignored) {
            assertTrue(true);
        }

        // Cannot get tool by alias as anon user
        try {
            anonContainersApi.getToolByAlias("foobar");
            fail("Should not be able to retrieve tool.");
        } catch (ApiException ignored) {
            assertTrue(true);
        }

        // Publish tool
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        containersApi.publish(refresh.getId(), publishRequest);

        // Get published tool by alias as owner
        DockstoreTool publishedAliasTool = containersApi.getToolByAlias("foobar");
        assertNotNull(publishedAliasTool, "Should retrieve the tool by alias");

        // Cannot get tool by alias as other user
        publishedAliasTool = otherUserContainersApi.getToolByAlias("foobar");
        assertNotNull(publishedAliasTool, "Should retrieve the tool by alias");

        // Cannot get tool by alias as anon user
        publishedAliasTool = anonContainersApi.getToolByAlias("foobar");
        assertNotNull(publishedAliasTool, "Should retrieve the tool by alias");

    }

    /**
     * This tests a not found zip file
     */
    @Test
    void sillyContainerZipFile() throws IOException {
        final ApiClient anonWebClient = CommonTestUtilities.getWebClient(false, null, testingPostgres);
        ContainersApi anonContainersApi = new ContainersApi(anonWebClient);
        boolean success = false;
        try {
            anonContainersApi.getToolZip(100000000L, 1000000L);
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_NOT_FOUND, ex.getCode());
            success = true;
        }
        assertTrue(success, "should have got 404");
    }

    /**
     * This tests that zip file can be downloaded or not based on published state and auth.
     */
    @Test
    void downloadZipFileTestAuth() throws IOException {
        final ApiClient ownerWebClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi ownerContainersApi = new ContainersApi(ownerWebClient);

        final ApiClient anonWebClient = CommonTestUtilities.getWebClient(false, null, testingPostgres);
        ContainersApi anonContainersApi = new ContainersApi(anonWebClient);

        final ApiClient otherUserWebClient = CommonTestUtilities.getWebClient(true, OTHER_USERNAME, testingPostgres);
        ContainersApi otherUserContainersApi = new ContainersApi(otherUserWebClient);

        // Register and refresh tool
        DockstoreTool tool = ownerContainersApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);
        DockstoreTool refresh = ownerContainersApi.refresh(tool.getId());
        Long toolId = refresh.getId();
        Tag tag = refresh.getWorkflowVersions().get(0);
        Long versionId = tag.getId();

        // Try downloading unpublished
        // Owner: Should pass
        ownerContainersApi.getToolZip(toolId, versionId);
        // Anon: Should fail
        boolean success = true;
        try {
            anonContainersApi.getToolZip(toolId, versionId);
        } catch (ApiException ex) {
            success = false;
        } finally {
            assertFalse(success, "User does not have access to tool.");
        }
        // Other user: Should fail
        success = true;
        try {
            otherUserContainersApi.getToolZip(toolId, versionId);
        } catch (ApiException ex) {
            success = false;
        } finally {
            assertFalse(success, "User does not have access to tool.");
        }

        // Publish
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        ownerContainersApi.publish(toolId, publishRequest);

        // Try downloading published
        // Owner: Should pass
        ownerContainersApi.getToolZip(toolId, versionId);
        // Anon: Should pass
        anonContainersApi.getToolZip(toolId, versionId);
        // Other user: Should pass
        otherUserContainersApi.getToolZip(toolId, versionId);
    }

    @Test
    void testUsernameRequiredFilter() {
        String gitUrl = "git@github.com:DockstoreTestUser2/dockstore-whalesay-imports.git";
        io.dockstore.openapi.client.ApiClient openApiClient = BaseIT.getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.HostedApi openApiHosted = new io.dockstore.openapi.client.api.HostedApi(openApiClient);
        final io.dockstore.openapi.client.api.ContainersApi openApiContainers = new io.dockstore.openapi.client.api.ContainersApi(openApiClient);
        final io.dockstore.openapi.client.api.WorkflowsApi openApiWorkflows = new io.dockstore.openapi.client.api.WorkflowsApi(openApiClient);
        final io.dockstore.openapi.client.api.OrganizationsApi openApiOrganizations = new io.dockstore.openapi.client.api.OrganizationsApi(openApiClient);
        io.dockstore.openapi.client.api.UsersApi openApiUsers = new io.dockstore.openapi.client.api.UsersApi(openApiClient);

        io.dockstore.openapi.client.model.User user1 = openApiUsers.getUser();

        testingPostgres.runUpdateStatement("update enduser set usernameChangeRequired = 't' where username = 'DockstoreTestUser2'");
        try {
            openApiHosted.createHostedTool("awesomeTool", Registry.QUAY_IO.getDockerPath().toLowerCase(), CWL.getShortName(), "coolNamespace", null);
            fail("Should not be able to create a tool");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertTrue(ex.getMessage().contains("Your username contains one or more of the following keywords"));
        }

        io.dockstore.openapi.client.model.DockstoreTool tool1 = getOpenApiQuayContainer(gitUrl);
        try {
            openApiContainers.registerManual(tool1);
            fail("Should not be able to create a tool");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertTrue(ex.getMessage().contains("Your username contains one or more of the following keywords"));
        }

        tool1 = openApiContainers.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        try {
            openApiContainers.refresh(tool1.getId());
            fail("Should not be able to create a tool");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertTrue(ex.getMessage().contains("Your username contains one or more of the following keywords"));
        }

        tool1 = openApiContainers.getContainerByToolPath("registry.hub.docker.com/seqware/seqware/test5", null);
        io.dockstore.openapi.client.model.StarRequest starRequest1 = new io.dockstore.openapi.client.model.StarRequest();
        starRequest1.setStar(true);
        try {
            openApiContainers.starEntry(starRequest1, tool1.getId());
            fail("Should not be able to star a tool");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertTrue(ex.getMessage().contains("Your username contains one or more of the following keywords"));
        }

        try {
            openApiWorkflows.manualRegister("github", "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.wdl", "altname",
                DescriptorLanguage.WDL.getShortName(), "/test.json");
            fail("Should not be able to register a workflow");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertTrue(ex.getMessage().contains("Your username contains one or more of the following keywords"));
        }

        // briefly switch so the workflow can get published and check that a previously blocked request works now
        testingPostgres.runUpdateStatement("update tool set ispublished = 'f'");
        openApiUsers.changeUsername("thisIsFine");
        openApiContainers.starEntry(starRequest1, tool1.getId());
        openApiWorkflows.manualRegister("github", "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.wdl", "altname", DescriptorLanguage.WDL.getShortName(), "/test.json");
        io.dockstore.openapi.client.model.Workflow workflow1 = openApiWorkflows.getWorkflow(1000L, null);

        // Change back to continue testing
        testingPostgres.runUpdateStatement("update enduser set usernameChangeRequired = 't' where username = 'thisIsFine'");
        testingPostgres.runUpdateStatement("update enduser set username = 'DockstoreTestUser2' where username = 'thisIsFine'");

        try {
            openApiWorkflows.starEntry1(workflow1.getId(), starRequest1);
            fail("Should not be able to star a workflow");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertTrue(ex.getMessage().contains("Your username contains one or more of the following keywords"));
        }

        try {
            openApiWorkflows.addWorkflow(SourceControl.GITHUB.name(), "dockstoretesting", "basic-workflow");
            fail("Should not be able to add a workflow");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertTrue(ex.getMessage().contains("Your username contains one or more of the following keywords"));
        }

        io.dockstore.openapi.client.model.Organization organization = new io.dockstore.openapi.client.model.Organization();
        organization.setName("testname");
        organization.setDisplayName("test name");
        organization.setEmail("test@email.com");
        organization.setDescription("");
        organization.setTopic("This is a short topic");

        try {
            openApiOrganizations.createOrganization(organization);
            fail("Should not be able to create an organization");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertTrue(ex.getMessage().contains("Your username contains one or more of the following keywords"));
        }

        testingPostgres.runUpdateStatement("update enduser set usernameChangeRequired = 'f' where username = 'DockstoreTestUser2'");
        organization = openApiOrganizations.createOrganization(organization);
        testingPostgres.runUpdateStatement("update enduser set usernameChangeRequired = 't' where username = 'DockstoreTestUser2'");

        try {
            openApiOrganizations.starOrganization(starRequest1, organization.getId());
            fail("Should not be able to star an organization");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertTrue(ex.getMessage().contains("Your username contains one or more of the following keywords"));
        }
    }
}
