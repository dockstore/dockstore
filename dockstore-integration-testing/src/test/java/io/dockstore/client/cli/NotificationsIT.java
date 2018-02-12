package io.dockstore.client.cli;

import java.io.IOException;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.IntegrationTest;
import io.dockstore.common.TestUtility;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.Assertion;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

/**
 * @author gluu
 * @since 16/01/18
 */
@Category(IntegrationTest.class)
public class NotificationsIT extends BaseIT {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();
    private final static String sampleCWLDescriptor = ResourceHelpers.resourceFilePath("dockstore-tool-helloworld.cwl");
    private final static String sampleWDLDescriptor = ResourceHelpers.resourceFilePath("wdl.wdl");
    private final static String sampleCWLTestJson = "https://raw.githubusercontent.com/ga4gh/dockstore/f343bcd6e4465a8ef790208f87740bd4d5a9a4da/dockstore-client/src/test/resources/test.cwl.json";
    private final static String sampleWDLTestJson = ResourceHelpers.resourceFilePath("wdl.json");
    private final static String SLACK_DESTINATION = "Destination is Slack. Message is not 100% compatible.";
    private final static String SENDING_NOTIFICATION = "Sending notifications message.";
    private final static String GENERATING_UUID = "The UUID generated for this specific execution is ";

    public void clearLogs() {
        systemOutRule.clearLog();
        systemErrRule.clearLog();
    }

    @Override
    @Before
    public void resetDBBetweenTests() throws Exception {
        clearLogs();
    }

    /**
     * Tests if an error is displayed when UUID is specified with no webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchCWLToolWithNotificationsUUIDNoURL() throws IOException {
        exit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        exit.checkAssertionAfterwards(() -> {
            String log = systemErrRule.getLog();
            Assert.assertTrue(log, log.contains("Aborting launch."));
        });
        Client.main(
                new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--local-entry", sampleCWLDescriptor, "--json",
                        sampleCWLTestJson, "--uuid", "potato", "--debug" });
    }

    /**
     * Tests if debug message is displayed when UUID is specified but with an invalid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchCWLToolWithNotificationsUUIDInvalidURL() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocationWithInvalidNotifications(true), "tool", "launch",
                "--local-entry", sampleCWLDescriptor, "--json", sampleCWLTestJson, "--uuid", "potato", "--debug" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains(SENDING_NOTIFICATION));
        Assert.assertFalse(log, log.contains(SLACK_DESTINATION));
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
                        sampleCWLDescriptor, "--json", sampleCWLTestJson, "--uuid", "potato", "--debug" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains(SENDING_NOTIFICATION));
        Assert.assertTrue(log, log.contains(SLACK_DESTINATION));
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
                        sampleCWLDescriptor, "--json", sampleCWLTestJson, "--debug" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains(SENDING_NOTIFICATION));
        Assert.assertTrue(log, log.contains(GENERATING_UUID));
        Assert.assertTrue(log, log.contains(SLACK_DESTINATION));
    }

    // WDL TESTS

    /**
     * Tests if an error is displayed when UUID is specified with no webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchWDLToolWithNotificationsUUIDNoURL() throws IOException {
        exit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        exit.checkAssertionAfterwards(() -> {
            String log = systemErrRule.getLog();
            Assert.assertTrue(log, log.contains("Aborting launch."));
        });
        Client.main(
                new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--local-entry", sampleWDLDescriptor,
                        "--json", sampleWDLTestJson, "--uuid", "potato" });


    }

    /**
     * Tests if debug message is displayed when UUID is specified but with an invalid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchWDLToolWithNotificationsUUIDInvalidURL() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocationWithInvalidNotifications(true), "tool", "launch",
                "--local-entry", sampleWDLDescriptor, "--json", sampleWDLTestJson, "--uuid", "potato", "--debug" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains(SENDING_NOTIFICATION));
        Assert.assertFalse(log, log.contains(SLACK_DESTINATION));
    }

    /**
     * Tests if no debug message is displayed when UUID is specified with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchWDLToolWithNotificationsUUIDValidURL() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocationWithValidNotifications(true), "tool", "launch",
                "--local-entry", sampleWDLDescriptor, "--json", sampleWDLTestJson, "--uuid", "potato", "--debug" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains(SENDING_NOTIFICATION));
        Assert.assertTrue(log, log.contains(SLACK_DESTINATION));
    }

    /**
     * Tests if nothing relevant is displayed when UUID is not specified but with a valid webhook URL
     *
     * @throws IOException
     */
    @Test
    public void launchWDLToolWithNotificationsNoUUIDValidURL() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocationWithValidNotifications(true), "tool", "launch",
                "--local-entry", sampleWDLDescriptor, "--json", sampleWDLTestJson, "--debug" });
        String log = systemOutRule.getLog();
        Assert.assertTrue(log, log.contains(GENERATING_UUID));
        Assert.assertTrue(log, log.contains(SENDING_NOTIFICATION));
        Assert.assertTrue(log, log.contains(SLACK_DESTINATION));
    }
}
