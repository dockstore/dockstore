/*
 * Copyright 2025 OICR and UCSC
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Schema(name = "TimeSeriesMetric", description = "Describes a metric that consists of a series of data values sampled at evenly-spaced intervals in time", allOf = Metric.class)
public class TimeSeriesMetric extends Metric {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @NotNull
    @ArraySchema(arraySchema = @Schema(description = "List of sample values, oldest values first"), schema = @Schema(description = "Sample value"))
    private List<Double> values;

    @Column(nullable = false)
    @NotNull
    @Schema(description = "Earliest time represented by this time series", requiredMode = RequiredMode.REQUIRED)
    private Timestamp begins;

    @Column(nullable = false)
    @NotNull
    @Schema(description = "Latest time represented by this time series", requiredMode = RequiredMode.REQUIRED)
    private Timestamp ends;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Schema(description = "The interval between samples", requiredMode = RequiredMode.REQUIRED)
    private TimeSeriesMetricInterval interval;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    @JsonIgnore
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    @JsonIgnore
    private Timestamp dbUpdateDate;

    public TimeSeriesMetric() {
    }

    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    public void setDbCreateDate(Timestamp dbCreateDate) {
        this.dbCreateDate = dbCreateDate;
    }

    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    public void setDbUpdateDate(Timestamp dbUpdateDate) {
        this.dbUpdateDate = dbUpdateDate;
    }

    public List<Double> getValues() {
        return values;
    }

    public void setValues(List<Double> values) {
        this.values = values;
    }

    public Timestamp getBegins() {
        return begins;
    }

    public void setBegins(Timestamp begins) {
        this.begins = begins;
    }

    public Timestamp getEnds() {
        return ends;
    }

    public void setEnds(Timestamp ends) {
        this.ends = ends;
    }

    public TimeSeriesMetricInterval getInterval() {
        return interval;
    }

    public void setInterval(TimeSeriesMetricInterval interval) {
        this.interval = interval;
    }

    public enum TimeSeriesMetricInterval {
        SECOND(ChronoUnit.SECONDS),
        MINUTE(ChronoUnit.MINUTES),
        HOUR(ChronoUnit.HOURS),
        DAY(ChronoUnit.DAYS),
        WEEK(ChronoUnit.WEEKS),
        MONTH(ChronoUnit.MONTHS),
        YEAR(ChronoUnit.YEARS);

        private final TemporalUnit temporalUnit;

        TimeSeriesMetricInterval(TemporalUnit temporalUnit) {
            this.temporalUnit = temporalUnit;
        }

        public long count(Instant from, Instant to) {
            if (from.compareTo(to) >= 0) {
                return 0;
            }
            return temporalUnit.between(time(from), time(to)) + 1;
        }

        public Instant add(Instant from, long intervalCount) {
            return time(from).plus(intervalCount, temporalUnit).toInstant();
        }

        private ZonedDateTime time(Instant instant) {
            return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
    }
}
