package io.dockstore.webservice;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;
import io.dockstore.webservice.DockstoreWebserviceConfiguration.ExternalConfig;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

public class CloudWatchMetricsReporter extends ScheduledReporter {

    private static final Logger LOG = LoggerFactory.getLogger(CloudWatchMetricsReporter.class);
    private CloudWatchClient cw = null;
    private String namespace = null;

    protected CloudWatchMetricsReporter(MetricRegistry registry, String name, MetricFilter filter, TimeUnit rateUnit,
        TimeUnit durationUnit, ExternalConfig config) {
        super(registry, name, filter, rateUnit, durationUnit);
        initialize(config);
    }

    private void initialize(ExternalConfig config) {
        try {
            this.cw = CloudWatchClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
            this.namespace = config.getHostname() + "_LogMetrics";
        } catch (SdkClientException e) {
            LOG.error("Unable to create CloudWatchClient", e);
        }
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters, SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters,
        SortedMap<String, Timer> timers) {

        List<MetricDatum> metricDataList = new ArrayList<>();
        gauges.forEach((key, value) -> {
            // start with Guages but we should add more later
            // this can be hooked as a reporter in dropwizard metrics to self-report rather than wait for a healthcheck call
            // also put metrics into cloudwatch
            // from https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_cloudwatch_code_examples.html

            String time = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
            Instant instant = Instant.parse(time);
            MetricDatum metricDatum = getMetricDatum(key, (double) value.getValue(), instant);
            metricDataList.add(metricDatum);
        });

        PutMetricDataRequest request = PutMetricDataRequest.builder()
            .namespace(namespace)
            .metricData(metricDataList)
            .build();

        try {
            cw.putMetricData(request);
            LOG.info("Added metric values for for metrics in " + namespace);
        } catch (Exception e) {
            LOG.info("Unable to add metric values for for metrics in " + namespace, e);
        }
    }

    private static MetricDatum getMetricDatum(String metricName, double value, Instant instant) {
        return MetricDatum.builder()
            .metricName(metricName)
            .unit(StandardUnit.NONE)
            .value(value)
            .timestamp(instant)
            .build();
    }
}
