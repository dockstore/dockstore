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

package io.dockstore.webservice.resources;

import static io.dockstore.webservice.Constants.USERNAME_CONTAINS_KEYWORD_PATTERN;
import static io.dockstore.webservice.resources.ResourceConstants.APPEASE_SWAGGER_PATCH;
import static io.dockstore.webservice.resources.ResourceConstants.JWT_SECURITY_DEFINITION_NAME;
import static io.dockstore.webservice.resources.ResourceConstants.MAX_PAGINATION_LIMIT;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_LIMIT;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_LIMIT_TEXT;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_OFFSET_TEXT;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.Lists;
import io.dockstore.common.EntryType;
import io.dockstore.common.HttpStatusMessageConstants;
import io.dockstore.common.Registry;
import io.dockstore.common.Repository;
import io.dockstore.common.SourceControl;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.api.Limits;
import io.dockstore.webservice.api.PrivilegeRequest;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.CloudInstance;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.DeletedUsername;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.EntryUpdateTime;
import io.dockstore.webservice.core.ExtendedUserData;
import io.dockstore.webservice.core.LambdaEvent;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.OrganizationUpdateTime;
import io.dockstore.webservice.core.OrganizationUser;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.TokenViews;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.database.EntryLite;
import io.dockstore.webservice.core.database.UserInfo;
import io.dockstore.webservice.core.database.WorkflowSummary;
import io.dockstore.webservice.helpers.DeletedUserHelper;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface.GitRepo;
import io.dockstore.webservice.helpers.TransactionHelper;
import io.dockstore.webservice.jdbi.AppToolDAO;
import io.dockstore.webservice.jdbi.BioWorkflowDAO;
import io.dockstore.webservice.jdbi.DeletedUsernameDAO;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.LambdaEventDAO;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dockstore.webservice.jdbi.ServiceDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.dropwizard.auth.Auth;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import jakarta.ws.rs.core.Response;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xliu
 */
@Path("/users")
@Api("/users")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "users", description = ResourceConstants.USERS)
public class UserResource implements AuthenticatedResourceInterface, SourceControlResourceInterface {
    protected static final Pattern GITHUB_ID_PATTERN = Pattern.compile(".*/u/(\\d+).*");
    private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);
    //protected static final Pattern USERNAME_CONTAINS_KEYWORD_PATTERN = Pattern.compile("(?i)(dockstore|admin|curator|system|manager)");
    private static final Pattern VALID_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z]+[.a-zA-Z0-9-_]*$");
    private static final String CLOUD_INSTANCE_ID_DESCRIPTION = "ID of cloud instance to update/delete";
    private static final String USER_NOT_FOUND_DESCRIPTION = "User not found";
    public static final String USER_PROFILES = "userProfiles";
    private static final String USER_INCLUDE = USER_PROFILES + ", ...";
    private static final String USER_INCLUDE_MESSAGE = "Comma-delimited list of fields to include: " + USER_INCLUDE;
    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;

    private SessionFactory sessionFactory;
    private final WorkflowResource workflowResource;
    private final DockerRepoResource dockerRepoResource;
    private final WorkflowDAO workflowDAO;
    private final ToolDAO toolDAO;
    private final BioWorkflowDAO bioWorkflowDAO;
    private final AppToolDAO appToolDAO;
    private final ServiceDAO serviceDAO;
    private final EventDAO eventDAO;
    private final LambdaEventDAO lambdaEventDAO;
    private final DeletedUsernameDAO deletedUsernameDAO;
    private final NotebookDAO notebookDAO;
    private final PermissionsInterface authorizer;
    private final CachingAuthenticator<String, User> cachingAuthenticator;
    private final HttpClient client;

    private final String bitbucketClientSecret;
    private final String bitbucketClientID;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public UserResource(HttpClient client, SessionFactory sessionFactory, WorkflowResource workflowResource,
        DockerRepoResource dockerRepoResource, CachingAuthenticator<String, User> cachingAuthenticator, PermissionsInterface authorizer, DockstoreWebserviceConfiguration configuration) {
        this.eventDAO = new EventDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.tokenDAO = new TokenDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.appToolDAO = new AppToolDAO(sessionFactory);
        this.toolDAO = new ToolDAO(sessionFactory);
        this.bioWorkflowDAO = new BioWorkflowDAO(sessionFactory);
        this.serviceDAO = new ServiceDAO(sessionFactory);
        this.lambdaEventDAO = new LambdaEventDAO(sessionFactory);
        this.deletedUsernameDAO = new DeletedUsernameDAO(sessionFactory);
        this.notebookDAO = new NotebookDAO(sessionFactory);
        this.sessionFactory = sessionFactory;
        this.workflowResource = workflowResource;
        this.dockerRepoResource = dockerRepoResource;
        this.authorizer = authorizer;
        this.cachingAuthenticator = cachingAuthenticator;
        this.client = client;
        this.bitbucketClientID = configuration.getBitbucketClientID();
        this.bitbucketClientSecret = configuration.getBitbucketClientSecret();
    }

    /**
     * Checks if the include string (csv) includes some field
     * @param include CSV string
     * @param field Field to query for
     * @return True if include has the given field, false otherwise
     */
    private boolean checkIncludes(String include, String field) {
        String includeString = (include == null ? "" : include);
        List<String> includeSplit = Arrays.asList(includeString.split(","));
        return includeSplit.contains(field);
    }

    /**
     * If include contains userProfiles, initialize the profiles for the user
     *
     * @param include
     * @param user
     */
    private void initializeAdditionalFields(String include, User user) {
        if (checkIncludes(include, USER_PROFILES)) {
            Hibernate.initialize(user.getUserProfiles());
        }
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/username/{username}")
    @Operation(operationId = "listUser", description = "Get a user by username.")
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "A user with the specified username", content = @Content(schema = @Schema(implementation = User.class)))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "Get a user by username.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User listUser(@ApiParam("Username of user to return") @PathParam("username") @NotBlank String username,
        @Parameter(name = "include", description = USER_INCLUDE_MESSAGE, in = ParameterIn.QUERY) @ApiParam(value = USER_INCLUDE_MESSAGE) @QueryParam("include") String include,
        // authUser isn't used here, but don't remove; needed to trigger AuthenticatedUserFilter
        @Parameter(hidden = true, name = "user") @Auth Optional<User> authUser) {
        @SuppressWarnings("deprecation")
        User user = userDAO.findByUsername(username);
        checkNotNullUser(user);

        initializeAdditionalFields(include, user);
        return user;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/user")
    @Operation(operationId = "getUser", description = "Get the logged-in user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "The logged-in user", content = @Content(schema = @Schema(implementation = User.class)))
    @ApiOperation(nickname = "getUser", value = "Get the logged-in user.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User getUser(@ApiParam(hidden = true) @Parameter(hidden = true) @Auth User user) {
        User foundUser = userDAO.findById(user.getId());
        checkNotNullUser(foundUser);
        Hibernate.initialize(foundUser.getUserProfiles());
        return foundUser;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/user/memberships")
    @Operation(operationId = "getUserMemberships", description = "Get the logged-in user's memberships.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A set of the logged-in user's memberships", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrganizationUser.class))))
    @ApiOperation(value = "Get the logged-in user's memberships.", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = OrganizationUser.class, responseContainer = "set")
    public Set<OrganizationUser> getUserMemberships(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user) {
        User foundUser = userDAO.findById(user.getId());
        checkNotNullUser(foundUser);
        Set<OrganizationUser> organizationUsers = foundUser.getOrganizations();
        organizationUsers.forEach(organizationUser -> Hibernate.initialize(organizationUser.getOrganization()));
        return organizationUsers;
    }



    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/user/extended")
    @Operation(operationId = "getExtendedUserData", description = "Get additional information about the authenticated user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "Additional information about the authenticated user", content = @Content(schema = @Schema(implementation = ExtendedUserData.class)))
    @ApiOperation(value = "Get additional information about the authenticated user.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = ExtendedUserData.class)
    public ExtendedUserData getExtendedUserData(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user) {
        User foundUser = userDAO.findById(user.getId());
        checkNotNullUser(foundUser);
        return new ExtendedUserData(foundUser, this.authorizer, userDAO);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/user/changeUsername")
    @Operation(operationId = "changeUsername", description = "Change username if possible.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully changed username", content = @Content(schema = @Schema(implementation = User.class)))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiOperation(value = "Change username if possible.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User changeUsername(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User authUser, @ApiParam("Username to change to") @QueryParam("username") String username) {
        checkUserId(authUser, authUser.getId());
        if (!VALID_USERNAME_PATTERN.asPredicate().test(username)) {
            throw new CustomWebApplicationException("Username pattern invalid", HttpStatus.SC_BAD_REQUEST);
        }
        restrictUsername(username);

        User user = userDAO.findById(authUser.getId());
        checkNotNullUser(user);
        if (!new ExtendedUserData(user, this.authorizer, userDAO).canChangeUsername()) {
            throw new CustomWebApplicationException("Cannot change username, user not ready", HttpStatus.SC_BAD_REQUEST);
        }

        if (userDAO.findByUsername(username) != null || DeletedUserHelper.nonReusableUsernameFound(username, deletedUsernameDAO)) {
            String errorMsg = "Cannot change user to " + Utilities.cleanForLogging(username) + " because it is already in use";
            LOG.error(errorMsg);
            throw new CustomWebApplicationException(errorMsg, HttpStatus.SC_BAD_REQUEST);
        }


        user.setUsername(username);
        user.setSetupComplete(true);
        user.setUsernameChangeRequired(false);
        userDAO.clearCache();
        List<Token> tokens = tokenDAO.findByUserId(user.getId());
        Optional<Token> dockstoreToken = tokens
                .stream()
                .filter((Token token) -> Objects.equals(TokenType.DOCKSTORE, token.getTokenSource()))
                .findFirst();

        if (dockstoreToken.isPresent()) {
            dockstoreToken.get().setUsername(username);
            cachingAuthenticator.invalidate(dockstoreToken.get().getContent());
        }
        return userDAO.findById(user.getId());
    }

    public static void restrictUsername(String username) {
        Matcher matcher = USERNAME_CONTAINS_KEYWORD_PATTERN.matcher(username);
        if (matcher.find()) {
            throw new CustomWebApplicationException("Cannot change username to " + username
                    + " because it contains one or more of the following keywords: dockstore, admin, curator, system, or manager", HttpStatus.SC_BAD_REQUEST);
        }

    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/user")
    @Operation(operationId = "selfDestruct", description = "Delete user if possible.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Boolean indicating if user was deleted successfully", content = @Content(schema = @Schema(implementation = Boolean.class)))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_INTERNAL_SERVER_ERROR + "", description = HttpStatusMessageConstants.INTERNAL_SERVER_ERROR)
    @ApiOperation(value = "Delete user if possible.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Boolean.class)
    public boolean selfDestruct(
            @ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User authUser,
            @ApiParam(value = "Optional user id if deleting another user. Only admins can delete another user.") @Parameter(description = "Optional user id if deleting another user. Only admins can delete another user.", name = "userId", in = ParameterIn.QUERY) @QueryParam("userId") Long userId) {
        User user;
        if (userId != null) {
            checkIsAdmin(authUser);
            user = userDAO.findById(userId);
        } else {
            checkNotNullUser(authUser);
            user = userDAO.findById(authUser.getId());
        }
        checkNotNullUser(user);

        if (!new ExtendedUserData(user, this.authorizer, userDAO).canChangeUsername()) {
            throw new CustomWebApplicationException("Cannot delete user, user not ready for deletion", HttpStatus.SC_BAD_REQUEST);
        }

        // Remove dangling sharing artifacts before getting rid of tokens
        this.authorizer.selfDestruct(user);

        // Delete entries for which this user is the only user
        deleteSelfFromEntries(user);
        invalidateTokensForUser(user);
        deleteSelfFromLambdaEvents(user);
        boolean isDeleted =  userDAO.delete(user);
        if (isDeleted) {
            DeletedUsername deletedUsername = new DeletedUsername(user.getUsername());
            deletedUsernameDAO.create(deletedUsername);
        }
        return isDeleted;
    }

    private void invalidateTokensForUser(User user) {
        List<Token> byUserId = tokenDAO.findByUserId(user.getId());
        for (Token token : byUserId) {
            tokenDAO.delete(token);
            // invalidate tokens from caching authenticator
            cachingAuthenticator.invalidate(token.getContent());
        }
    }

    private void deleteSelfFromEntries(User user) {
        user.getEntries().stream()
                // The getIsPublished() check is arguably redundant as canChangeUsername(), above, already checks, but just in case...
                .filter(e -> e.getUsers().size() == 1 && !e.getIsPublished())
                .forEach(entry -> {
                    EntryDAO entryDAO;
                    if (entry instanceof Workflow) {
                        entryDAO = workflowDAO;
                    } else if (entry instanceof Tool) {
                        entryDAO = toolDAO;
                    } else {
                        throw new CustomWebApplicationException(
                                MessageFormat.format("Unexpected entry type {0}", entry.getClass().toString()),
                                HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    }
                    eventDAO.deleteEventByEntryID(entry.getId());
                    entryDAO.delete(entry);
                });
    }

    // We don't delete the LambdaEvent because it is useful for other users
    private void deleteSelfFromLambdaEvents(User user) {
        lambdaEventDAO.findByUser(user).forEach(lambdaEvent -> lambdaEvent.setUser(null));
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/user/{userId}/bannedStatus")
    @RolesAllowed("admin")
    @Consumes("application/json")
    @Operation(operationId = "banUser", description = "Update banned status of user. Removes all tokens for banned users.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_NO_CONTENT + "", description = "Successfully banned/unbanned user")
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "Updated banned status of user. Removes all tokens for banned users.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) })
    public void banUser(
        @ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User authUser, @ApiParam(value = "User to terminate", required = true) @Parameter(name = "userId", required = true) @PathParam("userId") long targetUserId,
        @ApiParam(value = "isBanned", required = true) @Parameter(name = "isBanned", required = true) Boolean isBanned) {
        // note this bans the user but leaves behind a tombstone to prevent re-login
        checkUserId(authUser, authUser.getId());

        User targetUser = userDAO.findById(targetUserId);
        checkNotNullUser(targetUser);

        if (isBanned) {
            invalidateTokensForUser(targetUser);
        }
        targetUser.setBanned(isBanned);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/checkUser/{username}")
    @ApiOperation(value = "Check if user with some username exists.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Boolean.class)
    @Operation(operationId = "checkUserExists", description = "Check if user with some username exists.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "Boolean indicating if a user with the specified username exists", content = @Content(schema = @Schema(implementation = Boolean.class)))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    public boolean checkUserExists(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
                                   @ApiParam("User name to check") @PathParam("username") @NotBlank String username) {
        @SuppressWarnings("deprecation")
        User foundUser = userDAO.findByUsername(username);
        if (foundUser == null && !DeletedUserHelper.nonReusableUsernameFound(username, deletedUsernameDAO)) {
            return false;
        }
        return true;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{userId}/tokens")
    @JsonView(TokenViews.User.class)
    @Operation(operationId = "getUserTokens", description = "Get information about tokens with user id.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of tokens belonging to user specified by userId", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Token.class))))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "Get information about tokens with user id.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Token.class, responseContainer = "List")
    public List<Token> getUserTokens(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        if (!user.isCurator() && !user.getIsAdmin()) {
            checkUserId(user, userId);
        }
        User fetchedUser = userDAO.findById(userId);
        checkNotNullUser(fetchedUser);
        return tokenDAO.findByUserId(userId);
    }

    @POST
    @Timed
    @UnitOfWork()
    @RolesAllowed("admin")
    @Path("/{userId}/tokens")
    @Operation(operationId = "createMetricsRobotToken", description = "Create a Dockstore token for a metrics robot user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "The new token.", content = @Content(schema = @Schema(implementation = String.class)))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    public String createMetricsRobotToken(@Parameter(hidden = true, name = "user")@Auth User user,
        @PathParam("userId") long userId) {
        User targetUser = userDAO.findById(userId);
        checkNotNullUser(targetUser);
        if (!targetUser.isMetricsRobot()) {
            throw new CustomWebApplicationException("Target user must be a metrics robot.", HttpStatus.SC_BAD_REQUEST);
        }

        Token dockstoreToken = tokenDAO.createDockstoreToken(userId, targetUser.getUsername());
        return dockstoreToken.getContent();
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{userId}/containers/published")
    @Operation(operationId = "userPublishedContainers", description = "List all published tools from a user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "A list of published tools from a user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Tool.class))))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "List all published tools from a user.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class, responseContainer = "List")
    public List<Tool> userPublishedContainers(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUserId(user, userId);
        User fetchedUser = userDAO.findById(userId);
        checkNotNullUser(fetchedUser);

        // get live entity
        final List<Tool> immutableList = toolDAO.findMyEntriesPublished(userId);
        final List<Tool> repositories = Lists.newArrayList(immutableList);
        repositories.removeIf(c -> !c.getIsPublished());
        return repositories;
    }

    //TODO: should separate out services and workflows
    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{userId}/workflows/published")
    @Operation(operationId = "userPublishedWorkflows", description = "List all published workflows from a user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of published workflows from a user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Workflow.class))))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "List all published workflows from a user.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class, responseContainer = "List")
    public List<Workflow> userPublishedWorkflows(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUserId(user, userId);
        User fetchedUser = userDAO.findById(userId);
        checkNotNullUser(fetchedUser);

        // get live entity
        final List<Workflow> immutableList = workflowDAO.findMyEntriesPublished(userId);
        final List<Workflow> repositories = Lists.newArrayList(immutableList);
        repositories.removeIf(workflow -> !workflow.getIsPublished());
        return repositories;
    }

    /**
     *
     * @param authUser
     * @param userId
     * @param organization
     * @param dockerRegistry not really a registry the way we use it now (ex: quay.io), rename in 1.10 this is actually a repository
     * @return
     */
    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/containers/{organization}/refresh")
    @Operation(operationId = "refreshToolsByOrganization", description = "Refresh all tools owned by the authenticated user with specified organization.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of tools owned by the user with the specified organization", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Tool.class))))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "Refresh all tools owned by the authenticated user with specified organization.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class, responseContainer = "List")
    public List<Tool> refreshToolsByOrganization(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId,
            @ApiParam(value = "Organization", required = true) @PathParam("organization") String organization,
            @ApiParam(value = "Docker registry", required = true) @QueryParam("dockerRegistry") String dockerRegistry) {

        checkUserId(authUser, userId);
        User fetchedUser = userDAO.findById(userId);
        checkNotNullUser(fetchedUser);

        // Check if the user has tokens for the organization they're refreshing
        checkToolTokens(authUser, userId, organization);
        if (dockerRegistry == null) {
            throw new CustomWebApplicationException("A repository is required", HttpStatus.SC_BAD_REQUEST);
        }
        dockerRepoResource.refreshToolsForUser(userId, organization, dockerRegistry);


        userDAO.clearCache();
        authUser = userDAO.findById(authUser.getId());
        // Update user data
        authUser.updateUserMetadata(tokenDAO);

        List<Tool> finalTools = getTools(authUser);
        bulkUpsertTools(authUser);
        return finalTools;
    }

    // TODO: Only update the ones that have changed
    private void bulkUpsertTools(User authUser) {
        List<Entry> toolEntries = toolDAO.findMyEntriesPublished(authUser.getId()).stream().map(Entry.class::cast)
                .collect(Collectors.toList());
        if (!toolEntries.isEmpty()) {
            PublicStateManager.getInstance().bulkUpsert(toolEntries);
        }
    }

    private void checkToolTokens(User authUser, Long userId, String organization) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        List<Tool> tools = userContainers(authUser, userId);
        if (organization != null && !organization.isEmpty()) {
            tools.removeIf(tool -> !tool.getNamespace().equals(organization));
        }
        Token gitLabToken = Token.extractToken(tokens, TokenType.GITLAB_COM);
        Token quayioToken = Token.extractToken(tokens, TokenType.QUAY_IO);
        Set<Registry> uniqueRegistry = new HashSet<>();
        tools.forEach(tool -> uniqueRegistry.add(tool.getRegistryProvider()));
        if (uniqueRegistry.size() == 0 && quayioToken == null) {
            throw new CustomWebApplicationException("You have no tools and no Quay.io token to automatically add tools. Please add a Quay.io token.", HttpStatus.SC_BAD_REQUEST);
        }
        if (uniqueRegistry.contains(Registry.QUAY_IO) && quayioToken == null) {
            throw new CustomWebApplicationException("You have Quay.io tools but no Quay.io token to refresh the tools with. Please add a Quay.io token.", HttpStatus.SC_BAD_REQUEST);
        }
        if (uniqueRegistry.contains(Registry.GITLAB) && gitLabToken == null) {
            throw new CustomWebApplicationException("You have GitLab tools but no GitLab token to refresh the tools with. Please add a GitLab token", HttpStatus.SC_BAD_REQUEST);
        }
    }

    @GET
    @Path("/{userId}/workflows")
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "userWorkflows", description = "List all workflows owned by the authenticated user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME), method = "GET")
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of workflows owned by the user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Workflow.class))))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "List all workflows owned by the authenticated user.", nickname = "userWorkflows", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class, responseContainer = "List")
    public List<Workflow> userWorkflows(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
            @Parameter(name = "userId", description = "User ID", required = true, in = ParameterIn.PATH) @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUserId(user, userId);
        final User fetchedUser = this.userDAO.findById(userId);
        checkNotNullUser(fetchedUser);
        return convertWorkflowSummariesToWorkflows(bioWorkflowDAO.findUserBioWorkflows(fetchedUser.getId()), BioWorkflow::new);
    }

    @GET
    @Path("/{userId}/appTools")
    @Timed
    @UnitOfWork
    @Operation(operationId = "userAppTools", description = "List all appTools owned by the authenticated user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
        + "", description = "A list of GitHub App tools owned by the user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Workflow.class))))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    public List<Workflow> userAppTools(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
        @Parameter(name = "userId", description = "User ID", required = true, in = ParameterIn.PATH) @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUserId(user, userId);
        final User fetchedUser = this.userDAO.findById(userId);
        checkNotNullUser(fetchedUser);
        return convertWorkflowSummariesToWorkflows(appToolDAO.findUserAppTools(fetchedUser.getId()), AppTool::new);
    }

    @GET
    @Path("/{userId}/notebooks")
    @Timed
    @UnitOfWork
    @Operation(operationId = "userNotebooks", description = "List all notebooks owned by the authenticated user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME), method = "GET")
    @ApiResponse(responseCode = HttpStatus.SC_OK
        + "", description = "A list of notebooks owned by the user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Workflow.class))))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    public List<Workflow> userNotebooks(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
        @Parameter(name = "userId", description = "User ID", required = true, in = ParameterIn.PATH) @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUserId(user, userId);
        final User fetchedUser = this.userDAO.findById(userId);
        checkNotNullUser(fetchedUser);
        return convertWorkflowSummariesToWorkflows(notebookDAO.findUserNotebooks(fetchedUser.getId()), Notebook::new);
    }

    private List<Workflow> convertWorkflowSummariesToWorkflows(List<WorkflowSummary> myWorkflows, Supplier<Workflow> workflowCreator) {
        List<Workflow> workflows = new ArrayList<>();
        myWorkflows.forEach(myWorkflow -> {
            Workflow workflow = workflowCreator.get();
            workflow.setOrganization(myWorkflow.organization());
            workflow.setId(myWorkflow.id());
            workflow.setSourceControl(myWorkflow.sourceControl());
            workflow.setIsPublished(myWorkflow.isPublished());
            workflow.setWorkflowName(myWorkflow.workflowName());
            workflow.setRepository(myWorkflow.repository());
            workflow.setMode(myWorkflow.workflowMode());
            workflow.setGitUrl(myWorkflow.gitUrl());
            workflow.setDescription(myWorkflow.description());
            workflow.setArchived(myWorkflow.archived());
            workflows.add(workflow);
        });
        return workflows;
    }

    @GET
    @Path("/{userId}/services")
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "userServices", description = "List all services owned by the authenticated user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of services owned by the user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Workflow.class))))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "List all services owned by the authenticated user.", nickname = "userServices", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class, responseContainer = "List")
    public List<Workflow> userServices(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUserId(user, userId);
        final User fetchedUser = this.userDAO.findById(userId);
        checkNotNullUser(fetchedUser);
        return getStrippedServices(fetchedUser);
    }

    private List<Workflow> getServices(User user) {
        return serviceDAO.findMyEntries(user.getId()).stream().map(Service.class::cast).collect(Collectors.toList());
    }
    private List<Workflow> getStrippedServices(User user) {
        final List<Workflow> services = getServices(user);
        services.forEach(service -> Hibernate.initialize(service.getWorkflowVersions()));
        EntryVersionHelper.stripContentFromEntries(services, this.userDAO);
        return services;
    }

    private List<Workflow> getStrippedWorkflowsAndServices(User user) {
        final List<Workflow> workflows = workflowDAO.findMyEntries(user.getId());
        EntryVersionHelper.stripContentFromEntries(workflows, this.userDAO);
        return workflows;

    }

    private List<Tool> getTools(User user) {
        return toolDAO.findMyEntries(user.getId());
    }

    @GET
    @Path("/{userId}/containers")
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "userContainers", description = "List all tools owned by the authenticated user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "A list of tools owned by the user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Tool.class))))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "List all tools owned by the authenticated user.", nickname = "userContainers", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class, responseContainer = "List")
    public List<Tool> userContainers(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUserId(user, userId);
        final User byId = this.userDAO.findById(userId);
        checkNotNullUser(byId);
        List<Tool> tools = getTools(byId);
        EntryVersionHelper.stripContentFromEntries(tools, this.userDAO);
        return tools;
    }
    @GET
    @Path("/users/organizations")
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "getUserDockstoreOrganizations", description = "Get all of the Dockstore organizations for a user, sorted by most recently updated.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of the Dockstore organizations for a user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrganizationUpdateTime.class))))
    @ApiOperation(value = "See OpenApi for details")
    public List<OrganizationUpdateTime> getUserDockstoreOrganizations(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User authUser,
                                                            @Min(1) @Parameter(name = "count", description = "Maximum number of organizations to return", in = ParameterIn.QUERY) @QueryParam("count") Integer count,
                                                            @Parameter(name = "filter", description = "Filter paths with matching text", in = ParameterIn.QUERY) @QueryParam("filter") String filter) {
        final List<OrganizationUpdateTime> organizations = new ArrayList<>();
        final User fetchedUser = this.userDAO.findById(authUser.getId());
        checkNotNullUser(fetchedUser);

        // Retrieve all organizations and get timestamps
        Set<OrganizationUser> organizationUsers = fetchedUser.getOrganizations();

        organizationUsers.forEach((OrganizationUser organizationUser) -> {
            Organization organization = organizationUser.getOrganization();
            Optional<Collection> mostRecentCollection = organization.getCollections().stream().max(Comparator.comparing(Collection::getDbUpdateDate));
            Timestamp timestamp = organization.getDbUpdateDate();
            if (mostRecentCollection.isPresent() && timestamp.before(mostRecentCollection.get().getDbUpdateDate())) {
                timestamp = mostRecentCollection.get().getDbUpdateDate();
            }
            organizations.add(new OrganizationUpdateTime(organization.getName(), organization.getDisplayName(), timestamp));
        });

        // Sort all organizations by timestamp
        List<OrganizationUpdateTime> sortedOrganizations = organizations
                .stream()
                .filter((OrganizationUpdateTime organizationUpdateTime) -> filter == null || filter.isBlank() || organizationUpdateTime.getName().toLowerCase().contains(filter.toLowerCase()) || organizationUpdateTime.getDisplayName().toLowerCase().contains(filter.toLowerCase()))
                .sorted(Comparator.comparing(OrganizationUpdateTime::getLastUpdateDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        // Grab subset if necessary
        if (count != null) {
            return sortedOrganizations.subList(0, Math.min(count, sortedOrganizations.size()));
        }
        return sortedOrganizations;
    }

    @GET
    @Path("/users/entries")
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "getUserEntries", description = "Get all of the entries for a user, sorted by most recently updated.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of the entries for a user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = EntryUpdateTime.class))))
    @ApiOperation(value = "See OpenApi for details")
    public List<EntryUpdateTime> getUserEntries(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User authUser,
                                                @Min(1) @Parameter(name = "count", description = "Maximum number of entries to return", in = ParameterIn.QUERY) @QueryParam("count") Integer count,
                                                @Parameter(name = "filter", description = "Filter paths with matching text", in = ParameterIn.QUERY) @QueryParam("filter") String filter,
                                                @Parameter(name = "type", description = "Type of entry", in = ParameterIn.QUERY) @QueryParam("type") EntrySearchType type) {
        //get entries with only minimal columns from database
        final List<EntryLite> entriesLite = new ArrayList<>();
        final long userId = authUser.getId();
        if (type == null || type == EntrySearchType.TOOLS) {
            entriesLite.addAll(toolDAO.findEntryVersions(userId));
            entriesLite.addAll(appToolDAO.findEntryVersions(userId));
        }
        if (type == null || type == EntrySearchType.WORKFLOWS) {
            entriesLite.addAll(bioWorkflowDAO.findEntryVersions(userId));
        }
        if (type == null || type == EntrySearchType.SERVICES) {
            entriesLite.addAll(serviceDAO.findEntryVersions(userId));
        }
        if (type == null || type == EntrySearchType.NOTEBOOKS) {
            entriesLite.addAll(notebookDAO.findEntryVersions(userId));
        }

        //cleanup fields for UI: filter(if applicable), sort, and limit by count(if applicable)
        List<EntryUpdateTime> filteredEntries = entriesLite
                .stream().map(e -> new EntryUpdateTime(e.getEntryPath(), e.getPrettyPath(), e.getEntryType(), new Timestamp(e.getLastUpdated().getTime())))
                .filter((EntryUpdateTime entryUpdateTime) -> filter == null || filter.isBlank() || entryUpdateTime.getPath().toLowerCase().contains(filter.toLowerCase()))
                .sorted(Comparator.comparing(EntryUpdateTime::getLastUpdateDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(count != null ? count : Integer.MAX_VALUE)
                .collect(Collectors.toList());
        return filteredEntries;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/starredTools")
    @Operation(operationId = "getStarredTools", description = "Get the authenticated user's starred tools.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of the authenticated user's starred tools", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Entry.class))))
    @ApiOperation(value = "Get the authenticated user's starred tools.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Entry.class, responseContainer = "List")
    public Set<Entry> getStarredTools(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user) {
        User u = userDAO.findById(user.getId());
        checkNotNullUser(u);
        return this.getStarredEntries(u, Set.of(EntryType.TOOL, EntryType.APPTOOL));
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/starredWorkflows")
    @Operation(operationId = "getStarredWorkflows", description = "Get the authenticated user's starred workflows.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of the authenticated user's starred workflows", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Entry.class))))
    @ApiOperation(value = "Get the authenticated user's starred workflows.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Entry.class, responseContainer = "List")
    public Set<Entry> getStarredWorkflows(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user) {
        User u = userDAO.findById(user.getId());
        checkNotNullUser(u);
        return this.getStarredEntries(u, Set.of(EntryType.WORKFLOW));
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/starredNotebooks")
    @Operation(operationId = "getStarredNotebooks", description = "Get the authenticated user's starred notebooks.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of the authenticated user's starred notebooks", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Entry.class))))
    @ApiOperation(value = "Get the authenticated user's starred notebooks.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) },
            response = Entry.class, responseContainer = "List")
    public Set<Entry> getStarredNotebooks(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user) {
        User u = userDAO.findById(user.getId());
        checkNotNullUser(u);
        return this.getStarredEntries(u, Set.of(EntryType.NOTEBOOK));
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/starredServices")
    @Operation(operationId = "getStarredServices", description = "Get the authenticated user's starred services.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of the authenticated user's starred services", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Entry.class))))
    @ApiOperation(value = "Get the authenticated user's starred services.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Entry.class, responseContainer = "List")
    public Set<Entry> getStarredServices(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user) {
        User u = userDAO.findById(user.getId());
        checkNotNullUser(u);
        return this.getStarredEntries(u, Set.of(EntryType.SERVICE));
    }

    /**
     * Helper function for getStarred endpoints
     *
     * @param user
     * @param desiredTypes
     * @return a user's starredEntries for desired entryTypes
     */
    public Set<Entry> getStarredEntries(User user, Set<EntryType> desiredTypes) {
        return user.getStarredEntries().stream().filter(entry -> desiredTypes.contains(entry.getEntryType()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/starredOrganizations")
    @Operation(operationId = "getStarredOrganizations", description = "Get the authenticated user's starred organizations.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of the authenticated user's starred organizations", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Organization.class))))
    @ApiOperation(value = "Get the authenticated user's starred organizations.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Organization.class, responseContainer = "List")
    public Set<Organization> getStarredOrganizations(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user) {
        User u = userDAO.findById(user.getId());
        checkNotNullUser(u);
        return u.getStarredOrganizations();
    }

    @GET
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @Path("/updateUserMetadata")
    @Operation(operationId = "updateUserMetadata", description = "Update metadata of all users.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "Successfully updated metadata of all users", content = @Content(array = @ArraySchema(schema = @Schema(implementation = User.class))))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiOperation(value = "Update metadata of all users.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Admin only.", response = User.class, responseContainer = "List")
    public List<User> updateUserMetadata(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user) {
        List<User> users = userDAO.findAll();
        for (User u : users) {
            u.updateUserMetadata(tokenDAO, false);
        }

        return userDAO.findAll();
    }

    @POST
    @UnitOfWork
    @RolesAllowed("admin")
    @Path("/updateUserWorkflows")
    @Operation(operationId = "checkWorkflowOwnership", description = "Check workflow ownership", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_NO_CONTENT
        + "", description = "Successfully updated workflow ownership for all users")
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    public Response updateUserWorkflows(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User adminUser) {
        final List<Long> allGitHubUsers = userDAO.findAllGitHubUserIds();
        allGitHubUsers.forEach(userId -> {
            // Break into many transactions so all workflows are not loaded into memory
            try {
                new TransactionHelper(sessionFactory).transaction(() -> {
                    tokenDAO.findGithubByUserId(userId).stream().limit(1).forEach(gitHubToken -> {
                        final SourceCodeRepoInterface sourceCodeRepo =
                            SourceCodeRepoFactory.createSourceCodeRepo(gitHubToken);
                        final Set<String> orgMemberships = sourceCodeRepo.getOrganizationMemberships();
                        final Set<GitRepo> gitRepos = sourceCodeRepo.getRepoLevelAccessRepositories();
                        final User user = userDAO.findById(userId);
                        user.getEntries().stream().filter(Workflow.class::isInstance)
                            .map(Workflow.class::cast)
                            .filter(workflow -> {
                                final boolean userHasOrganizationAccess =
                                    orgMemberships.contains(workflow.getOrganization());
                                return !userHasOrganizationAccess && !hasRepoLevelAccess(gitRepos, workflow);
                            })
                            .forEach(workflow -> workflow.removeUser(user));
                    });
                });
            } catch (Exception e) {
                // LOG and continue -- don't let one user's invalid token or other problem block everybody
                final String message = String.format("Error updating workflows for user %s", userId);
                LOG.error(message, e);
            }
        });
        return Response.noContent().build();
    }

    private static boolean hasRepoLevelAccess(final Set<GitRepo> gitRepos, final Workflow workflow) {
        return gitRepos.contains(new GitRepo(workflow.getOrganization(), workflow.getRepository()));
    }

    @GET
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @Path("/emails")
    @Operation(operationId = "getAllUserEmails", description = "Admin-only endpoint. Get the emails of all Dockstore users", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "See OpenApi for details", hidden = true)
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "A list of Dockstore users' emails.", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserInfo.class))))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    public List<UserInfo> getAllUserEmails(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user) {
        List<UserInfo> userInfo = userDAO.findAllGitHubUserInfo();
        userInfo.addAll(userDAO.findAllGoogleUserInfo());
        return userInfo;
    }

    /**
     * TODO: Use enum for the source parameter
     * @param user      The Authorized user
     * @param source    token source, currently either the google or github TokenType
     * @return          The updated user
     */
    @GET
    @Timed
    @UnitOfWork
    @Path("/user/updateUserMetadata")
    @Operation(operationId = "updateLoggedInUserMetadata", description = "Update metadata for logged in user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully updated metadata for logged in user", content = @Content(schema = @Schema(implementation = User.class)))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiOperation(value = "Update metadata for logged in user.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User updateLoggedInUserMetadata(@ApiParam(hidden = true)@Parameter(hidden = true, name = "user")@Auth User user, @NotNull @ApiParam(value = "Token source", allowableValues = "google.com, github.com") @QueryParam("source") TokenType source) {
        User dbuser = userDAO.findById(user.getId());
        checkNotNullUser(dbuser);
        if (source.equals(TokenType.GOOGLE_COM)) {
            updateGoogleAccessToken(user.getId());
        }
        dbuser.updateUserMetadata(tokenDAO, source, true);
        return dbuser;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/user/updateAcceptedDocuments")
    @Operation(operationId = "updateAcceptedDocuments", description = "Update the user's TOS and privacy policy to the latest versions.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "User with updated TOS/Privacy Policy", content = @Content(schema = @Schema(implementation = User.class)))
    @ApiOperation(value = "Update the user's TOS and privacy policy to the latest versions.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class, hidden = true)
    public User updateAcceptedDocuments(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user) {
        User dbUser = userDAO.findById(user.getId());
        checkNotNullUser(dbUser);
        TokenResource.acceptTOSAndPrivacyPolicy(dbUser);
        return dbUser;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @RolesAllowed({"admin", "curator"})
    @Path("/user/{userId}/limits")
    @Operation(operationId = "getUserLimits", description = "Returns the specified user's limits. ADMIN or CURATOR only", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "A user's limits", content = @Content(schema = @Schema(implementation = Limits.class)))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "Returns the specified user's limits. ADMIN or CURATOR only", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Limits.class)
    public Limits getUserLimits(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        User user = userDAO.findById(userId);
        checkNotNullUser(user);
        Limits limits = new Limits();
        limits.setHostedEntryCountLimit(user.getHostedEntryCountLimit());
        limits.setHostedEntryVersionLimit(user.getHostedEntryVersionsLimit());
        return limits;
    }

    @PUT
    @Timed
    @UnitOfWork
    @RolesAllowed({"admin", "curator"})
    @Path("/user/{userId}/limits")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "setUserLimits", description = "Update the specified user's limits. ADMIN or CURATOR only", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully updated the user's limits", content = @Content(schema = @Schema(implementation = Limits.class)))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "Update the specified user's limits. ADMIN or CURATOR only", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Limits.class)
    public Limits setUserLimits(@ApiParam(hidden = true)  @Parameter(hidden = true, name = "user", in = ParameterIn.HEADER)@Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId,
            @ApiParam(value = "Limits to set for a user", required = true) Limits limits) {
        User user = userDAO.findById(userId);
        checkNotNullUser(user);
        user.setHostedEntryCountLimit(limits.getHostedEntryCountLimit());
        user.setHostedEntryVersionsLimit(limits.getHostedEntryVersionLimit());
        // User could be cached by Dockstore or Google token -- invalidate all
        tokenDAO.findByUserId(user.getId()).forEach(token -> this.cachingAuthenticator.invalidate(token.getContent()));
        return limits;
    }

    @POST
    @Path("/github/sync")
    @Timed
    @UnitOfWork
    @Operation(operationId = "syncUserWithGitHub", description = "Syncs Dockstore account with GitHub App Installations.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "Successfully synced Dockstore account with GitHub App installations", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Workflow.class))))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiOperation(value = "Syncs Dockstore account with GitHub App Installations.", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class, responseContainer = "List")
    public List<Workflow> syncUserWithGitHub(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User authUser) {
        final User user = userDAO.findById(authUser.getId());
        checkNotNullUser(user);
        workflowResource.syncEntitiesForUser(user);
        userDAO.clearCache();
        return getStrippedWorkflowsAndServices(userDAO.findById(user.getId()));
    }

    @PATCH
    @Timed
    @UnitOfWork
    @Path("/{userId}/workflows")
    @ApiOperation(value = "Adds a user to any Dockstore workflows that they should have access to.", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class, responseContainer = "List")
    @Operation(operationId = "addUserToDockstoreWorkflows", description = "Adds the logged-in user to any Dockstore workflows that they should have access to.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "Successfully added user to Dockstore workflows that they should have access to", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Workflow.class))))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    public List<Workflow> addUserToDockstoreWorkflows(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User authUser,
            @ApiParam(name = "userId", required = true, value = "User to update") @PathParam("userId") @Parameter(name = "userId", in = ParameterIn.PATH, description = "User to update", required = true) long userId,
            @ApiParam(name = "emptyBody", value = APPEASE_SWAGGER_PATCH) @Parameter(description = APPEASE_SWAGGER_PATCH, name = "emptyBody") String emptyBody) {
        final User user = userDAO.findById(authUser.getId());
        checkNotNullUser(user);
        if (!Objects.equals(userId, user.getId())) {
            throw new CustomWebApplicationException("The user id provided does not match the logged-in user id.", HttpStatus.SC_BAD_REQUEST);
        }
        // Ignore hosted workflows
        List<SourceControl> sourceControls = Arrays.stream(SourceControl.values()).filter(sourceControl -> !Objects.equals(sourceControl, SourceControl.DOCKSTORE)).toList();

        List<Token> scTokens = getAndRefreshBitbucketTokens(user, tokenDAO, client, bitbucketClientID, bitbucketClientSecret)
                .stream()
                .filter(token -> sourceControls.contains(token.getTokenSource().getSourceControl()))
                .toList();

        scTokens.forEach(token -> {
            final SourceCodeRepoInterface sourceCodeRepo =  SourceCodeRepoFactory.createSourceCodeRepo(token);
            final SourceControl sourceControl = token.getTokenSource().getSourceControl();
            addUserToReposInOrgsWhereUserIsAMember(user, sourceControl, sourceCodeRepo);
            addUserToReposWithRepoLevelMembership(user, sourceControl, sourceCodeRepo);
        });
        return convertWorkflowSummariesToWorkflows(this.bioWorkflowDAO.findUserBioWorkflows(user.getId()), BioWorkflow::new);
    }

    /**
     * Adds the user to all workflows whose repos the user direct access to. For example, if a user has been
     * granted GitHub permissions to the GitHub organization "myorg" and the repository "anotherOrg/repo",
     * this method will add the user to "anotherOrg/repo", only (if not already present).
     * @param user
     * @param sourceControl
     * @param sourceCodeRepo
     */
    private void addUserToReposWithRepoLevelMembership(final User user, final SourceControl sourceControl,
        final SourceCodeRepoInterface sourceCodeRepo) {
        sourceCodeRepo.getRepoLevelAccessRepositories().forEach(gitRepo ->
            workflowDAO.findByPathWithoutUser(sourceControl, gitRepo.getOrganization(), gitRepo.getRepository(),
                    user)
            .forEach(workflow -> workflow.addUser(user)));
    }

    /**
     * Adds the user to all workflows against repos in organizations where the user is a member.
     * For example, if a user has been
     * granted GitHub permissions to the GitHub organization "myorg" and the repository "anotherOrg/repo",
     * this method will add the user to all workflows based on repos in the "myorg" organization.
     * @param user
     * @param sourceControl
     * @param sourceCodeRepo
     */
    private void addUserToReposInOrgsWhereUserIsAMember(final User user, final SourceControl sourceControl,
        final SourceCodeRepoInterface sourceCodeRepo) {
        final List<String> organizationMemberships = new ArrayList(sourceCodeRepo.getOrganizationMemberships());
        workflowDAO.findByOrganizationsWithoutUser(sourceControl, organizationMemberships,
                user)
            .forEach(workflow -> workflow.addUser(user));
    }

    @PUT
    @Timed
    @UnitOfWork
    @RolesAllowed({"admin"})
    @Path("/{userId}/privileges")
    @Consumes("application/json")
    @Operation(operationId = "setUserPrivileges", description = "Updates the provided userID to admin or curator status, usable by ADMINs only", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully updated user to admin or curator status", content = @Content(schema = @Schema(implementation = User.class)))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "Updates the provided userID to admin or curator status, usable by ADMINs only", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class, hidden = true)
    public User setUserPrivilege(@Parameter(hidden = true, name = "user")@Auth User authUser,
                                 @Parameter(name = "User ID", required = true) @PathParam("userId") Long userID,
                                 @Parameter(name = "Set privilege for a user", required = true) PrivilegeRequest privilegeRequest) {
        User targetUser = userDAO.findById(userID);
        checkNotNullUser(targetUser);

        // This ensures that the user cannot modify their own privileges.
        if (authUser.getId() == targetUser.getId()) {
            throw new CustomWebApplicationException("You cannot modify your own privileges", HttpStatus.SC_FORBIDDEN);
        }

        // If the authorized user is not an admin, fail gracefully.
        // This is a backup test for the @RolesAllowed annotation above, to prevent anarchy in case it is somehow disabled.
        if (!authUser.getIsAdmin()) {
            throw new CustomWebApplicationException("You do not have privileges to modify administrative rights", HttpStatus.SC_FORBIDDEN);
        }

        // Set the new privileges.
        targetUser.setIsAdmin(privilegeRequest.isAdmin());
        targetUser.setCurator(privilegeRequest.isCurator());
        targetUser.setPlatformPartner(privilegeRequest.getPlatformPartner());
        targetUser.setMetricsRobot(privilegeRequest.getMetricsRobot());

        // Invalidate any tokens corresponding to the target user.
        tokenDAO.findByUserId(targetUser.getId()).forEach(token -> this.cachingAuthenticator.invalidate(token.getContent()));

        return targetUser;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/github/events")
    @Operation(operationId = "getUserGitHubEvents", description = "Get all of the GitHub Events for the logged in user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of GitHub Events for the logged in user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LambdaEvent.class))))
    @ApiOperation(value = "See OpenApi for details")
    public List<LambdaEvent> getUserGitHubEvents(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User authUser,
            @Parameter(description = PAGINATION_OFFSET_TEXT) @Min(0) @QueryParam("offset") Integer offset,
            @Parameter(description = PAGINATION_LIMIT_TEXT) @Max(MAX_PAGINATION_LIMIT) @DefaultValue(PAGINATION_LIMIT) @QueryParam("limit") Integer limit,
            @DefaultValue("") @QueryParam("filter") String filter,
            @DefaultValue("dbCreateDate") @QueryParam("sortCol") String sortCol,
            @DefaultValue("desc") @QueryParam("sortOrder") String sortOrder,
            @Context HttpServletResponse response) {
        final User user = userDAO.findById(authUser.getId());
        checkNotNullUser(user);
        List<LambdaEvent> byUser = lambdaEventDAO.findByUser(user, offset, limit, filter, sortCol, sortOrder);
        response.addHeader(LambdaEventResource.X_TOTAL_COUNT, String.valueOf(byUser.size()));
        response.addHeader(LambdaEventResource.ACCESS_CONTROL_EXPOSE_HEADERS, LambdaEventResource.X_TOTAL_COUNT);
        return byUser;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/registries")
    @Operation(operationId = "getUserRegistries", description = "Get all of the git registries accessible to the logged in user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of the git registries accessible to the logged in user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SourceControl.class))))
    @ApiOperation(value = "See OpenApi for details")
    public List<SourceControl> getUserRegistries(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User authUser) {
        return tokenDAO.findByUserId(authUser.getId())
                .stream()
                .filter(token -> token.getTokenSource().isSourceControlToken())
                .map(token -> token.getTokenSource().getSourceControl())
                .collect(Collectors.toList());
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/registries/{gitRegistry}/organizations")
    @Operation(operationId = "getUserOrganizations", description = "Get all of the organizations for a given git registry accessible to the logged in user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of organizations for a given git registry accessible to the logged in user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class), uniqueItems = true)))
    @ApiOperation(value = "See OpenApi for details")
    public Set<String> getUserOrganizations(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User authUser,
                                            @Parameter(name = "gitRegistry", description = "Git registry", required = true, in = ParameterIn.PATH) @PathParam("gitRegistry") SourceControl gitRegistry) {

        SourceCodeRepoInterface sourceCodeRepo = createSourceCodeRepo(authUser, gitRegistry, tokenDAO, client, bitbucketClientID, bitbucketClientSecret);
        if (sourceCodeRepo == null) {
            return Set.of();
        } else {
            return sourceCodeRepo.getOrganizations();
        }
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/registries/{gitRegistry}/organizations/{organization}")
    @Operation(operationId = "getUserOrganizationRepositories", description = "Get all of the repositories for an organization for a given git registry accessible to the logged in user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "A list of repositories for an organization for a given git registry accessible to the logged in user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Repository.class))))
    @ApiOperation(value = "See OpenApi for details")
    public List<Repository> getUserOrganizationRepositories(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User authUser,
                                                           @Parameter(name = "gitRegistry", description = "Git registry", required = true, in = ParameterIn.PATH) @PathParam("gitRegistry") SourceControl gitRegistry,
                                                           @Parameter(name = "organization", description = "Git organization", required = true, in = ParameterIn.PATH) @PathParam("organization") String organization) {
        Map<String, String> repositoryUrlToName = getGitRepositoryMap(authUser, gitRegistry);
        return repositoryUrlToName.values().stream()
                .filter(repository -> repository.startsWith(organization + "/"))
                .map(repository -> new Repository(repository.split("/")[0], repository.split("/")[1], gitRegistry, workflowDAO.findByPath(gitRegistry + "/" + repository, false, BioWorkflow.class).isPresent(), canDeleteWorkflow(gitRegistry + "/" + repository)))
                .sorted(Comparator.comparing(Repository::getRepositoryName))
                .collect(Collectors.toList());
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{userId}/cloudInstances")
    @ApiOperation(value = "See OpenApi for details", hidden = true)
    @Operation(operationId = "getUserCloudInstances", description = "Get all cloud instances belonging to the user", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = HttpStatusMessageConstants.OK, content = @Content(array = @ArraySchema(schema = @Schema(implementation = CloudInstance.class))))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    public Set<CloudInstance> getUserCloudInstances(@Parameter(hidden = true, name = "user")@Auth User authUser,
        @ApiParam(name = "userId", required = true, value = "User to update") @PathParam("userId") @Parameter(name = "userId", in = ParameterIn.PATH, description = "ID of user to get cloud instances for", required = true) long userId) {
        final User user = userDAO.findById(userId);
        checkUserId(authUser, userId);
        checkNotNullUser(user);
        Set<CloudInstance> cloudInstances = user.getCloudInstances();
        cloudInstances.forEach(e -> Hibernate.initialize(e.getSupportedLanguages()));
        return cloudInstances;
    }

    @POST
    @Timed
    @UnitOfWork()
    @Path("/{userId}/cloudInstances")
    @Operation(operationId = "postUserCloudInstance", description = "Create a new cloud instance belonging to the user", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiResponse(responseCode = HttpStatus.SC_NO_CONTENT + "", description = HttpStatusMessageConstants.NO_CONTENT)
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED + "", description = HttpStatusMessageConstants.UNAUTHORIZED)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = USER_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "See OpenApi for details", hidden = true)
    public Set<CloudInstance> postUserCloudInstance(@Parameter(hidden = true, name = "user")@Auth User authUser,
            @PathParam("userId") @Parameter(name = "userId", in = ParameterIn.PATH, description = "ID of user to create the cloud instance for", required = true) long userId,
            @Parameter(description = "Cloud instance to add to the user", name = "Cloud Instance", required = true) CloudInstance cloudInstanceBody) {
        final User user = userDAO.findById(userId);
        checkUserId(authUser, userId);
        checkNotNullUser(user);
        CloudInstance cloudInstanceToBeAdded = new CloudInstance();
        cloudInstanceToBeAdded.setPartner(cloudInstanceBody.getPartner());
        cloudInstanceToBeAdded.setUrl(cloudInstanceBody.getUrl());
        cloudInstanceToBeAdded.setSupportsFileImports(cloudInstanceBody.isSupportsFileImports());
        cloudInstanceToBeAdded.setSupportsHttpImports(cloudInstanceBody.isSupportsHttpImports());
        cloudInstanceToBeAdded.setSupportedLanguages(cloudInstanceBody.getSupportedLanguages());
        cloudInstanceToBeAdded.setDisplayName(cloudInstanceBody.getDisplayName());
        // TODO: Figure how to make this not required (already adding the instance to the user)
        cloudInstanceToBeAdded.setUser(user);
        user.getCloudInstances().add(cloudInstanceToBeAdded);
        user.getCloudInstances().forEach(e -> Hibernate.initialize(e.getSupportedLanguages()));
        return user.getCloudInstances();
    }

    @DELETE
    @Timed
    @UnitOfWork()
    @Path("/{userId}/cloudInstances/{cloudInstanceId}")
    @Operation(operationId = "deleteUserCloudInstance", description = "Delete a cloud instance belonging to the user", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_NO_CONTENT + "", description = HttpStatusMessageConstants.NO_CONTENT)
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED + "", description = HttpStatusMessageConstants.UNAUTHORIZED)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = HttpStatusMessageConstants.NOT_FOUND)
    @ApiOperation(value = "See OpenApi for details", hidden = true)
    public void deleteUserCloudInstance(@Parameter(hidden = true, name = "user")@Auth User authUser,
            @PathParam("userId") @Parameter(name = "userId", in = ParameterIn.PATH, description = "ID of user to delete the cloud instance for", required = true) long userId,
            @PathParam("cloudInstanceId") @Parameter(name = "cloudInstanceId", in = ParameterIn.PATH, description = CLOUD_INSTANCE_ID_DESCRIPTION, required = true) long cloudInstanceId) {
        final User user = userDAO.findById(userId);
        checkUserId(authUser, userId);
        checkNotNullUser(user);
        boolean deleted = user.getCloudInstances().removeIf(cloudInstance -> cloudInstance.getId() == cloudInstanceId);
        if (!deleted) {
            throw new CustomWebApplicationException("ID of cloud instance does not exist", HttpStatus.SC_NOT_FOUND);
        }
    }

    @PUT
    @Timed
    @UnitOfWork()
    @Path("/{userId}/cloudInstances/{cloudInstanceId}")
    @Operation(operationId = "putUserCloudInstance", description = "Update a cloud instance belonging to the user", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "See OpenApi for details", hidden = true)
    @ApiResponse(responseCode = HttpStatus.SC_NO_CONTENT + "", description = HttpStatusMessageConstants.NO_CONTENT)
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED + "", description = HttpStatusMessageConstants.UNAUTHORIZED)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = HttpStatusMessageConstants.NOT_FOUND)
    public void putUserCloudInstance(@Parameter(hidden = true, name = "user")@Auth User authUser,
            @PathParam("userId") @Parameter(name = "userId", in = ParameterIn.PATH, description = "ID of user to update the cloud instance for", required = true) long userId,
            @PathParam("cloudInstanceId") @Parameter(name = "cloudInstanceId", in = ParameterIn.PATH, description = CLOUD_INSTANCE_ID_DESCRIPTION, required = true) long cloudInstanceId,
            @Parameter(description = "Cloud instance to replace for a user", name = "Cloud Instance", required = true) CloudInstance cloudInstanceBody) {
        final User user = userDAO.findById(userId);
        checkUserId(authUser, userId);
        checkNotNullUser(user);
        Optional<CloudInstance> optionalExistingCloudInstance = user.getCloudInstances().stream()
                .filter(cloudInstance -> cloudInstance.getId() == cloudInstanceId).findFirst();
        if (optionalExistingCloudInstance.isPresent()) {
            CloudInstance cloudInstance = optionalExistingCloudInstance.get();
            cloudInstance.setPartner(cloudInstanceBody.getPartner());
            cloudInstance.setUrl(cloudInstanceBody.getUrl());
            cloudInstance.setSupportsFileImports(cloudInstanceBody.isSupportsFileImports());
            cloudInstance.setSupportsHttpImports(cloudInstanceBody.isSupportsHttpImports());
            cloudInstance.setSupportedLanguages(cloudInstanceBody.getSupportedLanguages());
        } else {
            throw new CustomWebApplicationException("ID of cloud instance does not exist", HttpStatus.SC_NOT_FOUND);
        }
    }

    /**
     * Check if a workflow can be deleted.
     * For now this is simply if a workflow is a stub or not
     * @param path full path to workflow
     * @return can delete workflow
     */
    private boolean canDeleteWorkflow(String path) {
        Optional<BioWorkflow> workflow = workflowDAO.findByPath(path, false, BioWorkflow.class);
        if (workflow.isPresent()) {
            return workflow.get().getMode() == WorkflowMode.STUB;
        }
        return false;
    }

    /**
     * For a given user and git registry, retrieve a map of git url to repository path
     * @param user
     * @param sourceControl
     * @return mapping of git url to repository path
     */
    private Map<String, String> getGitRepositoryMap(User user, SourceControl sourceControl) {
        SourceCodeRepoInterface sourceCodeRepo = createSourceCodeRepo(user, sourceControl, tokenDAO, client, bitbucketClientID, bitbucketClientSecret);
        if (sourceCodeRepo == null) {
            return new HashMap<>();
        } else {
            return sourceCodeRepo.getWorkflowGitUrl2RepositoryId();
        }
    }

    /**
     * Updates the user's google access token in the DB
     * @param userId    The user's ID
     */
    private void updateGoogleAccessToken(Long userId) {
        List<Token> googleByUserId = tokenDAO.findGoogleByUserId(userId);
        if (!googleByUserId.isEmpty()) {
            Token googleToken = googleByUserId.get(0);
            Optional<String> validAccessToken = GoogleHelper
                    .getValidAccessToken(googleToken);
            if (validAccessToken.isPresent()) {
                googleToken.setContent(validAccessToken.get());
                tokenDAO.update(googleToken);
            }
        }
    }

    private void checkNotNullUser(User user) {
        if (user == null) {
            throw new CustomWebApplicationException("User not found.", HttpStatus.SC_NOT_FOUND);
        }
    }
}
