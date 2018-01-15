package io.dockstore.client.cli.nested.NotifcationsClients;

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
    protected String hookURL;
    protected String uuid;

    public NotificationsClient(String hookURL, String uuid) {
        this.hookURL = hookURL;
        this.uuid = uuid;
    }

    public void sendMessage(String message, boolean success) {
        if (hookURL == null || hookURL.isEmpty() || uuid == null || uuid.isEmpty()) {
            return;
        } else {
            String messageToSend = createMessage(message, success);
            if (this.hookURL.contains("://hooks.slack.com")) {
                SlackClient slackClient = new SlackClient();
                slackClient.sendMessage(this.hookURL, messageToSend);
            } else {
                return;
            }
        }
    }

    private String createMessage(String message, boolean success) {
        String messageToSend = uuid + ": " + message;
        if (!success) {
            messageToSend = uuid + ": failed-" + message;
        }
        return messageToSend;
    }
}
