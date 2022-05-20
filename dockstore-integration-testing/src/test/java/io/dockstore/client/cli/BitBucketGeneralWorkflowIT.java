/*
 *    Copyright 2022 OICR and UCSC
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.dockstore.common.BitBucketTest;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.jdbi.FileDAO;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jetty.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

/**
 * This test suite tests various workflow related processes.
 * Created by aduncan on 05/04/16.
 */
@Category(BitBucketTest.class)
public class BitBucketGeneralWorkflowIT extends BaseIT {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    private FileDAO fileDAO;

    @Before
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        this.fileDAO = new FileDAO(sessionFactory);

        // used to allow us to use fileDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }


    /**
     * This tests that smart refresh correctly refreshes the right versions based on some scenarios for BitBucket
     */
    @Test
    public void testSmartRefreshBitbucket() {
        commonSmartRefreshTest(SourceControl.BITBUCKET, "dockstore_testuser2/dockstore-workflow", "cwl_import");
    }

    private void commonSmartRefreshTest(SourceControl sourceControl, String workflowPath, String versionOfInterest) {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        String correctDescriptorPath = "/Dockstore.cwl";
        String incorrectDescriptorPath = "/Dockstore2.cwl";

        String fullPath = sourceControl.toString() + "/" + workflowPath;

        // Add workflow
        workflowsApi.manualRegister(sourceControl.name(), workflowPath, correctDescriptorPath, "",
                DescriptorLanguage.CWL.getShortName(), "");

        // Smart refresh individual that is valid (should add versions that doesn't exist)
        Workflow workflow = workflowsApi.getWorkflowByPath(fullPath, BIOWORKFLOW, "");
        workflow = workflowsApi.refresh(workflow.getId(), false);

        // All versions should be synced
        workflow.getWorkflowVersions().forEach(workflowVersion -> assertTrue(workflowVersion.isSynced()));

        // When the commit ID is null, a refresh should occur
        WorkflowVersion oldVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        testingPostgres.runUpdateStatement("update workflowversion set commitid = NULL where name = '" + versionOfInterest + "'");
        workflow = workflowsApi.refresh(workflow.getId(), false);
        WorkflowVersion updatedVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        assertNotNull(updatedVersion.getCommitID());
        assertNotEquals(versionOfInterest + " version should be updated (different dbupdatetime)", oldVersion.getDbUpdateDate(), updatedVersion.getDbUpdateDate());

        // When the commit ID is different, a refresh should occur
        oldVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        testingPostgres.runUpdateStatement("update workflowversion set commitid = 'dj90jd9jd230d3j9' where name = '" + versionOfInterest + "'");
        workflow = workflowsApi.refresh(workflow.getId(), false);
        updatedVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        assertNotNull(updatedVersion.getCommitID());
        assertNotEquals(versionOfInterest + " version should be updated (different dbupdatetime)", oldVersion.getDbUpdateDate(), updatedVersion.getDbUpdateDate());

        // Updating the workflow should make the version not synced, a refresh should refresh all versions
        workflow.setWorkflowPath(incorrectDescriptorPath);
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflow.getWorkflowVersions().forEach(workflowVersion -> assertFalse(workflowVersion.isSynced()));
        workflow = workflowsApi.refresh(workflow.getId(), false);

        // All versions should be synced and updated
        workflow.getWorkflowVersions().forEach(workflowVersion -> assertTrue(workflowVersion.isSynced()));
        workflow.getWorkflowVersions().forEach(workflowVersion -> Objects.equals(workflowVersion.getWorkflowPath(), incorrectDescriptorPath));

        // Update the version to have the correct path
        WorkflowVersion testBothVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        testBothVersion.setWorkflowPath(correctDescriptorPath);
        List<WorkflowVersion> versions = new ArrayList<>();
        versions.add(testBothVersion);
        workflowsApi.updateWorkflowVersion(workflow.getId(), versions);

        // Refresh should only update the version that is not synced
        workflow = workflowsApi.getWorkflow(workflow.getId(), "");
        testBothVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        assertFalse("Version should not be synced", testBothVersion.isSynced());
        workflow = workflowsApi.refresh(workflow.getId(), false);
        testBothVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), versionOfInterest)).findFirst().get();
        assertTrue("Version should now be synced", testBothVersion.isSynced());
        assertEquals("Workflow version path should be set", correctDescriptorPath, testBothVersion.getWorkflowPath());
    }


    /**
     * This tests the dirty bit attribute for workflow versions with bitbucket
     */
    @Test
    public void testBitbucketDirtyBit() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // refresh all and individual
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore_testuser2/dockstore-workflow", "testname", "cwl",
            SourceControl.BITBUCKET, "/Dockstore.cwl", false);

        final long nullLastModifiedWorkflowVersions = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All Bitbucket workflow versions should have last modified populated after refreshing", 0,
            nullLastModifiedWorkflowVersions);

        // Check that no versions have a true dirty bit
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be no versions with dirty bit, there are " + count, 0, count);

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
        assertEquals("there should be 1 versions with dirty bit, there are " + count1, 1, count1);

        // Update default cwl
        workflow.setWorkflowPath("/Dockstoreclean.cwl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.refresh(workflow.getId(), false);

        // There should be 3 versions with new cwl
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'", long.class);
        assertEquals("there should be 4 versions with workflow path /Dockstoreclean.cwl, there are " + count2, 4, count2);

    }

    /**
     * Tests that refreshing with valid imports will work (for WDL)
     */
    @Test
    public void testRefreshWithImportsWDL() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // refresh all
        workflowsApi.manualRegister(SourceControl.BITBUCKET.name(), "dockstore_testuser2/dockstore-workflow", "/dockstore.wdl", "",
                DescriptorLanguage.WDL.getShortName(), "");


        // refresh individual that is valid
        Workflow workflow = workflowsApi
            .getWorkflowByPath(SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", BIOWORKFLOW, "");

        // Update workflow path
        workflow.setDescriptorType(Workflow.DescriptorTypeEnum.WDL);
        workflow.setWorkflowPath("/Dockstore.wdl");

        // Update workflow descriptor type
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);

        // Refresh workflow
        workflow = workflowsApi.refresh(workflow.getId(), false);

        // Publish workflow
        workflow = workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));

        // Unpublish workflow
        workflow = workflowsApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(false));

        // Restub
        workflow = workflowsApi.restub(workflow.getId());

        // Refresh a single version
        workflow = workflowsApi.refreshVersion(workflow.getId(), "master", false);
        assertEquals("Should only have one version", 1, workflow.getWorkflowVersions().size());
        assertTrue("Should have master version", workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> Objects.equals(workflowVersion.getName(), "master")));
        assertEquals("Should no longer be a stub workflow", Workflow.ModeEnum.FULL, workflow.getMode());

        // Refresh another version
        workflow = workflowsApi.refreshVersion(workflow.getId(), "cwl_import", false);
        assertEquals("Should now have two versions", 2, workflow.getWorkflowVersions().size());
        assertTrue("Should have cwl_import version", workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> Objects.equals(workflowVersion.getName(), "cwl_import")));

        try {
            workflowsApi.refreshVersion(workflow.getId(), "fakeVersion", false);
            fail("Should not be able to refresh a version that does not exist");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.BAD_REQUEST_400, ex.getCode());
        }
    }

    /**
     * This tests manually publishing a Bitbucket workflow, this test is all messed up and somehow depends on GitHub
     */
    @Test
    @Ignore
    public void testManualPublishBitbucket() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        // manual publish
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore_testuser2/dockstore-workflow", "testname", "wdl",
            SourceControl.BITBUCKET, "/Dockstore.wdl", true);

        // Check for two valid versions (wdl_import and surprisingly, cwl_import)
        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where valid='t' and (name='wdl_import' OR name='cwl_import')",
                long.class);
        assertEquals("There should be a valid 'wdl_import' version and a valid 'cwl_import' version", 2, count);

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All Bitbucket workflow versions should have last modified populated when manual published", 0, count2);

        // Check that commit ID is set
        workflow.getWorkflowVersions().forEach(workflowVersion -> {
            assertNotNull(workflowVersion.getCommitID());
        });

        // grab wdl file
        Optional<WorkflowVersion> version = workflow.getWorkflowVersions().stream()
            .filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "wdl_import")).findFirst();
        if (version.isEmpty()) {
            fail("wdl_import version should exist");
        }
        assertTrue(
            fileDAO.findSourceFilesByVersion(version.get().getId()).stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/Dockstore.wdl"))
                .findFirst().isPresent());
    }

}
