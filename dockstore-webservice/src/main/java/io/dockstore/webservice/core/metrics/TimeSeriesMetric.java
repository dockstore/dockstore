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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Schema(name = "TimeSeriesMetric", description = "Describes a metric that consists of a series of data values sampled at evenly-spaced points in time", allOf = Metric.class)
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
    @Schema(description = "Time between samples", requiredMode = RequiredMode.REQUIRED)
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

    /**
     * Calculate a time series that is shifted forward in time by the smallest number of intervals necessary to make its
     * "ends" time later than the specified "now" time.
     *
     * The resulting time series is shifted by adding new values to the recent "end" of the time series, and dropping the same
     * number of values from the beginning of the time series.  The resulting time series will have the same number of values
     * as this time series, and time series "bins" that are retained have the same time alignment and values.
     * If the resulting time series is shifted, its "begins" and "ends" times are adjusted to be consistent.
     * If no shifting was necessary, this method may return this time series.  However, it will never modify this time series.
     */
    public TimeSeriesMetric advanceTo(Instant now) {
        // Calculate the whole number of intervals by which we'd need to increase the "ends" time to make it later than "now".
        // This number is the same as the number of values we need to add to the end of the time series.
        long intervalCount = interval.count(ends.toInstant(), now);
        // If we don't need to add any intervals/values, return this time series as-is.
        if (intervalCount <= 0) {
            return this;
        }
        // Create a new time series that is shifted forward by the calculated number of intervals/values.
        TimeSeriesMetric advancedTimeSeries = new TimeSeriesMetric();
        advancedTimeSeries.setBegins(Timestamp.from(interval.add(begins.toInstant(), intervalCount)));
        advancedTimeSeries.setEnds(Timestamp.from(interval.add(ends.toInstant(), intervalCount)));
        advancedTimeSeries.setInterval(interval);
        advancedTimeSeries.setValues(shiftLeft(values, intervalCount, 0.));
        return advancedTimeSeries;
    }

    /**
     * Calculate a new list that contains the elements of the specified list,
     * shifted to the "left", towards the beginning of the list, by the specified number of elements.
     * The new list will have the same number of elements as the specified list.
     * Use the specified value for new elements on the "right" side of the list.
     */
    private static List<Double> shiftLeft(List<Double> list, long shiftCount, double newValue) {
        int size = list.size();
        int clampedShiftCount = (int)Math.max(Math.min(shiftCount, size), 0);
        List<Double> newList = new ArrayList<>(size);
        newList.addAll(list.subList(clampedShiftCount, size));
        newList.addAll(Collections.nCopies(clampedShiftCount, newValue));
        assert list.size() == newList.size();
        return newList;
    }

    /**
     * Calculate the maximum value of the specified number of most recent values in this time series.
     */
    public double maxOfMostRecentValues(int valueCount) {
        if (valueCount <= 0) {
            return Double.MIN_VALUE;
        }
        int size = values.size();
        return Collections.max(values.subList(Math.max(size - valueCount, 0), size));
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

        /**
         * Calculate the minimum whole number of intervals that would need to be added to the specified "from" time to
         * make it later than the specified "to" time.  If the "from" time is later than the "to" time, return zero.
         */
        public long count(Instant from, Instant to) {
            if (from.compareTo(to) >= 0) {
                return 0;
            }
            return temporalUnit.between(time(from), time(to)) + 1;
        }

        /**
         * Add the specified number of intervals to the specified "from" time.
         */
        public Instant add(Instant from, long intervalCount) {
            return time(from).plus(intervalCount, temporalUnit).toInstant();
        }

        private ZonedDateTime time(Instant instant) {
            return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
    }
}
