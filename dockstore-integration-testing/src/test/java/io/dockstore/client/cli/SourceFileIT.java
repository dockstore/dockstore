package io.dockstore.client.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.common.SourceFileTest;
import io.swagger.client.ApiClient;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.VersionsApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

@Category({ ConfidentialTest.class,  SourceFileTest.class})
public class SourceFileIT  extends BaseIT {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    @Test
    public void testGettingSourceFiles() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi toolTagsApi = new ContainertagsApi(webClient);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        VersionsApi versionsApi = new VersionsApi(webClient);

        // Sourcefiles for tags
        DockstoreTool tool = toolApi.getContainerByToolPath("quay.io/dockstoretestuser2/quayandgithub", null);
        Optional<Tag> tag = tool.getWorkflowVersions().stream().filter(existingTag -> Objects.equals(existingTag.getName(), "master")).findFirst();

        List<SourceFile> sourceFiles = versionsApi.getSourceFiles(tag.get().getId(), "tool", null);
        Assert.assertNotNull(sourceFiles);
        Assert.assertEquals(2, sourceFiles.size());

        // Check that filtering works
        List<String> fileTypes = new ArrayList<>();
        fileTypes.add(DescriptorLanguage.FileType.DOCKERFILE.toString());
        fileTypes.add(DescriptorLanguage.FileType.DOCKSTORE_CWL.toString());

        sourceFiles = versionsApi.getSourceFiles(tag.get().getId(), "tool", fileTypes);
        Assert.assertNotNull(sourceFiles);
        Assert.assertEquals(2, sourceFiles.size());

        fileTypes.remove(1);
        sourceFiles = versionsApi.getSourceFiles(tag.get().getId(), "tool", fileTypes);
        Assert.assertNotNull(sourceFiles);
        Assert.assertEquals(1, sourceFiles.size());

        fileTypes.clear();
        fileTypes.add(DescriptorLanguage.FileType.NEXTFLOW_CONFIG.toString());
        sourceFiles = versionsApi.getSourceFiles(tag.get().getId(), "tool", fileTypes);
        Assert.assertNotNull(sourceFiles);
        Assert.assertEquals(0, sourceFiles.size());


        // Sourcefiles for workflowversions
        Workflow workflow = workflowsApi
                .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.cwl", "", "cwl", "/test.json");
        workflow = workflowsApi.refresh(workflow.getId());

        WorkflowVersion workflowVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion1 -> workflowVersion1.getName().equals("testCWL")).findFirst().get();

        sourceFiles = versionsApi.getSourceFiles(workflowVersion.getId(), "workflow", null);
        Assert.assertNotNull(sourceFiles);
        Assert.assertEquals(1, sourceFiles.size());

        // Check that filtering works
        fileTypes.clear();
        fileTypes.add(DescriptorLanguage.FileType.DOCKSTORE_CWL.toString());
        sourceFiles = versionsApi.getSourceFiles(workflowVersion.getId(), "workflow", fileTypes);
        Assert.assertNotNull(sourceFiles);
        Assert.assertEquals(1, sourceFiles.size());

        fileTypes.clear();
        fileTypes.add(DescriptorLanguage.FileType.DOCKSTORE_WDL.toString());
        sourceFiles = versionsApi.getSourceFiles(workflowVersion.getId(), "workflow", fileTypes);
        Assert.assertNotNull(sourceFiles);
        Assert.assertEquals(0, sourceFiles.size());
    }
}
