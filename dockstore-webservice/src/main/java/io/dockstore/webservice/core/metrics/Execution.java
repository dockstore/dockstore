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
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * This is an object to encapsulate execution metrics data in an entity. Does not need to be stored in the database.
 */
@Schema(name = "Execution", description = "Metrics of a workflow execution on a platform")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Execution {

    @JsonProperty(required = true)
    @Schema(description = "The status of the execution", required = true)
    private ExecutionStatus executionStatus;

    @JsonProperty
    @Schema(description = "The total time it took for the execution to complete in ISO 8601 duration format", example = "PT30S")
    private String executionTime;

    @JsonProperty
    @Schema(description = "Memory requirements for the execution in GB", example = "2")
    private Double memoryRequirementsGB;

    @JsonProperty
    @Schema(description = "Number of CPUs required for the execution", example = "2")
    private Integer cpuRequirements;

    @JsonProperty
    @Schema(description = "Additional properties that aren't defined. Provide a context, like one from schema.org, if you want to use a specific vocabulary",
            example = """
            {
              "@context": {
                "schema": "https://schema.org"
              },
              "schema:actionStatus": "CompletedActionStatus"
            }
            """)
    private Map<String, Object> additionalProperties;

    public Execution() {
    }

    public Execution(ExecutionStatus executionStatus) {
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

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}
