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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import javax.ws.rs.core.Response;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Group;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.jdbi.GroupDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.apache.http.HttpStatus;
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
    private final GroupDAO groupDAO;
    private final TokenDAO tokenDAO;

    private final WorkflowResource workflowResource;
    private final DockerRepoResource dockerRepoResource;

    public UserResource(TokenDAO tokenDAO, UserDAO userDAO, GroupDAO groupDAO, WorkflowResource workflowResource,
            DockerRepoResource dockerRepoResource) {
        this.userDAO = userDAO;
        this.groupDAO = groupDAO;
        this.tokenDAO = tokenDAO;
        this.workflowResource = workflowResource;
        this.dockerRepoResource = dockerRepoResource;
        elasticManager = new ElasticManager();
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/groups")
    @ApiOperation(value = "Create user group", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Group.class)
    public Group createGroup(@ApiParam(hidden = true) @Auth User user, @QueryParam("group_name") String name) {
        Group group = new Group();
        group.setName(name);
        long create = groupDAO.create(group);
        return groupDAO.findById(create);
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/groups/{groupId}")
    @RolesAllowed("admin")
    @ApiOperation(value = "Deletes a group, admin only", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Response.class)
    @ApiResponses(@ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid groupId value"))
    public Response deleteGroup(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Group id to delete", required = true) @PathParam("groupId") Long groupId) {
        Group group = groupDAO.findById(groupId);

        groupDAO.delete(group);

        group = groupDAO.findById(groupId);
        if (group == null) {
            return Response.ok().build();
        } else {
            return Response.serverError().build();
        }
    }

    @GET
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @ApiOperation(value = "List all known users", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "List all users. Admin only.", response = User.class, responseContainer = "List")
    public List<User> listUsers(@ApiParam(hidden = true) @Auth User user) {
        return userDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/username/{username}")
    @ApiOperation(value = "Get user", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User listUser(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam("Username of user to return") @PathParam("username") String username) {
        User user = userDAO.findByUsername(username);
        checkUser(authUser, user.getId());
        return user;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}")
    @ApiOperation(value = "Get user with id", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
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
    @UnitOfWork
    @Path("/user")
    @ApiOperation(value = "Get the logged-in user", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User getUser(@ApiParam(hidden = true) @Auth User user) {
        return userDAO.findById(user.getId());
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens")
    @ApiOperation(value = "Get tokens with user id", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Token.class, responseContainer = "List")
    public List<Token> getUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        checkUser(user, userId);

        return tokenDAO.findByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens/github.com")
    @ApiOperation(value = "Get Github tokens with user id", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Token.class, responseContainer = "List")
    public List<Token> getGithubUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        checkUser(user, userId);

        return tokenDAO.findGithubByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens/gitlab.com")
    @ApiOperation(value = "Get Gitlab tokens with user id", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Token.class, responseContainer = "List")
    public List<Token> getGitlabUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        checkUser(user, userId);

        return tokenDAO.findGitlabByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens/quay.io")
    @ApiOperation(value = "Get Quay tokens with user id", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Token.class, responseContainer = "List")
    public List<Token> getQuayUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        checkUser(user, userId);

        return tokenDAO.findQuayByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens/dockstore")
    @ApiOperation(value = "Get Dockstore tokens with user id", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Token.class, responseContainer = "List")
    public List<Token> getDockstoreUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        checkUser(user, userId);

        return tokenDAO.findQuayByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/groups")
    @ApiOperation(value = "Get groups that the user belongs to", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Group.class, responseContainer = "List")
    public List<Group> getGroupsFromUser(@ApiParam(hidden = true) @Auth User authUser, @ApiParam("User") @PathParam("userId") long userId) {
        checkUser(authUser, userId);

        User user = userDAO.findById(userId);
        if (user == null) {
            throw new CustomWebApplicationException("User not found.", HttpStatus.SC_BAD_REQUEST);
        }

        return new ArrayList<>(user.getGroups());
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/groups/{groupId}/users")
    @ApiOperation(value = "Get users that belongs to a group", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class, responseContainer = "List")
    public List<User> getUsersFromGroup(@ApiParam(hidden = true) @Auth User user, @ApiParam("Group") @PathParam("groupId") long groupId) {
        Group group = groupDAO.findById(groupId);
        if (group == null) {
            throw new CustomWebApplicationException("Group not found.", HttpStatus.SC_BAD_REQUEST);
        }

        return new ArrayList<>(group.getUsers());
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/groups")
    @ApiOperation(value = "List all groups", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Group.class, responseContainer = "List")
    public List<Group> allGroups(@ApiParam(hidden = true) @Auth User user) {
        return groupDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/groups/{groupId}")
    @ApiOperation(value = "List a group", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Group.class)
    public Group getGroup(@ApiParam(hidden = true) @Auth User user, @ApiParam("Group") @PathParam("groupId") long groupId) {
        return groupDAO.findById(groupId);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{userId}/groups")
    @ApiOperation(value = "Add a group to a user", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    public User addGroupToUser(@ApiParam(hidden = true) @Auth User authUser, @ApiParam("User ID of user") @PathParam("userId") long userId,
            @ApiParam(value = "PublishRequest to refresh the list of repos for a user", required = true) Group groupParam) {
        checkUser(authUser, userId);

        User user = userDAO.findById(userId);
        // need a live group
        Group group = groupDAO.findById(groupParam.getId());

        if (user != null && group != null) {
            user.addGroup(group);
        } else {
            LOG.info(user.getUsername() + ": " + "user or group is null");
            throw new CustomWebApplicationException("Group and/or user not found.", HttpStatus.SC_BAD_REQUEST);
        }

        return user;

    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{userId}/groups/{groupId}")
    @ApiOperation(value = "Remove a user from a group", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class)
    @ApiResponses(@ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid user or group value"))
    public User removeUserFromGroup(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam("User ID of user") @PathParam("userId") long userId,
            @ApiParam("Group ID of group") @PathParam("groupId") long groupId) {
        checkUser(authUser, userId);

        User user = userDAO.findById(userId);
        Group group = groupDAO.findById(groupId);

        if (user != null && group != null) {
            user.removeGroup(group);
        } else {
            LOG.info(user.getUsername() + ": " + "user or group is null");
            throw new CustomWebApplicationException("Group and/or user not found.", HttpStatus.SC_BAD_REQUEST);
        }
        return user;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/containers/published")
    @ApiOperation(value = "List all published containers from a user", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Get user's published containers only", response = Tool.class, responseContainer = "List")
    public List<Tool> userPublishedContainers(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUser(user, userId);

        // get live entity
        final User byId = this.userDAO.findById(user.getId());
        final ImmutableList<Tool> immutableList = FluentIterable.from(byId.getEntries()).filter(Tool.class).toList();
        final List<Tool> repositories = Lists.newArrayList(immutableList);

        for (Iterator<Tool> iterator = repositories.iterator(); iterator.hasNext(); ) {
            Tool c = iterator.next();

            if (!c.getIsPublished()) {
                iterator.remove();
            }
        }

        return repositories;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/workflows/published")
    @ApiOperation(value = "List all published workflows from a user", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Get user's published workflows only", response = Workflow.class, responseContainer = "List")
    public List<Workflow> userPublishedWorkflows(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUser(user, userId);

        // get live entity
        final User byId = this.userDAO.findById(user.getId());
        final ImmutableList<Workflow> immutableList = FluentIterable.from(byId.getEntries()).filter(Workflow.class).toList();
        final List<Workflow> repositories = Lists.newArrayList(immutableList);

        for (Iterator<Workflow> iterator = repositories.iterator(); iterator.hasNext(); ) {
            Workflow workflow = iterator.next();

            if (!workflow.getIsPublished()) {
                iterator.remove();
            }
        }

        return repositories;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/containers/{organization}/refresh")
    @ApiOperation(value = "Refresh repos owned by the logged-in user with specified organization", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Refresh all tools in an organization", response = Tool.class, responseContainer = "List")
    public List<Tool> refreshToolsByOrganization(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId,
            @ApiParam(value = "Organization", required = true) @PathParam("organization") String organization) {

        checkUser(authUser, userId);

        // Check if the user has tokens for the organization they're refreshing
        checkToolTokens(authUser, userId, organization);
        List<Tool> tools = dockerRepoResource.refreshToolsForUser(userId, organization);

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
        Token gitLabToken = Token.extractToken(tokens, TokenType.GITLAB_COM.toString());
        Token quayioToken = Token.extractToken(tokens, TokenType.QUAY_IO.toString());
        Set<Registry> uniqueRegistry = new HashSet<>();
        tools.forEach(tool -> uniqueRegistry.add(tool.getRegistry()));
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
    @ApiOperation(value = "Refresh repos owned by the logged-in user", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Updates some metadata", response = Tool.class, responseContainer = "List")
    public List<Tool> refresh(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {

        checkUser(authUser, userId);

        // Checks if the user has the tokens for their current tools
        checkToolTokens(authUser, userId, null);

        List<Tool> tools = dockerRepoResource.refreshToolsForUser(userId, null);

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
    @ApiOperation(value = "Refresh workflows owned by the logged-in user with specified organization", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Refresh all workflows in an organization", response = Workflow.class, responseContainer = "List")
    public List<Workflow> refreshWorkflowsByOrganization(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId,
            @ApiParam(value = "Organization", required = true) @PathParam("organization") String organization) {

        checkUser(authUser, userId);

        // Refresh all workflows, including full workflows
        workflowResource.refreshStubWorkflowsForUser(authUser, organization);
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
    @ApiOperation(value = "Refresh workflows owned by the logged-in user", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Updates some metadata", response = Workflow.class, responseContainer = "List")
    public List<Workflow> refreshWorkflows(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {

        checkUser(authUser, userId);

        // Refresh all workflows, including full workflows
        workflowResource.refreshStubWorkflowsForUser(authUser, null);
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
    @UnitOfWork
    @ApiOperation(value = "List workflows owned by the logged-in user", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Lists all registered and unregistered workflows owned by the user", response = Workflow.class, responseContainer = "List")
    public List<Workflow> userWorkflows(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUser(user, userId);
        // need to avoid lazy initialize error
        final User authUser = this.userDAO.findById(userId);
        return getWorkflows(authUser);
    }

    private List<Workflow> getWorkflows(User user) {
        return user.getEntries().stream().filter(Workflow.class::isInstance).map(Workflow.class::cast).collect(Collectors.toList());
    }

    private List<Tool> getTools(User user) {
        return user.getEntries().stream().filter(Tool.class::isInstance).map(Tool.class::cast).collect(Collectors.toList());
    }

    @GET
    @Path("/{userId}/containers")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List repos owned by the logged-in user", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Lists all registered and unregistered containers owned by the user", response = Tool.class, responseContainer = "List")
    public List<Tool> userContainers(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        checkUser(user, userId);
        // need to avoid lazy initialize error
        final User byId = this.userDAO.findById(userId);
        return getTools(byId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/starredTools")
    @ApiOperation(value = "Get the logged-in user's starred tools", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Entry.class, responseContainer = "List")
    public Set<Entry> getStarredTools(@ApiParam(hidden = true) @Auth User user) {
        User u = userDAO.findById(user.getId());
        return u.getStarredEntries().stream().filter(element -> element instanceof Tool)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/starredWorkflows")
    @ApiOperation(value = "Get the logged-in user's starred workflows", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Entry.class, responseContainer = "List")
    public Set<Entry> getStarredWorkflows(@ApiParam(hidden = true) @Auth User user) {
        User u = userDAO.findById(user.getId());
        return u.getStarredEntries().stream().filter(element -> element instanceof Workflow)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @GET
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @Path("/updateUserMetadata")
    @ApiOperation(value = "Update metadata of all users", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Update all users metadata. Admin only.", response = User.class, responseContainer = "List")
    public List<User> updateUserMetadata(@ApiParam(hidden = true) @Auth User user) {
        List<User> users = userDAO.findAll();
        for (User u : users) {
            u.updateUserMetadata(tokenDAO);
        }

        return userDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/user/updateUserMetadata")
    @ApiOperation(value = "Update metadata for logged in user", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Update metadata for logged in user.", response = User.class)
    public User updateLoggedInUserMetadata(@ApiParam(hidden = true) @Auth User user) {
        User dbuser = userDAO.findById(user.getId());
        dbuser.updateUserMetadata(tokenDAO);
        return dbuser;
    }
}
