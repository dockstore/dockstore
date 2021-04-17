package io.dockstore.webservice.resources;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import io.dockstore.webservice.SimpleAuthorizer;
import io.dockstore.webservice.core.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every request will be filtered through here to determine if an admin user is making the request. If it is an admin,
 * and it is a request that requires admin privileges, then the username, method, and endpoint path will be logged.
 */
@Provider
public class AdminPrivilegesFilter implements ContainerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(AdminPrivilegesFilter.class);

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (requestContext != null) {
            final Principal principal = requestContext.getSecurityContext().getUserPrincipal();
            if (principal instanceof User) {
                final User user = (User)principal;
                if (user.getIsAdmin() && requestRequiresAdminRole()) {
                    final String logMessage = "Admin " + user.getUsername() + " making " + requestContext.getMethod()
                            + " privileged request at " + requestContext.getUriInfo().getPath();
                    LOG.info(logMessage);
                }
            }
        }
    }

    private boolean requestRequiresAdminRole() {
        final Method resourceMethod = resourceInfo.getResourceMethod();
        if (resourceMethod != null) { // JavaDoc says it can be null, hmm
            final RolesAllowed rolesAllowedAnnotation = resourceMethod.getAnnotation(RolesAllowed.class);
            if (rolesAllowedAnnotation != null) {
                return Arrays.stream(rolesAllowedAnnotation.value()).anyMatch(role -> SimpleAuthorizer.ADMIN.equals(role));
            }
        }
        return false;
    }
}
