class TimeSeriesMetricHelper {

    public static int calculatePadding(TimeSeriesMetric timeSeries, Instant now) {
    }

    public static int calculateMax(TimeSeriesMetric timeSeries, Instant now, int displayCount) {
        int padCount = calculatePadding(timeSeries, now);
        double[] values = timeSeries.getValues();
        int valueCount = Math.min(values.length, displayCount) - padCount;
        int max = 0;
        for (int i = 0; i < valueCount; i++) {
            max = Math.max(max, values[values.length - i - 1]);
        }
        return max;
    }
}
