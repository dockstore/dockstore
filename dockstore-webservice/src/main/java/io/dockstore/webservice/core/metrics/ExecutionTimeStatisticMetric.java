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

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * Note that execution time is stored in ISO 8601 duration format like https://schema.org/totalTime
 */
@Entity
@Table(name = "execution_time_metric")
@ApiModel(value = "ExecutionTimeMetric", description = "This describes aggregated execution time metrics in ISO 8601 for workflow executions.")
@Schema(name = "ExecutionTimeMetric", description = "This describes aggregated execution time metrics in ISO 8601 for workflow executions.")
public class ExecutionTimeStatisticMetric extends StatisticMetric {
    public ExecutionTimeStatisticMetric() {
    }

    public ExecutionTimeStatisticMetric(String minimum, String maximum, String average, int numberOfDataPointsForAverage) {
        super(minimum, maximum, average, numberOfDataPointsForAverage);
    }
}