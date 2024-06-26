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

package io.dockstore.common.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.common.metrics.constraints.ISO8601ExecutionTime;
import io.dockstore.common.metrics.constraints.ValidClientExecutionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * This is an object to encapsulate workflow run execution metrics data in an entity. Does not need to be stored in the database.
 */
@Schema(name = "RunExecution", description = "Metrics of an execution on a platform", allOf = Execution.class)
public class RunExecution extends Execution {

    @NotNull
    @ValidClientExecutionStatus
    @JsonProperty(required = true)
    @Schema(description = "The status of the execution", requiredMode = RequiredMode.REQUIRED, example = "SUCCESSFUL")
    private ExecutionStatus executionStatus;

    @ISO8601ExecutionTime
    @JsonProperty
    @Schema(description = "The total time it took for the execution to complete in ISO 8601 duration format", example = "PT30S")
    private String executionTime;

    @JsonProperty
    @PositiveOrZero
    @Schema(description = "In seconds, automatically calculated from executionTime and dateExecuted", example = "30", accessMode = Schema.AccessMode.READ_ONLY)
    private Long executionTimeSeconds;

    @JsonProperty
    @PositiveOrZero
    @Schema(description = "Memory requirements for the execution in GB", example = "2")
    private Double memoryRequirementsGB;

    @PositiveOrZero
    @JsonProperty
    @Schema(description = "Number of CPUs required for the execution", example = "2")
    private Integer cpuRequirements;

    @Valid
    @JsonProperty
    @Schema(description = "The cost of the execution in USD")
    private Cost cost;

    /**
     * Recording the region because cloud services may cost different amounts in different regions.
     * For now, the region is only recorded and is not used when aggregating the cost metric.
     */
    @JsonProperty
    @Schema(description = "The region the workflow was executed in", example = "us-central1")
    private String region;

    public RunExecution() {
    }

    public RunExecution(ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }

    public String getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(String executionTime) {
        this.executionTime = executionTime;
        // make life easier on AWS athena, also store duration in seconds
        try {
            this.executionTimeSeconds = Duration.parse(executionTime).getSeconds();
        } catch (DateTimeParseException e) {
            // ignore, expecting this to be caught by `ISO8601ExecutionTime` anyway
        }
    }

    public Double getMemoryRequirementsGB() {
        return memoryRequirementsGB;
    }

    public void setMemoryRequirementsGB(Double memoryRequirementsGB) {
        this.memoryRequirementsGB = memoryRequirementsGB;
    }

    public Integer getCpuRequirements() {
        return cpuRequirements;
    }

    public void setCpuRequirements(Integer cpuRequirements) {
        this.cpuRequirements = cpuRequirements;
    }

    public Cost getCost() {
        return cost;
    }

    public void setCost(Cost cost) {
        this.cost = cost;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void update(RunExecution newRunExecution) {
        // Can only update fields that are optional
        super.update(newRunExecution);
        this.setExecutionTime(newRunExecution.executionTime);
        this.memoryRequirementsGB = newRunExecution.memoryRequirementsGB;
        this.cpuRequirements = newRunExecution.cpuRequirements;
        this.cost = newRunExecution.cost;
        this.region = newRunExecution.region;
    }

    public Long getExecutionTimeSeconds() {
        return executionTimeSeconds;
    }
}
