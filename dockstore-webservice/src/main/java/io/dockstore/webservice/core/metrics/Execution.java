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
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * This is an object to encapsulate execution metrics data in an entity. Does not need to be stored in the database.
 */
@Schema(name = "Execution", description = "Metrics of a workflow execution on a platform", subTypes = { RunExecution.class, ValidationExecution.class })
public abstract class Execution {

    protected Execution() {
    }

    @NotNull
    @ISO8601ExecutionDate
    @JsonProperty(required = true)
    @Schema(description = "The date and time that the execution occurred in ISO 8601 UTC date format", requiredMode = Schema.RequiredMode.REQUIRED, example = "2023-03-31T15:06:49.888745366Z")
    private String dateExecuted;

    @JsonProperty
    @Schema(description = "Additional properties that aren't defined. Provide a context, like one from schema.org, if you want to use a specific vocabulary",
            example = """
            {
              "@context": {
                "schema": "https://schema.org"
              },
              "schema:actionStatus": "CompletedActionStatus"
            }
            """)
    private Map<String, Object> additionalProperties;

    public String getDateExecuted() {
        return dateExecuted;
    }

    public void setDateExecuted(String dateExecuted) {
        this.dateExecuted = dateExecuted;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}
