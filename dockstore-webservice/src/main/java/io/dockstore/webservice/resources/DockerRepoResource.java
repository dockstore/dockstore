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

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import io.dockstore.webservice.Helper;
import io.dockstore.webservice.api.RegisterRequest;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
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
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author dyuen
 */
@Path("/containers")
@Api(value = "containers")
@Produces(MediaType.APPLICATION_JSON)
public class DockerRepoResource {

    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;
    private final ContainerDAO containerDAO;
    private final TagDAO tagDAO;
    private final LabelDAO labelDAO;
    private final HttpClient client;

    private final String bitbucketClientID;
    private final String bitbucketClientSecret;

    public static final String TARGET_URL = "https://quay.io/api/v1/";

    private final ObjectMapper objectMapper;

    private static final Logger LOG = LoggerFactory.getLogger(DockerRepoResource.class);

    private final List<String> namespaces = new ArrayList<>();

    @SuppressWarnings("checkstyle:parameternumber")
    public DockerRepoResource(ObjectMapper mapper, HttpClient client, UserDAO userDAO, TokenDAO tokenDAO, ContainerDAO containerDAO,
            TagDAO tagDAO, LabelDAO labelDAO, String bitbucketClientID, String bitbucketClientSecret) {
        this.objectMapper = mapper;
        this.userDAO = userDAO;
        this.tokenDAO = tokenDAO;
        this.tagDAO = tagDAO;
        this.labelDAO = labelDAO;
        this.client = client;

        this.bitbucketClientID = bitbucketClientID;
        this.bitbucketClientSecret = bitbucketClientSecret;

        this.containerDAO = containerDAO;
    }

    @GET
    @Path("/refresh")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh all repos", notes = "Updates some metadata. ADMIN ONLY", response = Container.class, responseContainer = "List")
    // @SuppressWarnings("checkstyle:methodlength")
    public List<Container> refreshAll(@ApiParam(hidden = true) @Auth Token authToken) {
        User authUser = userDAO.findById(authToken.getUserId());
        Helper.checkUser(authUser);

        List<Container> containers = new ArrayList<>();
        List<User> users = userDAO.findAll();
        for (User user : users) {
            try {
                List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

                if (!tokens.isEmpty()) {
                    Token bitbucketToken = tokens.get(0);
                    Helper.refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
                }

                containers.addAll(Helper.refresh(user.getId(), client, objectMapper, userDAO, containerDAO, tokenDAO, tagDAO));
            } catch (WebApplicationException ex) {
                LOG.info("Failed to refresh user " + user.getId());
            }
        }
        return containers;
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all docker containers cached in database", notes = "List docker container repos currently known. Admin Only", response = Container.class, responseContainer = "List")
    public List<Container> allContainers(@ApiParam(hidden = true) @Auth Token authToken) {
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user);

        List<Container> list = containerDAO.findAll();
        return list;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @ApiOperation(value = "Get a cached repo", response = Container.class)
    public Container getContainer(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Container ID", required = true) @PathParam("containerId") Long containerId) {
        Container c = containerDAO.findById(containerId);
        Helper.checkContainer(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        return c;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/labels")
    @ApiOperation(value = "Update the labels linked to a container.",
		notes = "Labels are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.",
		response = Container.class)
    public Container updateLabels(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Container to modify.", required = true)
    			@PathParam("containerId") Long containerId,
            @ApiParam(value = "Comma-delimited list of labels.", required = true)
    			@QueryParam("labels") String labelStrings) {
        Container c = containerDAO.findById(containerId);
        Helper.checkContainer(c);
        
        if (labelStrings.length() == 0) {
        	c.setLabels(new HashSet<>(0));
        } else {
        	Set<String> labelStringSet = new HashSet<String>(
        			Arrays.asList(labelStrings.toLowerCase().split("\\s*,\\s*")));
            final String labelStringPattern = "^[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*$";
            final int labelMaxLength = 255;
            Set<Label> labels = new HashSet<Label>();
            for (final String labelString : labelStringSet) {
            	if (labelString.length() <= labelMaxLength
            			&& labelString.matches(labelStringPattern)) {
            		Label label = labelDAO.findByLabelValue(labelString);
            		if (label != null) {
            			labels.add(label);
            		} else {
            			label = new Label();
            			label.setValue(labelString);
            			long id = labelDAO.create(label);
            			labels.add(labelDAO.findById(id));
            		}
            	} else {
            		throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
            	}
            }
            c.setLabels(labels);
        }

        return c;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/users")
    @ApiOperation(value = "Get users of a container", response = User.class, responseContainer = "List")
    public List<User> getUsers(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Container ID", required = true) @PathParam("containerId") Long containerId) {
        Container c = containerDAO.findById(containerId);
        Helper.checkContainer(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        return new ArrayList(c.getUsers());
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/registered/{containerId}")
    @ApiOperation(value = "Get a registered container", notes = "NO authentication", response = Container.class)
    public Container getRegisteredContainer(@ApiParam(value = "Container ID", required = true) @PathParam("containerId") Long containerId) {
        Container c = containerDAO.findRegisteredById(containerId);
        Helper.checkContainer(c);

        return c;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{containerId}/register")
    @ApiOperation(value = "Register or unregister a container", notes = "Register a container (public or private). Assumes that user is using quay.io and github.", response = Container.class)
    public Container register(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Container id to delete", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "RegisterRequest to refresh the list of repos for a user", required = true) RegisterRequest request) {
        Container c = containerDAO.findById(containerId);
        Helper.checkContainer(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);
        if (request.getRegister()) {
            if (c.getHasCollab() && !c.getGitUrl().isEmpty()) {
                c.setIsRegistered(true);
            } else {
                throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
            }
        } else {
            c.setIsRegistered(false);
        }

        long id = containerDAO.create(c);
        c = containerDAO.findById(id);
        return c;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("registered")
    @ApiOperation(value = "List all registered containers. This would be a minimal resource that would need to be implemented "
            + "by a GA4GH reference server", tags = { "GA4GH", "containers" }, notes = "NO authentication", response = Container.class, responseContainer = "List")
    public List<Container> allRegisteredContainers() {
        List<Container> repositories = containerDAO.findAllRegistered();
        return repositories;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{path}/registered")
    @ApiOperation(value = "Get a registered container", notes = "NO authentication", response = Container.class)
    public Container getRegisteredContainerByPath(@ApiParam(value = "Repository path", required = true) @PathParam("path") String path) {
        Container c = containerDAO.findRegisteredByPath(path);
        if (c == null) {
            throw new WebApplicationException(HttpStatus.SC_NOT_FOUND);
        } else {
            return c;
        }
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/{repository}")
    @ApiOperation(value = "Get a container by path", notes = "Lists info of container. Enter full path (include quay.io in path).", response = Container.class)
    public Container getContainerByPath(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "repository", required = true) @PathParam("repository") String path) {
        Container container = containerDAO.findByPath(path);
        Helper.checkContainer(container);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, container);

        return container;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/shareWithUser")
    @ApiOperation(value = "User shares a container with a chosen user", notes = "Needs to be fleshed out.", hidden = true)
    public void shareWithUser(@QueryParam("container_id") Long containerId, @QueryParam("user_id") Long userId) {
        throw new UnsupportedOperationException();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/shareWithGroup")
    @ApiOperation(value = "User shares a container with a chosen group", notes = "Needs to be fleshed out.", hidden = true)
    public void shareWithGroup(@QueryParam("container_id") Long containerId, @QueryParam("group_id") Long groupId) {
        throw new UnsupportedOperationException();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/builds")
    @ApiOperation(value = "Get the list of repository builds.", notes = "For TESTING purposes. Also useful for getting more information about the repository.\n Enter full path without quay.io", response = String.class, hidden = true)
    public String builds(@ApiParam(hidden = true) @Auth Token authToken, @QueryParam("repository") String repo,
            @QueryParam("userId") long userId) {
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, userId);

        List<Token> tokens = tokenDAO.findByUserId(userId);
        StringBuilder builder = new StringBuilder();

        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                String url = TARGET_URL + "repository/" + repo + "/build/";
                Optional<String> asString = ResourceUtilities.asString(url, token.getContent(), client);

                if (asString.isPresent()) {
                    String json = asString.get();
                    LOG.info("RESOURCE CALL: " + url);

                    Gson gson = new Gson();
                    Map<String, ArrayList> map = new HashMap<>();
                    map = (Map<String, ArrayList>) gson.fromJson(json, map.getClass());

                    Map<String, Map<String, String>> map2 = new HashMap<>();

                    if (!map.get("builds").isEmpty()) {
                        map2 = (Map<String, Map<String, String>>) map.get("builds").get(0);

                        String gitURL = map2.get("trigger_metadata").get("git_url");
                        LOG.info(gitURL);

                        ArrayList<String> tags = (ArrayList<String>) map2.get("tags");
                        for (String tag : tags) {
                            LOG.info(tag);
                        }
                    }

                    builder.append(asString.get());
                }
                builder.append("\n");
            }
        }

        return builder.toString();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/search")
    @ApiOperation(value = "Search for matching registered containers."
            + " This would be a minimal resource that would need to be implemented by a GA4GH reference server", notes = "Search on the name (full path name) and description. NO authentication", response = Container.class, responseContainer = "List", tags = {
            "GA4GH", "containers" })
    public List<Container> search(@QueryParam("pattern") String word) {
        return containerDAO.searchPattern(word);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/tags")
    @ApiOperation(value = "List the tags for a registered container", response = Tag.class, responseContainer = "List", hidden = true)
    public List<Tag> tags(@ApiParam(hidden = true) @Auth Token authToken, @QueryParam("containerId") long containerId) {
        Container repository = containerDAO.findById(containerId);
        Helper.checkContainer(repository);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, repository);

        List<Tag> tags = new ArrayList<Tag>();
        tags.addAll(repository.getTags());
        return (List) tags;
    }

    // TODO: this method is very repetative with the method below, need to refactor
    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/dockerfile")
    @ApiOperation(value = "Get the corresponding Dockerfile on Github. This would be a minimal resource that would need to be implemented "
            + "by a GA4GH reference server", tags = { "GA4GH", "containers" }, notes = "Does not need authentication", response = Helper.FileResponse.class)
    public Helper.FileResponse dockerfile(@ApiParam(value = "Container id", required = true) @PathParam("containerId") Long containerId) {

        // info about this repository path
        Container container = containerDAO.findById(containerId);
        Helper.checkContainer(container);

        return Helper.readGitRepositoryFile(container, "Dockerfile", client);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/cwl")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github. This would be a minimal resource that would need to be implemented "
            + "by a GA4GH reference server", tags = { "GA4GH", "containers" }, notes = "Does not need authentication", response = Helper.FileResponse.class)
    public Helper.FileResponse cwl(@ApiParam(value = "Container id", required = true) @PathParam("containerId") Long containerId) {

        // info about this repository path
        Container container = containerDAO.findById(containerId);
        Helper.checkContainer(container);

        return Helper.readGitRepositoryFile(container, "Dockstore.cwl", client);
    }
}
