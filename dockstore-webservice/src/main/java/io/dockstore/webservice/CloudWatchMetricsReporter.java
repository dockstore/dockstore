package io.dockstore.webservice;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;
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
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
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
            // can also use the following to submit to cloudwatch locally
            // .credentialsProvider(DefaultCredentialsProvider.create())
            this.cw = CloudWatchClient.builder()
                .credentialsProvider(ContainerCredentialsProvider.builder().build())
                .build();
            this.namespace = config.getHostname() + "_LogMetrics";
        } catch (SdkClientException e) {
            LOG.error("Unable to create CloudWatchClient", e);
        }
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters, SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters,
        SortedMap<String, Timer> timers) {
        // start with Gauges but we should add the rest later
        if (!counters.isEmpty() || !histograms.isEmpty() || !meters.isEmpty() || !timers.isEmpty()) {
            throw new UnsupportedOperationException("If applicable, consider how a new metric type should be reported to CloudWatch");
        }

        if (cw == null) {
            LOG.debug("CloudWatchClient client init, unable to add metric values for metrics in " + namespace);
            return;
        }

        // inspired from https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_cloudwatch_code_examples.html
        List<MetricDatum> metricDataList = new ArrayList<>();
        gauges.forEach((name, gauge) -> {
            String time = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
            Instant instant = Instant.parse(time);
            if (gauge.getValue() instanceof Number number) {
                MetricDatum metricDatum;
                if (gauge instanceof RatioGauge) {
                    metricDatum = getMetricDatum(name, number.doubleValue(), instant, StandardUnit.PERCENT);
                } else {
                    metricDatum = getMetricDatum(name, number.doubleValue(), instant, StandardUnit.NONE);
                }
                metricDataList.add(metricDatum);
            }
        });

        if (!metricDataList.isEmpty()) {
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(namespace)
                .metricData(metricDataList)
                .build();

            try {
                cw.putMetricData(request);
                LOG.debug("Added metric values for metrics in {}", namespace);
            } catch (SdkException e) {
                LOG.info("AWS issue, unable to add metric values for metrics in " + namespace, e);
            }
        }
    }

    private static MetricDatum getMetricDatum(String metricName, double value, Instant instant, StandardUnit unit) {
        return MetricDatum.builder()
            .metricName(metricName)
            .unit(unit)
            .value(value)
            .timestamp(instant)
            .build();
    }
}
