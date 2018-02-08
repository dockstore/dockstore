/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.consumer;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import io.dockstore.common.model.BasicMessage;
import io.dockstore.consumer.handler.DOIHandler;
import io.dockstore.consumer.handler.MessageHandler;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(EventConsumer.class);

    /**
     * the maximum wait time allowed
     */
    private static final int MAX_WAIT_TIME_SECONDS = 20;
    private PropertiesConfiguration config;

    private EventConsumer() {
        this.config = getConsumerConfiguration();
    }

    private PropertiesConfiguration getConsumerConfiguration() {
        if (this.config == null) {
            Configurations configs = new Configurations();
            // Read data from this file
            File propertiesFile = new File(System.getProperty("user.home"), ".dockstore/consumer.config");

            try {
                this.config = configs.properties(propertiesFile);
            } catch (ConfigurationException e) {
                throw new RuntimeException("Could not read ~/.dockstore/consumer.config", e);
            }
        }
        return this.config;
    }

    /**
     * This will be the future event handler loop
     *
     * @param args
     * @return
     */
    public static void main(String[] args) {

        EventConsumer consumer = new EventConsumer();
        String sqsURL = consumer.getConsumerConfiguration().getString("sqsURL");
        String dockstoreToken = consumer.getConsumerConfiguration().getString("dockstoreToken");
        String dockstoreURL = consumer.getConsumerConfiguration().getString("dockstoreURL");
        String zenodoToken = consumer.getConsumerConfiguration().getString("zenodoToken");
        String zenodoURL = consumer.getConsumerConfiguration().getString("zenodoURL");


        AmazonSQS sqs = AmazonSQSClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
        LOG.info("Receiving messages from MyFifoQueue.fifo");
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(sqsURL);
        // there's no indefinite wait
        receiveMessageRequest.setMessageAttributeNames(Lists.newArrayList(".*"));
        receiveMessageRequest.setWaitTimeSeconds(MAX_WAIT_TIME_SECONDS);
        receiveMessageRequest.setMaxNumberOfMessages(1);
        Gson gson = new Gson();

        DOIHandler doiHandler = new DOIHandler(dockstoreURL, dockstoreToken, zenodoURL, zenodoToken);
        Map<String, MessageHandler> messageHandlers = new HashMap<>();
        messageHandlers.put(doiHandler.messageTypeHandled(), doiHandler);

        do {
            List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
            for (Message message : messages) {
                LOG.debug("  Message");
                LOG.debug("    MessageId:     " + message.getMessageId());
                LOG.debug("    ReceiptHandle: " + message.getReceiptHandle());
                LOG.debug("    MD5OfBody:     " + message.getMD5OfBody());
                LOG.debug("    Body:          " + message.getBody());
                for (Entry<String, String> entry : message.getAttributes().entrySet()) {
                    LOG.debug("  Attribute");
                    LOG.debug("    Name:  " + entry.getKey());
                    LOG.debug("    Value: " + entry.getValue());
                }

                // in reality, get the workflow here and create a zenodo entry for it
                MessageHandler messageHandler = messageHandlers.get(message.getMessageAttributes().get("type").getStringValue());
                if (messageHandler != null) {
                    BasicMessage basicMessage = (BasicMessage)gson.fromJson(message.getBody(), messageHandler.messageClassHandled());
                    boolean handled = messageHandler.handleMessage(basicMessage);

                    String messageReceiptHandle = messages.get(0).getReceiptHandle();
                    if (handled) {
                        // Delete the message
                        LOG.info("Deleting the message");
                        sqs.deleteMessage(new DeleteMessageRequest(sqsURL, messageReceiptHandle));
                    } else {
                        // requeue the message
                        sqs.changeMessageVisibility(sqsURL, messageReceiptHandle, 0);
                    }
                }

            }
        } while (true);
    }
}
