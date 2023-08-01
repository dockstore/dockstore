package io.dockstore.webservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.client.cli.BasicIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.LambdaEventsApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.LambdaEvent;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.SourceFile.TypeEnum;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class SubmoduleIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private final String installationId = "1179416";
    private final String workflowDockstoreYmlRepo = "dockstore-testing/wdl-humanwgs";


    @BeforeEach
    public void cleanDB() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }
    

    @Test
    void testSubmodulesNormalCase() {
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        workflowClient.handleGitHubRelease("refs/tags/v0.10-test", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/wdl-humanwgs", WorkflowSubClass.BIOWORKFLOW, "versions");
        final List<SourceFile> sourcefiles = workflowClient.getWorkflowVersionsSourcefiles(foobar.getId(), foobar.getWorkflowVersions().get(0).getId(), null);
        assertTrue(foobar.getWorkflowVersions().stream().allMatch(WorkflowVersion::isValid));
        // these files are in a different repo entirely
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/workflows/phase_vcf/phase_vcf.wdl")));
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/workflows/deepvariant/deepvariant.wdl")));
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/tasks/zip_index_vcf.wdl")));
        assertEquals(19, sourcefiles.size());
    }

    @Test
    void testNoSubmodules() {
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        workflowClient.handleGitHubRelease("refs/tags/v0.11-no-submodule", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/wdl-humanwgs", WorkflowSubClass.BIOWORKFLOW, "versions");
        final List<SourceFile> sourcefiles = workflowClient.getWorkflowVersionsSourcefiles(foobar.getId(), foobar.getWorkflowVersions().get(0).getId(), null);
        assertTrue(foobar.getWorkflowVersions().stream().allMatch(WorkflowVersion::isValid));
        // this test really just checks that the contents are more or less the same as when there is a submodule
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/workflows/phase_vcf/phase_vcf.wdl")));
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/workflows/deepvariant/deepvariant.wdl")));
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/tasks/zip_index_vcf.wdl")));
        assertEquals(19, sourcefiles.size());

        // cannot use generated equals or toString since ids will be different
        List<String> allNormalPaths = sourcefiles.stream().map(SourceFile::getPath).sorted().toList();
        List<String> allNormalContent = sourcefiles.stream().filter(s -> !s.getType().equals(TypeEnum.DOCKSTORE_YML)).map(SourceFile::getContent).sorted().toList();
        List<String> allAbsolutePaths = sourcefiles.stream().map(SourceFile::getAbsolutePath).sorted().toList();

        workflowClient.handleGitHubRelease("refs/tags/v0.10-test", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);
        Workflow foobarWithSubmodules = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/wdl-humanwgs", WorkflowSubClass.BIOWORKFLOW, "versions");
        final List<SourceFile> sourcefilesWithSubmodules = workflowClient.getWorkflowVersionsSourcefiles(foobarWithSubmodules.getId(), foobarWithSubmodules.getWorkflowVersions().get(0).getId(), null);
        List<String> subNormalPaths = sourcefilesWithSubmodules.stream().map(SourceFile::getPath).sorted().toList();
        List<String> subNormalContent = sourcefilesWithSubmodules.stream().filter(s -> !s.getType().equals(TypeEnum.DOCKSTORE_YML)).map(SourceFile::getContent).sorted().toList();
        List<String> subAbsolutePaths = sourcefilesWithSubmodules.stream().map(SourceFile::getAbsolutePath).sorted().toList();
        assertEquals(allNormalPaths, subNormalPaths);
        assertEquals(allNormalContent, subNormalContent);
        assertEquals(allAbsolutePaths, subAbsolutePaths);
    }

    @Test
    void testSubmodulesRecursiveCase() {
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        // this tag has a submodule inside a submodule
        workflowClient.handleGitHubRelease("refs/tags/v0.11-submodule-in-submodule", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/wdl-humanwgs", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertTrue(foobar.getWorkflowVersions().stream().allMatch(WorkflowVersion::isValid));
        final List<SourceFile> sourcefiles = workflowClient.getWorkflowVersionsSourcefiles(foobar.getId(), foobar.getWorkflowVersions().get(0).getId(), null);
        // these files are in a different repo entirely
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/workflows/phase_vcf/phase_vcf.wdl")));
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/workflows/deepvariant/deepvariant.wdl")));
        // this one is inside a different repo which points to yet a different repo
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/tasks/zip_index_vcf.wdl")));
        assertEquals(19, sourcefiles.size());
    }

    @Test
    void testSubmodulesCrossRegistryCase() {
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        // this tag has a bitbucket submodule, turns out even GitHub doesn't work well with it https://github.com/dockstore-testing/wdl-humanwgs/tree/v0.11-bitbucket-submodule/workflows
        // GitHub actually links to a 404
        workflowClient.handleGitHubRelease("refs/tags/v0.11-bitbucket-submodule", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/wdl-humanwgs", WorkflowSubClass.BIOWORKFLOW, "versions");
        assertTrue(foobar.getWorkflowVersions().stream().noneMatch(WorkflowVersion::isValid));
        final List<SourceFile> sourcefiles = workflowClient.getWorkflowVersionsSourcefiles(foobar.getId(), foobar.getWorkflowVersions().get(0).getId(), null);
        // these files are not present
        assertFalse(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/workflows/phase_vcf/phase_vcf.wdl")));
        assertFalse(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/workflows/deepvariant/deepvariant.wdl")));
        assertFalse(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/tasks/zip_index_vcf.wdl")));
        LambdaEventsApi lambdaEventsApi = new io.dockstore.openapi.client.api.LambdaEventsApi(webClient);
        final List<LambdaEvent> lambdaEventsByOrganization = lambdaEventsApi.getLambdaEventsByOrganization("dockstore-testing", "0", 10);
        assertTrue(lambdaEventsByOrganization.stream().anyMatch(e -> e.getMessage().contains("Failed to import") && e.getMessage().contains("Not found wdl-common/wdl")));
    }
}
