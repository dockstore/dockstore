package io.dockstore.webservice.resources;

import java.io.IOException;
import java.security.Principal;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import io.dockstore.webservice.core.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class AdminPrivilegesFilter implements ContainerResponseFilter {
    private static final Logger LOG = LoggerFactory.getLogger(AdminPrivilegesFilter.class);
    @Context
    UriInfo uriInfo;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        if (requestContext != null) {
            Principal principal = requestContext.getSecurityContext().getUserPrincipal();
            if (principal != null) {
                User user = (User)principal;
                if (user.getIsAdmin()) {
                    String logMessage = "Admin " + user.getUsername() + " making " + requestContext.getMethod() + " request at " + requestContext.getUriInfo().getPath();
                    LOG.info(logMessage);
                }
            }
        }
    }
}
