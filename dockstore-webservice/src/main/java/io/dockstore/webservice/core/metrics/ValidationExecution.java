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
import javax.validation.constraints.NotNull;

/**
 * This is an object to encapsulate an execution that validates a workflow in an entity. Does not need to be stored in the database.
 */
@Schema(name = "ValidationExecution", description = "Metrics of a workflow validated on a platform", allOf = Execution.class)
public class ValidationExecution extends Execution {

    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "The validator tool used to validate the workflow", required = true, example = "miniwdl")
    String validatorTool;

    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Boolean indicating if the workflow was validated successfully", required = true, example = "true")
    Boolean valid;

    public ValidationExecution() {
    }

    public ValidationExecution(String validatorTool, Boolean isValid) {
        this.validatorTool = validatorTool;
        this.valid = isValid;
    }

    public String getValidatorTool() {
        return validatorTool;
    }

    public void setValidatorTool(String validatorTool) {
        this.validatorTool = validatorTool;
    }

    public Boolean isValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }
}
