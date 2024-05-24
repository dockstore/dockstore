/*
 * Copyright 2024 OICR and UCSC
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

package io.dockstore.webservice;

import static io.dockstore.common.Hoverfly.BAD_PUT_CODE;
import static io.dockstore.common.Hoverfly.ORCID_DESTINATION_REGEX;
import static io.dockstore.common.Hoverfly.ORCID_SIMULATION_SOURCE;
import static io.dockstore.common.Hoverfly.ORCID_USER_1;
import static io.dockstore.common.Hoverfly.ORCID_USER_2;
import static io.dockstore.common.Hoverfly.PUT_CODE_USER_1;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubRelease;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.HoverflyTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.RepositoryConstants.DockstoreTestUser2;
import io.dockstore.common.RepositoryConstants.DockstoreTesting;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.EntriesApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.OrcidAuthorInformation;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.User;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.core.TokenScope;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.resources.EntryResource;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.specto.hoverfly.junit5.HoverflyExtension;
import io.specto.hoverfly.junit5.api.HoverflyConfig;
import io.specto.hoverfly.junit5.api.HoverflyCore;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@ExtendWith(HoverflyExtension.class)
// Must set destination otherwise Hoverfly will intercept everything, including GitHub requests
@HoverflyCore(mode = HoverflyMode.SIMULATE, config = @HoverflyConfig(destination = ORCID_DESTINATION_REGEX))
@Tag(ConfidentialTest.NAME)
@Tag(HoverflyTest.NAME)
public class ORCIDIT extends BaseIT {
    @BeforeEach
    @Override
    public void resetDBBetweenTests() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @BeforeEach
    public void startOrcidSimulation(Hoverfly hoverfly) {
        hoverfly.simulate(ORCID_SIMULATION_SOURCE);
    }

    /**
     * This test relies on Hoverfly to simulate responses from the ORCID API.
     * In the simulation, the responses are crafted for an ORCID author with ID 0000-0002-6130-1021.
     * ORCID authors with other IDs are considered "not found" by the simulation.
     */
    @Test
    void testGetWorkflowVersionOrcidAuthors() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        ApiClient anonymousWebClient = getAnonymousOpenAPIWebClient();
        WorkflowsApi anonymousWorkflowsApi = new WorkflowsApi(anonymousWebClient);
        String wdlWorkflowRepoPath = String.format("github.com/%s/%s", DockstoreTestUser2.TEST_AUTHORS, "foobar");

        // Workflows containing 1 descriptor author and multiple .dockstore.yml authors.
        // If the .dockstore.yml specifies an author, then only the .dockstore.yml's authors should be saved
        handleGitHubRelease(workflowsApi, DockstoreTestUser2.TEST_AUTHORS, "refs/heads/main", USER_2_USERNAME);
        // WDL workflow
        Workflow workflow = workflowsApi.getWorkflowByPath(wdlWorkflowRepoPath, WorkflowSubClass.BIOWORKFLOW, "versions,authors");
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> v.getName().equals("main")).findFirst().get();
        assertEquals(2, version.getAuthors().size());
        assertEquals(2, version.getOrcidAuthors().size());

        List<OrcidAuthorInformation> orcidAuthorInfo = workflowsApi.getWorkflowVersionOrcidAuthors(workflow.getId(), version.getId());
        assertEquals(1, orcidAuthorInfo.size()); // There's 1 OrcidAuthorInfo instead of 2 because only 1 ORCID ID from the version exists on ORCID

        // Publish workflow
        PublishRequest publishRequest = CommonTestUtilities.createOpenAPIPublishRequest(true);
        workflowsApi.publish1(workflow.getId(), publishRequest);

        // Check that an unauthenticated user can get the workflow version ORCID authors of a published workflow
        anonymousWorkflowsApi.getWorkflowVersionOrcidAuthors(workflow.getId(), version.getId());
        assertEquals(1, orcidAuthorInfo.size());
    }

    /**
     * Tests that exporting to ORCID does not work for entries or versions without DOI
     * Also tests that endpoint can be hit twice (create, then update)
     * Also tests handling of synchronization issues (put code on Dockstore not on ORCID, put code and DOI URL on ORCID, but not on Dockstore)
     */
    @Test
    void testOrcidExport(Hoverfly hoverfly) {
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
        assertTrue(workflows.size() > 0);
        Workflow workflow = workflowsApi.getWorkflow(workflowId, null);
        List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        Long workflowVersionId = workflowVersions.get(0).getId();

        try {
            entriesApi.exportToORCID(workflowId, null);
            fail("Should not have been able to export an entry without DOI concept URL");
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode());
            assertEquals(EntryResource.ENTRY_NO_DOI_ERROR_MESSAGE, e.getMessage());
        }
        final String conceptDoi = "https://doi.org/10.1038/s41586-020-1969-6";
        testingPostgres.runUpdateStatement(String.format("insert into doi(type, creator, name) values ('CONCEPT', 'USER', '%s')", conceptDoi));
        final long conceptDoiId = testingPostgres.runSelectStatement(String.format("select id from doi where name = '%s'", conceptDoi), long.class);
        testingPostgres.runUpdateStatement(String.format("insert into entry_concept_doi(entryid, doiid) values (%s, %s)", workflowId, conceptDoiId));

        try {
            entriesApi.exportToORCID(workflowId, workflowVersionId);
            fail("Should not have been able to export a version without DOI URL");
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode());
            assertEquals(EntryResource.VERSION_NO_DOI_ERROR_MESSAGE, e.getMessage());
        }
        try {
            entriesApi.exportToORCID(workflowId, workflowVersionId + 1);
            fail("Should not have been able to export a version that doesn't belong to the entry");
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode());
            assertEquals(EntryResource.VERSION_NOT_BELONG_TO_ENTRY_ERROR_MESSAGE, e.getMessage());
        }
        try {
            testingPostgres.runUpdateStatement("insert into token (id, content, refreshToken, tokensource, userid, username, scope) values "
                    + "(9001, 'fakeToken', 'fakeRefreshToken', 'orcid.org', 1, 'Potato', '/authenticate')");
            entriesApi.exportToORCID(workflowId, null);
            fail("Cannot insert the actual scope, must be enum");
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getCode());
            testingPostgres.runUpdateStatement("delete from token where id=9001");
        }

        try {
            testingPostgres.runUpdateStatement("insert into token (id, content, refreshToken, tokensource, userid, username, scope) values "
                    + "(9001, 'fakeToken', 'fakeRefreshToken', 'orcid.org', 1, 'Potato', '/activities/update')");
            entriesApi.exportToORCID(workflowId, null);
            fail("Cannot insert the actual scope, must be enum");
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getCode());
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
            assertEquals(HttpStatus.SC_UNAUTHORIZED, e.getCode());
        }

        testingPostgres.runUpdateStatement("update token set scope='" + TokenScope.ACTIVITIES_UPDATE.name() + "' where id=9001");

        // Hoverfly is not used as a class rule here because for some reason it's trying to intercept GitHub in both spy and simulation mode
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
        assertEquals(PUT_CODE_USER_1, putCode, "Should be able to find the put code for the ORCID work");
    }

    @Test
    void testMultipleUsersOrcidExport() {
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
        assertEquals(0, otherUsersApi.userWorkflows(otherUser.getId()).size(), "Other user should have no workflows");
        testingPostgres.runUpdateStatement(String.format("insert into user_entry (userid, entryid) values (%s, %s)", otherUser.getId(), workflowId));
        List<Workflow> workflows = otherUsersApi.userWorkflows(otherUser.getId());
        assertEquals(1, workflows.size(), "Other user should have one workflow");

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
        final String conceptDoi = "dummy";
        testingPostgres.runUpdateStatement(String.format("insert into doi(type, creator, name) values ('CONCEPT', 'USER', '%s')", conceptDoi));
        final long conceptDoiId = testingPostgres.runSelectStatement(String.format("select id from doi where name = '%s'", conceptDoi), long.class);
        testingPostgres.runUpdateStatement(String.format("insert into entry_concept_doi(entryid, doiid) values (%s, %s)", workflowId, conceptDoiId));

        // Give the workflow version a DOI url
        testingPostgres.runUpdateStatement(String.format("update version_metadata set doistatus='%s' where id=%s", Version.DOIStatus.CREATED.name(), workflowVersionId));
        final String versionDoi = "10.foo/bar";
        testingPostgres.runUpdateStatement(String.format("insert into doi(type, creator, name) values ('VERSION', 'USER', '%s')", versionDoi));
        final long versionDoiId = testingPostgres.runSelectStatement(String.format("select id from doi where name = '%s'", conceptDoi), long.class);
        testingPostgres.runUpdateStatement(String.format("insert into version_metadata_doi(versionmetadataid, doiid) values (%s, %s)", workflowId, versionDoiId));

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
}
