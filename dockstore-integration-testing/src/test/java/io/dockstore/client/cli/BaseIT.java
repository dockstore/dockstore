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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Constants;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.auth.ApiKeyAuth;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.postgresql.util.PSQLException;

import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;

/**
 * Base integration test class
 * A default configuration that cleans the database between tests
 */
@Category(ConfidentialTest.class)
public class BaseIT {

    public static final String ADMIN_USERNAME = "admin@admin.com";
    public static final String USER_1_USERNAME = "DockstoreTestUser";
    public static final String USER_2_USERNAME = "DockstoreTestUser2";
    static final String OTHER_USERNAME = "OtherUser";
    public static final String THREAD_AWARE_DB_NAME = "webservice_test_thread_" + Thread.currentThread().getId();
    public static final String THREAD_AWARE_DB_CONNECTION_STRING = "jdbc:postgresql://localhost:5432/" + THREAD_AWARE_DB_NAME;
    final String CURATOR_USERNAME = "curator@curator.com";

    public static final ThreadLocal<DropwizardTestSupport<DockstoreWebserviceConfiguration>> SUPPORT = ThreadLocal.withInitial(() -> new DropwizardTestSupport<>(DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH , ConfigOverride
        .config("database.url", THREAD_AWARE_DB_CONNECTION_STRING)));

    /**
     * Hacky map from DropwizardTestSupport to config file used for it since DropwizardTestSupport
     * doesn't seem to expose it
     */
    public static final ConcurrentHashMap<DropwizardTestSupport<DockstoreWebserviceConfiguration>, Pair<File, String>> supportMap = new ConcurrentHashMap<>();

    public static String rewriteClientConfig(String clientConfig){
        final Path tempFile;
        try {
            tempFile = Files.createTempFile("config", ".yaml");
            // FIXME: hacky way to switch out the port per thread
            String content = new String(Files.readAllBytes(Paths.get(clientConfig)), StandardCharsets.UTF_8);
            final int localPort = SUPPORT.get().getLocalPort();

            content = content.replaceAll("server-url:(.*):(.*)", "server-url:$1:" + localPort);
            Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
            tempFile.toFile().deleteOnExit();
            return tempFile.toFile().getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("error customizing configFile");
        }
    }


    @BeforeClass
    public static void dropAndRecreateDB() throws Exception {
        // write out a new configuraton based on overrides
        dropAndRecreateDB(CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH, SUPPORT.get());
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT);
        SUPPORT.get().before();
    }

    public static void dropAndRecreateDB(String configPath, DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws IOException, SQLException {
        final Path tempFile = Files.createTempFile("config", ".yaml");
        // FIXME: hacky way to switch out the database connection string per thread
        String content = new String(Files.readAllBytes(Paths.get(configPath)), StandardCharsets.UTF_8);
        content = content.replace("jdbc:postgresql://localhost:5432/webservice_test", THREAD_AWARE_DB_CONNECTION_STRING);
        Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
        supportMap.put(support, ImmutablePair.of(tempFile.toFile(), THREAD_AWARE_DB_NAME));
        tempFile.toFile().deleteOnExit();

        try {
            Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "dockstore", "dockstore");
            Statement statement = c.createStatement();
            statement.executeUpdate("DROP DATABASE IF EXISTS webservice_test_thread_" + Thread.currentThread().getId());
            statement.executeUpdate("CREATE DATABASE webservice_test_thread_" + Thread.currentThread().getId());
            statement.close();
            c.close();
        } catch(PSQLException e) {
            throw new RuntimeException("could not create database for thread");
        }
    }

    @AfterClass
    public static void afterClass(){
        SUPPORT.get().after();
    }

    @Before
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
    }

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };

    /**
     * Shared convenience method
     * @return
     */
    protected static ApiClient getWebClient(boolean authenticated, String username) {
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        File configFile = FileUtils.getFile("src", "test", "resources", "config2");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        CommonTestUtilities.setThreadedPort(parseConfig);

        ApiClient client = new ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        if (authenticated) {
            client.addDefaultHeader("Authorization", "Bearer " + (testingPostgres
                    .runSelectStatement("select content from token where tokensource='dockstore' and username= '" + username + "';",
                            new ScalarHandler<>())));
        }
        return client;
    }

    /**
     * the following were migrated from SwaggerClientIT and can be eventually merged. Note different config file used
     */

    protected static ApiClient getWebClient(String username) {
        return getWebClient(true, username);
    }

    protected static ApiClient getWebClient() {
        return getWebClient(true, false);
    }

    static ApiClient getAdminWebClient() {
        return getWebClient(true, true);
    }

    protected static ApiClient getAnonymousWebClient() {
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        CommonTestUtilities.setThreadedPort(parseConfig);
        ApiClient client = new ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        return client;
    }

    protected static ApiClient getWebClient(boolean correctUser, boolean admin) {
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        CommonTestUtilities.setThreadedPort(parseConfig);
        ApiClient client = new ApiClient();
        ApiKeyAuth bearer = (ApiKeyAuth) client.getAuthentication("BEARER");
        bearer.setApiKeyPrefix("BEARER");
        bearer.setApiKey((correctUser ?
                parseConfig.getString(admin ? Constants.WEBSERVICE_TOKEN_USER_1 : Constants.WEBSERVICE_TOKEN_USER_2) :
                "foobar"));
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        return client;
    }


    public interface SupportInterface {

        default ThreadLocal<DropwizardTestSupport> getSupport() {
            return ThreadLocal.withInitial(() -> new DropwizardTestSupport<>(DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH , ConfigOverride
                .config("database.url", THREAD_AWARE_DB_CONNECTION_STRING)));
        }

    }
}
