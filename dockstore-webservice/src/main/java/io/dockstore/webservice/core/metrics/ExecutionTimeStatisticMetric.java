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
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * Note that execution time is stored in seconds
 */
@Entity
@Table(name = "execution_time_metric")
@ApiModel(value = "ExecutionTimeMetric", description = "This describes aggregated execution time metrics in seconds for workflow executions.")
@Schema(name = "ExecutionTimeMetric", description = "This describes aggregated execution time metrics in seconds for workflow executions.", allOf = Metric.class)
public class ExecutionTimeStatisticMetric extends StatisticMetric {
    public static final String UNIT = "s"; // Store in seconds


    //TODO: could consider moving these down the hierarchy for cost, cpu, memory if those become well-populated
    //TODO: could also consider storing these as an array/record
    @Column(nullable = false)
    @NotNull
    @ApiModelProperty(value = "The 50th percentile value from the data points", required = true)
    @Schema(description = "The 50th percentile value from the data points", requiredMode = RequiredMode.REQUIRED)
    private double median;

    @Column(nullable = false)
    @NotNull
    @ApiModelProperty(value = "The 05th percentile value from the data points", required = true)
    @Schema(description = "The 05th percentile value from the data points", requiredMode = RequiredMode.REQUIRED)
    private double percentile05th;

    @Column(nullable = false)
    @NotNull
    @ApiModelProperty(value = "The 95th percentile value from the data points", required = true)
    @Schema(description = "The 95th percentile value from the data points", requiredMode = RequiredMode.REQUIRED)
    private double percentile95th;

    public ExecutionTimeStatisticMetric() {
    }

    @JsonCreator
    public ExecutionTimeStatisticMetric(
            @JsonProperty("minimum") double minimum,
            @JsonProperty("maximum") double maximum,
            @JsonProperty("average") double average,
            @JsonProperty("numberOfDataPointsForAverage") int numberOfDataPointsForAverage) {
        super(minimum, maximum, average, numberOfDataPointsForAverage, UNIT);
    }

    @JsonCreator
    public ExecutionTimeStatisticMetric(
        @JsonProperty("minimum") double minimum,
        @JsonProperty("maximum") double maximum,
        @JsonProperty("average") double average,
        @JsonProperty("average") double median,
        @JsonProperty("average") double percentile5th,
        @JsonProperty("average") double percentile95th,
        @JsonProperty("numberOfDataPointsForAverage") int numberOfDataPointsForAverage) {
        super(minimum, maximum, average, numberOfDataPointsForAverage, UNIT);
        this.setMedian(median);
        this.setPercentile05th(percentile5th);
        this.setPercentile95th(percentile95th);
    }

    @Override
    @Schema(description = "The unit of the data points", defaultValue = UNIT) // Override schema to provide a default value
    public String getUnit() {
        return super.getUnit();
    }

    public double getMedian() {
        return median;
    }

    public void setMedian(double median) {
        this.median = median;
    }

    public double getPercentile05th() {
        return percentile05th;
    }

    public void setPercentile05th(double percentile5th) {
        this.percentile05th = percentile5th;
    }

    public double getPercentile95th() {
        return percentile95th;
    }

    public void setPercentile95th(double percentile95th) {
        this.percentile95th = percentile95th;
    }
}
