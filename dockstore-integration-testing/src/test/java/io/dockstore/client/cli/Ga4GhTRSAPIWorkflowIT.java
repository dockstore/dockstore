/*
 *    Copyright 2022 OICR and UCSC
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.jdbi.FileDAO;
import io.openapi.model.DescriptorTypeWithPlain;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.EntriesApi;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Entry;
import io.swagger.client.model.FileFormat;
import io.swagger.client.model.FileWrapper;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag.ReferenceTypeEnum;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolFile;
import io.swagger.client.model.ToolFile.FileTypeEnum;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.Workflow.DescriptorTypeEnum;
import io.swagger.client.model.WorkflowVersion;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
class Ga4GhTRSAPIWorkflowIT extends BaseIT {
    public static final String DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore_workflow_cnv";
    private static final String DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL =
        Registry.QUAY_IO.getDockerPath() + "/dockstoretestuser2/dockstore-cgpmap";
    private static final String DOCKSTORE_TEST_USER2_MORE_IMPORT_STRUCTURE =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/workflow-seq-import";
    private static final String GATK_SV_TAG = "dockstore-test";

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());


    private FileDAO fileDAO;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.fileDAO = new FileDAO(sessionFactory);

        // used to allow us to use workflowDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);

    }
    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    /**
     * Tests GA4GH endpoint. Ideally this would be in GA4GH*IT, but because we're manually registering
     * the workflow, putting it here
     */
    @Test
    void testGa4ghEndpointForComplexWdlWorkflow() throws IOException {
        final ApiClient ownerWebClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi ownerWorkflowApi = new WorkflowsApi(ownerWebClient);
        Workflow refresh = registerGatkSvWorkflow(ownerWorkflowApi);
        ownerWorkflowApi.publish(refresh.getId(), CommonTestUtilities.createPublishRequest(true));
        final List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(refresh.getWorkflowVersions().stream()
            .filter(workflowVersion -> GATK_SV_TAG.equals(workflowVersion.getName())).findFirst().get().getId());
        final Ga4GhApi ga4GhApi = new Ga4GhApi(ownerWebClient);
        final List<ToolFile> files = ga4GhApi
            .toolsIdVersionsVersionIdTypeFilesGet("WDL", "#workflow/" + refresh.getFullWorkflowPath(), GATK_SV_TAG);
        assertEquals(1, files.stream().filter(f -> f.getFileType() == FileTypeEnum.PRIMARY_DESCRIPTOR).count());
        assertEquals(sourceFiles.size() - 1, files.stream().filter(f -> f.getFileType() == FileTypeEnum.SECONDARY_DESCRIPTOR).count());
        files.forEach(file -> {
            final String path = file.getPath();
            // TRS paths are relative
            assertTrue(sourceFiles.stream().anyMatch(sf -> sf.getAbsolutePath().equals("/" + path)));
        });

        // test zip download via GA4GH TRS 2.0.1
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(getOpenAPIWebClient(USER_2_USERNAME, testingPostgres));
        final List<io.dockstore.openapi.client.model.ToolFile> toolFiles = ga4Ghv20Api.toolsIdVersionsVersionIdTypeFilesGet("#workflow/" + refresh.getFullWorkflowPath(),
            DescriptorTypeWithPlain.WDL.toString(), GATK_SV_TAG, null);
        byte[] arbitraryURL = CommonTestUtilities.getArbitraryURL(
            "/ga4gh/trs/v2/tools/" + URLEncoder.encode("#workflow/" + refresh.getFullWorkflowPath(), StandardCharsets.UTF_8) + "/versions/" + URLEncoder.encode(GATK_SV_TAG, StandardCharsets.UTF_8)
                + "/" + DescriptorTypeWithPlain.WDL
                + "/files?format=zip", new GenericType<>() {
                }, ownerWebClient);
        File tempZip = File.createTempFile("temp", "zip");
        Path write = Files.write(tempZip.toPath(), arbitraryURL);
        try (ZipFile zipFile = new ZipFile(write.toFile())) {
            assertTrue(zipFile.size() > 0, "zip file seems to have files");
            assertTrue(zipFile.stream().anyMatch(file -> file.getName().endsWith(".wdl")), "zip file seems to have wdl files");
            assertTrue(zipFile.stream().filter(file -> file.getName().endsWith(".wdl")).findFirst().get().getSize() > 0, "zip file seems to have a wdl file with stuff in it");
        }
        tempZip.deleteOnExit();

        // test combination of zip only with json return (should fail)
        try {
            CommonTestUtilities.getArbitraryURL(
                "/ga4gh/trs/v2/tools/" + URLEncoder.encode("#workflow/" + refresh.getFullWorkflowPath(), StandardCharsets.UTF_8) + "/versions/" + URLEncoder.encode(GATK_SV_TAG, StandardCharsets.UTF_8)
                + "/" + DescriptorTypeWithPlain.WDL
                + "/files?format=zip", new GenericType<>() {
                }, ownerWebClient, MediaType.APPLICATION_JSON);
            fail("should have died with bad request");
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode());
        }
    }

    /**
     * This test checks that a user can successfully refresh their workflows (only stubs).
     *
     * @throws ApiException
     */
    @Test
    void testRefreshAllForAUser() throws ApiException {

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        long userId = 1;

        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(webClient);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        io.dockstore.openapi.client.ApiClient openAPIWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        refreshByOrganizationReplacement(workflowApi, openAPIWebClient);

        List<Workflow> workflows = usersApi.userWorkflows(userId);

        // Check that there are multiple workflows
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertTrue(count > 0, "Workflow entries should exist");

        // Check that there are only stubs (no workflow version)
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertEquals(0, count2, "No entries in workflowversion");
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals(0, count3, "No workflows are in full mode");

        // check that a nextflow workflow made it
        long nfWorkflowCount = workflows.stream().filter(w -> w.getGitUrl().contains("mta-nf")).count();
        assertTrue(nfWorkflowCount > 0, "Nextflow workflow not found");
        Workflow mtaNf = workflows.stream().filter(w -> w.getGitUrl().contains("mta-nf")).findFirst().get();
        mtaNf.setWorkflowPath("/nextflow.config");
        mtaNf.setDescriptorType(DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(mtaNf.getId(), mtaNf);
        workflowApi.refresh(mtaNf.getId(), false);
        // publish this way? (why is the auto-generated variable private?)
        workflowApi.publish(mtaNf.getId(), CommonTestUtilities.createPublishRequest(true));
        mtaNf = workflowApi.getWorkflow(mtaNf.getId(), null);
        assertTrue(mtaNf.getLastModifiedDate() != null && mtaNf.getLastModified() != 0, "a workflow lacks a date");
        assertNotNull(mtaNf, "Nextflow workflow not found after update");
        assertTrue(mtaNf.getWorkflowVersions().size() >= 2, "nextflow workflow should have at least two versions");

        int numOfSourceFiles = mtaNf.getWorkflowVersions().stream().mapToInt(version -> fileDAO.findSourceFilesByVersion(version.getId()).size()).sum();
        assertTrue(numOfSourceFiles >= 2, "nextflow workflow should have at least two sourcefiles");

        long scriptCount = mtaNf.getWorkflowVersions().stream()
            .mapToLong(version -> fileDAO.findSourceFilesByVersion(version.getId()).stream().filter(file -> file.getType() == DescriptorLanguage.FileType.NEXTFLOW).count())
            .sum();

        long configCount = mtaNf.getWorkflowVersions().stream()
            .mapToLong(version -> fileDAO.findSourceFilesByVersion(version.getId()).stream().filter(file -> file.getType() == DescriptorLanguage.FileType.NEXTFLOW_CONFIG).count())
            .sum();
        assertTrue(scriptCount >= 1 && configCount >= 1, "nextflow workflow should have at least one config file and one script file");

        // check that we can pull down the nextflow workflow via the ga4gh TRS API
        Ga4GhApi ga4Ghv2Api = new Ga4GhApi(webClient);
        List<Tool> toolV2s = ga4Ghv2Api.toolsGet(null, null, null, null, null, null, null, null, null, null, null);
        String mtaWorkflowID = "#workflow/github.com/DockstoreTestUser2/mta-nf";
        Tool toolV2 = ga4Ghv2Api.toolsIdGet(mtaWorkflowID);
        assertTrue(toolV2s.size() > 0 && toolV2s.stream().anyMatch(tool -> Objects.equals(tool.getId(), mtaWorkflowID)), "could get mta as part of list");
        assertNotNull(toolV2, "could get mta as a specific tool");

        // Check that a workflow from my namespace is present
        assertTrue(workflows.stream().anyMatch((Workflow workflow) -> workflow.getOrganization().equalsIgnoreCase("DockstoreTestUser2")),
            "Should have at least one repo from DockstoreTestUser2.");

        // Check that a workflow from an organization I belong to is present
        assertTrue(workflows.stream().anyMatch(
            (Workflow workflow) -> workflow.getOrganization().equalsIgnoreCase("dockstoretesting") && workflow.getRepository()
                .equalsIgnoreCase("basic-workflow")), "Should have repository basic-workflow from organization dockstoretesting.");

        // Check that a workflow that I am a collaborator on is present
        assertTrue(workflows.stream().anyMatch(
            (Workflow workflow) -> workflow.getOrganization().equalsIgnoreCase("DockstoreTestUser") && workflow.getRepository()
                .equalsIgnoreCase("dockstore-whalesay-2")), "Should have repository dockstore-whalesay-2 from DockstoreTestUser.");

        // Check that for a repo from my organization that I forked to DockstoreTestUser2, that it along with the original repo are present
        assertTrue(2 <= workflows.stream().filter((Workflow workflow) ->
            (workflow.getOrganization().equalsIgnoreCase("dockstoretesting") || workflow.getOrganization()
                .equalsIgnoreCase("DockstoreTestUser2")) && workflow.getRepository().equalsIgnoreCase("basic-workflow")).count(),
            "Should have two repos with name basic-workflow, one from DockstoreTestUser2 and one from dockstoretesting.");

    }

    /**
     * Tests for https://github.com/dockstore/dockstore/issues/2154
     */
    @Test
    void testMoreCWLImportsStructure() throws ApiException, URISyntaxException, IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi
            .manualRegister("github", "DockstoreTestUser2/workflow-seq-import", "/cwls/chksum_seqval_wf_interleaved_fq.cwl", "", "cwl",
                "/examples/chksum_seqval_wf_interleaved_fq.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_MORE_IMPORT_STRUCTURE, BIOWORKFLOW, null);

        workflowApi.refresh(workflowByPathGithub.getId(), false);
        assertEquals("GNU General Public License v3.0", workflowByPathGithub.getLicenseInformation().getLicenseName());
        workflowApi.publish(workflowByPathGithub.getId(), CommonTestUtilities.createPublishRequest(true));

        // check on URLs for workflows via ga4gh calls
        Ga4GhApi ga4Ghv2Api = new Ga4GhApi(webClient);
        FileWrapper toolDescriptor = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_MORE_IMPORT_STRUCTURE, "0.4.0");
        String content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        assertFalse(content.isEmpty());
        // check slashed paths
        checkForRelativeFile(ga4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_MORE_IMPORT_STRUCTURE, "0.4.0",
            "toolkit/if_input_is_bz2_convert_to_gz_else_just_rename.cwl");
        checkForRelativeFile(ga4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_MORE_IMPORT_STRUCTURE, "0.4.0",
            "toolkit/if_file_name_is_bz2_then_return_null_else_return_in_json_to_output.cwl");
        checkForRelativeFile(ga4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_MORE_IMPORT_STRUCTURE, "0.4.0",
            "../examples/chksum_seqval_wf_interleaved_fq.json");
    }

    // working on https://github.com/dockstore/dockstore/issues/3335
    @Test
    void testWeirdPathCase() throws ApiException, URISyntaxException, IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi
            .manualRegister("github", "dockstore-testing/viral-pipelines", "/pipes/WDL/workflows/multi_sample_assemble_kraken.wdl", "", "wdl",
                "");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath("github.com/dockstore-testing/viral-pipelines", BIOWORKFLOW, null);

        workflowApi.refresh(workflowByPathGithub.getId(), false);
        workflowApi.publish(workflowByPathGithub.getId(), CommonTestUtilities.createPublishRequest(true));

        // check on URLs for workflows via ga4gh calls
        Ga4GhApi ga4Ghv2Api = new Ga4GhApi(webClient);
        FileWrapper toolDescriptor = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("WDL", "#workflow/github.com/dockstore-testing/viral-pipelines", "test_path");
        String content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        assertFalse(content.isEmpty());
        // check relative path below the main descriptor
        checkForRelativeFile(ga4Ghv2Api, "#workflow/" + "github.com/dockstore-testing/viral-pipelines", "test_path",
            "../tasks/tasks_assembly.wdl");
    }


    /**
     * Tests manual registration of a tool and check that descriptors are downloaded properly.
     * Description is pulled properly from an $include.
     *
     * @throws ApiException exception used for errors coming back from the web service
     */
    @Test
    void testManualRegisterToolWithMixinsAndSymbolicLinks() throws ApiException, URISyntaxException, IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);

        DockstoreTool tool = new DockstoreTool();
        tool.setDefaultDockerfilePath("/Dockerfile");
        tool.setDefaultCwlPath("/cwls/cgpmap-bamOut.cwl");
        tool.setGitUrl("git@github.com:DockstoreTestUser2/dockstore-cgpmap.git");
        tool.setNamespace("dockstoretestuser2");
        tool.setName("dockstore-cgpmap");
        tool.setRegistryString(Registry.QUAY_IO.getDockerPath());
        tool.setDefaultVersion("symbolic.v1");

        DockstoreTool registeredTool = toolApi.registerManual(tool);
        registeredTool = toolApi.refresh(registeredTool.getId());

        // Make publish request (true)
        final PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        toolApi.publish(registeredTool.getId(), publishRequest);

        // look that branches and tags are typed correctly for tools
        assertTrue(registeredTool.getWorkflowVersions().stream().filter(version -> version.getReferenceType() == ReferenceTypeEnum.BRANCH)
            .count() >= 1, "should see at least 6 branches");
        assertTrue(registeredTool.getWorkflowVersions().stream().filter(version -> version.getReferenceType() == ReferenceTypeEnum.TAG).count()
            >= 2, "should see at least 6 tags");

        assertTrue(registeredTool.getDescription().contains("A Docker container for PCAP-core."), "did not pick up description from $include");
        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(registeredTool.getWorkflowVersions().stream().filter(tag -> Objects.equals(tag.getName(), "test.v1")).findFirst().get().getId());
        assertEquals(5, sourceFiles.size(), "did not import mixin and includes properly");
        sourceFiles = fileDAO.findSourceFilesByVersion(registeredTool.getWorkflowVersions().stream().filter(tag -> Objects.equals(tag.getName(), "symbolic.v1")).findFirst().get().getId());
        assertEquals(5, sourceFiles.size(), "did not import symbolic links to folders properly");
        // check that commit ids look properly recorded
        // check on commit ids for github
        boolean allHaveCommitIds = registeredTool.getWorkflowVersions().stream().noneMatch(version -> version.getCommitID().isEmpty());
        assertTrue(allHaveCommitIds, "not all tools seem to have commit ids");

        // check on URLs for workflows via ga4gh calls
        Ga4GhApi ga4Ghv2Api = new Ga4GhApi(webClient);
        FileWrapper toolDescriptor = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL, "symbolic.v1");
        String content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        assertFalse(content.isEmpty());
        // check slashed paths (this doesn't seem to make sense, the leading slash seems to indicate this is relative to the root)
        //        checkForRelativeFile(ga4Ghv2Api, DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL, "symbolic.v1", "/cgpmap-bamOut.cwl");
        // a true absolute path would seem to be
        checkForRelativeFile(ga4Ghv2Api, DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL, "symbolic.v1", "/cwls/cgpmap-bamOut.cwl");
        // check paths without slash
        checkForRelativeFile(ga4Ghv2Api, DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL, "symbolic.v1", "cgpmap-bamOut.cwl");
        // check other secondaries and the dockerfile
        checkForRelativeFile(ga4Ghv2Api, DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL, "symbolic.v1", "includes/doc.yml");
        checkForRelativeFile(ga4Ghv2Api, DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL, "symbolic.v1", "mixins/hints.yml");
        checkForRelativeFile(ga4Ghv2Api, DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL, "symbolic.v1", "mixins/requirements.yml");
        checkForRelativeFile(ga4Ghv2Api, DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL, "symbolic.v1", "/Dockerfile");
    }

    /**
     * Checks that a file can be received from TRS and passes through a valid URL to github, bitbucket, etc.
     *
     * @param ga4Ghv2Api
     * @param dockstoreTestUser2RelativeImportsTool
     * @param reference
     * @param filename
     * @throws IOException
     * @throws URISyntaxException
     */
    private void checkForRelativeFile(Ga4GhApi ga4Ghv2Api, String dockstoreTestUser2RelativeImportsTool, String reference, String filename)
        throws IOException, URISyntaxException {
        FileWrapper toolDescriptor;
        String content;
        toolDescriptor = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorRelativePathGet("CWL", dockstoreTestUser2RelativeImportsTool, reference, filename);
        content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        assertFalse(content.isEmpty());
    }

    @Test
    void testRelativeSecondaryFileOperations() throws ApiException, URISyntaxException, IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, BIOWORKFLOW, null);

        // This checks if a workflow whose default name was manually registered as an empty string would become null
        assertNull(workflowByPathGithub.getWorkflowName());

        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId(), false);

        // Test that the secondary file's input file formats are recognized (secondary file is varscan_cnv.cwl)
        List<FileFormat> fileFormats = workflow.getInputFileFormats();
        List<WorkflowVersion> workflowVersionsForFileFormat = workflow.getWorkflowVersions();
        assertTrue(workflowVersionsForFileFormat.stream().anyMatch(workflowVersion -> workflowVersion.getInputFileFormats().stream()
            .anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_2572"))));
        assertTrue(workflowVersionsForFileFormat.stream().anyMatch(workflowVersion -> workflowVersion.getInputFileFormats().stream()
            .anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_1929"))));
        assertTrue(workflowVersionsForFileFormat.stream().anyMatch(workflowVersion -> workflowVersion.getInputFileFormats().stream()
            .anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_3003"))));
        assertTrue(fileFormats.stream().anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_2572")));
        assertTrue(fileFormats.stream().anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_1929")));
        assertTrue(fileFormats.stream().anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_3003")));
        assertTrue(workflowVersionsForFileFormat.stream().anyMatch(workflowVersion -> workflowVersion.getOutputFileFormats().stream()
            .anyMatch(fileFormat -> fileFormat.getValue().equals("file://fakeFileFormat"))));
        assertTrue(workflow.getOutputFileFormats().stream().anyMatch(fileFormat -> fileFormat.getValue().equals("file://fakeFileFormat")));

        // This checks if a workflow whose default name is null would remain as null after refresh
        assertNull(workflow.getWorkflowName());

        // test out methods to access secondary files

        final List<SourceFile> masterImports = workflowApi
            .secondaryDescriptors(workflow.getId(), "master", DescriptorLanguage.CWL.toString());
        assertEquals(3, masterImports.size(), "should find 3 imports, found " + masterImports.size());
        final List<SourceFile> rootImports = workflowApi
            .secondaryDescriptors(workflow.getId(), "rootTest", DescriptorLanguage.CWL.toString());
        assertEquals(0, rootImports.size(), "should find 0 imports, found " + rootImports.size());

        // next, change a path for the root imports version
        List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        workflowVersions.stream().filter(v -> v.getName().equals("rootTest")).findFirst().get().setWorkflowPath("/cnv.cwl");
        workflowApi.updateWorkflowVersion(workflow.getId(), workflowVersions);
        workflowApi.refresh(workflowByPathGithub.getId(), false);
        final List<SourceFile> newMasterImports = workflowApi
            .secondaryDescriptors(workflow.getId(), "master", DescriptorLanguage.CWL.toString());
        assertEquals(3, newMasterImports.size(), "should find 3 imports, found " + newMasterImports.size());
        final List<SourceFile> newRootImports = workflowApi
            .secondaryDescriptors(workflow.getId(), "rootTest", DescriptorLanguage.CWL.toString());
        assertEquals(3, newRootImports.size(), "should find 3 imports, found " + newRootImports.size());

        workflowApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));
        // check on URLs for workflows via ga4gh calls
        Ga4GhApi ga4Ghv2Api = new Ga4GhApi(webClient);
        FileWrapper toolDescriptor = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        String content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        assertFalse(content.isEmpty());
        checkForRelativeFile(ga4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master", "adtex.cwl");
        // ignore extra separators, broken as side effect fix for of https://github.com/dockstore/dockstore/issues/3335
        // checkForRelativeFile(ga4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master", "/adtex.cwl");
        // test json should use relative path with ".."
        checkForRelativeFile(ga4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master", "../test.json");
        List<ToolFile> toolFiles = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeFilesGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        assertTrue(toolFiles.size() >= 5, "should have at least 5 files");
        assertTrue(toolFiles.stream().filter(toolFile -> !toolFile.getPath().startsWith("/")).count() >= 5, "all files should have relative paths");

        // check on urls created for test files
        List<FileWrapper> toolTests = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeTestsGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        assertTrue(toolTests.size() > 0, "could not find tool tests");
        for (FileWrapper test : toolTests) {
            content = IOUtils.toString(new URI(test.getUrl()), StandardCharsets.UTF_8);
            assertFalse(content.isEmpty());
        }
    }

    @Test
    void testAnonAndAdminGA4GH() throws ApiException, URISyntaxException, IOException {
        WorkflowsApi workflowApi = new WorkflowsApi(getWebClient(USER_2_USERNAME, testingPostgres));
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, BIOWORKFLOW, null);
        workflowApi.refresh(workflowByPathGithub.getId(), false);

        // should not be able to get content normally
        Ga4GhApi anonymousGa4Ghv2Api = new Ga4GhApi(CommonTestUtilities.getWebClient(false, null, testingPostgres));
        boolean thrownException = false;
        try {
            anonymousGa4Ghv2Api
                .toolsIdVersionsVersionIdTypeFilesGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        } catch (ApiException e) {
            thrownException = true;
        }
        assert (thrownException);

        boolean thrownListException = false;
        try {
            anonymousGa4Ghv2Api
                .toolsIdVersionsVersionIdTypeTestsGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        } catch (ApiException e) {
            thrownListException = true;
        }
        assert (thrownListException);

        // can get content via admin user
        Ga4GhApi adminGa4Ghv2Api = new Ga4GhApi(getWebClient(USER_2_USERNAME, testingPostgres));

        List<ToolFile> toolFiles = adminGa4Ghv2Api
            .toolsIdVersionsVersionIdTypeFilesGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        assertTrue(toolFiles.size() >= 5, "should have at least 5 files");

        // cannot get relative paths anonymously
        toolFiles.forEach(file -> {
            boolean thrownInnerException = false;
            try {
                anonymousGa4Ghv2Api.toolsIdVersionsVersionIdTypeDescriptorRelativePathGet("CWL",
                    "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master", file.getPath());
            } catch (ApiException e) {
                thrownInnerException = true;
            }
            assertTrue(thrownInnerException);
        });

        final AtomicInteger count = new AtomicInteger(0);
        // can get relative paths with admin user
        toolFiles.forEach(file -> {
            if (file.getFileType() == ToolFile.FileTypeEnum.TEST_FILE) {
                // enable later with a simplification to TRS
                FileWrapper test = adminGa4Ghv2Api.toolsIdVersionsVersionIdTypeDescriptorRelativePathGet("CWL",
                    "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master", file.getPath());
                assertFalse(test.getContent().isEmpty());
                count.incrementAndGet();
            } else if (file.getFileType() == ToolFile.FileTypeEnum.PRIMARY_DESCRIPTOR
                || file.getFileType() == ToolFile.FileTypeEnum.SECONDARY_DESCRIPTOR) {
                // annoyingly, some files are tool tests, some are tooldescriptor
                FileWrapper toolDescriptor = adminGa4Ghv2Api.toolsIdVersionsVersionIdTypeDescriptorRelativePathGet("CWL",
                    "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master", file.getPath());
                assertFalse(toolDescriptor.getContent().isEmpty());
                count.incrementAndGet();
            } else {
                fail();
            }
        });
        assertTrue(count.get() >= 5, "did not count expected (5) number of files, got" + count.get());
    }

    @Test
    void testAliasOperations() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, BIOWORKFLOW, null);
        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId(), false);
        workflowApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));

        Workflow md5workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker",
            "/checker-workflow-wrapping-workflow.cwl", "test", "cwl", null);
        workflowApi.refresh(md5workflow.getId(), false);
        workflowApi.publish(md5workflow.getId(), CommonTestUtilities.createPublishRequest(true));

        // give the workflow a few aliases
        EntriesApi genericApi = new EntriesApi(webClient);
        Entry entry = genericApi.addAliases(workflow.getId(), "awesome workflow, spam, test workflow");
        assertTrue(entry.getAliases().containsKey("awesome workflow") && entry.getAliases().containsKey("spam") && entry.getAliases()
            .containsKey("test workflow"), "entry is missing expected aliases");

        Workflow workflowById = workflowApi.getWorkflow(entry.getId(), null);
        assertNotNull(workflowById.getAliases(), "Getting workflow by ID has null alias");

        // check that the aliases work in TRS search
        Ga4GhApi ga4GhApi = new Ga4GhApi(webClient);
        // this generated code is mucho silly
        List<Tool> workflows = ga4GhApi.toolsGet(null, null, null, null, null, null, null, null, null, null, 100);
        assertEquals(2, workflows.size(), "expected workflows not found");
        List<Tool> awesomeWorkflow = ga4GhApi.toolsGet(null, "awesome workflow", null, null, null, null, null, null, null, null, 100);
        assertTrue(awesomeWorkflow.size() == 1 && awesomeWorkflow.get(0).getAliases().size() == 3, "workflow was not found or didn't have expected aliases");
        // add a few new aliases
        entry = genericApi.addAliases(workflow.getId(), "foobar, another workflow");
        assertTrue(entry.getAliases().containsKey("foobar") && entry.getAliases().containsKey("test workflow") && entry.getAliases().size() == 5, "entry is missing expected aliases");

        // try to add duplicates; this is not allowed
        boolean throwsError = false;
        try {
            // add a few new aliases
            entry = genericApi.addAliases(workflow.getId(), "another workflow");
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to add a duplicate Workflow alias.");
        }

        // Get workflow by alias
        Workflow aliasWorkflow = workflowApi.getWorkflowByAlias("foobar");
        assertNotNull(aliasWorkflow, "Should retrieve the workflow by alias");
    }


}
