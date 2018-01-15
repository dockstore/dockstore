package io.dockstore.client.cli.nested.NotifcationsClients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gluu
 * @since 12/01/18
 */
public class NotificationsClient {
    public static final String PROVISION_INPUT = "provision-in";
    public static final String RUN = "workflow-start";
    public static final String PROVISION_OUTPUT = "provision-out";
    public static final String COMPLETED = "workflow-complete";
    protected static final String USERNAME = "Dockstore CLI";
    private static final Logger LOG = LoggerFactory.getLogger(NotificationsClient.class);
    protected String hookURL;
    protected String uuid;


    public NotificationsClient(String hookURL, String uuid) {
        this.hookURL = hookURL;
        this.uuid = uuid;
    }

    /**
     * Send message using the webhook URL originally provided
     *
     * @param message Base message indicating the step of the tool/workflow
     * @param success The status of the step
     */
    public void sendMessage(String message, boolean success) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        } else if (hookURL == null || hookURL.isEmpty()) {
            System.err.println("Notifications UUID is specified but no notifications webhook URL found in config file");
        } else {
            LOG.debug("Sending notifications message");
            String messageToSend = createMessage(message, success);
            if (this.hookURL.contains("://hooks.slack.com")) {
                SlackClient slackClient = new SlackClient();
                slackClient.sendMessage(this.hookURL, messageToSend);
            } else {
                LOG.debug("Can not resolve webhook URL");
                return;
            }
        }
    }

    /**
     * Construct the fail/success message to send
     *
     * @param message The base message which does not include failure/success status
     * @param success Whether the step of the workflow/tool failed/succeeded
     * @return
     */
    private String createMessage(String message, boolean success) {
        String messageToSend = uuid + ": " + message;
        if (!success) {
            messageToSend = uuid + ": failed-" + message;
        }
        return messageToSend;
    }
}
