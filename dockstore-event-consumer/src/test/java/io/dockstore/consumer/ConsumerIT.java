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

import cloud.localstack.TestUtils;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import org.junit.Assert;
import org.junit.Test;

public class ConsumerIT {
    @Test
    public void testLocalstackFunctional() {
        AmazonSQS clientSQS = TestUtils.getClientSQS();
        CreateQueueResult funkyQueue = clientSQS.createQueue("funkyQueue");
        Assert.assertTrue(!funkyQueue.getQueueUrl().isEmpty());
        String funkyQueueURL = clientSQS.getQueueUrl("funkyQueue").getQueueUrl();
        clientSQS.sendMessage(funkyQueueURL,"messageBody");
        Assert.assertTrue(clientSQS.receiveMessage(funkyQueueURL).getMessages().size() > 0);
    }

    //TODO: we need tests from the web service side (hit the request DOI endpoints and it sends something into localstack)
    //TODO: we need tests that start from a message on localstack
}
