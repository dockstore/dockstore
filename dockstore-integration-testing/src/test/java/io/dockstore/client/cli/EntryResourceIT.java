package io.dockstore.client.cli;

import static io.dockstore.common.Hoverfly.BAD_PUT_CODE;
import static io.dockstore.common.Hoverfly.ORCID_SIMULATION_SOURCE;
import static io.dockstore.common.Hoverfly.PUT_CODE;
import static org.junit.Assert.fail;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.EntriesApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DescriptionMetrics;
import io.dockstore.openapi.client.model.User;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.resources.EntryResource;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class EntryResourceIT extends BaseIT {
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    /**
     * Tests that exporting to ORCID does not work for entries or versions without DOI
     * Also tests that endpoint can be hit twice (create, then update)
     * Also tests handling of synchronization issues (put code on Dockstore not on ORCID, put code and DOI URL on ORCID, but not on Dockstore)
     */
    @Test
    public void testOrcidExport() {
        ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        EntriesApi entriesApi = new EntriesApi(client);
        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        workflowsApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/dockstore-whalesay-wdl", "/dockstore.wdl", "",
                DescriptorLanguage.WDL.getShortName(), "");

        List<Workflow> workflows = usersApi.userWorkflows(user.getId());
        Long workflowId = workflows.get(0).getId();
        workflowsApi.refresh1(workflowId, false);
        Assert.assertTrue(workflows.size() > 0);
        Workflow workflow = workflowsApi.getWorkflow(workflowId, null);
        List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        Long workflowVersionId = workflowVersions.get(0).getId();

        try {
            entriesApi.exportToORCID(workflowId, null);
            fail("Should not have been able to export an entry without DOI concept URL");
        } catch (ApiException e) {
            Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode());
            Assert.assertEquals(EntryResource.ENTRY_NO_DOI_ERROR_MESSAGE, e.getMessage());
        }
        testingPostgres.runUpdateStatement("update workflow set conceptDOI='dummy'");
        try {
            entriesApi.exportToORCID(workflowId, workflowVersionId);
            fail("Should not have been able to export a version without DOI URL");
        } catch (ApiException e) {
            Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode());
            Assert.assertEquals(EntryResource.VERSION_NO_DOI_ERROR_MESSAGE, e.getMessage());
        }
        try {
            entriesApi.exportToORCID(workflowId, workflowVersionId + 1);
            fail("Should not have been able to export a version that doesn't belong to the entry");
        } catch (ApiException e) {
            Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode());
            Assert.assertEquals(EntryResource.VERSION_NOT_BELONG_TO_ENTRY_ERROR_MESSAGE, e.getMessage());
        }
        // Give the user a fake ORCID token
        testingPostgres.runUpdateStatement("insert into token (id, content, refreshToken, tokensource, userid, username) values (9001, 'fakeToken', 'fakeRefreshToken', 'orcid.org', 1, 'Potato')");
        testingPostgres.runUpdateStatement("update enduser set orcid='0000-0001-8365-0487' where id='1'");

        // Hoverfly is not used as a class rule here because for some reason it's trying to intercept GitHub in both spy and simulation mode
        try (Hoverfly hoverfly = new Hoverfly(HoverflyMode.SIMULATE)) {
            hoverfly.start();
            hoverfly.simulate(ORCID_SIMULATION_SOURCE);
            entriesApi.exportToORCID(workflowId, null);
            // Exporting twice should work because it's an update
            entriesApi.exportToORCID(workflowId, null);

            hoverfly.resetState();
            // Manually change it to the wrong put code
            testingPostgres.runUpdateStatement(
                    String.format("update workflow set orcidputcode='%s'where orcidputcode='%s'", BAD_PUT_CODE, PUT_CODE));
            // Dockstore should be able to recover from having the wrong put code for whatever reason
            entriesApi.exportToORCID(workflowId, null);

            // Clear DB
            testingPostgres.runUpdateStatement("update workflow set orcidputcode=null");
            Map<String, String> createdState = new HashMap<>();
            createdState.put("Work", "Created");
            hoverfly.setState(createdState);

            try {
                entriesApi.exportToORCID(workflowId, null);
                Assert.fail("Should've failed if DOI URL already exists on ORCID");
            } catch (ApiException e) {
                Assert.assertEquals(HttpStatus.SC_CONFLICT, e.getCode());
                Assert.assertTrue(e.getMessage().contains("Could not export to ORCID. There exists another ORCID work with the same DOI URL."));
            }
        }
    }

    @Test
    public void testDescriptionMetrics() {
        ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        EntriesApi entriesApi = new EntriesApi(client);
        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        workflowsApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/dockstore-whalesay-wdl", "/dockstore.wdl", "",
            DescriptorLanguage.WDL.getShortName(), "");

        List<Workflow> workflows = usersApi.userWorkflows(user.getId());
        Long workflowId = workflows.get(0).getId();
        workflowsApi.refresh1(workflowId, false);

        Assert.assertTrue(workflows.size() > 0);

        Workflow workflow = workflowsApi.getWorkflow(workflowId, null);
        List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        Long workflowVersionId = workflowVersions.get(0).getId();

        // The provided workflow should have a description
        try {
            DescriptionMetrics descriptionMetrics = entriesApi.getDescriptionMetrics(workflowId, workflowVersionId);
            Assert.assertTrue(descriptionMetrics.getCalculatedEntropy() > 0
                && descriptionMetrics.getCalculatedWordCount() > 0
                && descriptionMetrics.getDescriptionLength() > 0);
        } catch (Exception e) {
            fail("Description metrics should have calculated nonzero values for the description.");
        }

        // Update the version description to something specific
        final String newDescription = "'Test 1'";
        final String updateStatement = String.format("UPDATE version_metadata SET description=%s WHERE id=%d",
            newDescription, workflowVersionId);
        testingPostgres.runUpdateStatement(updateStatement);
        try {
            DescriptionMetrics descriptionMetrics = entriesApi.getDescriptionMetrics(workflowId, workflowVersionId);
            Assert.assertTrue(descriptionMetrics.getCalculatedEntropy() == 6
                && descriptionMetrics.getCalculatedWordCount() == 2
                && descriptionMetrics.getDescriptionLength() == 6);
        } catch (ApiException e) {
            fail("Description metrics should have calculated nonzero values for the description.");
        }

        // Update the version description to be null
        final String updateToNull = String.format("UPDATE version_metadata SET description=NULL WHERE id=%d", workflowVersionId);
        testingPostgres.runUpdateStatement(updateToNull);
        try {
            DescriptionMetrics descriptionMetrics = entriesApi.getDescriptionMetrics(workflowId, workflowVersionId);
            Assert.assertTrue(descriptionMetrics.getCalculatedEntropy() == 0
                && descriptionMetrics.getCalculatedWordCount() == 0
                && descriptionMetrics.getDescriptionLength() == 0);
        } catch (ApiException e) {
            fail("The version does not have a description, so metrics should be set to 0.");
        }
    }
}
