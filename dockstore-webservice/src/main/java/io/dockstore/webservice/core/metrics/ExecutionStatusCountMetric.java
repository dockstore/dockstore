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
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.EnumMap;
import java.util.Map;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.MapKeyEnumerated;
import javax.persistence.Table;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "execution_status")
@ApiModel(value = "ExecutionStatusMetric", description = "Aggregated metrics about workflow execution statuses")
@Schema(name = "ExecutionStatusMetric", description = "Aggregated metrics about workflow execution statuses")
@SuppressWarnings("checkstyle:magicnumber")
public class ExecutionStatusCountMetric extends CountMetric<ExecutionStatusCountMetric.ExecutionStatus> {

    @ElementCollection(fetch = FetchType.LAZY)
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "executionstatus")
    @Column(name = "count", nullable = false)
    @CollectionTable(name = "execution_status_count", joinColumns = @JoinColumn(name = "executionstatusid", referencedColumnName = "id"))
    @BatchSize(size = 25)
    @ApiModelProperty(value = "A map containing the count for each key")
    private Map<ExecutionStatus, Integer> count = new EnumMap<>(ExecutionStatus.class);

    @Column(nullable = false)
    @Schema(description = "Number of successful executions")
    private int numberOfSuccessfulExecutions;

    @Column(nullable = false)
    @Schema(description = "Number of failed executions. An execution may have failed because it was semantically or runtime invalid")
    private int numberOfFailedExecutions;

    @Column(nullable = false)
    @Schema(description = "Indicates if all executions of the workflow are semantic and runtime valid")
    boolean isValid;

    public ExecutionStatusCountMetric() {
        count.put(ExecutionStatus.SUCCESSFUL, 0);
        count.put(ExecutionStatus.FAILED_RUNTIME_INVALID, 0);
        count.put(ExecutionStatus.FAILED_SEMANTIC_INVALID, 0);
        calculateValidAndNumberOfExecutions();
    }

    public ExecutionStatusCountMetric(Map<ExecutionStatus, Integer> count) {
        this.count = count;
        calculateValidAndNumberOfExecutions();
    }

    @Override
    public Map<ExecutionStatus, Integer> getCount() {
        return count;
    }

    @Override
    public void addCount(ExecutionStatus key, int countToAdd) {
        super.addCount(key, countToAdd);
        calculateValidAndNumberOfExecutions();
    }

    private void calculateValidAndNumberOfExecutions() {
        this.isValid = (count.getOrDefault(ExecutionStatus.FAILED_SEMANTIC_INVALID, 0) + count.getOrDefault(ExecutionStatus.FAILED_RUNTIME_INVALID, 0)) == 0;
        this.numberOfSuccessfulExecutions = count.getOrDefault(ExecutionStatus.SUCCESSFUL, 0);
        this.numberOfFailedExecutions = count.getOrDefault(ExecutionStatus.FAILED_SEMANTIC_INVALID, 0) + count.getOrDefault(ExecutionStatus.FAILED_RUNTIME_INVALID, 0);
    }

    public void setCount(Map<ExecutionStatus, Integer> count) {
        this.count = count;
    }

    public int getNumberOfSuccessfulExecutions() {
        return numberOfSuccessfulExecutions;
    }

    public void setNumberOfSuccessfulExecutions(int numberOfSuccessfulExecutions) {
        this.numberOfSuccessfulExecutions = numberOfSuccessfulExecutions;
    }

    public int getNumberOfFailedExecutions() {
        return numberOfFailedExecutions;
    }

    public void setNumberOfFailedExecutions(int numberOfFailedExecutions) {
        this.numberOfFailedExecutions = numberOfFailedExecutions;
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public int getNumberOfExecutions() {
        return numberOfSuccessfulExecutions + numberOfFailedExecutions;
    }

    public enum ExecutionStatus {
        SUCCESSFUL,
        FAILED_SEMANTIC_INVALID,
        FAILED_RUNTIME_INVALID
    }
}
