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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A wrapper class that contains a list of tasks executed during a workflow execution
 */
@Schema(description = "Metrics of individual tasks that were executed during the workflow execution.")
public class TaskExecutions {

    @NotEmpty
    @JsonProperty(required = true)
    @Schema(description = "User-provided ID of the set of task executions. This ID is used to identify the set of task executions when updating the execution", requiredMode = RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "Metrics of individual tasks that were executed during the workflow execution.")
    List<RunExecution> taskExecutions = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<RunExecution> getTaskExecutions() {
        return taskExecutions;
    }

    public void setTaskExecutions(List<RunExecution> taskExecutions) {
        this.taskExecutions = taskExecutions;
    }

    public void update(TaskExecutions newTaskExecutions) {
        for (RunExecution newTaskExecution: newTaskExecutions.getTaskExecutions()) {
            // Find the same task in the old TaskExecutions set using the execution ID
            Optional<RunExecution> oldTaskExecution = this.taskExecutions.stream()
                    .filter(taskExecution -> taskExecution.getId().equals(newTaskExecution.getId()))
                    .findFirst();
            oldTaskExecution.ifPresent(runExecution -> runExecution.update(newTaskExecution));
        }
    }
}
