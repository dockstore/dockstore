/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.Workflow.TopicSelectionEnum;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import org.hibernate.Session;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
class OpenAPIGeneralWorkflowIT extends BaseIT {
    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();


    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();

        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    void testAddingWorkflowForumUrlAndTopic() throws io.swagger.client.ApiException {
        // Set up webservice
        ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        Workflow workflow = workflowsApi
                .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/test_lastmodified", "/Dockstore.cwl",
                        "test-update-workflow", DescriptorLanguage.CWL.toString(),
                        "/test.json");

        assertEquals(TopicSelectionEnum.AUTOMATIC, workflow.getTopicSelection(), "Should default to automatic");

        //update the forumUrl to hello.com
        final String newTopic = "newTopic";
        workflow.setForumUrl("hello.com");
        workflow.setTopicManual(newTopic);
        workflow.setTopicSelection(TopicSelectionEnum.MANUAL);
        Workflow updatedWorkflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);

        //check the workflow's forumUrl is hello.com
        final String updatedForumUrl = testingPostgres
                .runSelectStatement("select forumurl from workflow where workflowname = 'test-update-workflow'", String.class);
        assertEquals("hello.com", updatedForumUrl, "forumUrl should be updated, it is " + updatedForumUrl);

        assertEquals(newTopic, updatedWorkflow.getTopicManual());
        assertEquals(TopicSelectionEnum.MANUAL, updatedWorkflow.getTopicSelection());

        // Set workflow's topicSelection to AI
        testingPostgres.runUpdateStatement("update workflow set topicai = 'AI topic' where id = " + workflow.getId());
        assertFalse(workflow.isApprovedAITopic());
        workflow.setTopicSelection(TopicSelectionEnum.AI);
        updatedWorkflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        assertEquals("AI topic", updatedWorkflow.getTopicAI());
        assertEquals(TopicSelectionEnum.AI, updatedWorkflow.getTopicSelection());
        assertTrue(workflow.isApprovedAITopic());
    }
}
