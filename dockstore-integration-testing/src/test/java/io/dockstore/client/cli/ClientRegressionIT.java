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

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.CommonTestUtilities.TestingPostgres;
import io.dockstore.common.Registry;
import io.dockstore.common.RegressionTest;
import io.dockstore.common.TestUtility;
import io.dockstore.common.ToilCompatibleTest;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiException;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Assert;
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

import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;
import static io.dockstore.common.CommonTestUtilities.runOldDockstoreClient;

/**
 * @author dyuen
 */
@Category({ RegressionTest.class })
public class ClientRegressionIT extends BaseIT {
    static URL url;
    final static String version = "1.3.1";
    static File dockstore;
    static File testJson;
    final static String firstTool = ResourceHelpers.resourceFilePath("dockstore-tool-helloworld.cwl");
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT);
        Client.DEBUG.set(false);
    }

    @BeforeClass
    public static void getOldDockstoreClient() throws IOException {
        url = new URL("https://github.com/ga4gh/dockstore/releases/download/" + version +"/dockstore");
        dockstore = temporaryFolder.newFile("dockstore");
        FileUtils.copyURLToFile(url, dockstore);
        dockstore.setExecutable(true);
        String[] commandArray = new String[] { "--version" };
        url = new URL("https://raw.githubusercontent.com/DockstoreTestUser/dockstore_parameter_test/master/test.cwl.json");
        testJson = temporaryFolder.newFile("test.cwl.json");
        FileUtils.copyURLToFile(url, testJson);
//        This has problem executing for some reason
//        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(commandArray);
//        Assert.assertTrue(stringStringImmutablePair.getLeft().contains(version));
    }

    @Test
    public void testListEntriesOld() throws IOException, ApiException {
        String[] commandArray = new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "list" };
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
    }

    @Test
    public void testDebugModeListEntriesOld() throws IOException, ApiException {
        String[] commandArray = new String[] { "--debug", "--config", TestUtility.getConfigFileLocation(true), "tool", "list" };
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
    }

    @Test
    public void testPluginEnableOldClient() throws ExecuteException {
        String[] commandArray1 = new String[] { "--config", ResourceHelpers.resourceFilePath("pluginsTest1/configWithPlugins"), "plugin", "download" };
        ImmutablePair<String, String> stringStringImmutablePair1 = runOldDockstoreClient(dockstore, commandArray1);
        String[] commandArray2 = new String[] { "--config", ResourceHelpers.resourceFilePath("pluginsTest1/configWithPlugins"), "plugin", "list" };
        ImmutablePair<String, String> stringStringImmutablePair2 = runOldDockstoreClient(dockstore, commandArray2);
        String stdout = stringStringImmutablePair2.getLeft();
        Assert.assertTrue(stdout.contains("dockstore-file-synapse-plugin"));
        Assert.assertTrue(stdout.contains("dockstore-file-s3-plugin"));
        Assert.assertFalse(stdout.contains("dockstore-icgc-storage-client-plugin"));
    }

    @Test
    public void testPluginDisableOldClient() throws ExecuteException {
        String[] commandArray = new String[] { "--config", ResourceHelpers.resourceFilePath("pluginsTest2/configWithPlugins"), "plugin", "download" };
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
        commandArray = new String[] { "--config", ResourceHelpers.resourceFilePath("pluginsTest2/configWithPlugins"), "plugin", "list" };
        stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
        String stdout = stringStringImmutablePair.getLeft();
        Assert.assertFalse(stdout.contains("dockstore-file-synapse-plugin"));
        Assert.assertFalse(stdout.contains("dockstore-file-s3-plugin"));
        Assert.assertTrue(stdout.contains("dockstore-file-icgc-storage-client-plugin"));
    }

    @Test
    @Category(ToilCompatibleTest.class)
    public void launchingCWLWorkflowOld() throws IOException {
        final String firstWorkflowCWL = ResourceHelpers.resourceFilePath("1st-workflow.cwl");
        final String firstWorkflowJSON = ResourceHelpers.resourceFilePath("1st-workflow-job.json");
        String[] commandArray = new String[] { "--config", TestUtility.getConfigFileLocation(true), "workflow", "launch", "--local-entry", firstWorkflowCWL,
                        "--json", firstWorkflowJSON };
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
    }

    @Test
    public void testMetadataMethodsOld() throws IOException {
        String commandArray[] = new String[] { "--config", TestUtility.getConfigFileLocation(true), "--version" };
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
        Assert.assertTrue(stringStringImmutablePair.getLeft().contains("Dockstore version"));
        commandArray = new String[] { "--config", TestUtility.getConfigFileLocation(true), "--server-metadata" };
        stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
        Assert.assertTrue(stringStringImmutablePair.getLeft().contains("version"));
        systemOutRule.clearLog();
    }

    @Test
    public void testCacheCleaningOld() throws IOException {
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, new String[] {"--config", TestUtility.getConfigFileLocation(true), "--clean-cache" });
        systemOutRule.clearLog();
    }

    @Test
    public void pluginDownloadOld() throws IOException {
        String[] commandArray = new String[] { "--config", TestUtility.getConfigFileLocation(true), "plugin", "download" };
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
        systemOutRule.clearLog();
    }

    @Test
    public void touchOnAllHelpMessages() throws IOException {

        checkCommandForHelp(new String[] { "tool", "search" });
        checkCommandForHelp(new String[] { "tool", "info" });
        checkCommandForHelp(new String[] { "tool", "cwl" });
        checkCommandForHelp(new String[] { "tool", "wdl" });
        checkCommandForHelp(new String[] { "tool", "label" });
        checkCommandForHelp(new String[] { "tool", "test_parameter" });
        checkCommandForHelp(new String[] { "tool", "convert" });
        checkCommandForHelp(new String[] { "tool", "launch" });
        checkCommandForHelp(new String[] { "tool", "version_tag" });
        checkCommandForHelp(new String[] { "tool", "update_tool" });

        checkCommandForHelp(new String[] { "tool", "convert", "entry2json" });
        checkCommandForHelp(new String[] { "tool", "convert", "entry2tsv" });
        checkCommandForHelp(new String[] { "tool", "convert", "cwl2yaml" });
        checkCommandForHelp(new String[] { "tool", "convert", "cwl2json" });
        checkCommandForHelp(new String[] { "tool", "convert", "wdl2json" });

        checkCommandForHelp(new String[] {});
        checkCommandForHelp(new String[] { "tool" });
        checkCommandForHelp(new String[] { "tool", "list", "--help" });
        checkCommandForHelp(new String[] { "tool", "search", "--help" });
        checkCommandForHelp(new String[] { "tool", "publish", "--help" });
        checkCommandForHelp(new String[] { "tool", "info", "--help" });
        checkCommandForHelp(new String[] { "tool", "cwl", "--help" });
        checkCommandForHelp(new String[] { "tool", "wdl", "--help" });
        checkCommandForHelp(new String[] { "tool", "refresh", "--help" });
        checkCommandForHelp(new String[] { "tool", "label", "--help" });
        checkCommandForHelp(new String[] { "tool", "convert", "--help" });
        checkCommandForHelp(new String[] { "tool", "convert", "cwl2json", "--help" });
        checkCommandForHelp(new String[] { "tool", "convert", "cwl2yaml", "--help" });
        checkCommandForHelp(new String[] { "tool", "convert", "wdl2json", "--help" });
        checkCommandForHelp(new String[] { "tool", "convert", "entry2json", "--help" });
        checkCommandForHelp(new String[] { "tool", "convert", "entry2tsv", "--help" });
        checkCommandForHelp(new String[] { "tool", "launch", "--help" });
        checkCommandForHelp(new String[] { "tool", "version_tag", "--help" });
        checkCommandForHelp(new String[] { "tool", "version_tag", "remove", "--help" });
        checkCommandForHelp(new String[] { "tool", "version_tag", "update", "--help" });
        checkCommandForHelp(new String[] { "tool", "version_tag", "add", "--help" });
        checkCommandForHelp(new String[] { "tool", "update_tool", "--help" });
        checkCommandForHelp(new String[] { "tool", "manual_publish", "--help" });
        checkCommandForHelp(new String[] { "tool", "star", "--help" });
        checkCommandForHelp(new String[] { "tool", "test_parameter", "--help" });
        checkCommandForHelp(new String[] { "tool", "verify", "--help" });
        checkCommandForHelp(new String[] { "tool" });

        checkCommandForHelp(new String[] { "workflow", "convert", "entry2json" });
        checkCommandForHelp(new String[] { "workflow", "convert", "entry2tsv" });
        checkCommandForHelp(new String[] { "workflow", "convert", "cwl2yaml" });
        checkCommandForHelp(new String[] { "workflow", "convert", "cwl2json" });
        checkCommandForHelp(new String[] { "workflow", "convert", "wdl2json" });

        checkCommandForHelp(new String[] { "workflow", "search" });
        checkCommandForHelp(new String[] { "workflow", "info" });
        checkCommandForHelp(new String[] { "workflow", "cwl" });
        checkCommandForHelp(new String[] { "workflow", "wdl" });
        checkCommandForHelp(new String[] { "workflow", "label" });
        checkCommandForHelp(new String[] { "workflow", "test_parameter" });
        checkCommandForHelp(new String[] { "workflow", "convert" });
        checkCommandForHelp(new String[] { "workflow", "launch" });
        checkCommandForHelp(new String[] { "workflow", "version_tag" });
        checkCommandForHelp(new String[] { "workflow", "update_workflow" });
        checkCommandForHelp(new String[] { "workflow", "restub" });

        checkCommandForHelp(new String[] { "workflow", "list", "--help" });
        checkCommandForHelp(new String[] { "workflow", "search", "--help" });
        checkCommandForHelp(new String[] { "workflow", "publish", "--help" });
        checkCommandForHelp(new String[] { "workflow", "info", "--help" });
        checkCommandForHelp(new String[] { "workflow", "cwl", "--help" });
        checkCommandForHelp(new String[] { "workflow", "wdl", "--help" });
        checkCommandForHelp(new String[] { "workflow", "refresh", "--help" });
        checkCommandForHelp(new String[] { "workflow", "label", "--help" });
        checkCommandForHelp(new String[] { "workflow", "convert", "--help" });
        checkCommandForHelp(new String[] { "workflow", "convert", "cwl2json", "--help" });
        checkCommandForHelp(new String[] { "workflow", "convert", "cwl2yaml", "--help" });
        checkCommandForHelp(new String[] { "workflow", "convert", "wd2json", "--help" });
        checkCommandForHelp(new String[] { "workflow", "convert", "entry2json", "--help" });
        checkCommandForHelp(new String[] { "workflow", "convert", "entry2tsv", "--help" });
        checkCommandForHelp(new String[] { "workflow", "launch", "--help" });
        checkCommandForHelp(new String[] { "workflow", "version_tag", "--help" });
        checkCommandForHelp(new String[] { "workflow", "update_workflow", "--help" });
        checkCommandForHelp(new String[] { "workflow", "manual_publish", "--help" });
        checkCommandForHelp(new String[] { "workflow", "restub", "--help" });
        checkCommandForHelp(new String[] { "workflow", "star", "--help" });
        checkCommandForHelp(new String[] { "workflow", "test_parameter", "--help" });
        checkCommandForHelp(new String[] { "workflow", "verify", "--help" });
        checkCommandForHelp(new String[] { "workflow" });

        checkCommandForHelp(new String[] { "plugin", "list", "--help" });
        checkCommandForHelp(new String[] { "plugin", "download", "--help" });
        checkCommandForHelp(new String[] { "plugin" });
    }

    private void checkCommandForHelp(String[] argv) throws IOException {
        final ArrayList<String> strings = Lists.newArrayList(argv);
        strings.add("--config");
        strings.add(TestUtility.getConfigFileLocation(true));
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, strings.toArray(new String[strings.size()]));
        Assert.assertTrue(stringStringImmutablePair.getLeft().contains("Usage: dockstore"));
    }

    /**
     * Tests that the unpublished tool can be published, refreshed, then launched once the json and input file is attained
     * @throws ExecuteException
     */
    @Test
    public void testActualToolLaunch() throws ExecuteException {
        // manual publish the workflow
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish",
                        "--entry", "quay.io/dockstoretestuser/test_input_json", "--script"});
        // launch the workflow
        runOldDockstoreClient(dockstore,
                new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh",
                        "--entry", "quay.io/dockstoretestuser/test_input_json", "--script"});
        String[] commandArray = { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "launch", "--entry",
                "quay.io/dockstoretestuser/test_input_json", "--json", testJson.getAbsolutePath(), "--script"};
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
        Assert.assertTrue("Final process status was not a success", (stringStringImmutablePair.getLeft().contains("Final process status is success")));
        Assert.assertTrue("Final process status was not a success", (stringStringImmutablePair.getRight().contains("Final process status is success")));

    }

}
