package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Registry;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.Tag;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * A collection of tests for the version validation system
 *
 * @author aduncan
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@org.junit.jupiter.api.Tag(ConfidentialTest.NAME)
class ValidationIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    /**
     * this method will set up the webservice and return the container api
     *
     * @return ContainersApi
     */
    private ContainersApi setupToolWebService() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        return new ContainersApi(client);
    }

    /**
     * this method will set up the webservice and return the workflows api
     *
     * @return WorkflowsApi
     * @throws ApiException
     */
    private WorkflowsApi setupWorkflowWebService() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        return new WorkflowsApi(client);
    }

    private DockstoreTool getTool() {
        DockstoreTool c = new DockstoreTool();
        c.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        c.setName("dockstore-tool-validation");
        c.setGitUrl("https://github.com/DockstoreTestUser2/TestEntryValidation");
        c.setDefaultDockerfilePath("/Dockerfile");
        c.setDefaultCwlPath("/validTool.cwl");
        c.setRegistryString(Registry.DOCKER_HUB.getDockerPath());
        c.setIsPublished(false);
        c.setNamespace("DockstoreTestUser2");
        c.setToolname("test5");
        c.setPath("registry.hub.docker.com/dockstoretestuser2/dockstore-tool-validation");

        Tag tag = new Tag();
        tag.setName("master");
        tag.setReference("master");
        tag.setValid(true);
        tag.setImageId("123456");
        tag.setCwlPath(c.getDefaultCwlPath());
        tag.setWdlPath(c.getDefaultWdlPath());
        List<Tag> tags = new ArrayList<>();
        tags.add(tag);
        c.setWorkflowVersions(tags);

        return c;
    }

    /**
     * Returns whether a version with the given name for a workflow is valid or not
     *
     * @param workflow
     * @param name
     * @return is workflow version valid
     */
    protected boolean isWorkflowVersionValid(Workflow workflow, String name) {
        Optional<WorkflowVersion> workflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> Objects.equals(name, version.getName())).findFirst();

        if (workflowVersion.isPresent()) {
            return workflowVersion.get().isValid();
        }
        return false;
    }

    /**
     * Returns whether a tag with the given name for a tool is valid or not
     *
     * @param tool
     * @param name
     * @return is tag valid
     */
    protected boolean isTagValid(DockstoreTool tool, String name) {
        Optional<Tag> tag = tool.getWorkflowVersions().stream().filter(version -> Objects.equals(name, version.getName())).findFirst();

        if (tag.isPresent()) {
            return tag.get().isValid();
        }
        return false;
    }

    /**
     * Tests that we properly validate WDL workflows
     * Requires GitHub Repo DockstoreTestUser2/TestEntryValidation, master branch
     */
    @Test
    void testWdlWorkflow() {
        // Setup webservice and get workflows api
        WorkflowsApi workflowsApi = setupWorkflowWebService();

        // Register a workflow
        Workflow workflow = workflowsApi
            .manualRegister("GitHub", "DockstoreTestUser2/TestEntryValidation", "/validWorkflow.wdl", "testname", "wdl", "/test.json");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertTrue(isWorkflowVersionValid(workflow, "master"), "Should be valid");

        // change to empty wdl - should be invalid
        workflow.setWorkflowPath("empty.wdl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.updateWorkflowPath(workflow.getId(), workflow);
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertFalse(isWorkflowVersionValid(workflow, "master"));

        // change to missing import - should be invalid
        workflow.setWorkflowPath("/missingImport.wdl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.updateWorkflowPath(workflow.getId(), workflow);
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertFalse(isWorkflowVersionValid(workflow, "master"));

        // change to missing workflow section - should be invalid
        workflow.setWorkflowPath("/missingWorkflowSection.wdl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.updateWorkflowPath(workflow.getId(), workflow);
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertFalse(isWorkflowVersionValid(workflow, "master"));

        // change to valid tool - should be valid
        workflow.setWorkflowPath("/validTool.wdl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.updateWorkflowPath(workflow.getId(), workflow);
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertTrue(isWorkflowVersionValid(workflow, "master"), "Should be valid");

        // change back to valid workflow - should be valid
        workflow.setWorkflowPath("/validWorkflow.wdl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.updateWorkflowPath(workflow.getId(), workflow);
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertTrue(isWorkflowVersionValid(workflow, "master"), "Should be valid");

        // add invalid test json - should be invalid
        List<String> testParameterFiles = new ArrayList<>();
        testParameterFiles.add("/invalidJson.json");
        workflowsApi.addTestParameterFiles1(workflow.getId(), "", testParameterFiles, "master");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertFalse(isWorkflowVersionValid(workflow, "master"));
        workflowsApi.deleteTestParameterFiles1(workflow.getId(), testParameterFiles, "master");
        workflow = workflowsApi.refresh1(workflow.getId(), false);

        // add valid test json - should again be valid
        testParameterFiles = new ArrayList<>();
        testParameterFiles.add("/valid.json");
        workflowsApi.addTestParameterFiles1(workflow.getId(), "",  testParameterFiles, "master");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertTrue(isWorkflowVersionValid(workflow, "master"), "Should be valid");
    }

    /**
     * Tests that we properly validate CWL workflows
     * Requires GitHub Repo DockstoreTestUser2/TestEntryValidation, master branch
     */
    @Test
    void testCwlWorkflow() {
        // Setup webservice and get workflows api
        WorkflowsApi workflowsApi = setupWorkflowWebService();

        // Register a workflow
        workflowsApi.getApiClient().setDebugging(true);
        Workflow workflow = workflowsApi
            .manualRegister("GitHub", "DockstoreTestUser2/TestEntryValidation", "/validWorkflow.cwl", "testname", "cwl", "/test.json");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertTrue(isWorkflowVersionValid(workflow, "master"), "Should be valid");

        // change to empty cwl - should be invalid
        workflow.setWorkflowPath("/empty.cwl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.updateWorkflowPath(workflow.getId(), workflow);
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertFalse(isWorkflowVersionValid(workflow, "master"));

        // change to wrong version - should be invalid
        workflow.setWorkflowPath("/wrongVersion.cwl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.updateWorkflowPath(workflow.getId(), workflow);
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertFalse(isWorkflowVersionValid(workflow, "master"));

        // change to tool - should be invalid
        workflow.setWorkflowPath("/validTool.cwl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.updateWorkflowPath(workflow.getId(), workflow);
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertFalse(isWorkflowVersionValid(workflow, "master"));

        // change back to valid workflow - should be valid
        workflow.setWorkflowPath("/validWorkflow.cwl");
        workflow = workflowsApi.updateWorkflow(workflow.getId(), workflow);
        workflowsApi.updateWorkflowPath(workflow.getId(), workflow);
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertTrue(isWorkflowVersionValid(workflow, "master"), "Should be valid");

        // add invalid test json - should be invalid
        List<String> testParameterFiles = new ArrayList<>();
        testParameterFiles.add("/invalidJson.json");
        workflowsApi.addTestParameterFiles1(workflow.getId(), "", testParameterFiles, "master");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertFalse(isWorkflowVersionValid(workflow, "master"));
        workflowsApi.deleteTestParameterFiles1(workflow.getId(), testParameterFiles, "master");
        workflow = workflowsApi.refresh1(workflow.getId(), false);

        // add valid test json - should again be valid
        testParameterFiles = new ArrayList<>();
        testParameterFiles.add("/valid.json");
        workflowsApi.addTestParameterFiles1(workflow.getId(), "", testParameterFiles, "master");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertTrue(isWorkflowVersionValid(workflow, "master"), "Should be valid");
    }

    /**
     * This tests that validation works as expected on tools for CWL and WDL
     * Requires GitHub Repo DockstoreTestUser2/TestEntryValidation, master branch
     */
    @Test
    void testTool() {
        // Setup webservice and get tool api
        ContainersApi toolsApi = setupToolWebService();

        // Register tool, should be valid
        DockstoreTool tool = getTool();
        tool = toolsApi.registerManual(tool);
        assertNotNull(tool.getLicenseInformation().getLicenseName());
        tool = toolsApi.refresh(tool.getId());
        assertTrue(isTagValid(tool, "master"), "Should be valid");

        // Change to a workflow (not a valid tool)
        tool.setDefaultCwlPath("/validWorkflow.cwl");
        tool = toolsApi.updateContainer(tool.getId(), tool);
        toolsApi.updateTagContainerPath(tool.getId(), tool);
        tool = toolsApi.refresh(tool.getId());
        assertFalse(isTagValid(tool, "master"));

        // Change to invalid cwl version
        tool.setDefaultCwlPath("/wrongVersion.cwl");
        tool = toolsApi.updateContainer(tool.getId(), tool);
        toolsApi.updateTagContainerPath(tool.getId(), tool);
        tool = toolsApi.refresh(tool.getId());
        assertFalse(isTagValid(tool, "master"));

        // Change to valid cwl tool
        tool.setDefaultCwlPath("/validTool.cwl");
        tool = toolsApi.updateContainer(tool.getId(), tool);
        toolsApi.updateTagContainerPath(tool.getId(), tool);
        tool = toolsApi.refresh(tool.getId());
        assertTrue(isTagValid(tool, "master"), "Should be valid");

        // Add invalid json cwl - should be invalid
        List<String> testParameterFiles = new ArrayList<>();
        testParameterFiles.add("/invalidJson.json");
        toolsApi.addTestParameterFiles(tool.getId(), "", testParameterFiles,"master", "CWL");
        tool = toolsApi.refresh(tool.getId());
        assertFalse(isTagValid(tool, "master"));
        toolsApi.deleteTestParameterFiles(tool.getId(), testParameterFiles, "CWL", "master");
        tool = toolsApi.refresh(tool.getId());

        // Add valid json cwl - should be valid
        testParameterFiles = new ArrayList<>();
        testParameterFiles.add("/valid.json");
        toolsApi.addTestParameterFiles(tool.getId(),  "", testParameterFiles, "master", "CWL");
        tool = toolsApi.refresh(tool.getId());
        assertTrue(isTagValid(tool, "master"), "Should be valid");
        toolsApi.deleteTestParameterFiles(tool.getId(), testParameterFiles, "CWL", "master");
        tool = toolsApi.refresh(tool.getId());

        // add invalid wdl - should be valid
        tool.setDefaultWdlPath("/empty.wdl");
        tool = toolsApi.updateContainer(tool.getId(), tool);
        toolsApi.updateTagContainerPath(tool.getId(), tool);
        tool = toolsApi.refresh(tool.getId());
        assertTrue(isTagValid(tool, "master"), "Should be valid");

        // change cwl to invalid - should be invalid
        tool.setDefaultCwlPath("/invalidTool.cwl");
        tool = toolsApi.updateContainer(tool.getId(), tool);
        toolsApi.updateTagContainerPath(tool.getId(), tool);
        tool = toolsApi.refresh(tool.getId());
        assertFalse(isTagValid(tool, "master"));

        // make wdl valid - should be valid
        tool.setDefaultWdlPath("/validTool.wdl");
        tool = toolsApi.updateContainer(tool.getId(), tool);
        toolsApi.updateTagContainerPath(tool.getId(), tool);
        tool = toolsApi.refresh(tool.getId());
        assertTrue(isTagValid(tool, "master"), "Should be valid");

        // make wdl missing docker - should be invalid
        tool.setDefaultWdlPath("/missingDocker.wdl");
        tool = toolsApi.updateContainer(tool.getId(), tool);
        toolsApi.updateTagContainerPath(tool.getId(), tool);
        tool = toolsApi.refresh(tool.getId());
        assertFalse(isTagValid(tool, "master"));

        // make wdl missing import - should be invalid
        tool.setDefaultWdlPath("/missingImport.wdl");
        tool = toolsApi.updateContainer(tool.getId(), tool);
        toolsApi.updateTagContainerPath(tool.getId(), tool);
        tool = toolsApi.refresh(tool.getId());
        assertFalse(isTagValid(tool, "master"));

        // make wdl missing workflow section - should be invalid
        tool.setDefaultWdlPath("/missingWorkflowSection.wdl");
        tool = toolsApi.updateContainer(tool.getId(), tool);
        toolsApi.updateTagContainerPath(tool.getId(), tool);
        tool = toolsApi.refresh(tool.getId());
        assertFalse(isTagValid(tool, "master"));

        // make wdl which is valid workflow, not tool - should be invalid
        tool.setDefaultWdlPath("/validWorkflow.wdl");
        tool = toolsApi.updateContainer(tool.getId(), tool);
        toolsApi.updateTagContainerPath(tool.getId(), tool);
        tool = toolsApi.refresh(tool.getId());
        assertFalse(isTagValid(tool, "master"));

        // make wdl valid again - should be valid
        tool.setDefaultWdlPath("/validTool.wdl");
        tool = toolsApi.updateContainer(tool.getId(), tool);
        toolsApi.updateTagContainerPath(tool.getId(), tool);
        tool = toolsApi.refresh(tool.getId());
        assertTrue(isTagValid(tool, "master"), "Should be valid");

        // make invalid json wdl - should be invalid
        testParameterFiles = new ArrayList<>();
        testParameterFiles.add("/invalidJson.json");
        toolsApi.addTestParameterFiles(tool.getId(), "", testParameterFiles, "master", "WDL");
        tool = toolsApi.refresh(tool.getId());
        assertFalse(isTagValid(tool, "master"));
        toolsApi.deleteTestParameterFiles(tool.getId(), testParameterFiles, "WDL", "master");
        tool = toolsApi.refresh(tool.getId());

        // make valid json wdl - should be valid
        testParameterFiles = new ArrayList<>();
        testParameterFiles.add("/valid.json");
        toolsApi.addTestParameterFiles(tool.getId(), "", testParameterFiles, "master", "WDL");
        tool = toolsApi.refresh(tool.getId());
        assertTrue(isTagValid(tool, "master"), "Should be valid");
        toolsApi.deleteTestParameterFiles(tool.getId(), testParameterFiles, "WDL", "master");
        tool = toolsApi.refresh(tool.getId());
    }

    /**
     * This tests that validation works as expected on services
     * Requires GitHub Repo DockstoreTestUser2/test-service, missingFile branch
     */
    @Test
    void testService() {
        WorkflowsApi client = setupWorkflowWebService();
        String serviceRepo = "DockstoreTestUser2/test-service";
        String installationId = "1179416";

        // Add a service with a dockstore.yml that lists a file that is missing in the repository - should be invalid
        client.handleGitHubRelease(serviceRepo, "DockstoreTestUser2", "refs/heads/missingFile", installationId);
        long workflowCount = testingPostgres.runSelectStatement("select count(*) from service", long.class);
        assertEquals(1, workflowCount);
        Workflow service = client.getWorkflowByPath("github.com/" + serviceRepo, WorkflowSubClass.SERVICE, "versions");
        assertFalse(isWorkflowVersionValid(service, "missingFile"), "Should be invalid due to missing file in dockstore.yml");
    }

    @Test
    void readMePathTest() {
        final List<String> readMePaths = new ArrayList<>(
                Arrays.asList("README.md", "readme.md", "/README.md", "/readme.md", "README", "readme", "/README", "/readme"));
        readMePaths.forEach(readMePath -> {
            assertTrue(SourceCodeRepoInterface.matchesREADME(readMePath), readMePath);
        });
    }
}
