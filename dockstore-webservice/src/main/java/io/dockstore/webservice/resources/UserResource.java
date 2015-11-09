/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.webservice.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import io.dockstore.webservice.Helper;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Group;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.ContainerDAO;
import io.dockstore.webservice.jdbi.GroupDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author xliu
 */
@Path("/users")
@Api(value = "/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {
    private final HttpClient client;
    private final UserDAO userDAO;
    private final GroupDAO groupDAO;
    private final TokenDAO tokenDAO;
    private final ContainerDAO containerDAO;
    private final TagDAO tagDAO;
    private final String githubClientID;
    private final String githubClientSecret;

    private static final String TARGET_URL = "https://github.com/";

    private final ObjectMapper objectMapper;

    private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);

    @SuppressWarnings("checkstyle:parameternumber")
    public UserResource(ObjectMapper mapper, HttpClient client, TokenDAO tokenDAO, UserDAO userDAO, GroupDAO groupDAO,
            ContainerDAO containerDAO, TagDAO tagDAO, String githubClientID, String githubClientSecret) {
        this.objectMapper = mapper;
        this.client = client;
        this.userDAO = userDAO;
        this.groupDAO = groupDAO;
        this.tokenDAO = tokenDAO;
        this.containerDAO = containerDAO;
        this.tagDAO = tagDAO;
        this.githubClientID = githubClientID;
        this.githubClientSecret = githubClientSecret;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/groups")
    @ApiOperation(value = "Create user group", response = Group.class)
    public Group createGroup(@ApiParam(hidden = true) @Auth Token authToken, @QueryParam("group_name") String name) {
        Group group = new Group();
        group.setName(name);
        long create = groupDAO.create(group);
        return groupDAO.findById(create);
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/groups/{groupId}")
    @ApiOperation(value = "Deletes a group", response = Response.class)
    @ApiResponses(value = { @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid groupId value") })
    public Response deleteGroup(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Group id to delete", required = true) @PathParam("groupId") Long groupId) {
        User user = userDAO.findById(authToken.getUserId());
        Group group = groupDAO.findById(groupId);
        Helper.checkUser(user);

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
    @ApiOperation(value = "List all known users", notes = "List all users. Admin only.", response = User.class, responseContainer = "List")
    public List<User> listUsers(@ApiParam(hidden = true) @Auth Token authToken) {
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user);

        return userDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/username/{username}")
    @ApiOperation(value = "Get user", response = User.class)
    public User listUser(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Username of user to return") @PathParam("username") String username) {
        User authUser = userDAO.findById(authToken.getUserId());
        User user = userDAO.findByUsername(username);
        Helper.checkUser(authUser, user.getId());

        return user;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}")
    @ApiOperation(value = "Get user with id", response = User.class)
    public User getUser(@ApiParam(hidden = true) @Auth Token authToken, @ApiParam(value = "User to return") @PathParam("userId") long userId) {
        User authUser = userDAO.findById(authToken.getUserId());
        Helper.checkUser(authUser, userId);

        User user = userDAO.findById(userId);
        if (user == null) {
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }
        return user;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens")
    @ApiOperation(value = "Get tokens with user id", response = Token.class, responseContainer = "List")
    public List<Token> getUserTokens(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "User to return") @PathParam("userId") long userId) {
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, userId);

        return tokenDAO.findByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens/github.com")
    @ApiOperation(value = "Get Github tokens with user id", response = Token.class)
    public List<Token> getGithubUserTokens(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "User to return") @PathParam("userId") long userId) {
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, userId);

        return tokenDAO.findGithubByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens/quay.io")
    @ApiOperation(value = "Get Quay tokens with user id", response = Token.class)
    public List<Token> getQuayUserTokens(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "User to return") @PathParam("userId") long userId) {
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, userId);

        return tokenDAO.findQuayByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens/dockstore")
    @ApiOperation(value = "Get Dockstore tokens with user id", response = Token.class)
    public List<Token> getDockstoreUserTokens(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "User to return") @PathParam("userId") long userId) {
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, userId);

        return tokenDAO.findQuayByUserId(userId);
    }

    @POST
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Add new user", notes = "Register a new user", response = User.class)
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
    public List<Group> getGroupsFromUser(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "User") @PathParam("userId") long userId) {
        User authUser = userDAO.findById(authToken.getUserId());
        Helper.checkUser(authUser, userId);

        User user = userDAO.findById(userId);
        if (user == null) {
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }

        List grouplist = new ArrayList(user.getGroups());
        return grouplist;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/groups/{groupId}/users")
    @ApiOperation(value = "Get users that belongs to a group", response = User.class, responseContainer = "List")
    public List<User> getUsersFromGroup(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Group") @PathParam("groupId") long groupId) {
        Group group = groupDAO.findById(groupId);
        if (group == null) {
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }

        List userlist = new ArrayList(group.getUsers());
        return userlist;

    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/groups")
    @ApiOperation(value = "List all groups", response = Group.class, responseContainer = "List")
    public List<Group> allGroups(@ApiParam(hidden = true) @Auth Token authToken) {
        return groupDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/groups/{groupId}")
    @ApiOperation(value = "List a group", response = Group.class, responseContainer = "List")
    public List<Group> getGroup(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Group") @PathParam("groupId") long groupId) {
        return groupDAO.findAll();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{userId}/groups")
    @ApiOperation(value = "Add a group to a user", response = User.class)
    public User addGroupToUser(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "User ID of user") @PathParam("userId") long userId, @QueryParam("group_id") long groupId) {
        User authUser = userDAO.findById(authToken.getUserId());
        Helper.checkUser(authUser, userId);

        User user = userDAO.findById(userId);
        Group group = groupDAO.findById(groupId);

        if (user != null && group != null) {
            user.addGroup(group);
        } else {
            LOG.info("user or group is null");
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }

        return user;

    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{userId}/groups/{groupId}")
    @ApiOperation(value = "Remove a user from a group", response = User.class)
    @ApiResponses(value = { @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid user or group value") })
    public User removeUserFromGroup(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "User ID of user") @PathParam("userId") long userId,
            @ApiParam(value = "Group ID of group") @PathParam("groupId") long groupId) {
        User authUser = userDAO.findById(authToken.getUserId());
        Helper.checkUser(authUser, userId);

        User user = userDAO.findById(userId);
        Group group = groupDAO.findById(groupId);

        if (user != null && group != null) {
            user.removeGroup(group);
        } else {
            LOG.info("user or group is null");
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }
        return user;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/containers/registered")
    @ApiOperation(value = "List all registered containers from a user", notes = "Get user's registered containers only", response = Container.class, responseContainer = "List")
    public List<Container> userRegisteredContainers(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, userId);

        List<Container> repositories = new ArrayList(user.getContainers());

        for (Iterator<Container> iterator = repositories.iterator(); iterator.hasNext();) {
            Container c = iterator.next();

            if (!c.getIsRegistered()) {
                iterator.remove();
            }
        }

        return repositories;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/containers/refresh")
    @ApiOperation(value = "Refresh repos owned by the logged-in user", notes = "Updates some metadata", response = Container.class, responseContainer = "List")
    @SuppressWarnings("checkstyle:methodlength")
    public List<Container> refresh(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {

        User authUser = userDAO.findById(authToken.getUserId());
        Helper.checkUser(authUser, userId);

        List<Container> containers = Helper.refresh(userId, client, objectMapper, userDAO, containerDAO, tokenDAO, tagDAO);
        return containers;
    }

    @GET
    @Path("/{userId}/containers")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List repos owned by the logged-in user", notes = "Lists all registered and unregistered containers owned by the user", response = Container.class, responseContainer = "List")
    public List<Container> userContainers(@ApiParam(hidden = true) @Auth Token token,
            @ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        User user = userDAO.findById(token.getUserId());
        Helper.checkUser(user, userId);

        List<Container> ownedContainers = new ArrayList(user.getContainers());
        return ownedContainers;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/user")
    @ApiOperation(value = "Get the logged-in user", response = User.class)
    public User getUser(@ApiParam(hidden = true) @Auth Token authToken) {
        User user = userDAO.findById(authToken.getUserId());
        return user;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/organizations")
    @ApiOperation(value = "Get user's organizations", notes = "For testing purposes. Returns the list of organizations from user's Quay.io account", response = ArrayList.class, responseContainer = "List", hidden = true)
    public ArrayList getOrganizations(@ApiParam(hidden = true) @Auth Token authToken) {
        User authUser = userDAO.findById(authToken.getUserId());
        // Helper.checkUser(authUser);

        List<Token> tokens = tokenDAO.findQuayByUserId(authUser.getId());
        Token token = null;
        if (tokens.isEmpty()) {
            throw new WebApplicationException(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } else {
            token = tokens.get(0);
        }

        String url = "https://quay.io/api/v1/user/";
        Optional<String> asString = ResourceUtilities.asString(url, token.getContent(), client);
        if (asString.isPresent()) {
            String response = asString.get();
            LOG.info("RESOURCE CALL: " + url);

            Gson gson = new Gson();
            // Map<String, String> map = new HashMap<>();
            // map = (Map<String, String>) gson.fromJson(response, map.getClass());
            //
            // String username = map.get("username");
            // LOG.info(username);

            Map<String, ArrayList> map2 = new HashMap<>();
            map2 = (Map<String, ArrayList>) gson.fromJson(response, map2.getClass());
            ArrayList organizations = map2.get("organizations");

            return organizations;
        }
        return null;
    }
}
