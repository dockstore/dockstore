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

package io.dockstore.common;

import java.io.File;

import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.Application;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xliu
 */
public final class CommonTestUtilities {

    private static final Logger LOG = LoggerFactory.getLogger(CommonTestUtilities.class);

    // Travis is slow, need to wait up to 1 min for webservice to return
    public static final int WAIT_TIME = 60000;
    /**
     * confidential testing config, includes keys
     */
    public static final String CONFIG_PATH = ResourceHelpers.resourceFilePath("dockstoreTest.yml");
    static final String DUMMY_TOKEN_1 = "08932ab0c9ae39a880905666902f8659633ae0232e94ba9f3d2094cb928397e7";

    private CommonTestUtilities() {

    }

    /**
     * Drops the database and recreates from migrations, not including any test data, using new application
     * @param support
     * @throws Exception
     */
    public static void dropAndRecreateNoTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws Exception {
        LOG.info("Dropping and Recreating the database with no test data");
        Application<DockstoreWebserviceConfiguration> application = support.newApplication();
        application.run("db", "drop-all", "--confirm-delete-everything", CONFIG_PATH);
        application.run("db", "migrate", CONFIG_PATH, "--include", "1.3.0.generated,1.4.0");
    }

    /**
     * Drops the database and recreates from migrations for non-confidential tests
     * @param support
     * @throws Exception
     */
    public static void dropAndCreateWithTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication) throws Exception {
        LOG.info("Dropping and Recreating the database with non-confidential test data");
        Application<DockstoreWebserviceConfiguration> application;
        if (isNewApplication) {
            application = support.newApplication();
        } else {
            application= support.getApplication();
        }
        application.run("db", "drop-all", "--confirm-delete-everything", CONFIG_PATH);
        application.run("db", "migrate", CONFIG_PATH, "--include", "1.3.0.generated");
        application.run("db", "migrate", CONFIG_PATH, "--include", "test");
        application.run("db", "migrate", CONFIG_PATH, "--include", "1.4.0");
    }

    /**
     * Wrapper for dropping and recreating database from migrations for test confidential 1
     * @param support
     * @throws Exception
     */
    public static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws Exception {
        LOG.info("Dropping and Recreating the database with confidential 1 test data");
        cleanStatePrivate1(support, CONFIG_PATH);
    }

    /**
     * Drops and recreates database from migrations for test confidential 1
     * @param support
     * @param configPath
     * @throws Exception
     */
    public static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath) throws Exception {
        Application<DockstoreWebserviceConfiguration> application = support.getApplication();
        application.run("db", "drop-all", "--confirm-delete-everything", configPath);
        application.run("db", "migrate", configPath, "--include", "1.3.0.generated");
        application.run("db", "migrate", configPath, "--include", "test.confidential1");
        application.run("db", "migrate", configPath, "--include", "1.4.0");
    }

    /**
     * Wrapper fir dropping and recreating database from migrations for test confidential 2
     * @param support
     * @throws Exception
     */
    public static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication) throws Exception {
        LOG.info("Dropping and Recreating the database with confidential 2 test data");
        cleanStatePrivate2(support, CONFIG_PATH, isNewApplication);
    }

    /**
     * Drops and recreates database from migrations for test confidential 2
     * @param support
     * @param configPath
     * @throws Exception
     */
    public static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath, boolean isNewApplication) throws Exception {
        Application<DockstoreWebserviceConfiguration> application;
        if (isNewApplication) {
            application = support.newApplication();
        } else {
            application= support.getApplication();
        }
        application.run("db", "drop-all", "--confirm-delete-everything", configPath);
        application.run("db", "migrate", configPath, "--include", "1.3.0.generated");
        application.run("db", "migrate", configPath, "--include", "test.confidential2");
        application.run("db", "migrate", configPath, "--include", "1.4.0");

    }

    /**
     * Loads up a specific set of workflows into the database
     * Specifically for tests toolsIdGet4Workflows() in GA4GHV1IT.java and toolsIdGet4Workflows() in GA4GHV2IT.java
     * @param support
     * @throws Exception
     */
    public static void setupSamePathsTest(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws Exception {
        LOG.info("Migrating samepaths migrations");
        Application<DockstoreWebserviceConfiguration> application = support.getApplication();
        application.run("db", "migrate", CONFIG_PATH, "--include", "samepaths");
    }


    /**
     * Loads up a specific set of workflows into the database
     * Specifically for tests cwlrunnerWorkflowRelativePathNotEncodedAdditionalFiles in GA4GHV2IT.java
     * @param support
     * @throws Exception
     */
    public static void setupTestWorkflow(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws Exception {
        LOG.info("Migrating testworkflow migrations");
        Application<DockstoreWebserviceConfiguration> application = support.getApplication();
        application.run("db", "migrate", CONFIG_PATH, "--include", "testworkflow");
    }

    /**
     * Allows tests to clear the database completely
     **/
    private static void clearState() {
        final File configFile = FileUtils.getFile("src", "test", "resources", "config");
        final INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        BasicPostgreSQL basicPostgreSQL = new BasicPostgreSQL(parseConfig);
        basicPostgreSQL.clearDatabase();
    }

    /**
     * Allows tests to clear the database but add basic testing data
     * @return
     */
    public static TestingPostgres getTestingPostgres() {
        final File configFile = FileUtils.getFile("src", "test", "resources", "config");
        final INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        return new TestingPostgres(parseConfig);
    }


    public static class TestingPostgres extends BasicPostgreSQL {

        TestingPostgres(INIConfiguration config) {
            super(config);
        }

        @Override
        public void clearDatabase() {
            super.clearDatabase();
        }

        @Override
        public <T> T runSelectStatement(String query, ResultSetHandler<T> handler, Object... params) {
            return super.runSelectStatement(query, handler, params);
        }

        @Override
        public boolean runUpdateStatement(String query, Object... params) {
            return super.runUpdateStatement(query, params);
        }
    }
}
