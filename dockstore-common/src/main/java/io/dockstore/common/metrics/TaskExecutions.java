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

package io.dockstore.common.metrics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A wrapper class that contains a list of tasks executed during a workflow execution
 */
@Schema(description = "Metrics of individual tasks that were executed during the workflow execution.", allOf = Execution.class)
public class TaskExecutions extends Execution {

    @Schema(description = "Metrics of individual tasks that were executed during the workflow execution.")
    List<RunExecution> taskExecutions = new ArrayList<>();

    public List<RunExecution> getTaskExecutions() {
        return taskExecutions;
    }

    public void setTaskExecutions(List<RunExecution> taskExecutions) {
        this.taskExecutions = taskExecutions;
    }

    public void update(TaskExecutions newTaskExecutions) {
        super.update(newTaskExecutions);
        for (RunExecution newTaskExecution: newTaskExecutions.getTaskExecutions()) {
            // Find the same task in the old TaskExecutions set using the execution ID
            Optional<RunExecution> oldTaskExecution = this.taskExecutions.stream()
                    .filter(taskExecution -> taskExecution.getExecutionId().equals(newTaskExecution.getExecutionId()))
                    .findFirst();
            oldTaskExecution.ifPresent(runExecution -> runExecution.update(newTaskExecution));
        }
    }
}
