/*
 * Copyright 2023 OICR and UCSC
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

import io.dockstore.webservice.core.AuthenticatedUser;
import io.dockstore.webservice.core.User;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;

/**
 * Filter that saves the authenticated user of an API request.
 *
 * The standard DropWizard/Jersey way to handle this is to inject the SecurityContext with @Context, but we need this information
 * in the ObjectMapper, and I couldn't figure out a way to inject there.
 */
public class AuthenticatedUserFilter implements ContainerRequestFilter, ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        AuthenticatedUser.setUser(null);
        final SecurityContext securityContext = requestContext.getSecurityContext();
        if (securityContext != null && securityContext.getUserPrincipal() instanceof User user) {
            AuthenticatedUser.setUser(user);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // Remove the ThreadLocal in case Jersey reuses the thread. This was suggested by SonarCloud
        AuthenticatedUser.remove();
    }
}
