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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.common.ToolTest;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.EntriesApi;
import io.dockstore.openapi.client.api.HostedApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.DockstoreTool.TopicSelectionEnum;
import io.dockstore.openapi.client.model.Entry;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import org.apache.http.HttpStatus;
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
        assertSame(Workflow.TopicSelectionEnum.MANUAL, hostedWorkflow.getTopicSelection());
        hostedWorkflow.setTopicSelection(Workflow.TopicSelectionEnum.AUTOMATIC);
        WorkflowsApi workflowsApi = new WorkflowsApi(openApiWebClient);
        Workflow workflow = workflowsApi.updateWorkflow(hostedWorkflow.getId(), hostedWorkflow);
        // topic should change
        assertEquals("new foo", workflow.getTopic());
        // but should ignore automatic selection change
        assertEquals(Workflow.TopicSelectionEnum.MANUAL, workflow.getTopicSelection());

        // Hosted workflow should be able to select AI topic
        testingPostgres.runUpdateStatement("update workflow set topicai = 'AI topic' where id = " + workflow.getId());
        assertFalse(workflow.isApprovedAITopic());
        hostedWorkflow.setTopicSelection(Workflow.TopicSelectionEnum.AI);
        hostedWorkflow.setApprovedAITopic(true);
        workflow = workflowsApi.updateWorkflow(hostedWorkflow.getId(), hostedWorkflow);
        assertEquals(Workflow.TopicSelectionEnum.AI, workflow.getTopicSelection());
        assertTrue(workflow.isApprovedAITopic());
        assertEquals("AI topic", workflow.getTopic());
    }

    @Test
    void testEditingHostedToolTopics() {
        final ApiClient openApiWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final HostedApi hostedApi = new HostedApi(openApiWebClient);
        final DockstoreTool hostedTool = hostedApi.createHostedTool(Registry.QUAY_IO.getDockerPath().toLowerCase(), "foo", DescriptorLanguage.WDL.toString(), null, null);
        hostedTool.setTopicManual("new foo");
        assertSame(DockstoreTool.TopicSelectionEnum.MANUAL, hostedTool.getTopicSelection());
        hostedTool.setTopicSelection(DockstoreTool.TopicSelectionEnum.AUTOMATIC);
        ContainersApi containersApi = new ContainersApi(openApiWebClient);
        DockstoreTool dockstoreTool = containersApi.updateContainer(hostedTool.getId(), hostedTool);
        // topic should change
        assertEquals("new foo", dockstoreTool.getTopic());
        // but should ignore automatic selection change
        assertEquals(DockstoreTool.TopicSelectionEnum.MANUAL, dockstoreTool.getTopicSelection());

        // Should allow AI selection change
        testingPostgres.runUpdateStatement("update tool set topicai = 'AI topic' where id = " + hostedTool.getId());
        assertFalse(hostedTool.isApprovedAITopic());
        hostedTool.setTopicSelection(DockstoreTool.TopicSelectionEnum.AI);
        hostedTool.setApprovedAITopic(true);
        dockstoreTool = containersApi.updateContainer(hostedTool.getId(), hostedTool);
        assertEquals(DockstoreTool.TopicSelectionEnum.AI, dockstoreTool.getTopicSelection());
        assertTrue(dockstoreTool.isApprovedAITopic());
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

        // Set tool's topicSelection to AI
        testingPostgres.runUpdateStatement("update tool set topicai = 'AI topic' where id = " + toolTest.getId());
        assertFalse(toolTest.isApprovedAITopic());
        toolTest.setTopicSelection(TopicSelectionEnum.AI);
        toolTest.setApprovedAITopic(true);
        dockstoreTool = toolsApi.updateContainer(toolTest.getId(), toolTest);
        assertEquals("AI topic", dockstoreTool.getTopicAI());
        assertEquals(TopicSelectionEnum.AI, dockstoreTool.getTopicSelection());
        assertTrue(dockstoreTool.isApprovedAITopic());
    }

    /**
     * This tests that you can retrieve tools by alias (using optional auth)
     */
    @Test
    void testToolAlias() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi containersApi = new ContainersApi(webClient);
        EntriesApi entriesApi = new EntriesApi(webClient);

        final ApiClient anonWebClient = CommonTestUtilities.getOpenAPIWebClient(false, null, testingPostgres);
        EntriesApi anonEntriesApi = new EntriesApi(anonWebClient);

        final ApiClient otherUserWebClient = CommonTestUtilities.getOpenAPIWebClient(true, OTHER_USERNAME, testingPostgres);
        EntriesApi otherUserEntriesApi = new EntriesApi(otherUserWebClient);

        // Add tool
        DockstoreTool tool = containersApi.getContainerByToolPath(DOCKERHUB_TOOL_PATH, null);
        DockstoreTool refresh = containersApi.refresh(tool.getId());

        // Add alias
        Entry entry = entriesApi.addAliases1(refresh.getId(), "foobar");
        assertTrue(entry.getAliases().containsKey("foobar"), "Should have alias foobar");

        // Check that dates are present
        final Timestamp dbDate = testingPostgres.runSelectStatement("select dbcreatedate from entry_alias where id = " + entry.getId(), Timestamp.class);
        assertNotNull(dbDate);
        // Check that date looks sane
        final Calendar instance = GregorianCalendar.getInstance();
        instance.set(2020, Calendar.MARCH, 13);
        assertTrue(dbDate.after(instance.getTime()));

        // Can get unpublished tool by alias as owner
        assertNotNull(entriesApi.getEntryByAlias("foobar"), "Should retrieve the tool by alias");

        // Cannot get unpublished tool by alias as other user
        try {
            otherUserEntriesApi.getEntryByAlias("foobar");
            fail("Should not be able to retrieve tool.");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode(), "Should fail because user cannot access tool");
        }

        // Cannot get unpublished tool by alias as anon user
        try {
            anonEntriesApi.getEntryByAlias("foobar");
            fail("Should not be able to retrieve tool.");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode(), "Should fail because user cannot access tool");
        }

        // Publish tool
        PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);
        containersApi.publish(refresh.getId(), publishRequest);

        // Can get published tool by alias as owner
        assertNotNull(entriesApi.getEntryByAlias("foobar"), "Should retrieve the tool by alias");

        // Can get published tool by alias as other user
        assertNotNull(otherUserEntriesApi.getEntryByAlias("foobar"), "Should retrieve the tool by alias");

        // Can get published tool by alias as anon user
        assertNotNull(anonEntriesApi.getEntryByAlias("foobar"), "Should retrieve the tool by alias");
    }

    /**
     * Tests that the getPublicWorkflowVesions function excludes hidden versions correctly.
     */
    @Test
    void testGetPublicWorkflowVersions() {
        ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsOpenApi = new WorkflowsApi(client);

        Workflow workflow = registerWorkflowWithTwoVersions();

        //publish workflow
        workflow = workflowsOpenApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        List<WorkflowVersion> workflowVersions = workflowsOpenApi.getWorkflowVersions(workflow.getId(), null, null, null, null, null);
        //Hide version
        workflowVersions.get(1).setHidden(true);
        workflowsOpenApi.updateWorkflowVersion(workflow.getId(), workflowVersions);

        List<WorkflowVersion> publicWorkflowVersions = workflowsOpenApi.getPublicWorkflowVersions(workflow.getId(), null, null, null, null, null);
        assertEquals(1, publicWorkflowVersions.size(), "Should exclude hidden version thus only have 1 version");
    }

    @Test
    void testWorkflowVersionsSorting() {
        ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsOpenApi = new WorkflowsApi(client);

        Workflow workflow = registerWorkflowWithTwoVersions();
        // Test sorting by name in ascending order
        List<WorkflowVersion> workflowVersions = workflowsOpenApi.getWorkflowVersions(workflow.getId(), null, null, "name", "asc", null);
        assertEquals("master", workflowVersions.get(0).getName(), "The first version should be master");

        // Test sorting by name in descending order
        workflowVersions = workflowsOpenApi.getWorkflowVersions(workflow.getId(), null, null, "name", "desc", null);
        assertEquals("testCWL", workflowVersions.get(0).getName(), "The first version should be testCWL");
    }

    @Test
    void testWorkflowVersionsDefaultSorting() {
        ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsOpenApi = new WorkflowsApi(client);

        Workflow workflow = registerWorkflowWithTwoVersions();
        List<String> versionNames = workflowsOpenApi.getWorkflowVersions(workflow.getId(), null, null, null, null, null).stream().map(WorkflowVersion::getName).toList();
        assertTrue(versionNames.size() >= 2);

        // set each version as the default and confirm that when we don't specify a sortCol to `getWorkflowVersion`, the default version is first in the returned list
        for (String versionName: versionNames) {
            workflowsOpenApi.updateDefaultVersion1(workflow.getId(), versionName);
            List<WorkflowVersion> workflowVersions = workflowsOpenApi.getWorkflowVersions(workflow.getId(), null, null, null, null, null);
            assertEquals(versionName, workflowVersions.get(0).getName(), "the default version should be sorted first");
        }
    }

    private Workflow registerWorkflowWithTwoVersions() {
        ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsOpenApi = new WorkflowsApi(client);

        // refresh all
        workflowsOpenApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.cwl", "",
                DescriptorLanguage.CWL.getShortName(), "");

        // refresh individual that is valid
        io.dockstore.openapi.client.model.Workflow workflow = workflowsOpenApi
                .getWorkflowByPath("github.com/DockstoreTestUser2/hello-dockstore-workflow", WorkflowSubClass.valueOf(BIOWORKFLOW), "");


        // Refresh a single version
        workflow = workflowsOpenApi.refreshVersion(workflow.getId(), "master", false);
        assertEquals(1, workflow.getWorkflowVersions().size(), "Should only have one version");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> Objects.equals(workflowVersion.getName(), "master")), "Should have master version");

        // Refresh another version
        workflow = workflowsOpenApi.refreshVersion(workflow.getId(), "testCWL", false);
        assertEquals(2, workflow.getWorkflowVersions().size(), "Should now have two versions");

        return workflow;
    }
}
