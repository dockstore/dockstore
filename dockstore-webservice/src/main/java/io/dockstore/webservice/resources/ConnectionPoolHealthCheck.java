package io.dockstore.webservice.resources;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.health.HealthCheck;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionPoolHealthCheck extends HealthCheck  {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionPoolHealthCheck.class);

    private final int maxConnections;
    private final Map<String, Gauge> metricGauges;

    public ConnectionPoolHealthCheck(int maxConnections, Map<String, Gauge> metricGauges) {
        this.metricGauges = metricGauges;
        this.maxConnections = maxConnections;
    }

    @Override
    protected Result check() throws Exception {
        final int activeConnections = (int)metricGauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.active").getValue();
        final int sizeConnections = (int)metricGauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.size").getValue();
        final int idleConnections = (int)metricGauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.idle").getValue();
        final double loadConnections = (double) activeConnections / maxConnections;

        if (activeConnections == maxConnections) {
            LOG.info("size: {}, active: {}, idle: {}, calculatedLoad: {}", sizeConnections, activeConnections, idleConnections, loadConnections);
        }
        // All connections being in use doesn't necessarily mean the container is unhealthy; it's probably just processing many requests.
        // See discussion at https://ucsc-cgl.atlassian.net/browse/SEAB-4879. Always return healthy for now; perhaps we'll improve it to
        // detect unhealthiness in the future.
        return Result.healthy();
    }
}
