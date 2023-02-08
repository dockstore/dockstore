package io.dockstore.webservice;

import static io.dockstore.common.CommonTestUtilities.getWebClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.TestingPostgres;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.HostedApi;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Workflow;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * This integration test provides support to make testing different webservice configurations easier.
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class ConfigurationIT {

    private static final String DROPWIZARD_CONFIGURATION_FILE_PATH = CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH;
    private static final String VANILLA_SOURCEFILE_PATH = "/some-unique_file.json";

    private static final AtomicLong ID = new AtomicLong();
    private static TestingPostgres testingPostgres;

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private void before(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws Exception {
        CommonTestUtilities.cleanStatePrivate2(support, true, testingPostgres);
        support.before();
        testingPostgres = new TestingPostgres(support);
    }

    private void after(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws Exception {
        support.getEnvironment().healthChecks().shutdown();
        support.after();
        BaseIT.assertNoMetricsLeaks(support);
    }

    /**
     * Create a Dropwizard test support object with the specified configuration overrides.
     */
    private DropwizardTestSupport<DockstoreWebserviceConfiguration> createSupport(ConfigOverride... overrides) {
        return new DropwizardTestSupport<DockstoreWebserviceConfiguration>(DockstoreWebserviceApplication.class, DROPWIZARD_CONFIGURATION_FILE_PATH, overrides);
    }

    /**
     * Run the specified Runnable in the environment corrsponding to the supplied Dropwizard test support object.
     */
    private void runWithSupport(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, Runnable runnable) throws Exception {
        before(support);
        try {
            runnable.run();
        } finally {
            after(support);
        }
    }

    private long countSourceFilesWithPath(String path) {
        // this isn't the most robust way to run a SELECT, but it's good enough for the purposes of this test.
        return testingPostgres.runSelectStatement(String.format("select count(*) from sourcefile where path = '%s' or absolutepath = '%s'", path, path), long.class);
    }

    private void createWorkflow(String sourceFilePath) {
        ApiClient webClient = getWebClient(true, BaseIT.ADMIN_USERNAME, testingPostgres);
        HostedApi api = new HostedApi(webClient);
        Workflow hostedWorkflow = api.createHostedWorkflow("awesomeWorkflow" + ID.incrementAndGet(), null, DescriptorLanguage.CWL.getShortName(), null, null);
        SourceFile file = new SourceFile();
        file.setContent("cwlVersion: v1.0\nclass: Workflow");
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        file.setAbsolutePath("/Dockstore.cwl");
        SourceFile file2 = new SourceFile();
        file2.setContent("{}");
        file2.setType(SourceFile.TypeEnum.CWL_TEST_JSON);
        file2.setPath(sourceFilePath);
        file2.setAbsolutePath(sourceFilePath);
        api.editHostedWorkflow(hostedWorkflow.getId(), List.of(file, file2));
    }

    private void testGoodSourceFilePath(String path) {
        createWorkflow(path);
        assertEquals(1L, countSourceFilesWithPath(path), "should find sourcefile with path " + path);
    }

    private void testBadSourceFilePath(String path) {
        try {
            createWorkflow(path);
            fail("should have thrown");
        } catch (ApiException e) {
            // expected execution path
        }
        assertEquals(0L, countSourceFilesWithPath(path), "should not find sourcefile with path " + path);
    }

    @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
    private List<String> getSomeWeirdPaths() {
        return Stream.of("\t", "`", "$", "@", "ñ", "ሀ").map(w -> ("/" + w + VANILLA_SOURCEFILE_PATH)).collect(Collectors.toList());
    }

    @Test
    void testSourceFilePathsDefaultConfiguration() throws Exception {
        runWithSupport(createSupport(), () -> {
            testGoodSourceFilePath(VANILLA_SOURCEFILE_PATH);
            testGoodSourceFilePath("/ " + VANILLA_SOURCEFILE_PATH);
            getSomeWeirdPaths().forEach(this::testBadSourceFilePath);
        });
    }

    @Test
    void testSourceFilePathsEffectivelyUnrestricted() throws Exception {
        runWithSupport(createSupport(ConfigOverride.config("sourceFilePathRegex", ".*")), () -> {
            testGoodSourceFilePath(VANILLA_SOURCEFILE_PATH);
            getSomeWeirdPaths().forEach(this::testGoodSourceFilePath);
        });
    }

    @Test
    void testSourceFilePathsVeryTightlyRestricted() throws Exception {
        runWithSupport(createSupport(ConfigOverride.config("sourceFilePathRegex", "this regex literally only matches itself")), () -> {
            testBadSourceFilePath(VANILLA_SOURCEFILE_PATH);
            getSomeWeirdPaths().forEach(this::testBadSourceFilePath);
        });
    }
}
