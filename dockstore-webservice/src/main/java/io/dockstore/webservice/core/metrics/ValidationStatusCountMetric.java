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
import io.dockstore.webservice.core.metrics.ValidationExecution.ValidatorTool;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.util.EnumMap;
import java.util.Map;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "validation_status")
@ApiModel(value = "ValidationStatusMetric", description = "Aggregated metrics about workflow validation statuses")
@Schema(name = "ValidationStatusMetric", description = "Aggregated metrics about workflow validation statuses")
@SuppressWarnings("checkstyle:magicnumber")
public class ValidationStatusCountMetric extends CountMetric<ValidatorTool, ValidationInfo> {

    @ElementCollection(targetClass = ValidationInfo.class, fetch = FetchType.EAGER)
    @MapKeyColumn(name = "validatortool")
    @MapKeyEnumerated(EnumType.STRING)
    @CollectionTable(name = "validator_tool_validation_info", joinColumns = @JoinColumn(name = "validationstatusid", referencedColumnName = "id"))
    @BatchSize(size = 25)
    @ApiModelProperty(value = "A map containing key-value pairs indicating whether the validator tool successfully validated the workflow", required = true)
    @Schema(description = "A map containing key-value pairs indicating whether the validator tool successfully validated the workflow", requiredMode = RequiredMode.REQUIRED, example = """
            {
                "MINIWDL": true
            }
            """)
    private Map<ValidatorTool, ValidationInfo> validatorToolToIsValid = new EnumMap<>(ValidatorTool.class);


    public ValidationStatusCountMetric() {
    }

    public ValidationStatusCountMetric(Map<ValidatorTool, ValidationInfo> validatorToolToIsValid) {
        this.validatorToolToIsValid = validatorToolToIsValid;
    }

    @Override
    @JsonProperty("validatorToolToIsValid")
    public Map<ValidatorTool, ValidationInfo> getCount() {
        return validatorToolToIsValid;
    }

    public void setValidatorToolToIsValid(Map<ValidatorTool, ValidationInfo> validatorToolToIsValid) {
        this.validatorToolToIsValid = validatorToolToIsValid;
    }
}
