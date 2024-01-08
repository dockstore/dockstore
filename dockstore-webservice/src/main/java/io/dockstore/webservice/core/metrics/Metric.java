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
import jakarta.persistence.Column;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@Schema(name = "Metric", description = "Describes an aggregated metric")
public abstract class Metric {

    @Column
    @Schema(description = "Whether or not invalid executions were skipped during the aggregation", defaultValue = "false")
    boolean wereExecutionsSkipped = false;

    @Column
    @Schema(description = "The number of executions that were aggregated")
    int numberOfExecutionsAggregated;

    public boolean wereExecutionsSkipped() {
        return wereExecutionsSkipped;
    }

    public void setWereExecutionsSkipped(boolean wereExecutionsSkipped) {
        this.wereExecutionsSkipped = wereExecutionsSkipped;
    }

    public int getNumberOfExecutionsAggregated() {
        return numberOfExecutionsAggregated;
    }

    public void setNumberOfExecutionsAggregated(int numberOfExecutionsAggregated) {
        this.numberOfExecutionsAggregated = numberOfExecutionsAggregated;
    }
}
