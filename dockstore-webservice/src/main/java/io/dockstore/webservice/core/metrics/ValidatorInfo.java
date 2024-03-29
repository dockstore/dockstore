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

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * This class contains aggregated validation information for a particular validator tool.
 */
@Entity
@Table(name = "validator_info")
@Schema(description = "Aggregated validation information for a particular validator tool like miniwdl")
@SuppressWarnings("checkstyle:magicnumber")
public class ValidatorInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "Implementation specific ID for the validator info in this web service", required = true, position = 0)
    private long id;

    @NotEmpty
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade({ CascadeType.DETACH, CascadeType.SAVE_UPDATE })
    @JoinTable(name = "validator_versions", joinColumns = @JoinColumn(name = "validatorinfoid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "validatorversioninfoid", referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "A list containing validation info for the most recent execution of the validator tool versions")
    private Set<ValidatorVersionInfo> validatorVersions = new HashSet<>();

    @NotNull
    @Column(nullable = false)
    @Schema(description = "The version of the validator tool that was most recently executed", required = true)
    private String mostRecentVersionName;

    @NotNull
    @Column(nullable = false)
    @Schema(description = "A percentage representing how often the validator successfully validated the workflow", required = true, example = "100.0")
    private double passingRate;

    @NotNull
    @Column(nullable = false)
    @Schema(description = "The number of times the validator was executed on the workflow", required = true, example = "1")
    private int numberOfRuns;

    // database timestamps
    @Column(updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT NOW()")
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public ValidatorInfo() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getMostRecentVersionName() {
        return mostRecentVersionName;
    }

    public void setMostRecentVersionName(String mostRecentVersionName) {
        this.mostRecentVersionName = mostRecentVersionName;
    }

    public double getPassingRate() {
        return passingRate;
    }

    public void setPassingRate(double passingRate) {
        this.passingRate = passingRate;
    }

    public int getNumberOfRuns() {
        return numberOfRuns;
    }

    public void setNumberOfRuns(int numberOfRuns) {
        this.numberOfRuns = numberOfRuns;
    }

    public Set<ValidatorVersionInfo> getValidatorVersions() {
        return validatorVersions;
    }

    public void setValidatorVersions(Set<ValidatorVersionInfo> validatorVersions) {
        this.validatorVersions = validatorVersions;
    }
}
