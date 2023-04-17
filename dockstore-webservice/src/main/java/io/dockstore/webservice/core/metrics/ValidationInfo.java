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

import io.dockstore.webservice.core.ValidatorToolVersionConverter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Embeddable
@Schema(description = "Aggregated validation information")
@SuppressWarnings("checkstyle:magicnumber")
public class ValidationInfo implements Serializable {
    @Column(nullable = false)
    @Schema(description = "The version of the validator tool that was most recently executed", required = true, example = "1.0")
    private String mostRecentVersion;

    @Column(nullable = false)
    @Schema(description = "The boolean isValid value from the most recent validation run", required = true, example = "true")
    private Boolean mostRecentIsValid;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "The error message of the most recent validation run if it failed")
    private String mostRecentErrorMessage;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = ValidatorToolVersionConverter.class)
    @Schema(description = "A set of validator tool versions that successfully validated the workflow in its most recent run", required = true, example = "[\"1.0\"]")
    private List<String> successfulValidationVersions = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    @Convert(converter = ValidatorToolVersionConverter.class)
    @Schema(description = "A set of validator tool versions that unsuccessfully validated the workflow in its most recent run", required = true, example = "[]")
    private List<String> failedValidationVersions = new ArrayList<>();

    @Column(nullable = false)
    @Schema(description = "A percentage representing how often the validator successfully validates the workflow", required = true, example = "100.0")
    private Double passingRate;

    @Column(nullable = false)
    @Schema(description = "The number of times the validator was executed on the workflow", required = true, example = "1")
    private Integer numberOfRuns;

    // database timestamps
    @Column(updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT NOW()")
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public ValidationInfo() {
    }

    public String getMostRecentVersion() {
        return mostRecentVersion;
    }

    public void setMostRecentVersion(String mostRecentlyExecutedVersion) {
        this.mostRecentVersion = mostRecentlyExecutedVersion;
    }

    public Boolean getMostRecentIsValid() {
        return mostRecentIsValid;
    }

    public void setMostRecentIsValid(Boolean mostRecentIsValid) {
        this.mostRecentIsValid = mostRecentIsValid;
    }

    public String getMostRecentErrorMessage() {
        return mostRecentErrorMessage;
    }

    public void setMostRecentErrorMessage(String mostRecentErrorMessage) {
        this.mostRecentErrorMessage = mostRecentErrorMessage;
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

    public List<String> getSuccessfulValidationVersions() {
        return successfulValidationVersions;
    }

    public void setSuccessfulValidationVersions(List<String> successfulValidationVersions) {
        this.successfulValidationVersions = successfulValidationVersions;
    }

    public List<String> getFailedValidationVersions() {
        return failedValidationVersions;
    }

    public void setFailedValidationVersions(List<String> failedValidationVersions) {
        this.failedValidationVersions = failedValidationVersions;
    }
}
