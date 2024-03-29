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

package io.dockstore.webservice.metrics;

import static io.dockstore.common.metrics.ExecutionStatus.ABORTED;
import static io.dockstore.common.metrics.ExecutionStatus.FAILED_RUNTIME_INVALID;
import static io.dockstore.common.metrics.ExecutionStatus.FAILED_SEMANTIC_INVALID;
import static io.dockstore.common.metrics.ExecutionStatus.SUCCESSFUL;
import static io.dockstore.common.metrics.ValidationExecution.ValidatorTool.MINIWDL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Partner;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.core.metrics.CostStatisticMetric;
import io.dockstore.webservice.core.metrics.CpuStatisticMetric;
import io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric;
import io.dockstore.webservice.core.metrics.ExecutionTimeStatisticMetric;
import io.dockstore.webservice.core.metrics.MemoryStatisticMetric;
import io.dockstore.webservice.core.metrics.Metrics;
import io.dockstore.webservice.core.metrics.ValidationStatusCountMetric;
import io.dockstore.webservice.core.metrics.ValidatorInfo;
import io.dockstore.webservice.core.metrics.ValidatorVersionInfo;
import io.dockstore.webservice.jdbi.MetricsDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsIT extends BaseIT {

    private MetricsDAO metricsDAO;
    private Session session;
    private WorkflowDAO workflowDAO;
    private WorkflowVersionDAO workflowVersionDAO;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        this.metricsDAO = new MetricsDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);

        // used to allow us to use DAOs outside the web service
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    /**
     * Demos the creation, update, and persistence of the Metrics entity
     */
    @Test
    void testDAOs() {
        final Transaction transaction = session.beginTransaction();

        Metrics terraMetrics = createMetrics();
        Metrics dnaStack = createMetrics();
        metricsDAO.create(terraMetrics);
        metricsDAO.create(dnaStack);

        // Create a workflow and workflow version so we can add metrics for a specific platform to the workflow version
        Workflow workflow = createWorkflow();
        workflowDAO.create(workflow);
        WorkflowVersion workflowVersion = createWorkflowVersion(workflow);
        workflowVersion.getMetricsByPlatform().put(Partner.TERRA, terraMetrics);
        workflowVersion.getMetricsByPlatform().put(Partner.DNA_STACK, dnaStack);
        workflowVersionDAO.create(workflowVersion);
        transaction.commit();

        // Check that the objects were persisted correctly
        Metrics foundMetrics = metricsDAO.findById(terraMetrics.getId());
        assertNotNull(foundMetrics);
        assertEquals(terraMetrics.getId(), foundMetrics.getId());

        foundMetrics = metricsDAO.findById(dnaStack.getId());
        assertNotNull(foundMetrics);
        assertEquals(dnaStack.getId(), foundMetrics.getId());

        WorkflowVersion foundWorkflowVersion = workflowVersionDAO.findById(workflowVersion.getId());
        assertNotNull(foundWorkflowVersion);
        assertEquals(workflowVersion.getId(), foundWorkflowVersion.getId());
        assertNotNull(foundWorkflowVersion.getMetricsByPlatform().get(Partner.TERRA));
        assertNotNull(foundWorkflowVersion.getMetricsByPlatform().get(Partner.DNA_STACK));

        session.close();
    }

    private Workflow createWorkflow() {
        Workflow workflow = new BioWorkflow();
        workflow.setSourceControl(SourceControl.GITHUB);
        workflow.setOrganization("dockstore");
        workflow.setRepository("dockstore-ui2");
        workflow.setWorkflowName("test");
        workflow.setDescriptorType(DescriptorLanguage.CWL);
        return workflow;
    }

    private WorkflowVersion createWorkflowVersion(Workflow parent) {
        WorkflowVersion workflowVersion = new WorkflowVersion();
        workflowVersion.setName("2.0.1");
        workflowVersion.setReference("2.0.1");
        workflowVersion.setWorkflowPath("github.com/foo/bar");
        workflowVersion.setParent(parent);
        return workflowVersion;
    }

    /**
     * Creates a Metrics object containing information about workflow executions
     * @return
     */
    private Metrics createMetrics() {
        Metrics metrics = new Metrics();

        ExecutionStatusCountMetric executionStatusCountMetric = new ExecutionStatusCountMetric();
        // Add 10 successful workflow runs
        executionStatusCountMetric.putCount(SUCCESSFUL, 10);
        metrics.setExecutionStatusCount(executionStatusCountMetric);
        assertEquals(10, metrics.getExecutionStatusCount().getNumberOfExecutions());
        assertEquals(10, metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(0, metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(0, metrics.getExecutionStatusCount().getNumberOfAbortedExecutions());
        // Add 1 failed workflow run that was runtime invalid
        executionStatusCountMetric.putCount(FAILED_RUNTIME_INVALID, 1);
        metrics.setExecutionStatusCount(executionStatusCountMetric);
        assertEquals(11, metrics.getExecutionStatusCount().getNumberOfExecutions());
        assertEquals(10, metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(1, metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(0, metrics.getExecutionStatusCount().getNumberOfAbortedExecutions());
        // Add 1 failed workflow run that was semantically invalid
        executionStatusCountMetric.putCount(FAILED_SEMANTIC_INVALID, 1);
        metrics.setExecutionStatusCount(executionStatusCountMetric);
        assertEquals(12, metrics.getExecutionStatusCount().getNumberOfExecutions());
        assertEquals(10, metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(2, metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(0, metrics.getExecutionStatusCount().getNumberOfAbortedExecutions());
        // Add 1 aborted workflow run
        executionStatusCountMetric.putCount(ABORTED, 1);
        metrics.setExecutionStatusCount(executionStatusCountMetric);
        assertEquals(13, metrics.getExecutionStatusCount().getNumberOfExecutions());
        assertEquals(10, metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(2, metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(1, metrics.getExecutionStatusCount().getNumberOfAbortedExecutions());

        // Add aggregated information about execution time for the workflow runs.
        // The minimum execution time was 1 minute, the maximum was 5 minutes, and the average was 3 minutes. 10 data points were used to calculate the average
        ExecutionTimeStatisticMetric executionTimeStatisticMetric = new ExecutionTimeStatisticMetric(60.0, 300.0, 180.12, 10);
        executionStatusCountMetric.getMetricsByStatus(SUCCESSFUL).setExecutionTime(executionTimeStatisticMetric);

        // Add aggregated information about the CPU used for the workflow runs.
        // The minimum CPU used was 1, the maximum was 4, and the average was 2. 10 data points were used to calculate the average
        CpuStatisticMetric cpuStatisticMetric = new CpuStatisticMetric(1.0, 4.0, 2.0, 10);
        executionStatusCountMetric.getMetricsByStatus(SUCCESSFUL).setCpu(cpuStatisticMetric);

        // Add aggregated information about the memory used for the workflow runs.
        // The minimum CPU used was 1GB, the maximum was 4GB, and the average was 2G. 10 data points were used to calculate the average
        MemoryStatisticMetric memoryStatisticMetric = new MemoryStatisticMetric(1.0, 4.0, 2.5, 10);
        executionStatusCountMetric.getMetricsByStatus(SUCCESSFUL).setMemory(memoryStatisticMetric);

        // Add aggregated information about the cost of the workflow run
        CostStatisticMetric costStatisticMetric = new CostStatisticMetric(1.00, 4.00, 2.50, 10);
        executionStatusCountMetric.getMetricsByStatus(SUCCESSFUL).setCost(costStatisticMetric);

        // Add aggregated information about validation
        // Add a successful miniwdl validation
        ValidatorVersionInfo miniwdlValidatorVersionInfo = new ValidatorVersionInfo();
        miniwdlValidatorVersionInfo.setName("1.0");
        miniwdlValidatorVersionInfo.setIsValid(true);
        miniwdlValidatorVersionInfo.setDateExecuted(Instant.now().toString());
        miniwdlValidatorVersionInfo.setNumberOfRuns(5);
        miniwdlValidatorVersionInfo.setPassingRate(100d);
        ValidatorInfo miniwdlValidatorInfo = new ValidatorInfo();
        miniwdlValidatorInfo.setValidatorVersions(Set.of(miniwdlValidatorVersionInfo));
        miniwdlValidatorInfo.setMostRecentVersionName(miniwdlValidatorVersionInfo.getName());
        miniwdlValidatorInfo.setNumberOfRuns(miniwdlValidatorVersionInfo.getNumberOfRuns());
        miniwdlValidatorInfo.setPassingRate(miniwdlValidatorVersionInfo.getPassingRate());
        ValidationStatusCountMetric validationStatusCountMetric = new ValidationStatusCountMetric(Map.of(MINIWDL, miniwdlValidatorInfo));
        metrics.setValidationStatus(validationStatusCountMetric);

        return metrics;
    }
}
