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

import io.dockstore.webservice.core.metrics.constraints.HasExecutions;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@HasExecutions
@Schema(name = "ExecutionsRequestBody", description = "Request body model for executionMetricsPost")
public class ExecutionsRequestBody {

    @NotNull
    @Valid
    @Schema(description = "List of workflow run executions to submit")
    private List<RunExecution> runExecutions = new ArrayList<>();

    @NotNull
    @Valid
    @Schema(description = "List of workflow validation executions to submit")
    private List<ValidationExecution> validationExecutions = new ArrayList<>();

    public ExecutionsRequestBody() {
    }

    public List<RunExecution> getRunExecutions() {
        return runExecutions;
    }

    public void setRunExecutions(List<RunExecution> runExecutions) {
        this.runExecutions = runExecutions;
    }

    public List<ValidationExecution> getValidationExecutions() {
        return validationExecutions;
    }

    public void setValidationExecutions(List<ValidationExecution> validationExecutions) {
        this.validationExecutions = validationExecutions;
    }
}
