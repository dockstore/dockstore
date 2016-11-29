/*
 *    Copyright 2016 OICR
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

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities.TestingPostgres;
import io.dockstore.common.TestUtility;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Registry;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.swagger.client.ApiException;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemOutRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

import static io.dockstore.common.CommonTestUtilities.clearState;
import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;

/**
 *
 * @author dyuen
 */
public class ClientIT {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstore.yml"));

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Before
    public void clearDB() throws IOException, TimeoutException {
        clearState();
        Client.DEBUG.set(false);
    }

    @Test
    public void testListEntries() throws IOException, TimeoutException, ApiException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "list" });
    }

    @Test
    public void testDebugModeListEntries() throws IOException, TimeoutException, ApiException {
        Client.main(new String[] { "--debug", "--config", TestUtility.getConfigFileLocation(true), "tool", "list" });
    }

    @Test
    public void testListEntriesWithoutCreds() throws IOException, TimeoutException, ApiException {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(false), "tool", "list" });
    }

    @Test
    public void testListEntriesOnWrongPort() throws IOException, TimeoutException, ApiException {
        systemExit.expectSystemExitWithStatus(Client.CONNECTION_ERROR);
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true, false), "tool", "list" });
    }

    // Won't work as entry must be valid
    @Ignore
    public void quickRegisterValidEntry() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "publish", "quay.io/test_org/test6" });

        // verify DB
        final TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from tool where name = 'test6'", new ScalarHandler<>());
        Assert.assertTrue("should see three entries", count == 1);
    }

    @Ignore
    public void quickRegisterDuplicateEntry() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "publish", "quay.io/test_org/test6" });
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "publish", "quay.io/test_org/test6", "view1" });
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "publish", "quay.io/test_org/test6", "view2" });

        // verify DB
        final TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'test6'", new ScalarHandler<>());
        Assert.assertTrue("should see three entries", count == 3);
    }

    @Test
    public void quickRegisterInValidEntry() throws IOException {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "publish", "quay.io/test_org/test1" });
    }

    @Test
    public void quickRegisterUnknownEntry() throws IOException {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "publish", "quay.io/funky_container_that_does_not_exist" });
    }

    /* When you manually publish on the dockstore CLI, it will now refresh the container after it is added.
     Since the below containers use dummy data and don't connect with Github/Bitbucket/Quay, the refresh will throw an error.
     Todo: Set up these tests with real data (not confidential)
     */
    @Ignore("Since dockstore now checks for associated tags for Quay container, manual publishing of nonexistant images won't work")
    public void manualRegisterABunchOfValidEntries() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master" });
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test1" });
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test2" });
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master" });
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test1" });

        // verify DB
        final TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'bd2k-python-lib'",
                new ScalarHandler<>());
        Assert.assertTrue("should see three entries", count == 5);
    }

    @Test
    public void manualRegisterADuplicate() throws IOException {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master" });
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test1" });
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test1" });
    }

    @Test
    public void launchingCWLWorkflow() throws IOException {
        final String firstWorkflowCWL = ResourceHelpers.resourceFilePath("1st-workflow.cwl");
        final String firstWorkflowJSON = ResourceHelpers.resourceFilePath("1st-workflow-job.json");
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "workflow" ,"launch",
                "--entry", firstWorkflowCWL,  "--local-entry",  "--json",  firstWorkflowJSON });
    }

    @Test
    public void testMetadataMethods() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "--version"});
        Assert.assertTrue(systemOutRule.getLog().contains("Dockstore version"));
        systemOutRule.clearLog();
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "--server-metadata"});
        Assert.assertTrue(systemOutRule.getLog().contains("version"));
        systemOutRule.clearLog();
    }

    @Test
    public void testCacheCleaning() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "--clean-cache"});
        systemOutRule.clearLog();
    }

    @Test
    public void touchOnAllHelpMessages() throws IOException {
        checkCommandForHelp(new String[] {});
        checkCommandForHelp(new String[] {"tool"});
        checkCommandForHelp(new String[] { "tool", "list", "--help"});
        checkCommandForHelp(new String[] { "tool", "search", "--help"});
        checkCommandForHelp(new String[] { "tool", "publish", "--help"});
        checkCommandForHelp(new String[] { "tool", "info", "--help"});
        checkCommandForHelp(new String[] { "tool", "cwl", "--help"});
        checkCommandForHelp(new String[] { "tool", "wdl", "--help"});
        checkCommandForHelp(new String[] { "tool", "refresh", "--help"});
        checkCommandForHelp(new String[] { "tool", "label", "--help"});
        checkCommandForHelp(new String[] { "tool", "convert", "--help"});
        checkCommandForHelp(new String[] { "tool", "convert", "cwl2json", "--help"});
        checkCommandForHelp(new String[] { "tool", "convert", "cwl2yaml", "--help"});
        checkCommandForHelp(new String[] { "tool", "convert", "wdl2json", "--help"});
        checkCommandForHelp(new String[] { "tool", "convert", "entry2json", "--help"});
        checkCommandForHelp(new String[] { "tool", "convert", "entry2tsv", "--help"});
        checkCommandForHelp(new String[] { "tool", "launch", "--help"});
        checkCommandForHelp(new String[] { "tool", "version_tag", "--help"});
        checkCommandForHelp(new String[] { "tool", "version_tag", "remove", "--help"});
        checkCommandForHelp(new String[] { "tool", "version_tag", "update", "--help"});
        checkCommandForHelp(new String[] { "tool", "version_tag", "add", "--help"});
        checkCommandForHelp(new String[] { "tool", "update_tool", "--help"});
        checkCommandForHelp(new String[] { "tool", "manual_publish", "--help"});

        checkCommandForHelp(new String[] {"workflow"});
        checkCommandForHelp(new String[] { "workflow", "list", "--help"});
        checkCommandForHelp(new String[] { "workflow", "search", "--help"});
        checkCommandForHelp(new String[] { "workflow", "publish", "--help"});
        checkCommandForHelp(new String[] { "workflow", "info", "--help"});
        checkCommandForHelp(new String[] { "workflow", "cwl", "--help"});
        checkCommandForHelp(new String[] { "workflow", "wdl", "--help"});
        checkCommandForHelp(new String[] { "workflow", "refresh", "--help"});
        checkCommandForHelp(new String[] { "workflow", "label", "--help"});
        checkCommandForHelp(new String[] { "workflow", "convert", "--help"});
        checkCommandForHelp(new String[] { "workflow", "convert", "cwl2json", "--help"});
        checkCommandForHelp(new String[] { "workflow", "convert", "wd2json", "--help"});
        checkCommandForHelp(new String[] { "workflow", "convert", "entry2json", "--help"});
        checkCommandForHelp(new String[] { "workflow", "convert", "entry2tsv", "--help"});
        checkCommandForHelp(new String[] { "workflow", "launch", "--help"});
        checkCommandForHelp(new String[] { "workflow", "version_tag", "--help"});
        checkCommandForHelp(new String[] { "workflow", "update_workflow", "--help"});
        checkCommandForHelp(new String[] { "workflow", "manual_publish", "--help"});
        checkCommandForHelp(new String[] { "workflow", "restub", "--help"});

        checkCommandForHelp(new String[] {"workflow"});
    }

    private void checkCommandForHelp(String[] argv) throws IOException {
        final ArrayList<String> strings = Lists.newArrayList(argv);
        strings.add("--config");
        strings.add(TestUtility.getConfigFileLocation(true));

        Client.main(strings.toArray(new String[strings.size()]));
        Assert.assertTrue(systemOutRule.getLog().contains("HELP FOR DOCKSTORE"));
        systemOutRule.clearLog();
    }

}
