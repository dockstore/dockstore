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
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import java.util.EnumMap;
import java.util.Map;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "validation_status")
@ApiModel(value = "ValidationStatusMetric", description = "Aggregated metrics about workflow validation statuses")
@Schema(name = "ValidationStatusMetric", description = "Aggregated metrics about workflow validation statuses")
@SuppressWarnings("checkstyle:magicnumber")
public class ValidationStatusCountMetric extends CountMetric<ValidatorTool, ValidatorInfo> {

    /**
     * This field is a map of ValidatorTool enums to ValidatorInfo objects.
     */
    @NotEmpty
    @JsonProperty("validatorTools")
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinTable(name = "validation_status_validator_info", joinColumns = @JoinColumn(name = "validationstatusid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "validatorinfoid", referencedColumnName = "id", columnDefinition = "bigint"))
    @MapKeyColumn(name = "validatortool")
    @MapKeyEnumerated(EnumType.STRING)
    @BatchSize(size = 25)
    @ApiModelProperty(value = "A map containing key-value pairs indicating whether the validator tool successfully validated the workflow", required = true)
    @Schema(description = "A map containing key-value pairs indicating whether the validator tool successfully validated the workflow", requiredMode = RequiredMode.REQUIRED)
    private Map<ValidatorTool, ValidatorInfo> count = new EnumMap<>(ValidatorTool.class);


    public ValidationStatusCountMetric() {
    }

    public ValidationStatusCountMetric(Map<ValidatorTool, ValidatorInfo> validatorToolToIsValid) {
        this.count = validatorToolToIsValid;
    }

    @Override
    public Map<ValidatorTool, ValidatorInfo> getCount() {
        return count;
    }

    public void setCount(Map<ValidatorTool, ValidatorInfo> count) {
        this.count = count;
    }
}
