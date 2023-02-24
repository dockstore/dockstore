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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.core.Partner;
import io.dockstore.webservice.core.metrics.ExecutionTimeStatisticMetric;
import io.dockstore.webservice.core.metrics.MemoryStatisticMetric;
import java.util.Map;
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
public class ExtendedTRSOpenApiIT extends BaseIT {

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
        final String workflowId = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv/my-workflow";
        final String workflowVersionId = "master";
        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "my-workflow", "cwl",
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

        // Put metrics for platform2
        extendedGa4GhApi.aggregatedMetricsPut(metrics, platform2, workflowId, workflowVersionId);
        workflow = workflowsApi.getPublishedWorkflow(workflow.getId(), "metrics");
        workflowVersion = workflow.getWorkflowVersions().stream().filter(v -> workflowVersionId.equals(v.getName())).findFirst().orElse(null);
        assertNotNull(workflowVersion);
        assertEquals(2, workflowVersion.getMetricsByPlatform().size(), "Version should have metrics for 2 platforms");

        assertNotNull(workflowVersion.getMetricsByPlatform().get(platform1));
        assertNotNull(workflowVersion.getMetricsByPlatform().get(platform2));
    }
}
