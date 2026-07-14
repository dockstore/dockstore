/*
 *    Copyright 2026 OICR and UCSC
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

import static io.dockstore.webservice.resources.LambdaEventResource.X_TOTAL_COUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.EntriesApi;
import io.dockstore.openapi.client.api.OrganizationsApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Collection;
import io.dockstore.openapi.client.model.EntryLiteAndVersionName;
import io.dockstore.openapi.client.model.Organization;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
class AutoCategorizationIT extends BaseIT {

    private static final String WORKFLOW_PATH = "dockstore-testing/hello_world";
    private static final String DESCRIPTOR_PATH = "/hello_world.cwl";
    private static final long EPOCH_PAST = 1_000_000_000L;   // Sep 2001
    private static final long EPOCH_FUTURE = 99_999_999_999L; // Year 5138

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    private Workflow publishedWorkflow(WorkflowsApi workflowsApi, String name) {
        return openManualRegisterAndPublish(workflowsApi, WORKFLOW_PATH, name,
            DescriptorLanguage.CWL.getShortName(), SourceControl.GITHUB, DESCRIPTOR_PATH, true);
    }

    private Date getLastCategorizedDate(ApiClient client, long id) throws ApiException {
        return new EntriesApi(client).getLastCategorizedDate(id);
    }

    private Date setLastCategorizedDate(ApiClient client, long id, Long when) throws ApiException {
        return new EntriesApi(client).setLastCategorizedDate(id, "", when);
    }

    private List<EntryLiteAndVersionName> findEntriesToCategorize(ApiClient client, long intervalSeconds, int offset, int limit) throws ApiException {
        return new EntriesApi(client).findEntriesToCategorize(intervalSeconds, offset, limit);
    }

    private void hideAllVersions(ApiClient client, Workflow workflow) throws ApiException {
        List<WorkflowVersion> versions = workflow.getWorkflowVersions();
        versions.forEach(version -> version.setHidden(true));
        new WorkflowsApi(client).updateWorkflowVersion(workflow.getId(), versions);
    }

    @Test
    void testGetLastCategorizedDateIsInitiallyNull() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        Workflow workflow = publishedWorkflow(new WorkflowsApi(adminClient), "");
        assertNull(getLastCategorizedDate(userClient, workflow.getId()));
    }

    @Test
    void testSetAndGetLastCategorizedDate() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        Workflow workflow = publishedWorkflow(new WorkflowsApi(adminClient), "");
        long id = workflow.getId();

        Date set = setLastCategorizedDate(adminClient, id, EPOCH_PAST);
        assertNotNull(set);
        assertEquals(EPOCH_PAST * 1000L, set.getTime());

        Date got = getLastCategorizedDate(userClient, id);
        assertNotNull(got);
        assertEquals(EPOCH_PAST * 1000L, got.getTime());
    }

    @Test
    void testSetLastCategorizedDateDefaultsToNow() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Workflow workflow = publishedWorkflow(new WorkflowsApi(adminClient), "");

        long before = System.currentTimeMillis();
        Date set = setLastCategorizedDate(adminClient, workflow.getId(), null);
        long after = System.currentTimeMillis();

        assertNotNull(set);
        assertTrue(set.getTime() >= before && set.getTime() <= after,
            "Returned date should be approximately now");
    }

    @Test
    void testSetLastCategorizedDateRequiresAdminOrCurator() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        Workflow workflow = publishedWorkflow(new WorkflowsApi(adminClient), "");

        ApiException ex = assertThrows(ApiException.class,
            () -> setLastCategorizedDate(userClient, workflow.getId(), null));
        assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode());
    }

    @Test
    void testGetAndSetLastCategorizedDateForArchivedEntry() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        Workflow workflow = publishedWorkflow(new WorkflowsApi(adminClient), "");
        long id = workflow.getId();

        new EntriesApi(adminClient).archiveEntry(id);

        // archived-but-published entry is still readable by a regular user
        assertNull(getLastCategorizedDate(userClient, id));

        // admin can set the date on an archived entry
        Date set = setLastCategorizedDate(adminClient, id, EPOCH_PAST);
        assertEquals(EPOCH_PAST * 1000L, set.getTime());

        Date got = getLastCategorizedDate(userClient, id);
        assertEquals(EPOCH_PAST * 1000L, got.getTime());
    }

    @Test
    void testFindEntriesToCategorize() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(adminClient);

        // Entry A: published, never categorized, must appear
        Workflow entryA = publishedWorkflow(workflowsApi, "a");

        // Entry B: published, categorized long ago; refresh set dbUpdateDate >> farPast, must appear
        Workflow entryB = publishedWorkflow(workflowsApi, "b");
        setLastCategorizedDate(adminClient, entryB.getId(), EPOCH_PAST);

        // Entry C: published, categorized far in the future, must NOT appear
        Workflow entryC = publishedWorkflow(workflowsApi, "c");
        setLastCategorizedDate(adminClient, entryC.getId(), EPOCH_FUTURE);

        // Entry D: registered but not published, must NOT appear
        Workflow entryD = workflowsApi.manualRegister(SourceControl.GITHUB.name(), WORKFLOW_PATH,
            DESCRIPTOR_PATH, "d", DescriptorLanguage.CWL.getShortName(), "");
        workflowsApi.refresh1(entryD.getId(), false);

        // Entry E: published, but all versions are hidden, so it has no non-hidden, valid version; must NOT appear
        Workflow entryE = publishedWorkflow(workflowsApi, "e");
        hideAllVersions(adminClient, entryE);

        List<EntryLiteAndVersionName> toCategorize = findEntriesToCategorize(adminClient, 0L, 0, 100);
        List<String> trsIds = toCategorize.stream().map(e -> e.getEntryLite().getTrsId()).toList();

        assertTrue(trsIds.contains(entryA.getTrsId()), "Never-categorized published entry should appear");
        assertTrue(trsIds.contains(entryB.getTrsId()), "Stale-categorized published entry should appear");
        assertFalse(trsIds.contains(entryC.getTrsId()), "Future-dated categorized entry should not appear");
        assertFalse(trsIds.contains(entryD.getTrsId()), "Unpublished entry should not appear");
        assertFalse(trsIds.contains(entryE.getTrsId()), "Entry with no non-hidden, valid version should not appear");
    }

    @Test
    void testFindEntriesToCategorizeOrdering() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        EntriesApi entriesApi = new EntriesApi(adminClient);
        WorkflowsApi workflowsApi = new WorkflowsApi(adminClient);

        // Create four entries; ids are auto-incremented so creation order == id order
        Workflow entryA = publishedWorkflow(workflowsApi, "ord-a"); // null lastCategorizedDate
        Workflow entryB = publishedWorkflow(workflowsApi, "ord-b"); // older date
        Workflow entryC = publishedWorkflow(workflowsApi, "ord-c"); // newer date
        Workflow entryD = publishedWorkflow(workflowsApi, "ord-d"); // null lastCategorizedDate, id > A

        setLastCategorizedDate(adminClient, entryB.getId(), EPOCH_PAST);
        setLastCategorizedDate(adminClient, entryC.getId(), EPOCH_PAST + 86400L); // one day later than B

        List<EntryLiteAndVersionName> all = entriesApi.findEntriesToCategorize(0L, 0, 10000);
        List<String> trsIds = all.stream().map(e -> e.getEntryLite().getTrsId()).toList();

        int posA = trsIds.indexOf(entryA.getTrsId());
        int posB = trsIds.indexOf(entryB.getTrsId());
        int posC = trsIds.indexOf(entryC.getTrsId());
        int posD = trsIds.indexOf(entryD.getTrsId());
        assertTrue(posA >= 0 && posB >= 0 && posC >= 0 && posD >= 0, "All four entries must appear");

        // Null lastCategorizedDate entries come before any dated entry
        assertTrue(posA < posB, "Null-dated entry A should sort before dated entry B");
        assertTrue(posD < posB, "Null-dated entry D should sort before dated entry B");

        // Among null-dated entries, lower id sorts first (A was created before D)
        assertTrue(posA < posD, "Among null-dated entries, lower id (A) sorts before higher id (D)");

        // Among dated entries, ascending date order
        assertTrue(posB < posC, "Older lastCategorizedDate (B) should sort before newer (C)");
    }

    @Test
    void testFindEntriesToCategorizeRequiresAdminOrCurator() {
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);

        ApiException ex = assertThrows(ApiException.class,
            () -> findEntriesToCategorize(userClient, 0L, 0, 100));
        assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode());
    }

    @Test
    void testFindEntriesToCategorizeRequiresInterval() {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        EntriesApi entriesApi = new EntriesApi(adminClient);

        ApiException ex = assertThrows(ApiException.class,
            () -> entriesApi.findEntriesToCategorize(null, 0, 100));
        assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
    }

    @Test
    void testFindEntriesToCategorizePagingLimit() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        EntriesApi entriesApi = new EntriesApi(adminClient);
        WorkflowsApi workflowsApi = new WorkflowsApi(adminClient);

        for (int i = 1; i <= 5; i++) {
            publishedWorkflow(workflowsApi, "pg" + i);
        }

        entriesApi.findEntriesToCategorize(0L, 0, 1);
        long total = getXTotalCount(entriesApi);
        assertTrue(total >= 5);

        // limit=3 returns at most 3 entries; X-Total-Count still reflects the full total
        List<EntryLiteAndVersionName> page = entriesApi.findEntriesToCategorize(0L, 0, 3);
        assertEquals(3, page.size());
        assertEquals(total, getXTotalCount(entriesApi));
    }

    @Test
    void testFindEntriesToCategorizePagingOffset() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        EntriesApi entriesApi = new EntriesApi(adminClient);
        WorkflowsApi workflowsApi = new WorkflowsApi(adminClient);

        for (int i = 1; i <= 5; i++) {
            publishedWorkflow(workflowsApi, "pg" + i);
        }

        List<EntryLiteAndVersionName> all = entriesApi.findEntriesToCategorize(0L, 0, 10000);
        long total = getXTotalCount(entriesApi);
        assertTrue(total >= 5);

        List<EntryLiteAndVersionName> page0 = entriesApi.findEntriesToCategorize(0L, 0, 3);
        List<EntryLiteAndVersionName> page1 = entriesApi.findEntriesToCategorize(0L, 3, 3);
        assertEquals(3, page0.size());
        assertTrue(page1.size() >= 2); // total >= 5, so at least 2 remain after skipping 3

        assertTrue(Collections.disjoint(entryPaths(page0), entryPaths(page1)), "Pages must not overlap");
        assertTrue(entryPaths(all).containsAll(entryPaths(page0)));
        assertTrue(entryPaths(all).containsAll(entryPaths(page1)));
    }

    @Test
    void testFindEntriesToCategorizePagingPastEnd() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        EntriesApi entriesApi = new EntriesApi(adminClient);
        WorkflowsApi workflowsApi = new WorkflowsApi(adminClient);

        for (int i = 1; i <= 3; i++) {
            publishedWorkflow(workflowsApi, "pg" + i);
        }

        entriesApi.findEntriesToCategorize(0L, 0, 1);
        long total = getXTotalCount(entriesApi);

        // Offset past the end returns an empty list but X-Total-Count is unchanged
        List<EntryLiteAndVersionName> empty = entriesApi.findEntriesToCategorize(0L, 10000, 10);
        assertTrue(empty.isEmpty());
        assertEquals(total, getXTotalCount(entriesApi));
    }

    private long getXTotalCount(EntriesApi entriesApi) {
        return Long.parseLong(entriesApi.getApiClient().getResponseHeaders().get(X_TOTAL_COUNT).get(0));
    }

    private Set<String> entryPaths(List<EntryLiteAndVersionName> entries) {
        return entries.stream().map(e -> e.getEntryLite().getEntryPath()).collect(Collectors.toSet());
    }

    @Test
    void testCollectionMetadataIsNullByDefault() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi orgsApi = new OrganizationsApi(adminClient);
        Organization org = orgsApi.createOrganization(stubOrg());
        Collection collection = orgsApi.createCollection(stubCollection("Alignment"), org.getId());
        assertNull(collection.getMetadata());
    }

    @Test
    void testAdminCanManageCollectionMetadata() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi orgsApi = new OrganizationsApi(adminClient);
        Organization org = orgsApi.createOrganization(stubOrg());

        // Admin can set metadata on create
        Collection withMetadata = stubCollection("Alignment");
        withMetadata.setMetadata(Map.of("source", "test"));
        assertNotNull(orgsApi.createCollection(withMetadata, org.getId()).getMetadata());

        // Creating without metadata starts null; admin can update to set it
        Collection collection = orgsApi.createCollection(stubCollection("Categorization"), org.getId());
        assertNull(collection.getMetadata());
        collection.setMetadata(Map.of("source", "autocategorization"));
        assertNotNull(orgsApi.updateCollection(collection, org.getId(), collection.getId()).getMetadata());

        // Source field persists correctly on retrieval
        assertEquals("autocategorization", orgsApi.getCollectionById(org.getId(), collection.getId()).getMetadata().get("source"));
    }

    @Test
    void testNonAdminCannotSetCollectionMetadata() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        OrganizationsApi adminOrgsApi = new OrganizationsApi(adminClient);
        OrganizationsApi userOrgsApi = new OrganizationsApi(userClient);

        Organization org = adminOrgsApi.createOrganization(stubOrg());
        long orgId = org.getId();

        // Make OTHER_USERNAME an org maintainer and have them accept
        adminOrgsApi.addUserToOrgByUsername("MAINTAINER", OTHER_USERNAME, orgId);
        userOrgsApi.acceptOrRejectInvitation(orgId, true);

        // Non-admin/curator creates collection with metadata — metadata is silently discarded
        Collection withMetadata = stubCollection("Alignment");
        withMetadata.setMetadata(Map.of("source", "test"));
        Collection created = userOrgsApi.createCollection(withMetadata, orgId);
        assertNull(created.getMetadata(), "Non-admin/curator should not be able to set metadata on create");

        // Admin sets metadata via update
        created.setMetadata(Map.of("source", "test"));
        Collection withAdminMetadata = adminOrgsApi.updateCollection(created, orgId, created.getId());
        assertNotNull(withAdminMetadata.getMetadata());

        // Non-admin/curator tries to clear metadata via update — should be silently ignored
        withAdminMetadata.setMetadata(null);
        Collection unchanged = userOrgsApi.updateCollection(withAdminMetadata, orgId, withAdminMetadata.getId());
        assertNotNull(unchanged.getMetadata(), "Non-admin/curator should not be able to clear metadata");
    }

    private Organization stubOrg() {
        Organization org = new Organization();
        org.setName("TestOrg");
        org.setDisplayName("Test Organization");
        org.setEmail("test@org.com");
        org.setDescription("A test organization");
        org.setLink("https://www.example.com");
        org.setLocation("location");
        org.setTopic("topic");
        return org;
    }

    private Collection stubCollection(String name) {
        Collection c = new Collection();
        c.setName(name);
        c.setDisplayName(name + " Display");
        c.setDescription("A test collection");
        return c;
    }
}
