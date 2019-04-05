package io.dockstore.webservice.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

/**
 * This is an object to encapsulate a user's limits. It is used for both requests
 * and responses.
 *
 * It is separated out from the User object because the information is only
 * accessible by admins and curators.
 */
@ApiModel("Limits")
public class Limits {

    private Integer hostedEntryCountLimit;

    private Integer hostedEntryVersionLimit;

    @JsonProperty
    public Integer getHostedEntryCountLimit() {
        return hostedEntryCountLimit;
    }

    @JsonProperty
    public Integer getHostedEntryVersionLimit() {
        return hostedEntryVersionLimit;
    }

    public void setHostedEntryCountLimit(Integer hostedEntryCountLimit) {
        this.hostedEntryCountLimit = hostedEntryCountLimit;
    }

    public void setHostedEntryVersionLimit(Integer hostedEntryVersionLimit) {
        this.hostedEntryVersionLimit = hostedEntryVersionLimit;
    }
}
