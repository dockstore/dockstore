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
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import io.dockstore.webservice.core.Group;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
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
import java.util.ArrayList;
import java.util.List;
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
    private final String githubClientID;
    private final String githubClientSecret;

    private static final String TARGET_URL = "https://github.com/";

    private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);

    public UserResource(HttpClient client, TokenDAO tokenDAO, UserDAO userDAO, GroupDAO groupDAO, String githubClientID,
            String githubClientSecret) {
        this.client = client;
        this.userDAO = userDAO;
        this.groupDAO = groupDAO;
        this.tokenDAO = tokenDAO;
        this.githubClientID = githubClientID;
        this.githubClientSecret = githubClientSecret;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/createGroup")
    @ApiOperation(value = "Create user group", response = Group.class)
    public Group createGroup(@ApiParam(hidden = true) @Auth Token authToken, @QueryParam("group_name") String name) {
        Group group = new Group();
        group.setName(name);
        long create = groupDAO.create(group);
        return groupDAO.findById(create);
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all known users", notes = "List all users. Admin only.", response = User.class, responseContainer = "List", authorizations = @Authorization(value = "api_key"))
    public List<User> listUsers(@ApiParam(hidden = true) @Auth Token authToken) {
        User user = userDAO.findById(authToken.getUserId());
        if (!user.getIsAdmin()) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }

        return userDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/username/{username}")
    @ApiOperation(value = "Get user", response = User.class, authorizations = @Authorization(value = "api_key"))
    public User listUser(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Username of user to return") @PathParam("username") String username) {
        User user = userDAO.findById(authToken.getUserId());
        if (!user.getIsAdmin() && !(username.equals(user.getUsername()))) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }

        return userDAO.findByUsername(username);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}")
    @ApiOperation(value = "Get user with id", response = User.class)
    public User getUser(@ApiParam(hidden = true) @Auth Token authToken, @ApiParam(value = "User to return") @PathParam("userId") long userId) {
        User authUser = userDAO.findById(authToken.getUserId());
        if (!authUser.getIsAdmin() && authUser.getId() != userId) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }

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
        if (!user.getIsAdmin() && user.getId() != userId) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }

        return tokenDAO.findByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens/github.com")
    @ApiOperation(value = "Get Github tokens with user id", response = Token.class, responseContainer = "List")
    public Token getGithubUserTokens(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "User to return") @PathParam("userId") long userId) {
        User user = userDAO.findById(authToken.getUserId());
        if (!user.getIsAdmin() && user.getId() != userId) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }

        return tokenDAO.findGithubByUserId(userId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{userId}/tokens/quay.io")
    @ApiOperation(value = "Get Quay tokens with user id", response = Token.class, responseContainer = "List")
    public Token getQuayUserTokens(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "User to return") @PathParam("userId") long userId) {
        User user = userDAO.findById(authToken.getUserId());
        if (!user.getIsAdmin() && user.getId() != userId) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }
        return tokenDAO.findQuayByUserId(userId);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/registerUser")
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
    @Path("/getGroupsFromUser")
    @ApiOperation(value = "Get groups that the user belongs to", response = Group.class)
    public List<Group> getGroupsFromUser(@ApiParam(hidden = true) @Auth Token authToken, @QueryParam("user_id") long userId) {
        User authUser = userDAO.findById(authToken.getUserId());
        if (!authUser.getIsAdmin() && authUser.getId() != userId) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }

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
    @Path("/getUsersFromGroup")
    @ApiOperation(value = "Get users that belongs to a group", response = User.class)
    public List<User> getUsersFromGroup(@ApiParam(hidden = true) @Auth Token authToken, @QueryParam("group_id") long groupId) {
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
    @Path("/allGroups")
    @ApiOperation(value = "Get all groups", response = Group.class)
    public List<Group> allGroups(@ApiParam(hidden = true) @Auth Token authToken) {
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
        if (!authUser.getIsAdmin() && authUser.getId() != userId) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }

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
        if (!authUser.getIsAdmin() && authUser.getId() != userId) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }

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

}
