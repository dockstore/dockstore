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
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Group;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.GroupDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
//import javax.ws.rs.core.Response;
import org.apache.http.client.HttpClient;

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

    public UserResource(HttpClient client, UserDAO userDAO, GroupDAO groupDAO) {
        this.client = client;
        this.userDAO = userDAO;
        this.groupDAO = groupDAO;
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
        Set<Group> groups = user.getGroups();
        List grouplist = new ArrayList();
        grouplist.addAll(groups);
        return grouplist;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/getUsersFromGroup")
    @ApiOperation(value = "Get users that belongs to a group", response = User.class)
    public List<User> getUsersFromGroup(@QueryParam("group_id") Long groupId) {
        Group group = groupDAO.findById(groupId);
        Set<User> users = group.getUsers();
        List userlist = new ArrayList();
        userlist.addAll(users);
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
    @ApiOperation(value = "Add a user to a group", response = String.class)
    public String addUserToGroup(@QueryParam("user_id") Long userId, @QueryParam("group_id") Long groupId) {
        User user = userDAO.findById(userId);
        Group group = groupDAO.findById(groupId);

        group.addUser(user);

        return "Hello";
    }

}
