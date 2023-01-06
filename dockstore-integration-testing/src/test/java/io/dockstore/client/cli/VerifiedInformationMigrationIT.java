package io.dockstore.client.cli;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.TestingPostgres;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.Application;
import io.dropwizard.testing.DropwizardTestSupport;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * @author gluu
 * @since 19/07/18
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
class VerifiedInformationMigrationIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
    protected static TestingPostgres testingPostgres;

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

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

    @Test
    void toolVerifiedInformationMigrationTest() {
        Application<DockstoreWebserviceConfiguration> application = SUPPORT.getApplication();
        try {
            application.run("db", "drop-all", "--confirm-delete-everything", CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
            List<String> migrationList = Arrays.asList("1.3.0.generated", "1.3.1.consistency", "test", "1.4.0");
            CommonTestUtilities.runMigration(migrationList, application, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
        } catch (Exception e) {
            Assertions.fail("Could not run migrations up to 1.4.0");
        }

        testingPostgres.runUpdateStatement("UPDATE tag SET verified='t' where name='fakeName'");

        // Run full 1.5.0 migration
        try {
            List<String> migrationList = List.of("1.5.0");
            CommonTestUtilities.runMigration(migrationList, application, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
        } catch (Exception e) {
            Assertions.fail("Could not run 1.5.0 migration");
        }

        final long afterMigrationVerifiedCount = testingPostgres.runSelectStatement("select count(*) from sourcefile_verified", long.class);
        Assertions
            .assertEquals(5, afterMigrationVerifiedCount, "There should be 2 entries in sourcefile_verified after the migration but got: " + afterMigrationVerifiedCount);
    }
}
