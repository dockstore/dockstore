/*
 *    Copyright 2018 OICR
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

package io.dockstore.client.cli;

import static io.dockstore.webservice.core.Version.CANNOT_FREEZE_VERSIONS_WITH_NO_FILES;
import static io.dockstore.webservice.helpers.EntryVersionHelper.CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY;
import static io.dockstore.webservice.resources.WorkflowResource.A_WORKFLOW_MUST_BE_UNPUBLISHED_TO_RESTUB;
import static io.dockstore.webservice.resources.WorkflowResource.A_WORKFLOW_MUST_HAVE_NO_DOI_TO_RESTUB;
import static io.dockstore.webservice.resources.WorkflowResource.A_WORKFLOW_MUST_HAVE_NO_SNAPSHOT_TO_RESTUB;
import static io.dockstore.webservice.resources.WorkflowResource.FROZEN_VERSION_REQUIRED;
import static io.dockstore.webservice.resources.WorkflowResource.NO_ZENDO_USER_TOKEN;
import static io.dockstore.webservice.resources.WorkflowResource.YOU_CANNOT_CHANGE_THE_DESCRIPTOR_TYPE_OF_A_FULL_OR_HOSTED_WORKFLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SlowTest;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.jdbi.FileDAO;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.Workflow.ModeEnum;
import io.swagger.client.model.Workflow.TopicSelectionEnum;
import io.swagger.client.model.WorkflowVersion;
import io.swagger.client.model.WorkflowVersion.DoiStatusEnum;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jetty.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * This test suite tests various workflow related processes.
 * Created by aduncan on 05/04/16.
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
public class GeneralWorkflowIT extends BaseIT {

    private static final String DUMMY_DOI = "10.foo/bar";

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    private FileDAO fileDAO;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        this.fileDAO = new FileDAO(sessionFactory);

        // used to allow us to use fileDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }


    /**
     * This tests that smart refresh correctly refreshes the right versions based on some scenarios for GitHub
     */
    @Test
    void testSmartRefreshGitHub() {
        commonSmartRefreshTest(SourceControl.GITHUB, "DockstoreTestUser2/hello-dockstore-workflow", "testBoth");
    }

    /**
     * This tests that smart refresh correctly refreshes the right versions based on some scenarios for Gitlab
     */
    @Test
    void testSmartRefreshGitlab() {
        commonSmartRefreshTest(SourceControl.GITLAB, "dockstore.test.user2/dockstore-workflow-example", "master");
    }


    /**
     * This test checks that refresh all workflows (with a mix of stub and full) and refresh individual.  It then tries to publish them
     */
    @Test
    void testRefreshAndPublish() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // refresh all
        workflowsApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.cwl", "",
                DescriptorLanguage.CWL.getShortName(), "");

        // refresh individual that is valid
        Workflow workflow = workflowsApi.getWorkflowByPath("github.com/DockstoreTestUser2/hello-dockstore-workflow", BIOWORKFLOW, "");
        workflow = workflowsApi.refresh(workflow.getId(), false);
        assertNotNull(workflow.getLicenseInformation(), "Should have a license object even if it's null name");
        assertNotNull(workflow.getLicenseInformation().getLicenseName(), "Should have no license name");

        // check that valid is valid and full
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals(0, count, "there should be 0 published entries, there are " + count);
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals(2, count2, "there should be 2 valid versions, there are " + count2);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode='FULL'", long.class);
        assertEquals(1, count3, "there should be 1 full workflows, there are " + count3);
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertTrue(4 <= count4, "there should be at least 4 versions, there are " + count4);

        // attempt to publish it
        workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));

        final long count5 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals(1, count5, "there should be 1 published entry, there are " + count5);

        // unpublish
        workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(false));

        final long count6 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals(0, count6, "there should be 0 published entries, there are " + count6);

        // Restub
        workflow = workflowsApi.restub(workflow.getId());

        // Refresh a single version
        workflow = workflowsApi.refreshVersion(workflow.getId(), "master", false);
        assertEquals(1, workflow.getWorkflowVersions().size(), "Should only have one version");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> Objects.equals(workflowVersion.getName(), "master")), "Should have master version");
        assertEquals(ModeEnum.FULL, workflow.getMode(), "Should no longer be a stub workflow");

        // Refresh another version
        workflow = workflowsApi.refreshVersion(workflow.getId(), "testCWL", false);
        assertEquals(2, workflow.getWorkflowVersions().size(), "Should now have two versions");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> Objects.equals(workflowVersion.getName(), "testCWL")), "Should have testCWL version");

        try {
            workflowsApi.refreshVersion(workflow.getId(), "fakeVersion", false);
            fail("Should not be able to refresh a version that does not exist");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.BAD_REQUEST_400, ex.getCode());
        }
    }

    /**
     * This test manually publishing a workflow
     */
    @Test
    void testManualPublish() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "wdl", SourceControl.GITHUB,
            "/Dockstore.wdl", true);
    }

    /**
     * This tests attempting to manually publish a workflow with no valid versions
     */
    @Test
    void testManualPublishInvalid() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        // try and publish
        try {
            manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/dockstore_empty_repo", "testname", "wdl", SourceControl.GITHUB,
                "/Dockstore.wdl", true);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Repository does not meet requirements to publish"));
        }
    }

    /**
     * This tests adding and removing labels from a workflow
     */
    @Test
    void testLabelFormat() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // Set up workflow
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "wdl",
                SourceControl.GITHUB, "/Dockstore.wdl", true);

        // test good labels
        workflow = workflowsApi.updateLabels(workflow.getId(), "abc,abc-abc,123-123,abc-123,123-abc-abc", "");
        assertEquals(5, workflow.getLabels().size());

        // test bad labels
        try {
            workflow = workflowsApi.updateLabels(workflow.getId(), "-,-abc,123-,abc--123,123-abc-", "");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Invalid label format"));
        }
    }

    /**
     * This tests adding and removing labels from a workflow
     */
    @Test
    void testLabelEditing() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // Set up workflow
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "wdl",
            SourceControl.GITHUB, "/Dockstore.wdl", true);

        // add labels
        workflow = workflowsApi.updateLabels(workflow.getId(), "test1,test2", "");
        assertEquals(2, workflow.getLabels().size());

        final long count = testingPostgres.runSelectStatement("select count(*) from entry_label", long.class);
        assertEquals(2, count, "there should be 2 labels, there are " + count);

        // remove labels
        workflow = workflowsApi.updateLabels(workflow.getId(), "test2,test3", "");
        assertEquals(2, workflow.getLabels().size());

        final long count2 = testingPostgres.runSelectStatement("select count(*) from entry_label", long.class);
        assertEquals(2, count2, "there should be 2 labels, there are " + count2);
    }

    /**
     * This tests manually publishing a duplicate workflow (should fail)
     */
    @Test
    void testManualPublishDuplicate() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // Manually register workflow
        manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "", "wdl", SourceControl.GITHUB,
            "/Dockstore.wdl", true);

        // Manually register the same workflow
        try {
            manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "", "wdl", SourceControl.GITHUB,
                "/Dockstore.wdl", true);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("A workflow with the same path and name already exists."));
        }

    }

    /**
     * This tests that a user can update a workflow version
     */
    @Test
    void testUpdateWorkflowVersion() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // Manually register workflow
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "wdl",
            SourceControl.GITHUB, "/Dockstore.wdl", true);

        // Update workflow
        Optional<WorkflowVersion> workflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> Objects.equals(version.getName(), "testCWL")).findFirst();
        if (workflowVersion.isEmpty()) {
            fail("testCWL version should exist");
        }

        List<WorkflowVersion> workflowVersions = new ArrayList<>();
        WorkflowVersion updateWorkflowVersion = workflowVersion.get();
        updateWorkflowVersion.setHidden(true);
        updateWorkflowVersion.setWorkflowPath("/Dockstore2.wdl");
        workflowVersions.add(updateWorkflowVersion);
        workflowsApi.updateWorkflowVersion(workflow.getId(), workflowVersions);

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from workflowversion wv, version_metadata vm where wv.name = 'testCWL' and vm.hidden = 't' and wv.workflowpath = '/Dockstore2.wdl' and wv.id = vm.id",
            long.class);
        assertEquals(1, count, "there should be 1 matching workflow version, there is " + count);
    }

    /**
     * This tests that a restub will work on an unpublished, full workflow
     */
    @Test
    void testRestub() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // Restub workflow
        workflowsApi.restub(workflow.getId());

        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertEquals(0, count, "there should be 0 workflow versions, there are " + count);
    }

    /**
     * This tests that a restub will not work on an published, full workflow
     */
    @Test
    void testRestubError() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // Publish workflow
        workflow = workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));

        // Restub
        try {
            workflow = workflowsApi.restub(workflow.getId());
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("A workflow must be unpublished to restub"));
        }
    }

    /**
     * Tests updating workflow descriptor type when a workflow is FULL and when it is a STUB
     */
    @Test
    void testDescriptorTypes() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "wdl",
            SourceControl.GITHUB, "/Dockstore.wdl", true);

        final long count = testingPostgres.runSelectStatement("select count(*) from workflow where descriptortype = 'wdl'", long.class);
        assertEquals(1, count, "there should be 1 wdl workflow, there are " + count);

        workflow = workflowsApi.refresh(workflow.getId(), false);
        workflow.setDescriptorType(Workflow.DescriptorTypeEnum.CWL);
        try {
            workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains(YOU_CANNOT_CHANGE_THE_DESCRIPTOR_TYPE_OF_A_FULL_OR_HOSTED_WORKFLOW));
        }
    }

    /**
     * Tests updating a workflow tag with invalid workflow descriptor path
     */
    @Test
    void testWorkflowVersionIncorrectPath() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // Update workflow version to new path
        Optional<WorkflowVersion> workflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> Objects.equals(version.getName(), "master")).findFirst();
        if (workflowVersion.isEmpty()) {
            fail("Master version should exist");
        }

        List<WorkflowVersion> workflowVersions = new ArrayList<>();
        WorkflowVersion updateWorkflowVersion = workflowVersion.get();
        updateWorkflowVersion.setWorkflowPath("/newdescriptor.cwl");
        workflowVersions.add(updateWorkflowVersion);
        workflowVersions = workflowsApi.updateWorkflowVersion(workflow.getId(), workflowVersions);
        workflow = workflowsApi.refresh(workflow.getId(), false);

        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where name = 'master' and workflowpath = '/newdescriptor.cwl'",
                long.class);
        assertEquals(1, count, "the workflow version should now have a new descriptor path");

        // Update workflow version to incorrect path (wrong extension)
        workflowVersion = workflowVersions.stream().filter(version -> Objects.equals(version.getName(), "master")).findFirst();
        if (workflowVersion.isEmpty()) {
            fail("Master version should exist");
        }

        updateWorkflowVersion = workflowVersion.get();
        updateWorkflowVersion.setWorkflowPath("/Dockstore.wdl");
        workflowVersions.clear();
        workflowVersions.add(updateWorkflowVersion);
        try {
            workflowVersions = workflowsApi.updateWorkflowVersion(workflow.getId(), workflowVersions);
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Please ensure that the workflow path uses the file extension cwl"));
        }

    }


    @Test
    void testUpdateWorkflowPath() throws ApiException {
        // Set up webservice
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        usersApi.getUser();

        Workflow githubWorkflow = workflowsApi
            .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/Dockstore.cwl", "test-update-workflow", "cwl",
                "/test.json");

        // Publish github workflow
        Workflow workflow = workflowsApi.refresh(githubWorkflow.getId(), false);

        //update the default workflow path to be hello.cwl , the workflow path in workflow versions should also be changes
        workflow.setWorkflowPath("/hello.cwl");
        workflowsApi.updateWorkflowPath(githubWorkflow.getId(), workflow);
        workflowsApi.refresh(githubWorkflow.getId(), false);

        //check if the workflow versions have the same workflow path or not in the database
        final String masterpath = testingPostgres
            .runSelectStatement("select workflowpath from workflowversion where name = 'testWorkflowPath'", String.class);
        final String testpath = testingPostgres
            .runSelectStatement("select workflowpath from workflowversion where name = 'testWorkflowPath'", String.class);
        assertEquals("/Dockstore.cwl", masterpath, "master workflow path should be the same as default workflow path, it is " + masterpath);
        assertEquals("/Dockstore.cwl", testpath, "test workflow path should be the same as default workflow path, it is " + testpath);
    }

    @Test
    void testAddingWorkflowForumUrlAndTopic() throws ApiException {
        // Set up webservice
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
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
    }

    @Test
    void testTopicAfterRefresh() throws ApiException {
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", DescriptorLanguage.CWL.toString(),
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // confirm the default workflow topic settings
        final String topicAutomatic = workflow.getTopicAutomatic();
        assertEquals("test repo for CWL and WDL workflows", topicAutomatic);
        assertNull(workflow.getTopicManual());
        assertEquals(TopicSelectionEnum.AUTOMATIC, workflow.getTopicSelection());

        // set the automatic topic to a garbage string, change the manual topic, and select it
        final String topicManual = "a user-specified manual topic!";
        final String garbage = "fooooo";
        assertEquals(1,
            testingPostgres.runUpdateStatement(String.format("update workflow set topicAutomatic = '%s', topicManual = '%s', topicSelection = '%s' where id = %d", garbage, topicManual, "MANUAL", workflow.getId())));

        // confirm the new topic settings
        workflow = workflowsApi.getWorkflow(workflow.getId(), null);
        assertEquals(garbage, workflow.getTopicAutomatic());
        assertEquals(topicManual, workflow.getTopicManual());
        assertEquals(TopicSelectionEnum.MANUAL, workflow.getTopicSelection());

        // refresh the workflow
        Workflow refreshedWorkflow = workflowsApi.refresh(workflow.getId(), false);

        // make sure the automatic topic was refreshed, and that the manual topic and selection are the same
        assertEquals(workflow.getId(), refreshedWorkflow.getId());
        assertEquals(topicAutomatic, refreshedWorkflow.getTopicAutomatic());
        assertEquals(topicManual, refreshedWorkflow.getTopicManual());
        assertEquals(TopicSelectionEnum.MANUAL, refreshedWorkflow.getTopicSelection());
    }

    @Test
    void testWorkflowFreezingWithNoFiles() {
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Get workflow
        Workflow githubWorkflow = workflowsApi
            .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/wrongpath.wdl", "test-update-workflow", "wdl",
                "/wrong-test.json");

        Workflow workflowBeforeFreezing = workflowsApi.refresh(githubWorkflow.getId(), false);
        WorkflowVersion master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst()
            .get();
        master.setFrozen(true);
        try {
            List<WorkflowVersion> workflowVersions = workflowsApi
                .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        } catch (ApiException e) {
            // should exception
            assertTrue(e.getMessage().contains(CANNOT_FREEZE_VERSIONS_WITH_NO_FILES), "missing error message");
            return;
        }
        fail("should be unreachable");
    }

    @Test
    void testWorkflowFreezing() throws ApiException {
        // Set up webservice
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Get workflow
        Workflow githubWorkflow = workflowsApi
            .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/hello.wdl", "test-update-workflow", "wdl", "/test.json");

        // Publish github workflow
        Workflow workflowBeforeFreezing = workflowsApi.refresh(githubWorkflow.getId(), false);
        WorkflowVersion master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst()
            .get();
        master.setFrozen(true);
        final List<WorkflowVersion> workflowVersions1 = workflowsApi
            .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions1.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertTrue(master.isFrozen());

        // try various operations that should be disallowed

        // cannot modify version properties, like unfreezing for now
        workflowBeforeFreezing = workflowsApi.refresh(githubWorkflow.getId(), false);
        master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst().get();
        master.setFrozen(false);
        List<WorkflowVersion> workflowVersions = workflowsApi
            .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertTrue(master.isFrozen());

        // but should be able to change doi stuff
        master.setFrozen(true);
        master.setDoiStatus(WorkflowVersion.DoiStatusEnum.REQUESTED);
        master.setDoiURL(DUMMY_DOI);
        workflowVersions = workflowsApi.updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertEquals(DUMMY_DOI, master.getDoiURL());
        assertEquals(DoiStatusEnum.REQUESTED, master.getDoiStatus());

        // refresh should skip over the frozen version
        final Workflow refresh = workflowsApi.refresh(githubWorkflow.getId(), false);
        master = refresh.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst().get();

        // cannot modify sourcefiles for a frozen version
        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(master.getId());
        assertFalse(sourceFiles.isEmpty());
        sourceFiles.forEach(s -> {
            assertTrue(s.isFrozen());
            testingPostgres.runUpdateStatement("update sourcefile set content = 'foo' where id = " + s.getId());
            final String content = testingPostgres
                .runSelectStatement("select content from sourcefile where id = " + s.getId(), String.class);
            assertNotEquals("foo", content);
        });

        // try deleting a row join table
        sourceFiles.forEach(s -> {
            final int affected = testingPostgres
                .runUpdateStatement("delete from version_sourcefile vs where vs.sourcefileid = " + s.getId());
            assertEquals(0, affected);
        });

        // try updating a row in the join table
        sourceFiles.forEach(s -> {
            final int affected = testingPostgres
                .runUpdateStatement("update version_sourcefile set sourcefileid=123456 where sourcefileid = " + s.getId());
            assertEquals(0, affected);
        });

        final Long versionId = master.getId();
        // try creating a row in the join table
        sourceFiles.forEach(s -> {
            try {
                testingPostgres.runUpdateStatement(
                    "insert into version_sourcefile (versionid, sourcefileid) values (" + versionId + ", " + 1234567890 + ")");
                fail("Insert should have failed to do row-level security");
            } catch (Exception ex) {
                assertTrue(ex.getMessage().contains("new row violates row-level"));
            }
        });

        // cannot add or delete test files for frozen versions
        try {
            workflowsApi.deleteTestParameterFiles(githubWorkflow.getId(), Lists.newArrayList("foo"), "master");
            fail("could delete test parameter file");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains(CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY));
        }
        try {
            workflowsApi.addTestParameterFiles(githubWorkflow.getId(), Lists.newArrayList("foo"), "", "master");
            fail("could add test parameter file");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains(CANNOT_MODIFY_FROZEN_VERSIONS_THIS_WAY));
        }
    }

    /**
     * This tests the case where the version is:
     *  invalid
     *  has files
     *  processable (not too invalid which depends on the CWL handler)
     * can be snapshotted
     *
     * Specifically, this is a particular CWL CommandLineTool registered as a Workflow
     */
    @Test
    void testFreezingInvalidWorkflow() {
        String versionToSnapshot = "1.0";
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/tagged-apptool", "",
            DescriptorLanguage.CWL.toString(),
            SourceControl.GITHUB, "/tool.cwl", false);
        Workflow workflowBeforeFreezing = workflowsApi.refresh(workflow.getId(), false);
        WorkflowVersion version =
            workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals(versionToSnapshot)).findFirst().get();
        version.setFrozen(true);
        version.setDoiStatus(WorkflowVersion.DoiStatusEnum.REQUESTED);
        version.setDoiURL(DUMMY_DOI);
        assertFalse(version.isValid(), "Double check that this version is in fact invalid");
        List<WorkflowVersion> workflowVersions = workflowsApi
            .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(version));
        version = workflowVersions.stream().filter(v -> v.getName().equals(versionToSnapshot)).findFirst().get();
        assertEquals(DUMMY_DOI, version.getDoiURL());
        assertEquals(DoiStatusEnum.REQUESTED, version.getDoiStatus());
    }

    /**
     * This tests that a workflow's default version can be automatically set during refresh
     */
    @Test
    void testUpdateWorkflowDefaultVersionDuringRefresh() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        // Manually register workflow
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "", "cwl",
                SourceControl.GITHUB, "/Dockstore.cwl", true);
        assertEquals("master", workflow.getDefaultVersion(), "manualRegisterAndPublish does a refresh, it should automatically set the default version");
        workflow = workflowsApi.updateWorkflowDefaultVersion(workflow.getId(), "testBoth");
        assertEquals("testBoth", workflow.getDefaultVersion(), "Should be able to overwrite previous default version");
        workflow = workflowsApi.refresh(workflow.getId(), false);
        assertEquals("testBoth", workflow.getDefaultVersion(), "Refresh should not have set it back to the automatic one");
        // Mimic version on Dockstore no longer present on GitHub
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET name = 'deletedGitHubBranch', reference ='deletedGitHubBranch' where name='testBoth'");
        workflow = workflowsApi.refresh(workflow.getId(), false);
        assertEquals("master", workflow.getDefaultVersion(), "the old default was deleted during refresh, it should automatically set the default version again");
    }

    /**
     * This tests that a workflow can be updated to have default version, and that metadata is set related to the default version
     * WorkflowVersion 951 is testBoth
     * WorkflowVersion 952 is testCWL
     * WorkflowVersion 953 is testWWL
     */
    @Test
    void testUpdateWorkflowDefaultVersion() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // Manually register workflow
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", true);

        // Update workflow with version with no metadata
        workflow = workflowsApi.updateWorkflowDefaultVersion(workflow.getId(), "testWDL");

        // Assert default version is updated and no author or email is found
        long defaultVersionNumber = testingPostgres.runSelectStatement("select actualdefaultversion from workflow where organization = 'DockstoreTestUser2' and repository = 'hello-dockstore-workflow'", long.class);
        String defaultVersionName = testingPostgres.runSelectStatement("select name from workflowversion where id = '" + defaultVersionNumber + "'", String.class);
        assertEquals("testWDL", defaultVersionName, "the default version should be for the testWDL branch, but is for the branch " + defaultVersionName);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflow where actualdefaultversion = '" + defaultVersionNumber + "' and author is null and email is null",
                long.class);
        assertEquals(1, count2, "The given workflow shouldn't have any contact info");
        workflow = workflowsApi.getWorkflow(workflow.getId(), null);
        assertEquals("testWDL", workflow.getDefaultVersion());
        assertNull(workflow.getAuthor());
        assertNull(workflow.getEmail());
        // Update workflow with version with metadata
        workflow = workflowsApi.updateWorkflowDefaultVersion(workflow.getId(), "testBoth");
        workflow = workflowsApi.refresh(workflow.getId(), false);

        // Assert default version is updated and author and email are set
        defaultVersionNumber = testingPostgres.runSelectStatement("select actualdefaultversion from workflow where organization = 'DockstoreTestUser2' and repository = 'hello-dockstore-workflow'", long.class);
        defaultVersionName = testingPostgres.runSelectStatement("select name from workflowversion where id = '" + defaultVersionNumber + "'", String.class);
        assertEquals("testBoth", defaultVersionName, "the default version should be for the testBoth branch, but is for the branch " + defaultVersionName);

        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where actualdefaultversion = '" + defaultVersionNumber + "' and author = 'testAuthor' and email = 'testEmail'",
            long.class);
        assertEquals(1, count3, "The given workflow should have contact info");
        workflow = workflowsApi.getWorkflow(workflow.getId(), null);
        assertEquals("testBoth", workflow.getDefaultVersion());
        assertEquals("testAuthor", workflow.getAuthor());
        assertEquals("testEmail", workflow.getEmail());
        // Unpublish
        workflow = workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(false));

        // Alter workflow so that it has no valid tags
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET valid='f'");

        // Now you shouldn't be able to publish the workflow
        try {
            workflow = workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Repository does not meet requirements to publish"));
        }

        // Tests that a frozen version without a saved DAG could generate a DAG does not try to save it
        testingPostgres.runUpdateStatement("update workflowversion set frozen='t'");
        String workflowDag = workflowsApi.getWorkflowDag(workflow.getId(), defaultVersionNumber);
        assertTrue(workflowDag.contains("nodes"));
        assertTrue(workflowDag.contains("edges"));
    }

    /**
     * This test tests a bunch of different assumptions for how refresh should work for workflows
     */
    @Test
    void testRefreshRelatedConcepts() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // check that workflow is valid and full
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals(2, count2, "there should be 2 valid versions, there are " + count2);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode='FULL'", long.class);
        assertEquals(1, count3, "there should be 1 full workflows, there are " + count3);

        // Change path for each version so that it is invalid
        workflow.setWorkflowPath("thisisnotarealpath.cwl");
        workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflow = workflowsApi.refresh(workflow.getId(), false);

        // Workflow has no valid versions so you cannot publish

        // check that invalid
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='f'", long.class);
        assertTrue(4 <= count4, "there should be at least 4 invalid versions, there are " + count4);

        // Restub
        workflow = workflowsApi.restub(workflow.getId());

        // Update workflow to WDL
        workflow.setWorkflowPath("/Dockstore.wdl");
        workflow.setDescriptorType(Workflow.DescriptorTypeEnum.WDL);
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflow = workflowsApi.refresh(workflow.getId(), false);

        // Can now publish workflow
        workflow = workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));

        // unpublish
        workflow = workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(false));

        // Set paths to invalid
        workflow.setWorkflowPath("thisisnotarealpath.wdl");
        workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflow = workflowsApi.refresh(workflow.getId(), false);

        // Check that versions are invalid
        final long count5 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='f'", long.class);
        assertTrue(4 <= count5, "there should be at least 4 invalid versions, there are " + count5);

        // should now not be able to publish
        try {
            workflow = workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Repository does not meet requirements to publish"));
        }
    }

    /**
     * This tests the dirty bit attribute for workflow versions with github
     */
    @Test
    void testGithubDirtyBit() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/hello-dockstore-workflow", "testname", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // Check that no versions have a true dirty bit
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals(0, count, "there should be no versions with dirty bit, there are " + count);

        // Update workflow version to new path
        Optional<WorkflowVersion> workflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> Objects.equals(version.getName(), "master")).findFirst();
        if (workflowVersion.isEmpty()) {
            fail("Master version should exist");
        }

        List<WorkflowVersion> workflowVersions = new ArrayList<>();
        WorkflowVersion updateWorkflowVersion = workflowVersion.get();
        updateWorkflowVersion.setWorkflowPath("/Dockstoredirty.cwl");
        workflowVersions.add(updateWorkflowVersion);
        workflowVersions = workflowsApi.updateWorkflowVersion(workflow.getId(), workflowVersions);
        workflow = workflowsApi.refresh(workflow.getId(), false);

        // There should be on dirty bit
        final long count1 = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals(1, count1, "there should be 1 versions with dirty bit, there are " + count1);

        // Update default cwl
        workflow.setWorkflowPath("/Dockstoreclean.cwl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.refresh(workflow.getId(), false);

        // There should be 3 versions with new cwl
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'", long.class);
        assertTrue(3 <= count2, "there should be at least 3 versions with workflow path /Dockstoreclean.cwl, there are " + count2);
    }

 
    /**
     * This is a high level test to ensure that gitlab basics are working for gitlab as a workflow repo
     */
    @Test
    void testGitlab() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore.test.user2/dockstore-workflow-example", "testname", "cwl",
            SourceControl.GITLAB, "/Dockstore.cwl", false);

        final long nullLastModifiedWorkflowVersions = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals(0, nullLastModifiedWorkflowVersions, "All GitLab workflow versions should have last modified populated after refreshing");

        // Check a few things
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'", long.class);
        assertEquals(1, count, "there should be 1 workflow, there are " + count);

        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals(2, count2, "there should be 2 valid version, there are " + count2);

        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'", long.class);
        assertEquals(1, count3, "there should be 1 workflow, there are " + count3);

        workflow.getWorkflowVersions().forEach(workflowVersion -> {
            assertNotNull(workflowVersion.getCommitID());
        });

        // publish
        workflow = workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));
        final long count4 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and ispublished='t'",
            long.class);
        assertEquals(1, count4, "there should be 1 published workflow, there are " + count4);

        // unpublish
        workflow = workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(false));
        final long count5 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and ispublished='t'",
            long.class);
        assertEquals(0, count5, "there should be 0 published workflows, there are " + count5);

        // change default branch
        final long count6 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and author is null and email is null and description is null",
            long.class);
        assertEquals(1, count6, "The given workflow shouldn't have any contact info");

        workflow = workflowsApi.updateWorkflowDefaultVersion(workflow.getId(), "test");
        workflow = workflowsApi.refresh(workflow.getId(), false);

        final long count7 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where actualdefaultversion = 952 and author is null and email is null and description is null",
            long.class);
        assertEquals(0, count7, "The given workflow should now have contact info and description");

        // restub
        workflow = workflowsApi.restub(workflow.getId());
        final long count8 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='STUB' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'", long.class);
        assertEquals(1, count8, "The workflow should now be a stub");

        // Convert to WDL workflow
        workflow.setDescriptorType(Workflow.DescriptorTypeEnum.WDL);
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);

        // Should now be a WDL workflow
        final long count9 = testingPostgres.runSelectStatement("select count(*) from workflow where descriptortype='wdl'", long.class);
        assertEquals(1, count9, "there should be 1 wdl workflow" + count9);

        // Restub
        workflow = workflowsApi.restub(workflow.getId());

        // Refresh a single version
        workflow = workflowsApi.refreshVersion(workflow.getId(), "master", false);
        assertEquals(1, workflow.getWorkflowVersions().size(), "Should only have one version");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> Objects.equals(workflowVersion.getName(), "master")), "Should have master version");
        assertEquals(ModeEnum.FULL, workflow.getMode(), "Should no longer be a stub workflow");

        // Refresh another version
        workflow = workflowsApi.refreshVersion(workflow.getId(), "test", false);
        assertEquals(2, workflow.getWorkflowVersions().size(), "Should now have two versions");
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> Objects.equals(workflowVersion.getName(), "test")), "Should have test version");

        try {
            workflowsApi.refreshVersion(workflow.getId(), "fakeVersion", false);
            fail("Should not be able to refresh a version that does not exist");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.BAD_REQUEST_400, ex.getCode());
        }
    }


    /**
     * This tests manually publishing a gitlab workflow
     */
    @Test
    void testManualPublishGitlab() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // manual publish
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore.test.user2/dockstore-workflow-example", "testname", "wdl",
            SourceControl.GITLAB, "/Dockstore.wdl", true);

        // Check for one valid version
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals(1, count, "there should be 1 valid version, there are " + count);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals(0, count2, "All GitLab workflow versions should have last modified populated when manual published");

        // grab wdl file
        Optional<WorkflowVersion> version = workflow.getWorkflowVersions().stream()
            .filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "master")).findFirst();
        if (version.isEmpty()) {
            fail("master version should exist");
        }
        assertTrue(fileDAO.findSourceFilesByVersion(version.get().getId()).stream().anyMatch(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/Dockstore.wdl")));
    }

    /**
     * This tests getting branches and tags from gitlab repositories
     */
    @Test
    @Tag(SlowTest.NAME)
    void testGitLabTagAndBranchTracking() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // manual publish
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore.test.user2/dockstore-workflow-md5sum-unified", "testname",
            "wdl", SourceControl.GITLAB, "/checker.wdl", true);

        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertTrue(count >= 5, "there should be at least 5 versions, there are " + count);
        final long branchCount = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where referencetype = 'BRANCH'", long.class);
        assertTrue(branchCount >= 2, "there should be at least 2 branches, there are " + count);
        final long tagCount = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where referencetype = 'TAG'", long.class);
        assertTrue(tagCount >= 3, "there should be at least 3 tags, there are " + count);
    }

    /**
     * This tests that WDL files are properly parsed for secondary WDL files
     */
    @Test
    void testWDLWithImports() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/test_workflow_wdl", "testname", "wdl",
            SourceControl.GITHUB, "/hello.wdl", false);

        // Check for WDL files
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where path='helper.wdl'", long.class);
        assertEquals(1, count, "there should be 1 secondary file named helper.wdl, there are " + count);

    }

    /**
     * This tests basic concepts with workflow test parameter files
     */
    @Test
    void testTestParameterFile() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/parameter_test_workflow", "testname", "cwl",
            SourceControl.GITHUB, "/Dockstore.cwl", false);

        // There should be no sourcefiles
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(0, count, "there should be no source files that are test parameter files, there are " + count);

        // Update version master with test parameters
        List<String> toAdd = new ArrayList<>();
        toAdd.add("test.cwl.json");
        toAdd.add("test2.cwl.json");
        toAdd.add("fake.cwl.json");
        List<SourceFile> master = workflowsApi.addTestParameterFiles(workflow.getId(), toAdd, "", "master");
        List<String> toDelete = new ArrayList<>();
        toDelete.add("notreal.cwl.json");
        try {
            workflowsApi.deleteTestParameterFiles(workflow.getId(), toDelete, "master");
            fail("Should've have thrown an error when deleting non-existent file");
        } catch (ApiException e) {
            assertEquals(HttpStatus.NOT_FOUND_404, e.getCode(), "Should have returned a 404 when deleting non-existent file");
        }
        workflow = workflowsApi.refresh(workflow.getId(), false);

        final long count2 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(2, count2, "there should be two sourcefiles that are test parameter files, there are " + count2);

        // Update version with test parameters
        toAdd.clear();
        toAdd.add("test.cwl.json");
        workflowsApi.addTestParameterFiles(workflow.getId(), toAdd, "", "master");
        toDelete.clear();
        toDelete.add("test2.cwl.json");
        workflowsApi.deleteTestParameterFiles(workflow.getId(), toDelete, "master");
        workflow = workflowsApi.refresh(workflow.getId(), false);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(1, count3, "there should be one sourcefile that is a test parameter file, there are " + count3);

        // Update other version with test parameters
        toAdd.clear();
        toAdd.add("test.wdl.json");
        workflowsApi.addTestParameterFiles(workflow.getId(), toAdd, "", "wdltest");
        workflow = workflowsApi.refresh(workflow.getId(), false);
        final long count4 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='CWL_TEST_JSON'", long.class);
        assertEquals(2, count4, "there should be two sourcefiles that are cwl test parameter files, there are " + count4);

        // Restub
        workflow = workflowsApi.restub(workflow.getId());

        // Change to WDL
        workflow.setDescriptorType(Workflow.DescriptorTypeEnum.WDL);
        workflow.setWorkflowPath("Dockstore.wdl");
        workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.refresh(workflow.getId(), false);

        // Should be no sourcefiles
        final long count5 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals(0, count5, "there should be no source files that are test parameter files, there are " + count5);

        // Update version wdltest with test parameters
        toAdd.clear();
        toAdd.add("test.wdl.json");
        workflowsApi.addTestParameterFiles(workflow.getId(), toAdd, "", "wdltest");
        workflow = workflowsApi.refresh(workflow.getId(), false);
        final long count6 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='WDL_TEST_JSON'", long.class);
        assertEquals(1, count6, "there should be one sourcefile that is a wdl test parameter file, there are " + count6);
    }

    /**
     * This tests that you can refresh user data by refreshing a workflow
     * ONLY WORKS if the current user in the database dump has no metadata, and on Github there is metadata (bio, location)
     * If the user has metadata, test will pass as long as the user's metadata isn't the same as Github already
     */
    @Test
    void testRefreshingUserMetadata() {
        // Refresh all workflows
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(client);

        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "DockstoreTestUser2/parameter_test_workflow", "testname", DescriptorLanguage.CWL.getShortName(),
                SourceControl.GITHUB, "/Dockstore.cwl", false);

        // Check that user has been updated
        // TODO: bizarrely, the new GitHub Java API library doesn't seem to handle bio
        // final long count = testingPostgres.runSelectStatement("select count(*) from enduser where location='Toronto' and bio='I am a test user'", long.class);
        final long count = testingPostgres.runSelectStatement("select count(*) from user_profile where location='Toronto'", long.class);
        assertEquals(1, count, "One user should have this info now, there are  " + count);
    }

    @Test
    void testGenerateDOIFrozenVersion() throws ApiException {
        // Set up webservice
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);

        //register workflow
        Workflow githubWorkflow = workflowsApi
            .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/hello.wdl", "test-update-workflow", "wdl", "/test.json");

        Workflow workflowBeforeFreezing = workflowsApi.refresh(githubWorkflow.getId(), false);
        WorkflowVersion master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst()
            .get();

        //try issuing DOI for workflow version that is not frozen.
        try {
            workflowsApi.requestDOIForWorkflowVersion(workflowBeforeFreezing.getId(), master.getId(), "");
            fail("This line should never execute if version is mutable. DOI should only be generated for frozen versions of workflows.");
        } catch (ApiException ex) {
            assertTrue(ex.getResponseBody().contains(FROZEN_VERSION_REQUIRED));
        }

        //freeze version 'master'
        master.setFrozen(true);
        final List<WorkflowVersion> workflowVersions1 = workflowsApi
            .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions1.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertTrue(master.isFrozen());


        workflowsApi.publish(workflowBeforeFreezing.getId(), CommonTestUtilities.createPublishRequest(true));
        // should not be able to restub whether published or not since there is a snapshot/frozen
        try {
            workflowsApi.restub(workflowBeforeFreezing.getId());
            fail("This line should never execute, should not be able to restub workflow that is published.");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains(A_WORKFLOW_MUST_BE_UNPUBLISHED_TO_RESTUB));
        }

        //TODO: For now just checking for next failure (no Zenodo token), but should replace with when DOI registration tests are written
        try {
            workflowsApi.requestDOIForWorkflowVersion(workflowBeforeFreezing.getId(), master.getId(), "");
            fail("This line should never execute without valid Zenodo token");
        } catch (ApiException ex) {
            assertTrue(ex.getResponseBody().contains(NO_ZENDO_USER_TOKEN));
            // fake a DOI
            testingPostgres.runUpdateStatement("update workflow set conceptdoi = '10.5281/zenodo.8'");
        }

        // Should be able to refresh a workflow with a frozen version without throwing an error
        workflowsApi.refresh(githubWorkflow.getId(), false);

        // unpublish workflow
        workflowsApi.publish(workflowBeforeFreezing.getId(), CommonTestUtilities.createPublishRequest(false));
        try {
            workflowsApi.restub(workflowBeforeFreezing.getId());
            fail("This line should never execute, should not be able to restub workflow with DOI even if it is unpublished");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains(A_WORKFLOW_MUST_HAVE_NO_DOI_TO_RESTUB));
        }
        // don't die horribly when stubbing something with snapshots, explain the error
        testingPostgres.runUpdateStatement("update workflow set conceptdoi = null");
        try {
            workflowsApi.restub(workflowBeforeFreezing.getId());
            fail("This line should never execute, should not be able to restub workflow with DOI even if it is unpublished");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains(A_WORKFLOW_MUST_HAVE_NO_SNAPSHOT_TO_RESTUB));
        }
    }
}
