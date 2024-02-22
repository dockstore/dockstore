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

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.SequenceGenerator;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@Schema(name = "Metric", description = "Describes an aggregated metric", subTypes = { ExecutionTimeStatisticMetric.class, CpuStatisticMetric.class, MemoryStatisticMetric.class, CostStatisticMetric.class, ExecutionStatusCountMetric.class, ValidationStatusCountMetric.class })
public abstract class Metric {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "metric_id_seq")
    @SequenceGenerator(name = "metric_id_seq", sequenceName = "metric_id_seq", allocationSize = 1)
    @ApiModelProperty(value = "Implementation specific ID for metrics in this webservice")
    @Schema(description = "Implementation specific ID for metrics in this webservice")
    private long id;

    @Column
    @Schema(description = "The number of executions that were skipped during aggregation because they were invalid", defaultValue = "0")
    int numberOfSkippedExecutions = 0;

    protected Metric() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getNumberOfSkippedExecutions() {
        return numberOfSkippedExecutions;
    }

    public void setNumberOfSkippedExecutions(int numberOfSkippedExecutions) {
        this.numberOfSkippedExecutions = numberOfSkippedExecutions;
    }
}
