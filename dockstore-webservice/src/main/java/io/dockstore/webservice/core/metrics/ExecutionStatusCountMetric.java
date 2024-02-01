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

package io.dockstore.webservice.core.metrics;

import static io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric.ExecutionStatus.ABORTED;
import static io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric.ExecutionStatus.FAILED;
import static io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric.ExecutionStatus.FAILED_RUNTIME_INVALID;
import static io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric.ExecutionStatus.FAILED_SEMANTIC_INVALID;
import static io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric.ExecutionStatus.SUCCESSFUL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.MapKey;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import java.util.EnumMap;
import java.util.Map;

@Entity
@Table(name = "execution_status")
@ApiModel(value = "ExecutionStatusMetric", description = "Aggregated metrics about workflow execution statuses")
@Schema(name = "ExecutionStatusMetric", description = "Aggregated metrics about workflow execution statuses")
@SuppressWarnings("checkstyle:magicnumber")
public class ExecutionStatusCountMetric extends CountMetric<ExecutionStatusCountMetric.ExecutionStatus, MetricsByStatus> {

    @NotEmpty
    @MapKeyEnumerated(EnumType.STRING)
    @MapKey(name = "executionStatus")
    @ApiModelProperty(value = "A map containing the count for each key")
    @Schema(description = "A map containing the count for each key", requiredMode = RequiredMode.REQUIRED)
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinTable(name = "execution_status_count", joinColumns = @JoinColumn(name = "executionstatusid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "metricsbystatusid", referencedColumnName = "id", columnDefinition = "bigint"))
    private Map<ExecutionStatus, MetricsByStatus> count = new EnumMap<>(ExecutionStatus.class);

    @Column(nullable = false)
    @Schema(description = "Number of successful executions", accessMode = AccessMode.READ_ONLY, example = "5")
    private int numberOfSuccessfulExecutions;

    @Column(nullable = false)
    @Schema(description = "Number of failed executions. An execution may have failed because it was semantically or runtime invalid", accessMode = AccessMode.READ_ONLY, example = "2")
    private int numberOfFailedExecutions;

    @Column(nullable = false)
    @Schema(description = "Number of aborted executions. An execution is aborted if its execution is stopped after it has started", accessMode = AccessMode.READ_ONLY, example = "0")
    private int numberOfAbortedExecutions;

    public ExecutionStatusCountMetric() {
        putCount(SUCCESSFUL, 0);
        putCount(FAILED, 0);
        putCount(FAILED_RUNTIME_INVALID, 0);
        putCount(FAILED_SEMANTIC_INVALID, 0);
        putCount(ABORTED, 0);
        calculateNumberOfExecutions();
    }

    public ExecutionStatusCountMetric(Map<ExecutionStatus, MetricsByStatus> count) {
        this.count = count;
        calculateNumberOfExecutions();
    }

    @Override
    public Map<ExecutionStatus, MetricsByStatus> getCount() {
        return count;
    }

    public void calculateNumberOfExecutions() {
        this.numberOfSuccessfulExecutions = count.getOrDefault(SUCCESSFUL, new MetricsByStatus(SUCCESSFUL)).getExecutionStatusCount();
        this.numberOfFailedExecutions = count.getOrDefault(FAILED, new MetricsByStatus(FAILED)).getExecutionStatusCount() + count.getOrDefault(FAILED_SEMANTIC_INVALID, new MetricsByStatus(FAILED_SEMANTIC_INVALID)).getExecutionStatusCount() + count.getOrDefault(FAILED_RUNTIME_INVALID, new MetricsByStatus(FAILED_RUNTIME_INVALID)).getExecutionStatusCount();
        this.numberOfAbortedExecutions = count.getOrDefault(ABORTED, new MetricsByStatus(ABORTED)).getExecutionStatusCount();
    }

    public void setCount(Map<ExecutionStatus, MetricsByStatus> count) {
        this.count = count;
        calculateNumberOfExecutions();
    }

    public void putCount(MetricsByStatus metricsByStatus) {
        this.count.put(metricsByStatus.getExecutionStatus(), metricsByStatus);
    }

    public void putCount(ExecutionStatus executionStatus, int executionStatusCount) {
        putCount(new MetricsByStatus(executionStatus, executionStatusCount));
    }

    public MetricsByStatus getMetricsByStatus(ExecutionStatus executionStatus) {
        return this.count.get(executionStatus);
    }

    public int getNumberOfSuccessfulExecutions() {
        return numberOfSuccessfulExecutions;
    }

    public void setNumberOfSuccessfulExecutions(int numberOfSuccessfulExecutions) {
        this.numberOfSuccessfulExecutions = numberOfSuccessfulExecutions;
    }

    public int getNumberOfFailedExecutions() {
        return numberOfFailedExecutions;
    }

    public void setNumberOfFailedExecutions(int numberOfFailedExecutions) {
        this.numberOfFailedExecutions = numberOfFailedExecutions;
    }

    public int getNumberOfAbortedExecutions() {
        return numberOfAbortedExecutions;
    }

    public void setNumberOfAbortedExecutions(int numberOfAbortedExecutions) {
        this.numberOfAbortedExecutions = numberOfAbortedExecutions;
    }

    @JsonIgnore
    public int getNumberOfExecutions() {
        return numberOfSuccessfulExecutions + numberOfFailedExecutions + numberOfAbortedExecutions;
    }

    public enum ExecutionStatus {
        ALL, // Internal use only
        SUCCESSFUL,
        FAILED,
        FAILED_SEMANTIC_INVALID,
        FAILED_RUNTIME_INVALID,
        ABORTED
    }
}
