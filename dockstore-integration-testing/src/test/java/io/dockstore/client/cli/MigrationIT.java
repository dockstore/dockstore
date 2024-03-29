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

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.TestingPostgres;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Testing migration
 *
 * @author dyuen
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
class MigrationIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
    protected static TestingPostgres testingPostgres;

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @BeforeAll
    public static void dumpDBAndCreateSchema() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.getEnvironment().healthChecks().shutdown();
        SUPPORT.after();
    }

    /**
     * This test ensures that our testing databases remain compatible with the migrations.xml we provide
     * In other words, running migration again should be ok
     */
    @Test
    void testDB1WithNormalDatabase() throws Exception {
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
        SUPPORT.getApplication().run("db", "migrate", ResourceHelpers.resourceFilePath("dockstoreTest.yml"), "--include", "test");
    }

    @Test
    void testDB1WithStandardMigration() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
        SUPPORT.getApplication()
            .run("db", "migrate", ResourceHelpers.resourceFilePath("dockstoreTest.yml"), "--include", "test.confidential1");
    }

    @Test
    void testDB2WithStandardMigration() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        SUPPORT.getApplication()
            .run("db", "migrate", ResourceHelpers.resourceFilePath("dockstoreTest.yml"), "--include", "test.confidential2");
    }

    /**
     * This test ensures that an actual new database change (like adding a new column) works properly
     *
     * @throws Exception
     */
    @Test
    void testDB1WithFunkyMigration() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
        checkOnMigration();
    }

    private void checkOnMigration() throws Exception {

        SUPPORT.getApplication().run("db", "migrate", ResourceHelpers.resourceFilePath("dockstoreTest.yml"), "--migrations",
            ResourceHelpers.resourceFilePath("funky_migrations.xml"));
        // check that column was added
        final long count = testingPostgres.runSelectStatement("select count(funkfile) from tool", long.class);
        // count will be zero, but there should be no exception
        assertEquals(0, count, "could select from new column");
        final long orphanedTokensCount = testingPostgres
            .runSelectStatement("select count(*) from token where userid not in (select id from enduser)", long.class);
        assertEquals(0, orphanedTokensCount);
        // reset state
        testingPostgres.runUpdateStatement("alter table tool drop funkfile");
    }

    @Test
    void testDB2WithFunkyMigration() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        checkOnMigration();
    }
}
