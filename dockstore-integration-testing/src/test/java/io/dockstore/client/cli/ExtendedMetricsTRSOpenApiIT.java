/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.client.cli;

import static io.dockstore.common.LocalStackTestUtilities.IMAGE_TAG;
import static io.dockstore.common.LocalStackTestUtilities.createBucket;
import static io.dockstore.common.LocalStackTestUtilities.deleteBucketContents;
import static io.dockstore.common.LocalStackTestUtilities.getS3ObjectsFromBucket;
import static io.dockstore.common.metrics.ExecutionStatus.ABORTED;
import static io.dockstore.common.metrics.ExecutionStatus.FAILED_RUNTIME_INVALID;
import static io.dockstore.common.metrics.ExecutionStatus.FAILED_SEMANTIC_INVALID;
import static io.dockstore.common.metrics.ExecutionStatus.SUCCESSFUL;
import static io.dockstore.common.metrics.MetricsDataS3Client.generateKey;
import static io.dockstore.common.metrics.ValidationExecution.ValidatorTool.MINIWDL;
import static io.dockstore.common.metrics.constraints.HasExecutionsOrMetrics.MUST_CONTAIN_EXECUTIONS_OR_METRICS;
import static io.dockstore.common.metrics.constraints.HasUniqueExecutionIds.MUST_CONTAIN_UNIQUE_EXECUTION_IDS;
import static io.dockstore.common.metrics.constraints.ISO8601ExecutionDate.EXECUTION_DATE_FORMAT_ERROR;
import static io.dockstore.common.metrics.constraints.ISO8601ExecutionTime.EXECUTION_TIME_FORMAT_ERROR;
import static io.dockstore.common.metrics.constraints.ValidClientExecutionStatus.INVALID_EXECUTION_STATUS_MESSAGE;
import static io.dockstore.common.metrics.constraints.ValidExecutionId.INVALID_EXECUTION_ID_MESSAGE;
import static io.dockstore.webservice.core.metrics.constraints.HasMetrics.MUST_CONTAIN_METRICS;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.EXECUTION_NOT_FOUND_ERROR;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.FORBIDDEN_PLATFORM;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.INVALID_PLATFORM;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.TOOL_NOT_FOUND_ERROR;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.VERSION_NOT_FOUND_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cloud.localstack.ServiceName;
import cloud.localstack.awssdkv2.TestUtils;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import com.google.gson.Gson;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.LocalStackTest;
import io.dockstore.common.LocalStackTestUtilities;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Partner;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.common.metrics.MetricsData;
import io.dockstore.common.metrics.MetricsDataMetadata;
import io.dockstore.common.metrics.MetricsDataS3Client;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Cost;
import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.ExecutionsResponseBody;
import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.MetricsByStatus;
import io.dockstore.openapi.client.model.PrivilegeRequest;
import io.dockstore.openapi.client.model.PrivilegeRequest.PlatformPartnerEnum;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum;
import io.dockstore.openapi.client.model.TaskExecutions;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import io.dockstore.openapi.client.model.ValidatorInfo;
import io.dockstore.openapi.client.model.ValidatorVersionInfo;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.core.metrics.ExecutionTimeStatisticMetric;
import io.dockstore.webservice.core.metrics.MemoryStatisticMetric;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Extra confidential integration tests, focuses on proposed metrics GA4GH extensions
 * {@link BaseIT}
 */
@LocalstackDockerProperties(imageTag = IMAGE_TAG, services = { ServiceName.S3 }, environmentVariableProvider = LocalStackTestUtilities.LocalStackEnvironmentVariables.class)
@ExtendWith({ SystemStubsExtension.class, MuteForSuccessfulTests.class, BaseIT.TestStatus.class, LocalstackDockerExtension.class })
@Tag(ConfidentialTest.NAME)
@Tag(LocalStackTest.NAME)
class ExtendedMetricsTRSOpenApiIT extends BaseIT {

    public static final String DOCKSTORE_WORKFLOW_CNV_REPO = "DockstoreTestUser2/dockstore_workflow_cnv";
    private static final String DOCKSTORE_WORKFLOW_CNV_PATH = SourceControl.GITHUB + "/" + DOCKSTORE_WORKFLOW_CNV_REPO;
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedMetricsTRSOpenApiIT.class);

    private static String bucketName;
    private static String s3EndpointOverride;
    private static MetricsDataS3Client metricsDataClient;
    private static S3Client s3Client;


    @SystemStub
    public final SystemOut systemOut = new SystemOut();

    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @BeforeAll
    public static void setup() throws Exception {
        bucketName = SUPPORT.getConfiguration().getMetricsConfig().getS3BucketName();
        s3EndpointOverride = SUPPORT.getConfiguration().getMetricsConfig().getS3EndpointOverride();
        metricsDataClient = new MetricsDataS3Client(bucketName, s3EndpointOverride);
        // Create a bucket to be used for tests
        s3Client = TestUtils.getClientS3V2(); // Use localstack S3Client
        createBucket(s3Client, bucketName);
        deleteBucketContents(s3Client, bucketName); // This is here just in case a test was stopped before tearDown could clean up the bucket
    }

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @AfterEach
    public void tearDown() {
        // Delete all objects from the S3 bucket after each test
        deleteBucketContents(s3Client, bucketName);
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
     */
    @Test
    void testSubmitMetricsData() {
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
        final String workflowId = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv/my-workflow";
        final String workflowVersionId = "master";
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "my-workflow", "cwl",
                "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Add workflow execution metrics and task execution metrics for a workflow version for one platform
        // List of 1 workflow run execution
        List<RunExecution> runExecutions = createRunExecutions(1);
        // List of one set of task executions. This set of task executions represent two tasks that were executed during the workflow execution
        List<TaskExecutions> taskExecutions = List.of(createTaskExecutions(2));
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(runExecutions).taskExecutions(taskExecutions), platform1, workflowId, workflowVersionId, description);

        verifyMetricsDataList(workflowId, workflowVersionId, platform1, ownerUserId, description, 1);
        // Verify the workflow run execution submitted
        verifyRunExecutionMetricsDataContent(runExecutions, extendedGa4GhApi, workflowId, workflowVersionId, platform1);
        // Verify the set of task executions submitted
        verifyTaskExecutionMetricsDataContent(taskExecutions, extendedGa4GhApi, workflowId, workflowVersionId, platform1);

        // Send validation metrics data to S3 for the same workflow version, but different platform
        // This workflow version successfully validated with miniwdl
        ValidationExecution validationExecution = new ValidationExecution();
        validationExecution.setExecutionId(generateExecutionId());
        validationExecution.setIsValid(true);
        validationExecution.setValidatorTool(ValidationExecution.ValidatorToolEnum.MINIWDL);
        validationExecution.setValidatorToolVersion("v1.9.1");
        validationExecution.setDateExecuted(Instant.now().toString());
        List<ValidationExecution> validationExecutions = List.of(validationExecution);
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().validationExecutions(validationExecutions), platform2, workflowId, workflowVersionId, description);
        verifyMetricsDataList(workflowId, workflowVersionId, platform2, ownerUserId, description, 1);
        verifyValidationExecutionMetricsDataContent(validationExecutions, extendedGa4GhApi, workflowId, workflowVersionId, platform2);

        // Register and publish a tool
        io.dockstore.openapi.client.model.DockstoreTool tool = new io.dockstore.openapi.client.model.DockstoreTool();
        tool.setDefaultCwlPath("/cwls/cgpmap-bamOut.cwl");
        tool.setGitUrl("git@github.com:DockstoreTestUser2/dockstore-cgpmap.git");
        tool.setNamespace("dockstoretestuser2");
        tool.setName("dockstore-cgpmap");
        tool.setRegistryString(Registry.QUAY_IO.getDockerPath());
        tool.setDefaultVersion("symbolic.v1");
        tool.setDefaultCWLTestParameterFile("/examples/cgpmap/bamOut/bam_input.json");
        io.dockstore.openapi.client.model.DockstoreTool registeredTool = containersApi.registerManual(tool);
        registeredTool = containersApi.refresh(registeredTool.getId());
        containersApi.publish(registeredTool.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        final String toolId = "quay.io/dockstoretestuser2/dockstore-cgpmap";
        final String toolVersionId = "symbolic.v1";
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(runExecutions), platform1, toolId, toolVersionId, null);
        verifyMetricsDataList(toolId, toolVersionId, platform1, ownerUserId, "", 1);
        verifyRunExecutionMetricsDataContent(runExecutions, extendedGa4GhApi, toolId, toolVersionId, platform1);
        assertEquals(3, getS3ObjectsFromBucket(s3Client, bucketName).size(), "There should be 4 objects, 3 for workflows and 1 for tools");
    }

    @Test
    void testNumberOfFilesCreatedForMetricsSubmission() {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        final String platform1 = Partner.TERRA.name();
        final String description = "A single execution";

        // Register and publish a workflow
        final String workflowId = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv/my-workflow";
        final String workflowVersionId = "master";
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "my-workflow", "cwl",
                "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Add 1 workflow execution, 1 task execution set, 1 validation execution for a workflow version for one platform
        List<RunExecution> runExecutions = createRunExecutions(1);

        TaskExecutions taskExecutions = createTaskExecutions(2);

        ValidationExecution validationExecution = new ValidationExecution();
        validationExecution.setExecutionId(generateExecutionId());
        validationExecution.setIsValid(true);
        validationExecution.setValidatorTool(ValidationExecution.ValidatorToolEnum.MINIWDL);
        validationExecution.setValidatorToolVersion("v1.9.1");
        validationExecution.setDateExecuted(Instant.now().toString());

        ExecutionsRequestBody executionsRequestBody = new ExecutionsRequestBody()
                .runExecutions(runExecutions)
                .taskExecutions(List.of(taskExecutions))
                .validationExecutions(List.of(validationExecution));
        extendedGa4GhApi.executionMetricsPost(executionsRequestBody, platform1, workflowId, workflowVersionId, description);
        verifyMetricsDataList(workflowId, workflowVersionId, 1);
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

        // Test that the platform must be an actual platform and not ALL
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(goodExecutionsRequestBody, Partner.ALL.name(), id, versionId, description));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode(), "Should not be able to specify ALL as a platform");
        assertTrue(exception.getMessage().contains(INVALID_PLATFORM));

        // Test that the response body must contain ExecutionStatus for RunExecution
        List<RunExecution> runExecutions = createRunExecutions(1);
        runExecutions.forEach(execution -> execution.setExecutionStatus(null));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(runExecutions), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to submit metrics if ExecutionStatus is missing");
        assertTrue(exception.getMessage().contains("executionStatus") && exception.getMessage().contains("is missing"), "Should not be able to submit metrics if ExecutionStatus is missing");

        // Test that the response body must contain dateExecuted for RunExecution
        List<RunExecution> runExecutionsWithMissingDate = createRunExecutions(1);
        runExecutionsWithMissingDate.forEach(execution -> execution.setDateExecuted(null));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(runExecutionsWithMissingDate), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to submit metrics if dateExecuted is missing");
        assertTrue(exception.getMessage().contains("dateExecuted") && exception.getMessage().contains("is missing"), "Should not be able to submit metrics if dateExecuted is missing");

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

        // Test that negative values can't be submitted
        List<RunExecution> runExecutionsWithNegativeValues = createRunExecutions(1);
        // Negative cost value
        runExecutionsWithNegativeValues.forEach(execution -> execution.setCost(new Cost().value(-1.00)));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(runExecutionsWithNegativeValues), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to submit metrics with negative cost");
        assertTrue(exception.getMessage().contains("cost.value must be greater than or equal to 0"));
        // Negative CPU requirement
        runExecutionsWithNegativeValues.forEach(execution -> execution.setCpuRequirements(-1));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(runExecutionsWithNegativeValues), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to submit metrics with negative CPU");
        assertTrue(exception.getMessage().contains("cpuRequirements must be greater than or equal to 0"));
        // Negative and NaN memory requirement
        runExecutionsWithNegativeValues.forEach(execution -> execution.setMemoryRequirementsGB(-1.0));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(runExecutionsWithNegativeValues), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to submit metrics with negative memory");
        assertTrue(exception.getMessage().contains("memoryRequirementsGB must be greater than or equal to 0"));
        runExecutionsWithNegativeValues.forEach(execution -> execution.setMemoryRequirementsGB(Double.NaN));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(runExecutionsWithNegativeValues), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to submit metrics with NaN memory");
        assertTrue(exception.getMessage().contains("memoryRequirementsGB must be greater than or equal to 0"));

        // Test that the response body must contain the required fields for ValidationExecution
        List<ValidationExecution> validationExecutions = List.of(new ValidationExecution());
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().validationExecutions(validationExecutions), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to submit metrics if required fields for ValidationExecution are missing");
        assertTrue(exception.getMessage().contains("isValid") && exception.getMessage().contains("validatorTool") && exception.getMessage().contains("is missing"), "Should not be able to submit metrics if required fields for ValidationExecution are missing");

        // Test that malformed dateExecuteds for ValidationExecution throw an exception
        ValidationExecution validationExecution = new ValidationExecution();
        validationExecution.setIsValid(true);
        validationExecution.setValidatorTool(ValidationExecution.ValidatorToolEnum.MINIWDL);
        validationExecution.setDateExecuted("March 23, 2023");
        List<ValidationExecution> malformedDateExecuteds = List.of(validationExecution);
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().validationExecutions(malformedDateExecuteds), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to submit metrics if dateExecuted is malformed");
        assertTrue(exception.getMessage().contains(EXECUTION_DATE_FORMAT_ERROR));
        assertTrue(exception.getMessage().contains("March 23, 2023"), "Should not be able to submit metrics if dateExecuted is malformed");

        // Verify that not providing metrics data throws an exception
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody(), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should throw if execution metrics not provided");
        assertTrue(exception.getMessage().contains(MUST_CONTAIN_EXECUTIONS_OR_METRICS), "Should throw if execution metrics not provided");

        // Verify that the user cannot provide invalid execution IDs
        List<RunExecution> executionWithInvalidId = createRunExecutions(1);
        executionWithInvalidId.forEach(execution -> execution.setExecutionId("   "));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(executionWithInvalidId), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should throw if there the execution ID is empty");
        assertTrue(exception.getMessage().contains(INVALID_EXECUTION_ID_MESSAGE), "Should throw if there the execution ID is empty");
        // Invalid execution ID without alphanumerics
        executionWithInvalidId.forEach(execution -> execution.setExecutionId("!@#$%^&*"));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(executionWithInvalidId), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode());
        assertTrue(exception.getMessage().contains(INVALID_EXECUTION_ID_MESSAGE));

        // Verify that user cannot provide duplicate execution IDs in the same request body
        List<RunExecution> duplicateIdExecutions = createRunExecutions(2);
        duplicateIdExecutions.forEach(execution -> execution.setExecutionId("sameId"));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(duplicateIdExecutions), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should throw if there are duplicate execution IDs provided");
        assertTrue(exception.getMessage().contains(MUST_CONTAIN_UNIQUE_EXECUTION_IDS), "Should throw if there are duplicate execution IDs provided");

        // Verify that user can't submit a RunExecution with the status ALL (meant for internal use)
        List<RunExecution> executionWithAllStatus = createRunExecutions(1);
        executionWithAllStatus.forEach(execution -> execution.setExecutionStatus(ExecutionStatusEnum.ALL));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(executionWithAllStatus), platform, id, versionId, description));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should throw if the ALL status is used");
        assertTrue(exception.getMessage().contains(INVALID_EXECUTION_STATUS_MESSAGE), "Should throw if the ALL status is used");
    }

    @Test
    void testUpdateExecutionMetrics() {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final UsersApi usersApi = new UsersApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        final String platform1 = Partner.TERRA.name();
        final String description = "A single execution";
        final Long ownerUserId = usersApi.getUser().getId();

        // Register and publish a workflow
        final String workflowId = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv/my-workflow";
        final String workflowVersionId = "master";
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "my-workflow", "cwl",
                "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Add 10 workflow executions for a workflow version for one platform, one per endpoint call to generate 10 different files.
        // This is to ensure that the update endpoint can traverse multiple files to get the correct one to update
        for (int i = 0; i < 10; ++i) {
            extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(createRunExecutions(5)), platform1, workflowId, workflowVersionId, description);
        }
        verifyMetricsDataList(workflowId, workflowVersionId, platform1, ownerUserId, description, 10);

        // Add 1 workflow execution that we will update
        final String executionId = generateExecutionId();
        // A successful workflow execution
        RunExecution workflowExecution = new RunExecution();
        workflowExecution.setExecutionId(executionId);
        workflowExecution.dateExecuted(Instant.now().toString());
        workflowExecution.setExecutionStatus(ExecutionStatusEnum.SUCCESSFUL);
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(List.of(workflowExecution)), platform1, workflowId, workflowVersionId, description);
        verifyMetricsDataList(workflowId, workflowVersionId, platform1, ownerUserId, description, 11);
        verifyRunExecutionMetricsDataContent(List.of(workflowExecution), extendedGa4GhApi, workflowId, workflowVersionId, platform1);

        // Update the workflow execution so that it has execution time
        workflowExecution.setExecutionTime("PT5M"); // 5 mins
        workflowExecution.setExecutionStatus(ExecutionStatusEnum.FAILED_RUNTIME_INVALID); // Attempt to update the execution status. This should not be updated because it's not an optional field
        ExecutionsResponseBody responseBody = extendedGa4GhApi.executionMetricsUpdate(new ExecutionsRequestBody().runExecutions(List.of(workflowExecution)), platform1, workflowId, workflowVersionId, description);
        assertEquals(1, responseBody.getExecutionResponses().size());
        assertEquals(HttpStatus.SC_OK, responseBody.getExecutionResponses().get(0).getStatus());
        verifyMetricsDataList(workflowId, workflowVersionId, platform1, ownerUserId, description, 11); // There should still be 11 files
        ExecutionsRequestBody execution = extendedGa4GhApi.executionGet(workflowId, workflowVersionId, platform1, executionId);
        RunExecution updatedWorkflowExecutionFromS3 = execution.getRunExecutions().get(0);
        assertEquals("PT5M", updatedWorkflowExecutionFromS3.getExecutionTime(), "Execution time should've been updated");
        assertEquals(ExecutionStatusEnum.SUCCESSFUL, updatedWorkflowExecutionFromS3.getExecutionStatus(), "Execution status should not change");

        // Try to update an execution that doesn't exist
        List<RunExecution> nonExistentWorkflowExecution = createRunExecutions(1);
        responseBody = extendedGa4GhApi.executionMetricsUpdate(new ExecutionsRequestBody().runExecutions(nonExistentWorkflowExecution), platform1, workflowId, workflowVersionId, description);
        assertEquals(1, responseBody.getExecutionResponses().size());
        assertEquals(HttpStatus.SC_NOT_FOUND, responseBody.getExecutionResponses().get(0).getStatus());
        assertTrue(responseBody.getExecutionResponses().get(0).getError().contains(EXECUTION_NOT_FOUND_ERROR));
    }

    @Test
    void testAggregatedMetrics() {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        final WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final String platform1 = Partner.TERRA.name();
        final String platform2 = Partner.DNA_STACK.name();

        // Register and publish a workflow
        final String workflowId = String.format("#workflow/%s/my-workflow", DOCKSTORE_WORKFLOW_CNV_PATH);
        final String workflowVersionId = "master";
        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_WORKFLOW_CNV_REPO, "/workflow/cnv.cwl", "my-workflow", "cwl",
                "/test.json");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        ExecutionStatusMetric executionStatusMetric = new ExecutionStatusMetric()
                .count(Map.of(SUCCESSFUL.name(), new MetricsByStatus().executionStatusCount(1),
                        FAILED_SEMANTIC_INVALID.name(), new MetricsByStatus().executionStatusCount(1),
                        ABORTED.name(), new MetricsByStatus().executionStatusCount(2)));
        final double min = 1.0;
        final double max = 3.0;
        final double average = 2.0;
        final int numberOfDataPointsForAverage = 3;
        ExecutionTimeMetric executionTimeMetric = new ExecutionTimeMetric()
                .minimum(min)
                .maximum(max)
                .average(average)
                .numberOfDataPointsForAverage(numberOfDataPointsForAverage);
        executionStatusMetric.getCount().get(SUCCESSFUL.name()).setExecutionTime(executionTimeMetric);
        CpuMetric cpuMetric = new CpuMetric()
                .minimum(min)
                .maximum(max)
                .average(average)
                .numberOfDataPointsForAverage(numberOfDataPointsForAverage);
        executionStatusMetric.getCount().get(SUCCESSFUL.name()).setCpu(cpuMetric);
        MemoryMetric memoryMetric = new MemoryMetric()
                .minimum(min)
                .maximum(max)
                .average(average)
                .numberOfDataPointsForAverage(numberOfDataPointsForAverage);
        executionStatusMetric.getCount().get(SUCCESSFUL.name()).setMemory(memoryMetric);
        Metrics metrics = new Metrics()
                .executionStatusCount(executionStatusMetric);

        // Put run metrics for platform 1
        extendedGa4GhApi.aggregatedMetricsPut(metrics, platform1, workflowId, workflowVersionId);
        workflow = workflowsApi.getPublishedWorkflow(workflow.getId(), "metrics");
        WorkflowVersion workflowVersion = workflow.getWorkflowVersions().stream().filter(v -> workflowVersionId.equals(v.getName())).findFirst().orElse(null);
        assertNotNull(workflowVersion);
        assertEquals(1, workflowVersion.getMetricsByPlatform().size());

        Metrics platform1Metrics = workflowVersion.getMetricsByPlatform().get(platform1);
        assertNotNull(platform1Metrics);
        // Verify execution status
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(2, platform1Metrics.getExecutionStatusCount().getNumberOfAbortedExecutions());
        assertFalse(platform1Metrics.getExecutionStatusCount().getCount().containsKey(FAILED_RUNTIME_INVALID.name()), "Should not contain this because no executions had this status");
        // Verify execution time
        MetricsByStatus platform1SuccessfulMetrics = platform1Metrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name());
        assertEquals(min, platform1SuccessfulMetrics.getExecutionTime().getMinimum());
        assertEquals(max, platform1SuccessfulMetrics.getExecutionTime().getMaximum());
        assertEquals(average, platform1SuccessfulMetrics.getExecutionTime().getAverage());
        assertEquals(numberOfDataPointsForAverage, platform1SuccessfulMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(ExecutionTimeStatisticMetric.UNIT, platform1SuccessfulMetrics.getExecutionTime().getUnit());
        // Verify CPU
        assertEquals(min, platform1SuccessfulMetrics.getCpu().getMinimum());
        assertEquals(max, platform1SuccessfulMetrics.getCpu().getMaximum());
        assertEquals(average, platform1SuccessfulMetrics.getCpu().getAverage());
        assertEquals(numberOfDataPointsForAverage, platform1SuccessfulMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertNull(null, "CPU has no units");
        // Verify memory
        assertEquals(min, platform1SuccessfulMetrics.getMemory().getMinimum());
        assertEquals(max, platform1SuccessfulMetrics.getMemory().getMaximum());
        assertEquals(average, platform1SuccessfulMetrics.getMemory().getAverage());
        assertEquals(numberOfDataPointsForAverage, platform1SuccessfulMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(MemoryStatisticMetric.UNIT, platform1SuccessfulMetrics.getMemory().getUnit());

        // Put metrics for platform1 again to verify that the old metrics are deleted from the DB and there are no orphans
        extendedGa4GhApi.aggregatedMetricsPut(metrics, platform1, workflowId, workflowVersionId);
        long metricsDbCount = testingPostgres.runSelectStatement("select count(*) from metrics", long.class);
        assertEquals(1, metricsDbCount, "There should only be 1 row in the metrics table because we only have one entry version with aggregated metrics");

        ValidatorVersionInfo expectedMostRecentVersion = new ValidatorVersionInfo()
                .name("1.0")
                .isValid(true)
                .dateExecuted(Instant.now().toString())
                .numberOfRuns(1)
                .passingRate(100d);
        // Put validation metrics for platform2
        ValidationStatusMetric validationStatusMetric = new ValidationStatusMetric().validatorTools(Map.of(
                MINIWDL.toString(),
                new ValidatorInfo()
                        .mostRecentVersionName(expectedMostRecentVersion.getName())
                        .validatorVersions(List.of(expectedMostRecentVersion))
                        .passingRate(100d)
                        .numberOfRuns(1)));
        metrics = new Metrics().validationStatus(validationStatusMetric);
        extendedGa4GhApi.aggregatedMetricsPut(metrics, platform2, workflowId, workflowVersionId);
        workflow = workflowsApi.getPublishedWorkflow(workflow.getId(), "metrics");
        workflowVersion = workflow.getWorkflowVersions().stream().filter(v -> workflowVersionId.equals(v.getName())).findFirst().orElse(null);

        assertNotNull(workflowVersion);
        assertEquals(2, workflowVersion.getMetricsByPlatform().size(), "Version should have metrics for 2 platforms");

        assertNotNull(workflowVersion.getMetricsByPlatform().get(platform1));
        assertNotNull(workflowVersion.getMetricsByPlatform().get(platform2));

        // Verify validation status
        Metrics platform2Metrics = workflowVersion.getMetricsByPlatform().get(platform2);
        ValidatorInfo validatorInfo = platform2Metrics.getValidationStatus().getValidatorTools().get(MINIWDL.toString());
        assertNotNull(validatorInfo);
        assertEquals("1.0", validatorInfo.getMostRecentVersionName());
        Optional<ValidatorVersionInfo> mostRecentValidationVersion = validatorInfo.getValidatorVersions().stream().filter(validationVersion -> validatorInfo.getMostRecentVersionName().equals(validationVersion.getName())).findFirst();
        assertTrue(mostRecentValidationVersion.isPresent());
        assertTrue(mostRecentValidationVersion.get().isIsValid());
        assertEquals(100d, mostRecentValidationVersion.get().getPassingRate());
        assertEquals(1, mostRecentValidationVersion.get().getNumberOfRuns());
        assertEquals(100d, validatorInfo.getPassingRate());
        assertEquals(1, validatorInfo.getNumberOfRuns());
        platform1Metrics = workflowVersion.getMetricsByPlatform().get(platform1);

        // Verify that the endpoint can submit metrics that were aggregated across all platforms using Partner.ALL
        final String allPlatforms = Partner.ALL.name();
        extendedGa4GhApi.aggregatedMetricsPut(metrics, allPlatforms, workflowId, workflowVersionId);
        workflow = workflowsApi.getPublishedWorkflow(workflow.getId(), "metrics");
        workflowVersion = workflow.getWorkflowVersions().stream().filter(v -> workflowVersionId.equals(v.getName())).findFirst().orElse(null);
        assertNotNull(workflowVersion);
        Metrics allPlatformsMetrics = workflowVersion.getMetricsByPlatform().get(allPlatforms);

        Map<String, Metrics> metricsGet = extendedGa4GhApi.aggregatedMetricsGet(workflowId, workflowVersionId);
        assertNotNull(metricsGet.get(platform1));
        assertNotNull(metricsGet.get(platform2));
        assertNotNull(metricsGet.get(allPlatforms));

        assertEquals(platform1Metrics, metricsGet.get(platform1));
        assertEquals(platform2Metrics, metricsGet.get(platform2));
        assertEquals(allPlatformsMetrics, metricsGet.get(allPlatforms));
    }

    @Test
    void testPartnerPermissions() {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final UsersApi usersApi = new UsersApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        // Non-admin user
        final ApiClient otherWebClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final ExtendedGa4GhApi otherExtendedGa4GhApi = new ExtendedGa4GhApi(otherWebClient);
        final UsersApi otherUsersApi = new UsersApi(otherWebClient);

        String id = String.format("#workflow/%s", DOCKSTORE_WORKFLOW_CNV_PATH);
        String versionId = "master";
        String platform = Partner.TERRA.name();
        String differentPlatform = Partner.GALAXY.name();

        // setup and publish the workflow
        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_WORKFLOW_CNV_REPO, "/workflow/cnv.cwl", null, "cwl",
            "/test.json");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        ExecutionStatusMetric executionStatusMetric = new ExecutionStatusMetric().count(Map.of(SUCCESSFUL.name(), new MetricsByStatus().executionStatusCount(1)));
        Metrics metrics = new Metrics().executionStatusCount(executionStatusMetric);

        // Test that a non-admin/non-curator user can't put aggregated metrics
        ApiException exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.aggregatedMetricsPut(metrics, platform, id, versionId));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode(), "Non-admin and non-curator user should not be able to put aggregated metrics");

        // convert the user role and test that a platform partner can't put aggregated metrics (which adds metrics to the database)
        usersApi.setUserPrivileges(new PrivilegeRequest().platformPartner(PlatformPartnerEnum.TERRA), otherUsersApi.getUser().getId());
        exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.aggregatedMetricsPut(metrics, platform, id, versionId));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode(), "Platform partner should not be able to put aggregated metrics");

        // Test that a platform partner can post run executions for their platform
        List<RunExecution> executions = createRunExecutions(1);
        final String executionId = executions.get(0).getExecutionId();
        otherExtendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(executions), platform, id, versionId, "foo");
        verifyMetricsDataList(id, versionId, 1);
        // Test that a platform partner cannot post executions for a different platform
        exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(executions), differentPlatform, id, versionId, "foo"));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode(), "Platform partner should not be able to post executions for a different platform");
        assertEquals(FORBIDDEN_PLATFORM, exception.getMessage());

        // Test that a platform partner can get executions for their platform
        ExecutionsRequestBody executionsRequestBody = otherExtendedGa4GhApi.executionGet(id, versionId, platform, executionId);
        assertEquals(1, executionsRequestBody.getRunExecutions().size());
        assertEquals(executionId, executionsRequestBody.getRunExecutions().get(0).getExecutionId());
        // Test that a platform partner can't get executions for a different platform
        // Post metrics for a different platform
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(executions), differentPlatform, id, versionId, "foo");
        exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.executionGet(id, versionId, differentPlatform, executionId));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode(), "Platform partner should not be able to get executions for a different platform");
        assertEquals(FORBIDDEN_PLATFORM, exception.getMessage());

        // Test that a platform partner can update executions for their platform
        ExecutionsResponseBody responseBody = otherExtendedGa4GhApi.executionMetricsUpdate(new ExecutionsRequestBody().runExecutions(executions), platform, id, versionId, "");
        assertEquals(1, responseBody.getExecutionResponses().size());
        assertEquals(HttpStatus.SC_OK, responseBody.getExecutionResponses().get(0).getStatus());
        // Test that a platform partner can't update executions for a different platform
        exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.executionMetricsUpdate(new ExecutionsRequestBody().runExecutions(executions), differentPlatform, id, versionId, ""));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode(), "Platform partner should not be able to update executions for a different platform");
        assertEquals(FORBIDDEN_PLATFORM, exception.getMessage());
    }

    @Test
    void testAggregatedMetricsErrors() {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        // Non-admin user
        final ApiClient otherWebClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final ExtendedGa4GhApi otherExtendedGa4GhApi = new ExtendedGa4GhApi(otherWebClient);

        String id = String.format("#workflow/%s", DOCKSTORE_WORKFLOW_CNV_PATH);
        String versionId = "master";
        String platform = Partner.TERRA.name();

        ExecutionStatusMetric executionStatusMetric = new ExecutionStatusMetric().count(Map.of(SUCCESSFUL.name(), new MetricsByStatus().executionStatusCount(1)));
        Metrics metrics = new Metrics().executionStatusCount(executionStatusMetric);
        // Test malformed ID
        ApiException exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, "malformedId", "malformedVersionId"));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode());

        // Test ID that doesn't exist
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, "github.com/nonexistent/id", "master"));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode(), "Should not be able to submit metrics for non-existent id");
        assertTrue(exception.getMessage().contains(TOOL_NOT_FOUND_ERROR));

        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsGet("github.com/nonexistent/id", "master"));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode(), "Should not be able to get metrics for non-existent id");
        assertTrue(exception.getMessage().contains(TOOL_NOT_FOUND_ERROR));

        // Test version ID that doesn't exist
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_WORKFLOW_CNV_REPO, "/workflow/cnv.cwl", "",
                DescriptorLanguage.CWL.toString(), "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, id, "nonexistentVersionId"));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode(), "Should not be able to put aggregated metrics for non-existent version");
        assertTrue(exception.getMessage().contains(VERSION_NOT_FOUND_ERROR));

        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsGet(id, "nonexistentVersionId"));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode(), "Should not be able to get aggregated metrics for non-existent version");
        assertTrue(exception.getMessage().contains(VERSION_NOT_FOUND_ERROR));

        // Test that a non-admin/non-curator user can't put aggregated metrics
        exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.aggregatedMetricsPut(metrics, platform, id, versionId));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode(), "Non-admin and non-curator user should not be able to put aggregated metrics");

        Metrics emptyMetrics = new Metrics();
        // Test that the response body must contain ExecutionStatusCount or ValidationStatus
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(emptyMetrics, platform, id, versionId));
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, exception.getCode(), "Should not be able to put aggregated metrics if ExecutionStatusCount and ValidationStatus is missing");
        assertTrue(exception.getMessage().contains(MUST_CONTAIN_METRICS));

        // Verify that not providing metrics throws an exception
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(null, platform, id, versionId));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode(), "Should throw if execution metrics not provided");
    }

    /**
     * Tests that a Workflow Run RO-Crate file can be converted to a RunExecution object and get submitted.
     */
    @Test
    void testROCrateToRunExecution() throws IOException {
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        final String platform1 = Partner.TERRA.name();
        final String description = "A single execution";

        // Register and publish a workflow
        final String workflowId = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv/my-workflow";
        final String workflowVersionId = "master";

        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "my-workflow", "cwl",
                "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        //Retrieve Workflow run RO-crate json. Source for this json file: https://www.researchobject.org/workflow-run-crate/profiles/workflow_run_crate
        ClassLoader classLoader = getClass().getClassLoader();
        String path = classLoader.getResource("fixtures/sampleWorkflowROCrate.json").getPath();
        List<RunExecution> runExecutions = new ArrayList<>();
        runExecutions.add(convertROCrateToRunExecution(path));

        //post run execution metrics
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(runExecutions), platform1, workflowId, workflowVersionId, description);

        //retrieve the posted metrics
        verifyMetricsDataList(workflowId, workflowVersionId, 1);
        verifyRunExecutionMetricsDataContent(runExecutions, extendedGa4GhApi, workflowId, workflowVersionId, platform1);
    }

    @Test
    void testGetExecution() {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        // Anonymous user
        final ApiClient anonWebClient = getAnonymousOpenAPIWebClient();
        final ExtendedGa4GhApi anonExtendedGa4GhApi = new ExtendedGa4GhApi(anonWebClient);
        // Non-admin user
        final ApiClient nonAdminWebClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final ExtendedGa4GhApi nonAdminExtendedGa4GhApi = new ExtendedGa4GhApi(nonAdminWebClient);

        // Register and publish a workflow
        final String workflowId = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv/my-workflow";
        final String workflowVersionId = "master";
        final String platform = Partner.TERRA.name();
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "my-workflow", "cwl",
                "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        // Get an execution that doesn't exist
        ApiException exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.executionGet(workflowId, workflowVersionId, platform, "doesntexist"));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode());
        assertTrue(exception.getMessage().contains(EXECUTION_NOT_FOUND_ERROR));

        // Add 1 workflow execution for a workflow version for one platform
        final String executionId = generateExecutionId();
        // A successful workflow execution
        RunExecution workflowExecution = new RunExecution();
        workflowExecution.setExecutionId(executionId);
        workflowExecution.dateExecuted(Instant.now().toString());
        workflowExecution.setExecutionStatus(ExecutionStatusEnum.SUCCESSFUL);
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(List.of(workflowExecution)), platform, workflowId, workflowVersionId, "");

        // Get the execution
        ExecutionsRequestBody retrievedExecutionsRequestBody = extendedGa4GhApi.executionGet(workflowId, workflowVersionId, platform, executionId);
        assertEquals(1, getNumberOfExecutions(retrievedExecutionsRequestBody), "There should only be one execution in each file");
        assertEquals(1, retrievedExecutionsRequestBody.getRunExecutions().size());
        assertEquals(workflowExecution, retrievedExecutionsRequestBody.getRunExecutions().get(0));

        // Test that an anonymous user can't access the execution
        exception = assertThrows(ApiException.class, () -> anonExtendedGa4GhApi.executionGet(workflowId, workflowVersionId, platform, executionId));
        assertEquals(HttpStatus.SC_UNAUTHORIZED, exception.getCode());

        // Test than a non-admin can't access the execution
        exception = assertThrows(ApiException.class, () -> nonAdminExtendedGa4GhApi.executionGet(workflowId, workflowVersionId, platform, executionId));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode());
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
     * Verifies the MetricsData and MetricsDataMetadata for a platform.
     * @param id
     * @param versionId
     * @param platform
     * @param ownerId
     * @param description
     * @param expectedSize
     * @return
     */
    private List<MetricsData> verifyMetricsDataList(String id, String versionId, String platform, long ownerId, String description, int expectedSize) {
        List<MetricsData> metricsDataList = metricsDataClient.getMetricsData(id, versionId, Partner.valueOf(platform));
        assertEquals(expectedSize, metricsDataList.size());

        final String s3KeyWithDummyFileName = generateKey(id, versionId, platform, "tmpFileName");
        final String s3KeyPrefix = s3KeyWithDummyFileName.substring(0, s3KeyWithDummyFileName.lastIndexOf("/"));
        for (MetricsData metricsData: metricsDataList) {
            verifyMetricsDataInfo(metricsData, id, versionId, platform, s3KeyPrefix);
            verifyMetricsDataMetadata(metricsData, ownerId, description);
        }
        return metricsDataList;
    }

    private void verifyMetricsDataInfo(MetricsData metricsData, String id, String versionId, String platform, String s3KeyPrefix) {
        assertNotNull(metricsData);
        assertEquals(id, metricsData.toolId());
        assertEquals(versionId, metricsData.toolVersionName());
        assertEquals(platform, metricsData.platform());
        // The full file name and S3 key are not checked because it depends on the time the data was submitted which is unknown
        assertTrue(metricsData.fileName().endsWith(".json"));
        assertTrue(metricsData.s3Key().startsWith(s3KeyPrefix));
    }

    private void verifyMetricsDataMetadata(MetricsData metricsData, long ownerUserId, String description) {
        MetricsDataMetadata metricsDataMetadata = metricsDataClient.getMetricsDataMetadata(metricsData);
        assertEquals(ownerUserId, metricsDataMetadata.owner());
        assertEquals(description, metricsDataMetadata.description());
    }

    private void verifyRunExecutionMetricsDataContent(List<RunExecution> expectedWorkflowExecutions, ExtendedGa4GhApi extendedGa4GhApi, String trsId, String versionId, String platform) {
        expectedWorkflowExecutions.forEach(expectedWorkflowExecution -> {
            List<RunExecution> actualWorkflowExecutions = getExecution(extendedGa4GhApi, trsId, versionId, platform, expectedWorkflowExecution.getExecutionId()).getRunExecutions();
            assertEquals(1, actualWorkflowExecutions.size());
            assertTrue(expectedWorkflowExecutions.contains(actualWorkflowExecutions.get(0)));
        });
    }

    private int getNumberOfExecutions(ExecutionsRequestBody executionsRequestBody) {
        return executionsRequestBody.getRunExecutions().size() + executionsRequestBody.getTaskExecutions().size() + executionsRequestBody.getValidationExecutions().size();
    }

    private void verifyTaskExecutionMetricsDataContent(List<TaskExecutions> expectedTaskExecutions, ExtendedGa4GhApi extendedGa4GhApi, String trsId, String versionId, String platform) {
        expectedTaskExecutions.forEach(expectedTaskExecutionsSet -> {
            List<TaskExecutions> actualTaskExecution = getExecution(extendedGa4GhApi, trsId, versionId, platform, expectedTaskExecutionsSet.getExecutionId()).getTaskExecutions();
            assertEquals(1, actualTaskExecution.size());
            assertTrue(expectedTaskExecutions.contains(actualTaskExecution.get(0)));
        });
    }

    private ExecutionsRequestBody getExecution(ExtendedGa4GhApi extendedGa4GhApi, String trsId, String versionId, String platform, String executionId) {
        ExecutionsRequestBody executionsRequestBodyFromS3 = extendedGa4GhApi.executionGet(trsId, versionId, platform, executionId);
        assertEquals(1, getNumberOfExecutions(executionsRequestBodyFromS3), "Each file should only contain one execution");
        return executionsRequestBodyFromS3;
    }

    private void verifyValidationExecutionMetricsDataContent(List<ValidationExecution> expectedValidationExecutions, ExtendedGa4GhApi extendedGa4GhApi, String trsId, String versionId, String platform) {
        expectedValidationExecutions.forEach(expectedValidationExecution -> {
            List<ValidationExecution> actualValidationExecutions = getExecution(extendedGa4GhApi, trsId, versionId, platform, expectedValidationExecution.getExecutionId()).getValidationExecutions();
            assertEquals(1, actualValidationExecutions.size());
            assertTrue(expectedValidationExecutions.contains(actualValidationExecutions.get(0)));
        });
    }

    public static List<RunExecution> createRunExecutions(int numberOfExecutions) {
        List<RunExecution> executions = new ArrayList<>();
        for (int i = 0; i < numberOfExecutions; ++i) {
            // A successful execution that ran for 5 minutes, requires 2 CPUs and 2 GBs of memory
            RunExecution execution = new RunExecution();
            execution.setExecutionId(generateExecutionId()); // Set a random execution ID
            execution.setExecutionStatus(RunExecution.ExecutionStatusEnum.SUCCESSFUL);
            execution.setDateExecuted(Instant.now().toString());
            execution.setExecutionTime("PT5M");
            execution.setCpuRequirements(2);
            execution.setMemoryRequirementsGB(2.0);
            execution.setCost(new Cost().value(9.99));
            execution.setRegion("us-central1");
            Map<String, Object> additionalProperties = Map.of("schema.org:totalTime", "PT5M");
            execution.setAdditionalProperties(additionalProperties);
            executions.add(execution);
        }
        return executions;
    }

    public static TaskExecutions createTaskExecutions(int numberOfTasks) {
        TaskExecutions taskExecutions = new TaskExecutions();
        taskExecutions.setExecutionId(generateExecutionId());
        taskExecutions.setDateExecuted(Instant.now().toString());
        taskExecutions.setTaskExecutions(createRunExecutions(numberOfTasks));
        return taskExecutions;
    }

    public static String generateExecutionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Converts a Workflow Run RO-Crate JSON file located in path, roCratePath, to a RunExecution object. The entire
     * JSON file is added onto the additionalProperties field in RunExecution.
     * @param roCratePath
     * @return RunExecution object.
     */
    public RunExecution convertROCrateToRunExecution(String roCratePath) throws IOException {
        String roCrate = new String(Files.readAllBytes(Paths.get(roCratePath)));
        Map<String, Object> map = GSON.fromJson(roCrate, Map.class);
        RunExecution execution = new RunExecution();
        execution.setExecutionId(generateExecutionId());
        execution.setExecutionStatus(RunExecution.ExecutionStatusEnum.SUCCESSFUL);
        execution.setDateExecuted(Instant.now().toString());
        execution.setAdditionalProperties(map);
        return execution;
    }
}
