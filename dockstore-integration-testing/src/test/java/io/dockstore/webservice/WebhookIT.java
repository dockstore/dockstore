/*
 *
 *    Copyright 2020 OICR
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
 *
 */

package io.dockstore.webservice;

import java.util.List;
import java.util.Objects;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BasicIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.SourceControl;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author agduncan
 */
@Category(ConfidentialTest.class)
public class WebhookIT extends BaseIT {
    private static final int LAMBDA_ERROR = 418;

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private Session session;

    private final String workflowRepo = "DockstoreTestUser2/workflow-dockstore-yml";
    private final String installationId = "1179416";

    @Before
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use tokenDAO outside of the web service
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Test
    public void testWorkflowMigration() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), workflowRepo, "/Dockstore.wdl",
                "foobar", "wdl", "/test.json");
        
        // Refresh should work
        workflow = workflowApi.refresh(workflow.getId());
        assertEquals("Workflow should be FULL mode", Workflow.ModeEnum.FULL, workflow.getMode());
        assertTrue("All versions should be legacy", workflow.getWorkflowVersions().stream().allMatch(workflowVersion -> workflowVersion.isLegacyVersion()));

        // Webhook call should convert workflow to DOCKSTORE_YML
        List<Workflow> workflows = workflowApi.handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/tags/0.1", installationId);
        workflow = workflowApi.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "", false);
        assertEquals("Workflow should be DOCKSTORE_YML mode", Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertTrue("One version should be not legacy", workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> !workflowVersion.isLegacyVersion()));

        // Refresh should now no longer work
        try {
            workflowApi.refresh(workflow.getId());
            fail("Should fail on refresh and not reach this point");
        } catch (ApiException ex) {
            assertEquals("Should not be able to refresh a dockstore.yml workflow.", HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }

    }

    /**
     * This tests the GitHub release process
     */
    @Test
    public void testGitHubReleaseNoWorkflowOnDockstore() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Release 0.1 on GitHub - one new wdl workflow
        List<Workflow> workflows = client.handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/tags/0.1", installationId);
        assertEquals("Should only have one service", 1, workflows.size());

        // Ensure that new workflow is created and is what is expected
        Workflow workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "", false);
        assertEquals("Should be a WDL workflow", Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertEquals("Should have one version 0.1", 1, workflow.getWorkflowVersions().size());

        // Release 0.2 on GitHub - one existing wdl workflow, one new cwl workflow
        workflows = client.handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/tags/0.2", installationId);
        assertEquals("Should only have two services", 2, workflows.size());

        // Ensure that existing workflow is updated
        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "", false);

        // Ensure that new workflow is created and is what is expected
        Workflow workflow2 = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar2", "", false);
        assertEquals("Should be a CWL workflow", Workflow.DescriptorTypeEnum.CWL, workflow2.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", Workflow.ModeEnum.DOCKSTORE_YML, workflow2.getMode());
        assertEquals("Should have one version 0.2", 1, workflow2.getWorkflowVersions().size());

        // Branch master on GitHub - updates two existing workflows
        workflows = client.handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/heads/master", installationId);
        assertEquals("Should only have two services", 2, workflows.size());

        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "", false);
        assertTrue("Should have a master version.", workflow.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "master")));
        assertTrue("Should have a 0.1 version.", workflow.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.1")));
        assertTrue("Should have a 0.2 version.", workflow.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")));

        workflow2 = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar2", "", false);
        assertTrue("Should have a master version.", workflow2.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "master")));
        assertTrue("Should have a 0.2 version.", workflow2.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")));

        boolean hasLegacyVersion = workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> workflowVersion.isLegacyVersion());
        assertTrue("Workflow should not have any legacy refresh versions.", !hasLegacyVersion);
    }

    /**
     * This tests calling refresh on a workflow with a Dockstore.yml
     */
    @Test
    public void testManualRefreshWorkflowWithGitHubApp() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Release 0.1 on GitHub - one new wdl workflow
        List<Workflow> workflows = client.handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/tags/0.1", installationId);
        assertEquals("Should only have one service", 1, workflows.size());

        // Ensure that new workflow is created and is what is expected
        Workflow workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "", false);
        assertEquals("Should be a WDL workflow", Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertTrue("Should have a 0.1 version.", workflow.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.1")));
        boolean hasLegacyVersion = workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> workflowVersion.isLegacyVersion());
        assertTrue("Workflow should not have any legacy refresh versions.", !hasLegacyVersion);

        // Refresh
        try {
            client.refresh(workflow.getId());
            fail("Should fail on refresh and not reach this point");
        } catch (ApiException ex) {
            assertEquals("Should not be able to refresh a dockstore.yml workflow.", HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }
    }

    /**
     * This tests the GitHub release process does not work for users that do not exist on Dockstore
     */
    @Test
    public void testGitHubReleaseNoWorkflowOnDockstoreNoUser() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        try {
            client.handleGitHubRelease(workflowRepo, "thisisafakeuser", "refs/tags/0.1", installationId);
            Assert.fail("Should not reach this statement.");
        } catch (ApiException ex) {
            assertEquals("Should not be able to add a workflow when user does not exist on Dockstore.", LAMBDA_ERROR, ex.getCode());
        }
    }
}
