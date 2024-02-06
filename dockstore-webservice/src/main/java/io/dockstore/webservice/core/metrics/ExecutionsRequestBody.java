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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.dockstore.webservice.core.metrics.constraints.HasExecutionsOrMetrics;
import io.dockstore.webservice.core.metrics.constraints.HasUniqueExecutionIds;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@HasExecutionsOrMetrics
@HasUniqueExecutionIds
@Schema(name = "ExecutionsRequestBody", description = "Request body model for executionMetricsPost")
public class ExecutionsRequestBody {

    @NotNull
    @Valid
    @Schema(description = "List of workflow run executions to submit")
    private List<RunExecution> runExecutions = new ArrayList<>();

    @NotNull
    @Valid
    @Schema(description = "List of task run executions to submit. Each TaskExecution represents the tasks executed during a workflow execution.")
    private List<TaskExecutions> taskExecutions = new ArrayList<>();

    @NotNull
    @Valid
    @Schema(description = "List of workflow validation executions to submit")
    private List<ValidationExecution> validationExecutions = new ArrayList<>();

    @NotNull
    @Valid
    @ArraySchema(arraySchema = @Schema(description = "List of aggregated executions to submit", deprecated = true))
    @Deprecated(since = "1.15.0")
    private List<AggregatedExecution> aggregatedExecutions = new ArrayList<>();

    public ExecutionsRequestBody() {
    }

    public List<RunExecution> getRunExecutions() {
        return runExecutions;
    }

    public void setRunExecutions(List<RunExecution> runExecutions) {
        this.runExecutions = runExecutions;
    }

    public List<TaskExecutions> getTaskExecutions() {
        return taskExecutions;
    }

    public void setTaskExecutions(List<TaskExecutions> taskExecutions) {
        this.taskExecutions = taskExecutions;
    }

    public List<ValidationExecution> getValidationExecutions() {
        return validationExecutions;
    }

    public void setValidationExecutions(List<ValidationExecution> validationExecutions) {
        this.validationExecutions = validationExecutions;
    }

    @Deprecated(since = "1.15.0")
    public List<AggregatedExecution> getAggregatedExecutions() {
        return aggregatedExecutions;
    }

    @Deprecated(since = "1.15.0")
    public void setAggregatedExecutions(List<AggregatedExecution> aggregatedExecutions) {
        this.aggregatedExecutions = aggregatedExecutions;
    }

    public Optional<RunExecution> getRunExecutionByExecutionId(String executionId) {
        return this.runExecutions.stream().filter(workflowExecution -> executionId.equals(workflowExecution.getExecutionId())).findFirst();
    }

    public Optional<TaskExecutions> getTaskExecutionsByExecutionId(String executionId) {
        return this.taskExecutions.stream().filter(taskExecutionsSet -> executionId.equals(taskExecutionsSet.getExecutionId())).findFirst();
    }

    public Optional<ValidationExecution> getValidationExecutionByExecutionId(String executionId) {
        return this.validationExecutions.stream().filter(validationExecution -> executionId.equals(validationExecution.getExecutionId())).findFirst();
    }

    public Optional<AggregatedExecution> getAggregatedExecutionByExecutionId(String executionId) {
        return this.aggregatedExecutions.stream().filter(aggregatedExecution -> executionId.equals(aggregatedExecution.getExecutionId())).findFirst();
    }

    public boolean containsExecutionId(String executionId) {
        return getRunExecutionByExecutionId(executionId).isPresent()
                || getTaskExecutionsByExecutionId(executionId).isPresent()
                || getValidationExecutionByExecutionId(executionId).isPresent()
                || getAggregatedExecutionByExecutionId(executionId).isPresent();
    }

    /**
     * Finds the execution and returns it in an ExecutionsRequestBody containing only that execution if "this" contains the execution with executionId
     * @param executionId
     * @return
     */
    public Optional<ExecutionsRequestBody> getExecutionAsExecutionsRequestBodyWithOneExecution(String executionId) {
        ExecutionsRequestBody executionsRequestBodyWithOneExecution = new ExecutionsRequestBody();

        Optional<RunExecution> foundWorkflowExecution = getRunExecutionByExecutionId(executionId);
        if (foundWorkflowExecution.isPresent()) {
            executionsRequestBodyWithOneExecution.getRunExecutions().add(foundWorkflowExecution.get());
            return Optional.of(executionsRequestBodyWithOneExecution);
        }

        Optional<TaskExecutions> foundTaskExecutions = getTaskExecutionsByExecutionId(executionId);
        if (foundTaskExecutions.isPresent()) {
            executionsRequestBodyWithOneExecution.getTaskExecutions().add(foundTaskExecutions.get());
            return Optional.of(executionsRequestBodyWithOneExecution);
        }

        Optional<ValidationExecution> foundValidationExecution = getValidationExecutionByExecutionId(executionId);
        if (foundValidationExecution.isPresent()) {
            executionsRequestBodyWithOneExecution.getValidationExecutions().add(foundValidationExecution.get());
            return Optional.of(executionsRequestBodyWithOneExecution);
        }

        Optional<AggregatedExecution> foundAggregatedExecution = getAggregatedExecutionByExecutionId(executionId);
        if (foundAggregatedExecution.isPresent()) {
            executionsRequestBodyWithOneExecution.getAggregatedExecutions().add(foundAggregatedExecution.get());
            return Optional.of(executionsRequestBodyWithOneExecution);
        }

        return Optional.empty();
    }

    @JsonIgnore // Ignore because helper method
    public List<String> getExecutionIds() {
        List<String> executionIds = Stream.of(runExecutions, taskExecutions, validationExecutions).flatMap(List::stream)
                .map(Execution::getExecutionId)
                .collect(Collectors.toList());
        executionIds.addAll(aggregatedExecutions.stream().map(AggregatedExecution::getExecutionId).toList());
        return executionIds;
    }

    /**
     * Updates the execution in this object specified by executionToUpdate.
     * Does NOT update the deprecated AggregatedExecutions.
     * @param executionToUpdate
     */
    public void updateExecution(Execution executionToUpdate) {
        if (executionToUpdate instanceof RunExecution newWorkflowExecution) {
            getRunExecutionByExecutionId(newWorkflowExecution.getExecutionId()).ifPresent(oldWorkflowExecution -> oldWorkflowExecution.update(newWorkflowExecution));
        } else if (executionToUpdate instanceof TaskExecutions newTaskExecutions) {
            getTaskExecutionsByExecutionId(newTaskExecutions.getExecutionId()).ifPresent(oldTaskExecutions -> oldTaskExecutions.update(newTaskExecutions));
        } else if (executionToUpdate instanceof ValidationExecution newValidationExecution) {
            getValidationExecutionByExecutionId(newValidationExecution.getExecutionId()).ifPresent(oldValidationExecution -> oldValidationExecution.update(newValidationExecution));
        }
    }
}
