/* TODO add copyright header */

package io.dockstore.webservice.helpers;

import io.dockstore.webservice.core.metrics.TimeSeriesMetric;
import io.dockstore.webservice.core.metrics.TimeSeriesMetric.TimeSeriesMetricInterval;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class TimeSeriesMetricHelper {

    private TimeSeriesMetricHelper() {
    }

    public static TimeSeriesMetric pad(TimeSeriesMetric timeSeries, Instant now) {
        Instant begins = timeSeries.getBegins().toInstant();
        Instant ends = timeSeries.getEnds().toInstant();
        TimeSeriesMetricInterval interval = timeSeries.getInterval();
        // Calculate the number of intervals that we'd need to pad the "ends" date to be after now.
        long padCount = interval.count(ends, now);
        // If we don't need to pad, return the original time series.
        if (padCount <= 0) {
            return timeSeries;
        }
        // Create a new time series and populate it with values that are padded by the calculated number of intervals.
        TimeSeriesMetric paddedTimeSeries = new TimeSeriesMetric();
        paddedTimeSeries.setBegins(timestamp(interval.add(begins, padCount)));
        paddedTimeSeries.setEnds(timestamp(interval.add(ends, padCount)));
        paddedTimeSeries.setInterval(interval);
        paddedTimeSeries.setValues(padValues(timeSeries.getValues(), padCount));
        return paddedTimeSeries;
    }

    static List<Double> padValues(List<Double> values, long padCount) {
        int size = values.size();
        padCount = Math.min(padCount, size);
        padCount = Math.max(padCount, 0);
        List<Double> paddedValues = new ArrayList<>(size);
        for (int i = (int)padCount; i < size; i++) {
            paddedValues.add(values.get(i));
        }
        for (int i = 0; i < padCount; i++) {
            paddedValues.add(0.);
        }
        assert values.size() == paddedValues.size();
        return paddedValues;
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
