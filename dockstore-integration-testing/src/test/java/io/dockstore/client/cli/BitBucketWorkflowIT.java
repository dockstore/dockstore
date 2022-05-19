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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import io.dockstore.common.BitBucketTest;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.FileWrapper;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

/**
 * Extra confidential integration tests, focus on testing workflow interactions
 * {@link io.dockstore.client.cli.BaseIT}
 *
 * @author dyuen
 */
@Category(BitBucketTest.class)
public class BitBucketWorkflowIT extends BaseIT {
    public static final String DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME = "DockstoreTestUser2/hello-dockstore-workflow";
    public static final String DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW =
        SourceControl.GITHUB.toString() + "/" + DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME;
    public static final String DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore_workflow_cnv";
    private static final String DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW =
        SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow";

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();


    private WorkflowDAO workflowDAO;
    private FileDAO fileDAO;
    private WorkflowVersionDAO workflowVersionDAO;

    @Before
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);
        this.fileDAO = new FileDAO(sessionFactory);

        // used to allow us to use workflowDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);

    }
    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }


    /**
     * Checks that a file can be received from TRS and passes through a valid URL to github, bitbucket, etc.
     *
     * @param ga4Ghv2Api
     * @param dockstoreTestUser2RelativeImportsTool
     * @param reference
     * @param filename
     * @throws IOException
     * @throws URISyntaxException
     */
    private void checkForRelativeFile(Ga4GhApi ga4Ghv2Api, String dockstoreTestUser2RelativeImportsTool, String reference, String filename)
        throws IOException, URISyntaxException {
        FileWrapper toolDescriptor;
        String content;
        toolDescriptor = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorRelativePathGet("CWL", dockstoreTestUser2RelativeImportsTool, reference, filename);
        content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        assertFalse(content.isEmpty());
    }


    @Test
    public void testTargettedRefresh() throws ApiException, URISyntaxException, IOException {

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();
        Assert.assertNotEquals("getUser() endpoint should actually return the user profile", null, user.getUserProfiles());

        workflowApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.cwl", "",
                DescriptorLanguage.CWL.getShortName(), "/test.json");
        workflowApi.manualRegister(SourceControl.BITBUCKET.name(), "dockstore_testuser2/dockstore-workflow", "/Dockstore.cwl", "",
                DescriptorLanguage.CWL.getShortName(), "/test.json");
        List<Workflow> workflows = usersApi.userWorkflows(user.getId());

        for (Workflow workflow : workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, BIOWORKFLOW, null);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId(), false);
        final Workflow workflowByPathBitbucket = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW, BIOWORKFLOW, null);
        final Workflow refreshBitbucket = workflowApi.refresh(workflowByPathBitbucket.getId(), false);

        // tests for reference type for bitbucket workflows
        assertTrue("should see at least 4 branches", refreshBitbucket.getWorkflowVersions().stream()
            .filter(version -> version.getReferenceType() == WorkflowVersion.ReferenceTypeEnum.BRANCH).count() >= 4);
        assertTrue("should see at least 1 tags", refreshBitbucket.getWorkflowVersions().stream()
            .filter(version -> version.getReferenceType() == WorkflowVersion.ReferenceTypeEnum.TAG).count() >= 1);

        assertSame("github workflow is not in full mode", refreshGithub.getMode(), Workflow.ModeEnum.FULL);
        assertTrue("github workflow version count is wrong: " + refreshGithub.getWorkflowVersions().size(),
                4 <= refreshGithub.getWorkflowVersions().size());
        assertEquals("should find two versions with files for github workflow, found : " + refreshGithub.getWorkflowVersions().stream()
                .filter(workflowVersion -> !fileDAO.findSourceFilesByVersion(workflowVersion.getId()).isEmpty()).count(), 2,
            refreshGithub.getWorkflowVersions().stream().filter(workflowVersion -> !fileDAO.findSourceFilesByVersion(workflowVersion.getId()).isEmpty()).count());
        assertEquals("should find two valid versions for github workflow, found : " + refreshGithub.getWorkflowVersions().stream()
                .filter(WorkflowVersion::isValid).count(), 2,
            refreshGithub.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).count());

        assertSame("bitbucket workflow is not in full mode", refreshBitbucket.getMode(), Workflow.ModeEnum.FULL);

        assertEquals("bitbucket workflow version count is wrong: " + refreshBitbucket.getWorkflowVersions().size(), 5,
            refreshBitbucket.getWorkflowVersions().size());
        assertEquals("should find 4 versions with files for bitbucket workflow, found : " + refreshBitbucket.getWorkflowVersions().stream()
                .filter(workflowVersion -> !fileDAO.findSourceFilesByVersion(workflowVersion.getId()).isEmpty()).count(), 4,
            refreshBitbucket.getWorkflowVersions().stream().filter(workflowVersion -> !fileDAO.findSourceFilesByVersion(workflowVersion.getId()).isEmpty()).count());
        assertEquals("should find 0 valid versions for bitbucket workflow, found : " + refreshBitbucket.getWorkflowVersions().stream()
                .filter(WorkflowVersion::isValid).count(), 0,
            refreshBitbucket.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).count());

        // should not be able to get content normally
        Ga4GhApi anonymousGa4Ghv2Api = new Ga4GhApi(CommonTestUtilities.getWebClient(false, null, testingPostgres));
        Ga4GhApi adminGa4Ghv2Api = new Ga4GhApi(webClient);
        boolean exceptionThrown = false;
        try {
            anonymousGa4Ghv2Api
                .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, "master");
        } catch (ApiException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        FileWrapper adminToolDescriptor = adminGa4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, "testCWL");
        assertTrue("could not get content via optional auth", adminToolDescriptor != null && !adminToolDescriptor.getContent().isEmpty());

        workflowApi.publish(refreshGithub.getId(), CommonTestUtilities.createPublishRequest(true));
        // check on URLs for workflows via ga4gh calls
        FileWrapper toolDescriptor = adminGa4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, "testCWL");
        String content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        assertFalse(content.isEmpty());
        checkForRelativeFile(adminGa4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, "testCWL", "Dockstore.cwl");

        // check on commit ids for github
        boolean allHaveCommitIds = refreshGithub.getWorkflowVersions().stream().noneMatch(version -> version.getCommitID().isEmpty());
        assertTrue("not all workflows seem to have commit ids", allHaveCommitIds);
    }


    /**
     * Tests manual registration and publishing of a github and bitbucket workflow
     *
     * @throws ApiException exception used for errors coming back from the web service
     */
    @Test
    public void testManualRegisterThenPublish() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // Make publish request (true)
        final PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);

        // Manually register workflow github
        Workflow githubWorkflow = workflowApi
            .manualRegister("github", DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.wdl", "altname", "wdl", "/test.json");
        Assert.assertEquals("test repo for CWL and WDL workflows", githubWorkflow.getTopicAutomatic());

        // Manually register workflow bitbucket
        Workflow bitbucketWorkflow = workflowApi
            .manualRegister("bitbucket", "dockstore_testuser2/dockstore-workflow", "/Dockstore.cwl", "altname", "cwl", "/test.json");

        // Assert some things
        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals("No workflows are in full mode", 0, count);
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflow where workflowname = 'altname'", long.class);
        assertEquals("There should be two workflows with name altname, there are " + count2, 2, count2);

        // Publish github workflow
        Workflow refreshedWorkflow = workflowApi.refresh(githubWorkflow.getId(), false);
        workflowApi.publish(githubWorkflow.getId(), publishRequest);

        // Tag with a valid descriptor (but no description) and a recognizable README
        final String tagName = "1.0.0";

        Optional<WorkflowVersion> testWDL = refreshedWorkflow.getWorkflowVersions().stream().filter(workflowVersion -> workflowVersion.getName().equals(tagName)).findFirst();
        Assert.assertTrue("A workflow version with a descriptor that does not have a description should fall back to README", testWDL.get().getDescription().contains("test repo for CWL and WDL workflows"));

        // Intentionally mess up description to test if refresh fixes it
        testingPostgres.runUpdateStatement("update version_metadata set description='bad_potato'");

        refreshedWorkflow = workflowApi.refresh(githubWorkflow.getId(), true);
        workflowApi.publish(githubWorkflow.getId(), publishRequest);

        testWDL = refreshedWorkflow.getWorkflowVersions().stream().filter(workflowVersion -> workflowVersion.getName().equals(tagName)).findFirst();
        Assert.assertTrue("A workflow version that had a README description should get updated", testWDL.get().getDescription().contains("test repo for CWL and WDL workflows"));

        // Assert some things
        assertEquals("should have two published, found  " + workflowApi.allPublishedWorkflows(null, null, null, null, null, false, null).size(),
            1, workflowApi.allPublishedWorkflows(null, null, null, null, null, false, null).size());
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals("One workflow is in full mode", 1, count3);
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid = 't'", long.class);
        assertTrue("There should be at least 2 valid version tags, there are " + count4, 2 <= count4);

        workflowApi.refresh(bitbucketWorkflow.getId(), false);
        thrown.expect(ApiException.class);
        // Publish bitbucket workflow, will fail now since the workflow test case is actually invalid now
        workflowApi.publish(bitbucketWorkflow.getId(), publishRequest);
    }

    /**
    * We need an EntryVersionHelper instance so we can call EntryVersionHelper.writeStreamAsZip; getDAO never gets invoked.
    */
    private static class EntryVersionHelperImpl implements EntryVersionHelper {

        @Override
        public EntryDAO getDAO() {
            return null;
        }
    }
    
}


