/*
 * Copyright (C) 2015 Consonance
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;

import io.dockstore.webservice.Helper;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.ContainerDAO;
import io.dockstore.webservice.jdbi.LabelDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

/**
 *
 * @author dyuen
 */
@Path("/containers")
@Api(value = "containertags")
@Produces(MediaType.APPLICATION_JSON)
public class DockerRepoTagResource {

    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;
    private final ContainerDAO containerDAO;
    private final TagDAO tagDAO;
    private final LabelDAO labelDAO;
    private final HttpClient client;

    private static final Logger LOG = LoggerFactory.getLogger(DockerRepoTagResource.class);

    public DockerRepoTagResource(HttpClient client, UserDAO userDAO, TokenDAO tokenDAO, ContainerDAO containerDAO, TagDAO tagDAO,
            LabelDAO labelDAO) {
        this.userDAO = userDAO;
        this.tokenDAO = tokenDAO;
        this.tagDAO = tagDAO;
        this.labelDAO = labelDAO;
        this.client = client;

        this.containerDAO = containerDAO;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/{containerId}/tags")
    @ApiOperation(value = "Get tags  for a container by id", notes = "Lists tags for a container. Enter full path (include quay.io in path).", response = Tag.class, responseContainer = "Set")
    public Set<Tag> getTagsByPath(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Container to modify.", required = true) @PathParam("containerId") Long containerId) {
        Container c = containerDAO.findById(containerId);
        Helper.checkContainer(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        return c.getTags();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/tags")
    @ApiOperation(value = "Update the tags linked to a container", notes = "Tag correspond to each row of the versions table listing all information for a docker repo tag", response = Tag.class, responseContainer = "List")
    public Set<Tag> updateTags(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Container to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "List of modified tags", required = true) List<Tag> tags) {

        Container c = containerDAO.findById(containerId);
        Helper.checkContainer(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        // create a map for quick lookup
        Map<Long, Tag> mapOfExistingTags = new HashMap<>();
        for (Tag tag : c.getTags()) {
            mapOfExistingTags.put(tag.getId(), tag);
        }

        for (Tag tag : tags) {
            if (mapOfExistingTags.containsKey(tag.getId())) {
                // remove existing copy and add the new one
                final Tag existingTag = mapOfExistingTags.get(tag.getId());
                existingTag.updateByUser(tag);
            }
        }
        Container result = containerDAO.findById(containerId);
        Helper.checkContainer(result);
        return result.getTags();
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{containerId}/tags")
    @ApiOperation(value = "Add new tags linked to a container", notes = "Tag correspond to each row of the versions table listing all information for a docker repo tag", response = Tag.class, responseContainer = "List")
    public Set<Tag> addTags(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Container to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "List of new tags", required = true) List<Tag> tags) {

        Container c = containerDAO.findById(containerId);
        Helper.checkContainer(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        for (Tag tag : tags) {
            final long tagId = tagDAO.create(tag);
            final Tag byId = tagDAO.findById(tagId);
            c.addTag(byId);
        }

        Container result = containerDAO.findById(containerId);
        Helper.checkContainer(result);
        return result.getTags();
    }
}
