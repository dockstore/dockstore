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

package io.dockstore.common;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.google.common.io.Files;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.Application;
import io.dropwizard.testing.DropwizardTestSupport;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.common.CommonTestUtilities.DUMMY_TOKEN_1;

/**
 * @author jpatricia
 */
public final class TestUtility {

    private static final Logger LOG = LoggerFactory.getLogger(TestUtility.class);
    @Rule
    public TemporaryFolder folder= new TemporaryFolder();

    private TestUtility() {
        // utility class
    }

    public static String getConfigFileLocation(boolean correctUser) throws IOException {
        return getConfigFileLocation(correctUser, true, false);
    }

    public static String getConfigFileLocation(boolean correctUser, boolean validPort, boolean useCache) throws IOException {
        File tempDir = Files.createTempDir();
        final File tempFile = File.createTempFile("config", "config", tempDir);
        FileUtils.write(tempFile, "token: " + (correctUser ? DUMMY_TOKEN_1 : "foobar") + "\n", StandardCharsets.UTF_8);
        FileUtils.write(tempFile, "server-url: http://localhost:" + (validPort ? "8080" : "9001") + "\n", StandardCharsets.UTF_8, true);
        if (useCache) {
            FileUtils.write(tempFile, "use-cache: true\n", StandardCharsets.UTF_8, true);
        }

        return tempFile.getAbsolutePath();
    }

    /**
     * Drops the database and recreates from migrations, not including any test data, using new application
     * @param support   reference to testing instance of the dockstore web service
     * @param configPath    Dropwizard config file path
     * @throws Exception
     */
    public static void dropAndRecreateNoTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath) throws Exception {
        LOG.info("Dropping and Recreating the database with no test data");
        Application<DockstoreWebserviceConfiguration> application = support.newApplication();
        application.run("db", "drop-all", "--confirm-delete-everything", configPath);
        application.run("db", "migrate", configPath, "--include", "1.3.0.generated,1.3.1.consistency,1.4.0,1.5.0");
    }

    /**
     * Returns a path to a temporary configuration file with a invalid notifications webhook URL
     * All slack webhook URLs are apparently valid.
     */
    public static String getConfigFileLocationWithInvalidNotifications(boolean correctUser) throws IOException {
        String configFileLocation = getConfigFileLocation(correctUser);
        File f = new File(configFileLocation);
        FileUtils.write(f, "notifications: " + "potato" + "\n", StandardCharsets.UTF_8, true);
        return f.getAbsolutePath();
    }

    /**
     * Returns a path to a temporary configuration file with a valid notifications webhook URL
     * All slack webhook URLs are apparently valid.
     */
    public static String getConfigFileLocationWithValidNotifications(boolean correctUser) throws IOException {
        String configFileLocation = getConfigFileLocation(correctUser);
        File f = new File(configFileLocation);
        FileUtils.write(f, "notifications: " + "https://hooks.slack.com/services/potato" + "\n", StandardCharsets.UTF_8, true);
        return f.getAbsolutePath();
    }

    /**
     * Due to issue #1264, we need to create a ~/.dockstore/config in order to bypass dockstore asking for token/server-url
     * Creating a directory instead to avoid potential conflicts since it doesn't matter
     */
    public static void createFakeDockstoreConfigFile() {
        String path = System.getProperty("user.home") + File.separator + ".dockstore/config";
        File customDir = new File(path);
        if (customDir.exists()) {
            LOG.info(customDir + " already exists");
        } else if (customDir.mkdirs()) {
            LOG.info(customDir + " was created");
        } else {
            LOG.error(customDir + " was not created");
        }
    }
}
