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
package io.dockstore.webservice.doi;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.dockstore.common.model.DOIMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SQSDOIScheduler implements DOIGeneratorInterface {

    private static final Logger LOG = LoggerFactory.getLogger(SQSDOIScheduler.class);
    private String sqsURL;
    private Gson gson = new GsonBuilder().create();

    SQSDOIScheduler(String sqsURL) {
        this.sqsURL = sqsURL;
    }

    @Override
    public void createDOIForWorkflow(long workflowId, long workflowVersionId) {
        // TODO: is there a suitable enum for this?
        sendMessage("workflow", workflowId, workflowVersionId);
    }

    @Override
    public void createDOIForTool(long toolId, long toolVersionId) {
        // TODO: is there a suitable enum for this?
        sendMessage("tool", toolId, toolVersionId);
    }

    private void sendMessage(String messageType, long id, long versionId) {
        if (sqsURL == null) {
            LOG.error("Unable to send out a DOI message because the sqsURL was invalid");
            return;
        }
        AmazonSQS sqs = AmazonSQSClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
        // Send a message
        LOG.info("Sending a message to MyFifoQueue.fifo");

        DOIMessage message = new DOIMessage();
        message.setTargetEntry(messageType);
        message.setEntryId(id);
        message.setEntryVersionId(versionId);

        SendMessageRequest sendMessageRequest = new SendMessageRequest(
            sqsURL, gson.toJson(message));
        sendMessageRequest.addMessageAttributesEntry("type", new MessageAttributeValue().withDataType("String").withStringValue(message.getClass().getName()));
        // You must provide a non-empty MessageGroupId when sending messages to a FIFO queue
        sendMessageRequest.setMessageGroupId("messageGroup1");
        SendMessageResult sendMessageResult = sqs.sendMessage(sendMessageRequest);
        String sequenceNumber = sendMessageResult.getSequenceNumber();
        String messageId = sendMessageResult.getMessageId();
        LOG.info("SendMessage succeed with messageId " + messageId + ", sequence number " + sequenceNumber);
    }

}
