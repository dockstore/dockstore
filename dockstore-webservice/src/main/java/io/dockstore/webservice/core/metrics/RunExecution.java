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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.webservice.core.metrics.ExecutionStatusCountMetric.ExecutionStatus;
import io.dockstore.webservice.core.metrics.constraints.ISO8601ExecutionTime;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an object to encapsulate workflow run execution metrics data in an entity. Does not need to be stored in the database.
 */
@Schema(name = "RunExecution", description = "Metrics of a workflow execution on a platform", allOf = Execution.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RunExecution extends Execution {
    private static final Logger LOG = LoggerFactory.getLogger(RunExecution.class);

    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "The status of the execution", requiredMode = RequiredMode.REQUIRED)
    private ExecutionStatus executionStatus;

    @ISO8601ExecutionTime
    @JsonProperty
    @Schema(description = "The total time it took for the execution to complete in ISO 8601 duration format", example = "PT30S")
    private String executionTime;

    @JsonProperty
    @Schema(description = "Memory requirements for the execution in GB", example = "2")
    private Double memoryRequirementsGB;

    @JsonProperty
    @Schema(description = "Number of CPUs required for the execution", example = "2")
    private Integer cpuRequirements;

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

    /**
     * Check that the execution time is in ISO-8601 format by parsing it into a Duration.
     * @param executionTime ISO 8601 execution time
     * @return Duration parsed from the ISO 8601 execution time
     */
    public static Optional<Duration> checkExecutionTimeISO8601Format(String executionTime) {
        try {
            return Optional.of(Duration.parse(executionTime));
        } catch (DateTimeParseException e) {
            LOG.warn("Execution time {} is not in ISO 8601 format and could not be parsed to a Duration", executionTime, e);
            return Optional.empty();
        }
    }
}
