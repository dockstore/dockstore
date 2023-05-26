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

import static io.dockstore.common.DescriptorLanguage.CWL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.dockstore.openapi.client.api.HostedApi;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dropwizard.core.Application;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.model.PublishRequest;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.Executor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpStatus;
import org.assertj.core.util.Files;
import org.glassfish.jersey.client.ClientProperties;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xliu
 */
public final class CommonTestUtilities {

    private static final Logger LOG = LoggerFactory.getLogger(CommonTestUtilities.class);
    public static final String OLD_DOCKSTORE_VERSION = "1.13.0";
    public static final List<String> COMMON_MIGRATIONS = List.of("1.3.0.generated", "1.3.1.consistency", "1.4.0", "1.5.0", "1.6.0", "1.7.0",
            "1.8.0", "1.9.0", "1.10.0", "1.11.0", "1.12.0", "1.13.0", "1.14.0", "1.15.0");
    // Travis is slow, need to wait up to 1 min for webservice to return
    public static final int WAIT_TIME = 60000;
    public static final String PUBLIC_CONFIG_PATH = getUniversalResourceFileAbsolutePath("dockstore.yml").orElse(null);
    /**
     * confidential testing config, includes keys
     */
    public static final String CONFIDENTIAL_CONFIG_PATH = getUniversalResourceFileAbsolutePath("dockstoreTest.yml").orElse(null);
    static final String DUMMY_TOKEN_1 = "08932ab0c9ae39a880905666902f8659633ae0232e94ba9f3d2094cb928397e7";
    public static final String BITBUCKET_TOKEN_CACHE = "/tmp/dockstore-bitbucket-token-cache/";

    private CommonTestUtilities() {

    }

    /**
     * Drops the database and recreates from migrations, not including any test data, using new application
     *
     * @param support reference to testing instance of the dockstore web service
     */
    public static void dropAndRecreateNoTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) {
        dropAndRecreateNoTestData(support, CONFIDENTIAL_CONFIG_PATH);
    }

    public static void dropAndRecreateNoTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support,
        String dropwizardConfigurationFile) {
        LOG.info("Dropping and Recreating the database with no test data");
        dropAllAndRunMigration(listMigrations(), support.newApplication(), dropwizardConfigurationFile);
    }

    /**
     * Drops the database and recreates from migrations for non-confidential tests
     *
     * @param support reference to testing instance of the dockstore web service
     */
    public static void dropAndCreateWithTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication) {
        dropAndCreateWithTestData(support, isNewApplication, CONFIDENTIAL_CONFIG_PATH);
    }

    public static void dropAndCreateWithTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication,
        String dropwizardConfigurationFile)  {
        LOG.info("Dropping and Recreating the database with non-confidential test data");
        dropAllAndRunMigration(listMigrations("test", "test_1.5.0"), getApplication(support, isNewApplication), dropwizardConfigurationFile);
    }

    /**
     * Adds 3 tools to the database. 2 tools are unpublished with 1 version each. 1 tool is published and has two versions (1 hidden) and optionally deletes BitBucket token
     *
     * @param support reference to testing instance of the dockstore web service
     * @param isNewApplication
     * @param needBitBucketToken if false BitBucket token is deleted from database
     */
    public static void dropAndCreateWithTestDataAndAdditionalTools(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication,
        TestingPostgres testingPostgres, boolean needBitBucketToken) {
        dropAndCreateWithTestDataAndAdditionalTools(support, isNewApplication, CONFIDENTIAL_CONFIG_PATH);
        if (!needBitBucketToken) {
            deleteBitBucketToken(testingPostgres);
        }

    }

    // Adds 3 tools to the database. 2 tools are unpublished with 1 version each. 1 tool is published and has two versions (1 hidden).
    public static void dropAndCreateWithTestDataAndAdditionalTools(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication, TestingPostgres testingPostgres) {
        dropAndCreateWithTestDataAndAdditionalTools(support, isNewApplication, testingPostgres, false);
    }

    public static void dropAndCreateWithTestDataAndAdditionalTools(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication,
            String dropwizardConfigurationFile) {
        LOG.info("Dropping and Recreating the database with non-confidential test data");
        dropAllAndRunMigration(listMigrations("test", "add_test_tools", "test_1.5.0"), getApplication(support, isNewApplication), dropwizardConfigurationFile);
    }

    /**
     * Adds 3 tools to the database. 2 tools are unpublished with 1 version each. 1 tool is published and has two versions (1 hidden).
     * <p>
     * Adds 2 published workflows to the database.
     * @param support reference to testing instance of the dockstore web service
     * @param isNewApplication
     * @param dropwizardConfigurationFile
     */
    public static void dropAndCreateWithTestDataAndAdditionalToolsAndWorkflows(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication,
            String dropwizardConfigurationFile) {
        LOG.info("Dropping and Recreating the database with non-confidential test data");
        dropAllAndRunMigration(listMigrations("test", "add_test_tools", "testworkflow", "test_1.5.0"), getApplication(support, isNewApplication), dropwizardConfigurationFile);
    }

    /**
     * Shared convenience method
     * TODO: Somehow merge it with the method below, they are nearly identical
     * @return
     */
    public static ApiClient getWebClient(boolean authenticated, String username, TestingPostgres testingPostgres) {
        ApiClient client = new ApiClient();
        client.setBasePath(getBasePath());
        if (authenticated) {
            client.addDefaultHeader("Authorization", getDockstoreToken(testingPostgres, username));
        }
        return client;
    }

    /**
     * Shared convenience method
     * TODO: Somehow merge it with the method above, they are nearly identical
     * @return
     */
    public static io.dockstore.openapi.client.ApiClient getOpenAPIWebClient(boolean authenticated, String username, TestingPostgres testingPostgres) {
        io.dockstore.openapi.client.ApiClient client = new io.dockstore.openapi.client.ApiClient();
        client.setBasePath(getBasePath());
        if (authenticated) {
            client.addDefaultHeader("Authorization", getDockstoreToken(testingPostgres, username));
        }
        return client;
    }

    private static String getBasePath() {
        String configFileAbsolutePath = getUniversalResourceFileAbsolutePath("config2").orElse(null);
        INIConfiguration parseConfig = Utilities.parseConfig(configFileAbsolutePath);
        return parseConfig.getString(Constants.WEBSERVICE_BASE_PATH);
    }

    private static String getDockstoreToken(TestingPostgres testingPostgres, String username) {
        return "Bearer " + (testingPostgres
                .runSelectStatement("select content from token where tokensource='dockstore' and username= '" + username + "';", String.class));
    }
    /**
     * Deletes BitBucket Tokens from Database
     *
     * @param testingPostgres reference to the testing instance of Postgres
     */
    private static void deleteBitBucketToken(TestingPostgres testingPostgres)  {
        if (testingPostgres != null) {
            LOG.info("Deleting BitBucket Token from Database");
            testingPostgres.runUpdateStatement("delete from token where tokensource = 'bitbucket.org'");
        } else {
            LOG.info("testingPostgres is null");
        }
    }

    public static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication,
        TestingPostgres testingPostgres, boolean needBitBucketToken) {
        cleanStatePrivate(support, isNewApplication, testingPostgres, needBitBucketToken, TestUser.TEST_USER1);
    }

    /**
     * Wrapper for dropping and recreating database from migrations for test confidential 1 and optionally deleting BitBucket tokens
     *
     * @param support reference to testing instance of the dockstore web service
     * @param testingPostgres reference to the testing instance of Postgres
     * @param needBitBucketToken if false the bitbucket token will be deleted
     */
    public static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, TestingPostgres testingPostgres,
        boolean needBitBucketToken) {
        cleanStatePrivate(support, testingPostgres, needBitBucketToken, TestUser.TEST_USER1);
    }

    /**
     * Wrapper for dropping and recreating database from migrations for test confidential 1
     *
     * @param support reference to testing instance of the dockstore web service
     * @param testingPostgres reference to the testing instance of Postgres
     */
    public static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, TestingPostgres testingPostgres) {
        LOG.info("Dropping and Recreating the database with confidential 1 test data");
        cleanStatePrivate1(support, testingPostgres, false);
    }

    /**
     * Drops and recreates database from migrations for test confidential 1
     *
     * @param support reference to testing instance of the dockstore web service
     * @param configPath
     */
    public static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath,
        boolean isNewApplication) {
        cleanStatePrivate(support, configPath, isNewApplication, TestUser.TEST_USER1);
    }

    /**
     * Drops and recreates database from migrations for test confidential 1
     *
     * @param support    reference to testing instance of the dockstore web service
     * @param configPath
     */
    private static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath) {
        cleanStatePrivate(support, configPath, TestUser.TEST_USER1);
    }

    /**
     * Clean the database and reset it with a specific test user.
     * @param support
     * @param configPath
     * @param isNewApplication
     * @param testUser
     */
    public static void cleanStatePrivate(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath,
        boolean isNewApplication, TestUser testUser) {
        dropAllAndRunMigration(listMigrations(testUser.databasedump, testUser.databasedumpUpgrade), getApplication(support, isNewApplication), configPath);
    }

    /**
     * Clean the database and reset it with a specific test user, but also manage the bitbucket tokens.
     *
     * For efficiency, bitbucket tests should use this to preserve the bitbucket tokens to avoid them resetting with every test, breaking the cache.
     * @param support
     * @param testingPostgres
     * @param needBitBucketToken
     * @param testUser
     */
    public static void cleanStatePrivate(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, TestingPostgres testingPostgres,
        boolean needBitBucketToken, TestUser testUser) {
        LOG.info("Dropping and Recreating the database with confidential " + (testUser.ordinal() + 1) + " test data");
        cleanStatePrivate(support, CONFIDENTIAL_CONFIG_PATH, testUser);
        handleBitBucketTokens(support, testingPostgres, needBitBucketToken);
    }

    private static void cleanStatePrivate(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath, TestUser user) {
        dropAllAndRunMigration(listMigrations(user.databasedump, user.databasedumpUpgrade), support.getApplication(), configPath);
    }

    /**
     * Clean the database and reset it with a specific test user, but also manage the bitbucket tokens and set whether we want to restart the web service (I think).
     *
     * For efficiency, bitbucket tests should use this to preserve the bitbucket tokens to avoid them resetting with every test, breaking the cache.
     * @param support
     * @param isNewApplication
     * @param testingPostgres
     * @param needBitBucketToken
     * @param testUser
     */
    public static void cleanStatePrivate(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication,
        TestingPostgres testingPostgres, boolean needBitBucketToken, TestUser testUser) {
        LOG.info("Dropping and Recreating the database with confidential " + (testUser.ordinal() + 1) + " test data");

        cleanStatePrivate(support, CONFIDENTIAL_CONFIG_PATH, isNewApplication, testUser);
        handleBitBucketTokens(support, testingPostgres, needBitBucketToken);
    }

    public static void cacheBitbucketTokens(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) {
        DockstoreWebserviceApplication application = support.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        TokenDAO tokenDAO = new TokenDAO(sessionFactory);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        final List<Token> allBitBucketTokens = tokenDAO.findAllBitBucketTokens();
        File cacheDirectory = new File(BITBUCKET_TOKEN_CACHE);
        if (!cacheDirectory.exists()) {
            Files.newFolder(BITBUCKET_TOKEN_CACHE);
        }
        for (Token token : allBitBucketTokens) {
            // a token with an update time is not straight from the DB dump OR a token with a expiry date
            if (token.getDbUpdateDate() != null || (token.getExpirationTime() != null) && Instant.now().isBefore(Instant.ofEpochMilli(token.getExpirationTime()))) {
                final String serializedToken = gson.toJson(token);
                try {
                    FileUtils.writeStringToFile(new File(BITBUCKET_TOKEN_CACHE + Hashing.sha256().hashString(token.getRefreshToken(), StandardCharsets.UTF_8) + ".json"), serializedToken,
                        StandardCharsets.UTF_8, false);
                } catch (IOException | UncheckedIOException e) {
                    LOG.error("could not cache bitbucket token", e);
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Returns a list of migrations containing COMMON_MIGRATIONS and additional migrations
     * @param additionals
     * @return
     */
    public static List<String> listMigrations(String... additionals) {
        return Stream.concat(COMMON_MIGRATIONS.stream(), Stream.of(additionals)).collect(Collectors.toList());
    }

    public static void runMigration(List<String> migrations, Application<DockstoreWebserviceConfiguration> application,
        String configPath) {
        String migrationsString = String.join(",", migrations);
        try {
            if (!restoreMigratedDb(migrationsString)) {
                application.run("db", "migrate", configPath, "--include", migrationsString);
                dumpMigratedDb(migrationsString);
            }
        } catch (Exception e) {
            fail("database migration failed");
        }
    }

    private static String pathOfMigratedDb(String migrationsId) {
        return "/tmp/dockstore_dump_" + migrationsId + ".sql";
    }

    private static boolean dumpMigratedDb(String migrationsId) {
        String path = pathOfMigratedDb(migrationsId);
        boolean success = runCommand(String.format("pg_dump webservice_test -U postgres > %s", path));
        if (!success) {
            LOG.error("dump failed");
            runCommand(String.format("rm -f %s", path));
        }
        return success;
    }

    private static boolean restoreMigratedDb(String migrationsId) {
        String path = pathOfMigratedDb(migrationsId);
        if (!new File(path).exists()) {
            LOG.info("no dump exists");
            return false;
        }
        return runCommand(String.format("psql webservice_test -U postgres < %s", path));
    }

    private static boolean runCommand(String command) {
        LOG.info("running command: " + command);
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", command});
            boolean success = process.waitFor() == 0;
            if (!success) {
                LOG.error("command '" + command + "' failed");
                LOG.error("stderr: " + IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8));
            }
            return success;
        } catch (Exception e) {
            String message = "exception running command '" + command + "'";
            LOG.error(message);
            throw new RuntimeException(message, e);
        }
    }

    public static void dropAll(Application<DockstoreWebserviceConfiguration> application, String configPath) {
        try {
            application.run("db", "drop-all", "--confirm-delete-everything", configPath);
        } catch (Exception e) {
            fail("database drop-all failed");
        }
    }

    public static void dropAllAndRunMigration(List<String> migrations, Application<DockstoreWebserviceConfiguration> application,
        String configPath) {
        dropAll(application, configPath);
        runMigration(migrations, application, configPath);
    }

    private static void handleBitBucketTokens(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, TestingPostgres testingPostgres, boolean needBitBucketToken) {
        if (!needBitBucketToken) {
            deleteBitBucketToken(testingPostgres);
        } else {
            DockstoreWebserviceApplication application = support.getApplication();
            Session session = application.getHibernate().getSessionFactory().openSession();
            ManagedSessionContext.bind(session);
            //TODO restore bitbucket token from disk cache to reduce rate limit from busting cache with new access tokens
            SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
            TokenDAO tokenDAO = new TokenDAO(sessionFactory);
            final List<Token> allBitBucketTokens = tokenDAO.findAllBitBucketTokens();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            for (Token token : allBitBucketTokens) {
                try {
                    final String cacheCandidate = FileUtils.readFileToString(new File(BITBUCKET_TOKEN_CACHE + Hashing.sha256().hashString(token.getRefreshToken(), StandardCharsets.UTF_8) + ".json"),
                        StandardCharsets.UTF_8);
                    final Token cachedToken = gson.fromJson(cacheCandidate, Token.class);
                    if (cachedToken != null) {
                        testingPostgres.runUpdateStatement(
                            "update token set content = '" + cachedToken.getContent() + "', dbUpdateDate = '" + cachedToken.getDbUpdateDate().toLocalDateTime().toString() + "' where id = "
                                + cachedToken.getId());
                    }
                } catch (IOException | UncheckedIOException e) {
                    // probably ok
                    LOG.debug("could not read bitbucket token", e);
                }
            }
        }
    }

    /**
     * Wrapper for dropping and recreating database from migrations for test confidential 2
     *
     * @param support reference to testing instance of the dockstore web service
     * @param testingPostgres reference to the testing instance of Postgres
     * @param needBitBucketToken if false BitBucket token is deleted
     */
    public static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication,
        TestingPostgres testingPostgres, boolean needBitBucketToken) {
        cleanStatePrivate(support, isNewApplication, testingPostgres, needBitBucketToken, TestUser.TEST_USER2);
    }

    /**
     * Wrapper for dropping and recreating database from migrations for test confidential 2
     *
     * @param support reference to testing instance of the dockstore web service
     * @param testingPostgres reference to the testing instance of Postgres
     */
    public static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication,
        TestingPostgres testingPostgres) {

        cleanStatePrivate2(support, isNewApplication, testingPostgres, false);
        // TODO: You can uncomment the following line to disable GitLab tool and workflow discovery
        // getTestingPostgres(SUPPORT).runUpdateStatement("delete from token where tokensource = 'gitlab.com'");
    }

    /**
     * Drops and recreates database from migrations for test confidential 2
     *
     * @param support reference to testing instance of the dockstore web service
     * @param configPath
     */
    public static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath,
        boolean isNewApplication) {
        cleanStatePrivate(support, configPath, isNewApplication, TestUser.TEST_USER2);
    }

    /**
     * Adds 3 tools to the database. 2 tools are unpublished with 1 version each. 1 tool is published and has two versions (1 hidden)
     * and optionally deletes BitBucket token
     *
     * @param support reference to testing instance of the dockstore web service
     * @param isNewApplication
     * @param testingPostgres reference to the testing instance of Postgres
     * @param needBitBucketToken If false BitBucket tokens will be deleted
     */
    public static void addAdditionalToolsWithPrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication, TestingPostgres testingPostgres,
        boolean needBitBucketToken) {
        LOG.info("Dropping and Recreating the database with confidential 2 test data and additional tools");
        addAdditionalToolsWithPrivate2(support, CONFIDENTIAL_CONFIG_PATH, isNewApplication);
        if (!needBitBucketToken) {
            deleteBitBucketToken(testingPostgres);
        }
    }

    // Adds 3 tools to the database. 2 tools are unpublished with 1 version each. 1 tool is published and has two versions (1 hidden).
    public static void addAdditionalToolsWithPrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication, TestingPostgres testingPostgres) {
        addAdditionalToolsWithPrivate2(support,  isNewApplication, testingPostgres, false);
    }

    public static void addAdditionalToolsWithPrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath,
            boolean isNewApplication) {
        dropAllAndRunMigration(listMigrations("test.confidential2", "add_test_tools", "test.confidential2_1.5.0"), getApplication(support, isNewApplication), configPath);
    }

    public static Application<DockstoreWebserviceConfiguration> getApplication(final DropwizardTestSupport<DockstoreWebserviceConfiguration> support, final boolean isNewApplication) {
        return isNewApplication ? support.newApplication() : support.getApplication();
    }

    /**
     * Loads up a specific set of workflows into the database
     * Specifically for tests toolsIdGet4Workflows() in GA4GHV1IT.java and toolsIdGet4Workflows() in GA4GHV2IT.java
     *
     * @param support reference to testing instance of the dockstore web service
     */
    public static void setupSamePathsTest(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) {
        LOG.info("Migrating samepaths migrations");
        dropAllAndRunMigration(listMigrations("samepaths"), support.newApplication(), CONFIDENTIAL_CONFIG_PATH);
    }

    /**
     * Loads up a specific set of workflows into the database
     * Specifically for tests cwlrunnerWorkflowRelativePathNotEncodedAdditionalFiles in GA4GHV2IT.java
     *
     * @param support reference to testing instance of the dockstore web service
     */
    public static void setupTestWorkflow(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) {
        LOG.info("Migrating testworkflow migrations");
        dropAllAndRunMigration(listMigrations("test", "testworkflow", "test_1.5.0"), support.getApplication(), CONFIDENTIAL_CONFIG_PATH);
    }

    public static ImmutablePair<String, String> runOldDockstoreClient(File dockstore, String[] commandArray) throws RuntimeException {
        List<String> commandList = new ArrayList<>();
        commandList.add(dockstore.getAbsolutePath());
        commandList.addAll(Arrays.asList(commandArray));
        String commandString = String.join(" ", commandList);
        return Utilities.executeCommand(commandString);
    }

    /**
     * For running the old dockstore client when spaces are involved
     *
     * @param dockstore
     * @param commandArray
     * @throws RuntimeException
     */
    public static void runOldDockstoreClientWithSpaces(File dockstore, String[] commandArray) throws RuntimeException {
        List<String> commandList;
        CommandLine commandLine = new CommandLine(dockstore.getAbsoluteFile());

        commandList = Arrays.asList(commandArray);
        commandList.forEach(command -> {
            commandLine.addArgument(command, false);
        });
        Executor executor = new DefaultExecutor();
        try {
            executor.execute(commandLine);
        } catch (IOException e) {
            LOG.error("Could not execute command. " + e.getMessage(), e);
            e.printStackTrace();
        }
    }

    public static void checkToolList(String log) {
        assertTrue(log.contains("NAME"));
        assertTrue(log.contains("DESCRIPTION"));
        assertTrue(log.toLowerCase().contains("git repo"));
    }

    public static void restartElasticsearch() throws IOException {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        try (DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig()).build(); DockerClient instance = DockerClientImpl.getInstance(config, httpClient)) {
            List<Container> exec = instance.listContainersCmd().exec();
            Optional<Container> elasticsearch = exec.stream().filter(container -> container.getImage().contains("elasticsearch"))
                    .findFirst();
            if (elasticsearch.isPresent()) {
                Container container = elasticsearch.get();
                try {
                    instance.restartContainerCmd(container.getId());
                    // Wait 25 seconds for elasticsearch to become ready
                    // TODO: Replace with better wait
                    Thread.sleep(25000);
                } catch (Exception e) {
                    System.err.println("Problems restarting Docker container");
                }
            }

        }
    }

    // These two functions are duplicated from SwaggerUtility in dockstore-client to prevent importing dockstore-client
    // This cannot be moved to dockstore-common because PublishRequest requires built dockstore-webservice

    /**
     * @param bool
     * @return
     */
    public static PublishRequest createPublishRequest(Boolean bool) {
        PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublish(bool);
        return publishRequest;
    }

    public static io.dockstore.openapi.client.model.PublishRequest createOpenAPIPublishRequest(Boolean bool) {
        io.dockstore.openapi.client.model.PublishRequest publishRequest = new io.dockstore.openapi.client.model.PublishRequest();
        publishRequest.setPublish(bool);
        return publishRequest;
    }

    public static <T> T getArbitraryURL(String url, GenericType<T> type, ApiClient client, String acceptType) {
        return client
            .invokeAPI(url, "GET", new ArrayList<>(), null, new HashMap<>(), new HashMap<>(), acceptType, "application/zip",
                new String[] { "BEARER" }, type).getData();
    }

    /**
     * Get an arbitrary URL with the accept type defaulting to "application/zip".
     */
    public static <T> T getArbitraryURL(String url, GenericType<T> type, ApiClient client) {
        return getArbitraryURL(url, type, client, "application/zip");
    }

    /**
     * This retrieves a resource file using getResourceAsStream, which works for retrieving resource files packaged in a jar.
     * This allows other repos, like dockstore-support, to use utility methods that require resource files from this package.
     * @param resourceFileName
     * @return
     */
    public static Optional<File> getUniversalResourceFile(String resourceFileName) {
        File tempResourceFile = null;
        try (InputStream inputStream = Objects.requireNonNull(CommonTestUtilities.class.getClassLoader().getResourceAsStream(resourceFileName))) {
            tempResourceFile = File.createTempFile(resourceFileName, null);
            tempResourceFile.deleteOnExit();
            FileUtils.copyInputStreamToFile(inputStream, tempResourceFile);
            return Optional.of(tempResourceFile);
        } catch (Exception e) {
            LOG.error("Could not get resource file {}", resourceFileName, e);
            return Optional.empty();
        }
    }

    /**
     * Get the absolute path of a resource file. The resource file is retrieved using getResourceAsStream, which works for retrieving resource files packaged in a jar.
     * @param resourceFileName
     * @return
     */
    public static Optional<String> getUniversalResourceFileAbsolutePath(String resourceFileName) {
        Optional<File> resourceFile = getUniversalResourceFile(resourceFileName);
        if (resourceFile.isPresent()) {
            try {
                return Optional.of(resourceFile.get().getAbsolutePath());
            } catch (SecurityException e) {
                LOG.error("Could not get absolute path of resource file {}", resourceFile, e);
            }
        }
        return Optional.empty();
    }

    public static void testXTotalCount(Client jerseyClient, String path, int expectedValue) {
        Response response = jerseyClient.target(path).request().property(ClientProperties.READ_TIMEOUT, 0).get();
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        MultivaluedMap<String, Object> headers = response.getHeaders();
        Object xTotalCount = headers.getFirst("X-total-count");
        assertEquals(String.valueOf(expectedValue), xTotalCount);
    }

    public static Workflow createHostedWorkflowWithVersion(final HostedApi hostedApi) {
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow(null, "awesomeTool", CWL.getShortName(), null, null);
        SourceFile file = new SourceFile();
        file.setContent("cwlVersion: v1.0\n" + "class: Workflow");
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        file.setAbsolutePath("/Dockstore.cwl");
        hostedWorkflow = hostedApi.editHostedWorkflow(Lists.newArrayList(file), hostedWorkflow.getId());
        file.setContent("cwlVersion: v1.1\n" + "class: Workflow");
        hostedWorkflow = hostedApi.editHostedWorkflow(Lists.newArrayList(file), hostedWorkflow.getId());
        return hostedWorkflow;
    }

    /**
     * This collects some information about our test users
     */
    public enum TestUser {
        TEST_USER1("test.confidential1", "test.confidential1_1.5.0", "DockstoreTestUser"),
        TEST_USER2("test.confidential2", "test.confidential2_1.5.0", "DockstoreTestUser2"),
        TEST_USER4("test.confidential4", "test.confidential4_1.5.0", "DockstoreTestUser4");

        public final String databasedump;

        public final String databasedumpUpgrade;

        public final String dockstoreUserName;

        TestUser(String databasedump, String databasedumpUpgrade, String dockstoreUserName) {
            this.databasedump = databasedump;
            this.databasedumpUpgrade = databasedumpUpgrade;
            this.dockstoreUserName = dockstoreUserName;
        }
    }
}
