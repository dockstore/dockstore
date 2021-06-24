package io.dockstore.webservice.resources;

import com.codahale.metrics.health.HealthCheck;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.resources.proposedGA4GH.ToolsExtendedApi;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks that elastic search is healthy
 */
public class ElasticSearchHealthCheck extends HealthCheck {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchHealthCheck.class);

    private final ToolsExtendedApi toolsExtendedApi;

    public ElasticSearchHealthCheck(ToolsExtendedApi toolsExtendedApi) {
        this.toolsExtendedApi = toolsExtendedApi;
    }

    @Override
    protected Result check() throws Exception {
        String baseMessage = "Error contacting Elastic Search";
        // If elastic search is up with a valid index this should return healthy
        Response response;
        try {
            response = toolsExtendedApi.toolsIndexSearch(null, null, null);
        } catch (CustomWebApplicationException ex) {
            LOG.info(baseMessage, ex);
            return Result.unhealthy(baseMessage + ": " + ex.getResponse().getEntity());
        } catch (Exception ex) {
            LOG.info(baseMessage, ex);
            return Result.unhealthy(baseMessage + ": " + ex.getMessage());
        }

        if (response.getStatus() >= Response.Status.OK.getStatusCode() && response.getStatus() < Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
            return Result.healthy();
        } else {
            return Result.unhealthy(baseMessage + ", got status code '" + response.getStatus() + "'.");
        }
    }
}
