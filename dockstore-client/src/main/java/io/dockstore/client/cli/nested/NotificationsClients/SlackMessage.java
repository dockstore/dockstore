package io.dockstore.client.cli.nested.NotificationsClients;

/**
 * @author gluu
 * @since 19/01/18
 */
public class SlackMessage {
    String text;
    String username = "Dockstore CLI";

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
