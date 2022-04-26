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

import static io.dockstore.client.cli.GeneralWorkflowRegressionIT.KNOWN_BREAKAGE_MOVING_TO_1_6_0;
import static io.dockstore.common.CommonTestUtilities.OLD_DOCKSTORE_VERSION;
import static io.dockstore.common.CommonTestUtilities.runOldDockstoreClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.Registry;
import io.dockstore.common.RegressionTest;
import io.dockstore.common.TestUtility;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.model.DockstoreTool;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/**
 * Extra confidential integration tests with old dockstore client, don't rely on the type of repository used (Github, Dockerhub, Quay.io, Bitbucket)
 * Tests a variety of different CLI commands that start with 'dockstore tool'
 * Uses an older (CommonTestUtilities.OLD_DOCKSTORE_VERSION) dockstore client
 * Testing Dockstore CLI 1.3.6 at the time of creation
 *
 * @author gluu
 * @since 1.4.0
 */
@Category({ RegressionTest.class })
public class GeneralRegressionIT extends BaseIT {
    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();
    static URL url;
    static File dockstore;
    private static final String DOCKERHUB_TOOL_PATH = "registry.hub.docker.com/testPath/testUpdatePath/test5";
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @BeforeClass
    public static void getOldDockstoreClient() throws IOException {
        TestUtility.createFakeDockstoreConfigFile();
        url = new URL("https://github.com/dockstore/dockstore-cli/releases/download/" + OLD_DOCKSTORE_VERSION + "/dockstore");
        dockstore = temporaryFolder.newFile("dockstore");
        FileUtils.copyURLToFile(url, dockstore);
        assertTrue(dockstore.setExecutable(true));
    }

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.addAdditionalToolsWithPrivate2(SUPPORT, false);
    }

    /**
     * this method will set up the webservice and return the container api
     *
     * @return ContainersApi
     * @throws ApiException comes back from a web service error
     */
    private ContainersApi setupWebService() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        return new ContainersApi(client);
    }

    /**
     * this method will set up the databse and select data needed
     *
     * @return cwl/wdl/dockerfile path of the tool's tag in the database
     * @throws ApiException comes back from a web service error
     */
    private String getPathfromDB(String type) {
        // Set up DB

        // Select data from DB
        final Long toolID = testingPostgres.runSelectStatement("select id from tool where name = 'testUpdatePath'", long.class);
        final Long tagID = testingPostgres.runSelectStatement("select parentid from tag where parentid = " + toolID, long.class);

        return testingPostgres.runSelectStatement("select " + type + " from tag where id = " + tagID, String.class);
    }

    /**
     * Tests adding/editing/deleting container related labels (for search)
     */
    @Test
    public void testAddEditRemoveLabelOldClient() {
        // Test adding/removing labels for different containers
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--add", "quay", "--add", "github", "--remove", "dockerhub", "--script" });
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--add", "github", "--add", "dockerhub", "--remove", "quay", "--script" });

        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubalternate", "--add", "alternate", "--add", "github", "--script" });
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubalternate", "--remove", "github", "--script" });

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
    @Ignore(KNOWN_BREAKAGE_MOVING_TO_1_6_0)
    public void testVersionTagWDLCWLAndDockerfilePathsAlterationOldClient() {
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--cwl-path", "/testDir/Dockstore.cwl", "--wdl-path",
                "/testDir/Dockstore.wdl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tag, tool where tool.registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithub' and tool.toolname IS NULL and tool.id=tag.parentid and valid = 'f'",
            long.class);
        assertEquals("there should now be an invalid tag, found " + count, 1, count);

        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--cwl-path", "/Dockstore.cwl", "--wdl-path",
                "/Dockstore.wdl", "--dockerfile-path", "/Dockerfile", "--script" });

        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "refresh", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--script" });

        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tag, tool where tool.registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithub' and tool.toolname = '' and tool.id=tag.parentid and valid = 'f'",
            long.class);
        assertEquals("the invalid tag should now be valid, found " + count2, 0, count2);
    }

    /**
     * Tests adding tag tags to a manually registered container
     */
    @Test
    public void testAddVersionTagManualContainerOldClient() {
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry",
                Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url",
                "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference", "master", "--toolname", "alternate",
                "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub/alternate", "--name", "masterTest", "--image-id",
                "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

        final long count = testingPostgres.runSelectStatement(
            " select count(*) from  tag, tool where tag.parentid = tool.id and giturl ='git@github.com:dockstoretestuser2/quayandgithubalternate.git' and toolname = 'alternate'",
            long.class);
        assertEquals(
            "there should be 3 tags, 2  that are autogenerated (master and latest) and the newly added masterTest tag, found " + count, 3,
            count);

    }

    /**
     * Tests hiding and unhiding different versions of a container (quick registered)
     */
    @Test
    @Ignore(KNOWN_BREAKAGE_MOVING_TO_1_6_0)
    public void testVersionTagHideOld() {
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--hidden", "true", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tag where hidden = 't'", long.class);
        assertEquals("there should be 1 hidden tag", 1, count);

        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub", "--name", "master", "--hidden", "false", "--script" });

        final long count2 = testingPostgres.runSelectStatement("select count(*) from tag where hidden = 't'", long.class);
        assertEquals("there should be 0 hidden tag", 0, count2);
    }

    /**
     * Test update tag with only WDL to invalid then valid
     */
    @Test
    @Ignore(KNOWN_BREAKAGE_MOVING_TO_1_6_0)
    public void testVersionTagWDLOldClient() {
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubwdl", "--name", "master", "--wdl-path", "/randomDir/Dockstore.wdl", "--script" });
        // should now be invalid

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tag, tool where tool.registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithubwdl' and tool.toolname IS NULL and tool.id=tag.parentid and valid = 'f'",
            long.class);

        assertEquals("there should now be 1 invalid tag, found " + count, 1, count);

        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubwdl", "--name", "master", "--wdl-path", "/Dockstore.wdl", "--script" });
        // should now be valid
        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tag, tool where tool.registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and tool.namespace = 'dockstoretestuser2' and tool.name = 'quayandgithubwdl' and tool.toolname IS NULL and tool.id=tag.parentid and valid = 'f'",
            long.class);
        assertEquals("the tag should now be valid", 0, count2);

    }

    /**
     * Will test deleting a tag tag from a manually registered container
     */
    @Test
    public void testVersionTagDeleteOldClient() {
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry",
                Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url",
                "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference", "master", "--toolname", "alternate",
                "--cwl-path", "/testDir/Dockstore.cwl", "--wdl-path", "/testDir/Dockstore.wdl", "--dockerfile-path", "/testDir/Dockerfile",
                "--script" });

        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub/alternate", "--name", "masterTest", "--image-id",
                "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "remove", "--entry",
                "quay.io/dockstoretestuser2/quayandgithub/alternate", "--name", "masterTest", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", long.class);
        assertEquals("there should be no tags with the name masterTest", 0, count);
    }

    /**
     * Tests that tool2JSON works for entries on Dockstore
     */
    @Test
    public void testTool2JsonWdlOldClient() {
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
    public void registerUnregisterAndCopyOldClient() {
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubwdl" });

        boolean published = testingPostgres.runSelectStatement(
            "select ispublished from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithubwdl';", boolean.class);
        assertTrue("tool not published", published);

        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubwdl", "--entryname", "foo" });

        long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
            + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithubwdl';", long.class);
        assertEquals("should be two after republishing", 2, count);
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--unpub", "--entry",
                "quay.io/dockstoretestuser2/quayandgithubwdl" });

        published = testingPostgres.runSelectStatement("select ispublished from tool where registry = '" + Registry.QUAY_IO.getDockerPath()
            + "' and namespace = 'dockstoretestuser2' and name = 'quayandgithubwdl' and toolname IS NULL;", boolean.class);
        assertTrue("tool not unpublished", !published);
    }

    /**
     * Tests that WDL2JSON works for local file
     */
    @Test
    public void testWDL2JSONOld() {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("wdl.wdl"));
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "wdl2json", "--wdl",
                sourceFile.getAbsolutePath(), "--script" });
        // TODO: Test that output is the expected WDL file
    }

    @Test
    public void testCWL2JSONOld() {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("dockstore-tool-bamstats.cwl"));
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "cwl2json", "--cwl",
                sourceFile.getAbsolutePath(), "--script" });
        // TODO: Test that output is the expected JSON file
    }

    @Test
    public void testCWL2YAMLOld() {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("dockstore-tool-bamstats.cwl"));
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "cwl2yaml", "--cwl",
                sourceFile.getAbsolutePath(), "--script" });
        // TODO: Test that output is the expected yaml file
    }

    /**
     * Tests that WDL and CWL files can be grabbed from the command line
     */
    @Test
    @Ignore(KNOWN_BREAKAGE_MOVING_TO_1_6_0)
    public void testGetWdlAndCwlOld() {
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
    public void testLocalLaunchCWLOld() {
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
        DockstoreTool toolTest = toolsApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);
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
     * Test to update the default path of WDL and it should change the tag's WDL path in the database
     *
     * @throws ApiException
     */
    @Test
    public void testUpdateToolPathWDL() throws ApiException {
        //setup webservice and get tool api
        ContainersApi toolsApi = setupWebService();

        //register tool
        DockstoreTool toolTest = toolsApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);

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
        DockstoreTool toolTest = toolsApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);

        //change the default dockerfile and refresh
        toolTest.setDefaultDockerfilePath("/test1/Dockerfile");
        toolsApi.updateTagContainerPath(toolTest.getId(), toolTest);
        toolsApi.refresh(toolTest.getId());

        //check if the tag's dockerfile path have the same dockerfile path or not in the database
        final String path = getPathfromDB("dockerfilepath");
        assertEquals("the cwl path should be changed to /test1/Dockerfile", "/test1/Dockerfile", path);
    }
}
