package io.dockstore.webservice;

import static io.dockstore.common.CommonTestUtilities.getWebClient;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
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
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * This integration test provides support to make testing different webservice configurations easier.
 */
@Category(ConfidentialTest.class)
public class ConfigurationIT {

    private static final String DROPWIZARD_CONFIGURATION_FILE_PATH = CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH;
    private static final String VANILLA_SOURCEFILE_PATH = "/some-unique_file.json";

    private static AtomicLong id = new AtomicLong();
    private static TestingPostgres testingPostgres;
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };

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
        Workflow hostedWorkflow = api.createHostedWorkflow("awesomeWorkflow" + id.incrementAndGet(), null, DescriptorLanguage.CWL.getShortName(), null, null);
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
        Assert.assertEquals("should find sourcefile with path " + path, 1L, countSourceFilesWithPath(path));
    }

    private void testBadSourceFilePath(String path) {
        try {
            createWorkflow(path);
            Assert.fail("should have thrown");
        } catch (ApiException e) {
            // expected execution path
        }
        Assert.assertEquals("should not find sourcefile with path " + path, 0L, countSourceFilesWithPath(path));
    }

    @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
    private List<String> getSomeWeirdPaths() {
        return List.of("\t", "`", "$", "@", "\u00f1", "\u1200").stream().map(w -> ("/" + w + VANILLA_SOURCEFILE_PATH)).collect(Collectors.toList());
    }

    @Test
    public void testSourceFilePathsDefaultConfiguration() throws Exception {
        runWithSupport(createSupport(), () -> {
            testGoodSourceFilePath(VANILLA_SOURCEFILE_PATH);
            testGoodSourceFilePath("/ " + VANILLA_SOURCEFILE_PATH);
            getSomeWeirdPaths().forEach(this::testBadSourceFilePath);
        });
    }

    @Test
    public void testSourceFilePathsEffectivelyUnrestricted() throws Exception {
        runWithSupport(createSupport(ConfigOverride.config("sourceFilePathRegex", ".*")), () -> {
            testGoodSourceFilePath(VANILLA_SOURCEFILE_PATH);
            getSomeWeirdPaths().forEach(this::testGoodSourceFilePath);
        });
    }

    @Test
    public void testSourceFilePathsVeryTightlyRestricted() throws Exception {
        runWithSupport(createSupport(ConfigOverride.config("sourceFilePathRegex", "this regex literally only matches itself")), () -> {
            testBadSourceFilePath(VANILLA_SOURCEFILE_PATH);
            getSomeWeirdPaths().forEach(this::testBadSourceFilePath);
        });
    }
}
