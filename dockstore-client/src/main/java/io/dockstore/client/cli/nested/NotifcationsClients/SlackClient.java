package io.dockstore.client.cli.nested.NotifcationsClients;

import net.gpedro.integrations.slack.SlackApi;
import net.gpedro.integrations.slack.SlackMessage;

import static io.dockstore.client.cli.nested.NotifcationsClients.NotificationsClient.USERNAME;

/**
 * @author gluu
 * @since 12/01/18
 */
public final class SlackClient implements Notifications {
    public void sendMessage(String hookURL, String message) {
        SlackApi api = new SlackApi(hookURL);
        api.call(new SlackMessage(USERNAME, message));
    }
}
