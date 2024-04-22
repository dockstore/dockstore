package io.dockstore.webservice.resources;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.health.HealthCheck;
import io.dockstore.webservice.DockstoreWebserviceConfiguration.ExternalConfig;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

public class ConnectionPoolHealthCheck extends HealthCheck  {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionPoolHealthCheck.class);

    private final int maxConnections;
    private final Map<String, Gauge> metricGauges;
    private CloudWatchClient cw = null;
    private String namespace = null;

    public ConnectionPoolHealthCheck(ExternalConfig configuration, int maxConnections, Map<String, Gauge> metricGauges) {
        this.maxConnections = maxConnections;
        this.metricGauges = metricGauges;

        this.cw = CloudWatchClient.builder()
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
        this.namespace = configuration.getHostname() + "_LogMetrics";
    }

    @Override
    protected Result check() throws Exception {
        final int activeConnections = (int)metricGauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.active").getValue();
        final int sizeConnections = (int)metricGauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.size").getValue();
        final int idleConnections = (int)metricGauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.idle").getValue();
        final double loadConnections = (double) activeConnections / maxConnections;

        // this can be hooked as a reporter in dropwizard metrics to self-report rather than wait for a healthcheck call
        // also put metrics into cloudwatch
        // from https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_cloudwatch_code_examples.html

        String time = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        Instant instant = Instant.parse(time);


        MetricDatum active = getMetricDatum("active", activeConnections, instant);
        MetricDatum size = getMetricDatum("size", sizeConnections, instant);
        MetricDatum idle = getMetricDatum("idle", idleConnections, instant);
        MetricDatum load = getMetricDatum("calculatedLoad", loadConnections, instant);

        List<MetricDatum> metricDataList = new ArrayList<>();
        metricDataList.add(active);
        metricDataList.add(size);
        metricDataList.add(idle);
        metricDataList.add(load);

        PutMetricDataRequest request = PutMetricDataRequest.builder()
            .namespace(namespace)
            .metricData(metricDataList)
            .build();

        try {
            cw.putMetricData(request);
            LOG.info("Added metric values for for metrics in " + namespace);
        }  catch (Exception e) {
            LOG.info("Unable to add metric values for for metrics in " + namespace, e);
        }

        if (activeConnections == maxConnections) {
            LOG.info("size: {}, active: {}, idle: {}, calculatedLoad: {}", sizeConnections, activeConnections, idleConnections, loadConnections);
            return Result.unhealthy("No database connections available");
        } else {
            return Result.healthy();
        }
    }

    private static MetricDatum getMetricDatum(String metricName, double value, Instant instant) {
        return MetricDatum.builder()
            .metricName("io.dropwizard.db.ManagedPooledDataSource.hibernate." + metricName)
            .unit(StandardUnit.NONE)
            .value(value)
            .timestamp(instant)
            .build();
    }
}
