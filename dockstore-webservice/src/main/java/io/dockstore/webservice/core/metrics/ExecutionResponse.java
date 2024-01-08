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
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(name = "ExecutionResponse", description = "Response for a single execution metric as a result of an API")
public class ExecutionResponse {

    @Schema(description = "The ID of the execution that the response is for", requiredMode = RequiredMode.REQUIRED)
    private String executionId;

    @Schema(description = "The response status code of the action for the execution", requiredMode = RequiredMode.REQUIRED)
    private int status;

    @Schema(description = "The error message if one exists")
    private String error;

    public ExecutionResponse(String executionId, int status) {
        this(executionId, status, null);
    }

    public ExecutionResponse(String executionId, int status, String error) {
        this.executionId = executionId;
        this.status = status;
        this.error = error;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
