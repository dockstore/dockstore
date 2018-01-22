package io.dockstore.client.cli.nested.NotificationsClients;

/**
 * Message structure based on what Slack accepts with the exception of the uuid
 *
 * @author gluu
 * @since 19/01/18
 */
public class Message {
    String text;

    // Slack specific property
    String username = "Dockstore CLI";
    String uuid;

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
