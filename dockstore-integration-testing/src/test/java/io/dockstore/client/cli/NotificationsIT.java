package io.dockstore.client.cli;

import java.io.IOException;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.TestUtility;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

/**
 * @author gluu
 * @since 16/01/18
 */
public class NotificationsIT extends BaseIT {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    final static String firstTool = ResourceHelpers.resourceFilePath("dockstore-tool-helloworld.cwl");
    final static String firstWorkflow = ResourceHelpers.resourceFilePath("wdl.wdl");
    final static String firstToolJSON = "https://raw.githubusercontent.com/ga4gh/dockstore/f343bcd6e4465a8ef790208f87740bd4d5a9a4da/dockstore-client/src/test/resources/test.cwl.json";
    final static String firstWorkflowJSON = ResourceHelpers.resourceFilePath("wdl.json");

    @After
    public void clearLogs() {
        systemOutRule.clearLog();
        systemErrRule.clearLog();
    }

    @Override
    @Before
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanState(SUPPORT);
        clearLogs();
    }

    /**
     * Tests if an error is displayed when UUID is specified with no webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchCWLToolWithNotificationsUUIDNoURL() throws IOException {
        Client.main(
                new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--local-entry", firstTool, "--json",
                        firstToolJSON, "--uuid", "potato", "--debug" });
        String log = systemErrRule.getLog();
        Assert.assertTrue(log, log.contains("Notifications UUID is specified but no notifications webhook URL found in config file"));
    }

    /**
     * Tests if debug message is displayed when UUID is specified but with an invalid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchCWLToolWithNotificationsUUIDInvalidURL() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocationWithInvalidNotifications(true), "tool", "launch",
                "--local-entry", firstTool, "--json", firstToolJSON, "--uuid", "potato", "--debug" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains("Sending notifications message"));
        Assert.assertTrue(log, log.contains("Can not resolve webhook URL"));
    }

    /**
     * Tests if no debug message is displayed when UUID is specified with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchCWLToolWithNotificationsUUIDValidURL() throws IOException {
        Client.main(
                new String[] { "--config", TestUtility.getConfigFileLocationWithValidNotifications(true), "tool", "launch", "--local-entry",
                        firstTool, "--json", firstToolJSON, "--uuid", "potato", "--debug" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains("Sending notifications message"));
        Assert.assertTrue(log, !log.contains("Can not resolve webhook URL"));
    }

    /**
     * Tests if nothing relevant is displayed when UUID is not specified but with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchCWLToolWithNotificationsNoUUIDValidURL() throws IOException {
        Client.main(
                new String[] { "--config", TestUtility.getConfigFileLocationWithValidNotifications(true), "tool", "launch", "--local-entry",
                        firstTool, "--json", firstToolJSON, "--debug" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, !log.contains("Sending notifications message"));
        Assert.assertTrue(log, !log.contains("Can not resolve webhook URL"));
    }

    // WDL TESTS

    /**
     * Tests if an error is displayed when UUID is specified with no webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchWDLToolWithNotificationsUUIDNoURL() throws IOException {
        Client.main(
                new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--local-entry", firstWorkflow,
                        "--json", firstWorkflowJSON, "--uuid", "potato", "--debug" });
        String log = systemErrRule.getLog();
        Assert.assertTrue(log, log.contains("Notifications UUID is specified but no notifications webhook URL found in config file"));
    }

    /**
     * Tests if debug message is displayed when UUID is specified but with an invalid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchWDLToolWithNotificationsUUIDInvalidURL() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocationWithInvalidNotifications(true), "tool", "launch",
                "--local-entry", firstWorkflow, "--json", firstWorkflowJSON, "--uuid", "potato", "--debug" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains("Sending notifications message"));
        Assert.assertTrue(log, log.contains("Can not resolve webhook URL"));
    }

    /**
     * Tests if no debug message is displayed when UUID is specified with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchWDLToolWithNotificationsUUIDValidURL() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocationWithValidNotifications(true), "tool", "launch",
                "--local-entry", firstWorkflow, "--json", firstWorkflowJSON, "--uuid", "potato", "--debug" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains("Sending notifications message"));
        Assert.assertTrue(log, !log.contains("Can not resolve webhook URL"));
    }

    /**
     * Tests if nothing relevant is displayed when UUID is not specified but with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchWDLToolWithNotificationsNoUUIDValidURL() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocationWithValidNotifications(true), "tool", "launch",
                "--local-entry", firstWorkflow, "--json", firstWorkflowJSON, "--debug" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, !log.contains("Sending notifications message"));
        Assert.assertTrue(log, !log.contains("Can not resolve webhook URL"));
    }
}
