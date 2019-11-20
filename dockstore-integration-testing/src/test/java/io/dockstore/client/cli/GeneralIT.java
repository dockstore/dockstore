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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Registry;
import io.dockstore.common.ToolTest;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.EntriesApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Entry;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static io.dockstore.webservice.core.Version.CANNOT_FREEZE_VERSIONS_WITH_NO_FILES;
import static io.dockstore.webservice.helpers.EntryVersionHelper.CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Extra confidential integration tests, don't rely on the type of repository used (Github, Dockerhub, Quay.io, Bitbucket)
 *
 * @author aduncan
 */
@Category({ ConfidentialTest.class, ToolTest.class })
public class GeneralIT extends BaseIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    /**
     * This method will create and register a new container for testing
     *
     * @return DockstoreTool
     * @throws ApiException
     */
    private DockstoreTool getContainer() {
        DockstoreTool c = new DockstoreTool();
        c.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        c.setName("testUpdatePath");
        c.setGitUrl("https://github.com/DockstoreTestUser2/dockstore-tool-imports");
        c.setDefaultDockerfilePath("/Dockerfile");
        c.setDefaultCwlPath("/dockstore.cwl");
        c.setRegistryString(Registry.DOCKER_HUB.toString());
        c.setIsPublished(false);
        c.setNamespace("testPath");
        c.setToolname("test5");
        c.setPath("quay.io/dockstoretestuser2/dockstore-tool-imports");
        Tag tag = new Tag();
        tag.setName("1.0");
        tag.setReference("master");
        tag.setValid(true);
        tag.setImageId("123456");
        tag.setCwlPath(c.getDefaultCwlPath());
        tag.setWdlPath(c.getDefaultWdlPath());
        // construct source files
        SourceFile fileCWL = new SourceFile();
        fileCWL.setContent("cwlstuff");
        fileCWL.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        fileCWL.setPath("/dockstore.cwl");
        fileCWL.setAbsolutePath("/dockstore.cwl");
        List<SourceFile> list = new ArrayList<>();
        list.add(fileCWL);
        tag.setSourceFiles(list);
        SourceFile fileDockerFile = new SourceFile();
        fileDockerFile.setContent("dockerstuff");
        fileDockerFile.setType(SourceFile.TypeEnum.DOCKERFILE);
        fileDockerFile.setPath("/Dockerfile");
        fileDockerFile.setAbsolutePath("/Dockerfile");
        tag.getSourceFiles().add(fileDockerFile);
        List<Tag> tags = new ArrayList<>();
        tags.add(tag);
        c.setWorkflowVersions(tags);
        return c;
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
     * this method will set up the databse and select data needed
     *
     * @return cwl/wdl/dockerfile path of the tool's tag in the database
     * @throws ApiException
     */
    private String getPathfromDB(String type) {
        // Set up DB

        // Select data from DB
        final Long toolID = testingPostgres.runSelectStatement("select id from tool where name = 'testUpdatePath'", long.class);
        final Long tagID = testingPostgres.runSelectStatement("select tagid from tool_tag where toolid = " + toolID, long.class);

        return testingPostgres.runSelectStatement("select " + type + " from tag where id = " + tagID, String.class);
    }

    /**
     * Checks that all automatic containers have been found by dockstore and are not registered/published
     */
    @Test
    public void testListAvailableContainers() {

        final long count = testingPostgres.runSelectStatement("select count(*) from tool where ispublished='f'", long.class);
        assertEquals("there should be 4 entries, there are " + count, 4, count);
    }

    /**
     * Checks that you can't add/remove labels unless they all are of proper format
     */
    @Test
    public void testLabelIncorrectInput() {
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
    public void testAddEditRemoveLabel() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);

        // Test adding/removing labels for different containers
        toolApi.updateLabels(tool.getId(), "quay,github", "");
        toolApi.updateLabels(tool.getId(), "github,dockerhub", "");
        toolApi.updateLabels(tool.getId(), "alternate,github,dockerhub", "");
        toolApi.updateLabels(tool.getId(), "alternate,dockerhub", "");

        final long count = testingPostgres.runSelectStatement("select count(*) from entry_label where entryid = '2'", long.class);
        assertEquals("there should be 2 labels for the given container, there are " + count, 2, count);

        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from label where value = 'quay' or value = 'github' or value = 'dockerhub' or value = 'alternate'",
            long.class);
        assertEquals("there should be 4 labels in the database (No Duplicates), there are " + count2, 4, count2);

    }

    /**
     * Tests altering the cwl and dockerfile paths to invalid locations (quick registered)
     */
    @Test
    public void testVersionTagWDLCWLAndDockerfilePathsAlteration() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        Optional<Tag> tag = tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master")).findFirst();
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
            "select count(*) from tag,tool_tag,tool where tool.registry = '" + Registry.QUAY_IO.toString()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithub' and tool.toolname IS NULL and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'",
            long.class);
        assertEquals("there should now be an invalid tag, found " + count, 1, count);

        tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master")).findFirst();
        if (tag.isEmpty()) {
            fail("Tag master should exist");
        }
        tags = new ArrayList<>();
        updatedTag = tag.get();
        updatedTag.setCwlPath("/Dockstore.cwl");
        updatedTag.setWdlPath("/Dockstore.wdl");
        updatedTag.setDockerfilePath("/Dockerfile");
        tags.add(updatedTag);
        toolTagsApi.updateTags(tool.getId(), tags);
        tool = toolApi.refresh(tool.getId());

        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tag,tool_tag,tool where tool.registry = '" + Registry.QUAY_IO.toString()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithub' and tool.toolname IS NULL and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'",
            long.class);
        assertEquals("the invalid tag should now be valid, found " + count2, 0, count2);
    }

    /**
     * Test trying to remove a tag for auto build
     */
    @Test
    public void testVersionTagRemoveAutoContainer() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        Optional<Tag> tag = tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master")).findFirst();
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
    public void testVersionTagAddAutoContainer() {
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
        tool.setRegistryString(Registry.QUAY_IO.toString());
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

    /**
     * Tests adding tags to a manually registered container
     */
    @Test
    public void testAddVersionTagManualContainer() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = createManualTool();
        tool.setDefaultDockerfilePath("/testDir/Dockerfile");
        tool.setDefaultCwlPath("/testDir/Dockstore.cwl");
        tool = toolApi.registerManual(tool);
        toolApi.refresh(tool.getId());

        // Add tag
        tool = addTag(tool, toolTagsApi, toolApi);

        final long count = testingPostgres.runSelectStatement(
            " select count(*) from  tool_tag, tool where tool_tag.toolid = tool.id and giturl ='git@github.com:dockstoretestuser2/quayandgithubalternate.git' and toolname = 'alternate'",
            long.class);
        assertEquals(
            "there should be 3 tags, 2  that are autogenerated (master and latest) and the newly added masterTest tag, found " + count, 3,
            count);
    }

    /**
     * Tests hiding and unhiding different versions of a container (quick registered)
     */
    @Test
    public void testVersionTagHide() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        Optional<Tag> tag = tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master")).findFirst();
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
            .runSelectStatement("select count(*) from tag t, version_metadata vm where vm.hidden = 't' and t.id = vm.id", long.class);
        assertEquals("there should be 1 hidden tag", 1, count);

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
            .runSelectStatement("select count(*) from tag t, version_metadata vm where vm.hidden = 't' and t.id = vm.id", long.class);
        assertEquals("there should be 0 hidden tag", 0, count2);
    }

    /**
     * Test update tag with only WDL to invalid then valid
     */
    @Test
    public void testVersionTagWDL() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithubwdl", null);
        Optional<Tag> tag = tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master")).findFirst();
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
            "select count(*) from tag,tool_tag,tool where tool.registry = '" + Registry.QUAY_IO.toString()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithubwdl' and tool.toolname IS NULL and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'",
            long.class);

        assertEquals("there should now be 1 invalid tag, found " + count, 1, count);
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
            "select count(*) from tag,tool_tag,tool where tool.registry = '" + Registry.QUAY_IO.toString()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithubwdl' and tool.toolname IS NULL and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'",
            long.class);
        assertEquals("the tag should now be valid", 0, count2);

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

    /**
     * Will test deleting a tag from a manually registered container
     */
    @Test
    public void testVersionTagDelete() {
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
        Optional<Tag> optionalTag = tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "masterTest")).findFirst();
        if (optionalTag.isEmpty()) {
            fail("Tag masterTest should exist");
        }
        toolTagsApi.deleteTags(tool.getId(), optionalTag.get().getId());

        final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", long.class);
        assertEquals("there should be no tags with the name masterTest", 0, count);
    }

    /**
     * Check that cannot retrieve an incorrect individual container
     */
    @Test
    public void testGetIncorrectContainer() {
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
    public void testGetOtherUsersContainer() {
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
    public void testUserPrivilege() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);

        DockstoreTool tool = createManualTool();
        tool.setToolname("testTool");
        tool.setDefaultDockerfilePath("/testDir/Dockerfile");
        tool.setDefaultCwlPath("/testDir/Dockstore.cwl");
        tool = toolApi.registerManual(tool);
        tool = toolApi.refresh(tool.getId());

        // Repo user has access to
        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.toString()
            + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithub' and toolname = 'testTool'", long.class);
        assertEquals("the container should exist", 1, count);

        // Repo user is part of org
        DockstoreTool tool2 = createManualTool();
        tool2.setToolname("testOrg");
        tool2.setName("testrepo2");
        tool2.setNamespace("dockstore2");
        tool2.setGitUrl("git@github.com:dockstoretestuser2/quayandgithub.git");
        tool2 = toolApi.registerManual(tool2);
        tool2 = toolApi.refresh(tool2.getId());

        final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.toString()
            + "' and namespace = 'dockstore2' and name = 'testrepo2' and toolname = 'testOrg'", long.class);
        assertEquals("the container should exist", 1, count2);

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
    public void testUpdateToolPathCWL() throws ApiException {
        //setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();

        //register tool
        DockstoreTool c = getContainer();
        DockstoreTool toolTest = toolsApi.registerManual(c);
        toolsApi.refresh(toolTest.getId());

        //change the default cwl path and refresh
        toolTest.setDefaultCwlPath("/test1.cwl");
        toolsApi.updateTagContainerPath(toolTest.getId(), toolTest);
        toolsApi.refresh(toolTest.getId());

        //check if the tag's dockerfile path have the same cwl path or not in the database
        final String path = getPathfromDB("cwlpath");
        assertEquals("the cwl path should be changed to /test1.cwl", "/test1.cwl", path);
    }

    /**
     * should be able to refresh a tool where image ids are changing (constraints issue from #1405)
     *
     * @throws ApiException should not see error from the webservice
     */
    @Test
    public void testImageIDUpdateDuringRefresh() throws ApiException {
        ContainersApi containersApi = setupWebService();

        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        c.setRegistry(DockstoreTool.RegistryEnum.QUAY_IO);
        c.setNamespace("dockstoretestuser2");
        c.setName("dockstore-tool-imports");
        c.setMode(DockstoreTool.ModeEnum.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS);
        c = containersApi.registerManual(c);

        assertTrue("should see one (or more) tags: " + c.getWorkflowVersions().size(), c.getWorkflowVersions().size() >= 1);

        UsersApi usersApi = new UsersApi(containersApi.getApiClient());
        final Long userid = usersApi.getUser().getId();
        usersApi.refresh(userid);

        testingPostgres.runUpdateStatement("update tag set imageid = 'silly old value'");
        int size = containersApi.getContainer(c.getId(), null).getWorkflowVersions().size();
        long size2 = containersApi.getContainer(c.getId(), null).getWorkflowVersions().stream()
            .filter(tag -> tag.getImageId().equals("silly old value")).count();
        assertTrue(size == size2 && size >= 1);
        // individual refresh should update image ids
        containersApi.refresh(c.getId());
        DockstoreTool container = containersApi.getContainer(c.getId(), null);
        size = container.getWorkflowVersions().size();
        size2 = container.getWorkflowVersions().stream().filter(tag -> tag.getImageId().equals("silly old value")).count();
        assertTrue(size2 == 0 && size >= 1);

        // so should overall refresh
        testingPostgres.runUpdateStatement("update tag set imageid = 'silly old value'");
        usersApi.refresh(userid);
        container = containersApi.getContainer(c.getId(), null);
        size = container.getWorkflowVersions().size();
        size2 = container.getWorkflowVersions().stream().filter(tag -> tag.getImageId().equals("silly old value")).count();
        assertTrue(size2 == 0 && size >= 1);

        // so should organizational refresh
        testingPostgres.runUpdateStatement("update tag set imageid = 'silly old value'");
        usersApi.refreshToolsByOrganization(userid, container.getNamespace());
        container = containersApi.getContainer(c.getId(), null);
        size = container.getWorkflowVersions().size();
        size2 = container.getWorkflowVersions().stream().filter(tag -> tag.getImageId().equals("silly old value")).count();
        assertTrue(size2 == 0 && size >= 1);
    }

    /**
     * Tests that image and checksum information can be grabbed from Quay and update db correctly.
     */
    @Test
    public void testGrabbingImagesFromQuay() {
        ContainersApi containersApi = setupWebService();
        DockstoreTool tool = getContainer();
        tool.setRegistry(DockstoreTool.RegistryEnum.QUAY_IO);
        tool.setNamespace("dockstoretestuser2");
        tool.setName("dockstore-tool-imports");
        tool.setMode(DockstoreTool.ModeEnum.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS);
        tool = containersApi.registerManual(tool);

        assertEquals(0, containersApi.getContainer(tool.getId(), null).getWorkflowVersions().get(0).getImages().size());

        UsersApi usersApi = new UsersApi(containersApi.getApiClient());
        final Long userid = usersApi.getUser().getId();
        usersApi.refresh(userid);

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
        usersApi.refresh(userid);
        final long count2 = testingPostgres.runSelectStatement("select count(*) from image", long.class);
        assertEquals(imageID, containersApi.getContainer(tool.getId(), null).getWorkflowVersions().get(0).getImages().get(0).getImageID());
        assertEquals(imageID2, containersApi.getContainer(tool.getId(), null).getWorkflowVersions().get(1).getImages().get(0).getImageID());
        assertEquals(count, count2);
    }

    /**
     * Tests that if a tool has a tag with mismatching tag name and tag reference, and it is set as the default tag
     * then the author metadata is properly grabbed.
     */
    @Test
    public void testParseMetadataFromToolWithTagNameAndReferenceMismatch() {
        // Setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();

        // Create tool with mismatching tag name and tag reference
        DockstoreTool tool = getContainer();
        tool.setDefaultVersion("1.0");
        DockstoreTool toolTest = toolsApi.registerManual(tool);
        toolsApi.refresh(toolTest.getId());

        DockstoreTool refreshedTool = toolsApi.getContainer(toolTest.getId(), null);
        assertNotNull("Author should be set, even if tag name and tag reference are mismatched.", refreshedTool.getAuthor());
    }

    /**
     * Test to update the default path of WDL and it should change the tag's WDL path in the database
     *
     * @throws ApiException
     */
    @Test
    public void testUpdateToolPathWDL() throws ApiException {
        //setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();

        //register tool
        DockstoreTool c = getContainer();
        DockstoreTool toolTest = toolsApi.registerManual(c);
        toolsApi.refresh(toolTest.getId());

        //change the default wdl path and refresh
        toolTest.setDefaultWdlPath("/test1.wdl");
        toolsApi.updateTagContainerPath(toolTest.getId(), toolTest);
        toolsApi.refresh(toolTest.getId());

        //check if the tag's wdl path have the same wdl path or not in the database
        final String path = getPathfromDB("wdlpath");
        assertEquals("the cwl path should be changed to /test1.wdl", "/test1.wdl", path);
    }

    @Test
    public void testToolFreezingWithNoFiles() {
        //setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();
        ContainertagsApi tagsApi = new ContainertagsApi(toolsApi.getApiClient());

        //register tool
        DockstoreTool c = getContainer();
        c.setDefaultCwlPath("foo.cwl");
        c.setDefaultWdlPath("foo.wdl");
        c.setDefaultDockerfilePath("foo");
        c.getWorkflowVersions().forEach(tag -> {
            tag.setCwlPath("foo.cwl");
            tag.setWdlPath("foo.wdl");
            tag.setDockerfilePath("foo");
        });
        DockstoreTool toolTest = toolsApi.registerManual(c);
        DockstoreTool refresh = toolsApi.refresh(toolTest.getId());
        assertFalse(refresh.getWorkflowVersions().isEmpty());
        Tag master = refresh.getWorkflowVersions().stream().filter(t -> t.getName().equals("1.0")).findFirst().get();
        master.setFrozen(true);
        master.setImageId("awesomeid");
        try {
            tagsApi.updateTags(refresh.getId(), Lists.newArrayList(master));
        } catch (ApiException e) {
            // should exception
            assertTrue("missing error message", e.getMessage().contains(CANNOT_FREEZE_VERSIONS_WITH_NO_FILES));
            return;
        }
        fail("should be unreachable");
    }

    @Test
    public void testToolFreezing() throws ApiException {
        //setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();
        ContainertagsApi tagsApi = new ContainertagsApi(toolsApi.getApiClient());

        //register tool
        DockstoreTool c = getContainer();
        DockstoreTool toolTest = toolsApi.registerManual(c);
        DockstoreTool refresh = toolsApi.refresh(toolTest.getId());

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
        master.setDoiURL("foo");
        tags = tagsApi.updateTags(refresh.getId(), Lists.newArrayList(master));
        master = tags.stream().filter(t -> t.getName().equals("1.0")).findFirst().get();
        assertEquals("foo", master.getDoiURL());
        assertEquals(Tag.DoiStatusEnum.REQUESTED, master.getDoiStatus());

        // try modifying sourcefiles
        // cannot modify sourcefiles for a frozen version
        assertFalse(master.getSourceFiles().isEmpty());
        master.getSourceFiles().forEach(s -> {
            assertTrue(s.isFrozen());
            testingPostgres.runUpdateStatement("update sourcefile set content = 'foo' where id = " + s.getId());
            final String content = testingPostgres
                .runSelectStatement("select content from sourcefile where id = " + s.getId(), String.class);
            assertNotEquals("foo", content);
        });

        // try deleting a row join table
        master.getSourceFiles().forEach(s -> {
            final int affected = testingPostgres.runUpdateStatement("delete from version_sourcefile vs where vs.sourcefileid = " + s.getId());
            assertEquals(0, affected);
        });

        // try updating a row in the join table
        master.getSourceFiles().forEach(s -> {
            final int affected = testingPostgres.runUpdateStatement("update version_sourcefile set sourcefileid=123456 where sourcefileid = " + s.getId());
            assertEquals(0, affected);
        });

        final Long versionId = master.getId();
        // try creating a row in the join table
        master.getSourceFiles().forEach(s -> {
            try {
                testingPostgres.runUpdateStatement("insert into version_sourcefile (versionid, sourcefileid) values (" + versionId
                        + ", " + 1234567890 + ")");
                fail("Insert should have failed to do row-level security");
            } catch (Exception ex) {
                Assert.assertTrue(ex.getMessage().contains("new row violates row-level"));
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
    public void testUpdateToolPathDockerfile() throws ApiException {
        //setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();

        //register tool
        DockstoreTool c = getContainer();
        DockstoreTool toolTest = toolsApi.registerManual(c);
        toolsApi.refresh(toolTest.getId());

        //change the default dockerfile and refresh
        toolTest.setDefaultDockerfilePath("/test1/Dockerfile");
        toolsApi.updateTagContainerPath(toolTest.getId(), toolTest);
        toolsApi.refresh(toolTest.getId());

        //check if the tag's dockerfile path have the same dockerfile path or not in the database
        final String path = getPathfromDB("dockerfilepath");
        assertEquals("the cwl path should be changed to /test1/Dockerfile", "/test1/Dockerfile", path);
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
        tool.setRegistryString(Registry.QUAY_IO.toString());
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
    public void testManualToAuto() {
        String gitUrl = "git@github.com:DockstoreTestUser2/md5sum-checker.git";
        ContainersApi toolsApi = setupWebService();
        DockstoreTool tool = getQuayContainer(gitUrl);
        DockstoreTool toolTest = toolsApi.registerManual(tool);
        toolsApi.refresh(toolTest.getId());

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode = '" + DockstoreTool.ModeEnum.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS + "' and giturl = '"
                + gitUrl + "' and name = 'my-md5sum' and namespace = 'dockstoretestuser2' and toolname = 'altname'", long.class);
        assertEquals("The tool should be auto, there are " + count, 1, count);
    }

    /**
     * Tests that manually adding a tool that shouldn't become auto stays manual
     * The tool should specify a git URL that does not match any in any Quay builds
     */
    @Test
    public void testManualToolStayManual() {
        String gitUrl = "git@github.com:DockstoreTestUser2/dockstore-whalesay-imports.git";
        ContainersApi toolsApi = setupWebService();
        DockstoreTool tool = getQuayContainer(gitUrl);
        DockstoreTool toolTest = toolsApi.registerManual(tool);
        toolsApi.refresh(toolTest.getId());

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode = '" + DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH + "' and giturl = '" + gitUrl
                + "' and name = 'my-md5sum' and namespace = 'dockstoretestuser2' and toolname = 'altname'", long.class);
        assertEquals("The tool should be manual, there are " + count, 1, count);
    }

    /**
     * Tests that you can properly check if a user with some username exists
     */
    @Test
    public void testCheckUser() {
        // Authorized user should pass
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        boolean userOneExists = userApi.checkUserExists("DockstoreTestUser2");
        assertTrue("User DockstoreTestUser2 should exist", userOneExists);
        boolean userTwoExists = userApi.checkUserExists(BaseIT.OTHER_USERNAME);
        assertTrue("User OtherUser should exist", userTwoExists);
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
        assertTrue("Should throw an expection when not authorized.", failed);
    }

    /**
     * This tests that you can retrieve tools by alias (using optional auth)
     */
    @Test
    public void testToolAlias() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi containersApi = new ContainersApi(webClient);
        EntriesApi entryApi = new EntriesApi(webClient);

        final ApiClient anonWebClient = CommonTestUtilities.getWebClient(false, null, testingPostgres);
        ContainersApi anonContainersApi = new ContainersApi(anonWebClient);

        final ApiClient otherUserWebClient = CommonTestUtilities.getWebClient(true, OTHER_USERNAME, testingPostgres);
        ContainersApi otherUserContainersApi = new ContainersApi(otherUserWebClient);

        // Add tool
        DockstoreTool tool = containersApi.registerManual(getContainer());
        DockstoreTool refresh = containersApi.refresh(tool.getId());

        // Add alias
        Entry entry = entryApi.addAliases(refresh.getId(), "foobar");
        Assert.assertTrue("Should have alias foobar", entry.getAliases().containsKey("foobar"));

        // Get unpublished tool by alias as owner
        DockstoreTool aliasTool = containersApi.getToolByAlias("foobar");
        Assert.assertNotNull("Should retrieve the tool by alias", aliasTool);

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
        PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        containersApi.publish(refresh.getId(), publishRequest);

        // Get published tool by alias as owner
        DockstoreTool publishedAliasTool = containersApi.getToolByAlias("foobar");
        Assert.assertNotNull("Should retrieve the tool by alias", publishedAliasTool);

        // Cannot get tool by alias as other user
        publishedAliasTool = otherUserContainersApi.getToolByAlias("foobar");
        Assert.assertNotNull("Should retrieve the tool by alias", publishedAliasTool);

        // Cannot get tool by alias as anon user
        publishedAliasTool = anonContainersApi.getToolByAlias("foobar");
        Assert.assertNotNull("Should retrieve the tool by alias", publishedAliasTool);

    }

    /**
     * This tests that zip file can be downloaded or not based on published state and auth.
     */
    @Test
    public void downloadZipFileTestAuth() throws IOException {
        final ApiClient ownerWebClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi ownerContainersApi = new ContainersApi(ownerWebClient);

        final ApiClient anonWebClient = CommonTestUtilities.getWebClient(false, null, testingPostgres);
        ContainersApi anonContainersApi = new ContainersApi(anonWebClient);

        final ApiClient otherUserWebClient = CommonTestUtilities.getWebClient(true, OTHER_USERNAME, testingPostgres);
        ContainersApi otherUserContainersApi = new ContainersApi(otherUserWebClient);

        // Register and refresh tool
        DockstoreTool tool = ownerContainersApi.registerManual(getContainer());
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
            assertFalse("User does not have access to tool.", success);
        }
        // Other user: Should fail
        success = true;
        try {
            otherUserContainersApi.getToolZip(toolId, versionId);
        } catch (ApiException ex) {
            success = false;
        } finally {
            assertFalse("User does not have access to tool.", success);
        }

        // Publish
        PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        ownerContainersApi.publish(toolId, publishRequest);

        // Try downloading published
        // Owner: Should pass
        ownerContainersApi.getToolZip(toolId, versionId);
        // Anon: Should pass
        anonContainersApi.getToolZip(toolId, versionId);
        // Other user: Should pass
        otherUserContainersApi.getToolZip(toolId, versionId);
    }

}
