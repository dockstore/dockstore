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
import static org.junit.jupiter.api.Assertions.assertSame;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Registry;
import io.dockstore.common.ToolTest;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.HostedApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.DockstoreTool.TopicSelectionEnum;
import io.dockstore.openapi.client.model.Workflow;
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

/**
 * Extra confidential integration tests, don't rely on the type of repository used (Github, Dockerhub, Quay.io, Bitbucket).
 * Uses OpenAPI client classes.
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
@Tag(ToolTest.NAME)
class OpenAPIGeneralIT extends BaseIT {
    private static final String DOCKERHUB_TOOL_PATH = "registry.hub.docker.com/testPath/testUpdatePath/test5";

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();
    private Session session;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();

        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.addAdditionalToolsWithPrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    void testEditingHostedWorkflowTopics() {
        final ApiClient openApiWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final HostedApi hostedApi = new HostedApi(openApiWebClient);
        final Workflow hostedWorkflow = hostedApi.createHostedWorkflow(null, "foo", DescriptorLanguage.WDL.toString(), null, null);
        hostedWorkflow.setTopicManual("new foo");
        hostedWorkflow.setTopicAI("AI topic");
        assertSame(Workflow.TopicSelectionEnum.MANUAL, hostedWorkflow.getTopicSelection());
        hostedWorkflow.setTopicSelection(Workflow.TopicSelectionEnum.AUTOMATIC);
        WorkflowsApi workflowsApi = new WorkflowsApi(openApiWebClient);
        Workflow workflow = workflowsApi.updateWorkflow(hostedWorkflow.getId(), hostedWorkflow);
        // topic should change
        assertEquals("new foo", workflow.getTopic());
        assertEquals("AI topic", workflow.getTopicAI(), "AI topic should be updated");
        // but should ignore automatic selection change
        assertEquals(Workflow.TopicSelectionEnum.MANUAL, workflow.getTopicSelection());

        // Hosted workflow should be able to select AI topic
        hostedWorkflow.setTopicSelection(Workflow.TopicSelectionEnum.AI);
        workflow = workflowsApi.updateWorkflow(hostedWorkflow.getId(), hostedWorkflow);
        assertEquals(Workflow.TopicSelectionEnum.AI, workflow.getTopicSelection());
        assertEquals("AI topic", workflow.getTopic());
    }

    @Test
    void testEditingHostedToolTopics() {
        final ApiClient openApiWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final HostedApi hostedApi = new HostedApi(openApiWebClient);
        final DockstoreTool hostedTool = hostedApi.createHostedTool(Registry.QUAY_IO.getDockerPath().toLowerCase(), "foo", DescriptorLanguage.WDL.toString(), null, null);
        hostedTool.setTopicManual("new foo");
        hostedTool.setTopicAI("AI topic");
        assertSame(DockstoreTool.TopicSelectionEnum.MANUAL, hostedTool.getTopicSelection());
        hostedTool.setTopicSelection(DockstoreTool.TopicSelectionEnum.AUTOMATIC);
        ContainersApi containersApi = new ContainersApi(openApiWebClient);
        DockstoreTool dockstoreTool = containersApi.updateContainer(hostedTool.getId(), hostedTool);
        // topic should change
        assertEquals("new foo", dockstoreTool.getTopic());
        assertEquals("AI topic", dockstoreTool.getTopicAI());
        // but should ignore automatic selection change
        assertEquals(DockstoreTool.TopicSelectionEnum.MANUAL, dockstoreTool.getTopicSelection());

        // Should allow AI selection change
        hostedTool.setTopicSelection(DockstoreTool.TopicSelectionEnum.AI);
        dockstoreTool = containersApi.updateContainer(hostedTool.getId(), hostedTool);
        assertEquals(DockstoreTool.TopicSelectionEnum.AI, dockstoreTool.getTopicSelection());
        assertEquals("AI topic", dockstoreTool.getTopic());
    }

    /**
     * Test to update the tool's forum, topic, and topic selection and it should change the in the database
     *
     */
    @Test
    void testUpdateToolForumUrlAndTopic() {
        final String forumUrl = "hello.com";
        //setup webservice and get tool api
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolsApi = new ContainersApi(webClient);

        DockstoreTool toolTest = toolsApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);
        toolsApi.refresh(toolTest.getId());

        assertEquals(TopicSelectionEnum.AUTOMATIC, toolTest.getTopicSelection(), "Should default to automatic");

        //change the forumurl
        toolTest.setForumUrl(forumUrl);
        final String newTopic = "newTopic";
        toolTest.setTopicManual(newTopic);
        toolTest.setTopicSelection(TopicSelectionEnum.MANUAL);
        DockstoreTool dockstoreTool = toolsApi.updateContainer(toolTest.getId(), toolTest);

        //check the tool's forumurl is updated in the database
        final String updatedForumUrl = testingPostgres.runSelectStatement("select forumurl from tool where id = " + toolTest.getId(), String.class);
        assertEquals(forumUrl, updatedForumUrl, "the forumurl should be hello.com");

        // check the tool's topicManual and topicSelection
        assertEquals(newTopic, dockstoreTool.getTopicManual());
        assertEquals(TopicSelectionEnum.MANUAL, toolTest.getTopicSelection());

        // Set tool's topicAI and topicSelection to AI
        toolTest.setTopicAI("AI topic");
        toolTest.setTopicSelection(TopicSelectionEnum.AI);
        dockstoreTool = toolsApi.updateContainer(toolTest.getId(), toolTest);
        assertEquals("AI topic", dockstoreTool.getTopicAI());
        assertEquals(TopicSelectionEnum.AI, dockstoreTool.getTopicSelection());
    }
}
