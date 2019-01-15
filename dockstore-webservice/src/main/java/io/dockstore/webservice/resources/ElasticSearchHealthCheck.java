package io.dockstore.webservice.resources;

import javax.ws.rs.core.Response;

import com.codahale.metrics.health.HealthCheck;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.resources.proposedGA4GH.ToolsExtendedApi;

/**
 * Checks that elastic search is healthy
 */
public class ElasticSearchHealthCheck extends HealthCheck {
    private static final int LOWER_BOUND_SUCCESS_STATUS = 200;
    private static final int UPPER_BOUND_SUCCESS_STATUS = 300;
    private final ToolsExtendedApi toolsExtendedApi;

    public ElasticSearchHealthCheck(ToolsExtendedApi toolsExtendedApi) {
        this.toolsExtendedApi = toolsExtendedApi;
    }

    @Override
    protected Result check() throws Exception {
        // If elastic search is up with a valid index this should return healthy
        Response response;
        try {
            response = toolsExtendedApi.toolsIndexSearch(null, null, null);
        } catch (CustomWebApplicationException ex) {
            ex.printStackTrace();
            return Result.unhealthy("Error contacting Elastic Search: " + ex.getResponse().getEntity());
        } catch (Exception ex) {
            ex.printStackTrace();
            return Result.unhealthy("Error contacting Elastic Search: " + ex.getMessage());
        }

        if (response.getStatus() >= LOWER_BOUND_SUCCESS_STATUS && response.getStatus() < UPPER_BOUND_SUCCESS_STATUS) {
            return Result.healthy();
        } else {
            return Result.unhealthy("Error contacting Elastic Search, got status code '" + response.getStatus() + "'.");
        }
    }
}
