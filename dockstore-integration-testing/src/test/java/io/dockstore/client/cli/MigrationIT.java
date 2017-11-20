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

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static io.dockstore.common.CommonTestUtilities.clearStateMakePrivate;
import static io.dockstore.common.CommonTestUtilities.clearStateMakePrivate2;
import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;

/**
 * Testing migration
 *
 * @author dyuen
 */
@Category(ConfidentialTest.class)
public class MigrationIT {

//    @Rule
//    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
//
//    @Rule
//    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstoreTest.yml"));

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };


    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    public void clearDBandSetup1() throws IOException, TimeoutException {
        clearStateMakePrivate();
    }

    public void clearDBandSetup2() throws IOException, TimeoutException {
        clearStateMakePrivate2();
    }

    /**
     * This test ensures that our testing databases remain compatible with the migrations.xml we provide
     * In other words, running migration should properly result in no changes
     */
    @Test
    public void testDB1WithStandardMigration() throws Exception {
        clearDBandSetup1();
        RULE.getApplication().run("db", "migrate", ResourceHelpers.resourceFilePath("dockstoreTest.yml"));
    }

    @Test
    public void testDB2WithStandardMigration() throws Exception {
        clearDBandSetup2();
        RULE.getApplication().run("db", "migrate", ResourceHelpers.resourceFilePath("dockstoreTest.yml"));
    }

    /**
     * This test ensures that an actual new database change (like adding a new column) works properly
     * @throws Exception
     */
    @Test
    public void testDB1WithFunkyMigration() throws Exception {
        clearDBandSetup1();
        checkOnMigration();
    }

    private void checkOnMigration() throws Exception {

        RULE.getApplication().run("db", "migrate", ResourceHelpers.resourceFilePath("dockstoreTest.yml"), "--migrations", ResourceHelpers.resourceFilePath("funky_migrations.xml"));
        // check that column was added
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(funkfile) from tool", new ScalarHandler<>());
        // count will be zero, but there should be no exception
        Assert.assertTrue("could select from new column", count == 0);

        // reset state
       testingPostgres.runUpdateStatement("alter table tool drop funkfile");
    }

    @Test
    public void testDB2WithFunkyMigration() throws Exception {
        clearDBandSetup2();
        checkOnMigration();
    }
}
