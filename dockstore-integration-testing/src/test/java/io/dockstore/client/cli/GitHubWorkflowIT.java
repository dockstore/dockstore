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

import static io.openapi.api.impl.ToolsApiServiceImpl.DESCRIPTOR_FILE_SHA256_TYPE_FOR_TRS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.ImageData;
import io.dockstore.openapi.client.model.ToolVersion;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.jdbi.FileDAO;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import io.swagger.model.DescriptorType;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

@Category({ ConfidentialTest.class, WorkflowTest.class })
public class GitHubWorkflowIT extends BaseIT {
    public static final String DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME = "DockstoreTestUser2/hello-dockstore-workflow";
    public static final String DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW =
        SourceControl.GITHUB.toString() + "/" + DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME;
    private final String installationId = "1179416";
    private final String toolAndWorkflowRepo = "DockstoreTestUser2/test-workflows-and-tools";
    private final String toolAndWorkflowRepoToolPath = "DockstoreTestUser2/test-workflows-and-tools/md5sum";
    private static final String DOCKER_IMAGE_SHA_TYPE_FOR_TRS = "sha-256";
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();


    private FileDAO fileDAO;

    @Before
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.fileDAO = new FileDAO(sessionFactory);

        // used to allow us to use workflowDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);

    }
    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
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
        io.dockstore.openapi.client.ApiClient openAPIWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);

        // should start with nothing published
        assertTrue("should start with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false, null).isEmpty());
        // refresh just for the current user
        UsersApi usersApi = new UsersApi(webClient);

        refreshByOrganizationReplacement(workflowApi, openAPIWebClient);

        assertTrue("should remain with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false, null).isEmpty());
        // ensure that sorting or filtering don't expose unpublished workflows
        assertTrue("should start with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, null, "descriptorType", "asc", false, null).isEmpty());
        assertTrue("should start with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, "hello", null, null, false, null).isEmpty());
        assertTrue("should start with nothing published ",
            workflowApi.allPublishedWorkflows(null, null, "hello", "descriptorType", "asc", false, null).isEmpty());

        // assertTrue("should have a bunch of stub workflows: " +  usersApi..allWorkflows().size(), workflowApi.allWorkflows().size() == 4);

        final Workflow workflowByPath = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, BIOWORKFLOW, null);
        // refresh targeted
        workflowApi.refresh(workflowByPath.getId(), false);

        // publish one
        final PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        workflowApi.publish(workflowByPath.getId(), publishRequest);
        assertEquals("should have one published, found  " + workflowApi.allPublishedWorkflows(null, null, null, null, null, false, null).size(),
            1, workflowApi.allPublishedWorkflows(null, null, null, null, null, false, null).size());
        final Workflow publishedWorkflow = workflowApi.getPublishedWorkflow(workflowByPath.getId(), null);
        assertNotNull("did not get published workflow", publishedWorkflow);
        final Workflow publishedWorkflowByPath = workflowApi
            .getPublishedWorkflowByPath(DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, BIOWORKFLOW, null,  null);
        assertNotNull("did not get published workflow", publishedWorkflowByPath);

        // publish everything so pagination testing makes more sense (going to unfortunately use rate limit)
        Lists.newArrayList("github.com/" + DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME,
                "github.com/DockstoreTestUser2/dockstore-whalesay-imports", "github.com/DockstoreTestUser2/parameter_test_workflow")
            .forEach(path -> {
                Workflow workflow = workflowApi.getWorkflowByPath(path, BIOWORKFLOW, null);
                workflowApi.refresh(workflow.getId(), false);
                workflowApi.publish(workflow.getId(), publishRequest);
            });
        List<Workflow> workflows = workflowApi.allPublishedWorkflows(null, null, null, null, null, false, null);
        // test offset
        assertEquals("offset does not seem to be working",
            workflowApi.allPublishedWorkflows(1, null, null, null, null, false, null).get(0).getId(), workflows.get(1).getId());
        // test limit
        assertEquals(1, workflowApi.allPublishedWorkflows(null, 1, null, null, null, false, null).size());
        // test custom sort column
        List<Workflow> ascId = workflowApi.allPublishedWorkflows(null, null, null, "id", "asc", false, null);
        List<Workflow> descId = workflowApi.allPublishedWorkflows(null, null, null, "id", "desc", false, null);
        assertEquals("sort by id does not seem to be working", ascId.get(0).getId(), descId.get(descId.size() - 1).getId());
        // test filter
        List<Workflow> filteredLowercase = workflowApi.allPublishedWorkflows(null, null, "whale", "stars", null, false, null);
        assertEquals(1, filteredLowercase.size());
        filteredLowercase.forEach(workflow -> assertNull(workflow.getAliases()));
        List<Workflow> filteredUppercase = workflowApi.allPublishedWorkflows(null, null, "WHALE", "stars", null, false, null);
        assertEquals(1, filteredUppercase.size());
        assertEquals(filteredLowercase, filteredUppercase);

        // Tests for subclass

        assertEquals("There should be no app tools published", 0,
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false,
                WorkflowSubClass.APPTOOL.getValue()).size());

        final int publishedWorkflowsCount = workflowApi.allPublishedWorkflows(null, null, null, null, null, false,
            null).size();
        assertEquals("An null subclass param defaults to services param value",
            publishedWorkflowsCount,
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false,
                WorkflowSubClass.BIOWORKFLOW.getValue()).size());

        // Create an app tool and publish it
        workflowApi.handleGitHubRelease(toolAndWorkflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/main", installationId);
        Workflow appTool = workflowApi.getWorkflowByPath("github.com/" + toolAndWorkflowRepoToolPath, APPTOOL, "versions");
        workflowApi.publish(appTool.getId(), publishRequest);
        assertEquals("There should be 1 app tool published", 1,
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false,
                WorkflowSubClass.APPTOOL.getValue()).size());
        assertEquals("Published workflow count should be unchanged", publishedWorkflowsCount,
            workflowApi.allPublishedWorkflows(null, null, null, null, null, false,
                WorkflowSubClass.BIOWORKFLOW.getValue()).size());
    }

    /**
     * Tests that the info for quay images included in CWL workflows are grabbed and that the trs endpoints convert this info correctly
     */
    @Test
    public void testGettingImagesFromQuay() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);

        //Check image info is grabbed
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/hello_world", "", DescriptorType.CWL.toString(), SourceControl.GITHUB, "/hello_world.cwl", true);
        WorkflowVersion version = snapshotWorkflowVersion(workflowsApi, workflow, "1.0.1");
        assertEquals("Should only be one image in this workflow", 1, version.getImages().size());
        verifyImageChecksumsAreSaved(version);

        List<ToolVersion> versions = ga4Ghv20Api.toolsIdVersionsGet("#workflow/github.com/dockstore-testing/hello_world");
        verifyTRSImageConversion(versions, "1.0.1", 1);

        // Test that a workflow version that contains duplicate images will not store multiples
        workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/zhanghj-8555114", "", DescriptorType.CWL.toString(), SourceControl.GITHUB, "/main.cwl", true);
        WorkflowVersion versionWithDuplicateImages = snapshotWorkflowVersion(workflowsApi, workflow, "1.0");
        assertEquals("Should have grabbed 3 images", 3, versionWithDuplicateImages.getImages().size());
        verifyImageChecksumsAreSaved(versionWithDuplicateImages);
        versions = ga4Ghv20Api.toolsIdVersionsGet("#workflow/github.com/dockstore-testing/zhanghj-8555114");
        verifyTRSImageConversion(versions, "1.0", 3);
    }

    private void verifyTRSImageConversion(final List<ToolVersion> versions, final String snapShottedVersionName, final int numImages) {
        assertFalse("Should have at least one version", versions.isEmpty());
        boolean snapshotInList = false;
        for (ToolVersion trsVersion : versions) {
            if (trsVersion.getName().equals(snapShottedVersionName)) {
                assertTrue(trsVersion.isIsProduction());
                assertTrue(String.format("There should be at least %s image(s) in this workflow. There are %s.", numImages, trsVersion.getImages().size()), trsVersion.getImages().size() >= numImages);
                snapshotInList = true;
                assertFalse(trsVersion.getImages().isEmpty());
                for (ImageData imageData :trsVersion.getImages()) {
                    assertNotNull(imageData.getChecksum());
                    imageData.getChecksum().stream().forEach(checksum -> {
                        assertEquals(checksum.getType(), DOCKER_IMAGE_SHA_TYPE_FOR_TRS);
                        assertFalse(checksum.getChecksum().isEmpty());
                    });
                    assertNotNull(imageData.getSize());
                    assertNotNull(imageData.getRegistryHost());
                }
            } else {
                assertFalse(trsVersion.isIsProduction());
                assertEquals("Non-snapshotted versions should have 0 images ", 0, trsVersion.getImages().size());
            }
        }
        assertTrue("Snapshotted version should be in the list", snapshotInList);
    }

    private void verifyImageChecksumsAreSaved(WorkflowVersion version) {
        assertFalse(version.getImages().isEmpty());
        version.getImages().stream().forEach(image -> image.getChecksums().stream().forEach(checksum -> {
            assertFalse(checksum.getChecksum().isEmpty());
            assertFalse(checksum.getType().isEmpty());
        })
        );
    }

    @Test
    public void testGettingImagesFromGitHubContainerRegistry() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);

        // Test that a versioned multi-architecture image gets an image per architecture: ghcr.io/homebrew/core/python/3.9:3.9.6 -> 5 OS/Arch images
        // Test that a specific architecture image referenced in the following format is grabbed correctly: ghcr.io/<owner>/<image_name>:<tag>@sha256:<digest>
        // Test that an image referenced by digest is grabbed correctly
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/hello-wdl-workflow", "", DescriptorType.WDL.toString(), SourceControl.GITHUB, "/Dockstore.wdl", true);
        WorkflowVersion version = snapshotWorkflowVersion(workflowsApi, workflow, "ghcrImages");
        assertTrue("Should have at least 7 images. There are " + version.getImages().size(), version.getImages().size() >= 7);
        verifyImageChecksumsAreSaved(version);

        List<ToolVersion> versions = ga4Ghv20Api.toolsIdVersionsGet("#workflow/github.com/dockstore-testing/hello-wdl-workflow");
        verifyTRSImageConversion(versions, "ghcrImages", 7);
    }

    @Test
    public void testGettingImagesFromAmazonECR() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);

        // Test that a versioned multi-architecture image gets an image per architecture: public.ecr.aws/ubuntu/ubuntu:18.04 -> 5 OS/Arch images
        // Test that an image referenced by digest is grabbed correctly
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/hello-wdl-workflow", "", DescriptorType.WDL.toString(), SourceControl.GITHUB, "/Dockstore.wdl", true);
        WorkflowVersion version = snapshotWorkflowVersion(workflowsApi, workflow, "ecrImages");
        assertTrue("Should have at least 6 images. There are " + version.getImages().size(), version.getImages().size() >= 6);
        verifyImageChecksumsAreSaved(version);

        List<ToolVersion> versions = ga4Ghv20Api.toolsIdVersionsGet("#workflow/github.com/dockstore-testing/hello-wdl-workflow");
        verifyTRSImageConversion(versions, "ecrImages", 6);
    }

    /**
     * Tests the a checksum is calculated for workflow sourcefiles on refresh or snapshot. Also checks that trs endpoints convert correctly.
     * */
    @Test
    public void testChecksumsForSourceFiles() {
        // Test grabbing checksum on refresh
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);
        Workflow workflow = workflowsApi.manualRegister("github", DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.wdl", "", DescriptorLanguage.WDL.toString(), "/test.json");

        workflow = workflowsApi.refresh(workflow.getId(), false);
        List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        assertFalse(workflowVersions.isEmpty());
        boolean testedWDL = false;

        for (WorkflowVersion workflowVersion : workflowVersions) {
            if (workflowVersion.getName().equals("testBoth") || workflowVersion.getName().equals("testWDL")) {
                testedWDL = true;
                List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(workflowVersion.getId());
                assertNotNull(sourceFiles);
                verifySourcefileChecksumsSaved(sourceFiles);
                sourceFiles.stream().forEach(sourceFile -> assertFalse("Source file should have a checksum", sourceFile.getChecksums().get(0).toString().isEmpty()));
            }
        }
        assertTrue(testedWDL);

        // Test grabbing checksum on snapshot
        Workflow workflow2 = manualRegisterAndPublish(workflowsApi, "dockstore-testing/hello_world", "", DescriptorLanguage.CWL.toString(), SourceControl.GITHUB, "/hello_world.cwl", true);
        WorkflowVersion snapshotVersion = workflow2.getWorkflowVersions().stream().filter(v -> v.getName().equals("1.0.1")).findFirst().get();
        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(snapshotVersion.getId());
        assertNotNull(sourceFiles);
        snapshotWorkflowVersion(workflowsApi, workflow2, "1.0.1");
        verifySourcefileChecksumsSaved(sourceFiles);

        // Make sure refresh does not error.
        workflowsApi.refresh(workflow2.getId(), false);

        // Test TRS conversion
        io.dockstore.openapi.client.model.FileWrapper fileWrapper = ga4Ghv20Api.toolsIdVersionsVersionIdTypeDescriptorGet(DescriptorLanguage.CWL.toString(), "#workflow/github.com/dockstore-testing/hello_world", "1.0.1");
        verifyTRSSourcefileConversion(fileWrapper);

        testingPostgres.runUpdateStatement("update sourcefile set content = null");
        // Make sure the above worked
        final Long nullContentCount = testingPostgres.runSelectStatement(
            "select count(*) from sourcefile where content is null", Long.class);
        assertNotEquals(0, nullContentCount.longValue());

        // Test that null content has a checksum
        final Long nullSha256Count = testingPostgres.runSelectStatement(
            "select count(*) from sourcefile where sha256 is null", Long.class);
        assertEquals(0, nullSha256Count.longValue());

    }

    private void verifyTRSSourcefileConversion(final io.dockstore.openapi.client.model.FileWrapper fileWrapper) {
        assertEquals(1, fileWrapper.getChecksum().size());
        fileWrapper.getChecksum().stream().forEach(checksum -> {
            assertFalse(checksum.getChecksum().isEmpty());
            assertEquals(DESCRIPTOR_FILE_SHA256_TYPE_FOR_TRS, checksum.getType());
        });
    }

    private void verifySourcefileChecksumsSaved(final List<io.dockstore.webservice.core.SourceFile> sourceFiles) {
        assertTrue(sourceFiles.size() >= 1);
        sourceFiles.stream().forEach(sourceFile -> {
            assertFalse("Source File should have a checksum", sourceFile.getChecksums().isEmpty());
            assertTrue(sourceFile.getChecksums().size() >= 1);
            sourceFile.getChecksums().stream().forEach(checksum -> {
                assertEquals(io.dockstore.webservice.core.SourceFile.SHA_TYPE, checksum.getType());
                assertFalse(checksum.getChecksum().isEmpty());
            });
        });
    }

    /**
     * Test that the image_name is set correctly after TRS image conversion.
     * This is a separate test from verifyTRSImageConversion because it's difficult to map the snapshot version's images to the
     * TRS version's images if there's more than 1 Docker reference in the workflow.
     * This test works with workflows containing 1 Docker reference (may not necessarily have only 1 image because DockerHub can provide
     * multiple images for a single version, one for each os/architecture).
     */
    @Test
    public void testTRSImageName() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);
        WorkflowVersion snapshotVersion;
        ToolVersion trsVersion;

        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/hello-wdl-workflow", "",
            DescriptorType.WDL.toString(), SourceControl.GITHUB, "/Dockstore.wdl", true);

        // Workflow with Quay image specified using a tag
        String quayTagVersionName = "1.0";
        snapshotVersion = snapshotWorkflowVersion(workflowsApi, workflow, quayTagVersionName);
        assertEquals("Should only be one image in this workflow", 1, snapshotVersion.getImages().size());
        trsVersion = ga4Ghv20Api.toolsIdVersionsVersionIdGet("#workflow/github.com/dockstore-testing/hello-wdl-workflow", quayTagVersionName);
        assertEquals("Should be one image in this TRS version", 1, trsVersion.getImages().size());
        trsVersion.getImages().stream().forEach(image -> assertEquals("quay.io/ga4gh-dream/dockstore-tool-helloworld:1.0.2", image.getImageName()));

        // Workflow with Quay image specified using a digest
        String quayDigestVersionName = "quayDigestImage";
        snapshotVersion = snapshotWorkflowVersion(workflowsApi, workflow, quayDigestVersionName);
        assertEquals("Should only be one image in this workflow", 1, snapshotVersion.getImages().size());
        trsVersion = ga4Ghv20Api.toolsIdVersionsVersionIdGet("#workflow/github.com/dockstore-testing/hello-wdl-workflow", quayDigestVersionName);
        assertEquals("Should be one image in this TRS version", 1, trsVersion.getImages().size());
        trsVersion.getImages().stream().forEach(image -> assertEquals(
            "quay.io/ga4gh-dream/dockstore-tool-helloworld@sha256:3a854fd1ebd970011fa57c8c099347314eda36cc746fd831f4deff9a1d433718",
            image.getImageName()));

        // Workflow with Docker Hub image specified using a tag (6 images actually retrieved, one per architecture type)
        String dockerHubTagVersionName = "dockerHubTagImage";
        snapshotVersion = snapshotWorkflowVersion(workflowsApi, workflow, dockerHubTagVersionName);
        assertEquals("Should only be six images in this workflow", 6, snapshotVersion.getImages().size()); // 1 image per architecture type
        trsVersion = ga4Ghv20Api.toolsIdVersionsVersionIdGet("#workflow/github.com/dockstore-testing/hello-wdl-workflow", dockerHubTagVersionName);
        assertEquals("Should be six images in this TRS version", 6, trsVersion.getImages().size());
        trsVersion.getImages().stream().forEach(image -> assertEquals("library/ubuntu:16.04", image.getImageName()));

        // Workflow with Docker Hub image specified using a digest
        String dockerHubDigestVersionName = "dockerHubDigestImage";
        snapshotVersion = snapshotWorkflowVersion(workflowsApi, workflow, dockerHubDigestVersionName);
        assertEquals("Should only be one image in this workflow", 1, snapshotVersion.getImages().size());
        trsVersion = ga4Ghv20Api.toolsIdVersionsVersionIdGet("#workflow/github.com/dockstore-testing/hello-wdl-workflow", dockerHubDigestVersionName);
        assertEquals("Should be one image in this TRS version", 1, trsVersion.getImages().size());
        // library/ubuntu@sha256:d7bb0589725587f2f67d0340edb81fd1fcba6c5f38166639cf2a252c939aa30c refers to ubuntu version 16.04, amd64 os/arch
        trsVersion.getImages().stream().forEach(image ->
            assertEquals("library/ubuntu@sha256:d7bb0589725587f2f67d0340edb81fd1fcba6c5f38166639cf2a252c939aa30c", image.getImageName()));
    }

    @Test
    public void testGettingImagesFromDockerHub() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);

        // Test that a version of an official dockerhub image will get an image per architecture. (python 2.7) Also check that regular
        // DockerHub images are grabbed correctly broadinstitute/gatk:4.0.1.1
        Workflow workflow = manualRegisterAndPublish(workflowsApi, "dockstore-testing/broad-prod-wgs-germline-snps-indels", "", DescriptorType.WDL.toString(), SourceControl.GITHUB, "/JointGenotypingWf.wdl", true);
        WorkflowVersion version = snapshotWorkflowVersion(workflowsApi, workflow, "1.1.2");
        assertEquals("Should 10 images in this workflow", 10, version.getImages().size());
        verifyImageChecksumsAreSaved(version);

        List<ToolVersion> versions = ga4Ghv20Api.toolsIdVersionsGet("#workflow/github.com/dockstore-testing/broad-prod-wgs-germline-snps-indels");
        verifyTRSImageConversion(versions, "1.1.2", 10);
    }

    /**
     * Test for cwl1.1
     * Of the languages support features, this tests:
     * Workflow Registration
     * Metadata Display
     * Validation
     */
    @Test
    public void cwlVersion11() {
        final ApiClient userApiClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi userWorkflowsApi = new WorkflowsApi(userApiClient);
        userWorkflowsApi.manualRegister("github", "dockstore-testing/Workflows-For-CI", "/cwl/v1.1/metadata.cwl", "metadata", "cwl",
            "/cwl/v1.1/cat-job.json");
        final Workflow workflowByPathGithub = userWorkflowsApi
            .getWorkflowByPath("github.com/dockstore-testing/Workflows-For-CI/metadata", BIOWORKFLOW, null);
        final Workflow workflow = userWorkflowsApi.refresh(workflowByPathGithub.getId(), true);
        workflow.getWorkflowVersions().forEach(workflowVersion -> {
            assertEquals("Print the contents of a file to stdout using 'cat' running in a docker container.", workflow.getDescription());
            assertEquals("Peter Amstutz", workflow.getAuthor());
            assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        });
        assertEquals("Default branch should've been set to get metadata", "master", workflow.getDefaultVersion());
        assertEquals("peter.amstutz@curoverse.com", workflow.getEmail());
        assertEquals("Print the contents of a file to stdout using 'cat' running in a docker container.", workflow.getDescription());
        assertEquals("Peter Amstutz", workflow.getAuthor());
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        assertEquals("Default version should've been set to get metadata", "master", workflow.getDefaultVersion());
        Optional<WorkflowVersion> optionalWorkflowVersion = workflow.getWorkflowVersions().stream()
            .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersion.isPresent());
        WorkflowVersion workflowVersion = optionalWorkflowVersion.get();
        List<io.dockstore.webservice.core.SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(workflowVersion.getId());
        assertEquals(2, sourceFiles.size());
        assertTrue(sourceFiles.stream().anyMatch(sourceFile -> sourceFile.getPath().equals("/cwl/v1.1/cat-job.json")));
        assertTrue(sourceFiles.stream().anyMatch(sourceFile -> sourceFile.getPath().equals("/cwl/v1.1/metadata.cwl")));
        // Check validation works.  It is invalid because this is a tool and not a workflow.
        assertFalse(workflowVersion.isValid());

        userWorkflowsApi
            .manualRegister("github", "dockstore-testing/Workflows-For-CI", "/cwl/v1.1/count-lines1-wf.cwl", "count-lines1-wf", "cwl",
                "/cwl/v1.1/wc-job.json");
        final Workflow workflowByPathGithub2 = userWorkflowsApi
            .getWorkflowByPath("github.com/dockstore-testing/Workflows-For-CI/count-lines1-wf", BIOWORKFLOW, null);
        final Workflow workflow2 = userWorkflowsApi.refresh(workflowByPathGithub2.getId(), false);
        assertTrue(workflow.getWorkflowVersions().stream().anyMatch(versions -> "master".equals(versions.getName())));
        Optional<WorkflowVersion> optionalWorkflowVersion2 = workflow2.getWorkflowVersions().stream()
            .filter(version -> "master".equalsIgnoreCase(version.getName())).findFirst();
        assertTrue(optionalWorkflowVersion2.isPresent());
        WorkflowVersion workflowVersion2 = optionalWorkflowVersion2.get();
        // Check validation works.  It should be valid
        assertTrue(workflowVersion2.isValid());
        userWorkflowsApi.publish(workflowByPathGithub2.getId(), CommonTestUtilities.createPublishRequest(true));
    }

    /**
     * This tests that you can get all workflows by path (ignores workflow name)
     */
    @Test
    public void testGetAllWorkflowByPath() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        String path = "github.com/DockstoreTestUser2/nested-wdl";

        io.dockstore.openapi.client.model.Workflow workflow1 = workflowsApi.manualRegister("github", "DockstoreTestUser2/nested-wdl",
            "/Dockstore.wdl", "workflow1", "wdl", "/test.json");
        assertEquals(path, workflow1.getPath());

        io.dockstore.openapi.client.model.Workflow workflow2 = workflowsApi.manualRegister("github", "DockstoreTestUser2/nested-wdl",
            "/Dockstore.wdl", "workflow2", "wdl", "/test.json");
        assertEquals(path, workflow2.getPath());

        List<io.dockstore.openapi.client.model.Workflow> foundWorkflows = workflowsApi.getAllWorkflowByPath(path);
        assertEquals(2, foundWorkflows.size());
    }

    /**
     * This tests that you can get a workflows by full workflow path
     */
    @Test
    public void testGetWorkflowByPath() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);

        // Find a workflow with no workflow name
        io.dockstore.openapi.client.model.Workflow workflow = workflowsApi.manualRegister("github", "DockstoreTestUser2/nested-wdl",
            "/Dockstore.wdl", null, "wdl", "/test.json");
        assertEquals("github.com/DockstoreTestUser2/nested-wdl", workflow.getFullWorkflowPath());
        io.dockstore.openapi.client.model.Workflow foundWorkflow = workflowsApi.getWorkflowByPath(workflow.getFullWorkflowPath(), WorkflowSubClass.BIOWORKFLOW, "");
        assertEquals(workflow.getId(), foundWorkflow.getId());

        // Find a workflow with a workflow name
        workflow = workflowsApi.manualRegister("github", "DockstoreTestUser2/nested-wdl",
            "/Dockstore.wdl", "foo", "wdl", "/test.json");
        assertEquals("github.com/DockstoreTestUser2/nested-wdl/foo", workflow.getFullWorkflowPath());
        foundWorkflow = workflowsApi.getWorkflowByPath(workflow.getFullWorkflowPath(), WorkflowSubClass.BIOWORKFLOW, "");
        assertEquals(workflow.getId(), foundWorkflow.getId());
    }


}
