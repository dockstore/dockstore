package io.dockstore.webservice.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.common.Partner;
import io.swagger.annotations.ApiModel;

/**
 * This is an object to encapsulate the privilege request. Used for requests in
 * /users/{userid}/privileges
 */
@ApiModel("PrivilegeRequest")
public class PrivilegeRequest {
    private boolean admin;
    private boolean curator;
    private Partner platformPartner;
    private Partner metricsRobotPartner;

    @JsonProperty
    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    @JsonProperty
    public boolean isCurator() {
        return curator;
    }

    public void setCurator(boolean curator) {
        this.curator = curator;
    }

    @JsonProperty
    public Partner getPlatformPartner() {
        return platformPartner;
    }

    public void setPlatformPartner(Partner platformPartner) {
        this.platformPartner = platformPartner;
    }

    @JsonProperty
    public Partner getMetricsRobotPartner() {
        return metricsRobotPartner;
    }

    public void setMetricsRobotPartner(Partner metricsRobotPartner) {
        this.metricsRobotPartner = metricsRobotPartner;
    }
}
