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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

@Deprecated(since = "1.15.0")
@Schema(name = "AggregatedExecution", description = "Aggregated metrics of multiple executions on a platform", allOf = Metrics.class, deprecated = true)
public class AggregatedExecution extends Metrics {

    @NotEmpty
    @JsonProperty(required = true)
    @Schema(description = "User-provided ID of the execution. This ID is used to identify the execution when updating the execution", requiredMode = RequiredMode.REQUIRED)
    private String executionId;

    @Deprecated(since = "1.15.0")
    @JsonProperty
    @ApiModelProperty(value = "Additional aggregated metrics")
    @Schema(description = "Additional aggregated metrics", deprecated = true)
    private Map<String, Object> additionalAggregatedMetrics;

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    @Deprecated(since = "1.15.0")
    public Map<String, Object> getAdditionalAggregatedMetrics() {
        return additionalAggregatedMetrics;
    }

    @Deprecated(since = "1.15.0")
    @JsonProperty
    public void setAdditionalAggregatedMetrics(Map<String, Object> additionalAggregatedMetrics) {
        this.additionalAggregatedMetrics = additionalAggregatedMetrics;
    }
}
