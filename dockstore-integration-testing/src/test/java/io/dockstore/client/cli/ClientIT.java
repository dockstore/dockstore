/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.client.cli;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import com.google.common.io.Files;

import io.dockstore.common.CommonTestUtilities.TestingPostgres;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Registry;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.swagger.client.ApiException;

import static io.dockstore.common.CommonTestUtilities.DUMMY_TOKEN_1;
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
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Before
    public void clearDB() throws IOException, TimeoutException {
        clearState();
    }

    public static String getConfigFileLocation(boolean correctUser) throws IOException {
        return getConfigFileLocation(correctUser, true);
    }

    public static String getConfigFileLocation(boolean correctUser, boolean validPort) throws IOException {
        File tempDir = Files.createTempDir();
        final File tempFile = File.createTempFile("config", "config", tempDir);
        FileUtils.write(tempFile, "token: " + (correctUser ? DUMMY_TOKEN_1 : "foobar") + "\n");
        FileUtils.write(tempFile, "server-url: http://localhost:" + (validPort ? "8000" : "9001") + "\n", true);

        return tempFile.getAbsolutePath();
    }

    @Test
    public void testListEntries() throws IOException, TimeoutException, ApiException {
        Client.main(new String[] { "--config", getConfigFileLocation(true), "list" });
    }

    @Test
    public void testDebugModeListEntries() throws IOException, TimeoutException, ApiException {
        Client.main(new String[] { "--debug", "--config", getConfigFileLocation(true), "list" });
    }

    @Test
    public void testListEntriesWithoutCreds() throws IOException, TimeoutException, ApiException {
        systemExit.expectSystemExitWithStatus(1);
        Client.main(new String[] { "--config", getConfigFileLocation(false), "list" });
    }

    @Test
    public void testListEntriesOnWrongPort() throws IOException, TimeoutException, ApiException {
        systemExit.expectSystemExitWithStatus(Client.CONNECTION_ERROR);
        Client.main(new String[] { "--config", getConfigFileLocation(true, false), "list" });
    }

    @Test
    public void quickRegisterValidEntry() throws IOException {
        Client.main(new String[] { "--config", getConfigFileLocation(true), "publish", "quay.io/test_org/test6" });

        // verify DB
        final TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'test6'", new ScalarHandler<>());
        Assert.assertTrue("should see three entries", count == 1);
    }

    @Ignore
    public void quickRegisterDuplicateEntry() throws IOException {
        Client.main(new String[] { "--config", getConfigFileLocation(true), "publish", "quay.io/test_org/test6" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "publish", "quay.io/test_org/test6", "view1" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "publish", "quay.io/test_org/test6", "view2" });

        // verify DB
        final TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'test6'", new ScalarHandler<>());
        Assert.assertTrue("should see three entries", count == 3);
    }

    @Test
    public void quickRegisterInValidEntry() throws IOException {
        systemExit.expectSystemExitWithStatus(Client.GENERIC_ERROR);
        Client.main(new String[] { "--config", getConfigFileLocation(true), "publish", "quay.io/test_org/test1" });
    }

    @Test
    public void quickRegisterUnknownEntry() throws IOException {
        systemExit.expectSystemExitWithStatus(Client.GENERIC_ERROR);
        Client.main(new String[] { "--config", getConfigFileLocation(true), "publish", "quay.io/funky_container_that_does_not_exist" });
    }

    /* When you manually publish on the dockstore CLI, it will now refresh the container after it is added.
     Since the below containers use dummy data and don't connect with Github/Bitbucket/Quay, the refresh will throw an error.
     Todo: Set up these tests with real data (not confidential)
     */
    @Ignore
    public void manualRegisterABunchOfValidEntries() throws IOException {
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test1" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test2" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
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
        systemExit.expectSystemExitWithStatus(Client.GENERIC_ERROR);
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test1" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test1" });
    }




}
