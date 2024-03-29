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
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cpu_metric")
@ApiModel(value = "CpuMetric", description = "This describes aggregated CPU metrics for workflow executions.")
@Schema(name = "CpuMetric", description = "This describes aggregated CPU metrics for workflow executions.", allOf = Metric.class)
public class CpuStatisticMetric extends StatisticMetric {
    public CpuStatisticMetric() {
    }

    public CpuStatisticMetric(double minimum, double maximum, double average, int numberOfDataPointsForAverage) {
        super(minimum, maximum, average, numberOfDataPointsForAverage);
    }
}
