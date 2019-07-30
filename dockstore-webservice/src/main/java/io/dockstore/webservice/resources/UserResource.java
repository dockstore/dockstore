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

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Lists;
import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.api.Limits;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.ExtendedUserData;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.OrganizationUser;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.jdbi.EntryDAO;
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
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * @author xliu
 */
@Path("/users")
@Api("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource implements AuthenticatedResourceInterface {
    private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);
    private final ElasticManager elasticManager;
    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;

    private final WorkflowResource workflowResource;
    private final DockerRepoResource dockerRepoResource;
    private final WorkflowDAO workflowDAO;
    private final ToolDAO toolDAO;
    private PermissionsInterface authorizer;
    private final CachingAuthenticator cachingAuthenticator;

    public UserResource(SessionFactory sessionFactory, WorkflowResource workflowResource, DockerRepoResource dockerRepoResource,
            CachingAuthenticator cachingAuthenticator, PermissionsInterface authorizer) {
        this.userDAO = new UserDAO(sessionFactory);
        this.tokenDAO = new TokenDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.toolDAO = new ToolDAO(sessionFactory);
        this.workflowResource = workflowResource;
        this.dockerRepoResource = dockerRepoResource;
        this.authorizer = authorizer;
        elasticManager = new ElasticManager();
        this.cachingAuthenticator = cachingAuthenticator;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/username/{username}")
    @ApiOperation(value = "Get a user by username.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User listUser(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam("Username of user to return") @PathParam("username") String username) {
        @SuppressWarnings("deprecation")
        User user = userDAO.findByUsername(username);
        checkUser(authUser, user.getId());
        return user;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{userId}")
    @Operation(operationId = "getSpecificUser")
    @ApiOperation(nickname = "getSpecificUser", value = "Get user by id.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User getUser(@ApiParam(hidden = true) @Auth User authUser, @ApiParam("User to return") @PathParam("userId") long userId) {
        checkUser(authUser, userId);
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new CustomWebApplicationException("User not found.", HttpStatus.SC_BAD_REQUEST);
        }
        return user;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/user")
    @Operation(operationId = "getUser")
    @ApiOperation(nickname = "getUser", value = "Get the logged-in user.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User getUser(@ApiParam(hidden = true) @Auth User user) {
        User foundUser = userDAO.findById(user.getId());
        Hibernate.initialize(foundUser.getUserProfiles());
        return foundUser;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/user/memberships")
    @ApiOperation(value = "Get the logged-in users memberships.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = OrganizationUser.class, responseContainer = "set")
    public Set<OrganizationUser> getUserMemberships(@ApiParam(hidden = true) @Auth User user) {
        User foundUser = userDAO.findById(user.getId());
        Set<OrganizationUser> organizationUsers = foundUser.getOrganizations();
        organizationUsers.forEach(organizationUser -> Hibernate.initialize(organizationUser.getOrganization()));
        return organizationUsers;
    }



    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/user/extended")
    @ApiOperation(value = "Get additional information about the logged-in user.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = ExtendedUserData.class)
    public ExtendedUserData getExtendedUserData(@ApiParam(hidden = true) @Auth User user) {
        User foundUser = userDAO.findById(user.getId());
        return new ExtendedUserData(foundUser, this.authorizer);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/user/changeUsername")
    @ApiOperation(value = "Change username if possible.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User changeUsername(@ApiParam(hidden = true) @Auth User authUser, @ApiParam("Username to change to") @QueryParam("username") String username) {
        checkUser(authUser, authUser.getId());
        Pattern pattern = Pattern.compile("^[a-zA-Z]+[.a-zA-Z0-9-_]*$");
        if (!pattern.asPredicate().test(username)) {
            throw new CustomWebApplicationException("Username pattern invalid", HttpStatus.SC_BAD_REQUEST);
        }
        User user = userDAO.findById(authUser.getId());
        if (!new ExtendedUserData(user, this.authorizer).canChangeUsername()) {
            throw new CustomWebApplicationException("Cannot change username, user not ready", HttpStatus.SC_BAD_REQUEST);
        }
        user.setUsername(username);
        user.setSetupComplete(true);
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

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/user")
    @ApiOperation(value = "Delete user if possible.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Boolean.class)
    public boolean selfDestruct(
            @ApiParam(hidden = true) @Auth User authUser) {
        checkUser(authUser, authUser.getId());
        User user = userDAO.findById(authUser.getId());
        if (!new ExtendedUserData(user, this.authorizer).canChangeUsername()) {
            throw new CustomWebApplicationException("Cannot delete user, user not ready for deletion", HttpStatus.SC_BAD_REQUEST);
        }
        // Remove dangling sharing artifacts before getting rid of tokens
        this.authorizer.selfDestruct(user);

        // Delete entries for which this user is the only user
        deleteSelfFromEntries(user);

        invalidateTokensForUser(user);

        return userDAO.delete(user);
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
                    entryDAO.delete(entry);
                });
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/user/{userId}")
    @RolesAllowed("admin")
    @ApiOperation(value = "Terminate user if possible.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Boolean.class, nickname = "terminateUser")
    public boolean terminateUser(
        @ApiParam(hidden = true) @Auth User authUser,  @ApiParam("User to terminate") @PathParam("userId") long targetUserId) {
        // note this terminates the user but leaves behind a tombstone to prevent re-login
        checkUser(authUser, authUser.getId());

        User targetUser = userDAO.findById(targetUserId);
        if (targetUser == null) {
            throw new CustomWebApplicationException("User not found", HttpStatus.SC_BAD_REQUEST);
        }

        invalidateTokensForUser(targetUser);

        targetUser.setBanned(true);
        return true;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/checkUser/{username}")
    @ApiOperation(value = "Check if user with some username exists.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Boolean.class)
    public boolean checkUserExists(@ApiParam(hidden = true) @Auth User user,
                                   @ApiParam("User name to check") @PathParam("username") String username) {
        @SuppressWarnings("deprecation")
        User foundUser = userDAO.findByUsername(username);
        return foundUser != null;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{userId}/tokens")
    @ApiOperation(value = "Get tokens with user id.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Token.class, responseContainer = "List")
    public List<Token> getUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        checkUser(user, userId);
        return tokenDAO.findByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{userId}/tokens/github.com")
    @ApiOperation(value = "Get Github tokens with user id.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Token.class, responseContainer = "List")
    public List<Token> getGithubUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        checkUser(user, userId);
        return tokenDAO.findGithubByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{userId}/tokens/gitlab.com")
    @ApiOperation(value = "Get Gitlab tokens with user id.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Token.class, responseContainer = "List")
    public List<Token> getGitlabUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        checkUser(user, userId);
        return tokenDAO.findGitlabByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{userId}/tokens/quay.io")
    @ApiOperation(value = "Get Quay tokens with user id.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Token.class, responseContainer = "List")
    public List<Token> getQuayUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        checkUser(user, userId);
        return tokenDAO.findQuayByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{userId}/tokens/dockstore")
    @ApiOperation(value = "Get Dockstore tokens with user id.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Token.class, responseContainer = "List")
    public List<Token> getDockstoreUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        checkUser(user, userId);
        return tokenDAO.findQuayByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{userId}/containers/published")
    @ApiOperation(value = "List all published tools from a user.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class, responseContainer = "List")
    public List<Tool> userPublishedContainers(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUser(user, userId);

        // get live entity
        final User byId = this.userDAO.findById(user.getId());
        final List<Tool> immutableList = byId.getEntries().stream().filter(Tool.class::isInstance).map(Tool.class::cast)
            .collect(Collectors.toList());
        final List<Tool> repositories = Lists.newArrayList(immutableList);
        repositories.removeIf(c -> !c.getIsPublished());
        return repositories;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{userId}/workflows/published")
    @ApiOperation(value = "List all published workflows from a user.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class, responseContainer = "List")
    public List<Workflow> userPublishedWorkflows(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUser(user, userId);

        // get live entity
        final User byId = this.userDAO.findById(user.getId());
        final List<Workflow> immutableList = byId.getEntries().stream().filter(Workflow.class::isInstance).map(Workflow.class::cast)
            .collect(Collectors.toList());
        final List<Workflow> repositories = Lists.newArrayList(immutableList);
        repositories.removeIf(workflow -> !workflow.getIsPublished());
        return repositories;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/containers/{organization}/refresh")
    @ApiOperation(value = "Refresh all tools owned by the logged-in user with specified organization.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class, responseContainer = "List")
    public List<Tool> refreshToolsByOrganization(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId,
            @ApiParam(value = "Organization", required = true) @PathParam("organization") String organization) {

        checkUser(authUser, userId);

        // Check if the user has tokens for the organization they're refreshing
        checkToolTokens(authUser, userId, organization);
        dockerRepoResource.refreshToolsForUser(userId, organization);

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
        Set<Entry> allEntries = authUser.getEntries();
        List<Entry> toolEntries = allEntries.parallelStream().filter(entry -> entry instanceof Tool && entry.getIsPublished())
                .collect(Collectors.toList());
        if (!toolEntries.isEmpty()) {
            elasticManager.bulkUpsert(toolEntries);
        }
    }

    // TODO: Only update the ones that have changed
    private void bulkUpsertWorkflows(User authUser) {
        Set<Entry> allEntries = authUser.getEntries();
        List<Entry> toolEntries = allEntries.parallelStream().filter(entry -> entry instanceof Workflow && entry.getIsPublished())
                .collect(Collectors.toList());
        if (!toolEntries.isEmpty()) {
            elasticManager.bulkUpsert(toolEntries);
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
    @Timed
    @UnitOfWork
    @Path("/{userId}/containers/refresh")
    @ApiOperation(value = "Refresh all tools owned by the logged-in user.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class, responseContainer = "List")
    public List<Tool> refresh(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {

        checkUser(authUser, userId);

        // Checks if the user has the tokens for their current tools
        checkToolTokens(authUser, userId, null);

        dockerRepoResource.refreshToolsForUser(userId, null);
        userDAO.clearCache();
        // TODO: Only update the ones that have changed
        authUser = userDAO.findById(authUser.getId());
        // Update user data
        authUser.updateUserMetadata(tokenDAO);

        List<Tool> finalTools = getTools(authUser);
        bulkUpsertTools(authUser);
        return finalTools;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/workflows/{organization}/refresh")
    @ApiOperation(value = "Refresh all workflows owned by the logged-in user with specified organization.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class, responseContainer = "List")
    public List<Workflow> refreshWorkflowsByOrganization(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId,
            @ApiParam(value = "Organization", required = true) @PathParam("organization") String organization) {

        checkUser(authUser, userId);

        // Refresh all workflows, including full workflows
        workflowResource.refreshStubWorkflowsForUser(authUser, organization, new HashSet<>());
        userDAO.clearCache();
        // Refresh the user
        authUser = userDAO.findById(authUser.getId());
        // Update user data
        authUser.updateUserMetadata(tokenDAO);

        List<Workflow> finalWorkflows = getWorkflows(authUser);
        bulkUpsertWorkflows(authUser);
        return finalWorkflows;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/workflows/refresh")
    @ApiOperation(value = "Refresh all workflows owned by the logged-in user.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class, responseContainer = "List")
    public List<Workflow> refreshWorkflows(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {

        checkUser(authUser, userId);

        // Refresh all workflows, including full workflows
        workflowResource.refreshStubWorkflowsForUser(authUser, null, new HashSet<>());
        // Refresh the user
        authUser = userDAO.findById(authUser.getId());
        // Update user data
        authUser.updateUserMetadata(tokenDAO);
        List<Workflow> finalWorkflows = getWorkflows(authUser);
        bulkUpsertWorkflows(authUser);
        return finalWorkflows;
    }

    @GET
    @Path("/{userId}/workflows")
    @Timed
    @UnitOfWork(readOnly = true)
    @ApiOperation(value = "List all workflows owned by the logged-in user.", nickname = "userWorkflows", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class, responseContainer = "List")
    public List<Workflow> userWorkflows(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUser(user, userId);
        final User fetchedUser = this.userDAO.findById(userId);
        if (fetchedUser == null) {
            throw new CustomWebApplicationException("The given user does not exist.", HttpStatus.SC_NOT_FOUND);
        }
        List<Workflow> workflows = getWorkflows(fetchedUser);
        EntryVersionHelper.stripContent(workflows, this.userDAO);
        return workflows;
    }

    private List<Workflow> getWorkflows(User user) {
        return user.getEntries().stream().filter(BioWorkflow.class::isInstance).map(BioWorkflow.class::cast).collect(Collectors.toList());
    }

    @GET
    @Path("/{userId}/services")
    @Timed
    @UnitOfWork(readOnly = true)
    @ApiOperation(value = "List all services owned by the logged-in user.", nickname = "userServices", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class, responseContainer = "List")
    public List<Workflow> userServices(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUser(user, userId);
        final User fetchedUser = this.userDAO.findById(userId);
        if (fetchedUser == null) {
            throw new CustomWebApplicationException("The given user does not exist.", HttpStatus.SC_NOT_FOUND);
        }
        return getStrippedServices(fetchedUser);
    }

    private List<Workflow> getServices(User user) {
        return user.getEntries().stream().filter(Service.class::isInstance).map(Service.class::cast).collect(Collectors.toList());
    }

    private List<Workflow> getStrippedServices(User user) {
        final List<Workflow> services = getServices(user);
        EntryVersionHelper.stripContent(services, this.userDAO);
        return services;
    }

    private List<Tool> getTools(User user) {
        return user.getEntries().stream().filter(Tool.class::isInstance).map(Tool.class::cast).collect(Collectors.toList());
    }

    @GET
    @Path("/{userId}/containers")
    @Timed
    @UnitOfWork(readOnly = true)
    @ApiOperation(value = "List all tools owned by the logged-in user.", nickname = "userContainers", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class, responseContainer = "List")
    public List<Tool> userContainers(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUser(user, userId);
        final User byId = this.userDAO.findById(userId);
        List<Tool> tools = getTools(byId);
        EntryVersionHelper.stripContent(tools, this.userDAO);
        return tools;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/starredTools")
    @ApiOperation(value = "Get the logged-in user's starred tools.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Entry.class, responseContainer = "List")
    public Set<Entry> getStarredTools(@ApiParam(hidden = true) @Auth User user) {
        User u = userDAO.findById(user.getId());
        return u.getStarredEntries().stream().filter(element -> element instanceof Tool)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/starredWorkflows")
    @ApiOperation(value = "Get the logged-in user's starred workflows.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Entry.class, responseContainer = "List")
    public Set<Entry> getStarredWorkflows(@ApiParam(hidden = true) @Auth User user) {
        User u = userDAO.findById(user.getId());
        return u.getStarredEntries().stream().filter(element -> element instanceof Workflow)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/starredOrganizations")
    @ApiOperation(value = "Get the logged-in user's starred organizations.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Organization.class, responseContainer = "List")
    public Set<Organization> getStarredOrganizations(@ApiParam(hidden = true) @Auth User user) {
        User u = userDAO.findById(user.getId());
        return u.getStarredOrganizations();
    }

    @GET
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @Path("/updateUserMetadata")
    @ApiOperation(value = "Update metadata of all users.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Admin only.", response = User.class, responseContainer = "List")
    public List<User> updateUserMetadata(@ApiParam(hidden = true) @Auth User user) {
        List<User> users = userDAO.findAll();
        for (User u : users) {
            u.updateUserMetadata(tokenDAO);
        }

        return userDAO.findAll();
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
    @ApiOperation(value = "Update metadata for logged in user.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User updateLoggedInUserMetadata(@ApiParam(hidden = true) @Auth User user, @ApiParam(value = "Token source", allowableValues = "google.com, github.com") @QueryParam("source") TokenType source) {
        User dbuser = userDAO.findById(user.getId());
        if (source.equals(TokenType.GOOGLE_COM)) {
            updateGoogleAccessToken(user.getId());
        }
        dbuser.updateUserMetadata(tokenDAO, source);
        return dbuser;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @RolesAllowed({"admin", "curator"})
    @Path("/user/{userId}/limits")
    @ApiOperation(value = "Returns the specified user's limits. ADMIN or CURATOR only", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Limits.class)
    public Limits getUserLimits(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new CustomWebApplicationException("User not found", HttpStatus.SC_NOT_FOUND);
        }
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
    @ApiOperation(value = "Update the specified user's limits. ADMIN or CURATOR only", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Limits.class)
    public Limits setUserLimits(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId,
            @ApiParam(value = "Limits to set for a user", required = true) Limits limits) {
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new CustomWebApplicationException("User not found", HttpStatus.SC_NOT_FOUND);
        }
        user.setHostedEntryCountLimit(limits.getHostedEntryCountLimit());
        user.setHostedEntryVersionsLimit(limits.getHostedEntryVersionLimit());
        // User could be cached by Dockstore or Google token -- invalidate all
        tokenDAO.findByUserId(user.getId()).stream().forEach(token -> this.cachingAuthenticator.invalidate(token.getContent()));
        return limits;
    }

    @POST
    @Path("/services/refresh")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Syncs service data with Git accounts.", notes = "Currently only works with GitHub", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) },
            response = Workflow.class, responseContainer = "List")
    public List<Workflow> syncUserServices(@ApiParam(hidden = true) @Auth User authUser) {
        final User user = userDAO.findById(authUser.getId());
        return syncAndGetServices(user, Optional.empty());
    }

    @POST
    @Path("/services/{organizationName}/refresh")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Syncs services with Git accounts for a specified organization.",
            authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) },
            response = Workflow.class, responseContainer = "List")
    public List<Workflow> syncUserServicesbyOrganization(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam(value = "Organization name", required = true) @PathParam("organizationName") String organization) {
        final User user = userDAO.findById(authUser.getId());
        return syncAndGetServices(user, Optional.of(organization));
    }

    private List<Workflow> syncAndGetServices(User user, Optional<String> organization2) {
        workflowResource.syncServicesForUser(user, organization2);
        userDAO.clearCache();
        return getStrippedServices(userDAO.findById(user.getId()));
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

}
