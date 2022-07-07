package io.dockstore.webservice;

import static io.dockstore.common.CommonTestUtilities.getWebClient;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.common.TestingPostgres;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.api.WorkflowsApi;
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
        CommonTestUtilities.dropAndRecreateNoTestData(support);
        support.before();
        testingPostgres = new TestingPostgres(support);
        CommonTestUtilities.cleanStatePrivate2(support, false);
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

    private void registerWorkflow() {
        ApiClient webClient = getWebClient(true, BaseIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        workflowsApi.manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/md5sum-checker", "/md5sum/md5sum-workflow.wdl", "WDL", DescriptorLanguage.WDL.toString(), "/test.json");
    }

    @Test
    public void testRegisterDefaultConfiguration() throws Exception {
        runWithSupport(createSupport(), this::registerWorkflow);
        Assert.assertEquals(1L, countSourceFilesWithPath("/md5sum/md5sum-workflow.wdl"));
    }

    @Test
    public void testRegisterLooselyRestrictedSourceFilePaths() throws Exception {
        runWithSupport(createSupport(ConfigOverride.config("sourceFilePathRegex", ".*")), this::registerWorkflow);
        Assert.assertEquals(1L, countSourceFilesWithPath("/md5sum/md5sum-workflow.wdl"));
    }

    @Test
    public void testRegisterTightlyRestrictedSourceFilePaths() throws Exception {
        runWithSupport(createSupport(ConfigOverride.config("sourceFilePathRegex", "this regex literally only matches itself")), this::registerWorkflow);
        Assert.assertEquals(0L, countSourceFilesWithPath("/md5sum/md5sum-workflow.wdl"));
    }
}
