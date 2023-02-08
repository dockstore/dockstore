/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.metrics;

import static io.dockstore.webservice.metrics.MetricsDataS3ClientIT.LOCALSTACK_IMAGE_TAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cloud.localstack.ServiceName;
import cloud.localstack.awssdkv2.TestUtils;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.IEnvironmentVariableProvider;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import com.google.gson.Gson;
import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.LocalStackTest;
import io.dockstore.common.Registry;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.webservice.core.Partner;
import io.dockstore.webservice.core.metrics.MetricsData;
import io.dockstore.webservice.core.metrics.MetricsDataMetadata;
import io.dockstore.webservice.core.metrics.MetricsDataS3Client;
import io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl;
import io.dropwizard.testing.ResourceHelpers;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(LocalstackDockerExtension.class)
@Tag(LocalStackTest.NAME)
@LocalstackDockerProperties(imageTag = LOCALSTACK_IMAGE_TAG, services = { ServiceName.S3 }, environmentVariableProvider = MetricsDataS3ClientIT.LocalStackEnvironmentVariables.class)
public class MetricsDataS3ClientIT extends BaseIT {
    public static final String LOCALSTACK_IMAGE_TAG = "1.3.1";
    private static final Gson GSON = new Gson();
    private static String bucketName;
    private static S3Client s3Client;
    private static MetricsDataS3Client metricsDataClient;

    @BeforeAll
    public static void setup() throws URISyntaxException {
        bucketName = SUPPORT.getConfiguration().getMetricsConfig().getS3BucketName();
        s3Client = TestUtils.getClientS3V2(); // Use localstack S3Client
        metricsDataClient = new MetricsDataS3Client(bucketName, s3Client);
        // Create a bucket to be used for tests
        CreateBucketRequest request = CreateBucketRequest.builder().bucket(bucketName).build();
        s3Client.createBucket(request);
        deleteBucketContents(); // This is here just in case a test was stopped before tearDown could clean up the bucket
    }

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        // Set the MetricsDataS3Client to use the localstack S3 client for testing
        ToolsApiExtendedServiceImpl.setMetricsDataS3Client(new MetricsDataS3Client(SUPPORT.getConfiguration().getMetricsConfig().getS3BucketName(), s3Client));
    }

    @AfterEach
    public void tearDown() {
        // Delete all objects from the S3 bucket after each test
        deleteBucketContents();
    }

    private static void deleteBucketContents() {
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).build();
        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        List<S3Object> contents = response.contents();
        contents.forEach(s3Object -> {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(s3Object.key()).build();
            s3Client.deleteObject(deleteObjectRequest);
        });
    }

    /**
     * Test submitting metrics data using the Extended GA4GH endpoint.
     *
     * At the end of this test, the S3 folder structure should look like the following:
     * local-dockstore-metrics-data
     * ├── tool
     * │   └── quay.io
     * │       └── dockstoretestuser2
     * │           └── dockstore-cgpmap
     * │               └── symbolic.v1
     * │                   └── TERRA
     * │                       └── 1673972062578.json
     * └── workflow
     *     └── github.com
     *         └── DockstoreTestUser2
     *             └── dockstore_workflow_cnv
     *                 └── master
     *                     ├── TERRA
     *                     │   └── 1673972062578.json
     *                     └── DNA_STACK
     *                         └── 1673972062578.json
     * @throws IOException
     */
    @Test
    void testSubmitMetricsData() throws IOException {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final UsersApi usersApi = new UsersApi(webClient);
        final ContainersApi containersApi = new ContainersApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        final String platform1 = Partner.TERRA.name();
        final String platform2 = Partner.DNA_STACK.name();
        final String description = "A single execution";
        final Long ownerUserId = usersApi.getUser().getId();

        // Register and publish a workflow
        final String workflowId = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv";
        final String workflowVersionId = "master";
        final String workflowExpectedS3KeyPrefixFormat = "workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv/master/%s"; // This is the prefix without the file name
        Workflow workflow = workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl",
                "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Add execution metrics for a workflow version for one platform
        List<Execution> executions = createExecutions(1);
        extendedGa4GhApi.executionMetricsPost(executions, platform1, workflowId, workflowVersionId, description);
        List<MetricsData> metricsDataList = verifyMetricsDataList(workflowId, workflowVersionId, 1);
        MetricsData metricsData = verifyMetricsDataInfo(metricsDataList, workflowId, workflowVersionId, platform1, String.format(workflowExpectedS3KeyPrefixFormat, platform1));
        verifyMetricsDataMetadata(metricsData, ownerUserId, description);
        verifyMetricsDataContent(metricsData, executions);

        // Send more metrics data to S3 for the same workflow version, but different platform
        executions = createExecutions(2);
        extendedGa4GhApi.executionMetricsPost(executions, platform2, workflowId, workflowVersionId, description);
        metricsDataList = verifyMetricsDataList(workflowId, workflowVersionId, 2);
        metricsData = verifyMetricsDataInfo(metricsDataList, workflowId, workflowVersionId, platform2, String.format(workflowExpectedS3KeyPrefixFormat, platform2));
        verifyMetricsDataMetadata(metricsData, ownerUserId, description);
        verifyMetricsDataContent(metricsData, executions);

        // Register and publish a tool
        DockstoreTool tool = new DockstoreTool();
        tool.setDefaultCwlPath("/cwls/cgpmap-bamOut.cwl");
        tool.setGitUrl("git@github.com:DockstoreTestUser2/dockstore-cgpmap.git");
        tool.setNamespace("dockstoretestuser2");
        tool.setName("dockstore-cgpmap");
        tool.setRegistryString(Registry.QUAY_IO.getDockerPath());
        tool.setDefaultVersion("symbolic.v1");
        tool.setDefaultCWLTestParameterFile("/examples/cgpmap/bamOut/bam_input.json");
        DockstoreTool registeredTool = containersApi.registerManual(tool);
        registeredTool = containersApi.refresh(registeredTool.getId());
        containersApi.publish(registeredTool.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        final String toolId = "quay.io/dockstoretestuser2/dockstore-cgpmap";
        final String toolVersionId = "symbolic.v1";
        final String toolExpectedS3KeyPrefixFormat = "tool/quay.io/dockstoretestuser2/dockstore-cgpmap/symbolic.v1/%s"; // This is the prefix without the file name. Format by providing the platform
        extendedGa4GhApi.executionMetricsPost(executions, platform1, toolId, toolVersionId, null);
        metricsDataList = verifyMetricsDataList(toolId, toolVersionId, 1);
        metricsData = verifyMetricsDataInfo(metricsDataList, toolId, toolVersionId, platform1, String.format(toolExpectedS3KeyPrefixFormat, platform1));
        verifyMetricsDataMetadata(metricsData, ownerUserId, "");
        verifyMetricsDataContent(metricsData, executions);
        assertEquals(3, getS3ObjectsFromBucket().size(), "There should be 3 objects, 2 for workflows and 1 for tools");
    }

    @Test
    void testSubmitMetricsDataErrors() {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        // Non-admin user
        final ApiClient otherWebClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final ExtendedGa4GhApi otherExtendedGa4GhApi = new ExtendedGa4GhApi(otherWebClient);

        String id = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv";
        String versionId = "master";
        String platform = Partner.TERRA.name();
        String description = "A single execution";

        // Test malformed ID
        ApiException exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(List.of(), platform, "malformedId", "malformedVersionId", null));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getCode());

        // Test ID that doesn't exist
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(List.of(), platform, "github.com/nonexistent/id", "master", null));
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), exception.getCode(), "Should not be able to submit metrics for non-existent id");
        assertTrue(exception.getMessage().contains("Tool not found"), "Should not be able to submit metrics for non-existent id");

        // Test version ID that doesn't exist
        Workflow workflow = workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl",
                "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(List.of(), platform, id, "nonexistentVersionId", null));
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), exception.getCode(), "Should not be able to submit metrics for non-existent version");
        assertTrue(exception.getMessage().contains("Version not found"), "Should not be able to submit metrics for non-existent version");

        // Test that a non-admin/non-curator user can't submit metrics
        exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.executionMetricsPost(createExecutions(1), platform, id, versionId, description));
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), exception.getCode(), "Non-admin and non-curator user should not be able to submit metrics");

        // Test that the response body must contain ExecutionStatus
        List<Execution> executions = createExecutions(1);
        executions.forEach(execution -> execution.setExecutionStatus(null));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(executions, platform, id, versionId, description));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getCode(), "Should not be able to submit metrics if ExecutionStatus is missing");
        assertTrue(exception.getMessage().contains("must contain ExecutionStatus"), "Should not be able to submit metrics if ExecutionStatus is missing");

        // Verify that not providing metrics data throws an exception
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(List.of(), platform, id, versionId, description));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getCode(), "Should throw if execution metrics not provided");
        assertTrue(exception.getMessage().contains("Execution metrics data must be provided"), "Should throw if execution metrics not provided");
    }

    /**
     * Tests the scenario where an S3 folder has more than 1000 objects. The ListObjectsV2Request returns at most 1,000 objects and paginates the rest.
     * This tests that we can retrieve all S3 objects if there is pagination.
     * @throws IOException
     */
    @Test
    void testGetMetricsDataPagination() throws FileNotFoundException {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        final String platform = Partner.TERRA.name();
        final String description = "A single execution";
        // This uses the request body from a file, which is a visual JSON example of what the request body looks like
        BufferedReader metricsRequestBodyBufferedReader = new BufferedReader(new FileReader(ResourceHelpers.resourceFilePath("prototype-metrics-request-body.json")));
        List<Execution> executions = Arrays.stream(GSON.fromJson(metricsRequestBodyBufferedReader, Execution[].class)).toList();

        // Register and publish a workflow
        final String workflowId = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv";
        final String workflowVersionId = "master";
        Workflow workflow = workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl",
                "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Create 1001 S3 objects by calling the endpoint that submits metrics 1001 times for a workflow version and verify that we retrieve all 1001 objects
        for (int i = 0; i < 1001; ++i) {
            // Note that all these objects will be in the same folder because only the file name is different for each object
            extendedGa4GhApi.executionMetricsPost(executions, platform, workflowId, workflowVersionId, description);
        }

        List<MetricsData> metricsDataList = metricsDataClient.getMetricsData(workflowId, workflowVersionId);
        assertEquals(1001, metricsDataList.size());
    }

    private List<Execution> createExecutions(int numberOfExecutions) {
        List<Execution> executions = new ArrayList<>();
        for (int i = 0; i < numberOfExecutions; ++i) {
            // A successful execution that ran for 5 minutes
            Execution execution = new Execution();
            execution.setExecutionStatus(Execution.ExecutionStatusEnum.SUCCESSFUL);
            execution.setExecutionTime("PT5m");
            Map<String, Object> additionalProperties = Map.of("schema.org:totalTime", "PT5m");
            execution.setAdditionalProperties(additionalProperties);
            executions.add(execution);
        }
        return executions;
    }

    /**
     * Checks the number of MetricsData that a version has then return the list
     * @param id
     * @param versionId
     * @param expectedSize
     * @return List of MetricsData
     */
    private List<MetricsData> verifyMetricsDataList(String id, String versionId, int expectedSize) {
        List<MetricsData> metricsDataList = metricsDataClient.getMetricsData(id, versionId);
        assertEquals(expectedSize, metricsDataList.size());
        return metricsDataList;
    }

    /**
     * Verifies the MetricsData info then returns it
     * @param metricsDataList
     * @param id
     * @param versionId
     * @param platform
     * @param s3KeyPrefix
     * @return
     */
    private MetricsData verifyMetricsDataInfo(List<MetricsData> metricsDataList, String id, String versionId, String platform, String s3KeyPrefix) {
        MetricsData metricsData = metricsDataList.stream().filter(data -> data.s3Key().startsWith(s3KeyPrefix)).findFirst().orElse(null);
        assertNotNull(metricsData);
        assertEquals(id, metricsData.toolId());
        assertEquals(versionId, metricsData.toolVersionName());
        assertEquals(platform, metricsData.platform());
        // The full file name and S3 key are not checked because it depends on the time the data was submitted which is unknown
        assertTrue(metricsData.fileName().endsWith(".json"));
        assertTrue(metricsData.s3Key().startsWith(s3KeyPrefix));
        return metricsData;
    }

    private void verifyMetricsDataMetadata(MetricsData metricsData, long ownerUserId, String description) {
        MetricsDataMetadata metricsDataMetadata = metricsDataClient.getMetricsDataMetadata(metricsData);
        assertEquals(ownerUserId, metricsDataMetadata.owner());
        assertEquals(description, metricsDataMetadata.description());
    }

    private void verifyMetricsDataContent(MetricsData metricsData, List<Execution> expectedExecutions)
            throws IOException {
        String metricsDataContent = metricsDataClient.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(), metricsData.platform(), metricsData.fileName());
        List<Execution> s3Executions = Arrays.stream(GSON.fromJson(metricsDataContent, Execution[].class)).toList();
        assertEquals(expectedExecutions.size(), s3Executions.size());
        for (Execution s3Execution : s3Executions) {
            assertTrue(expectedExecutions.contains(s3Execution));
        }
    }

    private List<S3Object> getS3ObjectsFromBucket() {
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).build();
        ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(request);
        return listObjectsV2Response.contents();
    }

    public static class LocalStackEnvironmentVariables implements IEnvironmentVariableProvider {
        @Override
        public Map<String, String> getEnvironmentVariables() {
            // Need this so that S3 key encoding works. Remove when there's a new localstack release containing the fix
            // https://github.com/localstack/localstack/issues/7374#issuecomment-1360950643
            return Map.of("PROVIDER_OVERRIDE_S3", "asf");
        }
    }
}
