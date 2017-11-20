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
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.io.FileUtils;

/**
 * @author xliu
 */
public final class CommonTestUtilities {

    /**
     * confidential testing config, includes keys
     */
    public static final String CONFIG_PATH = ResourceHelpers.resourceFilePath("dockstoreTest.yml");
    static final String DUMMY_TOKEN_1 = "08932ab0c9ae39a880905666902f8659633ae0232e94ba9f3d2094cb928397e7";

    private CommonTestUtilities() {

    }

    public static void dropAndRecreate(DropwizardAppRule<DockstoreWebserviceConfiguration> rule) throws Exception {
        rule.getApplication().run("db", "drop-all", "--confirm-delete-everything", CONFIG_PATH);
        rule.getApplication().run("db", "migrate", CONFIG_PATH, "--include", "1.3.0.generated");
    }

    public static void cleanStatePrivate1(DropwizardAppRule<DockstoreWebserviceConfiguration> rule) throws Exception {
        cleanStatePrivate1(rule, CONFIG_PATH);
    }

    public static void cleanStatePrivate1(DropwizardAppRule<DockstoreWebserviceConfiguration> rule, String configPath) throws Exception {
        clearState();
        rule.getApplication().run("db", "migrate", configPath, "--include", "test.confidential1");
    }

    public static void cleanStatePrivate2(DropwizardAppRule<DockstoreWebserviceConfiguration> rule) throws Exception {
        cleanStatePrivate2(rule, CONFIG_PATH);

    }

    public static void cleanStatePrivate2(DropwizardAppRule<DockstoreWebserviceConfiguration> rule, String configPath) throws Exception {
        clearState();
        rule.getApplication().run("db", "migrate", configPath, "--include", "test.confidential2");
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
            // need to load non-confidential data
            DropwizardAppRule<DockstoreWebserviceConfiguration> rule = new DropwizardAppRule<>(
                DockstoreWebserviceApplication.class, CONFIG_PATH);
            try {
                rule.getApplication().run("db", "migrate", CONFIG_PATH, "--include", "test");
            } catch (Exception e) {
                throw new RuntimeException("unable to reset database with normal test data");
            }
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
