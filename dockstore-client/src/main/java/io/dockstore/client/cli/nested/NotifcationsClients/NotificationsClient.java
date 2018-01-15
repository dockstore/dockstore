package io.dockstore.client.cli.nested.NotifcationsClients;

/**
 * @author gluu
 * @since 12/01/18
 */
public class NotificationsClient {
    public static final String PROVISION_INPUT = "Provisioning your input files to your local machine";
    public static final String RUN = "Provisioning your input files to your local machine";
    public static final String PROVISION_OUTPUT = "Provisioning your output files to their final destinations";
    public static final String COMPLETED = "Tool/Workflow launch completed";
    protected static final String USERNAME = "Dockstore CLI";
    protected String hookURL;
    protected String uuid;

    public NotificationsClient(String hookURL, String uuid) {
        this.hookURL = hookURL;
        this.uuid = uuid;
    }

    public void sendMessage(String message) {
        if (hookURL.isEmpty() || hookURL == null || uuid.isEmpty() || uuid == null) {
            return;
        } else {
            if (this.hookURL.contains("://hooks.slack.com")) {
                SlackClient slackClient = new SlackClient();
                slackClient.sendMessage(this.hookURL, message);
            } else {
                return;
            }
        }
    }
}
