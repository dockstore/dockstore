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
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.SequenceGenerator;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class StatisticMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "statisticmetric_id_seq")
    @SequenceGenerator(name = "statisticmetric_id_seq", sequenceName = "statisticmetric_id_seq", allocationSize = 1)
    @ApiModelProperty(value = "Implementation specific ID for statistical metrics in this webservice")
    @Schema(description = "Implementation specific ID for statistical metrics in this webservice")
    private long id;

    @Column
    @ApiModelProperty(value = "The minimum value from the data points")
    @Schema(description = "The minimum value from the data points")
    private String minimum;

    @Column
    @ApiModelProperty(value = "The maximum value from the data points")
    @Schema(description = "The maximum value from the data points")
    private String maximum;

    @Column
    @ApiModelProperty(value = "The average value from the data points")
    @Schema(description = "The average value from the data points")
    private String average;

    @Column
    @ApiModelProperty(value = "The number of data points used to calculate the average")
    @Schema(description = "The number of data points used to calculate the average")
    private String numberOfDataPointsForAverage;

    public StatisticMetric() {
    }

    public StatisticMetric(String minimum, String maximum, String average) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.average = average;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getMinimum() {
        return minimum;
    }

    public void setMinimum(String minimum) {
        this.minimum = minimum;
    }

    public String getMaximum() {
        return maximum;
    }

    public void setMaximum(String maximum) {
        this.maximum = maximum;
    }

    public String getAverage() {
        return average;
    }

    public void setAverage(String average) {
        this.average = average;
    }

    public String getNumberOfDataPointsForAverage() {
        return numberOfDataPointsForAverage;
    }

    public void setNumberOfDataPointsForAverage(String numberOfDataPointsForAverage) {
        this.numberOfDataPointsForAverage = numberOfDataPointsForAverage;
    }
}
