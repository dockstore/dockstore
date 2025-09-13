package io.dockstore.webservice.resources;

import static io.dockstore.webservice.resources.LambdaEventResource.ACCESS_CONTROL_EXPOSE_HEADERS;
import static io.dockstore.webservice.resources.LambdaEventResource.X_TOTAL_COUNT;
import static io.dockstore.webservice.resources.ResourceConstants.JWT_SECURITY_DEFINITION_NAME;
import static io.dockstore.webservice.resources.ResourceConstants.MAX_PAGINATION_LIMIT;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_LIMIT;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_LIMIT_TEXT;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_OFFSET_TEXT;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.AbstractNotification;
import io.dockstore.webservice.core.GitHubAppNotification;
import io.dockstore.webservice.core.PublicNotification;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.UserNotification;
import io.dockstore.webservice.jdbi.GitHubAppNotificationDAO;
import io.dockstore.webservice.jdbi.NotificationDAO;
import io.dockstore.webservice.jdbi.UserNotificationDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/curation")
@Api("/curation")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "curation", description = ResourceConstants.CURATION)
public class NotificationResource {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationResource.class);

    // interface between the endpoint and the database
    private final NotificationDAO notificationDAO;
    private final UserNotificationDAO userNotificationDAO;
    private final GitHubAppNotificationDAO gitHubAppNotificationDAO;

    // constructor
    public NotificationResource(SessionFactory sessionFactory) {
        this.notificationDAO = new NotificationDAO(sessionFactory);
        this.userNotificationDAO = new UserNotificationDAO(sessionFactory);
        this.gitHubAppNotificationDAO = new GitHubAppNotificationDAO(sessionFactory);
    }

    // get a notification by its id
    @GET
    @Path("/notifications/{id}")
    @UnitOfWork
    @Operation(operationId = "getNotification", description = "Return the notification with given id")
    @ApiOperation(value = "Return the notification with given id", notes = "NO Authentication", responseContainer = "List", response = PublicNotification.class)
    public PublicNotification getNotification(@PathParam("id") Long id) {
        PublicNotification notification = notificationDAO.findById(id);
        throwErrorIfNull(notification);
        return notification;
    }

    // get all active notifications
    @GET
    @Path("/notifications")
    @UnitOfWork
    @Operation(operationId = "getActiveNotifications", description = "Return all active notifications")
    @ApiOperation(value = "Return all active notifications", notes = "NO Authentication", responseContainer = "List", response = PublicNotification.class)
    public List<PublicNotification> getActiveNotifications() {
        return notificationDAO.getActiveNotifications();
    }

    // post a new notification
    @POST
    @Path("/notifications")
    @UnitOfWork
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"curator", "admin"})
    @Operation(operationId = "createNotification", description = "Create a notification", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Create a notification", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)},
            notes = "Curator/admin only", response = PublicNotification.class)
    public PublicNotification createNotification(@ApiParam(value = "Notification to create", required = true) @Parameter(name = "notification", description = "Notification to create", required = true) PublicNotification notification) {
        long id = notificationDAO.create(notification);
        return notificationDAO.findById(id);
    }

    // delete a notification by its id
    @DELETE
    @Path("/notifications/{id}")
    @UnitOfWork
    @RolesAllowed({ "curator", "admin" })
    @Operation(operationId = "deleteNotification", description = "Delete a notification", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Delete a notification", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Curator/admin only")
    public void deleteNotification(@ApiParam(value = "Notification to delete", required = true) @PathParam("id") Long id) {
        PublicNotification notification = notificationDAO.findById(id);
        throwErrorIfNull(notification);
        notificationDAO.delete(notification);
    }

    @PUT
    @Path("/notifications/{id}")
    @UnitOfWork
    @RolesAllowed({ "curator", "admin" })
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateNotification", description = "Update a notification", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Update a notification", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)},
            notes = "Curator/admin only", response = PublicNotification.class)
    public PublicNotification updateNotification(@ApiParam(value = "Notification to update", required = true) @PathParam("id") long id,
                                           @ApiParam(value = "Updated version of notification", required = true) PublicNotification notification) {
        if (id != notification.getId()) {
            String msg = "ID in path and notification param must match";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }
        return notificationDAO.update(notification);
    }

    @GET
    @Path("/notifications/user")
    @UnitOfWork
    @Operation(operationId = "getUserNotifications", description = "Return all notifications for a user", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public List<UserNotification> getUserNotifications(@Parameter(hidden = true, name = "user") @Auth User user, @Parameter(description = PAGINATION_OFFSET_TEXT) @QueryParam("offset") @DefaultValue("0") @Min(0) int offset,
        @Parameter(description = PAGINATION_LIMIT_TEXT) @DefaultValue(PAGINATION_LIMIT) @QueryParam("limit") @Min(1) @Max(MAX_PAGINATION_LIMIT) int limit,
        @Context HttpServletResponse response) {
        response.addHeader(X_TOTAL_COUNT, String.valueOf(userNotificationDAO.getCountByUser(user)));
        response.addHeader(ACCESS_CONTROL_EXPOSE_HEADERS, X_TOTAL_COUNT);
        return userNotificationDAO.findByUser(user, offset, limit);
    }

    // delete a notification by its id
    @DELETE
    @Path("/notifications/user/{id}")
    @UnitOfWork
    @Operation(operationId = "deleteUserNotification", description = "Delete a user notification", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public void deleteUserNotification(@Parameter(description = "Notification to delete", required = true) @PathParam("id") Long id, @Parameter(hidden = true, name = "user") @Auth User user) {
        UserNotification notification = userNotificationDAO.findById(id);
        throwErrorIfNull(notification);

        if (notification.getUser().getId() != user.getId()) {
            throw new CustomWebApplicationException("User is not authorized to delete this notification", HttpStatus.SC_FORBIDDEN);
        }
        userNotificationDAO.delete(notification);
    }

    // hide a notification by its id
    @PATCH
    @Path("/notifications/user/{id}/hide")
    @UnitOfWork
    @Operation(operationId = "hideUserNotification",  description = "Hide a user notification", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public void hideUserNotification(@Parameter(description = "Notification to hide", required = true) @PathParam("id") Long id, @Parameter(hidden = true, name = "user") @Auth User user) {
        UserNotification notification = userNotificationDAO.findById(id);
        throwErrorIfNull(notification);

        if (notification.getUser().getId() != user.getId()) {
            throw new CustomWebApplicationException("User is not authorized to hide this notification", HttpStatus.SC_FORBIDDEN);
        }
        notification.setHidden(true);
    }

    @GET
    @Path("/notifications/user/githubapp")
    @UnitOfWork
    @Operation(operationId = "getGitHubAppNotifications", description = "Return all GitHub App notifications for a user", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public List<GitHubAppNotification> getUserGitHubAppNotifications(@Parameter(hidden = true, name = "user") @Auth User user, @Parameter(description = PAGINATION_OFFSET_TEXT) @QueryParam("offset") @DefaultValue("0") @Min(0) int offset,
        @Parameter(description = PAGINATION_LIMIT_TEXT) @DefaultValue(PAGINATION_LIMIT) @QueryParam("limit") @Min(1) @Max(MAX_PAGINATION_LIMIT) int limit,
        @Context HttpServletResponse response) {
        response.addHeader(X_TOTAL_COUNT, String.valueOf(gitHubAppNotificationDAO.getCountByUser(user)));
        response.addHeader(ACCESS_CONTROL_EXPOSE_HEADERS, X_TOTAL_COUNT);
        return gitHubAppNotificationDAO.findByUser(user, offset, limit);
    }

    private void throwErrorIfNull(AbstractNotification notification) {
        if (notification == null) {
            String msg = "Notification not found";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
    }

}
