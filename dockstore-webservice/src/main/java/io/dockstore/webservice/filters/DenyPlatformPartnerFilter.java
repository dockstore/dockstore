/*
 * Copyright 2025 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.filters;

import io.dockstore.webservice.SimpleAuthorizer;
import io.dockstore.webservice.core.User;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.security.Principal;
import java.text.MessageFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deny all requests by a "platformPartner" user to an endpoint that does not have a @RolesAllowed annotation.
 */
@Provider
public class DenyPlatformPartnerFilter implements ContainerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(DenyPlatformPartnerFilter.class);

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (requestContext != null) {
            final Principal principal = requestContext.getSecurityContext().getUserPrincipal();
            if (principal instanceof User user) {
                if (user.isPlatformPartner()) {
                    final Method resourceMethod = resourceInfo.getResourceMethod();
                    if (resourceMethod != null) {
                        final RolesAllowed rolesAllowedAnnotation = resourceMethod.getAnnotation(RolesAllowed.class);
                        if (rolesAllowedAnnotation == null) {
                            throw new ForbiddenException();
                        }
                    }
                }
            }
        }
    }
}
