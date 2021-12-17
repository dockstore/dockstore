package io.dockstore.client.cli;

import static io.dockstore.common.Hoverfly.BAD_PUT_CODE;
import static io.dockstore.common.Hoverfly.ORCID_SIMULATION_SOURCE;
import static io.dockstore.common.Hoverfly.ORCID_USER_1;
import static io.dockstore.common.Hoverfly.ORCID_USER_2;
import static io.dockstore.common.Hoverfly.PUT_CODE_USER_1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.EntriesApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DescriptionMetrics;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.User;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.core.TokenScope;
import io.dockstore.webservice.core.Version;
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
        testingPostgres.runUpdateStatement("update workflow set conceptDOI='https://doi.org/10.1038/s41586-020-1969-6'");
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
        try {
            testingPostgres.runUpdateStatement("insert into token (id, content, refreshToken, tokensource, userid, username, scope) values "
                + "(9001, 'fakeToken', 'fakeRefreshToken', 'orcid.org', 1, 'Potato', '/authenticate')");
            entriesApi.exportToORCID(workflowId, null);
            Assert.fail("Cannot insert the actual scope, must be enum");
        } catch (ApiException e) {
            Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getCode());
            testingPostgres.runUpdateStatement("delete from token where id=9001");
        }

        try {
            testingPostgres.runUpdateStatement("insert into token (id, content, refreshToken, tokensource, userid, username, scope) values "
                + "(9001, 'fakeToken', 'fakeRefreshToken', 'orcid.org', 1, 'Potato', '/activities/update')");
            entriesApi.exportToORCID(workflowId, null);
            Assert.fail("Cannot insert the actual scope, must be enum");
        } catch (ApiException e) {
            Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getCode());
            testingPostgres.runUpdateStatement("delete from token where id=9001");
        }

        // Give the user a fake ORCID token
        testingPostgres.runUpdateStatement("insert into token (id, content, refreshToken, tokensource, userid, username, scope) values "
            + "(9001, 'fakeToken', 'fakeRefreshToken', 'orcid.org', 1, 'Potato', '" + TokenScope.AUTHENTICATE.name() + "')");
        testingPostgres.runUpdateStatement(String.format("update enduser set orcid='%s' where id='1'", ORCID_USER_1));

        try {
            entriesApi.exportToORCID(workflowId, null);
            fail("Should not have been able to export without a token in the correct scope");
        } catch (ApiException e) {
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, e.getCode());
        }

        testingPostgres.runUpdateStatement("update token set scope='" + TokenScope.ACTIVITIES_UPDATE.name() + "' where id=9001");

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
                    String.format("update entry_orcidputcode set orcidputcode='%s' where orcidputcode='%s'", BAD_PUT_CODE, PUT_CODE_USER_1));
            // Dockstore should be able to recover from having the wrong put code for whatever reason
            entriesApi.exportToORCID(workflowId, null);

            // Remove the put code. Test scenario where the put code and DOI URL are on ORCID, but the put code is not on Dockstore
            testingPostgres.runUpdateStatement(String.format("delete from entry_orcidputcode where entry_id=%s", workflowId));
            Map<String, String> createdState = new HashMap<>();
            createdState.put("Work1", "Created");
            hoverfly.setState(createdState);

            entriesApi.exportToORCID(workflowId, null); // Exporting should succeed. Dockstore will find the put code and update the ORCID work
            String putCode = testingPostgres.runSelectStatement(String.format("select orcidputcode from entry_orcidputcode where entry_id = '%s'", workflowId), String.class);
            Assert.assertEquals("Should be able to find the put code for the ORCID work", PUT_CODE_USER_1, putCode);
        }
    }

    @Test
    public void testMultipleUsersOrcidExport() {
        ApiClient userClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        EntriesApi entriesApi = new EntriesApi(userClient);
        UsersApi usersApi = new UsersApi(userClient);
        User user = usersApi.getUser();
        WorkflowsApi workflowsApi = new WorkflowsApi(userClient);

        ApiClient otherUserClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        EntriesApi otherEntriesApi = new EntriesApi(otherUserClient);
        UsersApi otherUsersApi = new UsersApi(otherUserClient);
        User otherUser = otherUsersApi.getUser();

        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/dockstore-whalesay-wdl", "/dockstore.wdl", "",
                DescriptorLanguage.WDL.getShortName(), "");
        Long workflowId = workflow.getId();
        workflowsApi.refresh1(workflowId, false);
        workflow = workflowsApi.getWorkflow(workflowId, null);
        List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        Long workflowVersionId = workflowVersions.get(0).getId();

        // Give otherUser access to the workflow to mimic being part of the same GitHub organization as the first user
        Assert.assertEquals("Other user should have no workflows", 0, otherUsersApi.userWorkflows(otherUser.getId()).size());
        testingPostgres.runUpdateStatement(String.format("insert into user_entry (userid, entryid) values (%s, %s)", otherUser.getId(), workflowId));
        List<Workflow> workflows = otherUsersApi.userWorkflows(otherUser.getId());
        Assert.assertEquals("Other user should have one workflow", 1, workflows.size());

        // Give user 1 a fake ORCID token
        testingPostgres.runUpdateStatement("insert into token (id, content, refreshToken, tokensource, userid, username, scope) values "
                + "(9001, 'fakeToken1', 'fakeRefreshToken1', 'orcid.org', 1, 'Potato', '" + TokenScope.ACTIVITIES_UPDATE.name() + "')");
        testingPostgres.runUpdateStatement(String.format("update enduser set orcid='%s' where id=%s", ORCID_USER_1, user.getId()));

        // Give other user a fake ORCID token
        testingPostgres.runUpdateStatement("insert into token (id, content, refreshToken, tokensource, userid, username, scope) values "
                + "(9002, 'fakeToken2', 'fakeRefreshToken2', 'orcid.org', 2, 'Tomato', '" + TokenScope.ACTIVITIES_UPDATE.name() + "')");
        testingPostgres.runUpdateStatement(String.format("update enduser set orcid='%s' where id=%s", ORCID_USER_2, otherUser.getId()));

        // Give the workflow a concept DOI
        testingPostgres.runUpdateStatement(String.format("update workflow set conceptDOI='dummy' where id=%s", workflowId));

        // Give the workflow version a DOI url
        testingPostgres.runUpdateStatement(String.format("update version_metadata set doistatus='%s' where id=%s", Version.DOIStatus.CREATED.name(), workflowVersionId));
        testingPostgres.runUpdateStatement(String.format("update version_metadata set doiurl='10.foo/bar' where id=%s", workflowVersionId));

        // Hoverfly is not used as a class rule here because for some reason it's trying to intercept GitHub in both spy and simulation mode
        try (Hoverfly hoverfly = new Hoverfly(HoverflyMode.SIMULATE)) {
            hoverfly.start();
            hoverfly.simulate(ORCID_SIMULATION_SOURCE);
            // User 1 should be able to export to ORCID
            entriesApi.exportToORCID(workflowId, null);
            // Exporting twice should work because it's an update
            entriesApi.exportToORCID(workflowId, null);

            // User 2 should also be able to export the same workflow to ORCID
            otherEntriesApi.exportToORCID(workflowId, null);
            // Exporting twice should work because it's an update
            otherEntriesApi.exportToORCID(workflowId, null);

            // Test workflow version ORCID export
            hoverfly.resetState();

            // User 1 should be able to export workflow version to ORCID
            entriesApi.exportToORCID(workflowId, workflowVersionId);
            entriesApi.exportToORCID(workflowId, workflowVersionId); // Should be able to export twice to update

            // User 2 should be able to export the same workflow version to ORCID
            otherEntriesApi.exportToORCID(workflowId, workflowVersionId);
            otherEntriesApi.exportToORCID(workflowId, workflowVersionId); // Should be able to export twice to update
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
            Assert.assertEquals("Incorrect entropy", 5, (long) descriptionMetrics.getCalculatedEntropy());
            Assert.assertEquals("Incorrect word count", 2, (long) descriptionMetrics.getCalculatedWordCount());
            Assert.assertEquals("Incorrect description length", 6, (long) descriptionMetrics.getDescriptionLength());
        } catch (ApiException e) {
            fail("Description metrics should have calculated nonzero values for the description.");
        }

        // Update the version description to be null
        final String updateToNull = String.format("UPDATE version_metadata SET description=NULL WHERE id=%d", workflowVersionId);
        testingPostgres.runUpdateStatement(updateToNull);
        try {
            DescriptionMetrics descriptionMetrics = entriesApi.getDescriptionMetrics(workflowId, workflowVersionId);
            Assert.assertEquals("Incorrect entropy", 0, (long) descriptionMetrics.getCalculatedEntropy());
            Assert.assertEquals("Incorrect word count", 0, (long) descriptionMetrics.getCalculatedWordCount());
            Assert.assertEquals("Incorrect description length", 0, (long) descriptionMetrics.getDescriptionLength());
        } catch (ApiException e) {
            fail("The version does not have a description, so metrics should be set to 0.");
        }
    }

    @Test
    public void testUpdateEntryToGetTopics() {
        ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        EntriesApi entriesApi = new EntriesApi(client);
        ContainersApi containersApi = new ContainersApi(client);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        DockstoreTool existingTool = containersApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", "");
        assertNull(existingTool.getTopic());

        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/hello-dockstore-workflow", "/dockstore.wdl", "",
                DescriptorLanguage.WDL.getShortName(), "");
        // Registering a workflow sets the topic. Set it to null in the DB so the migration can be tested
        testingPostgres.runUpdateStatement(String.format("update workflow set topicAutomatic=null where id='%s'", workflow.getId()));
        workflow = workflowsApi.getWorkflow(workflow.getId(), "");
        assertNull(workflow.getTopic());

        entriesApi.updateEntryToGetTopics();

        existingTool = containersApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", "");
        assertEquals("Test repo for dockstore", existingTool.getTopic());
        workflow = workflowsApi.getWorkflow(workflow.getId(), "");
        assertEquals("test repo for CWL and WDL workflows", workflow.getTopic());
    }
}
