package io.dockstore.client.cli.nested.NotifcationsClients;

/**
 * @author gluu
 * @since 12/01/18
 */
public interface Notifications {
    void sendMessage(String hookURL, String message);
}
