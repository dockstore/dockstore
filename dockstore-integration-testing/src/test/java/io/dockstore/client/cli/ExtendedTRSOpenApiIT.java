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

import static io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric.ExecutionStatus.FAILED_RUNTIME_INVALID;
import static io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric.ExecutionStatus.FAILED_SEMANTIC_INVALID;
import static io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric.ExecutionStatus.SUCCESSFUL;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.EXECUTION_STATUS_COUNT_ERROR;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.TOOL_NOT_FOUND_ERROR;
import static io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl.VERSION_NOT_FOUND_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.core.Partner;
import io.dockstore.webservice.core.metrics.ExecutionTimeStatisticMetric;
import io.dockstore.webservice.core.metrics.MemoryStatisticMetric;
import io.dockstore.webservice.metrics.MetricsDataS3ClientIT;
import io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Extra confidential integration tests, focuses on proposed GA4GH extensions
 * {@link BaseIT}
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(BaseIT.TestStatus.class)
@Tag(ConfidentialTest.NAME)
class ExtendedTRSOpenApiIT extends BaseIT {

    private static final String DOCKSTORE_WORKFLOW_CNV_REPO = "DockstoreTestUser2/dockstore_workflow_cnv";
    private static final String DOCKSTORE_WORKFLOW_CNV_PATH = SourceControl.GITHUB + "/" + DOCKSTORE_WORKFLOW_CNV_REPO;

    @SystemStub
    public final SystemOut systemOut = new SystemOut();

    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    void testAggregatedMetricsPut() {
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
                .count(Map.of(SUCCESSFUL.name(), 1,
                        FAILED_SEMANTIC_INVALID.name(), 1));
        final double min = 1.0;
        final double max = 3.0;
        final double average = 2.0;
        final int numberOfDataPointsForAverage = 3;
        ExecutionTimeMetric executionTimeMetric = new ExecutionTimeMetric()
                .minimum(min)
                .maximum(max)
                .average(average)
                .numberOfDataPointsForAverage(numberOfDataPointsForAverage);
        CpuMetric cpuMetric = new CpuMetric()
                .minimum(min)
                .maximum(max)
                .average(average)
                .numberOfDataPointsForAverage(numberOfDataPointsForAverage);
        MemoryMetric memoryMetric = new MemoryMetric()
                .minimum(min)
                .maximum(max)
                .average(average)
                .numberOfDataPointsForAverage(numberOfDataPointsForAverage);
        Metrics metrics = new Metrics()
                .executionStatusCount(executionStatusMetric)
                .executionTime(executionTimeMetric)
                .cpu(cpuMetric)
                .memory(memoryMetric);

        // Put metrics for platform 1
        extendedGa4GhApi.aggregatedMetricsPut(metrics, platform1, workflowId, workflowVersionId);
        workflow = workflowsApi.getPublishedWorkflow(workflow.getId(), "metrics");
        WorkflowVersion workflowVersion = workflow.getWorkflowVersions().stream().filter(v -> workflowVersionId.equals(v.getName())).findFirst().orElse(null);
        assertNotNull(workflowVersion);
        assertEquals(1, workflowVersion.getMetricsByPlatform().size());

        Metrics platform1Metrics = workflowVersion.getMetricsByPlatform().get(platform1);
        assertNotNull(platform1Metrics);
        // Verify execution status
        assertFalse(platform1Metrics.getExecutionStatusCount().isValid());
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertFalse(platform1Metrics.getExecutionStatusCount().getCount().containsKey(FAILED_RUNTIME_INVALID.name()), "Should not contain this because no executions had this status");
        // Verify execution time
        assertEquals(min, platform1Metrics.getExecutionTime().getMinimum());
        assertEquals(max, platform1Metrics.getExecutionTime().getMaximum());
        assertEquals(average, platform1Metrics.getExecutionTime().getAverage());
        assertEquals(numberOfDataPointsForAverage, platform1Metrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(ExecutionTimeStatisticMetric.UNIT, platform1Metrics.getExecutionTime().getUnit());
        // Verify CPU
        assertEquals(min, platform1Metrics.getCpu().getMinimum());
        assertEquals(max, platform1Metrics.getCpu().getMaximum());
        assertEquals(average, platform1Metrics.getCpu().getAverage());
        assertEquals(numberOfDataPointsForAverage, platform1Metrics.getCpu().getNumberOfDataPointsForAverage());
        assertNull(null, "CPU has no units");
        // Verify memory
        assertEquals(min, platform1Metrics.getMemory().getMinimum());
        assertEquals(max, platform1Metrics.getMemory().getMaximum());
        assertEquals(average, platform1Metrics.getMemory().getAverage());
        assertEquals(numberOfDataPointsForAverage, platform1Metrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(MemoryStatisticMetric.UNIT, platform1Metrics.getMemory().getUnit());

        // Put metrics for platform1 again to verify that the old metrics are deleted from the DB and there are no orphans
        extendedGa4GhApi.aggregatedMetricsPut(metrics, platform1, workflowId, workflowVersionId);
        long metricsDbCount = testingPostgres.runSelectStatement("select count(*) from metrics", long.class);
        assertEquals(1, metricsDbCount, "There should only be 1 row in the metrics table because we only have one entry version with aggregated metrics");

        // Put metrics for platform2
        extendedGa4GhApi.aggregatedMetricsPut(metrics, platform2, workflowId, workflowVersionId);
        workflow = workflowsApi.getPublishedWorkflow(workflow.getId(), "metrics");
        workflowVersion = workflow.getWorkflowVersions().stream().filter(v -> workflowVersionId.equals(v.getName())).findFirst().orElse(null);
        assertNotNull(workflowVersion);
        assertEquals(2, workflowVersion.getMetricsByPlatform().size(), "Version should have metrics for 2 platforms");

        assertNotNull(workflowVersion.getMetricsByPlatform().get(platform1));
        assertNotNull(workflowVersion.getMetricsByPlatform().get(platform2));
    }

    @Test
    void testPartnerPermissions() {
        // Admin user
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        // Non-admin user
        final ApiClient otherWebClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final ExtendedGa4GhApi otherExtendedGa4GhApi = new ExtendedGa4GhApi(otherWebClient);

        String id = String.format("#workflow/%s", DOCKSTORE_WORKFLOW_CNV_PATH);
        String versionId = "master";
        String platform = Partner.TERRA.name();

        // setup and publish the workflow
        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_WORKFLOW_CNV_REPO, "/workflow/cnv.cwl", null, "cwl",
            "/test.json");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        ExecutionStatusMetric executionStatusMetric = new ExecutionStatusMetric().count(Map.of(SUCCESSFUL.name(), 1));
        Metrics metrics = new Metrics().executionStatusCount(executionStatusMetric);

        // Test that a non-admin/non-curator user can't put aggregated metrics
        ApiException exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.aggregatedMetricsPut(metrics, platform, id, versionId));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode(), "Non-admin and non-curator user should not be able to put aggregated metrics");

        // convert the user role and test that a platform partner can put aggregated metrics
        testingPostgres.runUpdateStatement("update enduser set platformpartner = 't' where username = '" + OTHER_USERNAME + "'");
        otherExtendedGa4GhApi.aggregatedMetricsPut(metrics, platform, id, versionId);

        // Add execution metrics for a workflow version for one platform
        List<RunExecution> executions = MetricsDataS3ClientIT.createRunExecutions(1);
        ApiException exception2 = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(executions), platform, id, versionId, "foo"));
        // we were denied because S3 is not up and running in this class, not because of permissions issues
        assertTrue(exception2.getMessage().contains(ToolsApiExtendedServiceImpl.COULD_NOT_SUBMIT_METRICS_DATA) && exception2.getCode() == HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testAggregatedMetricsPutErrors() {
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

        ExecutionStatusMetric executionStatusMetric = new ExecutionStatusMetric().count(Map.of(SUCCESSFUL.name(), 1));
        Metrics metrics = new Metrics().executionStatusCount(executionStatusMetric);
        // Test malformed ID
        ApiException exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, "malformedId", "malformedVersionId"));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode());

        // Test ID that doesn't exist
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, "github.com/nonexistent/id", "master"));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode(), "Should not be able to submit metrics for non-existent id");
        assertTrue(exception.getMessage().contains(TOOL_NOT_FOUND_ERROR));

        // Test version ID that doesn't exist
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_WORKFLOW_CNV_REPO, "/workflow/cnv.cwl", "",
                DescriptorLanguage.CWL.toString(), "/test.json");
        workflow = workflowApi.refresh1(workflow.getId(), false);
        workflowApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(metrics, platform, id, "nonexistentVersionId"));
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getCode(), "Should not be able to put aggregated metrics for non-existent version");
        assertTrue(exception.getMessage().contains(VERSION_NOT_FOUND_ERROR));

        // Test that a non-admin/non-curator user can't put aggregated metrics
        exception = assertThrows(ApiException.class, () -> otherExtendedGa4GhApi.aggregatedMetricsPut(metrics, platform, id, versionId));
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getCode(), "Non-admin and non-curator user should not be able to put aggregated metrics");

        Metrics emptyMetrics = new Metrics();
        // Test that the response body must contain ExecutionStatusCount
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(emptyMetrics, platform, id, versionId));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode(), "Should not be able to put aggregated metrics if ExecutionStatusCount is missing");
        assertTrue(exception.getMessage().contains(EXECUTION_STATUS_COUNT_ERROR));

        // Verify that not providing metrics throws an exception
        exception = assertThrows(ApiException.class, () -> extendedGa4GhApi.aggregatedMetricsPut(null, platform, id, versionId));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode(), "Should throw if execution metrics not provided");
    }
}
