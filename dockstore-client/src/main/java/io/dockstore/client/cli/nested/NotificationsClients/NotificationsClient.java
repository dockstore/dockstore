package io.dockstore.client.cli.nested.NotificationsClients;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import com.google.gson.Gson;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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
            String messageToSend = createText(message, success);
            // Message to be sent
            String jsonMessage;

            // This block figures out the message to send based on the hookURL. Can only figure it out if it's Slack
            // Otherwise, not sure what to send
            if (this.hookURL.contains("://hooks.slack.com")) {
                SlackMessage slackPojo = new SlackMessage();
                slackPojo.setText(messageToSend);
                Gson gson = new Gson();
                jsonMessage = gson.toJson(slackPojo);
            } else {
                LOG.debug("Can not resolve webhook URL");
                return;
            }
            // If there's a message, send it.
            if (jsonMessage != null && !jsonMessage.isEmpty()) {
                generalSendMessage(jsonMessage);
            }
        }
    }

    /**
     * Construct the fail/success text to send
     *
     * @param message The base text which does not include failure/success status
     * @param success Whether the step of the workflow/tool failed/succeeded
     * @return
     */
    private String createText(String message, boolean success) {
        String textToSend = uuid + ": " + message;
        if (!success) {
            textToSend = uuid + ": failed-" + message;
        }
        return textToSend;
    }

    /**
     * The general method of sending a message.
     * Currently works for Slack.  Whether it works for others depend on several assumptions:
     * 1. The message is send via HTTP POST
     * 2. The message is send with Content-type: application/json
     * 3. The message is a JSON
     * @param jsonMessage
     */
    private void generalSendMessage(String jsonMessage) {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(this.hookURL);

        StringEntity entity = null;
        try {
            entity = new StringEntity(jsonMessage);
        } catch (UnsupportedEncodingException e) {
            LOG.error("Cannot encode json");
        }
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        try {
            CloseableHttpResponse response = client.execute(httpPost);
            client.close();
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                LOG.warn("Did not successfully send notification");
            }
        } catch (IOException e) {
            LOG.error("Cannot send jsonMessage");
        }
    }
}
