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
import io.dockstore.webservice.core.metrics.constraints.ISO8601ExecutionDate;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;

/**
 * This is an object to encapsulate an execution that validates a workflow in an entity. Does not need to be stored in the database.
 */
@Schema(name = "ValidationExecution", description = "Metrics of a workflow validated on a platform", allOf = Execution.class)
public class ValidationExecution extends Execution {

    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "The validator tool used to validate the workflow", requiredMode = RequiredMode.REQUIRED, example = "miniwdl")
    private ValidatorTool validatorTool;

    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "The version of the validator tool", requiredMode = RequiredMode.REQUIRED)
    private String validatorToolVersion;

    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Boolean indicating if the workflow was validated successfully", requiredMode = RequiredMode.REQUIRED, example = "true")
    private Boolean isValid;

    @Schema(description = "The error message for a failed validation by the validator tool")
    private String errorMessage;

    @NotNull
    @ISO8601ExecutionDate
    @JsonProperty(required = true)
    @Schema(description = "The date and time that the validator tool was executed in ISO 8601 UTC date format", requiredMode = RequiredMode.REQUIRED, example = "2023-03-31T15:06:49.888745366Z")
    private String dateExecuted;

    public ValidationExecution() {
    }

    public ValidationExecution(ValidatorTool validatorTool, Boolean isValid) {
        this.validatorTool = validatorTool;
        this.isValid = isValid;
    }

    public ValidatorTool getValidatorTool() {
        return validatorTool;
    }

    public void setValidatorTool(ValidatorTool validatorTool) {
        this.validatorTool = validatorTool;
    }

    public String getValidatorToolVersion() {
        return validatorToolVersion;
    }

    public void setValidatorToolVersion(String validatorToolVersion) {
        this.validatorToolVersion = validatorToolVersion;
    }

    public Boolean getIsValid() {
        return isValid;
    }

    public void setIsValid(Boolean isValid) {
        this.isValid = isValid;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getDateExecuted() {
        return dateExecuted;
    }

    public void setDateExecuted(String dateExecuted) {
        this.dateExecuted = dateExecuted;
    }

    /**
     * Enums for tools that can validate a workflow.
     */
    public enum ValidatorTool {
        MINIWDL,
        WOMTOOL,
        CWLTOOL,
        NF_VALIDATION,
        OTHER // This is meant for validator tools that we may not know about yet, but can add in the future
    }
}
