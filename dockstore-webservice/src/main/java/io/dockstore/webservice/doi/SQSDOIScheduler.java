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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.dockstore.common.model.DOIMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class SQSDOIScheduler implements DOIGeneratorInterface {

    private static final Logger LOG = LoggerFactory.getLogger(SQSDOIScheduler.class);
    private final String sqsURL;
    private final Gson gson = new GsonBuilder().create();

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
        final SqsClient sqs = SqsClient.builder().region(Region.US_EAST_1).build();
        // Send a message
        LOG.info("Sending a message to MyFifoQueue.fifo");

        DOIMessage message = new DOIMessage();
        message.setTargetEntry(messageType);
        message.setEntryId(id);
        message.setEntryVersionId(versionId);
        // You must provide a non-empty MessageGroupId when sending messages to a FIFO queue
        final SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
            .queueUrl(sqsURL).messageBody(gson.toJson(message)).messageGroupId("messageGroup1")
            .build();
        final MessageAttributeValue attributeValue = MessageAttributeValue.builder().stringValue(message.getClass().getName()).build();
        sendMessageRequest.messageAttributes().put("type", attributeValue);
        final SendMessageResponse sendMessageResponse = sqs.sendMessage(sendMessageRequest);
        String sequenceNumber = sendMessageResponse.sequenceNumber();
        String messageId = sendMessageResponse.messageId();
        LOG.info("SendMessage succeed with messageId " + messageId + ", sequence number " + sequenceNumber);
    }

}
