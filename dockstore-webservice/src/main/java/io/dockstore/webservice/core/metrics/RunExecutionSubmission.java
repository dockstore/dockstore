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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.dockstore.webservice.core.metrics.constraints.HasWorkflowOrTaskExecutions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;

/**
 * This is an object to encapsulate workflow run execution metrics data in an entity. Does not need to be stored in the database.
 */
@Schema(name = "RunExecutionSubmission", description = "An object representing the submission of overall workflow or task metrics for a workflow that was executed on a platform")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@HasWorkflowOrTaskExecutions
public class RunExecutionSubmission {
    @Valid
    @Schema(description = "Metrics of an overall workflow execution. This field is optional and the user may provide individual task execution metrics instead.")
    private RunExecution workflowExecution;

    @Valid
    @Schema(description = "Metrics of individual tasks that were executed during the workflow execution. This field is optional and the user may provide the overall workflow execution metrics instead.")
    private List<RunExecution> taskExecutions;

    public RunExecution getWorkflowExecution() {
        return workflowExecution;
    }

    public void setWorkflowExecution(RunExecution workflowExecution) {
        this.workflowExecution = workflowExecution;
    }

    public List<RunExecution> getTaskExecutions() {
        return taskExecutions;
    }

    public void setTaskExecutions(List<RunExecution> taskExecutions) {
        this.taskExecutions = taskExecutions;
    }
}
