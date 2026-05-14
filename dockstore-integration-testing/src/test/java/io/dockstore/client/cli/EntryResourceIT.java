package io.dockstore.client.cli;

import static io.dockstore.webservice.resources.LambdaEventResource.X_TOTAL_COUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.EntriesApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DescriptionMetrics;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.EntryLiteAndVersionName;
import io.dockstore.openapi.client.model.User;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
class EntryResourceIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    void testDescriptionMetrics() {
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

        // The provided workflow should have a description
        try {
            DescriptionMetrics descriptionMetrics = entriesApi.getDescriptionMetrics(workflowId, workflowVersionId);
            assertTrue(descriptionMetrics.getCalculatedEntropy() > 0
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
            assertEquals(5, (long) descriptionMetrics.getCalculatedEntropy(), "Incorrect entropy");
            assertEquals(2, (long) descriptionMetrics.getCalculatedWordCount(), "Incorrect word count");
            assertEquals(6, (long) descriptionMetrics.getDescriptionLength(), "Incorrect description length");
        } catch (ApiException e) {
            fail("Description metrics should have calculated nonzero values for the description.");
        }

        // Update the version description to be null
        final String updateToNull = String.format("UPDATE version_metadata SET description=NULL WHERE id=%d", workflowVersionId);
        testingPostgres.runUpdateStatement(updateToNull);
        try {
            DescriptionMetrics descriptionMetrics = entriesApi.getDescriptionMetrics(workflowId, workflowVersionId);
            assertEquals(0, (long) descriptionMetrics.getCalculatedEntropy(), "Incorrect entropy");
            assertEquals(0, (long) descriptionMetrics.getCalculatedWordCount(), "Incorrect word count");
            assertEquals(0, (long) descriptionMetrics.getDescriptionLength(), "Incorrect description length");
        } catch (ApiException e) {
            fail("The version does not have a description, so metrics should be set to 0.");
        }
    }

    @Test
    void testUpdateEntryToGetTopics() {
        ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi containersApi = new ContainersApi(client);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        DockstoreTool existingTool = containersApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", "");
        assertNull(existingTool.getTopic());
        containersApi.refresh(existingTool.getId());

        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/hello-dockstore-workflow", "/dockstore.wdl", "",
                DescriptorLanguage.WDL.getShortName(), "");
        workflowsApi.refresh1(workflow.getId(), true);


        existingTool = containersApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", "");
        assertEquals("Test repo for dockstore", existingTool.getTopic());
        workflow = workflowsApi.getWorkflow(workflow.getId(), "");
        assertEquals("test repo for CWL and WDL workflows", workflow.getTopic());
    }

    @Test
    void testUpdateLanguageVersions() {
        ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        EntriesApi entriesApi = new EntriesApi(client);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        assertEquals(0, rowsWithDescriptorTypeVersions());
        Workflow wdlWorkflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.wdl", "",
            DescriptorLanguage.WDL.getShortName(), "");
        wdlWorkflow = workflowsApi.refresh1(wdlWorkflow.getId(), true);
        final List<WorkflowVersion> wdlWorkflowVersions = wdlWorkflow.getWorkflowVersions();
        Workflow cwlWorkflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.cwl", "cwlworkflow",
            DescriptorLanguage.CWL.getShortName(), "");
        cwlWorkflow = workflowsApi.refresh1(cwlWorkflow.getId(), true);
        final List<WorkflowVersion> cwlWorkflowVersions = cwlWorkflow.getWorkflowVersions();

        // Clear descriptor language versions
        testingPostgres.runUpdateStatement("update version_metadata set descriptortypeversions = null");
        testingPostgres.runUpdateStatement("update sourcefile_metadata set typeVersion = null");
        // Confirm the above worked cleared out the language versions
        wdlWorkflow = workflowsApi.getWorkflow(wdlWorkflow.getId(), null);
        wdlWorkflow.getWorkflowVersions().forEach(wv -> assertEquals(List.of(), wv.getVersionMetadata().getDescriptorTypeVersions()));
        cwlWorkflow = workflowsApi.getWorkflow(cwlWorkflow.getId(), null);
        cwlWorkflow.getWorkflowVersions().forEach(wv -> assertEquals(List.of(), wv.getVersionMetadata().getDescriptorTypeVersions()));

        final Integer processed = entriesApi.updateLanguageVersions(Boolean.TRUE);

        // Running the endpoint should have restored the language versions to what they were
        // before wiping the DB rows.
        wdlWorkflow = workflowsApi.getWorkflow(wdlWorkflow.getId(), null);
        wdlWorkflow.getWorkflowVersions().forEach(wv -> assertTrue(languageVersionsMatch(wv, wdlWorkflowVersions)));
        cwlWorkflow = workflowsApi.getWorkflow(cwlWorkflow.getId(), null);
        cwlWorkflow.getWorkflowVersions().forEach(wv -> assertTrue(languageVersionsMatch(wv, cwlWorkflowVersions)));

        assertEquals(6, processed); // 4 tools, plus 2 workflows above
        assertEquals(7, rowsWithDescriptorTypeVersions());
    }

    private boolean languageVersionsMatch(WorkflowVersion updatedVersion, List<WorkflowVersion> oldVersions) {
        return oldVersions.stream().filter(oldVersion -> oldVersion.getName().equals(updatedVersion.getName()))
            .findFirst()
            .map(found -> found.getVersionMetadata().getDescriptorTypeVersions().equals(updatedVersion.getVersionMetadata().getDescriptorTypeVersions()))
            .orElse(Boolean.FALSE);
    }

    private Long rowsWithDescriptorTypeVersions() {
        return testingPostgres.runSelectStatement(
            "select count(*) from version_metadata where descriptortypeversions is not null",
            Long.class);
    }

    @Test
    void testGetAllEntries() throws ApiException {
        EntriesApi entriesApi = new EntriesApi(getAnonymousOpenAPIWebClient());

        List<EntryLiteAndVersionName> entries = entriesApi.getAllEntries(0, 100);
        long total = getXTotalCount(entriesApi);

        // cleanStatePrivate2 has 1 published tool and 2 published workflows
        assertTrue(total >= 3);
        assertEquals(total, entries.size());
        entries.forEach(e -> assertNotNull(e.getEntryLite().getTrsId()));
    }

    @Test
    void testGetAllEntriesPagination() throws ApiException {
        EntriesApi entriesApi = new EntriesApi(getAnonymousOpenAPIWebClient());

        entriesApi.getAllEntries(0, 100);
        long total = getXTotalCount(entriesApi);
        assertTrue(total >= 3);

        // limit restricts page size but X-Total-Count still reflects the full total
        List<EntryLiteAndVersionName> limited = entriesApi.getAllEntries(0, 1);
        assertEquals(1, limited.size());
        assertEquals(total, getXTotalCount(entriesApi));

        // offset paginates without overlap
        List<EntryLiteAndVersionName> page0 = entriesApi.getAllEntries(0, 2);
        List<EntryLiteAndVersionName> page1 = entriesApi.getAllEntries(2, 2);
        assertEquals(2, page0.size());
        Set<String> ids0 = page0.stream().map(e -> e.getEntryLite().getTrsId()).collect(Collectors.toSet());
        Set<String> ids1 = page1.stream().map(e -> e.getEntryLite().getTrsId()).collect(Collectors.toSet());
        assertTrue(Collections.disjoint(ids0, ids1));
    }

    private long getXTotalCount(EntriesApi entriesApi) {
        return Long.parseLong(entriesApi.getApiClient().getResponseHeaders().get(X_TOTAL_COUNT).get(0));
    }
}
