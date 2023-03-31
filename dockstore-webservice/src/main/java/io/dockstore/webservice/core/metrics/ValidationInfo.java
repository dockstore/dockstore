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

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Embeddable
@Schema(description = "Aggregated validation information")
public class ValidationInfo implements Serializable {

    @Column(nullable = false)
    @Schema(description = "The boolean isValid value from the newest validation run")
    private boolean latestIsValid;

    @Column(nullable = false)
    private long passingRate;

    @Column(nullable = false)
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

    public ValidationInfo(boolean latestIsValid, long passingRate, Integer numberOfRuns) {
        this.latestIsValid = latestIsValid;
        this.passingRate = passingRate;
        this.numberOfRuns = numberOfRuns;
    }

    public boolean isLatestIsValid() {
        return latestIsValid;
    }

    public void setLatestIsValid(boolean latestIsValid) {
        this.latestIsValid = latestIsValid;
    }

    public long getPassingRate() {
        return passingRate;
    }

    public void setPassingRate(long passingRate) {
        this.passingRate = passingRate;
    }

    public Integer getNumberOfRuns() {
        return numberOfRuns;
    }

    public void setNumberOfRuns(Integer numberOfRuns) {
        this.numberOfRuns = numberOfRuns;
    }
}
