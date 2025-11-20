package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.webservice.core.metrics.TimeSeriesMetric;
import io.dockstore.webservice.core.metrics.TimeSeriesMetric.TimeSeriesMetricInterval;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class TimeSeriesMetricHelperTest {

    @Test
    public void testPadIntervals() {

        for (TimeSeriesMetricInterval interval: TimeSeriesMetricInterval.values()) {

            TimeSeriesMetric timeSeries = makeTimeSeries(40, interval, Instant.now());

            Instant now = timeSeries.getEnds().toInstant();
            TimeSeriesMetric paddedTimeSeries = TimeSeriesMetricHelper.pad(timeSeries, now);
            assertEquals(timeSeries.getBegins(), paddedTimeSeries.getBegins());
            assertEquals(timeSeries.getEnds(), paddedTimeSeries.getEnds());
            checkValues(paddedTimeSeries, 0);

            now = timeSeries.getEnds().toInstant().plus(1, ChronoUnit.NANOS);
            paddedTimeSeries = TimeSeriesMetricHelper.pad(timeSeries, now);
            assertTrue(timeSeries.getBegins().compareTo(paddedTimeSeries.getBegins()) < 0);
            assertTrue(timeSeries.getEnds().compareTo(paddedTimeSeries.getEnds()) < 0);
            checkValues(paddedTimeSeries, 1);

            now = interval.add(now, 1);
            paddedTimeSeries = TimeSeriesMetricHelper.pad(timeSeries, now);
            assertTrue(timeSeries.getBegins().compareTo(paddedTimeSeries.getBegins()) < 0);
            assertTrue(timeSeries.getEnds().compareTo(paddedTimeSeries.getEnds()) < 0);
            checkValues(paddedTimeSeries, 2);
        }
    }

    @Test
    public void testPadMonthlyInterval() {
        Instant january1Midnight = Instant.parse("2025-01-01T00:00:00Z");
        TimeSeriesMetric timeSeries = makeTimeSeries(36, TimeSeriesMetricInterval.MONTH, january1Midnight);

        Instant february1Minus1Minute = Instant.parse("2025-01-31T23:59:59Z");
        TimeSeriesMetric paddedTimeSeries = TimeSeriesMetricHelper.pad(timeSeries, february1Minus1Minute);
        assertEquals(seconds(timeSeries), seconds(paddedTimeSeries), ChronoUnit.DAYS.getDuration().toSeconds() * 2);
        assertEquals(Duration.of(31, ChronoUnit.DAYS), deltaBegins(timeSeries, paddedTimeSeries));
        assertEquals(Duration.of(31, ChronoUnit.DAYS), deltaEnds(timeSeries, paddedTimeSeries));
        assertEquals(timeSeries.getValues().size(), paddedTimeSeries.getValues().size());
        checkValues(paddedTimeSeries, 1);
    }

    @Test
    public void testMax() {
        TimeSeriesMetric timeSeries = makeTimeSeries(List.of(4., 2., 3., 1.), TimeSeriesMetricInterval.DAY, Instant.now());
        assertEquals(0, TimeSeriesMetricHelper.max(timeSeries, 0));
        assertEquals(1, TimeSeriesMetricHelper.max(timeSeries, 1));
        assertEquals(3, TimeSeriesMetricHelper.max(timeSeries, 2));
        assertEquals(3, TimeSeriesMetricHelper.max(timeSeries, 3));
        assertEquals(4, TimeSeriesMetricHelper.max(timeSeries, 4));
        assertEquals(4, TimeSeriesMetricHelper.max(timeSeries, 5));
    }

    private void checkValues(TimeSeriesMetric timeSeries, int zeroCount) {
        List<Double> values = timeSeries.getValues();
        int size = values.size();
        for (int i = 0; i < size; i++) {
            double value = values.get(i);
            if (i < size - zeroCount) {
                assertEquals((double)(i + 1 + zeroCount), value);
            } else {
                assertEquals(0., value);
            }
        }
    }

    private TimeSeriesMetric makeTimeSeries(int binCount, TimeSeriesMetricInterval interval, Instant ends) {
        return makeTimeSeries(numbers(binCount), interval, ends);
    }

    private TimeSeriesMetric makeTimeSeries(List<Double> values, TimeSeriesMetricInterval interval, Instant ends) {
        TimeSeriesMetric timeSeries = new TimeSeriesMetric();
        timeSeries.setValues(values);
        timeSeries.setInterval(interval);
        timeSeries.setBegins(Timestamp.from(interval.add(ends, -values.size())));
        timeSeries.setEnds(Timestamp.from(ends));
        return timeSeries;
    }

    private List<Double> numbers(int size) {
        return IntStream.rangeClosed(1, size).boxed().map(Number::doubleValue).toList();
    }

    private Duration duration(TimeSeriesMetric timeSeries) {
        return delta(timeSeries.getBegins(), timeSeries.getEnds());
    }

    private long seconds(TimeSeriesMetric timeSeries) {
        return duration(timeSeries).toSeconds();
    }

    private Duration deltaBegins(TimeSeriesMetric from, TimeSeriesMetric to) {
        return delta(from.getBegins(), to.getBegins());
    }

    private Duration deltaEnds(TimeSeriesMetric from, TimeSeriesMetric to) {
        return delta(from.getEnds(), to.getEnds());
    }

    private Duration delta(Timestamp from, Timestamp to) {
        return Duration.between(from.toInstant(), to.toInstant());
    }
}
