package io.dockstore.webservice.resources;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.health.HealthCheck;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

public class ConnectionPoolHealthCheck extends HealthCheck  {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionPoolHealthCheck.class);

    private final int maxConnections;
    private final Map<String, Gauge> metricGauges;
    private final CloudWatchClient cw;
    private final String cluster;
    private final String containerID;
    private final String namespace;

    public ConnectionPoolHealthCheck(int maxConnections, Map<String, Gauge> metricGauges) {
        this.maxConnections = maxConnections;
        this.metricGauges = metricGauges;

        this.cw = CloudWatchClient.builder()
            .credentialsProvider(ProfileCredentialsProvider.create())
            .build();
        String ecsContainerMetadataUri = System.getenv("ECS_CONTAINER_METADATA_URI");
        try {
            String ecsMetadata = IOUtils.toString(new URL(ecsContainerMetadataUri), StandardCharsets.UTF_8);
            Map<String, String> dto = new Gson().fromJson(ecsMetadata, Map.class);
            this.cluster = dto.get("Cluster");
            this.containerID = dto.get("ContainerID");
            this.namespace = "ECS/" + cluster + "/" + containerID;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    protected Result check() throws Exception {
        final int activeConnections = (int)metricGauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.active").getValue();
        final int sizeConnections = (int)metricGauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.size").getValue();
        final int idleConnections = (int)metricGauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.idle").getValue();
        final double loadConnections = (double) activeConnections / maxConnections;

        // this can be hooked as a reporter in dropwizard metrics to self-report rather than wait for a healthcheck call
        if (cluster != null && containerID != null) {
            // also put metrics into cloudwatch
            // from https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_cloudwatch_code_examples.html

            String time = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
            Instant instant = Instant.parse(time);

            Dimension[] dimensions = {Dimension.builder().name("ClusterName").value(cluster).build(),
                Dimension.builder().name("ContainerID").value(containerID).build()};

            MetricDatum active = getMetricDatum("active", activeConnections, instant, dimensions);
            MetricDatum size = getMetricDatum("size", sizeConnections, instant, dimensions);
            MetricDatum idle = getMetricDatum("idle", idleConnections, instant, dimensions);
            MetricDatum load = getMetricDatum("calculatedLoad", loadConnections, instant, dimensions);

            List<MetricDatum> metricDataList = new ArrayList<>();
            metricDataList.add(active);
            metricDataList.add(size);
            metricDataList.add(idle);
            metricDataList.add(load);

            PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(namespace)
                .metricData(metricDataList)
                .build();

            cw.putMetricData(request);
            LOG.debug("Added metric values for for metrics in " + namespace);
        }

        if (activeConnections == maxConnections) {
            LOG.info("size: {}, active: {}, idle: {}, calculatedLoad: {}", sizeConnections, activeConnections, idleConnections, loadConnections);
            return Result.unhealthy("No database connections available");
        } else {
            return Result.healthy();
        }
    }

    private static MetricDatum getMetricDatum(String metricName, double value, Instant instant, Dimension[] dimensions) {
        return MetricDatum.builder()
            .metricName("io.dropwizard.db.ManagedPooledDataSource.hibernate." + metricName)
            .unit(StandardUnit.NONE)
            .value(value)
            .timestamp(instant)
            .dimensions(dimensions)
            .build();
    }
}
