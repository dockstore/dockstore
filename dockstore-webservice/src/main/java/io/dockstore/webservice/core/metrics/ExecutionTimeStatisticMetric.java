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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Note that execution time is stored in seconds
 */
@Entity
@Table(name = "execution_time_metric")
@ApiModel(value = "ExecutionTimeMetric", description = "This describes aggregated execution time metrics in seconds for workflow executions.")
@Schema(name = "ExecutionTimeMetric", description = "This describes aggregated execution time metrics in seconds for workflow executions.")
public class ExecutionTimeStatisticMetric extends StatisticMetric {
    public static final String UNIT = "s"; // Store in seconds

    public ExecutionTimeStatisticMetric() {
    }

    @JsonCreator
    public ExecutionTimeStatisticMetric(
            @JsonProperty("minimum") Double minimum,
            @JsonProperty("maximum") Double maximum,
            @JsonProperty("average") Double average,
            @JsonProperty("numberOfDataPointsForAverage") int numberOfDataPointsForAverage) {
        super(minimum, maximum, average, numberOfDataPointsForAverage, UNIT);
    }

    @Override
    @Schema(description = "The unit of the data points", defaultValue = UNIT) // Override schema to provide a default value
    public String getUnit() {
        return super.getUnit();
    }
}
