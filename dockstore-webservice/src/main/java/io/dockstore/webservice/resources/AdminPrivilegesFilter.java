package io.dockstore.webservice.resources;

import io.dockstore.webservice.SimpleAuthorizer;
import io.dockstore.webservice.core.User;
import jakarta.annotation.security.RolesAllowed;
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
                    final String logMessage = MessageFormat.format("Admin {0} id {1} making {2} privileged request at {3}",
                            user.getUsername(), Long.toString(user.getId()), requestContext.getMethod(), requestContext.getUriInfo().getPath());
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
                final String[] value = rolesAllowedAnnotation.value();
                // Ignore methods that work with either curator or admin
                return value.length == 1 && SimpleAuthorizer.ADMIN.equals(value[0]);
            }
        }
        return false;
    }
}
