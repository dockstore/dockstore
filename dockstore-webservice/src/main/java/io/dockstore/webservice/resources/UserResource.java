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
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Group;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.GroupDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.dropwizard.views.View;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import org.apache.http.client.HttpClient;
import org.eclipse.egit.github.core.client.GitHubClient;
//import org.eclipse.egit.github.core.service.ContentsService;
//import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.egit.github.core.service.UserService;

/**
 *
 * @author xliu
 */
@Path("/user")
@Api(value = "/user")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {
    private final HttpClient client;
    private final UserDAO userDAO;
    private final GroupDAO groupDAO;
    private final TokenDAO tokenDAO;
    private final String githubClientID;
    private final String githubClientSecret;

    private static final String TARGET_URL = "https://github.com/";

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
    public Group createGroup(@QueryParam("group_name") String name) {
        Group group = new Group();
        group.setName(name);
        long create = groupDAO.create(group);
        return groupDAO.findById(create);
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all known users", notes = "List all users", response = User.class, responseContainer = "List", authorizations = @Authorization(value = "api_key"))
    public List<User> listUsers() {
        return userDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/username/{username}")
    @ApiOperation(value = "Get user", response = User.class, authorizations = @Authorization(value = "api_key"))
    public User listUser(@ApiParam(value = "Username of user to return") @PathParam("username") String username) {
        return userDAO.findByUsername(username);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/getUser")
    @ApiOperation(value = "Get user with id", response = User.class)
    public User getUser(@QueryParam("user_id") Long userId) {
        return userDAO.findById(userId);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/registerUser")
    @ApiOperation(value = "Add new user", notes = "Register a new user", response = User.class)
    public User registerUser(@QueryParam("username") String username, @QueryParam("password") String password,
            @QueryParam("is_admin") boolean isAdmin) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setIsAdmin(isAdmin);
        long create = userDAO.create(user);
        return userDAO.findById(create);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/getGroupsFromUser")
    @ApiOperation(value = "Get groups that the user belongs to", response = Group.class)
    public List<Group> getGroupsFromUser(@QueryParam("user_id") Long userId) {
        User user = userDAO.findById(userId);
        List grouplist = new ArrayList(user.getGroups());
        return grouplist;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/getUsersFromGroup")
    @ApiOperation(value = "Get users that belongs to a group", response = User.class)
    public List<User> getUsersFromGroup(@QueryParam("group_id") Long groupId) {
        Group group = groupDAO.findById(groupId);
        List userlist = new ArrayList(group.getUsers());
        return userlist;

    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/allGroups")
    @ApiOperation(value = "Get all groups", response = Group.class)
    public List<Group> allGroups() {
        return groupDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/addGroupToUser")
    @ApiOperation(value = "Add a group to a user", response = String.class)
    public String addGroupToUser(@QueryParam("user_id") Long userId, @QueryParam("group_id") Long groupId) {
        User user = userDAO.findById(userId);
        Group group = groupDAO.findById(groupId);

        user.addGroup(group);

        return "Hello";
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/addUserToGroup")
    @ApiOperation(value = "Add a user to a group", notes = "Does not work. Need to add group to user. This is because the user is defined as the 'owner'. It can be implemented the other way around.", response = String.class)
    public String addUserToGroup(@QueryParam("user_id") Long userId, @QueryParam("group_id") Long groupId) {
        User user = userDAO.findById(userId);
        Group group = groupDAO.findById(groupId);

        group.addUser(user);

        return "Hello";
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/registerGithub")
    @ApiOperation(value = "", response = GithubRegisterView.class)
    @Produces(MediaType.TEXT_HTML)
    public GithubRegisterView registerGithub() {
        return new GithubRegisterView();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/registerGithubRedirect")
    @ApiOperation(value = "", response = User.class)
    public User registerGithubRedirect(@QueryParam("code") String code) {
        Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "login/oauth/access_token?code=" + code + "&client_id="
                + githubClientID + "&client_secret=" + githubClientSecret, null, client);

        String accessToken;
        if (asString.isPresent()) {
            Map<String, String> split = Splitter.on('&').trimResults().withKeyValueSeparator("=").split(asString.get());
            accessToken = split.get("access_token");
        } else {
            throw new WebApplicationException("Could not retrieve github.com token based on code");
        }

        GitHubClient githubClient = new GitHubClient();
        githubClient.setOAuth2Token(accessToken);
        try {
            UserService uService = new UserService(githubClient);
            // RepositoryService service = new RepositoryService(githubClient);
            // ContentsService cService = new ContentsService(githubClient);
            org.eclipse.egit.github.core.User githubUser = uService.getUser();

            String githubLogin = githubUser.getLogin();

            User user = new User();
            user.setUsername(githubLogin);
            long userID = userDAO.create(user);

            Token token = new Token();
            token.setTokenSource(TokenType.GITHUB_COM.toString());
            token.setContent(accessToken);
            token.setUserId(userID);
            tokenDAO.create(token);

            return userDAO.findById(userID);

        } catch (IOException ex) {
            throw new WebApplicationException("Token ignored due to IOException");
        }
    }

    /**
     * @return the clientID
     */
    public String getGithubClientID() {
        return githubClientID;
    }

    public class GithubRegisterView extends View {
        private final UserResource parent;

        public GithubRegisterView() {
            super("github.register.auth.view.ftl");
            this.parent = UserResource.this;
        }

        /**
         * @return the parent
         */
        public UserResource getParent() {
            return parent;
        }
    }

}
