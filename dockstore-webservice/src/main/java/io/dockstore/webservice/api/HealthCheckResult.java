/*
 * Copyright 2022 OICR and UCSC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.dockstore.webservice.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

/**
 * This is an object to encapsulate Dockstore health check information.
 */
@ApiModel("HealthCheckResult")
public class HealthCheckResult {
    private String healthCheckName;
    private boolean healthy;

    public HealthCheckResult(String healthCheckName, boolean healthy) {
        this.healthCheckName = healthCheckName;
        this.healthy = healthy;
    }

    @JsonProperty
    public String getHealthCheckName() {
        return healthCheckName;
    }

    public void setHealthCheckName(String healthCheckName) {
        this.healthCheckName = healthCheckName;
    }

    @JsonProperty
    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }
}
