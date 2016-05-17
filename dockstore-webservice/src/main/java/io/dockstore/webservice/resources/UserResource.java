/*
 *    Copyright 2016 OICR
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

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Group;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.Helper;
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
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 *
 * @author xliu
 */
@Path("/users")
@Api("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {
    private final HttpClient client;
    private final UserDAO userDAO;
    private final GroupDAO groupDAO;
    private final TokenDAO tokenDAO;


    private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);
    private final WorkflowResource workflowResource;
    private final DockerRepoResource dockerRepoResource;

    public UserResource(HttpClient client, TokenDAO tokenDAO, UserDAO userDAO, GroupDAO groupDAO,
            WorkflowResource workflowResource, DockerRepoResource dockerRepoResource) {
        this.client = client;
        this.userDAO = userDAO;
        this.groupDAO = groupDAO;
        this.tokenDAO = tokenDAO;
        this.workflowResource = workflowResource;
        this.dockerRepoResource = dockerRepoResource;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/groups")
    @ApiOperation(value = "Create user group", response = Group.class)
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
    @ApiOperation(value = "Deletes a group, admin only", response = Response.class)
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
    @ApiOperation(value = "List all known users", notes = "List all users. Admin only.", response = User.class, responseContainer = "List")
    public List<User> listUsers(@ApiParam(hidden = true) @Auth User user) {
        return userDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/username/{username}")
    @ApiOperation(value = "Get user", response = User.class)
    public User listUser(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam("Username of user to return") @PathParam("username") String username) {
        User user = userDAO.findByUsername(username);
        Helper.checkUser(authUser, user.getId());

        return user;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}")
    @ApiOperation(value = "Get user with id", response = User.class)
    public User getUser(@ApiParam(hidden = true) @Auth User authUser, @ApiParam("User to return") @PathParam("userId") long userId) {
        Helper.checkUser(authUser, userId);

        User user = userDAO.findById(userId);
        if (user == null) {
            throw new CustomWebApplicationException("User not found.", HttpStatus.SC_BAD_REQUEST);
        }
        return user;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens")
    @ApiOperation(value = "Get tokens with user id", response = Token.class, responseContainer = "List")
    public List<Token> getUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        Helper.checkUser(user, userId);

        return tokenDAO.findByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens/github.com")
    @ApiOperation(value = "Get Github tokens with user id", response = Token.class, responseContainer = "List")
    public List<Token> getGithubUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        Helper.checkUser(user, userId);

        return tokenDAO.findGithubByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens/quay.io")
    @ApiOperation(value = "Get Quay tokens with user id", response = Token.class, responseContainer = "List")
    public List<Token> getQuayUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        Helper.checkUser(user, userId);

        return tokenDAO.findQuayByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens/dockstore")
    @ApiOperation(value = "Get Dockstore tokens with user id", response = Token.class, responseContainer = "List")
    public List<Token> getDockstoreUserTokens(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("User to return") @PathParam("userId") long userId) {
        Helper.checkUser(user, userId);

        return tokenDAO.findQuayByUserId(userId);
    }

    @POST
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @ApiOperation(value = "Add new user", notes = "Register a new user, admin only", response = User.class)
    public User registerUser(@QueryParam("username") String username, @QueryParam("is_admin") boolean isAdmin) {
        final Random random = new Random();
        final int bufferLength = 1024;
        final byte[] buffer = new byte[bufferLength];
        random.nextBytes(buffer);
        String randomString = BaseEncoding.base64Url().omitPadding().encode(buffer);
        final String accessToken = Hashing.sha256().hashString(username + randomString, Charsets.UTF_8).toString();

        User user = new User();
        user.setUsername(username);
        user.setIsAdmin(isAdmin);
        long userId = userDAO.create(user);

        Token token = new Token();
        token.setTokenSource(TokenType.DOCKSTORE.toString());
        token.setContent(accessToken);
        token.setUsername(username);
        token.setUserId(userId);
        tokenDAO.create(token);

        return userDAO.findById(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/groups")
    @ApiOperation(value = "Get groups that the user belongs to", response = Group.class, responseContainer = "List")
    public List<Group> getGroupsFromUser(@ApiParam(hidden = true) @Auth User authUser, @ApiParam("User") @PathParam("userId") long userId) {
        Helper.checkUser(authUser, userId);

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
    @ApiOperation(value = "Get users that belongs to a group", response = User.class, responseContainer = "List")
    public List<User> getUsersFromGroup(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("Group") @PathParam("groupId") long groupId) {
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
    @ApiOperation(value = "List all groups", response = Group.class, responseContainer = "List")
    public List<Group> allGroups(@ApiParam(hidden = true) @Auth User user) {
        return groupDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/groups/{groupId}")
    @ApiOperation(value = "List a group", response = Group.class)
    public Group getGroup(@ApiParam(hidden = true) @Auth User user, @ApiParam("Group") @PathParam("groupId") long groupId) {
        return groupDAO.findById(groupId);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{userId}/groups")
    @ApiOperation(value = "Add a group to a user", response = User.class)
    public User addGroupToUser(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam("User ID of user") @PathParam("userId") long userId,
            @ApiParam(value = "PublishRequest to refresh the list of repos for a user", required = true) Group groupParam) {
        Helper.checkUser(authUser, userId);

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
    @ApiOperation(value = "Remove a user from a group", response = User.class)
    @ApiResponses(@ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid user or group value"))
    public User removeUserFromGroup(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam("User ID of user") @PathParam("userId") long userId, @ApiParam("Group ID of group") @PathParam("groupId") long groupId) {
        Helper.checkUser(authUser, userId);

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
    @ApiOperation(value = "List all published containers from a user", notes = "Get user's published containers only", response = Tool.class, responseContainer = "List")
    public List<Tool> userPublishedContainers(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        Helper.checkUser(user, userId);

        // get live entity
        final User byId = this.userDAO.findById(user.getId());
        final ImmutableList<Tool> immutableList = FluentIterable.from(byId.getEntries()).filter(Tool.class).toList();
        final List<Tool> repositories = Lists.newArrayList(immutableList);

        for (Iterator<Tool> iterator = repositories.iterator(); iterator.hasNext();) {
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
    @ApiOperation(value = "List all published workflows from a user", notes = "Get user's published workflows only", response = Workflow.class, responseContainer = "List")
    public List<Workflow> userPublishedWorkflows(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        Helper.checkUser(user, userId);

        // get live entity
        final User byId = this.userDAO.findById(user.getId());
        final ImmutableList<Workflow> immutableList = FluentIterable.from(byId.getEntries()).filter(Workflow.class).toList();
        final List<Workflow> repositories = Lists.newArrayList(immutableList);

        for (Iterator<Workflow> iterator = repositories.iterator(); iterator.hasNext();) {
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
    @Path("/{userId}/containers/refresh")
    @ApiOperation(value = "Refresh repos owned by the logged-in user", notes = "Updates some metadata", response = Tool.class, responseContainer = "List")
    public List<Tool> refresh(@ApiParam(hidden = true) @Auth User authUser,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {

        Helper.checkUser(authUser, userId);

        return dockerRepoResource.refreshToolsForUser(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/workflows/refresh")
    @ApiOperation(value = "Refresh workflows owned by the logged-in user", notes = "Updates some metadata", response = Workflow.class, responseContainer = "List")
    public List<Workflow> refreshWorkflows(@ApiParam(hidden = true) @Auth User authUser,
                                 @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {

        Helper.checkUser(authUser, userId);

        workflowResource.refreshStubWorkflowsForUser(authUser);
        // refresh the user
        authUser = userDAO.findById(authUser.getId());
        return FluentIterable.from(authUser.getEntries()).filter(Workflow.class).toList();
    }

    @GET
    @Path("/{userId}/workflows")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List workflows owned by the logged-in user", notes = "Lists all registered and unregistered workflows owned by the user", response = Workflow.class, responseContainer = "List")
    public List<Workflow> userWorkflows(@ApiParam(hidden = true) @Auth User user,
                                        @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        Helper.checkUser(user, userId);
        // need to avoid lazy initialize error
        final User byId = this.userDAO.findById(userId);
        return FluentIterable.from(byId.getEntries()).filter(Workflow.class).toList();
    }


    @GET
    @Path("/{userId}/containers")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List repos owned by the logged-in user", notes = "Lists all registered and unregistered containers owned by the user", response = Tool.class, responseContainer = "List")
    public List<Tool> userContainers(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        Helper.checkUser(user, userId);
        // need to avoid lazy initialize error
        final User byId = this.userDAO.findById(userId);
        return FluentIterable.from(byId.getEntries()).filter(Tool.class).toList();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/user")
    @ApiOperation(value = "Get the logged-in user", response = User.class)
    public User getUser(@ApiParam(hidden = true) @Auth User user) {
        return userDAO.findById(user.getId());
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/organizations")
    @RolesAllowed("admin")
    @ApiOperation(value = "Get user's organizations", notes = "For testing purposes. Returns the list of organizations from user's Quay.io account", response = ArrayList.class, responseContainer = "List", hidden = true)
    public ArrayList getOrganizations(@ApiParam(hidden = true) @Auth User authUser) {
        // Helper.checkUser(authUser);

        List<Token> tokens = tokenDAO.findQuayByUserId(authUser.getId());
        Token token;
        if (tokens.isEmpty()) {
            throw new CustomWebApplicationException("Quay.io token not found.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } else {
            token = tokens.get(0);
        }

        String url = "https://quay.io/api/v1/user/";
        Optional<String> asString = ResourceUtilities.asString(url, token.getContent(), client);
        if (asString.isPresent()) {
            String response = asString.get();
            LOG.info(authUser.getUsername() + ": " + "RESOURCE CALL: {}", url);

            Gson gson = new Gson();
            // Map<String, String> map = new HashMap<>();
            // map = (Map<String, String>) gson.fromJson(response, map.getClass());
            //
            // String username = map.get("username");
            // LOG.info(user.getUsername() + ": " + (username);

            Map<String, ArrayList> map2 = new HashMap<>();
            map2 = (Map<String, ArrayList>) gson.fromJson(response, map2.getClass());
            return map2.get("organizations");
        }
        return null;
    }
}
