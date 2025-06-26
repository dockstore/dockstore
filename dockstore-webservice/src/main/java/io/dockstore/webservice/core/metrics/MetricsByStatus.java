/*
 * Copyright 2024 OICR and UCSC
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

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "metrics_by_status")
@Schema(name = "MetricsByStatus", description = "Aggregated metrics grouped by execution status")
public class MetricsByStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Implementation specific ID for the metrics in this webservice")
    private long id;

    @Column(nullable = false)
    @NotNull
    @Schema(description = "The number of executions for the status", requiredMode = RequiredMode.REQUIRED)
    private int executionStatusCount;

    @Valid
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "executiontimeid", referencedColumnName = "id")
    @Schema(description = "Aggregated execution time metrics in seconds for the status")
    private ExecutionTimeStatisticMetric executionTime;

    @Valid
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "memoryid", referencedColumnName = "id")
    @Schema(description = "Aggregated memory metrics in GB for the status")
    private MemoryStatisticMetric memory;

    @Valid
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "cpuid", referencedColumnName = "id")
    @Schema(description = "Aggregated CPU metrics for the status")
    private CpuStatisticMetric cpu;

    @Valid
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "costid", referencedColumnName = "id")
    @Schema(description = "Aggregated cost metrics in USD for the status")
    private CostStatisticMetric cost;

    @Valid
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "dailyruncounttimeseriesid", referencedColumnName = "id")
    @Schema(description = "daily run count time series for the status")
    private TimeSeriesMetric dailyRunCountTimeSeries;

    public MetricsByStatus() {

    }

    public MetricsByStatus(int executionStatusCount) {
        this.executionStatusCount = executionStatusCount;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getExecutionStatusCount() {
        return executionStatusCount;
    }

    public void setExecutionStatusCount(int executionStatusCount) {
        this.executionStatusCount = executionStatusCount;
    }

    public ExecutionTimeStatisticMetric getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(ExecutionTimeStatisticMetric executionTime) {
        this.executionTime = executionTime;
    }

    public MemoryStatisticMetric getMemory() {
        return memory;
    }

    public void setMemory(MemoryStatisticMetric memory) {
        this.memory = memory;
    }

    public CpuStatisticMetric getCpu() {
        return cpu;
    }

    public void setCpu(CpuStatisticMetric cpu) {
        this.cpu = cpu;
    }

    public CostStatisticMetric getCost() {
        return cost;
    }

    public void setCost(CostStatisticMetric cost) {
        this.cost = cost;
    }

    public TimeSeriesMetric getDailyRunCountTimeSeries() {
        return dailyRunCountTimeSeries;
    }

    public void setDailyRunCountTimeSeries(TimeSeriesMetric dailyRunCountTimeSeries) {
        this.dailyRunCountTimeSeries = dailyRunCountTimeSeries;
    }
}
