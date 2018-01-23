package io.dockstore.client.cli.nested.NotificationsClients;

/**
 * Message structure to be sent
 *
 * @author gluu
 * @since 19/01/18
 */
public class Message {
    // Slack keyword
    String text;
    // Slack keyword
    String username = System.getProperty("user.name");
    String uuid;
    String platform = "Dockstore CLI " + Message.class.getPackage().getImplementationVersion();

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
