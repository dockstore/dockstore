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

import static io.dockstore.common.CommonTestUtilities.OLD_DOCKSTORE_VERSION;
import static io.dockstore.common.CommonTestUtilities.checkToolList;
import static io.dockstore.common.CommonTestUtilities.runOldDockstoreClient;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.RegressionTest;
import io.dockstore.common.TestUtility;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * Tests a variety of basic dockstore CLI commands along with some tool commands
 * using an older (CommonTestUtilities.OLD_DOCKSTORE_VERSION) dockstore client
 * Testing Dockstore CLI 1.3.6 at the time of creation
 *
 * @author gluu
 * @since 1.4.0
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(RegressionTest.NAME)
class ClientRegressionIT extends BaseIT {
    @TempDir
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();
    private static File dockstore;
    private static File testJson;

    private static final Logger LOG = LoggerFactory.getLogger(ClientRegressionIT.class);

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    @BeforeAll
    public static void getOldDockstoreClient() throws IOException {
        TestUtility.createFakeDockstoreConfigFile();
        URL url = new URL("https://github.com/dockstore/dockstore-cli/releases/download/" + OLD_DOCKSTORE_VERSION + "/dockstore");
        dockstore = temporaryFolder.newFile("dockstore");
        FileUtils.copyURLToFile(url, dockstore);
        assertTrue(dockstore.setExecutable(true));
        url = new URL("https://raw.githubusercontent.com/DockstoreTestUser/dockstore_parameter_test/master/test.cwl.json");
        testJson = temporaryFolder.newFile("test.cwl.json");
        FileUtils.copyURLToFile(url, testJson);
    }

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
    }

    @Test
    void testListEntriesOld() throws IOException, ApiException {
        String[] commandArray = new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "list", "--script" };
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
        checkToolList(stringStringImmutablePair.getLeft());
    }

    @Test
    void testDebugModeListEntriesOld() throws IOException, ApiException {
        String[] commandArray = new String[] { "--debug", "--config", TestUtility.getConfigFileLocation(true), "tool", "list", "--script" };
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
        checkToolList(stringStringImmutablePair.getLeft());
    }

    @Test
    void testPluginEnableOldClient() {
        String[] commandArray1 = new String[] { "--config", ResourceHelpers.resourceFilePath("pluginsTest1/configWithPlugins"), "plugin",
            "download" };
        runOldDockstoreClient(dockstore, commandArray1);
        String[] commandArray2 = new String[] { "--config", ResourceHelpers.resourceFilePath("pluginsTest1/configWithPlugins"), "plugin",
            "list" };
        ImmutablePair<String, String> stringStringImmutablePair2 = runOldDockstoreClient(dockstore, commandArray2);
        String stdout = stringStringImmutablePair2.getLeft();
        assertTrue(stdout.contains("dockstore-file-synapse-plugin"));
        assertTrue(stdout.contains("dockstore-file-s3-plugin"));
        assertFalse(stdout.contains("dockstore-icgc-storage-client-plugin"));
    }

    @Test
    void testPluginDisableOldClient() {
        String[] commandArray = new String[] { "--config", ResourceHelpers.resourceFilePath("pluginsTest2/configWithPlugins"), "plugin",
            "download" };
        runOldDockstoreClient(dockstore, commandArray);
        commandArray = new String[] { "--config", ResourceHelpers.resourceFilePath("pluginsTest2/configWithPlugins"), "plugin", "list" };
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
        String stdout = stringStringImmutablePair.getLeft();
        assertFalse(stdout.contains("dockstore-file-synapse-plugin"));
        assertFalse(stdout.contains("dockstore-file-s3-plugin"));
        assertTrue(stdout.contains("dockstore-file-icgc-storage-client-plugin"));
    }

    @Test
    void testMetadataMethodsOld() throws IOException {
        String[] commandArray = new String[] { "--config", TestUtility.getConfigFileLocation(true), "--version" };
        ImmutablePair<String, String> stringStringImmutablePair;
        try {
            stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
            assertTrue(stringStringImmutablePair.getLeft().contains("Dockstore version " + OLD_DOCKSTORE_VERSION));
        } catch (Exception e) {
            // Sometimes there's an error: Can't find the latest version. Something might be wrong with the connection to Github.
            LOG.debug("error running old client", e);
        }
        commandArray = new String[] { "--config", TestUtility.getConfigFileLocation(true), "--server-metadata" };
        stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
        assertTrue(stringStringImmutablePair.getLeft().contains("version"));
        systemOutRule.clear();
    }

    @Test
    void testCacheCleaningOld() throws IOException {
        runOldDockstoreClient(dockstore, new String[] { "--config", TestUtility.getConfigFileLocation(true), "--clean-cache" });
        systemOutRule.clear();
    }

    @Test
    void pluginDownloadOld() throws IOException {
        String[] commandArray = new String[] { "--config", TestUtility.getConfigFileLocation(true), "plugin", "download" };
        runOldDockstoreClient(dockstore, commandArray);
        systemOutRule.clear();
    }

    /**
     * Tests that the unpublished tool can be published, refreshed, then launched once the json and input file is attained
     */
    @Test
    void testActualToolLaunch() {
        // manual publish the workflow
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
                "quay.io/dockstoretestuser/test_input_json", "--script" });
        // launch the workflow
        runOldDockstoreClient(dockstore,
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
                "quay.io/dockstoretestuser/test_input_json", "--script" });
        String[] commandArray = { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "launch", "--entry",
            "quay.io/dockstoretestuser/test_input_json", "--json", testJson.getAbsolutePath(), "--script" };
        ImmutablePair<String, String> stringStringImmutablePair = runOldDockstoreClient(dockstore, commandArray);
        assertTrue((stringStringImmutablePair.getLeft().contains("Final process status is success")), "Final process status was not a success");
        assertTrue((stringStringImmutablePair.getRight().contains("Final process status is success")), "Final process status was not a success");

    }

}
