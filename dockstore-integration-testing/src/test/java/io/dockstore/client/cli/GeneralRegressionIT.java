/*
 *    Copyright 2017 OICR
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
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.Registry;
import io.dockstore.common.RegressionTest;
import io.dockstore.common.ToilCompatibleTest;
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
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;
import static io.dockstore.common.CommonTestUtilities.runOldDockstoreClient;
import static io.dockstore.common.CommonTestUtilities.version;
import static org.junit.Assert.assertTrue;

/**
 * Extra confidential integration tests, don't rely on the type of repository used (Github, Dockerhub, Quay.io, Bitbucket)
 *
 * @author aduncan
 */
@Category({ RegressionTest.class })
public class GeneralRegressionIT extends BaseIT {
    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };
    static URL url;
    static File dockstore;

    @BeforeClass
    public static void getOldDockstoreClient() throws IOException {
        url = new URL("https://github.com/ga4gh/dockstore/releases/download/" + version + "/dockstore");
        dockstore = temporaryFolder.newFile("dockstore");
        FileUtils.copyURLToFile(url, dockstore);
        dockstore.setExecutable(true);
        String[] commandArray = new String[] { "--version" };
        //        This has problem executing for some reason
        //        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(commandArray);
        //        Assert.assertTrue(stringStringImmutablePair.getLeft().contains(version));
    }

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    /**
     * This method will create and register a new container for testing
     *
     * @return DockstoreTool
     * @throws IOException
     * @throws TimeoutException
     * @throws ApiException
     */
    private DockstoreTool getContainer() {
        DockstoreTool c = new DockstoreTool();
        c.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        c.setName("testUpdatePath");
        c.setGitUrl("https://github.com/DockstoreTestUser2/dockstore-tool-imports");
        c.setDefaultDockerfilePath("/Dockerfile");
        c.setDefaultCwlPath("/Dockstore.cwl");
        c.setRegistryString(Registry.DOCKER_HUB.toString());
        c.setIsPublished(false);
        c.setNamespace("testPath");
        c.setToolname("test5");
        c.setPath("quay.io/dockstoretestuser2/dockstore-tool-imports");
        Tag tag = new Tag();
        tag.setName("master");
        tag.setReference("refs/heads/master");
        tag.setValid(true);
        tag.setImageId("123456");
        // construct source files
        SourceFile fileCWL = new SourceFile();
        fileCWL.setContent("cwlstuff");
        fileCWL.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        fileCWL.setPath("/Dockstore.cwl");
        List<SourceFile> list = new ArrayList<>();
        list.add(fileCWL);
        tag.setSourceFiles(list);
        SourceFile fileDockerFile = new SourceFile();
        fileDockerFile.setContent("dockerstuff");
        fileDockerFile.setType(SourceFile.TypeEnum.DOCKERFILE);
        fileDockerFile.setPath("/Dockerfile");
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
        // Set up webservice
        ApiClient client = getWebClient();

        //Set up user api and get the container api
        UsersApi usersApi = new UsersApi(client);
        final Long userId = usersApi.getUser().getId();
        usersApi.refresh(userId);
        ContainersApi toolsApi = new ContainersApi(client);

        // Make publish request (true)
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);

        return toolsApi;
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
     * Tests adding/editing/deleting container related labels (for search)
     */
    @Test
    public void testAddEditRemoveLabelOldClient() throws ExecuteException {
        // Test adding/removing labels for different containers
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--add", "quay", "--add", "github", "--remove", "dockerhub",
                        "--script" });
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--add", "github", "--add", "dockerhub", "--remove", "quay",
                        "--script" });

        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubalternate", "--add", "alternate", "--add", "github", "--script" });
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubalternate", "--remove", "github", "--script" });

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres
                .runSelectStatement("select count(*) from entry_label where entryid = '2'", new ScalarHandler<>());
        assertTrue("there should be 2 labels for the given container, there are " + count, count == 2);

        final long count2 = testingPostgres.runSelectStatement(
                "select count(*) from label where value = 'quay' or value = 'github' or value = 'dockerhub' or value = 'alternate'",
                new ScalarHandler<>());
        assertTrue("there should be 4 labels in the database (No Duplicates), there are " + count2, count2 == 4);
    }

    /**
     * Tests altering the cwl and dockerfile paths to invalid locations (quick registered)
     */
    @Test
    public void testVersionTagWDLCWLAndDockerfilePathsAlterationOldClient() throws ExecuteException {
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--cwl-path", "/testDir/Dockstore.cwl",
                        "--wdl-path", "/testDir/Dockstore.wdl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement(
                "select count(*) from tag,tool_tag,tool where tool.registry = '" + Registry.QUAY_IO.toString()
                        + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithub' and tool.toolname = '' and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'",
                new ScalarHandler<>());
        assertTrue("there should now be an invalid tag, found " + count, count == 1);

        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--cwl-path", "/Dockstore.cwl", "--wdl-path",
                        "/Dockstore.wdl", "--dockerfile-path", "/Dockerfile", "--script" });

        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "refresh", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--script" });

        final long count2 = testingPostgres.runSelectStatement(
                "select count(*) from tag,tool_tag,tool where tool.registry = '" + Registry.QUAY_IO.toString()
                        + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithub' and tool.toolname = '' and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'",
                new ScalarHandler<>());
        assertTrue("the invalid tag should now be valid, found " + count2, count2 == 0);
    }

    /**
     * Tests adding tag tags to a manually registered container
     */
    @Test
    public void testAddVersionTagManualContainerOldClient() throws ExecuteException {
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry",
                        Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url",
                        "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference", "master", "--toolname",
                        "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub/alternate", "--name", "masterTest", "--image-id",
                        "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement(
                " select count(*) from  tool_tag, tool where tool_tag.toolid = tool.id and giturl ='git@github.com:dockstoretestuser2/quayandgithubalternate.git' and toolname = 'alternate'",
                new ScalarHandler<>());
        assertTrue(
                "there should be 3 tags, 2  that are autogenerated (master and latest) and the newly added masterTest tag, found " + count,
                count == 3);

    }

    /**
     * Tests hiding and unhiding different versions of a container (quick registered)
     */
    @Test
    public void testVersionTagHideOld() throws ExecuteException {
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--hidden", "true", "--script" });

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from tag where hidden = 't'", new ScalarHandler<>());
        assertTrue("there should be 1 hidden tag", count == 1);

        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--hidden", "false", "--script" });

        final long count2 = testingPostgres.runSelectStatement("select count(*) from tag where hidden = 't'", new ScalarHandler<>());
        assertTrue("there should be 0 hidden tag", count2 == 0);
    }

    /**
     * Test update tag with only WDL to invalid then valid
     */
    @Test
    public void testVersionTagWDLOldClient() throws ExecuteException {
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl", "--name", "master", "--wdl-path", "/randomDir/Dockstore.wdl",
                        "--script" });
        // should now be invalid
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement(
                "select count(*) from tag,tool_tag,tool where tool.registry = '" + Registry.QUAY_IO.toString()
                        + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithubwdl' and tool.toolname = '' and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'",
                new ScalarHandler<>());

        assertTrue("there should now be 1 invalid tag, found " + count, count == 1);

        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl", "--name", "master", "--wdl-path", "/Dockstore.wdl", "--script" });
        // should now be valid
        final long count2 = testingPostgres.runSelectStatement(
                "select count(*) from tag,tool_tag,tool where tool.registry = '" + Registry.QUAY_IO.toString()
                        + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithubwdl' and tool.toolname = '' and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'",
                new ScalarHandler<>());
        assertTrue("the tag should now be valid", count2 == 0);

    }

    /**
     * Will test deleting a tag tag from a manually registered container
     */
    @Test
    public void testVersionTagDeleteOldClient() throws ExecuteException {
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry",
                        Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url",
                        "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference", "master", "--toolname",
                        "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--wdl-path", "/testDir/Dockstore.wdl", "--dockerfile-path",
                        "/testDir/Dockerfile", "--script" });

        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub/alternate", "--name", "masterTest", "--image-id",
                        "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "remove", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub/alternate", "--name", "masterTest", "--script" });

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", new ScalarHandler<>());
        assertTrue("there should be no tags with the name masterTest", count == 0);
    }

    /**
     * Tests that tool2JSON works for entries on Dockstore
     */
    @Test
    public void testTool2JSONWDLOldClient() throws ExecuteException {
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl" });
        // need to publish before converting
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "entry2json", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl", "--descriptor", "wdl", "--script" });
        // TODO: Test that output is the expected WDL file
    }

    @Test
    public void registerUnregisterAndCopyOldClient() throws ExecuteException {
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl" });
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        boolean published = testingPostgres.runSelectStatement(
                "select ispublished from tool where registry = '" + Registry.QUAY_IO.toString()
                        + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithubwdl';", new ScalarHandler<>());
        assertTrue("tool not published", published);

        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl", "--entryname", "foo" });

        long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.toString()
                + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithubwdl';", new ScalarHandler<>());
        assertTrue("should be two after republishing", count == 2);

        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--unpub", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl" });

        published = testingPostgres.runSelectStatement("select ispublished from tool where registry = '" + Registry.QUAY_IO.toString()
                + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithubwdl' and toolname = '';", new ScalarHandler<>());
        assertTrue("tool not unpublished", !published);
    }

    /**
     * Tests that WDL2JSON works for local file
     */
    @Test
    public void testWDL2JSONOld() throws ExecuteException {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("wdl.wdl"));
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "wdl2json", "--wdl",
                        sourceFile.getAbsolutePath(), "--script" });
        // TODO: Test that output is the expected WDL file
    }

    @Test
    @Category(ToilCompatibleTest.class)
    public void testCWL2JSONOld() throws ExecuteException {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("dockstore-tool-bamstats.cwl"));
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "cwl2json", "--cwl",
                        sourceFile.getAbsolutePath(), "--script" });
        // TODO: Test that output is the expected JSON file
    }

    @Test
    @Category(ToilCompatibleTest.class)
    public void testCWL2YAMLOld() throws ExecuteException {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("dockstore-tool-bamstats.cwl"));
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "cwl2yaml", "--cwl",
                        sourceFile.getAbsolutePath(), "--script" });
        // TODO: Test that output is the expected yaml file
    }

    /**
     * Change toolname of a container
     */
    @Test
    public void testChangeToolnameOld() throws ExecuteException {
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry",
                        Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser2", "--name", "quayandgithubalternate", "--git-url",
                        "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference", "master", "--toolname",
                        "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubalternate/alternate", "--toolname", "alternate", "--script" });

        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.toString()
                        + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithubalternate' and toolname = 'alternate'",
                new ScalarHandler<>());
        assertTrue("there should only be one instance of the container with the toolname set to alternate", count == 1);

        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubalternate", "--toolname", "toolnameTest", "--script" });

        final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.toString()
                        + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithubalternate' and toolname = 'toolnameTest'",
                new ScalarHandler<>());
        assertTrue("there should only be one instance of the container with the toolname set to toolnameTest", count2 == 1);

    }

    /**
     * Tests that WDL and CWL files can be grabbed from the command line
     */
    @Test
    public void testGetWdlAndCwlOld() throws ExecuteException {
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl" });
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "wdl", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithubwdl", "--script" });
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub" });
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "cwl", "--entry",
                        "quay.io/dockstoretestuser2/quayandgithub", "--script" });
    }

    /**
     * Tests that a developer can launch a CWL Tool locally, instead of getting files from Dockstore
     */
    @Test
    @Category(ToilCompatibleTest.class)
    public void testLocalLaunchCWLOld() throws ExecuteException {
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "launch", "--local-entry",
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
        assertTrue("the cwl path should be changed to /test1.cwl", path.equals("/test1.cwl"));
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
        assertTrue("the cwl path should be changed to /test1.wdl", path.equals("/test1.wdl"));
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
        assertTrue("the cwl path should be changed to /test1/Dockerfile", path.equals("/test1/Dockerfile"));
    }
}
