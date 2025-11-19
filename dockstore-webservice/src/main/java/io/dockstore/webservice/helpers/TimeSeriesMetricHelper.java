/* TODO add copyright header */

import static io.dockstore.webservice.core.metrics.TimeSeriesMetric.TimeSeriesMetricInterval.MONTH;
import static io.dockstore.webservice.core.metrics.TimeSeriesMetric.TimeSeriesMetricInterval.WEEK;
import static io.dockstore.webservice.core.metrics.TimeSeriesMetric.TimeSeriesMetricInterval.DAY;
import io.dockstore.webservice.core.metrics.TimeSeriesMetric;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;

class TimeSeriesMetricHelper {

    private final static long SECONDS_IN_DAY = 24L * 3600L;
    private final static long SECONDS_IN_WEEK = 7L * SECONDS_IN_DAY;
    private final static long AVERAGE_SECONDS_IN_MONTH = (long)(365.2425 / 12L * SECONDS_IN_DAY);

    static int calculatePadding(TimeSeriesMetric timeSeries, Instant now) {
        TimeSeriesMetric.TimeSeriesMetricInterval interval = timeSeries.getInterval();
        Instant ends = timeSeries.getEnds().toInstant();
        return switch (interval) {
            case MONTH -> calculatePaddingMonth(ends, now);
            case WEEK -> calculatePaddingWeek(ends, now);
            case DAY -> calculatePaddingDay(ends, now);
            default -> throw new UnsupportedOperationException("time series interval '%' not supported".formatted(interval));
        };
    }

    static int calculatePaddingMonth(Instant ends, Instant now) {
        final int APPROXIMATION_MONTHS = 24;
        if (seconds(ends, now) < APPROXIMATION_MONTHS * AVERAGE_SECONDS_IN_MONTH) {
            // If the time gap is small, calculate the padding by stepping through the months.
            return calculatePaddingBySteppingMonths(ends, now, APPROXIMATION_MONTHS);
        } else {
            // If the time gap is large, return an approximation based on the average length of a month.
            return calculatePaddingUsingFixedInterval(ends, now, AVERAGE_SECONDS_IN_MONTH);
        }
    }

    static int calculatePaddingWeek(Instant ends, Instant now) {
        return calculatePaddingUsingFixedInterval(ends, now, SECONDS_IN_WEEK);
    }

    static int calculatePaddingDay(Instant ends, Instant now) {
        return calculatePaddingUsingFixedInterval(ends, now, SECONDS_IN_DAY);
    }

    static int calculatePaddingUsingFixedInterval(Instant ends, Instant now, long intervalSeconds) {
        return Math.max((int)Math.ceil(seconds(ends, now) / (double)intervalSeconds), 0);
    }

    static int calculatePaddingBySteppingMonths(Instant ends, Instant now, int paddingMax) {
        Instant midpointOfNextMonth = ends.plusSeconds(AVERAGE_SECONDS_IN_MONTH / 2);
        int month = calculateMonth(midpointOfNextMonth);
        int year = calculateYear(midpointOfNextMonth);
        long endsSeconds = ends.getEpochSecond();
        long nowSeconds = now.getEpochSecond();
        int paddingCount = 0;
        while (nowSeconds > endsSeconds && paddingCount < paddingMax) {
            endsSeconds += daysInMonth(month, year) * SECONDS_IN_DAY;
            paddingCount++;
            month++;
            if (month > 12) {
                month = 1;
                year++;
            }
        }
        return paddingCount;
    }

    static int calculateMonth(Instant when) {
        return Date.from(when).getMonth();
    }

    static int calculateYear(Instant when) {
        return Date.from(when).getYear();
    }

    static int daysInMonth(int month, int year) {
        return YearMonth.of(year, month).lengthOfMonth();
    }

    static long seconds(Instant begins, Instant ends) {
        return Duration.between(begins, ends).toSeconds();
    }

    public static double calculateMaximumValueDisplayed(TimeSeriesMetric timeSeries, Instant now, int binCount) {
        // Calculate the required number of bins of padding.
        int paddingCount = calculatePadding(timeSeries, now);
        // Given the calculated padding, compute the number of bins at the end of the time series that should be displayed.
        List<Double> values = timeSeries.getValues();
        int displayCount = Math.min(values.size(), binCount) - paddingCount;
        // Calculate the maximum value of the bins that would be displayed.
        double max = Double.MIN_VALUE;
        for (int i = 0; i < displayCount; i++) {
            max = Math.max(max, values.get(values.size() - i - 1));
        }
        return max;
    }
}
