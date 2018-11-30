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
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Registry;
import io.dockstore.common.ToilCompatibleTest;
import io.dockstore.common.ToolTest;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Extra confidential integration tests, don't rely on the type of repository used (Github, Dockerhub, Quay.io, Bitbucket)
 *
 * @author aduncan
 */
@Category({ConfidentialTest.class, ToolTest.class})
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
        c.setTags(tags);
        return c;
    }

    /**
     * this method will set up the webservice and return the container api
     *
     * @return ContainersApi
     * @throws ApiException
     */
    private ContainersApi setupWebService() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME);
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
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        // Select data from DB
        final Long toolID = testingPostgres.runSelectStatement("select id from tool where name = 'testUpdatePath'", new ScalarHandler<>());
        final Long tagID = testingPostgres.runSelectStatement("select tagid from tool_tag where toolid = " + toolID, new ScalarHandler<>());

        return testingPostgres.runSelectStatement("select " + type + " from tag where id = " + tagID, new ScalarHandler<>());
    }

    /**
     * Checks that all automatic containers have been found by dockstore and are not registered/published
     */
    @Test
    public void testListAvailableContainers() {
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from tool where ispublished='f'", new ScalarHandler<>());
        assertEquals("there should be 4 entries, there are " + count, 4, count);
    }

    /**
     * Checks that you can't add/remove labels unless they all are of proper format
     */
    @Test
    public void testLabelIncorrectInput() {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--add", "docker-hub", "--add", "quay.io", "--script" });
    }

    /**
     * Checks that you can't add/remove labels if there is a duplicate label being added and removed
     */
    @Test
    public void testLabelMatchingAddAndRemove() {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--add", "quay", "--add", "dockerhub", "--remove", "dockerhub", "--script" });
    }

    /**
     * Tests adding/editing/deleting container related labels (for search)
     */
    @Test
    public void testAddEditRemoveLabel() {
        // Test adding/removing labels for different containers
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--add", "quay", "--add", "github", "--remove", "dockerhub", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--add", "github", "--add", "dockerhub", "--remove", "quay", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubalternate", "--add", "alternate", "--add", "github", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubalternate", "--remove", "github", "--script" });

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres
                .runSelectStatement("select count(*) from entry_label where entryid = '2'", new ScalarHandler<>());
        assertEquals("there should be 2 labels for the given container, there are " + count, 2, count);

        final long count2 = testingPostgres.runSelectStatement(
                "select count(*) from label where value = 'quay' or value = 'github' or value = 'dockerhub' or value = 'alternate'",
                new ScalarHandler<>());
        assertEquals("there should be 4 labels in the database (No Duplicates), there are " + count2, 4, count2);

    }

    /**
     * Tests altering the cwl and dockerfile paths to invalid locations (quick registered)
     */
    @Test
    public void testVersionTagWDLCWLAndDockerfilePathsAlteration() {
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--cwl-path", "/testDir/Dockstore.cwl",
                        "--wdl-path", "/testDir/Dockstore.wdl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement(
                "select count(*) from tag,tool_tag,tool where tool.registry = '"+ Registry.QUAY_IO.toString() +"' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithub' and tool.toolname IS NULL and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'",
                new ScalarHandler<>());
        assertEquals("there should now be an invalid tag, found " + count, 1, count);

        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--cwl-path", "/Dockstore.cwl", "--wdl-path",
                        "/Dockstore.wdl", "--dockerfile-path", "/Dockerfile", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "refresh", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--script" });

        final long count2 = testingPostgres.runSelectStatement(
                "select count(*) from tag,tool_tag,tool where tool.registry = '"+ Registry.QUAY_IO.toString() +"' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithub' and tool.toolname IS NULL and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'",
                new ScalarHandler<>());
        assertEquals("the invalid tag should now be valid, found " + count2, 0, count2);
    }

    /**
     * Test trying to remove a tag tag for auto build
     */
    @Test
    public void testVersionTagRemoveAutoContainer() {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "remove", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--script" });
    }

    /**
     * Test trying to add a tag tag for auto build
     */
    @Test
    public void testVersionTagAddAutoContainer() {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--name", "masterTest", "--image-id",
                        "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });
    }

    /**
     * Tests adding tag tags to a manually registered container
     */
    @Test
    public void testAddVersionTagManualContainer() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url",
                "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference", "master", "--toolname", "alternate",
                "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub/alternate", "--name", "masterTest", "--image-id",
                        "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement(
                " select count(*) from  tool_tag, tool where tool_tag.toolid = tool.id and giturl ='git@github.com:dockstoretestuser2/quayandgithubalternate.git' and toolname = 'alternate'",
                new ScalarHandler<>());
        assertEquals(
            "there should be 3 tags, 2  that are autogenerated (master and latest) and the newly added masterTest tag, found " + count, 3,
            count);

    }

    /**
     * Tests hiding and unhiding different versions of a container (quick registered)
     */
    @Test
    public void testVersionTagHide() {
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--hidden", "true", "--script" });

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from tag where hidden = 't'", new ScalarHandler<>());
        assertEquals("there should be 1 hidden tag", 1, count);

        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--hidden", "false", "--script" });

        final long count2 = testingPostgres.runSelectStatement("select count(*) from tag where hidden = 't'", new ScalarHandler<>());
        assertEquals("there should be 0 hidden tag", 0, count2);
    }

    /**
     * Test update tag with only WDL to invalid then valid
     */
    @Test
    public void testVersionTagWDL() {
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl", "--name", "master", "--wdl-path", "/randomDir/Dockstore.wdl",
                        "--script" });
        // should now be invalid
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement(
                "select count(*) from tag,tool_tag,tool where tool.registry = '"+ Registry.QUAY_IO.toString() +"' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithubwdl' and tool.toolname IS NULL and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'",
                new ScalarHandler<>());

        assertEquals("there should now be 1 invalid tag, found " + count, 1, count);

        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl", "--name", "master", "--wdl-path", "/Dockstore.wdl", "--script" });
        // should now be valid
        final long count2 = testingPostgres.runSelectStatement(
                "select count(*) from tag,tool_tag,tool where tool.registry = '"+ Registry.QUAY_IO.toString() +"' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithubwdl' and tool.toolname IS NULL and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'",
                new ScalarHandler<>());
        assertEquals("the tag should now be valid", 0, count2);

    }

    /**
     * Will test deleting a tag tag from a manually registered container
     */
    @Test
    public void testVersionTagDelete() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url",
                "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference", "master", "--toolname", "alternate",
                "--cwl-path", "/testDir/Dockstore.cwl", "--wdl-path", "/testDir/Dockstore.wdl", "--dockerfile-path", "/testDir/Dockerfile",
                "--script" });

        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub/alternate", "--name", "masterTest", "--image-id",
                        "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "remove", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub/alternate", "--name", "masterTest", "--script" });

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", new ScalarHandler<>());
        assertEquals("there should be no tags with the name masterTest", 0, count);
    }

    /**
     * Check that refreshing an incorrect individual container won't work
     */
    @Test
    public void testRefreshIncorrectContainer() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "refresh", "--entry",
                "quay.io/dockstoretestuser2/unknowncontainer", "--script" });
    }

    /**
     * Tests that tool2JSON works for entries on Dockstore
     */
    @Test
    public void testTool2JSONWDL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubwdl" });
        // need to publish before converting
        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "entry2json", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl", "--descriptor", "wdl", "--script" });
        // TODO: Test that output is the expected WDL file
        Assert.assertTrue(systemOutRule.getLog().contains("\"test.hello.name\": \"String\""));
    }

    @Test
    public void registerUnregisterAndCopy() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubwdl" });
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        boolean published = testingPostgres
                .runSelectStatement("select ispublished from tool where registry = '"+ Registry.QUAY_IO.toString() +"' and namespace = 'dockstoretestuser2' and name = 'quayandgithubwdl';",
                        new ScalarHandler<>());
        assertTrue("tool not published", published);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubwdl", "--entryname", "foo" });

        long count = testingPostgres
                .runSelectStatement("select count(*) from tool where registry = '"+ Registry.QUAY_IO.toString() +"' and namespace = 'dockstoretestuser2' and name = 'quayandgithubwdl';",
                        new ScalarHandler<>());
        assertEquals("should be two after republishing", 2, count);

        Client.main(
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--unpub", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl" });

        published = testingPostgres.runSelectStatement(
                "select ispublished from tool where registry = '"+ Registry.QUAY_IO.toString() +"' and namespace = 'dockstoretestuser2' and name = 'quayandgithubwdl' and toolname IS NULL;",
                new ScalarHandler<>());
        assertTrue("tool not unpublished", !published);
    }

    /**
     * Tests that WDL2JSON works for local file
     */
    @Test
    public void testWDL2JSON() {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("wdl.wdl"));
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "wdl2json", "--wdl",
                sourceFile.getAbsolutePath(), "--script" });
        // TODO: Test that output is the expected WDL file
    }

    @Test
    @Category(ToilCompatibleTest.class)
    public void testCWL2JSON() {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("dockstore-tool-bamstats.cwl"));
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "cwl2json", "--cwl",
                sourceFile.getAbsolutePath(), "--script" });
        // TODO: Test that output is the expected JSON file
    }

    @Test
    @Category(ToilCompatibleTest.class)
    public void testCWL2YAML() {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("dockstore-tool-bamstats.cwl"));
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "cwl2yaml", "--cwl",
                sourceFile.getAbsolutePath(), "--script" });
        // TODO: Test that output is the expected yaml file
    }

    /**
     * Check that a user can't refresh another users container
     */
    @Test
    public void testRefreshOtherUsersContainer() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "refresh", "--entry",
                "quay.io/test_org/test1", "--script" });
    }

    /**
     * Tests that a user can only add Quay containers that they own directly or through an organization
     */
    @Test
    public void testUserPrivilege() {
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Repo user has access to
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url",
                "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference", "master", "--toolname", "testTool",
                "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });
        final long count = testingPostgres.runSelectStatement(
                "select count(*) from tool where registry = '"+ Registry.QUAY_IO.toString() +"' and namespace = 'dockstoretestuser2' and name = 'quayandgithub' and toolname = 'testTool'",
                new ScalarHandler<>());
        assertEquals("the container should exist", 1, count);

        // Repo user is part of org
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.name(), "--namespace", "dockstore2", "--name", "testrepo2", "--git-url",
                "git@github.com:dockstoretestuser2/quayandgithub.git", "--git-reference", "master", "--toolname", "testOrg", "--cwl-path",
                "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from tool where registry = '"+ Registry.QUAY_IO.toString() +"' and namespace = 'dockstore2' and name = 'testrepo2' and toolname = 'testOrg'",
                        new ScalarHandler<>());
        assertEquals("the container should exist", 1, count2);

        // Repo user doesn't own
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "testrepo", "--git-url",
                "git@github.com:dockstoretestuser/quayandgithub.git", "--git-reference", "master", "--toolname", "testTool", "--cwl-path",
                "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });
    }

    /**
     * Checks that auto upgrade works and that the dockstore CLI is updated to the latest tag
     * Must be run after class since upgrading before tests may cause them to fail
     */
    @Ignore
    public static void testAutoUpgrade() {
        String installLocation = Client.getInstallLocation();
        String latestVersion = Client.getLatestVersion();

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "--upgrade", "--script" });
        String currentVersion = Client.getCurrentVersion();

        if (installLocation != null && latestVersion != null && currentVersion != null) {
            Assert.assertEquals("Dockstore CLI should now be up to date with the latest stable tag.", currentVersion, latestVersion);
        }
    }

    /**
     * Tests that WDL and CWL files can be grabbed from the command line
     */
    @Test
    public void testGetWdlAndCwl() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubwdl" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "wdl", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubwdl", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "cwl", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--script" });
    }

    /**
     * Tests that attempting to get a WDL file when none exists won't work
     */
    @Test
    public void testGetWdlFailure() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "wdl", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--script" });
    }

    /**
     * Tests that a developer can launch a CWL Tool locally, instead of getting files from Dockstore
     */
    @Test
    @Category(ToilCompatibleTest.class)
    public void testLocalLaunchCWL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "launch", "--local-entry",
                ResourceHelpers.resourceFilePath("arrays.cwl"), "--json",
                ResourceHelpers.resourceFilePath("testArrayHttpInputLocalOutput.json"), "--script" });
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

        assertTrue("should see one (or more) tags: " + c.getTags().size(), c.getTags().size() >= 1);

        UsersApi usersApi = new UsersApi(containersApi.getApiClient());
        final Long userid = usersApi.getUser().getId();
        usersApi.refresh(userid);

        CommonTestUtilities.getTestingPostgres().runUpdateStatement("update tag set imageid = 'silly old value'");
        int size = containersApi.getContainer(c.getId()).getTags().size();
        long size2 = containersApi.getContainer(c.getId()).getTags().stream().filter(tag -> tag.getImageId().equals("silly old value")).count();
        assertTrue(size == size2 && size >= 1);
        // individual refresh should update image ids
        containersApi.refresh(c.getId());
        DockstoreTool container = containersApi.getContainer(c.getId());
        size = container.getTags().size();
        size2 = container.getTags().stream().filter(tag -> tag.getImageId().equals("silly old value")).count();
        assertTrue(size2 == 0 && size >= 1);

        // so should overall refresh
        CommonTestUtilities.getTestingPostgres().runUpdateStatement("update tag set imageid = 'silly old value'");
        usersApi.refresh(userid);
        container = containersApi.getContainer(c.getId());
        size = container.getTags().size();
        size2 = container.getTags().stream().filter(tag -> tag.getImageId().equals("silly old value")).count();
        assertTrue(size2 == 0 && size >= 1);

        // so should organizational refresh
        CommonTestUtilities.getTestingPostgres().runUpdateStatement("update tag set imageid = 'silly old value'");
        usersApi.refreshToolsByOrganization(userid, container.getNamespace());
        container = containersApi.getContainer(c.getId());
        size = container.getTags().size();
        size2 = container.getTags().stream().filter(tag -> tag.getImageId().equals("silly old value")).count();
        assertTrue(size2 == 0 && size >= 1);
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

        DockstoreTool refreshedTool = toolsApi.getContainer(toolTest.getId());
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
     * This tests that attempting to launch a CWL tool locally, where no file exists, an IOError will occur
     */
    @Test
    public void testLocalLaunchCWLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "launch", "--local-entry",
                "imnotreal.cwl", "--json", "filtercount-job.json", "--script" });
    }

    /**
     * This tests that attempting to launch a WDL tool locally, where no file exists, an IOError will occur
     */
    @Test
    public void testLocalLaunchWDLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "launch", "--local-entry",
                "imnotreal.wdl", "--json", "imnotreal-job.json", "--descriptor", "wdl", "--script" });
    }

    /**
     * This tests that attempting to launch a CWL tool remotely, where no file exists, an APIError will occur
     */
    @Test
    public void testRemoteLaunchCWLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "launch", "--entry",
                "imnotreal.cwl", "--json", "imnotreal-job.json", "--script" });
    }

    /**
     * This tests that attempting to launch a WDL tool remotely, where no file exists, an APIError will occur
     */
    @Test
    public void testRemoteLaunchWDLNoFile() {
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "launch", "--entry",
                "imnotreal.wdl", "--json", "imnotreal-job.json", "--descriptor", "wdl", "--script" });
    }

    /**
     * Creates a basic Manual Tool with Quay
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

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres
                .runSelectStatement("select count(*) from tool where mode = '" + DockstoreTool.ModeEnum.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS + "' and giturl = '" + gitUrl + "' and name = 'my-md5sum' and namespace = 'dockstoretestuser2' and toolname = 'altname'", new ScalarHandler<>());
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

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres
                .runSelectStatement("select count(*) from tool where mode = '" + DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH + "' and giturl = '" + gitUrl + "' and name = 'my-md5sum' and namespace = 'dockstoretestuser2' and toolname = 'altname'", new ScalarHandler<>());
        assertEquals("The tool should be manual, there are " + count, 1, count);
    }

    /**
     * Tests that you can properly check if a user with some username exists
     */
    @Test
    public void testCheckUser() {
        // Authorized user should pass
        ApiClient client = getWebClient(USER_2_USERNAME);
        UsersApi userApi = new UsersApi(client);
        boolean userOneExists = userApi.checkUserExists("DockstoreTestUser2");
        assertTrue("User DockstoreTestUser2 should exist", userOneExists);
        boolean userTwoExists = userApi.checkUserExists(BaseIT.OTHER_USERNAME);
        assertTrue("User OtherUser should exist", userTwoExists);
        boolean fakeUserExists = userApi.checkUserExists("NotARealUser");
        assertTrue("User NotARealUser should not exist", !fakeUserExists);

        // Unauthorized user should fail
        ApiClient unauthClient = getWebClient(false, "");
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
     * This tests that zip file can be downloaded or not based on published state and auth.
     */
    @Test
    public void downloadZipFileTestAuth() throws IOException {
        final ApiClient ownerWebClient = getWebClient(USER_2_USERNAME);
        ContainersApi ownerContainersApi = new ContainersApi(ownerWebClient);

        final ApiClient anonWebClient = getWebClient(false, null);
        ContainersApi anonContainersApi = new ContainersApi(anonWebClient);

        final ApiClient otherUserWebClient = getWebClient(true, OTHER_USERNAME);
        ContainersApi otherUserContainersApi = new ContainersApi(otherUserWebClient);

        // Register and refresh tool
        DockstoreTool tool = ownerContainersApi.registerManual(getContainer());
        DockstoreTool refresh = ownerContainersApi.refresh(tool.getId());
        Long toolId = refresh.getId();
        Tag tag = refresh.getTags().get(0);
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
            assertTrue("User does not have access to tool.", !success);
        }
        // Other user: Should fail
        success = true;
        try {
            otherUserContainersApi.getToolZip(toolId, versionId);
        } catch (ApiException ex) {
            success = false;
        } finally {
            assertTrue("User does not have access to tool.", !success);
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

        // test that these zips can be downloaded via CLI

        // download zip via CLI
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "download", "--entry", refresh.getToolPath() + ":" + tag.getName(), "--zip", "--script" });
        File downloadedZip = new File(new ToolClient(null, false).zipFilename(refresh));
        // record entries
        List<String> collect = new ZipFile(downloadedZip).stream().map(ZipEntry::getName).collect(Collectors.toList());
        assert(downloadedZip.exists());
        assert(downloadedZip.delete());
        

        // download and unzip via CLI while at it
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "download", "--entry", refresh.getToolPath() + ":" + tag.getName(), "--script" });
        collect.forEach(entry -> {
            File innerFile = new File(System.getProperty("user.dir"), entry);
            assert (innerFile.exists());
            assert (innerFile.delete());
        });
    }
}
