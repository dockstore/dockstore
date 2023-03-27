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

import static io.dockstore.client.cli.BaseIT.OTHER_USERNAME;
import static io.dockstore.client.cli.BaseIT.USER_2_USERNAME;
import static io.dockstore.common.CommonTestUtilities.getOpenAPIWebClient;
import static io.dockstore.common.LocalStackTestUtilities.IMAGE_TAG;
import static io.dockstore.common.LocalStackTestUtilities.LocalStackEnvironmentVariables;
import static io.dockstore.common.LocalStackTestUtilities.createBucket;
import static io.dockstore.common.LocalStackTestUtilities.deleteBucketContents;
import static io.dockstore.common.LocalStackTestUtilities.getS3ObjectsFromBucket;
import static io.dockstore.webservice.core.metrics.constraints.HasExecutions.MUST_CONTAIN_EXECUTIONS;
import static io.dockstore.webservice.core.metrics.constraints.ISO1806ExecutionTime.EXECUTION_TIME_FORMAT_ERROR;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.TOOL_NOT_FOUND_ERROR;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.VERSION_NOT_FOUND_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cloud.localstack.ServiceName;
import cloud.localstack.awssdkv2.TestUtils;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import com.google.gson.Gson;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.LocalStackTest;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.common.TestingPostgres;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Partner;
import io.dockstore.webservice.core.metrics.MetricsData;
import io.dockstore.webservice.core.metrics.MetricsDataMetadata;
import io.dockstore.webservice.core.metrics.MetricsDataS3Client;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(LocalstackDockerExtension.class)
@Tag(LocalStackTest.NAME)
@LocalstackDockerProperties(imageTag = IMAGE_TAG, services = { ServiceName.S3 }, environmentVariableProvider = LocalStackEnvironmentVariables.class)
public class MetricsDataS3ClientIT {
    private static final Gson GSON = new Gson();
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
    private static TestingPostgres testingPostgres;
    private static String bucketName;
    private static String s3EndpointOverride;
    private static S3Client s3Client;
    private static MetricsDataS3Client metricsDataClient;

    @BeforeAll
    public static void setup() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);

        bucketName = SUPPORT.getConfiguration().getMetricsConfig().getS3BucketName();
        s3EndpointOverride = SUPPORT.getConfiguration().getMetricsConfig().getS3EndpointOverride();
        metricsDataClient = new MetricsDataS3Client(bucketName, s3EndpointOverride);

        // Create a bucket to be used for tests
        s3Client = TestUtils.getClientS3V2(); // Use localstack S3Client
        createBucket(s3Client, bucketName);
        deleteBucketContents(s3Client, bucketName); // This is here just in case a test was stopped before tearDown could clean up the bucket
    }

    @BeforeEach
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @AfterEach
    public void tearDown() {
        // Delete all objects from the S3 bucket after each test
        deleteBucketContents(s3Client, bucketName);
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.getEnvironment().healthChecks().shutdown();
        SUPPORT.after();
    }

    /**
     * Test submitting metrics data using the Extended GA4GH endpoint.
     *
     * At the end of this test, the S3 folder structure should look like the following. Note that OBJECT METADATA is the S3 object metadata and is not part of the folder structure
     * local-dockstore-metrics-data
     * ├── tool
     * │   └── quay.io
     * │       └── dockstoretestuser2
     * │           └── dockstore-cgpmap
     * │               └── symbolic.v1
     * │                   └── TERRA
     * │                       └── 1673972062578.json
     * │                           └── OBJECT METADATA
     * │                               └── owner: 1
     * │                               └── description:
     * └── workflow
     *     └── github.com
     *         └── DockstoreTestUser2
     *             └── dockstore_workflow_cnv%2Fmy-workflow
     *                 └── master
     *                     ├── TERRA
     *                     │   └── 1673972062578.json
     *                     │       └── OBJECT METADATA
     *                     │           └── owner: 1
     *                     │           └── description: A single execution
     *                     └── DNA_STACK
     *                         └── 1673972062578.json
     *                             └── OBJECT METADATA
     *                                 └── owner: 1
     *                                 └── description: A single execution
     * @throws IOException
     */
    @Test
    void testSubmitMetricsData() throws IOException {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final UsersApi usersApi = new UsersApi(webClient);
        final ContainersApi containersApi = new ContainersApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        final String platform1 = Partner.TERRA.name();
        final String platform2 = Partner.DNA_STACK.name();
        final String description = "A single execution";
        final Long ownerUserId = usersApi.getUser().getId();

        // Register and publish a workflow
        final String workflowId = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv/my-workflow";
        final String workflowVersionId = "master";
        final String workflowExpectedS3KeyPrefixFormat = "workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv%%2Fmy-workflow/master/%s"; // This is the prefix without the file name
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "my-workflow", "cwl",
                "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Add execution metrics for a workflow version for one platform
        List<RunExecution> runExecutions = createRunExecutions(1);
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(runExecutions), platform1, workflowId, workflowVersionId, description);
        List<MetricsData> metricsDataList = verifyMetricsDataList(workflowId, workflowVersionId, 1);
        MetricsData metricsData = verifyMetricsDataInfo(metricsDataList, workflowId, workflowVersionId, platform1, String.format(workflowExpectedS3KeyPrefixFormat, platform1));
        verifyMetricsDataMetadata(metricsData, ownerUserId, description);
        verifyRunExecutionMetricsDataContent(metricsData, runExecutions);

        // Send validation metrics data to S3 for the same workflow version, but different platform
        List<ValidationExecution> validationExecutions = List.of(new ValidationExecution().valid(true).validatorTool("miniwdl")); // This workflow version successfully validated with miniwdl
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().validationExecutions(validationExecutions), platform2, workflowId, workflowVersionId, description);
        metricsDataList = verifyMetricsDataList(workflowId, workflowVersionId, 2);
        metricsData = verifyMetricsDataInfo(metricsDataList, workflowId, workflowVersionId, platform2, String.format(workflowExpectedS3KeyPrefixFormat, platform2));
        verifyMetricsDataMetadata(metricsData, ownerUserId, description);
        verifyValidationExecutionMetricsDataContent(metricsData, validationExecutions);

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
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(runExecutions), platform1, toolId, toolVersionId, null);
        metricsDataList = verifyMetricsDataList(toolId, toolVersionId, 1);
        metricsData = verifyMetricsDataInfo(metricsDataList, toolId, toolVersionId, platform1, String.format(toolExpectedS3KeyPrefixFormat, platform1));
        verifyMetricsDataMetadata(metricsData, ownerUserId, "");
        verifyRunExecutionMetricsDataContent(metricsData, runExecutions);
        assertEquals(3, getS3ObjectsFromBucket(s3Client, bucketName).size(), "There should be 3 objects, 2 for workflows and 1 for tools");
    }

    @Test
    void testSubmitMetricsDataErrors() {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        // Non-admin user
        final ApiClient otherWebClient = getOpenAPIWebClient(true, OTHER_USERNAME, testingPostgres);
        final ExtendedGa4GhApi otherExtendedGa4GhApi = new ExtendedGa4GhApi(otherWebClient);

        String id = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv";
        String versionId = "master";
        String platform = Partner.TERRA.name();
        String description = "A single execution";
        ExecutionsRequestBody goodExecutionsRequestBody = new ExecutionsRequestBody().runExecutions(createRunExecutions(1));

        // Test malformed ID
        ApiException exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(goodExecutionsRequestBody, platform, "malformedId", "malformedVersionId", null));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode());

        // Test ID that doesn't exist
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(goodExecutionsRequestBody, platform, "github.com/nonexistent/id", "master", null));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode(), "Should not be able to submit metrics for non-existent id");
        assertTrue(exception.getMessage().contains(TOOL_NOT_FOUND_ERROR), "Should not be able to submit metrics for non-existent id");

        // Test version ID that doesn't exist
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "",
                DescriptorLanguage.CWL.toString(), "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(goodExecutionsRequestBody, platform, id, "nonexistentVersionId", null));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode(), "Should not be able to submit metrics for non-existent version");
        assertTrue(exception.getMessage().contains(VERSION_NOT_FOUND_ERROR), "Should not be able to submit metrics for non-existent version");

        // Test that a non-admin/non-curator user can't submit metrics
        exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.executionMetricsPost(goodExecutionsRequestBody, platform, id, versionId, description));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode(), "Non-admin and non-curator user should not be able to submit metrics");

        // Test that the response body must contain ExecutionStatus for RunExecution
        List<RunExecution> runExecutions = createRunExecutions(1);
        runExecutions.forEach(execution -> execution.setExecutionStatus(null));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(runExecutions), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to submit metrics if ExecutionStatus is missing");
        assertTrue(exception.getMessage().contains("executionStatus") && exception.getMessage().contains("is missing"), "Should not be able to submit metrics if ExecutionStatus is missing");

        // Test that malformed ExecutionTimes for RunExecution throw an exception
        List<RunExecution> malformedExecutionTimes = List.of(
                new RunExecution().executionStatus(RunExecution.ExecutionStatusEnum.SUCCESSFUL).executionTime("1 second"),
                new RunExecution().executionStatus(RunExecution.ExecutionStatusEnum.SUCCESSFUL).executionTime("PT 1S") // Should not have space
        );
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(malformedExecutionTimes), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to submit metrics if ExecutionTime is malformed");
        assertTrue(exception.getMessage().contains(EXECUTION_TIME_FORMAT_ERROR));
        assertTrue(exception.getMessage().contains("1 second")
                && exception.getMessage().contains("PT 1S"), "Should not be able to submit metrics if ExecutionTime is malformed");

        // Test that the response body must contain the required fields for ValidationExecution
        List<ValidationExecution> validationExecutions = List.of(new ValidationExecution());
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().validationExecutions(validationExecutions), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to submit metrics if required fields for ValidationExecution are missing");
        assertTrue(exception.getMessage().contains("valid") && exception.getMessage().contains("validatorTool") && exception.getMessage().contains("is missing"), "Should not be able to submit metrics if required fields for ValidationExecution are missing");

        // Verify that not providing metrics data throws an exception
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody(), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should throw if execution metrics not provided");
        assertTrue(exception.getMessage().contains(MUST_CONTAIN_EXECUTIONS), "Should throw if execution metrics not provided");
    }

    /**
     * Tests the scenario where an S3 folder has more than 1000 objects. The ListObjectsV2Request returns at most 1,000 objects and paginates the rest (<a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectsV2.html">source</a>)
     * This tests that we can retrieve all S3 objects if there is pagination.
     * @throws IOException
     */
    @Test
    void testGetMetricsDataPagination() throws FileNotFoundException {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(true, USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        final String platform = Partner.TERRA.name();
        final String description = "A single execution";
        // This uses the request body from a file, which is a visual JSON example of what the request body looks like
        BufferedReader metricsRequestBodyBufferedReader = new BufferedReader(new FileReader(ResourceHelpers.resourceFilePath("prototype-metrics-request-body.json")));
        ExecutionsRequestBody executionsRequestBody = GSON.fromJson(metricsRequestBodyBufferedReader, ExecutionsRequestBody.class);

        // Register and publish a workflow
        final String workflowId = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv";
        final String workflowVersionId = "master";
        Workflow workflow = workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "",
                DescriptorLanguage.CWL.toString(), "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Create 1001 S3 objects by calling the endpoint that submits metrics 1001 times for a workflow version and verify that we retrieve all 1001 objects
        for (int i = 0; i < 1001; ++i) {
            // Note that all these objects will be in the same folder because only the file name is different for each object
            extendedGa4GhApi.executionMetricsPost(executionsRequestBody, platform, workflowId, workflowVersionId, description);
        }

        List<MetricsData> metricsDataList = metricsDataClient.getMetricsData(workflowId, workflowVersionId);
        assertEquals(1001, metricsDataList.size());
    }

    public static List<RunExecution> createRunExecutions(int numberOfExecutions) {
        List<RunExecution> executions = new ArrayList<>();
        for (int i = 0; i < numberOfExecutions; ++i) {
            // A successful execution that ran for 5 minutes, requires 2 CPUs and 2 GBs of memory
            RunExecution execution = new RunExecution();
            execution.setExecutionStatus(RunExecution.ExecutionStatusEnum.SUCCESSFUL);
            execution.setExecutionTime("PT5M");
            execution.setCpuRequirements(2);
            execution.setMemoryRequirementsGB(2.0);
            Map<String, Object> additionalProperties = Map.of("schema.org:totalTime", "PT5M");
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

    private void verifyRunExecutionMetricsDataContent(MetricsData metricsData, List<RunExecution> expectedRunExecutions)
            throws IOException {
        String metricsDataContent = metricsDataClient.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(), metricsData.platform(), metricsData.fileName());
        ExecutionsRequestBody executionsRequestBody = GSON.fromJson(metricsDataContent, ExecutionsRequestBody.class);
        List<RunExecution> s3RunExecutions = executionsRequestBody.getRunExecutions();
        assertEquals(expectedRunExecutions.size(), s3RunExecutions.size());
        for (RunExecution s3RunExecution : s3RunExecutions) {
            assertTrue(expectedRunExecutions.contains(s3RunExecution));
        }
    }

    private void verifyValidationExecutionMetricsDataContent(MetricsData metricsData, List<ValidationExecution> expectedValidationExecutions)
            throws IOException {
        String metricsDataContent = metricsDataClient.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(), metricsData.platform(), metricsData.fileName());
        ExecutionsRequestBody executionsRequestBody = GSON.fromJson(metricsDataContent, ExecutionsRequestBody.class);
        List<ValidationExecution> s3ValidationExecutions = executionsRequestBody.getValidationExecutions();
        assertEquals(expectedValidationExecutions.size(), s3ValidationExecutions.size());
        for (ValidationExecution s3RunExecution : s3ValidationExecutions) {
            assertTrue(expectedValidationExecutions.contains(s3RunExecution));
        }
    }
}
