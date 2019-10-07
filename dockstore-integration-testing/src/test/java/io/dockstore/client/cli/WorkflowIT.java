/*
 *    Copyright 2018 OICR
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.ws.rs.core.GenericType;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.EntriesApi;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Entry;
import io.swagger.client.model.FileFormat;
import io.swagger.client.model.FileWrapper;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolFile;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.Workflow.DescriptorTypeEnum;
import io.swagger.client.model.WorkflowVersion;
import io.swagger.model.DescriptorType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Extra confidential integration tests, focus on testing workflow interactions
 * {@link io.dockstore.client.cli.BaseIT}
 *
 * @author dyuen
 */
@Category({ ConfidentialTest.class, WorkflowTest.class })
public class WorkflowIT extends BaseIT {
    public static final String DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow";
    public static final String DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore_workflow_cnv";
    private static final String DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW =
        SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow";
    private static final String DOCKSTORE_TEST_USER2_IMPORTS_DOCKSTORE_WORKFLOW =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore-whalesay-imports";
    private static final String DOCKSTORE_TEST_USER2_GDC_DNASEQ_CWL_WORKFLOW =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/gdc-dnaseq-cwl";
    // workflow with external library in lib directory
    private static final String DOCKSTORE_TEST_USER2_NEXTFLOW_LIB_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/rnatoy";
    // workflow that uses containers
    private static final String DOCKSTORE_TEST_USER2_NEXTFLOW_DOCKER_WORKFLOW =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/galaxy-workflows";
    // workflow with includeConfig in config file directory
    private static final String DOCKSTORE_TEST_USER2_INCLUDECONFIG_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/vipr";
    private static final String DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL =
        Registry.QUAY_IO.toString() + "/dockstoretestuser2/dockstore-cgpmap";
    private static final String DOCKSTORE_TEST_USER2_MORE_IMPORT_STRUCTURE =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/workflow-seq-import";
    private static final String GATK_SV_TAG = "dockstore-test";
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private final String clientConfig = ResourceHelpers.resourceFilePath("clientConfig");
    private final String jsonFilePath = ResourceHelpers.resourceFilePath("wc-job.json");

    private WorkflowDAO workflowDAO;
    private Session session;

    @Before
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.workflowDAO = new WorkflowDAO(sessionFactory);

        // used to allow us to use workflowDAO outside of the web service
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }
    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    @Test
    public void testStubRefresh() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();

        final List<Workflow> workflows = usersApi.refreshWorkflows(user.getId());
        for (Workflow workflow : workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        assertTrue("workflow size was " + workflows.size(), workflows.size() > 1);
        assertTrue(
            "found non stub workflows " + workflows.stream().filter(workflow -> workflow.getMode() != Workflow.ModeEnum.STUB).count(),
            workflows.stream().allMatch(workflow -> workflow.getMode() == Workflow.ModeEnum.STUB));
    }

    @Test
    public void testTargettedRefresh() throws ApiException, URISyntaxException, IOException {

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();
        Assert.assertNotEquals("getUser() endpoint should actually return the user profile", null, user.getUserProfiles());

        final List<Workflow> workflows = usersApi.refreshWorkflows(user.getId());

        for (Workflow workflow : workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, null, false);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());
        final Workflow workflowByPathBitbucket = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW, null, false);
        final Workflow refreshBitbucket = workflowApi.refresh(workflowByPathBitbucket.getId());

        // tests for reference type for bitbucket workflows
        assertTrue("should see at least 4 branches", refreshBitbucket.getWorkflowVersions().stream()
            .filter(version -> version.getReferenceType() == WorkflowVersion.ReferenceTypeEnum.BRANCH).count() >= 4);
        assertTrue("should see at least 1 tags", refreshBitbucket.getWorkflowVersions().stream()
            .filter(version -> version.getReferenceType() == WorkflowVersion.ReferenceTypeEnum.TAG).count() >= 1);

        assertSame("github workflow is not in full mode", refreshGithub.getMode(), Workflow.ModeEnum.FULL);
        assertEquals("github workflow version count is wrong: " + refreshGithub.getWorkflowVersions().size(), 4,
            refreshGithub.getWorkflowVersions().size());
        assertEquals("should find two versions with files for github workflow, found : " + refreshGithub.getWorkflowVersions().stream()
                .filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count(), 2,
            refreshGithub.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count());
        assertEquals("should find two valid versions for github workflow, found : " + refreshGithub.getWorkflowVersions().stream()
                .filter(WorkflowVersion::isValid).count(), 2,
            refreshGithub.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).count());

        assertSame("bitbucket workflow is not in full mode", refreshBitbucket.getMode(), Workflow.ModeEnum.FULL);

        assertEquals("bitbucket workflow version count is wrong: " + refreshBitbucket.getWorkflowVersions().size(), 5,
            refreshBitbucket.getWorkflowVersions().size());
        assertEquals("should find 4 versions with files for bitbucket workflow, found : " + refreshBitbucket.getWorkflowVersions().stream()
                .filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count(), 4,
            refreshBitbucket.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count());
        assertEquals("should find 0 valid versions for bitbucket workflow, found : " + refreshBitbucket.getWorkflowVersions().stream()
                .filter(WorkflowVersion::isValid).count(), 0,
            refreshBitbucket.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).count());

        // should not be able to get content normally
        Ga4GhApi anonymousGa4Ghv2Api = new Ga4GhApi(CommonTestUtilities.getWebClient(false, null, testingPostgres));
        Ga4GhApi adminGa4Ghv2Api = new Ga4GhApi(webClient);
        boolean exceptionThrown = false;
        try {
            anonymousGa4Ghv2Api
                .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, "master");
        } catch (ApiException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        FileWrapper adminToolDescriptor = adminGa4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, "testCWL");
        assertTrue("could not get content via optional auth", adminToolDescriptor != null && !adminToolDescriptor.getContent().isEmpty());

        workflowApi.publish(refreshGithub.getId(), SwaggerUtility.createPublishRequest(true));
        // check on URLs for workflows via ga4gh calls
        FileWrapper toolDescriptor = adminGa4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, "testCWL");
        String content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        assertFalse(content.isEmpty());
        checkForRelativeFile(adminGa4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, "testCWL", "Dockstore.cwl");

        // check on commit ids for github
        boolean allHaveCommitIds = refreshGithub.getWorkflowVersions().stream().noneMatch(version -> version.getCommitID().isEmpty());
        assertTrue("not all workflows seem to have commit ids", allHaveCommitIds);
    }

    @Test
    public void testHostedDelete() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();
        usersApi.refreshWorkflows(user.getId());
        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, null, false);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());

        // using hosted apis to edit normal workflows should fail
        HostedApi hostedApi = new HostedApi(webClient);
        thrown.expect(ApiException.class);
        hostedApi.deleteHostedWorkflowVersion(refreshGithub.getId(), "v1.0");
    }

    @Test
    public void testHostedEdit() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();
        usersApi.refreshWorkflows(user.getId());
        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, null, false);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());

        // using hosted apis to edit normal workflows should fail
        HostedApi hostedApi = new HostedApi(webClient);
        thrown.expect(ApiException.class);
        hostedApi.editHostedWorkflow(refreshGithub.getId(), new ArrayList<>());
    }

    @Test
    public void testWorkflowLaunchOrNotLaunchBasedOnCredentials() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker",
            "/checker-workflow-wrapping-workflow.cwl", "test", "cwl", null);
        assertEquals("There should be one user of the workflow after manually registering it.", 1, workflow.getUsers().size());
        Workflow refresh = workflowApi.refresh(workflow.getId());

        assertFalse(refresh.isIsPublished());

        // should be able to launch properly with correct credentials even though the workflow is not published
        FileUtils.writeStringToFile(new File("md5sum.input"), "foo", StandardCharsets.UTF_8);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry", toolpath,
                "--json", ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json"), "--script" });

        // should not be able to launch properly with incorrect credentials
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "workflow", "launch", "--entry", toolpath,
                "--json", ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json"), "--script" });
    }

    /**
     * This tests workflow convert entry2json when the main descriptor is nested far within the GitHub repo with secondary descriptors too
     */
    @Test
    public void testEntryConvertWDLWithSecondaryDescriptors() {
        String toolpath = SourceControl.GITHUB.toString() + "/dockstore-testing/skylab";
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "dockstore-testing/skylab",
            "/pipelines/smartseq2_single_sample/SmartSeq2SingleSample.wdl", "", "wdl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId());
        workflowApi.publish(refresh.getId(), SwaggerUtility.createPublishRequest(true));

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "convert", "entry2json", "--entry",
                toolpath + ":Dockstore_Testing", "--script" });
        List<String> stringList = new ArrayList<>();
        stringList.add("\"SmartSeq2SingleCell.gtf_file\": \"File\"");
        stringList.add("\"SmartSeq2SingleCell.genome_ref_fasta\": \"File\"");
        stringList.add("\"SmartSeq2SingleCell.rrna_intervals\": \"File\"");
        stringList.add("\"SmartSeq2SingleCell.fastq2\": \"File\"");
        stringList.add("\"SmartSeq2SingleCell.hisat2_ref_index\": \"File\"");
        stringList.add("\"SmartSeq2SingleCell.hisat2_ref_trans_name\": \"String\"");
        stringList.add("\"SmartSeq2SingleCell.stranded\": \"String\"");
        stringList.add("\"SmartSeq2SingleCell.sample_name\": \"String\"");
        stringList.add("\"SmartSeq2SingleCell.output_name\": \"String\"");
        stringList.add("\"SmartSeq2SingleCell.fastq1\": \"File\"");
        stringList.add("\"SmartSeq2SingleCell.hisat2_ref_trans_index\": \"File\"");
        stringList.add("\"SmartSeq2SingleCell.hisat2_ref_name\": \"String\"");
        stringList.add("\"SmartSeq2SingleCell.rsem_ref_index\": \"File\"");
        stringList.add("\"SmartSeq2SingleCell.gene_ref_flat\": \"File\"");
        stringList.forEach(string -> Assert.assertTrue(systemOutRule.getLog().contains(string)));
    }

    /**
     * This tests that you are able to download zip files for versions of a workflow
     */
    @Test
    public void downloadZipFile() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // Register and refresh workflow
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", "cwl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId());
        Long workflowId = refresh.getId();
        WorkflowVersion workflowVersion = refresh.getWorkflowVersions().get(0);
        Long versionId = workflowVersion.getId();

        // Download unpublished workflow version
        workflowApi.getWorkflowZip(workflowId, versionId);
        byte[] arbitraryURL = SwaggerUtility.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
        }, webClient);
        Path write = Files.write(File.createTempFile("temp", "zip").toPath(), arbitraryURL);
        ZipFile zipFile = new ZipFile(write.toFile());
        assertTrue("zip file seems incorrect",
            zipFile.stream().map(ZipEntry::getName).collect(Collectors.toList()).contains("md5sum/md5sum-workflow.cwl"));

        // should not be able to get zip anonymously before publication
        boolean thrownException = false;
        try {
            SwaggerUtility.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
            }, CommonTestUtilities.getWebClient(false, null, testingPostgres));
        } catch (Exception e) {
            thrownException = true;
        }
        assertTrue(thrownException);

        // Download published workflow version
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", toolpath,
                "--script" });
        arbitraryURL = SwaggerUtility.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
        }, CommonTestUtilities.getWebClient(false, null, testingPostgres));
        write = Files.write(File.createTempFile("temp", "zip").toPath(), arbitraryURL);
        zipFile = new ZipFile(write.toFile());
        assertTrue("zip file seems incorrect",
            zipFile.stream().map(ZipEntry::getName).collect(Collectors.toList()).contains("md5sum/md5sum-workflow.cwl"));

        // download and unzip via CLI
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "download", "--entry",
            toolpath + ":" + workflowVersion.getName(), "--script" });
        zipFile.stream().forEach((ZipEntry entry) -> {
            if (!(entry).isDirectory()) {
                File innerFile = new File(System.getProperty("user.dir"), entry.getName());
                assert (innerFile.exists());
                assert (innerFile.delete());
            }
        });

        // download zip via CLI
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "download", "--entry",
            toolpath + ":" + workflowVersion.getName(), "--zip", "--script" });
        File downloadedZip = new File(new WorkflowClient(null, null, null, false).zipFilename(workflow));
        assert (downloadedZip.exists());
        assert (downloadedZip.delete());
    }

    /**
     * This tests that zip file can be downloaded or not based on published state and auth.
     */
    @Test
    public void downloadZipFileTestAuth() {
        final ApiClient ownerWebClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi ownerWorkflowApi = new WorkflowsApi(ownerWebClient);

        final ApiClient anonWebClient = CommonTestUtilities.getWebClient(false, null, testingPostgres);
        WorkflowsApi anonWorkflowApi = new WorkflowsApi(anonWebClient);

        final ApiClient otherUserWebClient = CommonTestUtilities.getWebClient(true, OTHER_USERNAME, testingPostgres);
        WorkflowsApi otherUserWorkflowApi = new WorkflowsApi(otherUserWebClient);

        // Register and refresh workflow
        Workflow workflow = ownerWorkflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", "cwl", null);
        Workflow refresh = ownerWorkflowApi.refresh(workflow.getId());
        Long workflowId = refresh.getId();
        Long versionId = refresh.getWorkflowVersions().get(0).getId();

        // Try downloading unpublished
        // Owner: Should pass
        ownerWorkflowApi.getWorkflowZip(workflowId, versionId);
        // Anon: Should fail
        boolean success = true;
        try {
            anonWorkflowApi.getWorkflowZip(workflowId, versionId);
        } catch (ApiException ex) {
            success = false;
        } finally {
            assertFalse(success);
        }
        // Other user: Should fail
        success = true;
        try {
            otherUserWorkflowApi.getWorkflowZip(workflowId, versionId);
        } catch (ApiException ex) {
            success = false;
        } finally {
            assertFalse(success);
        }

        // Publish
        PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        ownerWorkflowApi.publish(workflowId, publishRequest);

        // Try downloading published
        // Owner: Should pass
        ownerWorkflowApi.getWorkflowZip(workflowId, versionId);
        // Anon: Should pass
        anonWorkflowApi.getWorkflowZip(workflowId, versionId);
        // Other user: Should pass
        otherUserWorkflowApi.getWorkflowZip(workflowId, versionId);
    }

    /**
     * Downloads and verifies dockstore-testing/gatk-sv-clinical, a complex WDL workflow with lots
     * of imports
     */
    @Test
    public void downloadZipComplex() throws IOException {
        final ApiClient ownerWebClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi ownerWorkflowApi = new WorkflowsApi(ownerWebClient);
        Workflow refresh = registerGatkSvWorkflow(ownerWorkflowApi);

        Long workflowId = refresh.getId();
        Long versionId = refresh.getWorkflowVersions().get(0).getId();

        // Try downloading unpublished
        // Owner: Should pass
        ownerWorkflowApi.getWorkflowZip(workflowId, versionId);

        // Unfortunately, the generated Swagger client for getWorkflowZip returns void.
        // Verify the zip contents by making the server side calls
        final io.dockstore.webservice.core.Workflow dockstoreWorkflow = workflowDAO.findById(workflowId);
        final Optional<io.dockstore.webservice.core.WorkflowVersion> version = dockstoreWorkflow.getWorkflowVersions().stream()
                .filter(wv -> wv.getId() == versionId).findFirst();
        Assert.assertTrue(version.isPresent());
        final File tempFile = File.createTempFile("dockstore-test", ".zip");
        try {
            final FileOutputStream fos = new FileOutputStream(tempFile);
            final Set<io.dockstore.webservice.core.SourceFile> sourceFiles = version.get().getSourceFiles();
            new EntryVersionHelperImpl().writeStreamAsZip(sourceFiles, fos, Paths.get("/GATKSVPipelineClinical.wdl"));
            final ZipFile zipFile = new ZipFile(tempFile);
            final long wdlCount = zipFile.stream().filter(e -> e.getName().endsWith(".wdl")).count();
            Assert.assertEquals(sourceFiles.size(), wdlCount);
            zipFile.stream().filter(e -> e.getName().endsWith(".wdl")).forEach(e -> {
                final String name = "/" + e.getName();
                Assert.assertTrue("expected " + name, sourceFiles.stream().anyMatch(sf -> sf.getAbsolutePath().equals(name)));
            });
            zipFile.close();
        } finally {
            FileUtils.deleteQuietly(tempFile);
        }
    }

    /**
     * Tests GA4GH endpoint. Ideally this would be in GA4GH*IT, but because we're manually registering
     * the workflow, putting it here
     */
    @Test
    public void testGa4ghEndpointForComplexWdlWorkflow() {
        final ApiClient ownerWebClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi ownerWorkflowApi = new WorkflowsApi(ownerWebClient);
        Workflow refresh = registerGatkSvWorkflow(ownerWorkflowApi);
        ownerWorkflowApi.publish(refresh.getId(), SwaggerUtility.createPublishRequest(true));
        final List<SourceFile> sourceFiles = refresh.getWorkflowVersions().stream()
                .filter(workflowVersion -> GATK_SV_TAG.equals(workflowVersion.getName())).findFirst().get().getSourceFiles();
        final Ga4GhApi ga4GhApi = new Ga4GhApi(ownerWebClient);
        final List<ToolFile> files = ga4GhApi
                .toolsIdVersionsVersionIdTypeFilesGet("WDL", "#workflow/" + refresh.getFullWorkflowPath(), GATK_SV_TAG);
        Assert.assertEquals(1, files.stream().filter(f -> f.getFileType() == ToolFile.FileTypeEnum.PRIMARY_DESCRIPTOR).count());
        Assert.assertEquals(sourceFiles.size() - 1, files.stream().filter(f -> f.getFileType() == ToolFile.FileTypeEnum.SECONDARY_DESCRIPTOR).count());
        files.stream().forEach(file -> {
            final String path = file.getPath();
            // TRS paths are relative
            Assert.assertTrue(sourceFiles.stream().anyMatch(sf -> sf.getAbsolutePath().equals("/" + path)));
        });
    }

    @Test
    public void testCheckerWorkflowDownloadBasedOnCredentials() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", "cwl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId());
        assertFalse(refresh.isIsPublished());
        workflowApi.registerCheckerWorkflow("checker-workflow-wrapping-workflow.cwl", workflow.getId(), "cwl", "checker-input-cwl.json");
        workflowApi.refresh(workflow.getId());

        final String fileWithIncorrectCredentials = ResourceHelpers.resourceFilePath("config_file.txt");
        final String fileWithCorrectCredentials = ResourceHelpers.resourceFilePath("config_file2.txt");

        // should be able to download properly with correct credentials even though the workflow is not published
        FileUtils.writeStringToFile(new File("md5sum.input"), "foo", StandardCharsets.UTF_8);
        Client.main(
            new String[] { "--config", fileWithCorrectCredentials, "checker", "download", "--entry", toolpath, "--version", "master",
                "--script" });

        // Publish the workflow
        Client.main(new String[] { "--config", fileWithCorrectCredentials, "workflow", "publish", "--entry", toolpath, "--script" });

        // should be able to download properly with incorrect credentials because the entry is published
        Client.main(
            new String[] { "--config", fileWithIncorrectCredentials, "checker", "download", "--entry", toolpath, "--version", "master",
                "--script" });

        // Unpublish the workflow
        Client.main(
            new String[] { "--config", fileWithCorrectCredentials, "workflow", "publish", "--entry", toolpath, "--unpub", "--script" });

        // should not be able to download properly with incorrect credentials because the entry is not published
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(
            new String[] { "--config", fileWithIncorrectCredentials, "checker", "download", "--entry", toolpath, "--version", "master",
                "--script" });
    }

    @Test
    public void testCheckerWorkflowLaunchBasedOnCredentials() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                "test", "cwl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId());
        Assert.assertFalse(refresh.isIsPublished());

        workflowApi.registerCheckerWorkflow("/checker-workflow-wrapping-workflow.cwl", workflow.getId(), "cwl", "checker-input-cwl.json");

        workflowApi.refresh(workflow.getId());

        // should be able to launch properly with correct credentials even though the workflow is not published
        FileUtils.writeStringToFile(new File("md5sum.input"), "foo", StandardCharsets.UTF_8);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "checker", "launch", "--entry", toolpath,
                "--json", ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json"), "--script" });

        // should be able to launch properly with incorrect credentials but the entry is published
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", toolpath,
                "--script" });
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "checker", "launch", "--entry", toolpath,
                "--json", ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json"), "--script" });

        // should not be able to launch properly with incorrect credentials
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", toolpath,
                "--unpub", "--script" });
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "checker", "launch", "--entry", toolpath,
                "--json", ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json"), "--script" });
    }

    @Test
    public void testNextflowRefresh() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();

        final List<Workflow> workflows = usersApi.refreshWorkflows(user.getId());

        for (Workflow workflow : workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_NEXTFLOW_LIB_WORKFLOW, null, false);
        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_NEXTFLOW_LIB_WORKFLOW, null, false);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());

        assertSame("github workflow is not in full mode", refreshGithub.getMode(), Workflow.ModeEnum.FULL);

        // look that branches and tags are typed correctly for workflows on GitHub
        assertTrue("should see at least 6 branches", refreshGithub.getWorkflowVersions().stream()
            .filter(version -> version.getReferenceType() == WorkflowVersion.ReferenceTypeEnum.BRANCH).count() >= 6);
        assertTrue("should see at least 6 tags", refreshGithub.getWorkflowVersions().stream()
            .filter(version -> version.getReferenceType() == WorkflowVersion.ReferenceTypeEnum.TAG).count() >= 6);

        assertEquals("github workflow version count is wrong: " + refreshGithub.getWorkflowVersions().size(), 12,
            refreshGithub.getWorkflowVersions().size());
        assertEquals("should find 12 versions with files for github workflow, found : " + refreshGithub.getWorkflowVersions().stream()
                .filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count(), 12,
            refreshGithub.getWorkflowVersions().stream().filter(workflowVersion -> !workflowVersion.getSourceFiles().isEmpty()).count());
        assertEquals("should find 12 valid versions for github workflow, found : " + refreshGithub.getWorkflowVersions().stream()
                .filter(WorkflowVersion::isValid).count(), 12,
            refreshGithub.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).count());

        // nextflow version should have
        assertTrue("should find 2 files for each version for now: " + refreshGithub.getWorkflowVersions().stream()
                .filter(workflowVersion -> workflowVersion.getSourceFiles().size() != 2).count(),
            refreshGithub.getWorkflowVersions().stream().noneMatch(workflowVersion -> workflowVersion.getSourceFiles().size() != 2));

        // check that container is properly parsed
        Optional<WorkflowVersion> nextflow = refreshGithub.getWorkflowVersions().stream()
            .filter(workflow -> workflow.getName().equals("master")).findFirst();
        String workflowDag = workflowApi.getWorkflowDag(refreshGithub.getId(), nextflow.get().getId());
        ArrayList<String> dagList = Lists.newArrayList(workflowDag);

        Assert.assertTrue("Should use nextflow/rnatoy and not ubuntu:latest", dagList.get(0)
            .contains("\"docker\":\"nextflow/rnatoy@sha256:9ac0345b5851b2b20913cb4e6d469df77cf1232bafcadf8fd929535614a85c75\""));
    }

    @Test
    public void testNextflowWorkflowWithConfigIncludes() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();

        final List<Workflow> workflows = usersApi.refreshWorkflows(user.getId());

        for (Workflow workflow : workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_INCLUDECONFIG_WORKFLOW, null, false);
        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_INCLUDECONFIG_WORKFLOW, null, false);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());

        assertEquals("workflow does not include expected config included files", 3,
            refreshGithub.getWorkflowVersions().stream().filter(version -> version.getName().equals("master")).findFirst().get()
                .getSourceFiles().stream().filter(file -> file.getPath().startsWith("conf/")).count());
    }

    @Test
    public void testNextflowWorkflowWithImages() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();

        final List<Workflow> workflows = usersApi.refreshWorkflows(user.getId());

        for (Workflow workflow : workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_NEXTFLOW_DOCKER_WORKFLOW, null, false);
        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_NEXTFLOW_DOCKER_WORKFLOW, null, false);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());

        assertSame("github workflow is not in full mode", refreshGithub.getMode(), Workflow.ModeEnum.FULL);
        Optional<WorkflowVersion> first = refreshGithub.getWorkflowVersions().stream().filter(version -> version.getName().equals("1.0"))
            .findFirst();
        String tableToolContent = workflowApi.getTableToolContent(refreshGithub.getId(), first.get().getId());
        String workflowDag = workflowApi.getWorkflowDag(refreshGithub.getId(), first.get().getId());
        assertFalse(tableToolContent.isEmpty());
        assertFalse(workflowDag.isEmpty());
        Gson gson = new Gson();
        List<Map<String, String>> list = gson.fromJson(tableToolContent, List.class);
        Map<Map, List> map = gson.fromJson(workflowDag, Map.class);
        assertTrue("tool table should be present", list.size() >= 9);
        long dockerCount = list.stream().filter(tool -> !tool.get("docker").isEmpty()).count();
        assertEquals("tool table is populated with docker images", dockerCount, list.size());
        assertTrue("workflow dag should be present", map.entrySet().size() >= 2);
        assertTrue("workflow dag is not as large as expected", map.get("nodes").size() >= 11 && map.get("edges").size() >= 13);
    }

    /**
     * This test checks that a user can successfully refresh their workflows (only stubs)
     *
     * @throws ApiException
     */
    @Test
    public void testRefreshAllForAUser() throws ApiException {

        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        long userId = 1;

        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(webClient);
        final List<Workflow> workflows = usersApi.refreshWorkflows(userId);

        // Check that there are multiple workflows
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertTrue("Workflow entries should exist", count > 0);

        // Check that there are only stubs (no workflow version)
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertEquals("No entries in workflowversion", 0, count2);
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals("No workflows are in full mode", 0, count3);

        // check that a nextflow workflow made it
        long nfWorkflowCount = workflows.stream().filter(w -> w.getGitUrl().contains("mta-nf")).count();
        assertTrue("Nextflow workflow not found", nfWorkflowCount > 0);
        Workflow mtaNf = workflows.stream().filter(w -> w.getGitUrl().contains("mta-nf")).findFirst().get();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        mtaNf.setWorkflowPath("/nextflow.config");
        mtaNf.setDescriptorType(DescriptorTypeEnum.NFL);
        workflowApi.updateWorkflow(mtaNf.getId(), mtaNf);
        workflowApi.refresh(mtaNf.getId());
        // publish this way? (why is the auto-generated variable private?)
        workflowApi.publish(mtaNf.getId(), SwaggerUtility.createPublishRequest(true));
        mtaNf = workflowApi.getWorkflow(mtaNf.getId(), null);
        Assert.assertTrue("a workflow lacks a date", mtaNf.getLastModifiedDate() != null && mtaNf.getLastModified() != 0);
        assertNotNull("Nextflow workflow not found after update", mtaNf);
        assertTrue("nextflow workflow should have at least two versions", mtaNf.getWorkflowVersions().size() >= 2);
        int numOfSourceFiles = mtaNf.getWorkflowVersions().stream().mapToInt(version -> version.getSourceFiles().size()).sum();
        assertTrue("nextflow workflow should have at least two sourcefiles", numOfSourceFiles >= 2);
        long scriptCount = mtaNf.getWorkflowVersions().stream()
            .mapToLong(version -> version.getSourceFiles().stream().filter(file -> file.getType() == SourceFile.TypeEnum.NEXTFLOW).count())
            .sum();
        long configCount = mtaNf.getWorkflowVersions().stream().mapToLong(
            version -> version.getSourceFiles().stream().filter(file -> file.getType() == SourceFile.TypeEnum.NEXTFLOW_CONFIG).count())
            .sum();
        assertTrue("nextflow workflow should have at least one config file and one script file", scriptCount >= 1 && configCount >= 1);

        // check that we can pull down the nextflow workflow via the ga4gh TRS API
        Ga4GhApi ga4Ghv2Api = new Ga4GhApi(webClient);
        List<Tool> toolV2s = ga4Ghv2Api.toolsGet(null, null, null, null, null, null, null, null, null, null, null);
        String mtaWorkflowID = "#workflow/github.com/DockstoreTestUser2/mta-nf";
        Tool toolV2 = ga4Ghv2Api.toolsIdGet(mtaWorkflowID);
        assertTrue("could get mta as part of list",
            toolV2s.size() > 0 && toolV2s.stream().anyMatch(tool -> Objects.equals(tool.getId(), mtaWorkflowID)));
        assertNotNull("could get mta as a specific tool", toolV2);

        // Check that a workflow from my namespace is present
        assertTrue("Should have at least one repo from DockstoreTestUser2.",
            workflows.stream().anyMatch((Workflow workflow) -> workflow.getOrganization().equalsIgnoreCase("DockstoreTestUser2")));

        // Check that a workflow from an organization I belong to is present
        assertTrue("Should have repository basic-workflow from organization dockstoretesting.", workflows.stream().anyMatch(
            (Workflow workflow) -> workflow.getOrganization().equalsIgnoreCase("dockstoretesting") && workflow.getRepository()
                .equalsIgnoreCase("basic-workflow")));

        // Check that a workflow that I am a collaborator on is present
        assertTrue("Should have repository dockstore-whalesay-2 from DockstoreTestUser.", workflows.stream().anyMatch(
            (Workflow workflow) -> workflow.getOrganization().equalsIgnoreCase("DockstoreTestUser") && workflow.getRepository()
                .equalsIgnoreCase("dockstore-whalesay-2")));

        // Check that for a repo from my organization that I forked to DockstoreTestUser2, that it along with the original repo are present
        assertEquals("Should have two repos with name basic-workflow, one from DockstoreTestUser2 and one from dockstoretesting.", 2,
            workflows.stream().filter((Workflow workflow) ->
                (workflow.getOrganization().equalsIgnoreCase("dockstoretesting") || workflow.getOrganization()
                    .equalsIgnoreCase("DockstoreTestUser2")) && workflow.getRepository().equalsIgnoreCase("basic-workflow")).count());

    }

    /**
     * This test does not use admin rights, note that a number of operations go through the UserApi to get this to work
     *
     * @throws ApiException exception used for errors coming back from the web service
     */
    @Test
    public void testPublishingAndListingOfPublished() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // should start with nothing published
        assertTrue("should start with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false).isEmpty());
        // refresh just for the current user
        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();
        usersApi.refreshWorkflows(userId);
        assertTrue("should remain with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false).isEmpty());
        // ensure that sorting or filtering don't expose unpublished workflows
        assertTrue("should start with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, null, "descriptorType", "asc", false).isEmpty());
        assertTrue("should start with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, "hello", null, null, false).isEmpty());
        assertTrue("should start with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, "hello", "descriptorType", "asc", false).isEmpty());

        // assertTrue("should have a bunch of stub workflows: " +  usersApi..allWorkflows().size(), workflowApi.allWorkflows().size() == 4);

        final Workflow workflowByPath = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, null, false);
        // refresh targeted
        workflowApi.refresh(workflowByPath.getId());

        // publish one
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish(workflowByPath.getId(), publishRequest);
        assertEquals("should have one published, found  " + workflowApi.allPublishedWorkflows(null, null, null, null, null, false).size(),
            1, workflowApi.allPublishedWorkflows(null, null, null, null, null, false).size());
        final Workflow publishedWorkflow = workflowApi.getPublishedWorkflow(workflowByPath.getId(), null);
        assertNotNull("did not get published workflow", publishedWorkflow);
        final Workflow publishedWorkflowByPath = workflowApi
            .getPublishedWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, null, false);
        assertNotNull("did not get published workflow", publishedWorkflowByPath);

        // publish everything so pagination testing makes more sense (going to unfortunately use rate limit)
        Lists.newArrayList("github.com/DockstoreTestUser2/hello-dockstore-workflow",
            "github.com/DockstoreTestUser2/dockstore-whalesay-imports", "github.com/DockstoreTestUser2/parameter_test_workflow")
            .forEach(path -> {
                Workflow workflow = workflowApi.getWorkflowByPath(path, null, false);
                workflowApi.refresh(workflow.getId());
                workflowApi.publish(workflow.getId(), publishRequest);
            });
        List<Workflow> workflows = workflowApi.allPublishedWorkflows(null, null, null, null, null, false);
        // test offset
        assertEquals("offset does not seem to be working",
            workflowApi.allPublishedWorkflows("1", null, null, null, null, false).get(0).getId(), workflows.get(1).getId());
        // test limit
        assertEquals(1, workflowApi.allPublishedWorkflows(null, 1, null, null, null, false).size());
        // test custom sort column
        List<Workflow> ascId = workflowApi.allPublishedWorkflows(null, null, null, "id", "asc", false);
        List<Workflow> descId = workflowApi.allPublishedWorkflows(null, null, null, "id", "desc", false);
        assertEquals("sort by id does not seem to be working", ascId.get(0).getId(), descId.get(descId.size() - 1).getId());
        // test filter
        List<Workflow> filteredLowercase = workflowApi.allPublishedWorkflows(null, null, "whale", "stars", null, false);
        assertEquals(1, filteredLowercase.size());
        List<Workflow> filteredUppercase = workflowApi.allPublishedWorkflows(null, null, "WHALE", "stars", null, false);
        assertEquals(1, filteredUppercase.size());
        assertEquals(filteredLowercase, filteredUppercase);
    }

    /**
     * Tests manual registration and publishing of a github and bitbucket workflow
     *
     * @throws ApiException exception used for errors coming back from the web service
     */
    @Test
    public void testManualRegisterThenPublish() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);

        // Set up postgres

        // Get workflows
        usersApi.refreshWorkflows(userId);

        // Manually register workflow github
        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.wdl", "altname", "wdl", "/test.json");

        // Manually register workflow bitbucket
        Workflow bitbucketWorkflow = workflowApi
            .manualRegister("bitbucket", "dockstore_testuser2/dockstore-workflow", "/Dockstore.cwl", "altname", "cwl", "/test.json");

        // Assert some things
        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals("No workflows are in full mode", 0, count);
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflow where workflowname = 'altname'", long.class);
        assertEquals("There should be two workflows with name altname, there are " + count2, 2, count2);

        // Publish github workflow
        workflowApi.refresh(githubWorkflow.getId());
        workflowApi.publish(githubWorkflow.getId(), publishRequest);

        // Assert some things
        assertEquals("should have two published, found  " + workflowApi.allPublishedWorkflows(null, null, null, null, null, false).size(),
            1, workflowApi.allPublishedWorkflows(null, null, null, null, null, false).size());
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals("One workflow is in full mode", 1, count3);
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid = 't'", long.class);
        assertEquals("There should be 2 valid version tags, there are " + count4, 2, count4);

        workflowApi.refresh(bitbucketWorkflow.getId());
        thrown.expect(ApiException.class);
        // Publish bitbucket workflow, will fail now since the workflow test case is actually invalid now
        workflowApi.publish(bitbucketWorkflow.getId(), publishRequest);
    }

    @Test
    public void testCreationOfIncorrectHostedWorkflowTypeGarbage() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        thrown.expect(ApiException.class);
        hostedApi.createHostedWorkflow("name", null, "garbage type", null, null);
    }

    @Test
    public void testDuplicateHostedWorkflowCreation() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        hostedApi.createHostedWorkflow("name", null, DescriptorType.CWL.toString(), null, null);
        thrown.expectMessage("already exists");
        hostedApi.createHostedWorkflow("name", null, DescriptorType.CWL.toString(), null, null);
    }

    @Test
    public void testDuplicateHostedToolCreation() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        hostedApi.createHostedTool("name", Registry.DOCKER_HUB.toString(), DescriptorType.CWL.toString(), "namespace", null);
        thrown.expectMessage("already exists");
        hostedApi.createHostedTool("name", Registry.DOCKER_HUB.toString(), DescriptorType.CWL.toString(), "namespace", null);
    }

    @Test
    public void testHostedWorkflowMetadataAndLaunch() throws IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("name", null, DescriptorType.CWL.toString(), null, null);
        assertNotNull(hostedWorkflow.getLastModifiedDate());
        assertNotNull(hostedWorkflow.getLastUpdated());

        // make a couple garbage edits
        SourceFile source = new SourceFile();
        source.setPath("/Dockstore.cwl");
        source.setAbsolutePath("/Dockstore.cwl");
        source.setContent("cwlVersion: v1.0\nclass: Workflow");
        source.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        SourceFile source1 = new SourceFile();
        source1.setPath("sorttool.cwl");
        source1.setContent("foo");
        source1.setAbsolutePath("/sorttool.cwl");
        source1.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        SourceFile source2 = new SourceFile();
        source2.setPath("revtool.cwl");
        source2.setContent("foo");
        source2.setAbsolutePath("/revtool.cwl");
        source2.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(source, source1, source2));

        source.setContent("cwlVersion: v1.0\nclass: Workflow");
        source1.setContent("food");
        source2.setContent("food");
        final Workflow updatedHostedWorkflow = hostedApi
            .editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(source, source1, source2));
        assertNotNull(updatedHostedWorkflow.getLastModifiedDate());
        assertNotNull(updatedHostedWorkflow.getLastUpdated());

        // note that this workflow contains metadata defined on the inputs to the workflow in the old (pre-map) CWL way that is still valid v1.0 CWL
        source.setContent(FileUtils
            .readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/Dockstore.cwl")), StandardCharsets.UTF_8));
        source1.setContent(
            FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/sorttool.cwl")), StandardCharsets.UTF_8));
        source2.setContent(
            FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("hosted_metadata/revtool.cwl")), StandardCharsets.UTF_8));
        Workflow workflow = hostedApi.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(source, source1, source2));
        assertFalse(workflow.getInputFileFormats().isEmpty());
        assertFalse(workflow.getOutputFileFormats().isEmpty());

        // launch the workflow, note that the latest version of the workflow should launch (i.e. the working one)
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry",
            workflow.getFullWorkflowPath(), "--json", ResourceHelpers.resourceFilePath("revsort-job.json"), "--script" });

        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        workflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));
        // should also launch successfully with the wrong credentials when published
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "workflow", "launch", "--entry",
            workflow.getFullWorkflowPath(), "--json", ResourceHelpers.resourceFilePath("revsort-job.json"), "--script" });
    }

    /**
     * This tests that a nested WDL workflow (three levels) is properly parsed
     *
     * @throws ApiException exception used for errors coming back from the web service
     */
    @Test
    public void testNestedWdlWorkflow() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // Set up postgres

        // Manually register workflow github
        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/nested-wdl", "/Dockstore.wdl", "altname", "wdl", "/test.json");

        // Assert some things
        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", long.class);
        assertEquals("No workflows are in full mode", 0, count);

        // Refresh the workflow
        workflowApi.refresh(githubWorkflow.getId());

        // Confirm that correct number of sourcefiles are found
        githubWorkflow = workflowApi.getWorkflow(githubWorkflow.getId(), null);
        List<WorkflowVersion> versions = githubWorkflow.getWorkflowVersions();
        assertEquals("There should be two versions", 2, versions.size());

        Optional<WorkflowVersion> loopVersion = versions.stream().filter(version -> Objects.equals(version.getReference(), "infinite-loop"))
            .findFirst();
        if (loopVersion.isPresent()) {
            assertEquals("There should be two sourcefiles", 2, loopVersion.get().getSourceFiles().size());
        } else {
            fail("Could not find version infinite-loop");
        }

        Optional<WorkflowVersion> masterVersion = versions.stream().filter(version -> Objects.equals(version.getReference(), "master"))
            .findFirst();
        if (masterVersion.isPresent()) {
            assertEquals("There should be three sourcefiles", 3, masterVersion.get().getSourceFiles().size());
        } else {
            fail("Could not find version master");
        }
    }

    /**
     * Tests for https://github.com/dockstore/dockstore/issues/2154
     */
    @Test
    public void testMoreCWLImportsStructure() throws ApiException, URISyntaxException, IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi
            .manualRegister("github", "DockstoreTestUser2/workflow-seq-import", "/cwls/chksum_seqval_wf_interleaved_fq.cwl", "", "cwl",
                "/examples/chksum_seqval_wf_interleaved_fq.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_MORE_IMPORT_STRUCTURE, null, false);

        workflowApi.refresh(workflowByPathGithub.getId());
        workflowApi.publish(workflowByPathGithub.getId(), SwaggerUtility.createPublishRequest(true));

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

    /**
     * Tests manual registration of a tool and check that descriptors are downloaded properly.
     * Description is pulled properly from an $include.
     *
     * @throws ApiException exception used for errors coming back from the web service
     */
    @Test
    public void testManualRegisterToolWithMixinsAndSymbolicLinks() throws ApiException, URISyntaxException, IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);

        DockstoreTool tool = new DockstoreTool();
        tool.setDefaultDockerfilePath("/Dockerfile");
        tool.setDefaultCwlPath("/cwls/cgpmap-bamOut.cwl");
        tool.setGitUrl("git@github.com:DockstoreTestUser2/dockstore-cgpmap.git");
        tool.setNamespace("dockstoretestuser2");
        tool.setName("dockstore-cgpmap");
        tool.setRegistryString(Registry.QUAY_IO.toString());
        tool.setDefaultVersion("symbolic.v1");

        DockstoreTool registeredTool = toolApi.registerManual(tool);
        registeredTool = toolApi.refresh(registeredTool.getId());

        // Make publish request (true)
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        toolApi.publish(registeredTool.getId(), publishRequest);

        // look that branches and tags are typed correctly for tools
        assertTrue("should see at least 6 branches",
            registeredTool.getWorkflowVersions().stream().filter(version -> version.getReferenceType() == Tag.ReferenceTypeEnum.BRANCH)
                .count() >= 1);
        assertTrue("should see at least 6 tags",
            registeredTool.getWorkflowVersions().stream().filter(version -> version.getReferenceType() == Tag.ReferenceTypeEnum.TAG).count()
                >= 2);

        assertTrue("did not pick up description from $include",
            registeredTool.getDescription().contains("A Docker container for PCAP-core."));
        assertEquals("did not import mixin and includes properly", 5,
            registeredTool.getWorkflowVersions().stream().filter(tag -> Objects.equals(tag.getName(), "test.v1")).findFirst().get()
                .getSourceFiles().size());
        assertEquals("did not import symbolic links to folders properly", 5,
            registeredTool.getWorkflowVersions().stream().filter(tag -> Objects.equals(tag.getName(), "symbolic.v1")).findFirst().get()
                .getSourceFiles().size());
        // check that commit ids look properly recorded
        // check on commit ids for github
        boolean allHaveCommitIds = registeredTool.getWorkflowVersions().stream().noneMatch(version -> version.getCommitID().isEmpty());
        assertTrue("not all tools seem to have commit ids", allHaveCommitIds);

        // check on URLs for workflows via ga4gh calls
        Ga4GhApi ga4Ghv2Api = new Ga4GhApi(webClient);
        FileWrapper toolDescriptor = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL, "symbolic.v1");
        String content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        assertFalse(content.isEmpty());
        // check slashed paths
        checkForRelativeFile(ga4Ghv2Api, DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL, "symbolic.v1", "/cgpmap-bamOut.cwl");
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

    /**
     * Tests that trying to register a duplicate workflow fails, and that registering a non-existant repository failes
     *
     * @throws ApiException exception used for errors coming back from the web service
     */
    @Test
    public void testManualRegisterErrors() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Get workflows
        usersApi.refreshWorkflows(userId);

        // Manually register workflow
        boolean success = true;
        try {
            workflowApi.manualRegister("github", "DockstoreTestUser2/hello-dockstore-workflow", "/Dockstore.wdl", "", "wdl", "/test.json");
        } catch (ApiException c) {
            success = false;
        } finally {
            assertFalse(success);
        }

        success = true;
        try {
            workflowApi.manualRegister("github", "dasn/iodnasiodnasio", "/Dockstore.wdl", "", "wdl", "/test.json");
        } catch (ApiException c) {
            success = false;
        } finally {
            assertFalse(success);
        }
    }

    @Test
    public void testSecondaryFileOperations() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore-whalesay-imports", "/Dockstore.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_IMPORTS_DOCKSTORE_WORKFLOW, null, false);

        // This checks if a workflow whose default name was manually registered as an empty string would become null
        assertNull(workflowByPathGithub.getWorkflowName());

        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());

        // This checks if a workflow whose default name is null would remain as null after refresh
        assertNull(workflow.getWorkflowName());

        // test out methods to access secondary files
        final List<SourceFile> masterImports = workflowApi
            .secondaryDescriptors(workflow.getId(), "master", DescriptorLanguage.CWL.toString());
        assertEquals("should find 2 imports, found " + masterImports.size(), 2, masterImports.size());
        final SourceFile master = workflowApi.primaryDescriptor(workflow.getId(), "master", DescriptorLanguage.CWL.toString());
        assertTrue("master content incorrect", master.getContent().contains("untar") && master.getContent().contains("compile"));

        // get secondary files by path
        SourceFile argumentsTool = workflowApi
            .secondaryDescriptorPath(workflow.getId(), "arguments.cwl", "master", DescriptorLanguage.CWL.toString());
        assertTrue("argumentstool content incorrect", argumentsTool.getContent().contains("Example trivial wrapper for Java 7 compiler"));
    }

    @Test
    public void testRelativeSecondaryFileOperations() throws ApiException, URISyntaxException, IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, null, false);

        // This checks if a workflow whose default name was manually registered as an empty string would become null
        assertNull(workflowByPathGithub.getWorkflowName());

        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());

        // Test that the secondary file's input file formats are recognized (secondary file is varscan_cnv.cwl)
        List<FileFormat> fileFormats = workflow.getInputFileFormats();
        List<WorkflowVersion> workflowVersionsForFileFormat = workflow.getWorkflowVersions();
        Assert.assertTrue(workflowVersionsForFileFormat.stream().anyMatch(workflowVersion -> workflowVersion.getInputFileFormats().stream()
            .anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_2572"))));
        Assert.assertTrue(workflowVersionsForFileFormat.stream().anyMatch(workflowVersion -> workflowVersion.getInputFileFormats().stream()
            .anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_1929"))));
        Assert.assertTrue(workflowVersionsForFileFormat.stream().anyMatch(workflowVersion -> workflowVersion.getInputFileFormats().stream()
            .anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_3003"))));
        Assert.assertTrue(fileFormats.stream().anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_2572")));
        Assert.assertTrue(fileFormats.stream().anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_1929")));
        Assert.assertTrue(fileFormats.stream().anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_3003")));
        Assert.assertTrue(workflowVersionsForFileFormat.stream().anyMatch(workflowVersion -> workflowVersion.getOutputFileFormats().stream()
            .anyMatch(fileFormat -> fileFormat.getValue().equals("file://fakeFileFormat"))));
        Assert.assertTrue(
            workflow.getOutputFileFormats().stream().anyMatch(fileFormat -> fileFormat.getValue().equals("file://fakeFileFormat")));

        // This checks if a workflow whose default name is null would remain as null after refresh
        assertNull(workflow.getWorkflowName());

        // test out methods to access secondary files

        final List<SourceFile> masterImports = workflowApi
            .secondaryDescriptors(workflow.getId(), "master", DescriptorLanguage.CWL.toString());
        assertEquals("should find 3 imports, found " + masterImports.size(), 3, masterImports.size());
        final List<SourceFile> rootImports = workflowApi
            .secondaryDescriptors(workflow.getId(), "rootTest", DescriptorLanguage.CWL.toString());
        assertEquals("should find 0 imports, found " + rootImports.size(), 0, rootImports.size());

        // next, change a path for the root imports version
        List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        workflowVersions.stream().filter(v -> v.getName().equals("rootTest")).findFirst().get().setWorkflowPath("/cnv.cwl");
        workflowApi.updateWorkflowVersion(workflow.getId(), workflowVersions);
        workflowApi.refresh(workflowByPathGithub.getId());
        final List<SourceFile> newMasterImports = workflowApi
            .secondaryDescriptors(workflow.getId(), "master", DescriptorLanguage.CWL.toString());
        assertEquals("should find 3 imports, found " + newMasterImports.size(), 3, newMasterImports.size());
        final List<SourceFile> newRootImports = workflowApi
            .secondaryDescriptors(workflow.getId(), "rootTest", DescriptorLanguage.CWL.toString());
        assertEquals("should find 3 imports, found " + newRootImports.size(), 3, newRootImports.size());

        workflowApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));
        // check on URLs for workflows via ga4gh calls
        Ga4GhApi ga4Ghv2Api = new Ga4GhApi(webClient);
        FileWrapper toolDescriptor = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        String content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        assertFalse(content.isEmpty());
        checkForRelativeFile(ga4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master", "adtex.cwl");
        // ignore extra separators
        checkForRelativeFile(ga4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master", "/adtex.cwl");
        // test json should use relative path with ".."
        checkForRelativeFile(ga4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master", "../test.json");
        List<ToolFile> toolFiles = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeFilesGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        assertTrue("should have at least 5 files", toolFiles.size() >= 5);
        assertTrue("all files should have relative paths",
            toolFiles.stream().filter(toolFile -> !toolFile.getPath().startsWith("/")).count() >= 5);

        // check on urls created for test files
        List<FileWrapper> toolTests = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeTestsGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        assertTrue("could not find tool tests", toolTests.size() > 0);
        for (FileWrapper test : toolTests) {
            content = IOUtils.toString(new URI(test.getUrl()), StandardCharsets.UTF_8);
            assertFalse(content.isEmpty());
        }
    }

    @Test

    public void testAnonAndAdminGA4GH() throws ApiException, URISyntaxException, IOException {
        WorkflowsApi workflowApi = new WorkflowsApi(getWebClient(USER_2_USERNAME, testingPostgres));
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, null, false);
        workflowApi.refresh(workflowByPathGithub.getId());

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
        assertTrue("should have at least 5 files", toolFiles.size() >= 5);

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
        assertTrue("did not count expected (5) number of files, got" + count.get(), count.get() >= 5);
    }

    @Test
    public void testAliasOperations() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, null, false);
        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());
        workflowApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));

        Workflow md5workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker",
            "/checker-workflow-wrapping-workflow.cwl", "test", "cwl", null);
        workflowApi.refresh(md5workflow.getId());
        workflowApi.publish(md5workflow.getId(), SwaggerUtility.createPublishRequest(true));

        // give the workflow a few aliases
        EntriesApi genericApi = new EntriesApi(webClient);
        Entry entry = genericApi.updateAliases(workflow.getId(), "awesome workflow, spam, test workflow", "");
        Assert.assertTrue("entry is missing expected aliases",
            entry.getAliases().containsKey("awesome workflow") && entry.getAliases().containsKey("spam") && entry.getAliases()
                .containsKey("test workflow"));
        // check that the aliases work in TRS search
        Ga4GhApi ga4GhApi = new Ga4GhApi(webClient);
        // this generated code is mucho silly
        List<Tool> workflows = ga4GhApi.toolsGet(null, null, null, null, null, null, null, null, null, null, 100);
        Assert.assertEquals("expected workflows not found", 2, workflows.size());
        List<Tool> awesomeWorkflow = ga4GhApi.toolsGet(null, "awesome workflow", null, null, null, null, null, null, null, null, 100);
        Assert.assertTrue("workflow was not found or didn't have expected aliases",
            awesomeWorkflow.size() == 1 && awesomeWorkflow.get(0).getAliases().size() == 3);
        // remove a few aliases
        entry = genericApi.updateAliases(workflow.getId(), "foobar, test workflow", "");
        Assert.assertTrue("entry is missing expected aliases",
            entry.getAliases().containsKey("foobar") && entry.getAliases().containsKey("test workflow") && entry.getAliases().size() == 2);

        // Get workflow by alias
        Workflow aliasWorkflow = workflowApi.getWorkflowByAlias("foobar");
        Assert.assertNotNull("Should retrieve the workflow by alias", aliasWorkflow);
    }

    /**
     * This tests that the absolute path is properly set for CWL workflow sourcefiles for the primary descriptor and any imported files
     */
    @Test
    public void testAbsolutePathForImportedFilesCWL() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/gdc-dnaseq-cwl", "/workflows/dnaseq/transform.cwl", "", "cwl",
            "/workflows/dnaseq/transform.cwl.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_GDC_DNASEQ_CWL_WORKFLOW, null, false);
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());

        Assert.assertEquals("should have 2 version", 2, workflow.getWorkflowVersions().size());
        Optional<WorkflowVersion> workflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> Objects.equals(version.getName(), "test")).findFirst();
        if (workflowVersion.isEmpty()) {
            Assert.fail("Missing the test release");
        }

        List<SourceFile> sourceFiles = workflowVersion.get().getSourceFiles();
        Optional<SourceFile> primarySourceFile = sourceFiles.stream().filter(
            sourceFile -> Objects.equals(sourceFile.getPath(), "/workflows/dnaseq/transform.cwl") && Objects
                .equals(sourceFile.getAbsolutePath(), "/workflows/dnaseq/transform.cwl")).findFirst();
        if (primarySourceFile.isEmpty()) {
            Assert.fail("Does not properly set the absolute path of the primary descriptor.");
        }

        Optional<SourceFile> importedSourceFileOne = sourceFiles.stream().filter(
            sourceFile -> Objects.equals(sourceFile.getPath(), "../../tools/bam_readgroup_to_json.cwl") && Objects
                .equals(sourceFile.getAbsolutePath(), "/tools/bam_readgroup_to_json.cwl")).findFirst();
        if (importedSourceFileOne.isEmpty()) {
            Assert.fail("Does not properly set the absolute path of the imported file.");
        }

        Optional<SourceFile> importedSourceFileTwo = sourceFiles.stream().filter(
            sourceFile -> Objects.equals(sourceFile.getPath(), "integrity.cwl") && Objects
                .equals(sourceFile.getAbsolutePath(), "/workflows/dnaseq/integrity.cwl")).findFirst();
        if (importedSourceFileTwo.isEmpty()) {
            Assert.fail("Does not properly set the absolute path of the imported file.");
        }
    }

    /**
     * NOTE: This test is not normally run. It is only for running locally to confirm that the discourse topic generation is working.
     * <p>
     * Adds a discourse topic for a workflow (adds to a Automatic Tool and Workflow Threads - NEED TO DELETE TOPIC)
     * <p>
     * Requires you to have the correct discourse information set in the dockstoreTest.yml
     */
    @Ignore
    public void publishWorkflowAndTestDiscourseTopicCreation() {
        final ApiClient curatorApiClient = getWebClient(curatorUsername, testingPostgres);
        EntriesApi curatorEntriesApi = new EntriesApi(curatorApiClient);
        final ApiClient userApiClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi userWorkflowsApi = new WorkflowsApi(userApiClient);

        // Create a workflow with a random name
        String workflowName = Long.toString(Instant.now().toEpochMilli());
        userWorkflowsApi
            .manualRegister("github", "DockstoreTestUser2/gdc-dnaseq-cwl", "/workflows/dnaseq/transform.cwl", workflowName, "cwl",
                "/workflows/dnaseq/transform.cwl.json");
        final Workflow workflowByPathGithub = userWorkflowsApi
            .getWorkflowByPath(DOCKSTORE_TEST_USER2_GDC_DNASEQ_CWL_WORKFLOW + "/" + workflowName, null, false);
        final Workflow workflow = userWorkflowsApi.refresh(workflowByPathGithub.getId());

        // Publish workflow, which will also add a topic
        userWorkflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(true));

        // Should not be able to create a topic for the same workflow
        try {
            curatorEntriesApi.setDiscourseTopic(workflow.getId());
            fail("Should still not be able to set discourse topic.");
        } catch (ApiException ignored) {
            assertTrue(true);
        }

        // Unpublish and publish, should not throw error
        Workflow unpublishedWf = userWorkflowsApi.publish(workflow.getId(), SwaggerUtility.createPublishRequest(false));
        Workflow publishedWf = userWorkflowsApi.publish(unpublishedWf.getId(), SwaggerUtility.createPublishRequest(true));
        assertEquals("Topic id should remain the same.", unpublishedWf.getTopicId(), publishedWf.getTopicId());
    }

    /**
     * Test for cwl1.1
     * Of the languages support features, this tests:
     * Workflow Registration
     * Metadata Display
     * Validation
     * Launch remote workflow
     */
    @Test
    public void cwlVersion11() {
        final ApiClient userApiClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi userWorkflowsApi = new WorkflowsApi(userApiClient);
        userWorkflowsApi.manualRegister("github", "dockstore-testing/Workflows-For-CI", "/cwl/v1.1/metadata.cwl", "metadata", "cwl",
            "/cwl/v1.1/cat-job.json");
        final Workflow workflowByPathGithub = userWorkflowsApi
            .getWorkflowByPath("github.com/dockstore-testing/Workflows-For-CI/metadata", null, false);
        final Workflow workflow = userWorkflowsApi.refresh(workflowByPathGithub.getId());
        Assert.assertEquals("Print the contents of a file to stdout using 'cat' running in a docker container.", workflow.getDescription());
        Assert.assertEquals("Peter Amstutz", workflow.getAuthor());
        Assert.assertEquals("peter.amstutz@curoverse.com", workflow.getEmail());
        Assert.assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        Optional<WorkflowVersion> optionalWorkflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersion.isPresent());
        WorkflowVersion workflowVersion = optionalWorkflowVersion.get();
        List<SourceFile> sourceFiles = workflowVersion.getSourceFiles();
        Assert.assertEquals(2, sourceFiles.size());
        Assert.assertTrue(sourceFiles.stream().anyMatch(sourceFile -> sourceFile.getPath().equals("/cwl/v1.1/cat-job.json")));
        Assert.assertTrue(sourceFiles.stream().anyMatch(sourceFile -> sourceFile.getPath().equals("/cwl/v1.1/metadata.cwl")));
        // Check validation works.  It is invalid because this is a tool and not a workflow.
        Assert.assertFalse(workflowVersion.isValid());

        userWorkflowsApi
            .manualRegister("github", "dockstore-testing/Workflows-For-CI", "/cwl/v1.1/count-lines1-wf.cwl", "count-lines1-wf", "cwl",
                "/cwl/v1.1/wc-job.json");
        final Workflow workflowByPathGithub2 = userWorkflowsApi
            .getWorkflowByPath("github.com/dockstore-testing/Workflows-For-CI/count-lines1-wf", null, false);
        final Workflow workflow2 = userWorkflowsApi.refresh(workflowByPathGithub2.getId());
        Assert.assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        Optional<WorkflowVersion> optionalWorkflowVersion2 = workflow2.getWorkflowVersions().stream()
            .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersion2.isPresent());
        WorkflowVersion workflowVersion2 = optionalWorkflowVersion2.get();
        // Check validation works.  It should be valid
        Assert.assertTrue(workflowVersion2.isValid());
        userWorkflowsApi.publish(workflowByPathGithub2.getId(), SwaggerUtility.createPublishRequest(true));
        List<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--entry");
        args.add("github.com/dockstore-testing/Workflows-For-CI/count-lines1-wf");
        args.add("--yaml");
        args.add(jsonFilePath);
        args.add("--config");
        args.add(clientConfig);
        args.add("--script");

        Client.main(args.toArray(new String[0]));
        Assert.assertTrue(systemOutRule.getLog().contains("Final process status is success"));
    }

    private Workflow registerGatkSvWorkflow(WorkflowsApi ownerWorkflowApi) {
        // Register and refresh workflow
        Workflow workflow = ownerWorkflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), "dockstore-testing/gatk-sv-clinical", "/GATKSVPipelineClinical.wdl",
                "test", "wdl", "/test.json");
        return ownerWorkflowApi.refresh(workflow.getId());
    }

    /**
     * We need an EntryVersionHelper instance so we can call EntryVersionHelper.writeStreamAsZip; getDAO never gets invoked.
     */
    private static class EntryVersionHelperImpl implements EntryVersionHelper {

        @Override
        public EntryDAO getDAO() {
            return null;
        }
    }
}


