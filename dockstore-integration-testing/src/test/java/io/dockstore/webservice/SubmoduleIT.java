package io.dockstore.webservice;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.client.cli.BasicIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
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
    private final String dockstoreTesting = "dockstore-testing";
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
        // these files are in a different repo entirely
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/workflows/phase_vcf/phase_vcf.wdl")));
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/workflows/deepvariant/deepvariant.wdl")));
        assertTrue(sourcefiles.size() == 19);
    }

    @Test
    void testSubmodulesRecursiveCase() {
        final ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowClient = new WorkflowsApi(webClient);

        // this tag has a submodule inside a submodule
        workflowClient.handleGitHubRelease("refs/tags/v0.11-submodule-in-submodule", installationId, workflowDockstoreYmlRepo, BasicIT.USER_2_USERNAME);

        Workflow foobar = workflowClient.getWorkflowByPath("github.com/" + workflowDockstoreYmlRepo + "/wdl-humanwgs", WorkflowSubClass.BIOWORKFLOW, "versions");
        final List<SourceFile> sourcefiles = workflowClient.getWorkflowVersionsSourcefiles(foobar.getId(), foobar.getWorkflowVersions().get(0).getId(), null);
        // these files are in a different repo entirely
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/workflows/phase_vcf/phase_vcf.wdl")));
        assertTrue(sourcefiles.stream().anyMatch(f -> f.getPath().contains("../wdl-common/wdl/workflows/deepvariant/deepvariant.wdl")));
        assertTrue(sourcefiles.size() == 19);
    }
}
