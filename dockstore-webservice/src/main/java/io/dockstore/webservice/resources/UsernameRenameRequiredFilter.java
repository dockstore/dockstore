/*
 * Copyright 2021 OICR and UCSC
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
 */

package io.dockstore.webservice.resources;

import static io.dockstore.webservice.Constants.USERNAME_CHANGE_REQUIRED;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.User;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import java.security.Principal;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UsernameRenameRequired
@Provider
public class UsernameRenameRequiredFilter implements ContainerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(UsernameRenameRequiredFilter.class);

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (requestContext != null) {
            final Principal principal = requestContext.getSecurityContext().getUserPrincipal();
            if (principal instanceof User) {
                final User user = (User)principal;
                if (user.isUsernameChangeRequired()) {
                    throw new CustomWebApplicationException(USERNAME_CHANGE_REQUIRED, HttpStatus.SC_UNAUTHORIZED);
                }
            }
        }
    }

}
