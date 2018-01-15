package io.dockstore.client.cli.nested.NotifcationsClients;

/**
 * @author gluu
 * @since 12/01/18
 */
public interface Notifications {
    /**
     * Every client that implements Notifications must be able to send a message
     * @param hookURL   The hook URL of the resolved client
     * @param message   The complete message to be sent
     */
    void sendMessage(String hookURL, String message);
}
