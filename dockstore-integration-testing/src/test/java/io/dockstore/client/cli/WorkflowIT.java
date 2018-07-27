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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.ws.rs.core.GenericType;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.LanguageType;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
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
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.ToolFile;
import io.swagger.client.model.ToolTests;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Extra confidential integration tests, focus on testing workflow interactions
 * {@link io.dockstore.client.cli.BaseIT}
 * @author dyuen
 */
@Category({ConfidentialTest.class, WorkflowTest.class})
public class WorkflowIT extends BaseIT {

    private static final String DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow";
    private static final String DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW = SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow";
    private static final String DOCKSTORE_TEST_USER2_IMPORTS_DOCKSTORE_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore-whalesay-imports";
    private static final String DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore_workflow_cnv";
    // workflow with external library in lib directory
    private static final String DOCKSTORE_TEST_USER2_NEXTFLOW_LIB_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/rnatoy";
    // workflow that uses containers
    private static final String DOCKSTORE_TEST_USER2_NEXTFLOW_DOCKER_WORKFLOW = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/galaxy-workflows";
    private static final String DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL = Registry.QUAY_IO.toString() + "/dockstoretestuser2/dockstore-cgpmap";

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown= ExpectedException.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    @Test
    public void testStubRefresh() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();

        final List<Workflow> workflows = usersApi.refreshWorkflows(user.getId());
        for (Workflow workflow: workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        assertTrue("workflow size was " + workflows.size(), workflows.size() > 1);
        assertTrue(
                "found non stub workflows " + workflows.stream().filter(workflow -> workflow.getMode() != Workflow.ModeEnum.STUB).count(),
                workflows.stream().allMatch(workflow -> workflow.getMode() == Workflow.ModeEnum.STUB));
    }

    @Test
    public void testTargettedRefresh() throws ApiException, URISyntaxException, IOException {
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();
        Assert.assertNotEquals("getUser() endpoint should actually return the user profile", null, user.getUserProfiles());

        final List<Workflow> workflows = usersApi.refreshWorkflows(user.getId());

        for (Workflow workflow: workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());
        final Workflow workflowByPathBitbucket = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW);
        final Workflow refreshBitbucket = workflowApi.refresh(workflowByPathBitbucket.getId());

        // tests for reference type for bitbucket workflows
        assertTrue("should see at least 4 branches",refreshBitbucket.getWorkflowVersions().stream().filter(version -> version.getReferenceType() == WorkflowVersion.ReferenceTypeEnum.BRANCH).count() >= 4);
        assertTrue("should see at least 1 tags",refreshBitbucket.getWorkflowVersions().stream().filter(version -> version.getReferenceType() == WorkflowVersion.ReferenceTypeEnum.TAG).count() >= 1);

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
        assertEquals("should find 4 valid versions for bitbucket workflow, found : " + refreshBitbucket.getWorkflowVersions().stream()
                .filter(WorkflowVersion::isValid).count(), 4,
            refreshBitbucket.getWorkflowVersions().stream().filter(WorkflowVersion::isValid).count());

        // should not be able to get content normally
        Ga4GhApi anonymousGa4Ghv2Api = new Ga4GhApi(getWebClient(false, null));
        Ga4GhApi adminGa4Ghv2Api = new Ga4GhApi(webClient);
        boolean exceptionThrown = false;
        try {
            anonymousGa4Ghv2Api
                .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW, "master");
        } catch (ApiException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        ToolDescriptor adminToolDesciptor = adminGa4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW, "master");
        assertTrue("could not get content via optional auth", adminToolDesciptor != null && !adminToolDesciptor.getDescriptor().isEmpty());

        workflowApi.publish(workflowByPathBitbucket.getId(), new PublishRequest(){
            public Boolean isPublish() { return true;}
        });
        // check on URLs for workflows via ga4gh calls
        ToolDescriptor toolDescriptor = adminGa4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW, "master");
        String content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        Assert.assertTrue("could not find content from generated URL", !content.isEmpty());
        checkForRelativeFile(adminGa4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_DOCKSTORE_WORKFLOW, "master", "grep.cwl");


        // check on commit ids for github
        boolean allHaveCommitIds = refreshGithub.getWorkflowVersions().stream().noneMatch(version -> version.getCommitID().isEmpty());
        assertTrue("not all workflows seem to have commit ids", allHaveCommitIds);
    }

    @Test
    public void testHostedDelete() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();
        usersApi.refreshWorkflows(user.getId());
        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());

        // using hosted apis to edit normal workflows should fail
        HostedApi hostedApi = new HostedApi(webClient);
        thrown.expect(ApiException.class);
        hostedApi.deleteHostedWorkflowVersion(refreshGithub.getId(), "v1.0");
    }

    @Test
    public void testHostedEdit() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();
        usersApi.refreshWorkflows(user.getId());
        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());

        // using hosted apis to edit normal workflows should fail
        HostedApi hostedApi = new HostedApi(webClient);
        thrown.expect(ApiException.class);
        hostedApi.editHostedWorkflow(refreshGithub.getId(), new ArrayList<>());
    }

    @Test
    public void testWorkflowLaunchOrNotLaunchBasedOnCredentials() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "checker-workflow-wrapping-workflow.cwl",
                "test", "cwl", null);
        assertEquals("There should be one user of the workflow after manually registering it.", 1, workflow.getUsers().size());
        Workflow refresh = workflowApi.refresh(workflow.getId());


        Assert.assertTrue("workflow is already published for some reason", !refresh.isIsPublished());

        // should be able to launch properly with correct credentials even though the workflow is not published
        FileUtils.writeStringToFile(new File("md5sum.input"), "foo" , StandardCharsets.UTF_8);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry", toolpath, "--json" , ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json") ,  "--script" });

        // should not be able to launch properly with incorrect credentials
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "workflow", "launch", "--entry", toolpath, "--json" , ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json") ,  "--script" });
    }

    /**
     * This tests workflow convert entry2json when the main descriptor is nested far within the GitHub repo with secondary descriptors too
     * @throws IOException
     */
    @Test
    public void testEntryConvertWDLWithSecondaryDescriptors() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/dockstore-testing/skylab";
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi
                .manualRegister(SourceControl.GITHUB.getFriendlyName(), "dockstore-testing/skylab", "/pipelines/smartseq2_single_sample/SmartSeq2SingleSample.wdl",
                        "", "wdl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId());
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish(refresh.getId(), publishRequest);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "convert", "entry2json", "--entry", toolpath + ":Dockstore_Testing", "--script" });
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
        stringList.forEach(string -> {
            Assert.assertTrue(systemOutRule.getLog().contains(string));
        });
    }

    /**
     * This tests that you are able to download zip files for versions of a workflow
     */
    @Test
    public void downloadZipFile() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
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
        assertTrue("zip file seems incorrect", zipFile.stream().map(ZipEntry::getName).collect(Collectors.toList()).contains("/md5sum/md5sum-workflow.cwl"));

        // should not be able to get zip anonymously before publication
        boolean thrownException = false;
        try {
            SwaggerUtility.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
            }, getWebClient(false, null));
        } catch (Exception e) {
            thrownException = true;
        }
        assertTrue(thrownException);

        // Download published workflow version
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", toolpath, "--script" });
        arbitraryURL = SwaggerUtility.getArbitraryURL("/workflows/" + workflowId + "/zip/" + versionId, new GenericType<byte[]>() {
        }, getWebClient(false, null));
        write = Files.write(File.createTempFile("temp", "zip").toPath(), arbitraryURL);
        zipFile = new ZipFile(write.toFile());
        assertTrue("zip file seems incorrect", zipFile.stream().map(ZipEntry::getName).collect(Collectors.toList()).contains("/md5sum/md5sum-workflow.cwl"));

        // download and unzip via CLI
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "download", "--entry", toolpath + ":" + workflowVersion.getName(), "--script" });
        zipFile.stream().forEach(entry -> {
            File innerFile = new File(System.getProperty("user.dir"),entry.getName());
            assert(innerFile.exists());
            assert(innerFile.delete());
        });

        // download zip via CLI
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "download", "--entry", toolpath + ":" + workflowVersion.getName(), "--zip", "--script" });
        File downloadedZip = new File(workflow.getWorkflowName() + ".zip");
        assert(downloadedZip.exists());
        assert(downloadedZip.delete());
    }

    /**
     * This tests that zip file can be downloaded or not based on published state and auth.
     */
    @Test
    public void downloadZipFileTestAuth() {
        final ApiClient ownerWebClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi ownerWorkflowApi = new WorkflowsApi(ownerWebClient);

        final ApiClient anonWebClient = getWebClient(false, null);
        WorkflowsApi anonWorkflowApi = new WorkflowsApi(anonWebClient);

        final ApiClient otherUserWebClient = getWebClient(true, "OtherUser");
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
            assertTrue("User does not have access to workflow.", !success);
        }
        // Other user: Should fail
        success = true;
        try {
            otherUserWorkflowApi.getWorkflowZip(workflowId, versionId);
        } catch (ApiException ex) {
            success = false;
        } finally {
            assertTrue("User does not have access to workflow.", !success);
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

    @Test
    public void testCheckerWorkflowDownloadBasedOnCredentials() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");

        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        Workflow workflow = workflowApi
                .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                        "test", "cwl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId());
        Assert.assertTrue("workflow is already published for some reason", !refresh.isIsPublished());
        workflowApi.registerCheckerWorkflow("checker-workflow-wrapping-workflow.cwl", workflow.getId(), "cwl", "checker-input-cwl.json");
        workflowApi.refresh(workflow.getId());

        final String FILE_WITH_INCORRECT_CREDENTIALS = ResourceHelpers.resourceFilePath("config_file.txt");
        final String FILE_WITH_CORRECT_CREDENTIALS = ResourceHelpers.resourceFilePath("config_file2.txt");

        // should be able to download properly with correct credentials even though the workflow is not published
        FileUtils.writeStringToFile(new File("md5sum.input"), "foo" , StandardCharsets.UTF_8);
        Client.main(new String[] { "--config", FILE_WITH_CORRECT_CREDENTIALS, "checker", "download", "--entry", toolpath, "--version", "master",  "--script" });

        // Publish the workflow
        Client.main(new String[] { "--config", FILE_WITH_CORRECT_CREDENTIALS, "workflow", "publish", "--entry", toolpath, "--script" });

        // should be able to download properly with incorrect credentials because the entry is published
        Client.main(new String[] { "--config", FILE_WITH_INCORRECT_CREDENTIALS, "checker", "download", "--entry", toolpath, "--version", "master",  "--script" });

        // Unpublish the workflow
        Client.main(new String[] { "--config", FILE_WITH_CORRECT_CREDENTIALS, "workflow", "publish", "--entry", toolpath, "--unpub", "--script" });

        // should not be able to download properly with incorrect credentials because the entry is not published
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", FILE_WITH_INCORRECT_CREDENTIALS, "checker", "download", "--entry", toolpath, "--version", "master",  "--script" });
    }

    @Test
    public void testCheckerWorkflowLaunchBasedOnCredentials() throws IOException {
        String toolpath = SourceControl.GITHUB.toString() + "/DockstoreTestUser2/md5sum-checker/test";
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi
                .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "/md5sum/md5sum-workflow.cwl",
                        "test", "cwl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId());
        Assert.assertTrue("workflow is already published for some reason", !refresh.isIsPublished());

        workflowApi.registerCheckerWorkflow("checker-workflow-wrapping-workflow.cwl", workflow.getId(), "cwl", "checker-input-cwl.json");

        refresh = workflowApi.refresh(workflow.getId());

        // should be able to launch properly with correct credentials even though the workflow is not published
        FileUtils.writeStringToFile(new File("md5sum.input"), "foo" , StandardCharsets.UTF_8);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "checker", "launch", "--entry", toolpath, "--json" , ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json"),  "--script" });

        // should be able to launch properly with incorrect credentials but the entry is published
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", toolpath, "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "checker", "launch", "--entry", toolpath, "--json" , ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json"),  "--script" });

        // should not be able to launch properly with incorrect credentials
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", toolpath, "--unpub", "--script" });
        systemExit.expectSystemExitWithStatus(Client.ENTRY_NOT_FOUND);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "checker", "launch", "--entry", toolpath, "--json" , ResourceHelpers.resourceFilePath("md5sum-wrapper-tool.json"),  "--script" });
    }

    @Test
    public void testNextFlowRefresh() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();

        final List<Workflow> workflows = usersApi.refreshWorkflows(user.getId());

        for (Workflow workflow: workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_NEXTFLOW_LIB_WORKFLOW);
        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(LanguageType.NEXTFLOW.toString());
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_NEXTFLOW_LIB_WORKFLOW);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());

        assertSame("github workflow is not in full mode", refreshGithub.getMode(), Workflow.ModeEnum.FULL);

        // look that branches and tags are typed correctly for workflows on GitHub
        assertTrue("should see at least 6 branches",refreshGithub.getWorkflowVersions().stream().filter(version -> version.getReferenceType() == WorkflowVersion.ReferenceTypeEnum.BRANCH).count() >= 6);
        assertTrue("should see at least 6 tags",refreshGithub.getWorkflowVersions().stream().filter(version -> version.getReferenceType() == WorkflowVersion.ReferenceTypeEnum.TAG).count() >= 6);

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
    }

    @Test
    public void testNextFlowWorkflowWithImages() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        User user = usersApi.getUser();

        final List<Workflow> workflows = usersApi.refreshWorkflows(user.getId());

        for (Workflow workflow: workflows) {
            assertNotSame("", workflow.getWorkflowName());
        }

        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_NEXTFLOW_DOCKER_WORKFLOW);
        // need to set paths properly
        workflowByPathGithub.setWorkflowPath("/nextflow.config");
        workflowByPathGithub.setDescriptorType(LanguageType.NEXTFLOW.toString());
        workflowApi.updateWorkflow(workflowByPathGithub.getId(), workflowByPathGithub);

        workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_NEXTFLOW_DOCKER_WORKFLOW);
        final Workflow refreshGithub = workflowApi.refresh(workflowByPathGithub.getId());

        assertSame("github workflow is not in full mode", refreshGithub.getMode(), Workflow.ModeEnum.FULL);
        Optional<WorkflowVersion> first = refreshGithub.getWorkflowVersions().stream().filter(version -> version.getName().equals("1.0"))
            .findFirst();
        String tableToolContent = workflowApi.getTableToolContent(refreshGithub.getId(), first.get().getId());
        String workflowDag = workflowApi.getWorkflowDag(refreshGithub.getId(), first.get().getId());
        assertTrue("tool table should be present", !tableToolContent.isEmpty());
        assertTrue("workflow dag should be present", !workflowDag.isEmpty());
        Gson gson = new Gson();
        List<Map<String, String>> list = gson.fromJson(tableToolContent, List.class);
        Map<Map,List> map = gson.fromJson(workflowDag, Map.class);
        assertTrue("tool table should be present", list.size() >= 9);
        long dockerCount = list.stream().filter(tool -> !tool.get("docker").isEmpty()).count();
        assertEquals("tool table is populated with docker images", dockerCount, list.size());
        assertTrue("workflow dag should be present", map.entrySet().size() >= 2);
        assertTrue("workflow dag is not as large as expected", map.get("nodes").size() >= 11 && map.get("edges").size() >= 10);
    }

    /**
     * This test checks that a user can successfully refresh their workflows (only stubs)
     *
     * @throws ApiException
     */
    @Test
    public void testRefreshAllForAUser() throws ApiException {
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        testingPostgres.runUpdateStatement("update enduser set isadmin = 't' where username = 'DockstoreTestUser2';");
        long userId = 1;

        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        UsersApi usersApi = new UsersApi(webClient);
        final List<Workflow> workflow = usersApi.refreshWorkflows(userId);

        // Check that there are multiple workflows
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow", new ScalarHandler<>());
        assertTrue("Workflow entries should exist", count > 0);

        // Check that there are only stubs (no workflow version)
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion", new ScalarHandler<>());
        assertEquals("No entries in workflowversion", 0, count2);
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertEquals("No workflows are in full mode", 0, count3);

        // check that a nextflow workflow made it
        long nfWorkflowCount = workflow.stream().filter(w -> w.getGitUrl().contains("mta-nf")).count();
        assertTrue("Nextflow workflow not found", nfWorkflowCount > 0);
        Workflow mtaNf = workflow.stream().filter(w -> w.getGitUrl().contains("mta-nf")).findFirst().get();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        mtaNf.setWorkflowPath("/nextflow.config");
        mtaNf.setDescriptorType(SourceFile.TypeEnum.NEXTFLOW.toString());
        workflowApi.updateWorkflow(mtaNf.getId(), mtaNf);
        workflowApi.refresh(mtaNf.getId());
        // publish this way? (why is the auto-generated variable private?)
        workflowApi.publish(mtaNf.getId(), new PublishRequest(){
            @Override
            public Boolean isPublish() {
                return true;
            }
        });
        mtaNf = workflowApi.getWorkflow(mtaNf.getId());
        Assert.assertTrue("a workflow lacks a date", mtaNf.getLastModifiedDate() != null && mtaNf.getLastModified() != 0);
        assertNotNull("Nextflow workflow not found after update", mtaNf);
        assertTrue("nextflow workflow should have at least two versions", mtaNf.getWorkflowVersions().size() >= 2);
        int numOfSourceFiles = mtaNf.getWorkflowVersions().stream().mapToInt(version -> version.getSourceFiles().size()).sum();
        assertTrue("nextflow workflow should have at least two sourcefiles", numOfSourceFiles >= 2);
        long scriptCount = mtaNf.getWorkflowVersions().stream()
            .mapToLong(version -> version.getSourceFiles().stream().filter(file -> file.getType() == SourceFile.TypeEnum.NEXTFLOW).count()).sum();
        long configCount = mtaNf.getWorkflowVersions().stream()
            .mapToLong(version -> version.getSourceFiles().stream().filter(file -> file.getType() == SourceFile.TypeEnum.NEXTFLOW_CONFIG).count()).sum();
        assertTrue("nextflow workflow should have at least one config file and one script file", scriptCount >= 1 && configCount >= 1);

        // check that we can pull down the nextflow workflow via the ga4gh TRS API
        Ga4GhApi ga4Ghv2Api = new Ga4GhApi(webClient);
        List<Tool> toolV2s = ga4Ghv2Api.toolsGet(null, null,null, null, null, null, null, null, null, null, null);
        String mtaWorkflowID = "#workflow/github.com/DockstoreTestUser2/mta-nf";
        Tool toolV2 = ga4Ghv2Api.toolsIdGet(mtaWorkflowID);
        assertTrue("could get mta as part of list", toolV2s.size() > 0 && toolV2s.stream().anyMatch(tool -> Objects
            .equals(tool.getId(), mtaWorkflowID)));
        assertNotNull("could get mta as a specific tool", toolV2);
    }

    /**
     * This test does not use admin rights, note that a number of operations go through the UserApi to get this to work
     *
     * @throws ApiException
     */
    @Test
    public void testPublishingAndListingOfPublished() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        // should start with nothing published
        assertTrue("should start with nothing published ", workflowApi.allPublishedWorkflows(null, null, null, null, null).isEmpty());
        // refresh just for the current user
        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();
        usersApi.refreshWorkflows(userId);
        assertTrue("should remain with nothing published ", workflowApi.allPublishedWorkflows(null, null, null, null, null).isEmpty());
        // assertTrue("should have a bunch of stub workflows: " +  usersApi..allWorkflows().size(), workflowApi.allWorkflows().size() == 4);

        final Workflow workflowByPath = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW);
        // refresh targeted
        workflowApi.refresh(workflowByPath.getId());

        // publish one
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish(workflowByPath.getId(), publishRequest);
        assertEquals("should have one published, found  " + workflowApi.allPublishedWorkflows(null, null, null, null, null).size(), 1,
            workflowApi.allPublishedWorkflows(null, null, null, null, null).size());
        final Workflow publishedWorkflow = workflowApi.getPublishedWorkflow(workflowByPath.getId());
        assertNotNull("did not get published workflow", publishedWorkflow);
        final Workflow publishedWorkflowByPath = workflowApi.getPublishedWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW);
        assertNotNull("did not get published workflow", publishedWorkflowByPath);

        // publish everything so pagination testing makes more sense (going to unfortunately use rate limit)
        Lists.newArrayList("github.com/DockstoreTestUser2/hello-dockstore-workflow", "github.com/DockstoreTestUser2/dockstore-whalesay-imports", "bitbucket.org/dockstore_testuser2/dockstore-workflow", "github.com/DockstoreTestUser2/parameter_test_workflow").forEach(path -> {
            Workflow workflow = workflowApi.getWorkflowByPath(path);
            workflowApi.refresh(workflow.getId());
            workflowApi.publish(workflow.getId(), publishRequest);
        });
        List<Workflow> workflows = workflowApi.allPublishedWorkflows(null, null, null, null, null);
        // test offset
        assertTrue("offset does not seem to be working",
            Objects.equals(workflowApi.allPublishedWorkflows("1", null, null, null, null).get(0).getId(), workflows.get(1).getId()));
        // test limit
        assertEquals(1, workflowApi.allPublishedWorkflows(null, 1, null, null, null).size());
        // test custom sort column
        List<Workflow> ascId = workflowApi.allPublishedWorkflows(null, null, null, "id", "asc");
        List<Workflow> descId = workflowApi.allPublishedWorkflows(null, null, null, "id", "desc");
        assertTrue("sort by id does not seem to be working", Objects.equals(ascId.get(0).getId(), descId.get(descId.size() - 1).getId()));
        // test filter
        List<Workflow> filtered = workflowApi.allPublishedWorkflows(null, null, "whale" , "stars", null);
        assertEquals(1, filtered.size());
    }

    /**
     * Tests manual registration and publishing of a github and bitbucket workflow
     *
     * @throws ApiException
     */
    @Test
    public void testManualRegisterThenPublish() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Make publish request (true)
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);

        // Set up postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

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
                .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertEquals("No workflows are in full mode", 0, count);
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from workflow where workflowname = 'altname'", new ScalarHandler<>());
        assertEquals("There should be two workflows with name altname, there are " + count2, 2, count2);

        // Publish github workflow
        workflowApi.refresh(githubWorkflow.getId());
        workflowApi.publish(githubWorkflow.getId(), publishRequest);

        // Publish bitbucket workflow
        workflowApi.refresh(bitbucketWorkflow.getId());
        workflowApi.publish(bitbucketWorkflow.getId(), publishRequest);

        // Assert some things
        assertEquals("should have two published, found  " + workflowApi.allPublishedWorkflows(null, null, null, null, null).size(), 2,
            workflowApi.allPublishedWorkflows(null, null, null, null, null).size());
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertEquals("Two workflows are in full mode", 2, count3);
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from workflowversion where valid = 't'", new ScalarHandler<>());
        assertEquals("There should be 5 valid version tags, there are " + count4, 6, count4);
    }

    /**
     * This tests that a nested WDL workflow (three levels) is properly parsed
     * @throws ApiException
     */
    @Test
    public void testNestedWdlWorkflow() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        UsersApi usersApi = new UsersApi(webClient);
        final Long userId = usersApi.getUser().getId();

        // Set up postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Manually register workflow github
        Workflow githubWorkflow = workflowApi
                .manualRegister("github", "DockstoreTestUser2/nested-wdl", "/Dockstore.wdl", "altname", "wdl", "/test.json");

        // Assert some things
        final long count = testingPostgres
                .runSelectStatement("select count(*) from workflow where mode = '" + Workflow.ModeEnum.FULL + "'", new ScalarHandler<>());
        assertEquals("No workflows are in full mode", 0,count);

        // Refresh the workflow
        workflowApi.refresh(githubWorkflow.getId());

        // Confirm that correct number of sourcefiles are found
        githubWorkflow = workflowApi.getWorkflow(githubWorkflow.getId());
        List<WorkflowVersion> versions = githubWorkflow.getWorkflowVersions();
        assertEquals("There should be two versions", 2, versions.size());

        Optional<WorkflowVersion> loopVersion = versions.stream().filter(version -> Objects.equals(version.getReference(), "infinite-loop")).findFirst();
        if (loopVersion.isPresent()) {
            assertEquals("There should be two sourcefiles", 2, loopVersion.get().getSourceFiles().size());
        } else {
            fail("Could not find version infinite-loop");
        }

        Optional<WorkflowVersion> masterVersion = versions.stream().filter(version -> Objects.equals(version.getReference(), "master")).findFirst();
        if (masterVersion.isPresent()) {
            assertEquals("There should be three sourcefiles", 3, masterVersion.get().getSourceFiles().size());
        } else {
            fail("Could not find version master");
        }
    }


    /**
     * Tests manual registration of a tool and check that descriptors are downloaded properly.
     * Description is pulled properly from an $include.
     *
     * @throws ApiException
     */
    @Test
    public void testManualRegisterToolWithMixinsAndSymbolicLinks() throws ApiException, URISyntaxException, IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        ContainersApi toolApi = new ContainersApi(webClient);

        DockstoreTool tool = new DockstoreTool();
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
        assertTrue("should see at least 6 branches",registeredTool.getTags().stream().filter(version -> version.getReferenceType() == Tag.ReferenceTypeEnum.BRANCH).count() >= 1);
        assertTrue("should see at least 6 tags",registeredTool.getTags().stream().filter(version -> version.getReferenceType() == Tag.ReferenceTypeEnum.TAG).count() >= 2);

        assertTrue("did not pick up description from $include", registeredTool.getDescription().contains("A Docker container for PCAP-core."));
        assertEquals("did not import mixin and includes properly", 5,
            registeredTool.getTags().stream().filter(tag -> Objects.equals(tag.getName(), "test.v1")).findFirst().get().getSourceFiles()
                .size());
        assertEquals("did not import symbolic links to folders properly", 5,
            registeredTool.getTags().stream().filter(tag -> Objects.equals(tag.getName(), "symbolic.v1")).findFirst().get().getSourceFiles()
                .size());
        // check that commit ids look properly recorded
        // check on commit ids for github
        boolean allHaveCommitIds = registeredTool.getTags().stream().noneMatch(version -> version.getCommitID().isEmpty());
        assertTrue("not all tools seem to have commit ids", allHaveCommitIds);

        // check on URLs for workflows via ga4gh calls
        Ga4GhApi ga4Ghv2Api = new Ga4GhApi(webClient);
        ToolDescriptor toolDescriptor = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_TOOL, "symbolic.v1");
        String content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        Assert.assertTrue("could not find content from generated URL", !content.isEmpty());
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
     * @param ga4Ghv2Api
     * @param dockstoreTestUser2RelativeImportsTool
     * @param reference
     * @param filename
     * @throws IOException
     * @throws URISyntaxException
     */
    private void checkForRelativeFile(Ga4GhApi ga4Ghv2Api, String dockstoreTestUser2RelativeImportsTool, String reference, String filename)
        throws IOException, URISyntaxException {
        ToolDescriptor toolDescriptor;
        String content;
        toolDescriptor = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorRelativePathGet("CWL", dockstoreTestUser2RelativeImportsTool, reference, filename);
        content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        Assert.assertTrue("could not find " + filename + " from generated URL", !content.isEmpty());
    }

    /**
     * Tests that trying to register a duplicate workflow fails, and that registering a non-existant repository failes
     *
     * @throws ApiException
     */
    @Test
    public void testManualRegisterErrors() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
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
            assertTrue("The workflow cannot be registered as it is a duplicate.", !success);
        }

        success = true;
        try {
            workflowApi.manualRegister("github", "dasn/iodnasiodnasio", "/Dockstore.wdl", "", "wdl", "/test.json");
        } catch (ApiException c) {
            success = false;
        } finally {
            assertTrue("The workflow cannot be registered as the repository doesn't exist.", !success);
        }
    }

    @Test
    public void testSecondaryFileOperations() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore-whalesay-imports", "/Dockstore.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_IMPORTS_DOCKSTORE_WORKFLOW);

        // This checks if a workflow whose default name was manually registered as an empty string would become null
        assertNull(workflowByPathGithub.getWorkflowName());

        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());

        // This checks if a workflow whose default name is null would remain as null after refresh
        assertNull(workflow.getWorkflowName());

        // test out methods to access secondary files
        final List<SourceFile> masterImports = workflowApi.secondaryCwl(workflow.getId(), "master");
        assertEquals("should find 2 imports, found " + masterImports.size(), 2, masterImports.size());
        final SourceFile master = workflowApi.cwl(workflow.getId(), "master");
        assertTrue("master content incorrect", master.getContent().contains("untar") && master.getContent().contains("compile"));

        // get secondary files by path
        SourceFile argumentsTool = workflowApi.secondaryCwlPath(workflow.getId(), "arguments.cwl", "master");
        assertTrue("argumentstool content incorrect", argumentsTool.getContent().contains("Example trivial wrapper for Java 7 compiler"));
    }

    @Test
    public void testRelativeSecondaryFileOperations() throws ApiException, URISyntaxException, IOException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW);

        // This checks if a workflow whose default name was manually registered as an empty string would become null
        assertNull(workflowByPathGithub.getWorkflowName());

        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());

        // Test that the secondary file's input file formats are recognized (secondary file is varscan_cnv.cwl)
        List<FileFormat> fileFormats = workflow.getInputFileFormats();
        List<WorkflowVersion> workflowVersionsForFileFormat = workflow.getWorkflowVersions();
        Assert.assertTrue(workflowVersionsForFileFormat.stream().anyMatch(workflowVersion -> workflowVersion.getInputFileFormats().stream().anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_2572"))));
        Assert.assertTrue(workflowVersionsForFileFormat.stream().anyMatch(workflowVersion -> workflowVersion.getInputFileFormats().stream().anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_1929"))));
        Assert.assertTrue(workflowVersionsForFileFormat.stream().anyMatch(workflowVersion -> workflowVersion.getInputFileFormats().stream().anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_3003"))));
        Assert.assertTrue(fileFormats.stream().anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_2572")));
        Assert.assertTrue(fileFormats.stream().anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_1929")));
        Assert.assertTrue(fileFormats.stream().anyMatch(fileFormat -> fileFormat.getValue().equals("http://edamontology.org/format_3003")));
        Assert.assertTrue(workflowVersionsForFileFormat.stream().anyMatch(workflowVersion -> workflowVersion.getOutputFileFormats().stream().anyMatch(fileFormat -> fileFormat.getValue().equals("file://fakeFileFormat"))));
        Assert.assertTrue(workflow.getOutputFileFormats().stream().anyMatch(fileFormat -> fileFormat.getValue().equals("file://fakeFileFormat")));


        // This checks if a workflow whose default name is null would remain as null after refresh
        assertNull(workflow.getWorkflowName());

        // test out methods to access secondary files

        final List<SourceFile> masterImports = workflowApi.secondaryCwl(workflow.getId(), "master");
        assertEquals("should find 3 imports, found " + masterImports.size(), 3, masterImports.size());
        final List<SourceFile> rootImports = workflowApi.secondaryCwl(workflow.getId(), "rootTest");
        assertEquals("should find 0 imports, found " + rootImports.size(), 0, rootImports.size());

        // next, change a path for the root imports version
        List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        workflowVersions.stream().filter(v -> v.getName().equals("rootTest")).findFirst().get().setWorkflowPath("/cnv.cwl");
        workflowApi.updateWorkflowVersion(workflow.getId(), workflowVersions);
        workflowApi.refresh(workflowByPathGithub.getId());
        final List<SourceFile> newMasterImports = workflowApi.secondaryCwl(workflow.getId(), "master");
        assertEquals("should find 3 imports, found " + newMasterImports.size(), 3, newMasterImports.size());
        final List<SourceFile> newRootImports = workflowApi.secondaryCwl(workflow.getId(), "rootTest");
        assertEquals("should find 3 imports, found " + newRootImports.size(), 3, newRootImports.size());

        workflowApi.publish(workflow.getId(), new PublishRequest(){
            public Boolean isPublish() { return true;}
        });
        // check on URLs for workflows via ga4gh calls
        Ga4GhApi ga4Ghv2Api = new Ga4GhApi(webClient);
        ToolDescriptor toolDescriptor = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeDescriptorGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        String content = IOUtils.toString(new URI(toolDescriptor.getUrl()), StandardCharsets.UTF_8);
        Assert.assertTrue("could not find content from generated URL", !content.isEmpty());
        checkForRelativeFile(ga4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master", "adtex.cwl");
        // ignore extra separators
        checkForRelativeFile(ga4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master", "/adtex.cwl");
        // test json should use relative path with ".."
        checkForRelativeFile(ga4Ghv2Api, "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master", "../test.json");
        List<ToolFile> toolFiles = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeFilesGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        assertTrue("should have at least 5 files", toolFiles.size() >= 5);
        assertTrue("all files should have relative paths", toolFiles.stream().filter(toolFile -> !toolFile.getPath().startsWith("/")).count() >= 5);

        // check on urls created for test files
        List<ToolTests> toolTests = ga4Ghv2Api
            .toolsIdVersionsVersionIdTypeTestsGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        assertTrue("could not find tool tests", toolTests.size() > 0);
        for(ToolTests test: toolTests) {
            content = IOUtils.toString(new URI(test.getUrl()), StandardCharsets.UTF_8);
            Assert.assertTrue("could not find content from generated test JSON URL", !content.isEmpty());
        }
    }

    @Test
    public void testAnonAndAdminGA4GH() throws ApiException, URISyntaxException, IOException {
        WorkflowsApi workflowApi = new WorkflowsApi(getWebClient(USER_2_USERNAME));
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW);
        workflowApi.refresh(workflowByPathGithub.getId());

        // should not be able to get content normally
        Ga4GhApi anonymousGa4Ghv2Api = new Ga4GhApi(getWebClient(false, null));
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
        Ga4GhApi adminGa4Ghv2Api = new Ga4GhApi(getWebClient(USER_2_USERNAME));

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

        // can get relative paths with admin user
        toolFiles.forEach(file -> {
            if (file.getFileType() == ToolFile.FileTypeEnum.TEST_FILE) {
                // enable later with a simplification to TRS
//                ToolTests test = (ToolTests)adminGa4Ghv2Api.toolsIdVersionsVersionIdTypeDescriptorRelativePathGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW,
//                    "master", file.getPath());
//                assertTrue("test exists", !test.getTest().isEmpty());
            } else if (file.getFileType() == ToolFile.FileTypeEnum.PRIMARY_DESCRIPTOR || file.getFileType() == ToolFile.FileTypeEnum.SECONDARY_DESCRIPTOR) {
                // annoyingly, some files are tool tests, some are tooldescriptor
                ToolDescriptor toolDescriptor = adminGa4Ghv2Api.toolsIdVersionsVersionIdTypeDescriptorRelativePathGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW,
                    "master", file.getPath());
                assertTrue("descriptor exists", !toolDescriptor.getDescriptor().isEmpty());
            } else {
                fail();
            }
        });

        // check on urls created for test files
        List<ToolTests> toolTests = adminGa4Ghv2Api
            .toolsIdVersionsVersionIdTypeTestsGet("CWL", "#workflow/" + DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, "master");
        assertTrue("could not find tool tests", toolTests.size() > 0);
        for (ToolTests test : toolTests) {
            String content = IOUtils.toString(new URI(test.getUrl()), StandardCharsets.UTF_8);
            Assert.assertTrue("could not find content from generated test JSON URL", !content.isEmpty());
        }
    }


    @Test
    public void testAliasOperations() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW);
        // do targetted refresh, should promote workflow to fully-fleshed out workflow
        final Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId());
        workflowApi.publish(workflow.getId(), new PublishRequest(){
            public Boolean isPublish() { return true;}
        });

        Workflow md5workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), "DockstoreTestUser2/md5sum-checker", "checker-workflow-wrapping-workflow.cwl",
                "test", "cwl", null);
        workflowApi.refresh(md5workflow.getId());
        workflowApi.publish(md5workflow.getId(), new PublishRequest(){
            public Boolean isPublish() { return true;}
        });

        // give the workflow a few aliases
        EntriesApi genericApi = new EntriesApi(webClient);
        Entry entry = genericApi.updateAliases(workflow.getId(), "awesome workflow, spam, test workflow", "");
        Assert.assertTrue("entry is missing expected aliases", entry.getAliases().containsKey("awesome workflow") && entry.getAliases().containsKey("spam") && entry.getAliases().containsKey("test workflow"));
        // check that the aliases work in TRS search
        Ga4GhApi ga4GhApi = new Ga4GhApi(webClient);
        // this generated code is mucho silly
        List<Tool> workflows = ga4GhApi.toolsGet(null, null, null, null, null, null, null, null, null, null, 100);
        Assert.assertEquals("expected workflows not found", 2, workflows.size());
        List<Tool> awesome_workflow = ga4GhApi.toolsGet(null, "awesome workflow", null, null, null, null, null, null, null, null, 100);
        Assert.assertTrue("workflow was not found or didn't have expected aliases", awesome_workflow.size() == 1 && awesome_workflow.get(0).getAliases().size() ==3);
        // remove a few aliases
        entry = genericApi.updateAliases(workflow.getId(), "foobar, test workflow", "");
        Assert.assertTrue("entry is missing expected aliases", entry.getAliases().containsKey("foobar") && entry.getAliases().containsKey("test workflow") && entry.getAliases().size() == 2);
    }
}
