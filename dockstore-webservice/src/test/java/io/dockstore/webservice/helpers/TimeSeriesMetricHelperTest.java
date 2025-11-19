package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.webservice.core.metrics.TimeSeriesMetric;
import io.dockstore.webservice.core.metrics.TimeSeriesMetric.TimeSeriesMetricInterval;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class TimeSeriesMetricHelperTest {

    @Test
    public void testIntervals() {
       
        for (TimeSeriesMetricInterval interval: TimeSeriesMetricInterval.values()) { 
            TimeSeriesMetric timeSeries = makeTimeSeries(40, interval);
            Instant now = timeSeries.getEnds().toInstant().minus(1, ChronoUnit.SECONDS);
            TimeSeriesMetric paddedTimeSeries = TimeSeriesMetricHelper.pad(timeSeries, now);
            assertEquals(timeSeries.getBegins(), paddedTimeSeries.getBegins());
            assertEquals(timeSeries.getEnds(), paddedTimeSeries.getEnds());
            now = timeSeries.getEnds().toInstant().plus(1, ChronoUnit.SECONDS);
            paddedTimeSeries = TimeSeriesMetricHelper.pad(timeSeries, now);
            assertTrue(timeSeries.getBegins().compareTo(paddedTimeSeries.getBegins()) < 0);
            assertTrue(timeSeries.getEnds().compareTo(paddedTimeSeries.getEnds()) < 0);
            // check that the padded series have correct and end with correct # of zeros
            // check that padded series begin and end are offset in correct direction
        }
    }

    private TimeSeriesMetric makeTimeSeries(int binCount, TimeSeriesMetricInterval interval) {
        TimeSeriesMetric timeSeries = new TimeSeriesMetric();
        Instant now = Instant.now();
        timeSeries.setBegins(Timestamp.from(interval.addIntervals(now, -binCount)));
        timeSeries.setEnds(Timestamp.from(now));
        timeSeries.setInterval(interval);
        timeSeries.setValues(numbers(binCount));
        return timeSeries;
    }

    private List<Double> numbers(int size) {
        return IntStream.rangeClosed(1, size).boxed().map(Number::doubleValue).toList();
    }
}
