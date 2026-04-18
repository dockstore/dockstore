/*
 *    Copyright 2024 OICR and UCSC
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
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Workflow;
import java.util.Date;
import java.util.List;
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

    private static final String WORKFLOW_PATH = "DockstoreTestUser/dockstore-whalesay-2";
    private static final String DESCRIPTOR_PATH = "/dockstore.wdl";
    private static final long EPOCH_FAR_PAST = 1_000_000_000L;   // Sep 2001
    private static final long EPOCH_FAR_FUTURE = 99_999_999_999L; // year 5138

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
            DescriptorLanguage.WDL.getShortName(), SourceControl.GITHUB, DESCRIPTOR_PATH, true);
    }

    private Date getLastCategorizedDate(ApiClient client, long id) throws ApiException {
        return new EntriesApi(client).getLastCategorizedDate(id);
    }

    private Date setLastCategorizedDate(ApiClient client, long id, Long when) throws ApiException {
        return new EntriesApi(client).setLastCategorizedDate(id, when);
    }

    private List<Long> findEntriesToCategorize(ApiClient client, long cutoff) throws ApiException {
        return new EntriesApi(client).findEntriesToCategorize(cutoff);
    }

    @Test
    void testGetLastCategorizedDateIsInitiallyNull() throws ApiException {
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        Workflow workflow = publishedWorkflow(new WorkflowsApi(userClient), "");
        assertNull(getLastCategorizedDate(userClient, workflow.getId()));
    }

    @Test
    void testSetAndGetLastCategorizedDate() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        Workflow workflow = publishedWorkflow(new WorkflowsApi(userClient), "");
        long id = workflow.getId();

        Date set = setLastCategorizedDate(adminClient, id, EPOCH_FAR_PAST);
        assertNotNull(set);
        assertEquals(EPOCH_FAR_PAST * 1000L, set.getTime());

        Date got = getLastCategorizedDate(userClient, id);
        assertNotNull(got);
        assertEquals(EPOCH_FAR_PAST * 1000L, got.getTime());
    }

    @Test
    void testSetLastCategorizedDateDefaultsToNow() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        Workflow workflow = publishedWorkflow(new WorkflowsApi(userClient), "");

        long before = System.currentTimeMillis();
        Date set = setLastCategorizedDate(adminClient, workflow.getId(), null);
        long after = System.currentTimeMillis();

        assertNotNull(set);
        assertTrue(set.getTime() >= before && set.getTime() <= after,
            "Returned date should be approximately now");
    }

    @Test
    void testSetLastCategorizedDateRequiresAdminOrCurator() throws ApiException {
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        Workflow workflow = publishedWorkflow(new WorkflowsApi(userClient), "");

        ApiException ex = assertThrows(ApiException.class,
            () -> setLastCategorizedDate(userClient, workflow.getId(), null));
        assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode());
    }

    @Test
    void testGetAndSetLastCategorizedDateForArchivedEntry() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        Workflow workflow = publishedWorkflow(new WorkflowsApi(userClient), "");
        long id = workflow.getId();

        new EntriesApi(adminClient).archiveEntry(id);

        // archived-but-published entry is still readable by a regular user
        assertNull(getLastCategorizedDate(userClient, id));

        // admin can set the date on an archived entry
        Date set = setLastCategorizedDate(adminClient, id, EPOCH_FAR_PAST);
        assertEquals(EPOCH_FAR_PAST * 1000L, set.getTime());

        Date got = getLastCategorizedDate(userClient, id);
        assertEquals(EPOCH_FAR_PAST * 1000L, got.getTime());
    }

    @Test
    void testFindEntriesToCategorize() throws ApiException {
        ApiClient adminClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(userClient);

        // Entry A: published, never categorized → must appear
        Workflow entryA = publishedWorkflow(workflowsApi, "a");

        // Entry B: published, categorized long ago; refresh set dbUpdateDate >> farPast → must appear
        Workflow entryB = publishedWorkflow(workflowsApi, "b");
        setLastCategorizedDate(adminClient, entryB.getId(), EPOCH_FAR_PAST);

        // Entry C: published, categorized far in the future → must NOT appear
        Workflow entryC = publishedWorkflow(workflowsApi, "c");
        setLastCategorizedDate(adminClient, entryC.getId(), EPOCH_FAR_FUTURE);

        // Entry D: registered but not published → must NOT appear
        Workflow entryDStub = workflowsApi.manualRegister(SourceControl.GITHUB.name(), WORKFLOW_PATH,
            DESCRIPTOR_PATH, "d", DescriptorLanguage.WDL.getShortName(), "");
        workflowsApi.refresh1(entryDStub.getId(), false);

        long cutoffNow = System.currentTimeMillis() / 1000L;
        List<Long> toCategorize = findEntriesToCategorize(adminClient, cutoffNow);

        assertTrue(toCategorize.contains(entryA.getId()), "Never-categorized published entry should appear");
        assertTrue(toCategorize.contains(entryB.getId()), "Stale-categorized published entry should appear");
        assertFalse(toCategorize.contains(entryC.getId()), "Future-dated categorized entry should not appear");
        assertFalse(toCategorize.contains(entryDStub.getId()), "Unpublished entry should not appear");
    }

    @Test
    void testFindEntriesToCategorizeRequiresAdminOrCurator() {
        ApiClient userClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        long cutoff = System.currentTimeMillis() / 1000L;

        ApiException ex = assertThrows(ApiException.class,
            () -> findEntriesToCategorize(userClient, cutoff));
        assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode());
    }

    @Test
    void testFindEntriesToCategorizeRequiresCutoff() {
        ApiClient adminClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        EntriesApi entriesApi = new EntriesApi(adminClient);

        ApiException ex = assertThrows(ApiException.class,
            () -> entriesApi.findEntriesToCategorize(null));
        assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
    }
}
