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
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "validator_version_info")
@Schema(description = "Validation information for a validator tool version")
public class ValidatorVersionInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "Implementation specific ID for the validation version info in this web service", required = true, position = 0)
    private long id;

    @NotNull
    @Column(nullable = false)
    @JsonProperty(required = true)
    @Schema(description = "The version name of the validator tool", required = true)
    private String name;

    @NotNull
    @Column(nullable = false)
    @JsonProperty(required = true)
    @Schema(description = "Boolean indicating if the workflow was validated successfully", required = true, example = "true")
    private Boolean isValid;

    @Schema(description = "The error message for a failed validation by the validator tool")
    private String errorMessage;

    @NotNull
    @Column(nullable = false)
    @ISO8601ExecutionDate
    @JsonProperty(required = true)
    @Schema(description = "The date and time that the validator tool was executed in ISO 8601 UTC date format", required = true, example = "2023-03-31T15:06:49.888745366Z")
    private String dateExecuted;

    @NotNull
    @Column(nullable = false)
    @JsonProperty(required = true)
    @Schema(description = "A percentage representing how often the validator successfully validates the workflow", required = true, example = "100.0")
    private Double passingRate;

    @NotNull
    @Column(nullable = false)
    @JsonProperty(required = true)
    @Schema(description = "The number of times the validator was executed on the workflow", required = true, example = "1")
    private Integer numberOfRuns;

    // database timestamps
    @Column(updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT NOW()")
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public ValidatorVersionInfo() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getIsValid() {
        return isValid;
    }

    public void setIsValid(Boolean valid) {
        isValid = valid;
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

    public Double getPassingRate() {
        return passingRate;
    }

    public void setPassingRate(Double passingRate) {
        this.passingRate = passingRate;
    }

    public Integer getNumberOfRuns() {
        return numberOfRuns;
    }

    public void setNumberOfRuns(Integer numberOfRuns) {
        this.numberOfRuns = numberOfRuns;
    }
}
