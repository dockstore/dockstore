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
import java.util.HashMap;
import java.util.Map;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name = "execution_status")
@ApiModel()
public class ExecutionStatusCountMetric extends CountMetric<ExecutionStatusCountMetric.ExecutionStatus> {

    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL, targetEntity = CountMetric.class)
    @JoinTable(name = "execution_status_count", joinColumns = @JoinColumn(name = "executionstatusid", referencedColumnName = "id", columnDefinition = "bigint"))
    @MapKeyColumn(name = "executionstatus")
    @ApiModelProperty(value = "A map containing the count for each key")
    private Map<ExecutionStatus, Integer> count = new HashMap<>();

    @Column(nullable = false)
    @Schema(description = "Number of successful executions")
    private int numberOfSuccessfulExecutions;

    @Column(nullable = false)
    @Schema(description = "Number of failed executions. An execution may have failed because it was semantically or runtime invalid")
    private int numberOfFailedExecutions;

    @Column(nullable = false)
    @Schema(description = "Indicates if all executions of the workflow are semantic and runtime valid")
    boolean isValid;

    public ExecutionStatusCountMetric(long id, Map<ExecutionStatus, Integer> count) {
        super(id);
        this.count = count;
        this.isValid = !count.containsKey(ExecutionStatus.FAILED_SEMANTIC_INVALID) && !count.containsKey(ExecutionStatus.FAILED_RUNTIME_INVALID);
        this.numberOfSuccessfulExecutions = count.get(ExecutionStatus.SUCCESSFUL);
        this.numberOfFailedExecutions = count.get(ExecutionStatus.FAILED_SEMANTIC_INVALID) + count.get(ExecutionStatus.FAILED_RUNTIME_INVALID);
    }

    @Override
    public Map<ExecutionStatus, Integer> getCount() {
        return count;
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

    public enum ExecutionStatus {
        SUCCESSFUL,
        FAILED_SEMANTIC_INVALID,
        FAILED_RUNTIME_INVALID
    }
}
