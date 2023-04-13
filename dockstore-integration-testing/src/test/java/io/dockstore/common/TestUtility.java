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

import static io.dockstore.common.CommonTestUtilities.DUMMY_TOKEN_1;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jpatricia
 */
public final class TestUtility {

    private static final Logger LOG = LoggerFactory.getLogger(TestUtility.class);

    private TestUtility() {
        // utility class
    }

    public static String getConfigFileLocation(boolean correctUser) throws IOException {
        return getConfigFileLocation(correctUser, true, false);
    }

    public static String getConfigFileLocation(boolean correctUser, boolean validPort, boolean useCache) throws IOException {
        File tempDir = Files.createTempDirectory("tmpconfig").toFile();
        final File tempFile = File.createTempFile("config", "config", tempDir);
        FileUtils.write(tempFile, "token: " + (correctUser ? DUMMY_TOKEN_1 : "foobar") + "\n", StandardCharsets.UTF_8);
        FileUtils.write(tempFile, "server-url: http://localhost:" + (validPort ? "8080" : "9001") + "\n", StandardCharsets.UTF_8, true);
        if (useCache) {
            FileUtils.write(tempFile, "use-cache: true\n", StandardCharsets.UTF_8, true);
        }

        return tempFile.getAbsolutePath();
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
     * Currently in production, the basePath is "/api/" and Nginx removes first /api it finds and redirects it to "http://webservice:8080/$uri"
     * This mimics the nginx functionality
     *
     * @param originalUrl The original URL that the UI2 and the Swagger UI displays
     * @param basePath    The basePath set in the Dropwizard configuration file
     * @return The URL modified by nginx
     */
    public static String mimicNginxRewrite(String originalUrl, String basePath) {
        return "/api/".equals(basePath) ? originalUrl.replaceFirst("/api/", "/") : originalUrl;
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
