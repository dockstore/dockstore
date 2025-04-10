/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice;

import io.dockstore.webservice.core.User;
import io.dropwizard.auth.Authorizer;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public class SimpleAuthorizer implements Authorizer<User> {

    public static final String ADMIN = "admin";
    public static final String CURATOR = "curator";
    public static final String PLATFORM_PARTNER = "platformPartner";
    public static final String METRICS_ROBOT = "metricsRobot";
    public static final List<String> ROLES = List.of(ADMIN, CURATOR, PLATFORM_PARTNER, METRICS_ROBOT);
    private static final Logger LOG = LoggerFactory.getLogger(SimpleAuthorizer.class);

    @Override
    public boolean authorize(User principal, String role, @Nullable ContainerRequestContext requestContext) {
        if (principal.isBanned()) {
            LOG.error("Denying access to %s".formatted(principal));
            return false;
        }
        if (ADMIN.equalsIgnoreCase(role)) {
            return principal.getIsAdmin();
        } else if (CURATOR.equalsIgnoreCase(role)) {
            return principal.isCurator();
        } else if (PLATFORM_PARTNER.equalsIgnoreCase(role)) {
            return principal.isPlatformPartner();
        } else if (METRICS_ROBOT.equalsIgnoreCase(role)) {
            return principal.isMetricsRobot();
        } else {
            return true;
        }
    }
}
